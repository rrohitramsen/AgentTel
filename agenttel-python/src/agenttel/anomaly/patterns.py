"""Pattern matching for anomaly classification."""

from __future__ import annotations

from agenttel.enums import AnomalyPattern, DependencyState
from agenttel.models import AnomalyResult, DependencyHealth, OperationBaseline


class PatternMatcher:
    """Detects specific anomaly patterns from operational data."""

    def __init__(
        self,
        latency_degradation_factor: float = 2.0,
        error_rate_spike_factor: float = 5.0,
        cascade_failure_threshold: int = 3,
    ) -> None:
        self._latency_factor = latency_degradation_factor
        self._error_factor = error_rate_spike_factor
        self._cascade_threshold = cascade_failure_threshold

    def detect_latency_degradation(
        self,
        current_latency_ms: float,
        baseline: OperationBaseline,
    ) -> AnomalyPattern | None:
        """Detect if latency exceeds threshold relative to P50."""
        if baseline.latency_p50_ms > 0 and current_latency_ms > baseline.latency_p50_ms * self._latency_factor:
            return AnomalyPattern.LATENCY_DEGRADATION
        return None

    def detect_error_rate_spike(
        self,
        current_error_rate: float,
        baseline_error_rate: float,
    ) -> AnomalyPattern | None:
        """Detect if error rate exceeds threshold relative to baseline."""
        if baseline_error_rate > 0 and current_error_rate > baseline_error_rate * self._error_factor:
            return AnomalyPattern.ERROR_RATE_SPIKE
        if baseline_error_rate == 0 and current_error_rate > 0.05:
            return AnomalyPattern.ERROR_RATE_SPIKE
        return None

    def detect_cascade_failure(
        self,
        dependencies: list[DependencyHealth],
    ) -> AnomalyPattern | None:
        """Detect cascade failure when multiple dependencies are failing."""
        failing_count = sum(
            1 for d in dependencies if d.state == DependencyState.FAILING
        )
        if failing_count >= self._cascade_threshold:
            return AnomalyPattern.CASCADE_FAILURE
        return None

    def detect_sustained_degradation(
        self,
        recent_anomaly_results: list[AnomalyResult],
        window_size: int = 5,
    ) -> AnomalyPattern | None:
        """Detect sustained degradation over multiple observations."""
        if len(recent_anomaly_results) < window_size:
            return None
        recent = recent_anomaly_results[-window_size:]
        anomaly_count = sum(1 for r in recent if r.is_anomaly)
        if anomaly_count >= window_size - 1:
            return AnomalyPattern.SUSTAINED_DEGRADATION
        return None
