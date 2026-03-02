from __future__ import annotations

import collections
import json
import logging
from pathlib import Path
from typing import Any, Callable, Awaitable

import yaml
from aiohttp import web

from .models import (
    JsonRpcRequest,
    JsonRpcResponse,
    JsonRpcError,
    ToolDefinition,
    ToolContent,
    ToolResult,
)

logger = logging.getLogger(__name__)

ToolHandler = Callable[[dict[str, str]], Awaitable[str]]


class BufferedLogHandler(logging.Handler):
    """Captures log records into an in-memory ring buffer for the /logs endpoint."""

    def __init__(self, buffer: collections.deque) -> None:
        super().__init__()
        self._buffer = buffer

    def emit(self, record: logging.LogRecord) -> None:
        self._buffer.append(self.format(record))


class McpServer:
    """Python MCP server exposing tools over JSON-RPC 2.0 / HTTP.

    Mirrors the architecture of the Java McpServer in agenttel-agent:
    - POST /mcp   — JSON-RPC 2.0 (initialize, tools/list, tools/call)
    - GET  /health — Server health check
    - GET  /logs   — Recent log lines (ring buffer)
    - GET  /config — Managed agenttel.yml config
    """

    def __init__(
        self,
        host: str = "0.0.0.0",
        port: int = 8082,
        agenttel_config_path: str | None = None,
    ):
        self._host = host
        self._port = port
        self._agenttel_config_path = agenttel_config_path or "/app/agenttel.yml"
        self._tools: dict[str, ToolDefinition] = {}
        self._handlers: dict[str, ToolHandler] = {}
        self._app = web.Application()
        self._app.router.add_post("/mcp", self._handle_mcp)
        self._app.router.add_get("/health", self._handle_health)
        self._app.router.add_get("/logs", self._handle_logs)
        self._app.router.add_get("/config", self._handle_config)

        # In-memory log buffer
        self._log_buffer: collections.deque[str] = collections.deque(maxlen=200)
        handler = BufferedLogHandler(self._log_buffer)
        handler.setFormatter(logging.Formatter("%(asctime)s [%(name)s] %(levelname)s: %(message)s"))
        logging.getLogger("agenttel_instrument").addHandler(handler)

    def register_tool(
        self,
        definition: ToolDefinition,
        handler: ToolHandler,
    ) -> None:
        self._tools[definition.name] = definition
        self._handlers[definition.name] = handler
        logger.info("Registered tool: %s", definition.name)

    async def start(self) -> None:
        runner = web.AppRunner(self._app)
        await runner.setup()
        site = web.TCPSite(runner, self._host, self._port)
        await site.start()
        logger.info(
            "MCP server listening on %s:%d with %d tools",
            self._host, self._port, len(self._tools),
        )

    async def _handle_health(self, request: web.Request) -> web.Response:
        return web.json_response({
            "status": "ok",
            "tools": len(self._tools),
        })

    async def _handle_logs(self, request: web.Request) -> web.Response:
        return web.json_response({"logs": list(self._log_buffer)})

    async def _handle_config(self, request: web.Request) -> web.Response:
        try:
            raw = Path(self._agenttel_config_path).read_text(encoding="utf-8")
            config = yaml.safe_load(raw)
            return web.json_response({"config_path": self._agenttel_config_path, "config": config})
        except Exception as e:
            return web.json_response({"error": str(e)}, status=500)

    async def _handle_mcp(self, request: web.Request) -> web.Response:
        try:
            body = await request.json()
        except json.JSONDecodeError:
            return self._error_response(-32700, "Parse error", 0)

        try:
            rpc = JsonRpcRequest.model_validate(body)
        except Exception:
            return self._error_response(-32600, "Invalid request", body.get("id", 0))

        method = rpc.method
        request_id = rpc.id

        if method == "initialize":
            return self._json_response(request_id, {
                "protocolVersion": "2024-11-05",
                "capabilities": {"tools": {"listChanged": False}},
                "serverInfo": {
                    "name": "agenttel-instrument",
                    "version": "0.1.0",
                },
            })

        if method == "tools/list":
            tools_list = [
                tool.model_dump() for tool in self._tools.values()
            ]
            return self._json_response(request_id, {"tools": tools_list})

        if method == "tools/call":
            return await self._handle_tool_call(rpc.params, request_id)

        return self._error_response(-32601, f"Method not found: {method}", request_id)

    async def _handle_tool_call(
        self, params: dict[str, Any], request_id: int | str,
    ) -> web.Response:
        tool_name = params.get("name", "")
        arguments = params.get("arguments", {})

        handler = self._handlers.get(tool_name)
        if not handler:
            return self._error_response(
                -32602, f"Unknown tool: {tool_name}", request_id,
            )

        try:
            result_text = await handler(arguments)
            tool_result = ToolResult(
                content=[ToolContent(type="text", text=result_text)],
                isError=False,
            )
        except Exception as e:
            logger.exception("Tool %s failed", tool_name)
            tool_result = ToolResult(
                content=[ToolContent(type="text", text=f"Error: {e}")],
                isError=True,
            )

        return self._json_response(request_id, tool_result.model_dump())

    def _json_response(
        self, request_id: int | str, result: Any,
    ) -> web.Response:
        resp = JsonRpcResponse(
            jsonrpc="2.0", result=result, id=request_id,
        )
        return web.json_response(resp.model_dump())

    def _error_response(
        self, code: int, message: str, request_id: int | str,
    ) -> web.Response:
        resp = JsonRpcResponse(
            jsonrpc="2.0",
            error=JsonRpcError(code=code, message=message),
            id=request_id,
        )
        return web.json_response(resp.model_dump())
