"""Tests for Agent/MCP interface."""

from agenttel.agent.health import ServiceHealthAggregator
from agenttel.agent.identity import AgentIdentity, ToolPermissionRegistry
from agenttel.agent.remediation import RemediationRegistry, RemediationExecutor
from agenttel.agent.reporting import SloReportGenerator, TrendAnalyzer
from agenttel.enums import AgentRole, DependencyState, SloType
from agenttel.models import SloDefinition
from agenttel.slo.tracker import SloTracker


class TestServiceHealthAggregator:
    def test_record_and_summary(self):
        agg = ServiceHealthAggregator(service_name="test-service")
        for i in range(100):
            agg.record_operation("GET /users", float(i), is_error=(i < 5))

        summary = agg.get_summary()
        assert summary.service_name == "test-service"
        assert len(summary.operations) == 1
        assert summary.operations[0].request_count == 100
        assert summary.operations[0].error_count == 5

    def test_health_status_calculation(self):
        agg = ServiceHealthAggregator(service_name="test")
        for i in range(100):
            agg.record_operation("op", 10.0, is_error=False)
        assert agg.get_summary().status == "HEALTHY"

    def test_critical_with_high_errors(self):
        agg = ServiceHealthAggregator(service_name="test")
        for i in range(100):
            agg.record_operation("op", 10.0, is_error=(i < 20))
        assert agg.get_summary().status == "CRITICAL"


class TestAgentIdentity:
    def test_from_headers(self):
        headers = {
            "x-agent-id": "agent-1",
            "x-agent-role": "diagnostician",
            "x-agent-session-id": "session-1",
            "x-agent-name": "test-agent",
        }
        identity = AgentIdentity.from_headers(headers)
        assert identity.id == "agent-1"
        assert identity.role == AgentRole.DIAGNOSTICIAN
        assert identity.name == "test-agent"


class TestToolPermissionRegistry:
    def test_observer_permissions(self):
        registry = ToolPermissionRegistry()
        assert registry.is_allowed(AgentRole.OBSERVER, "get_service_health")
        assert not registry.is_allowed(AgentRole.OBSERVER, "execute_remediation")

    def test_admin_has_all_permissions(self):
        registry = ToolPermissionRegistry()
        assert registry.is_allowed(AgentRole.ADMIN, "execute_remediation")
        assert registry.is_allowed(AgentRole.ADMIN, "anything")


class TestRemediationRegistry:
    def test_register_and_execute(self):
        registry = RemediationRegistry()
        registry.register(
            name="restart",
            description="Restart the service",
            handler=lambda: "restarted",
        )
        executor = RemediationExecutor(registry)
        result = executor.execute("restart", approved=True)
        assert result.success
        assert "restarted" in result.message

    def test_requires_approval(self):
        registry = RemediationRegistry()
        registry.register(
            name="restart",
            description="Restart",
            handler=lambda: "restarted",
            requires_approval=True,
        )
        executor = RemediationExecutor(registry)
        result = executor.execute("restart", approved=False)
        assert not result.success
        assert "approval" in result.message.lower()


class TestSloReportGenerator:
    def test_generate_report(self):
        tracker = SloTracker()
        tracker.register(SloDefinition(name="avail", target=0.999))
        for _ in range(100):
            tracker.record_request("avail", is_failure=False)

        generator = SloReportGenerator(tracker)
        report = generator.generate()
        assert report.overall_compliance == 1.0
        assert len(report.slo_statuses) == 1


class TestTrendAnalyzer:
    def test_stable_trend(self):
        analyzer = TrendAnalyzer()
        for i in range(10):
            analyzer.record("latency", 100.0)
        result = analyzer.analyze("latency")
        assert result.direction == "stable"

    def test_degrading_trend(self):
        analyzer = TrendAnalyzer()
        for i in range(10):
            analyzer.record("latency", float(i * 10 + 100))
        result = analyzer.analyze("latency")
        assert result.direction == "degrading"
