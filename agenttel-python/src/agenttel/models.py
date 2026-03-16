"""AgentTel data models."""

from __future__ import annotations

import time
from typing import Any

from pydantic import BaseModel, Field

from agenttel.enums import (
    AlertLevel,
    AnomalyPattern,
    BaselineConfidence,
    BaselineSource,
    ConsumptionPattern,
    DependencyCriticality,
    DependencyState,
    DependencyType,
    EscalationLevel,
    SloType,
)


class OperationBaseline(BaseModel):
    """Baseline metrics for an operation."""

    operation_name: str
    latency_p50_ms: float
    latency_p95_ms: float = 0.0
    latency_p99_ms: float
    error_rate: float = 0.0
    source: BaselineSource = BaselineSource.STATIC
    confidence: BaselineConfidence = BaselineConfidence.HIGH
    sample_count: int = 0
    updated_at: float = Field(default_factory=time.time)


class DependencyDescriptor(BaseModel):
    """Describes a service dependency."""

    name: str
    type: DependencyType
    criticality: DependencyCriticality = DependencyCriticality.REQUIRED
    protocol: str | None = None
    timeout_ms: int = 5000
    circuit_breaker: bool = False
    fallback: str | None = None
    health_endpoint: str | None = None


class ConsumerDescriptor(BaseModel):
    """Describes a downstream consumer of this service."""

    name: str
    pattern: ConsumptionPattern = ConsumptionPattern.SYNC
    sla_latency_ms: int | None = None


class OperationContext(BaseModel):
    """Operational context for a service operation."""

    retryable: bool = False
    idempotent: bool = False
    runbook_url: str | None = None
    fallback_description: str | None = None
    escalation_level: EscalationLevel = EscalationLevel.NOTIFY_TEAM
    safe_to_restart: bool = True


class AnomalyResult(BaseModel):
    """Result of anomaly detection."""

    is_anomaly: bool
    score: float = 0.0
    z_score: float = 0.0
    pattern: AnomalyPattern | None = None
    baseline_mean: float = 0.0
    baseline_stddev: float = 0.0


class SloDefinition(BaseModel):
    """SLO definition."""

    name: str
    target: float
    type: SloType = SloType.AVAILABILITY


class SloStatus(BaseModel):
    """Current SLO status."""

    name: str
    target: float
    type: SloType
    total_requests: int = 0
    failed_requests: int = 0
    budget_remaining: float = 1.0
    burn_rate: float = 0.0
    alert_level: AlertLevel = AlertLevel.OK


class DependencyHealth(BaseModel):
    """Health status of a dependency."""

    name: str
    type: DependencyType
    criticality: DependencyCriticality
    state: DependencyState = DependencyState.UNKNOWN
    latency_ms: float = 0.0
    error_rate: float = 0.0
    last_check: float = Field(default_factory=time.time)


class OperationStats(BaseModel):
    """Aggregated operation statistics."""

    operation_name: str
    request_count: int = 0
    error_count: int = 0
    latency_p50_ms: float = 0.0
    latency_p95_ms: float = 0.0
    latency_p99_ms: float = 0.0
    latency_mean_ms: float = 0.0


class ServiceHealthSummary(BaseModel):
    """Overall service health summary."""

    service_name: str
    status: str = "HEALTHY"
    operations: list[OperationStats] = []
    dependencies: list[DependencyHealth] = []
    anomalies: list[AnomalyResult] = []
    slo_statuses: list[SloStatus] = []
    timestamp: float = Field(default_factory=time.time)


class DeploymentInfo(BaseModel):
    """Deployment metadata."""

    deployment_id: str | None = None
    version: str | None = None
    environment: str = "development"
    timestamp: float = Field(default_factory=time.time)
    commit_sha: str | None = None
    metadata: dict[str, Any] = {}
