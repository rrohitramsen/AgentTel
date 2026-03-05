package io.agenttel.agent.identity;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AgentIdentityTest {

    @Test
    void fromHeaders_extractsAllFields() {
        Map<String, String> headers = Map.of(
                "X-Agent-Id", "diag-agent-1",
                "X-Agent-Role", "diagnostician",
                "X-Agent-Session-Id", "session-abc"
        );

        AgentIdentity identity = AgentIdentity.fromHeaders(headers);

        assertThat(identity.agentId()).isEqualTo("diag-agent-1");
        assertThat(identity.role()).isEqualTo("diagnostician");
        assertThat(identity.sessionId()).isEqualTo("session-abc");
        assertThat(identity.isAnonymous()).isFalse();
    }

    @Test
    void fromHeaders_defaultsRoleToObserver() {
        Map<String, String> headers = Map.of("X-Agent-Id", "agent-2");

        AgentIdentity identity = AgentIdentity.fromHeaders(headers);

        assertThat(identity.agentId()).isEqualTo("agent-2");
        assertThat(identity.role()).isEqualTo("observer");
        assertThat(identity.sessionId()).isNull();
    }

    @Test
    void fromHeaders_nullHeaders_returnsAnonymous() {
        AgentIdentity identity = AgentIdentity.fromHeaders(null);

        assertThat(identity).isEqualTo(AgentIdentity.ANONYMOUS);
        assertThat(identity.isAnonymous()).isTrue();
    }

    @Test
    void fromHeaders_missingAgentId_returnsAnonymous() {
        Map<String, String> headers = Map.of("X-Agent-Role", "admin");

        AgentIdentity identity = AgentIdentity.fromHeaders(headers);

        assertThat(identity.isAnonymous()).isTrue();
    }

    @Test
    void fromHeaders_blankAgentId_returnsAnonymous() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Agent-Id", "  ");

        AgentIdentity identity = AgentIdentity.fromHeaders(headers);

        assertThat(identity.isAnonymous()).isTrue();
    }

    @Test
    void fromArguments_extractsAllFields() {
        Map<String, String> args = Map.of(
                "_agent_id", "remediation-bot",
                "_agent_role", "remediator",
                "_agent_session_id", "sess-xyz"
        );

        AgentIdentity identity = AgentIdentity.fromArguments(args);

        assertThat(identity.agentId()).isEqualTo("remediation-bot");
        assertThat(identity.role()).isEqualTo("remediator");
        assertThat(identity.sessionId()).isEqualTo("sess-xyz");
        assertThat(identity.isAnonymous()).isFalse();
    }

    @Test
    void fromArguments_nullArgs_returnsAnonymous() {
        AgentIdentity identity = AgentIdentity.fromArguments(null);

        assertThat(identity.isAnonymous()).isTrue();
    }

    @Test
    void resolve_prefersArgumentsOverHeaders() {
        Map<String, String> headers = Map.of(
                "X-Agent-Id", "header-agent",
                "X-Agent-Role", "observer"
        );
        Map<String, String> args = Map.of(
                "_agent_id", "arg-agent",
                "_agent_role", "admin"
        );

        AgentIdentity identity = AgentIdentity.resolve(headers, args);

        assertThat(identity.agentId()).isEqualTo("arg-agent");
        assertThat(identity.role()).isEqualTo("admin");
    }

    @Test
    void resolve_fallsBackToHeaders_whenArgsAnonymous() {
        Map<String, String> headers = Map.of(
                "X-Agent-Id", "header-agent",
                "X-Agent-Role", "diagnostician"
        );
        Map<String, String> args = Map.of("operation_name", "test");

        AgentIdentity identity = AgentIdentity.resolve(headers, args);

        assertThat(identity.agentId()).isEqualTo("header-agent");
        assertThat(identity.role()).isEqualTo("diagnostician");
    }

    @Test
    void anonymous_constant_hasExpectedValues() {
        assertThat(AgentIdentity.ANONYMOUS.agentId()).isEqualTo("anonymous");
        assertThat(AgentIdentity.ANONYMOUS.role()).isEqualTo("observer");
        assertThat(AgentIdentity.ANONYMOUS.sessionId()).isNull();
        assertThat(AgentIdentity.ANONYMOUS.isAnonymous()).isTrue();
    }
}
