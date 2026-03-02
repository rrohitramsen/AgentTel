from __future__ import annotations

from typing import Any, Optional
from pydantic import BaseModel, Field


# ---------- JSON-RPC envelope ----------

class JsonRpcRequest(BaseModel):
    """JSON-RPC 2.0 request envelope."""
    jsonrpc: str = "2.0"
    method: str = "tools/call"
    params: dict[str, Any] = Field(default_factory=dict)
    id: int | str = 1


class JsonRpcError(BaseModel):
    """JSON-RPC 2.0 error object."""
    code: int
    message: str
    data: Optional[Any] = None


class JsonRpcResponse(BaseModel):
    """JSON-RPC 2.0 response envelope."""
    jsonrpc: str = "2.0"
    result: Optional[Any] = None
    error: Optional[JsonRpcError] = None
    id: int | str = 1


# ---------- Tool definitions ----------

class ToolParameter(BaseModel):
    """Parameter definition for an MCP tool."""
    type: str = "string"
    description: str = ""
    required: bool = False


class ToolDefinition(BaseModel):
    """MCP tool definition."""
    name: str
    description: str
    inputSchema: dict[str, Any] = Field(default_factory=dict)


# ---------- Tool result ----------

class ToolContent(BaseModel):
    """Single content block returned by an MCP tool."""
    type: str = "text"
    text: str = ""


class ToolResult(BaseModel):
    """Result wrapper for tools/call responses."""
    content: list[ToolContent] = Field(default_factory=list)
    isError: bool = False
