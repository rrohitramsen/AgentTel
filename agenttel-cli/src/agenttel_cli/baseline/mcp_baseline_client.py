"""MCP baseline client — queries an MCP server for observed latency baselines."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import httpx
import yaml


@dataclass(frozen=True)
class BaselineSuggestion:
    """A suggested baseline for a single operation."""

    operation: str
    p50_ms: float
    p99_ms: float


class McpBaselineClient:
    """Connects to an MCP server over HTTP and retrieves observed baselines.

    Parameters
    ----------
    mcp_url:
        Base URL of the MCP server (e.g. ``http://localhost:8081``).
    timeout:
        HTTP request timeout in seconds.
    """

    def __init__(self, mcp_url: str, timeout: float = 30.0) -> None:
        self._mcp_url = mcp_url.rstrip("/")
        self._timeout = timeout

    def fetch_baselines(self, service_name: str | None = None) -> list[BaselineSuggestion]:
        """Fetch observed baselines from the MCP server.

        Calls the ``get_service_health`` and ``get_trend_analysis`` MCP tools
        to extract P50/P99 latency data per operation.

        Parameters
        ----------
        service_name:
            Optional service name filter. When ``None``, returns baselines
            for all available services.

        Returns
        -------
        list[BaselineSuggestion]
            Suggested baselines derived from observed metrics.
        """
        suggestions: list[BaselineSuggestion] = []

        # Fetch service health data.
        health_data = self._call_tool("get_service_health", service_name=service_name)
        suggestions.extend(self._parse_health_data(health_data))

        # Fetch trend analysis for finer-grained percentile data.
        trend_data = self._call_tool("get_trend_analysis", service_name=service_name)
        suggestions = self._merge_trend_data(suggestions, trend_data)

        return suggestions

    def apply_baselines_to_config(
        self,
        config_path: Path,
        suggestions: list[BaselineSuggestion],
    ) -> str:
        """Apply baseline suggestions to an existing agenttel.yml and return updated YAML.

        Parameters
        ----------
        config_path:
            Path to the current agenttel.yml.
        suggestions:
            Baselines to apply.

        Returns
        -------
        str
            Updated YAML string with baselines filled in.
        """
        raw = config_path.read_text(encoding="utf-8")
        config = yaml.safe_load(raw)

        if not isinstance(config, dict):
            return raw

        suggestion_map = {s.operation: s for s in suggestions}

        for op in config.get("operations", []):
            name = op.get("name")
            if name and name in suggestion_map:
                suggestion = suggestion_map[name]
                if "baseline" not in op:
                    op["baseline"] = {}
                op["baseline"]["p50"] = f"{suggestion.p50_ms}ms"
                op["baseline"]["p99"] = f"{suggestion.p99_ms}ms"

        return yaml.dump(config, default_flow_style=False, sort_keys=False, allow_unicode=True)

    # ------------------------------------------------------------------
    # MCP communication
    # ------------------------------------------------------------------

    def _call_tool(self, tool_name: str, **kwargs: Any) -> dict[str, Any]:
        """Call an MCP tool via HTTP POST.

        The MCP server is expected to expose a ``/tools/call`` endpoint
        that accepts JSON-RPC-style requests.

        Parameters
        ----------
        tool_name:
            Name of the MCP tool to invoke.
        **kwargs:
            Arguments forwarded to the tool.

        Returns
        -------
        dict
            The ``result`` field of the MCP response.

        Raises
        ------
        McpClientError
            If the MCP server returns an error or is unreachable.
        """
        # Filter out None values from kwargs.
        arguments = {k: v for k, v in kwargs.items() if v is not None}

        payload = {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "tools/call",
            "params": {
                "name": tool_name,
                "arguments": arguments,
            },
        }

        try:
            with httpx.Client(timeout=self._timeout) as client:
                response = client.post(
                    f"{self._mcp_url}/mcp",
                    json=payload,
                    headers={"Content-Type": "application/json"},
                )
                response.raise_for_status()
        except httpx.HTTPError as exc:
            raise McpClientError(f"MCP request to '{tool_name}' failed: {exc}") from exc

        body = response.json()
        if "error" in body:
            raise McpClientError(f"MCP tool '{tool_name}' returned error: {body['error']}")

        return body.get("result", {})

    # ------------------------------------------------------------------
    # Response parsing
    # ------------------------------------------------------------------

    @staticmethod
    def _parse_health_data(data: dict[str, Any]) -> list[BaselineSuggestion]:
        """Extract baseline suggestions from ``get_service_health`` response."""
        suggestions: list[BaselineSuggestion] = []

        # The health response is expected to contain a list of content blocks.
        content = data.get("content", [])
        for block in content:
            if block.get("type") != "text":
                continue
            text = block.get("text", "")
            # Attempt to parse embedded YAML/JSON metric data.
            try:
                metrics = yaml.safe_load(text)
            except yaml.YAMLError:
                continue

            if not isinstance(metrics, dict):
                continue

            operations = metrics.get("operations", metrics.get("endpoints", []))
            for op in operations if isinstance(operations, list) else []:
                name = op.get("name", op.get("operation"))
                p50 = op.get("p50", op.get("p50_ms"))
                p99 = op.get("p99", op.get("p99_ms"))
                if name and p50 is not None and p99 is not None:
                    suggestions.append(
                        BaselineSuggestion(
                            operation=name,
                            p50_ms=float(p50),
                            p99_ms=float(p99),
                        )
                    )

        return suggestions

    @staticmethod
    def _merge_trend_data(
        suggestions: list[BaselineSuggestion],
        trend_data: dict[str, Any],
    ) -> list[BaselineSuggestion]:
        """Merge trend analysis data into existing suggestions.

        If the trend data provides more recent percentile values, those take
        precedence over the health-based values.
        """
        trend_map: dict[str, BaselineSuggestion] = {}

        content = trend_data.get("content", [])
        for block in content:
            if block.get("type") != "text":
                continue
            try:
                parsed = yaml.safe_load(block.get("text", ""))
            except yaml.YAMLError:
                continue

            if not isinstance(parsed, dict):
                continue

            trends = parsed.get("trends", parsed.get("operations", []))
            for trend in trends if isinstance(trends, list) else []:
                name = trend.get("name", trend.get("operation"))
                p50 = trend.get("p50", trend.get("p50_ms"))
                p99 = trend.get("p99", trend.get("p99_ms"))
                if name and p50 is not None and p99 is not None:
                    trend_map[name] = BaselineSuggestion(
                        operation=name,
                        p50_ms=float(p50),
                        p99_ms=float(p99),
                    )

        if not trend_map:
            return suggestions

        # Merge: trend data overrides health data for matching operations.
        merged: dict[str, BaselineSuggestion] = {s.operation: s for s in suggestions}
        merged.update(trend_map)
        return list(merged.values())


class McpClientError(Exception):
    """Raised when communication with the MCP server fails."""
