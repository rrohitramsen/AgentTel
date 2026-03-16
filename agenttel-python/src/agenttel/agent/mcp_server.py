"""MCP server exposing AgentTel tools over JSON-RPC 2.0."""

from __future__ import annotations

import json
import logging
from typing import Any, Callable

logger = logging.getLogger("agenttel.mcp")


# Tool descriptor for MCP
class ToolDescriptor:
    """Describes an MCP tool."""

    def __init__(
        self,
        name: str,
        description: str,
        handler: Callable[..., Any],
        parameters: dict[str, Any] | None = None,
    ) -> None:
        self.name = name
        self.description = description
        self.handler = handler
        self.parameters = parameters or {}

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "inputSchema": {
                "type": "object",
                "properties": self.parameters,
            },
        }


class McpToolRegistry:
    """Registry of MCP tools."""

    def __init__(self) -> None:
        self._tools: dict[str, ToolDescriptor] = {}

    def register(self, tool: ToolDescriptor) -> None:
        self._tools[tool.name] = tool

    def get_tool(self, name: str) -> ToolDescriptor | None:
        return self._tools.get(name)

    def list_tools(self) -> list[dict[str, Any]]:
        return [t.to_dict() for t in self._tools.values()]


class McpServer:
    """Async HTTP server exposing tools over JSON-RPC 2.0.

    Endpoints:
      POST /mcp — JSON-RPC 2.0 handler
      GET /mcp/docs — List available tools
      GET /health — Health check
    """

    def __init__(
        self,
        tool_registry: McpToolRegistry,
        host: str = "0.0.0.0",
        port: int = 8091,
    ) -> None:
        self._registry = tool_registry
        self._host = host
        self._port = port
        self._app = None

    async def start(self) -> None:
        """Start the MCP server."""
        try:
            from aiohttp import web
        except ImportError:
            logger.error("aiohttp is required for MCP server. Install with: pip install agenttel[agent]")
            return

        app = web.Application()
        app.router.add_post("/mcp", self._handle_jsonrpc)
        app.router.add_get("/mcp/docs", self._handle_docs)
        app.router.add_get("/health", self._handle_health)
        self._app = app

        runner = web.AppRunner(app)
        await runner.setup()
        site = web.TCPSite(runner, self._host, self._port)
        await site.start()
        logger.info("MCP server started on %s:%d", self._host, self._port)

    async def _handle_jsonrpc(self, request: Any) -> Any:
        """Handle JSON-RPC 2.0 requests."""
        from aiohttp import web

        try:
            body = await request.json()
        except json.JSONDecodeError:
            return web.json_response(
                _jsonrpc_error(None, -32700, "Parse error"), status=400
            )

        method = body.get("method")
        params = body.get("params", {})
        req_id = body.get("id")

        if method == "tools/list":
            result = {"tools": self._registry.list_tools()}
            return web.json_response(_jsonrpc_result(req_id, result))

        if method == "tools/call":
            tool_name = params.get("name")
            tool_args = params.get("arguments", {})
            tool = self._registry.get_tool(tool_name)

            if tool is None:
                return web.json_response(
                    _jsonrpc_error(req_id, -32601, f"Unknown tool: {tool_name}")
                )

            try:
                result = tool.handler(**tool_args)
                return web.json_response(
                    _jsonrpc_result(req_id, {"content": [{"type": "text", "text": json.dumps(result, default=str)}]})
                )
            except Exception as exc:
                logger.error("Tool %s failed: %s", tool_name, exc)
                return web.json_response(
                    _jsonrpc_error(req_id, -32000, str(exc))
                )

        return web.json_response(
            _jsonrpc_error(req_id, -32601, f"Unknown method: {method}")
        )

    async def _handle_docs(self, request: Any) -> Any:
        """Return tool documentation."""
        from aiohttp import web

        return web.json_response({"tools": self._registry.list_tools()})

    async def _handle_health(self, request: Any) -> Any:
        """Health check endpoint."""
        from aiohttp import web

        return web.json_response({"status": "ok"})


