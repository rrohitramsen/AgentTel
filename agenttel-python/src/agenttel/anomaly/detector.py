"""Anomaly detection using z-score analysis."""

from __future__ import annotations

from agenttel.enums import AnomalyPattern
from agenttel.models import AnomalyResult


class AnomalyDetector:
    """Detects anomalies using z-score analysis."""

    def __init__(self, z_score_threshold: float = 3.0) -> None:
        self._threshold = z_score_threshold

    @property
    def threshold(self) -> float:
        return self._threshold

    def detect(
        self,
        current_value: float,
        baseline_mean: float,
        baseline_stddev: float,
    ) -> AnomalyResult:
        """Detect if the current value is anomalous relative to baseline.

        Args:
            current_value: The observed value.
            baseline_mean: Mean of the baseline distribution.
            baseline_stddev: Standard deviation of the baseline.

        Returns:
            AnomalyResult with detection details.
        """
        if baseline_stddev <= 0:
            return AnomalyResult(
                is_anomaly=False,
                score=0.0,
                z_score=0.0,
                baseline_mean=baseline_mean,
                baseline_stddev=baseline_stddev,
            )

        z_score = (current_value - baseline_mean) / baseline_stddev
        score = min(1.0, abs(z_score) / 4.0)
        is_anomaly = abs(z_score) >= self._threshold

        pattern = None
        if is_anomaly and z_score > 0:
            pattern = AnomalyPattern.LATENCY_DEGRADATION

        return AnomalyResult(
            is_anomaly=is_anomaly,
            score=score,
            z_score=z_score,
            pattern=pattern,
            baseline_mean=baseline_mean,
            baseline_stddev=baseline_stddev,
        )
