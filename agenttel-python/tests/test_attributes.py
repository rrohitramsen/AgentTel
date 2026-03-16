"""Tests for AgentTel semantic attributes."""

from agenttel import attributes as attr
from agenttel import agentic_attributes as aa


class TestAgentTelAttributes:
    def test_topology_attributes_have_correct_prefix(self):
        assert attr.TOPOLOGY_TEAM == "agenttel.topology.team"
        assert attr.TOPOLOGY_TIER == "agenttel.topology.tier"
        assert attr.TOPOLOGY_DOMAIN == "agenttel.topology.domain"

    def test_dependency_attributes(self):
        assert attr.DEPENDENCY_NAME == "agenttel.dependency.name"
        assert attr.DEPENDENCY_TYPE == "agenttel.dependency.type"
        assert attr.DEPENDENCY_CRITICALITY == "agenttel.dependency.criticality"

    def test_baseline_attributes(self):
        assert attr.BASELINE_LATENCY_P50_MS == "agenttel.baseline.latency_p50_ms"
        assert attr.BASELINE_LATENCY_P99_MS == "agenttel.baseline.latency_p99_ms"
        assert attr.BASELINE_SOURCE == "agenttel.baseline.source"

    def test_anomaly_attributes(self):
        assert attr.ANOMALY_DETECTED == "agenttel.anomaly.detected"
        assert attr.ANOMALY_SCORE == "agenttel.anomaly.score"
        assert attr.ANOMALY_Z_SCORE == "agenttel.anomaly.z_score"

    def test_slo_attributes(self):
        assert attr.SLO_NAME == "agenttel.slo.name"
        assert attr.SLO_TARGET == "agenttel.slo.target"
        assert attr.SLO_BUDGET_REMAINING == "agenttel.slo.budget_remaining"

    def test_genai_attributes(self):
        assert attr.GENAI_COST_USD == "agenttel.genai.cost_usd"
        assert attr.GENAI_FRAMEWORK == "agenttel.genai.framework"

    def test_all_attributes_have_agenttel_prefix(self):
        for name in dir(attr):
            if name.isupper() and not name.startswith("_"):
                value = getattr(attr, name)
                assert value.startswith("agenttel."), f"{name}={value} missing prefix"


class TestAgenticAttributes:
    def test_agent_identity_attributes(self):
        assert aa.AGENT_NAME == "agenttel.agent.name"
        assert aa.AGENT_TYPE == "agenttel.agent.type"
        assert aa.AGENT_FRAMEWORK == "agenttel.agent.framework"

    def test_invocation_attributes(self):
        assert aa.INVOCATION_ID == "agenttel.invocation.id"
        assert aa.INVOCATION_GOAL == "agenttel.invocation.goal"
        assert aa.INVOCATION_STATUS == "agenttel.invocation.status"

    def test_cost_attributes(self):
        assert aa.COST_TOTAL_USD == "agenttel.cost.total_usd"
        assert aa.COST_INPUT_TOKENS == "agenttel.cost.input_tokens"

    def test_all_agentic_attributes_have_prefix(self):
        for name in dir(aa):
            if name.isupper() and not name.startswith("_"):
                value = getattr(aa, name)
                assert value.startswith("agenttel."), f"{name}={value} missing prefix"
