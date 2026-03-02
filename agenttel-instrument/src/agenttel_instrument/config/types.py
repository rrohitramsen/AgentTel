from __future__ import annotations

from typing import Optional
from pydantic import BaseModel, Field


class ServerConfig(BaseModel):
    """Configuration for the MCP server."""
    host: str = "0.0.0.0"
    port: int = 8082


class BackendMcpConfig(BaseModel):
    """Connection to the Java AgentTel MCP server for baseline/health data."""
    host: str = "localhost"
    port: int = 8081
    protocol: str = "http"
    timeout_seconds: float = 30.0

    @property
    def base_url(self) -> str:
        return f"{self.protocol}://{self.host}:{self.port}"


class TracingConfig(BaseModel):
    """Configuration for OpenTelemetry tracing."""
    enabled: bool = True
    otlp_endpoint: str = "http://localhost:4318"
    service_name: str = "agenttel.instrument"


class InstrumentConfig(BaseModel):
    """Top-level configuration for the AgentTel Instrumentation server."""
    server: ServerConfig = Field(default_factory=ServerConfig)
    backend_mcp: BackendMcpConfig = Field(default_factory=BackendMcpConfig)
    tracing: TracingConfig = Field(default_factory=TracingConfig)
