"""Tests for TopologyRegistry."""

from agenttel.config import AgentTelConfig, TopologyConfig, DependencyConfig, ConsumerConfig
from agenttel.enums import DependencyType, DependencyCriticality, ConsumptionPattern, ServiceTier
from agenttel.topology.registry import TopologyRegistry


def create_test_config(
    team="payments-team",
    tier=ServiceTier.CRITICAL,
    domain="fintech",
    on_call_channel="#payments-oncall",
    dependencies=None,
    consumers=None,
):
    """Create a test AgentTelConfig with topology metadata."""
    return AgentTelConfig(
        topology=TopologyConfig(
            team=team,
            tier=tier,
            domain=domain,
            on_call_channel=on_call_channel,
        ),
        dependencies=dependencies or [],
        consumers=consumers or [],
    )


class TestTopologyRegistry:
    def test_register_service(self):
        """Verify that service metadata is accessible from the registry."""
        config = create_test_config(
            team="api-team",
            tier=ServiceTier.STANDARD,
            domain="backend",
        )
        registry = TopologyRegistry(config)

        assert registry.team == "api-team"
        assert registry.tier == "standard"
        assert registry.domain == "backend"

    def test_get_dependencies(self):
        """Verify dependencies are loaded from config."""
        config = create_test_config(
            dependencies=[
                DependencyConfig(
                    name="postgres",
                    type=DependencyType.DATABASE,
                    criticality=DependencyCriticality.REQUIRED,
                    protocol="postgresql",
                    timeout_ms=3000,
                ),
                DependencyConfig(
                    name="redis",
                    type=DependencyType.CACHE,
                    criticality=DependencyCriticality.DEGRADED,
                    protocol="redis",
                    timeout_ms=1000,
                ),
            ]
        )
        registry = TopologyRegistry(config)

        deps = registry.get_all_dependencies()
        assert len(deps) == 2

        pg = registry.get_dependency("postgres")
        assert pg is not None
        assert pg.name == "postgres"
        assert pg.type == DependencyType.DATABASE
        assert pg.criticality == DependencyCriticality.REQUIRED
        assert pg.timeout_ms == 3000

        redis = registry.get_dependency("redis")
        assert redis is not None
        assert redis.type == DependencyType.CACHE
        assert redis.criticality == DependencyCriticality.DEGRADED

    def test_get_consumers(self):
        """Verify consumers are loaded from config."""
        config = create_test_config(
            consumers=[
                ConsumerConfig(
                    name="mobile-app",
                    pattern=ConsumptionPattern.SYNC,
                    sla_latency_ms=200,
                ),
                ConsumerConfig(
                    name="analytics-pipeline",
                    pattern=ConsumptionPattern.ASYNC,
                ),
            ]
        )
        registry = TopologyRegistry(config)

        consumers = registry.get_all_consumers()
        assert len(consumers) == 2

        mobile = registry.get_consumer("mobile-app")
        assert mobile is not None
        assert mobile.name == "mobile-app"
        assert mobile.pattern == ConsumptionPattern.SYNC
        assert mobile.sla_latency_ms == 200

    def test_get_topology_attributes(self):
        """Verify topology attributes dict matches expected format."""
        config = create_test_config(
            team="platform-team",
            tier=ServiceTier.CRITICAL,
            domain="infrastructure",
            on_call_channel="#platform-oncall",
        )
        registry = TopologyRegistry(config)

        attrs = registry.get_topology_attributes()
        assert "agenttel.topology.team" in attrs
        assert attrs["agenttel.topology.team"] == "platform-team"
        assert "agenttel.topology.tier" in attrs
        assert attrs["agenttel.topology.tier"] == "critical"
        assert "agenttel.topology.domain" in attrs
        assert attrs["agenttel.topology.domain"] == "infrastructure"

    def test_metadata_preserved(self):
        """Verify that dependency metadata (protocol, circuit breaker, etc.) is preserved."""
        config = create_test_config(
            dependencies=[
                DependencyConfig(
                    name="auth-service",
                    type=DependencyType.INTERNAL_SERVICE,
                    criticality=DependencyCriticality.REQUIRED,
                    protocol="grpc",
                    timeout_ms=2000,
                    circuit_breaker=True,
                    fallback="cached-auth",
                    health_endpoint="/health",
                ),
            ]
        )
        registry = TopologyRegistry(config)

        dep = registry.get_dependency("auth-service")
        assert dep is not None
        assert dep.protocol == "grpc"
        assert dep.timeout_ms == 2000
        assert dep.circuit_breaker is True
        assert dep.fallback == "cached-auth"
        assert dep.health_endpoint == "/health"