def _jsonrpc_result(req_id: Any, result: Any) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": req_id, "result": result}


def _jsonrpc_error(req_id: Any, code: int, message: str) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": req_id, "error": {"code": code, "message": message}}


def create_default_tools(engine: Any) -> McpToolRegistry:
    """Create the default set of 15 MCP tools from an AgentTelEngine.

    Args:
        engine: AgentTelEngine instance.

    Returns:
        McpToolRegistry with all built-in tools registered.
    """
    from agenttel.agent.health import ServiceHealthAggregator

    registry = McpToolRegistry()

    # We need a health aggregator
    health_agg = ServiceHealthAggregator(service_name="")

    registry.register(ToolDescriptor(
        name="get_service_health",
        description="Current health status with latency/error stats",
        handler=lambda: health_agg.get_summary().model_dump(),
    ))

    registry.register(ToolDescriptor(
        name="get_operation_baselines",
        description="Rolling baselines for all operations",
        handler=lambda: {
            op: engine.baseline_provider.get_baseline(op).model_dump()
            for op in engine.rolling_baselines.all_operations()
            if engine.baseline_provider.get_baseline(op)
        },
    ))

    registry.register(ToolDescriptor(
        name="get_anomalies",
        description="Currently detected anomalies",
        handler=lambda: [],
    ))

    registry.register(ToolDescriptor(
        name="get_slo_status",
        description="SLO compliance and budget remaining",
        handler=lambda: [s.model_dump() for s in engine.slo_tracker.get_all_statuses()],
    ))

    registry.register(ToolDescriptor(
        name="get_dependency_health",
        description="Dependency states and latency",
        handler=lambda: {
            name: state.value
            for name, state in engine.causality_tracker.get_all_states().items()
        },
    ))

    registry.register(ToolDescriptor(
        name="get_topology",
        description="Service topology map",
        handler=lambda: {
            "team": engine.topology.team,
            "tier": engine.topology.tier,
            "domain": engine.topology.domain,
            "dependencies": [d.model_dump() for d in engine.topology.get_all_dependencies()],
            "consumers": [c.model_dump() for c in engine.topology.get_all_consumers()],
        },
    ))

    registry.register(ToolDescriptor(
        name="get_incident_context",
        description="Full incident context for diagnosis",
        handler=lambda: {"status": "no active incident"},
    ))

    registry.register(ToolDescriptor(
        name="get_error_classification",
        description="Recent error categories and counts",
        handler=lambda: {},
    ))

    registry.register(ToolDescriptor(
        name="get_deployment_info",
        description="Current deployment metadata",
        handler=lambda: engine.config.deployment.model_dump(),
    ))

    registry.register(ToolDescriptor(
        name="suggest_remediation",
        description="Remediation suggestions based on state",
        handler=lambda: [],
    ))

    registry.register(ToolDescriptor(
        name="execute_remediation",
        description="Execute a remediation action (with permission)",
        handler=lambda action="", approved=False: {
            "error": "No remediation actions registered"
        },
        parameters={"action": {"type": "string"}, "approved": {"type": "boolean"}},
    ))

    registry.register(ToolDescriptor(
        name="get_change_correlation",
        description="Correlate incidents to recent changes",
        handler=lambda: [],
    ))

    registry.register(ToolDescriptor(
        name="get_slo_report",
        description="SLO compliance report",
        handler=lambda: {},
    ))

    registry.register(ToolDescriptor(
        name="get_trend_analysis",
        description="Trend analysis over time window",
        handler=lambda metric="": {},
        parameters={"metric": {"type": "string"}},
    ))

    registry.register(ToolDescriptor(
        name="get_executive_summary",
        description="Executive-level incident summary",
        handler=lambda: {
            "service": health_agg.get_summary().model_dump(),
            "slos": [s.model_dump() for s in engine.slo_tracker.get_all_statuses()],
        },
    ))

    return registry
