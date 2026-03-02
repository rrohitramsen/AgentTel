from __future__ import annotations

import json
import logging
from typing import Any

import httpx

from ..config.types import McpConfig
from .models import (
    CrossStackContext,
    ExecutiveSummary,
    IncidentContext,
    JsonRpcRequest,
    JsonRpcResponse,
    OperationHealth,
    RecentAgentAction,
    RemediationAction,
    RemediationResult,
    ServiceHealth,
    SloReport,
    ToolResult,
    TrendAnalysis,
)

logger = logging.getLogger(__name__)


class McpClient:
    """Client for the AgentTel backend MCP server (JSON-RPC 2.0 over HTTP)."""

    def __init__(self, config: McpConfig) -> None:
        self._config = config
        self._request_id = 0
        self._http: httpx.AsyncClient | None = None

    # -- lifecycle --

    async def start(self) -> None:
        self._http = httpx.AsyncClient(
            base_url=self._config.base_url,
            timeout=httpx.Timeout(self._config.timeout_seconds),
        )

    async def close(self) -> None:
        if self._http is not None:
            await self._http.aclose()
            self._http = None

    # -- low-level RPC --

    def _next_id(self) -> int:
        self._request_id += 1
        return self._request_id

    async def call_tool(self, tool_name: str, arguments: dict[str, Any] | None = None) -> str:
        """Invoke an MCP tool via JSON-RPC 2.0 and return the text content."""
        if self._http is None:
            raise RuntimeError("McpClient not started; call start() first")

        request = JsonRpcRequest(
            method="tools/call",
            params={"name": tool_name, "arguments": arguments or {}},
            id=self._next_id(),
        )

        logger.debug("MCP request: %s(%s)", tool_name, arguments)

        resp = await self._http.post("/mcp", json=request.model_dump())
        resp.raise_for_status()

        rpc_response = JsonRpcResponse.model_validate(resp.json())

        if rpc_response.error is not None:
            raise RuntimeError(
                f"MCP error {rpc_response.error.code}: {rpc_response.error.message}"
            )

        # Parse the tool result
        tool_result = ToolResult.model_validate(rpc_response.result or {})

        if tool_result.isError:
            texts = " | ".join(c.text for c in tool_result.content)
            raise RuntimeError(f"MCP tool error: {texts}")

        return "\n".join(c.text for c in tool_result.content)

    # -- typed helpers --

    async def get_service_health(
        self, service: str = "default", time_range: str = "5m"
    ) -> ServiceHealth:
        """Poll the service health status.

        The MCP backend returns formatted text like:
          SERVICE: payments-platform | STATUS: HEALTHY | 2026-...
          OPERATIONS:
            POST /api/payments: err=0.0% p50=3ms p99=124ms
          SLOs:
            payment-availability: budget=100.0% burn=0.0x
        """
        raw = await self.call_tool(
            "get_service_health",
            {"service": service, "timeRange": time_range},
        )

        # Try JSON first (in case server format changes)
        try:
            data = json.loads(raw)
            return ServiceHealth.model_validate(data)
        except (json.JSONDecodeError, ValueError):
            pass

        # Parse the text format returned by the MCP backend
        return self._parse_health_text(raw, service)

    @staticmethod
    def _parse_health_text(raw: str, default_service: str = "unknown") -> ServiceHealth:
        """Parse the text-format health response into a ServiceHealth model."""
        import re

        svc_name = default_service
        overall = "unknown"
        operations: list[OperationHealth] = []
        timestamp = None

        for line in raw.split("\n"):
            # SERVICE: payments-platform | STATUS: HEALTHY | 2026-...
            m = re.match(r"^SERVICE:\s*(\S+)\s*\|\s*STATUS:\s*(\w+)(?:\s*\|\s*(.+))?", line, re.IGNORECASE)
            if m:
                svc_name = m.group(1)
                overall = m.group(2).lower()
                if m.group(3):
                    timestamp = m.group(3).strip()
                continue

            # "  POST /api/payments: err=0.0% p50=3ms p99=124ms"
            m = re.match(r"^\s{2}(\S+(?:\s+\S+)*?):\s+err=([0-9.]+)%\s+p50=(\d+)ms\s+p99=(\d+)ms", line)
            if m:
                err_rate = float(m.group(2))
                p99 = float(m.group(4))
                op_status = "degraded" if err_rate > 1.0 else ("critical" if err_rate > 5.0 else "healthy")
                operations.append(OperationHealth(
                    operation=m.group(1),
                    status=op_status,
                    error_rate=err_rate,
                    latency_p99_ms=p99,
                ))
                continue

        return ServiceHealth(
            service=svc_name,
            overall_status=overall,
            operations=operations,
            timestamp=timestamp,
        )

    async def get_incident_context(self, operation: str) -> IncidentContext:
        """Gather incident context for a specific operation."""
        raw = await self.call_tool(
            "get_incident_context",
            {"operation_name": operation},
        )
        try:
            data = json.loads(raw)
            return IncidentContext.model_validate(data)
        except (json.JSONDecodeError, ValueError):
            # MCP returns text format — use it as the summary
            return IncidentContext(operation=operation, summary=raw)

    async def get_slo_report(self, operation: str = "all") -> SloReport:
        """Retrieve SLO compliance data."""
        raw = await self.call_tool(
            "get_slo_report",
            {"operation": operation},
        )
        try:
            data = json.loads(raw)
            return SloReport.model_validate(data)
        except (json.JSONDecodeError, ValueError):
            return SloReport(operation=operation, details=raw)

    async def get_executive_summary(self) -> ExecutiveSummary:
        """Get a high-level executive summary."""
        raw = await self.call_tool("get_executive_summary", {})
        try:
            data = json.loads(raw)
            return ExecutiveSummary.model_validate(data)
        except (json.JSONDecodeError, ValueError):
            return ExecutiveSummary(summary=raw)

    async def list_remediation_actions(
        self, operation: str | None = None
    ) -> list[RemediationAction]:
        """List available remediation actions."""
        args: dict[str, Any] = {}
        if operation is not None:
            args["operation_name"] = operation
        raw = await self.call_tool("list_remediation_actions", args)
        try:
            data = json.loads(raw)
            if isinstance(data, list):
                return [RemediationAction.model_validate(item) for item in data]
            return []
        except (json.JSONDecodeError, ValueError):
            pass

        # Parse text format: "  - action-name: description [TYPE] (NEEDS APPROVAL)"
        import re
        actions: list[RemediationAction] = []
        for line in raw.splitlines():
            m = re.match(
                r"^\s+-\s+(\S+):\s+(.+?)\s+\[(\w+)\](?:\s+\(NEEDS APPROVAL\))?$",
                line,
            )
            if m:
                actions.append(RemediationAction(
                    id=m.group(1),
                    name=m.group(1),
                    description=m.group(2),
                    action_type=m.group(3).lower(),
                ))
        return actions

    async def execute_remediation(
        self, action_id: str, reason: str = "Agent-initiated remediation",
        parameters: dict[str, Any] | None = None,
    ) -> RemediationResult:
        """Execute a specific remediation action."""
        raw = await self.call_tool(
            "execute_remediation",
            {"action_name": action_id, "reason": reason},
        )
        try:
            data = json.loads(raw)
            return RemediationResult.model_validate(data)
        except (json.JSONDecodeError, ValueError):
            # MCP returns text like "Action: X\nSuccess: true\nMessage: ..."
            success = "success: true" in raw.lower()
            return RemediationResult(action_id=action_id, success=success, message=raw[:200])

    async def get_trend_analysis(
        self, operation: str, metric: str = "error_rate", window_minutes: int = 30
    ) -> TrendAnalysis:
        """Get trend analysis for a specific operation and metric."""
        raw = await self.call_tool(
            "get_trend_analysis",
            {"operation_name": operation, "window_minutes": str(window_minutes)},
        )
        try:
            data = json.loads(raw)
            return TrendAnalysis.model_validate(data)
        except (json.JSONDecodeError, ValueError):
            return TrendAnalysis(operation=operation, metric=metric, summary=raw)

    async def get_cross_stack_context(self, operation: str) -> CrossStackContext:
        """Get cross-stack correlation context."""
        raw = await self.call_tool(
            "get_cross_stack_context",
            {"operation_name": operation},
        )
        try:
            data = json.loads(raw)
            return CrossStackContext.model_validate(data)
        except (json.JSONDecodeError, ValueError):
            return CrossStackContext(summary=raw)

    async def get_recent_agent_actions(self, limit: int = 10) -> list[RecentAgentAction]:
        """Get recent actions taken by the monitor agent."""
        raw = await self.call_tool(
            "get_recent_agent_actions",
            {"limit": str(limit)},
        )
        try:
            data = json.loads(raw)
            if isinstance(data, list):
                return [RecentAgentAction.model_validate(item) for item in data]
            return []
        except (json.JSONDecodeError, ValueError):
            # Text response — return as a single action summary
            if raw.strip() and "No recent" not in raw:
                return [RecentAgentAction(action="summary", details=raw[:500])]
            return []
