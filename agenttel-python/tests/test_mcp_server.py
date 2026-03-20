"""Tests for MCP Server JSON-RPC 2.0 interface."""

import pytest

from agenttel.agent.mcp_server import (
    McpServer,
    McpToolRegistry,
    ToolDescriptor,
    _jsonrpc_result,
    _jsonrpc_error,
)


class TestMcpToolRegistry:
    def test_list_tools_returns_registered_tools(self):
        """Verify tools/list returns all registered tools."""
        registry = McpToolRegistry()

        registry.register(ToolDescriptor(
            name="get_health",
            description="Get service health",
            handler=lambda: {"status": "ok"},
        ))
        registry.register(ToolDescriptor(
            name="get_metrics",
            description="Get service metrics",
            handler=lambda: {},
        ))

        tools = registry.list_tools()
        assert len(tools) == 2

        names = [t["name"] for t in tools]
        assert "get_health" in names
        assert "get_metrics" in names

    def test_tools_call_success(self):
        """Verify calling a tool returns the expected result."""
        registry = McpToolRegistry()

        def greet_handler(name="World"):
            return {"greeting": f"Hello, {name}"}

        registry.register(ToolDescriptor(
            name="greet",
            description="Greet someone",
            handler=greet_handler,
            parameters={"name": {"type": "string"}},
        ))

        tool = registry.get_tool("greet")
        assert tool is not None
        result = tool.handler(name="Alice")
        assert result == {"greeting": "Hello, Alice"}

    def test_tools_call_error_handling(self):
        """Verify that tool handler errors are caught."""
        registry = McpToolRegistry()

        def failing_handler():
            raise ValueError("something went wrong")

        registry.register(ToolDescriptor(
            name="fail_tool",
            description="Always fails",
            handler=failing_handler,
        ))

        tool = registry.get_tool("fail_tool")
        assert tool is not None
        with pytest.raises(ValueError, match="something went wrong"):
            tool.handler()

    def test_unknown_tool_returns_none(self):
        """Verify that requesting an unknown tool returns None."""
        registry = McpToolRegistry()
        tool = registry.get_tool("nonexistent")
        assert tool is None

    def test_tool_descriptor_to_dict(self):
        """Verify ToolDescriptor serializes correctly."""
        desc = ToolDescriptor(
            name="get_topology",
            description="Get service topology",
            handler=lambda: {},
            parameters={"service": {"type": "string"}},
        )

        d = desc.to_dict()
        assert d["name"] == "get_topology"
        assert d["description"] == "Get service topology"
        assert d["inputSchema"]["type"] == "object"
        assert "service" in d["inputSchema"]["properties"]


class TestJsonRpcHelpers:
    def test_jsonrpc_result(self):
        """Verify JSON-RPC result envelope."""
        result = _jsonrpc_result(1, {"tools": []})
        assert result["jsonrpc"] == "2.0"
        assert result["id"] == 1
        assert result["result"] == {"tools": []}
        assert "error" not in result

    def test_jsonrpc_error(self):
        """Verify JSON-RPC error envelope."""
        result = _jsonrpc_error(1, -32601, "Unknown method")
        assert result["jsonrpc"] == "2.0"
        assert result["id"] == 1
        assert result["error"]["code"] == -32601
        assert result["error"]["message"] == "Unknown method"
        assert "result" not in result


@pytest.mark.asyncio
class TestMcpServerAsync:
    async def test_health_endpoint(self):
        """Verify /health returns ok status."""
        try:
            from aiohttp import web
            from aiohttp.test_utils import TestClient, TestServer
        except ImportError:
            pytest.skip("aiohttp not installed")

        registry = McpToolRegistry()
        server = McpServer(registry, port=0)

        # Create a test application manually
        app = web.Application()
        app.router.add_get("/health", server._handle_health)

        async with TestClient(TestServer(app)) as client:
            resp = await client.get("/health")
            assert resp.status == 200
            data = await resp.json()
            assert data["status"] == "ok"
