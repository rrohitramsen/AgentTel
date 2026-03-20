"""Tests for AgentTelSpanProcessor."""

from unittest.mock import MagicMock

from agenttel.config import AgentTelConfig
from agenttel.engine import AgentTelEngine


class TestAgentTelEngine:
    def test_from_config_with_defaults(self):
        config = AgentTelConfig()
        engine = AgentTelEngine(config)
        assert engine.config.enabled is True
        assert engine.processor is not None
        assert engine.anomaly_detector is not None
        assert engine.slo_tracker is not None

    def test_static_baselines_registered(self):
        config = AgentTelConfig()
        config.operations = {
            "[GET /users]": MagicMock(
                latency_p50_ms=50.0,
                latency_p99_ms=200.0,
                expected_error_rate=0.01,
                profile=None,
            )
        }
        engine = AgentTelEngine(config)
        # Static baseline should be registered
        baseline = engine.baseline_provider.get_baseline("[GET /users]")
        assert baseline is not None
        assert baseline.latency_p50_ms == 50.0

    def test_slo_registration(self):
        from agenttel.config import SloConfig

        config = AgentTelConfig()
        config.slo = {
            "availability": SloConfig(name="availability", target=0.999)
        }
        engine = AgentTelEngine(config)
        statuses = engine.slo_tracker.get_all_statuses()
        assert len(statuses) == 1
        assert statuses[0].name == "availability"
