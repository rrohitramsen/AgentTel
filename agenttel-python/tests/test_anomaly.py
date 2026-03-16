"""Tests for anomaly detection."""

from agenttel.anomaly.detector import AnomalyDetector
from agenttel.anomaly.patterns import PatternMatcher
from agenttel.enums import AnomalyPattern, DependencyState, DependencyCriticality, DependencyType
from agenttel.models import AnomalyResult, DependencyHealth, OperationBaseline


class TestAnomalyDetector:
    def test_no_anomaly_within_threshold(self):
        detector = AnomalyDetector(z_score_threshold=3.0)
        result = detector.detect(100.0, 100.0, 10.0)
        assert not result.is_anomaly
        assert result.z_score == 0.0

    def test_anomaly_above_threshold(self):
        detector = AnomalyDetector(z_score_threshold=3.0)
        result = detector.detect(140.0, 100.0, 10.0)
        assert result.is_anomaly
        assert result.z_score == 4.0
        assert result.pattern == AnomalyPattern.LATENCY_DEGRADATION

    def test_negative_z_score_no_pattern(self):
        detector = AnomalyDetector(z_score_threshold=3.0)
        result = detector.detect(60.0, 100.0, 10.0)
        assert result.is_anomaly
        assert result.z_score == -4.0
        assert result.pattern is None  # Only positive z-scores get pattern

    def test_zero_stddev_no_anomaly(self):
        detector = AnomalyDetector()
        result = detector.detect(200.0, 100.0, 0.0)
        assert not result.is_anomaly

    def test_score_calculation(self):
        detector = AnomalyDetector(z_score_threshold=3.0)
        result = detector.detect(140.0, 100.0, 10.0)
        assert result.score == 1.0  # z_score=4, score = min(1.0, 4/4) = 1.0

        result = detector.detect(120.0, 100.0, 10.0)
        assert result.score == 0.5  # z_score=2, score = 2/4 = 0.5


class TestPatternMatcher:
    def test_latency_degradation(self):
        matcher = PatternMatcher(latency_degradation_factor=2.0)
        baseline = OperationBaseline(
            operation_name="test",
            latency_p50_ms=50.0,
            latency_p99_ms=200.0,
        )
        assert matcher.detect_latency_degradation(150.0, baseline) == AnomalyPattern.LATENCY_DEGRADATION
        assert matcher.detect_latency_degradation(80.0, baseline) is None

    def test_error_rate_spike(self):
        matcher = PatternMatcher(error_rate_spike_factor=5.0)
        assert matcher.detect_error_rate_spike(0.25, 0.01) == AnomalyPattern.ERROR_RATE_SPIKE
        assert matcher.detect_error_rate_spike(0.02, 0.01) is None

    def test_cascade_failure(self):
        matcher = PatternMatcher(cascade_failure_threshold=3)
        deps = [
            DependencyHealth(name=f"dep-{i}", type=DependencyType.DATABASE, criticality=DependencyCriticality.REQUIRED, state=DependencyState.FAILING)
            for i in range(3)
        ]
        assert matcher.detect_cascade_failure(deps) == AnomalyPattern.CASCADE_FAILURE

        deps_healthy = [
            DependencyHealth(name="dep-0", type=DependencyType.DATABASE, criticality=DependencyCriticality.REQUIRED, state=DependencyState.HEALTHY)
        ]
        assert matcher.detect_cascade_failure(deps_healthy) is None

    def test_sustained_degradation(self):
        matcher = PatternMatcher()
        results = [AnomalyResult(is_anomaly=True, score=0.8) for _ in range(5)]
        assert matcher.detect_sustained_degradation(results) == AnomalyPattern.SUSTAINED_DEGRADATION

        results_mixed = [
            AnomalyResult(is_anomaly=True, score=0.8),
            AnomalyResult(is_anomaly=False, score=0.1),
            AnomalyResult(is_anomaly=True, score=0.8),
            AnomalyResult(is_anomaly=False, score=0.1),
            AnomalyResult(is_anomaly=True, score=0.8),
        ]
        assert matcher.detect_sustained_degradation(results_mixed) is None
