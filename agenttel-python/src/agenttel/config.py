"""AgentTel configuration loader."""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any

import yaml
from pydantic import BaseModel, Field, field_validator

from agenttel.enums import (
    ConsumptionPattern,
    DependencyCriticality,
    DependencyType,
    EscalationLevel,
    ServiceTier,
    SloType,
)


class TopologyConfig(BaseModel):
    """Service topology configuration."""

    team: str = ""
    tier: ServiceTier = ServiceTier.STANDARD
    domain: str = ""
    on_call_channel: str = ""
    service_version: str = ""

    model_config = {"populate_by_name": True}


class DependencyConfig(BaseModel):
    """Dependency configuration."""

    name: str
    type: DependencyType = DependencyType.INTERNAL_SERVICE
    criticality: DependencyCriticality = DependencyCriticality.REQUIRED
    protocol: str | None = None
    timeout_ms: int = Field(default=5000, alias="timeout-ms")
    circuit_breaker: bool = Field(default=False, alias="circuit-breaker")
    fallback: str | None = None
    health_endpoint: str | None = Field(default=None, alias="health-endpoint")

    model_config = {"populate_by_name": True}


class ConsumerConfig(BaseModel):
    """Consumer configuration."""

    name: str
    pattern: ConsumptionPattern = ConsumptionPattern.SYNC
    sla_latency_ms: int | None = Field(default=None, alias="sla-latency-ms")

    model_config = {"populate_by_name": True}


class ProfileConfig(BaseModel):
    """Operation profile configuration."""

    retryable: bool = False
    idempotent: bool = False
    escalation_level: EscalationLevel = Field(
        default=EscalationLevel.NOTIFY_TEAM, alias="escalation-level"
    )
    safe_to_restart: bool = Field(default=True, alias="safe-to-restart")
    fallback_description: str | None = Field(default=None, alias="fallback-description")

    model_config = {"populate_by_name": True}


class OperationConfig(BaseModel):
    """Per-operation configuration."""

    profile: str | None = None
    expected_latency_p50: str | None = Field(default=None, alias="expected-latency-p50")
    expected_latency_p99: str | None = Field(default=None, alias="expected-latency-p99")
    expected_error_rate: float | None = Field(default=None, alias="expected-error-rate")
    runbook_url: str | None = Field(default=None, alias="runbook-url")
    retryable: bool | None = None
    idempotent: bool | None = None

    model_config = {"populate_by_name": True}

    def parse_latency_ms(self, value: str | None) -> float | None:
        """Parse latency string like '80ms' to float."""
        if value is None:
            return None
        v = value.strip().lower()
        if v.endswith("ms"):
            return float(v[:-2])
        if v.endswith("s"):
            return float(v[:-1]) * 1000
        return float(v)

    @property
    def latency_p50_ms(self) -> float | None:
        return self.parse_latency_ms(self.expected_latency_p50)

    @property
    def latency_p99_ms(self) -> float | None:
        return self.parse_latency_ms(self.expected_latency_p99)


class BaselineConfig(BaseModel):
    """Baseline configuration."""

    rolling_window_size: int = Field(default=1000, alias="rolling-window-size")
    rolling_min_samples: int = Field(default=10, alias="rolling-min-samples")

    model_config = {"populate_by_name": True}


class AnomalyDetectionConfig(BaseModel):
    """Anomaly detection configuration."""

    enabled: bool = True
    z_score_threshold: float = Field(default=3.0, alias="z-score-threshold")

    model_config = {"populate_by_name": True}


class SloConfig(BaseModel):
    """SLO configuration."""

    name: str = ""
    target: float = 0.999
    type: SloType = SloType.AVAILABILITY


class DeploymentConfig(BaseModel):
    """Deployment configuration."""

    emit_on_startup: bool = Field(default=True, alias="emit-on-startup")
    deployment_id: str | None = Field(default=None, alias="deployment-id")
    version: str | None = None
    environment: str = "development"

    model_config = {"populate_by_name": True}


class McpConfig(BaseModel):
    """MCP server configuration."""

    enabled: bool = False
    port: int = 8091
    host: str = "0.0.0.0"


class AgentRoleConfig(BaseModel):
    """Agent role permissions configuration."""

    tools: list[str] = []


class AgentTelConfig(BaseModel):
    """Root AgentTel configuration."""

    enabled: bool = True
    topology: TopologyConfig = TopologyConfig()
    dependencies: list[DependencyConfig] = []
    consumers: list[ConsumerConfig] = []
    profiles: dict[str, ProfileConfig] = {}
    operations: dict[str, OperationConfig] = {}
    baselines: BaselineConfig = BaselineConfig()
    anomaly_detection: AnomalyDetectionConfig = Field(
        default=AnomalyDetectionConfig(), alias="anomaly-detection"
    )
    slo: dict[str, SloConfig] = {}
    deployment: DeploymentConfig = DeploymentConfig()
    mcp: McpConfig = McpConfig()
    agent_roles: dict[str, AgentRoleConfig] = Field(
        default={}, alias="agent-roles"
    )

    model_config = {"populate_by_name": True}

    @classmethod
    def from_yaml(cls, path: str | Path) -> AgentTelConfig:
        """Load configuration from a YAML file."""
        path = Path(path)
        if not path.exists():
            return cls()

        with open(path) as f:
            data = yaml.safe_load(f) or {}

        # Support both top-level and nested under 'agenttel' key
        if "agenttel" in data:
            data = data["agenttel"]

        return cls.model_validate(data)

    @classmethod
    def from_env(cls) -> AgentTelConfig:
        """Load configuration from environment or default paths."""
        config_path = os.environ.get("AGENTTEL_CONFIG", "agenttel.yml")
        return cls.from_yaml(config_path)
