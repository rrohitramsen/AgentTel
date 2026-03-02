from __future__ import annotations

from typing import Optional
from pydantic import BaseModel, Field


class LlmConfig(BaseModel):
    """Configuration for the LLM provider."""
    provider: str = "anthropic"
    model: str = "claude-sonnet-4-5-20250929"
    api_key: Optional[str] = Field(default=None, description="API key; falls back to ANTHROPIC_API_KEY env var")
    max_tokens: int = 4096
    temperature: float = 0.2


class McpConfig(BaseModel):
    """Configuration for the MCP backend connection."""
    host: str = "localhost"
    port: int = 8081
    protocol: str = "http"
    timeout_seconds: float = 30.0

    @property
    def base_url(self) -> str:
        return f"{self.protocol}://{self.host}:{self.port}"


class WatchConfig(BaseModel):
    """Configuration for the health-watch polling loop."""
    interval_seconds: float = 10.0
    degradation_threshold: float = 0.8
    consecutive_failures_before_alert: int = 2


class ActionsConfig(BaseModel):
    """Configuration for automated remediation actions."""
    auto_approve: list[str] = Field(
        default_factory=lambda: ["circuit_breaker", "cache_flush"]
    )
    require_approval: list[str] = Field(
        default_factory=lambda: ["rollback", "restart"]
    )
    dry_run: bool = False


class NotificationsConfig(BaseModel):
    """Configuration for outbound notifications."""
    webhook_url: Optional[str] = None
    timeout_seconds: float = 10.0


class LearningConfig(BaseModel):
    """Configuration for the incident-learning subsystem."""
    history_file: str = "./incident-history.json"
    max_entries: int = 1000


class TracingConfig(BaseModel):
    """Configuration for OpenTelemetry tracing."""
    enabled: bool = True
    otlp_endpoint: str = "http://localhost:4318"
    service_name: str = "agenttel.monitor"


class ImproveConfig(BaseModel):
    """Configuration for the Improve phase — instrument agent integration."""
    enabled: bool = True
    instrument_url: str = "http://localhost:8082"
    config_path: str = "./agenttel.yml"


class MonitorConfig(BaseModel):
    """Top-level configuration for the AgentTel Monitor agent."""
    llm: LlmConfig = Field(default_factory=LlmConfig)
    mcp: McpConfig = Field(default_factory=McpConfig)
    watch: WatchConfig = Field(default_factory=WatchConfig)
    actions: ActionsConfig = Field(default_factory=ActionsConfig)
    notifications: NotificationsConfig = Field(default_factory=NotificationsConfig)
    learning: LearningConfig = Field(default_factory=LearningConfig)
    tracing: TracingConfig = Field(default_factory=TracingConfig)
    improve: ImproveConfig = Field(default_factory=ImproveConfig)
