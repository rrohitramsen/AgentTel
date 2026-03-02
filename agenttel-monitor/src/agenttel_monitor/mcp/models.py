from __future__ import annotations

from typing import Any, Optional
from pydantic import BaseModel, Field


# ---------- JSON-RPC envelope ----------

class JsonRpcRequest(BaseModel):
    """JSON-RPC 2.0 request envelope."""
    jsonrpc: str = "2.0"
    method: str = "tools/call"
    params: dict[str, Any] = Field(default_factory=dict)
    id: int = 1


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
    id: int = 1


# ---------- Tool content ----------

class ToolContent(BaseModel):
    """Single content block returned by an MCP tool."""
    type: str = "text"
    text: str = ""


class ToolResult(BaseModel):
    """Result wrapper returned inside a JSON-RPC response for tools/call."""
    content: list[ToolContent] = Field(default_factory=list)
    isError: bool = False


# ---------- Domain models (parsed from tool text) ----------

class OperationHealth(BaseModel):
    """Health snapshot for one operation / endpoint."""
    operation: str = ""
    status: str = "unknown"
    error_rate: float = 0.0
    latency_p99_ms: float = 0.0
    throughput_rpm: float = 0.0
    details: Optional[str] = None


class ServiceHealth(BaseModel):
    """Aggregated service health returned by get_service_health."""
    service: str = ""
    overall_status: str = "unknown"
    operations: list[OperationHealth] = Field(default_factory=list)
    timestamp: Optional[str] = None


class IncidentContext(BaseModel):
    """Contextual data for an ongoing or recent incident."""
    operation: str = ""
    timeline: list[dict[str, Any]] = Field(default_factory=list)
    related_changes: list[str] = Field(default_factory=list)
    error_samples: list[str] = Field(default_factory=list)
    summary: str = ""


class SloReport(BaseModel):
    """SLO compliance report."""
    operation: str = ""
    slo_target: float = 0.0
    current_value: float = 0.0
    budget_remaining: float = 0.0
    status: str = "unknown"
    details: str = ""


class ExecutiveSummary(BaseModel):
    """High-level executive summary of system state."""
    summary: str = ""
    top_issues: list[str] = Field(default_factory=list)
    recommendations: list[str] = Field(default_factory=list)


class RemediationAction(BaseModel):
    """A remediation action available for execution."""
    id: str = ""
    name: str = ""
    description: str = ""
    action_type: str = ""
    target: str = ""
    parameters: dict[str, Any] = Field(default_factory=dict)
    risk_level: str = "low"


class RemediationResult(BaseModel):
    """Result of executing a remediation action."""
    action_id: str = ""
    success: bool = False
    message: str = ""
    details: Optional[dict[str, Any]] = None


class TrendAnalysis(BaseModel):
    """Trend analysis data."""
    operation: str = ""
    metric: str = ""
    trend: str = ""
    data_points: list[dict[str, Any]] = Field(default_factory=list)
    summary: str = ""


class CrossStackContext(BaseModel):
    """Cross-stack correlation data."""
    correlations: list[dict[str, Any]] = Field(default_factory=list)
    summary: str = ""


class RecentAgentAction(BaseModel):
    """A recent action taken by the monitor agent."""
    timestamp: str = ""
    action: str = ""
    target: str = ""
    result: str = ""
    details: str = ""
