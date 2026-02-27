package io.agenttel.api.attributes;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTelAttributesTest {

    @Test
    void topologyAttributeKeysHaveCorrectNames() {
        assertThat(AgentTelAttributes.TOPOLOGY_TEAM.getKey()).isEqualTo("agenttel.topology.team");
        assertThat(AgentTelAttributes.TOPOLOGY_TIER.getKey()).isEqualTo("agenttel.topology.tier");
        assertThat(AgentTelAttributes.TOPOLOGY_DOMAIN.getKey()).isEqualTo("agenttel.topology.domain");
        assertThat(AgentTelAttributes.TOPOLOGY_ON_CALL_CHANNEL.getKey()).isEqualTo("agenttel.topology.on_call_channel");
        assertThat(AgentTelAttributes.TOPOLOGY_DEPENDENCIES.getKey()).isEqualTo("agenttel.topology.dependencies");
        assertThat(AgentTelAttributes.TOPOLOGY_CONSUMERS.getKey()).isEqualTo("agenttel.topology.consumers");
    }

    @Test
    void baselineAttributeKeysHaveCorrectTypes() {
        assertThat(AgentTelAttributes.BASELINE_LATENCY_P50_MS.getType()).isEqualTo(AttributeType.DOUBLE);
        assertThat(AgentTelAttributes.BASELINE_LATENCY_P99_MS.getType()).isEqualTo(AttributeType.DOUBLE);
        assertThat(AgentTelAttributes.BASELINE_ERROR_RATE.getType()).isEqualTo(AttributeType.DOUBLE);
        assertThat(AgentTelAttributes.BASELINE_SOURCE.getType()).isEqualTo(AttributeType.STRING);
    }

    @Test
    void decisionAttributeKeysHaveCorrectTypes() {
        assertThat(AgentTelAttributes.DECISION_RETRYABLE.getType()).isEqualTo(AttributeType.BOOLEAN);
        assertThat(AgentTelAttributes.DECISION_RETRY_AFTER_MS.getType()).isEqualTo(AttributeType.LONG);
        assertThat(AgentTelAttributes.DECISION_IDEMPOTENT.getType()).isEqualTo(AttributeType.BOOLEAN);
        assertThat(AgentTelAttributes.DECISION_RUNBOOK_URL.getType()).isEqualTo(AttributeType.STRING);
        assertThat(AgentTelAttributes.DECISION_SAFE_TO_RESTART.getType()).isEqualTo(AttributeType.BOOLEAN);
    }

    @Test
    void severityAttributeKeysHaveCorrectNames() {
        assertThat(AgentTelAttributes.SEVERITY_ANOMALY_SCORE.getKey()).isEqualTo("agenttel.severity.anomaly_score");
        assertThat(AgentTelAttributes.SEVERITY_PATTERN.getKey()).isEqualTo("agenttel.severity.pattern");
        assertThat(AgentTelAttributes.SEVERITY_IMPACT_SCOPE.getKey()).isEqualTo("agenttel.severity.impact_scope");
        assertThat(AgentTelAttributes.SEVERITY_USER_FACING.getKey()).isEqualTo("agenttel.severity.user_facing");
    }

    @Test
    void allAttributeKeysStartWithAgenttelPrefix() {
        // Spot-check that all attributes follow the naming convention
        assertThat(AgentTelAttributes.CAUSE_HINT.getKey()).startsWith("agenttel.");
        assertThat(AgentTelAttributes.DEPLOYMENT_ID.getKey()).startsWith("agenttel.");
        assertThat(AgentTelAttributes.CIRCUIT_BREAKER_NAME.getKey()).startsWith("agenttel.");
    }
}
