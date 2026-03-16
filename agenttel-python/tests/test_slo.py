"""Tests for SLO tracking."""

from agenttel.enums import AlertLevel, SloType
from agenttel.models import SloDefinition
from agenttel.slo.tracker import SloTracker


class TestSloTracker:
    def test_register_and_get_status(self):
        tracker = SloTracker()
        tracker.register(SloDefinition(name="availability", target=0.999, type=SloType.AVAILABILITY))
        status = tracker.get_status("availability")
        assert status is not None
        assert status.name == "availability"
        assert status.target == 0.999
        assert status.budget_remaining == 1.0
        assert status.alert_level == AlertLevel.OK

    def test_budget_consumption(self):
        tracker = SloTracker()
        tracker.register(SloDefinition(name="avail", target=0.99))  # 1% budget

        # 100 requests, 0 failures = full budget
        for _ in range(100):
            tracker.record_request("avail", is_failure=False)
        status = tracker.get_status("avail")
        assert status.budget_remaining == 1.0

    def test_budget_exhaustion(self):
        tracker = SloTracker()
        tracker.register(SloDefinition(name="avail", target=0.99))  # 1% budget

        # 100 requests, 2 failures = 200% of budget consumed
        for i in range(100):
            tracker.record_request("avail", is_failure=(i < 2))

        status = tracker.get_status("avail")
        assert status.budget_remaining <= 0.0
        assert status.alert_level == AlertLevel.CRITICAL

    def test_alert_levels(self):
        tracker = SloTracker()
        tracker.register(SloDefinition(name="test", target=0.99))

        # Simulate 5% budget remaining
        for i in range(10000):
            tracker.record_request("test", is_failure=(i < 95))

        status = tracker.get_status("test")
        assert status.alert_level in {AlertLevel.WARNING, AlertLevel.CRITICAL}

    def test_multiple_slos(self):
        tracker = SloTracker()
        tracker.register(SloDefinition(name="avail", target=0.999))
        tracker.register(SloDefinition(name="latency", target=0.95, type=SloType.LATENCY_P99))

        statuses = tracker.get_all_statuses()
        assert len(statuses) == 2

    def test_unknown_slo(self):
        tracker = SloTracker()
        assert tracker.get_status("nonexistent") is None

    def test_reset(self):
        tracker = SloTracker()
        tracker.register(SloDefinition(name="test", target=0.99))
        tracker.record_request("test", is_failure=True)
        tracker.reset("test")
        status = tracker.get_status("test")
        assert status.total_requests == 0
