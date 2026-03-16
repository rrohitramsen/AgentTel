"""Tests for baseline providers."""

from agenttel.baseline.rolling import RollingWindow, RollingBaselineProvider
from agenttel.baseline.provider import StaticBaselineProvider
from agenttel.baseline.composite import CompositeBaselineProvider
from agenttel.enums import BaselineConfidence, BaselineSource
from agenttel.models import OperationBaseline


class TestRollingWindow:
    def test_empty_snapshot(self):
        window = RollingWindow(capacity=100)
        snap = window.snapshot()
        assert snap.sample_count == 0
        assert snap.mean == 0.0
        assert snap.confidence == BaselineConfidence.LOW

    def test_single_record(self):
        window = RollingWindow(capacity=100)
        window.record(50.0, False)
        snap = window.snapshot()
        assert snap.sample_count == 1
        assert snap.mean == 50.0
        assert snap.p50 == 50.0

    def test_multiple_records(self):
        window = RollingWindow(capacity=100)
        for i in range(100):
            window.record(float(i), False)
        snap = window.snapshot()
        assert snap.sample_count == 100
        assert 49 <= snap.mean <= 50
        assert snap.p50 > 0
        assert snap.p95 > snap.p50
        assert snap.p99 > snap.p95

    def test_error_rate(self):
        window = RollingWindow(capacity=100)
        for i in range(100):
            window.record(10.0, is_error=(i < 10))
        snap = window.snapshot()
        assert abs(snap.error_rate - 0.10) < 0.01

    def test_capacity_limit(self):
        window = RollingWindow(capacity=10)
        for i in range(20):
            window.record(float(i))
        assert window.size == 10

    def test_confidence_levels(self):
        window = RollingWindow(capacity=1000)
        for i in range(20):
            window.record(float(i))
        assert window.snapshot().confidence == BaselineConfidence.LOW

        for i in range(100):
            window.record(float(i))
        assert window.snapshot().confidence == BaselineConfidence.MEDIUM

        for i in range(200):
            window.record(float(i))
        assert window.snapshot().confidence == BaselineConfidence.HIGH


class TestRollingBaselineProvider:
    def test_no_baseline_below_min_samples(self):
        provider = RollingBaselineProvider(min_samples=10)
        for i in range(5):
            provider.record("GET /users", float(i))
        assert provider.get_baseline("GET /users") is None
        assert not provider.has_baseline("GET /users")

    def test_baseline_after_min_samples(self):
        provider = RollingBaselineProvider(min_samples=10)
        for i in range(20):
            provider.record("GET /users", float(i))
        baseline = provider.get_baseline("GET /users")
        assert baseline is not None
        assert baseline.operation_name == "GET /users"
        assert baseline.source == BaselineSource.ROLLING_7D
        assert baseline.latency_p50_ms > 0

    def test_multiple_operations(self):
        provider = RollingBaselineProvider(min_samples=5)
        for i in range(10):
            provider.record("op_a", float(i))
            provider.record("op_b", float(i * 2))
        assert provider.has_baseline("op_a")
        assert provider.has_baseline("op_b")
        assert len(provider.all_operations()) == 2


class TestStaticBaselineProvider:
    def test_register_and_retrieve(self):
        provider = StaticBaselineProvider()
        baseline = OperationBaseline(
            operation_name="GET /users",
            latency_p50_ms=50.0,
            latency_p99_ms=200.0,
        )
        provider.register(baseline)
        assert provider.has_baseline("GET /users")
        assert provider.get_baseline("GET /users") == baseline

    def test_missing_baseline(self):
        provider = StaticBaselineProvider()
        assert provider.get_baseline("missing") is None
        assert not provider.has_baseline("missing")


class TestCompositeBaselineProvider:
    def test_priority_ordering(self):
        static = StaticBaselineProvider()
        static.register(OperationBaseline(
            operation_name="op",
            latency_p50_ms=100.0,
            latency_p99_ms=500.0,
            source=BaselineSource.STATIC,
        ))

        rolling = RollingBaselineProvider(min_samples=5)
        for i in range(10):
            rolling.record("op", 50.0)

        # Rolling first, should win
        composite = CompositeBaselineProvider([rolling, static])
        baseline = composite.get_baseline("op")
        assert baseline is not None
        assert baseline.source == BaselineSource.ROLLING_7D

    def test_fallback_to_next_provider(self):
        static = StaticBaselineProvider()
        static.register(OperationBaseline(
            operation_name="op",
            latency_p50_ms=100.0,
            latency_p99_ms=500.0,
        ))

        rolling = RollingBaselineProvider(min_samples=100)
        # Not enough samples

        composite = CompositeBaselineProvider([rolling, static])
        baseline = composite.get_baseline("op")
        assert baseline is not None
        assert baseline.source == BaselineSource.STATIC
