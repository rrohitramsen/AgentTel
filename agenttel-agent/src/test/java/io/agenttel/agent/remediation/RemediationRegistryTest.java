package io.agenttel.agent.remediation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RemediationRegistryTest {

    private RemediationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RemediationRegistry();
    }

    @Test
    void register_storesActionByOperation() {
        RemediationAction action = RemediationAction.builder("restart", "GET /users")
                .description("Restart service instances")
                .type(RemediationAction.ActionType.RESTART)
                .build();

        registry.register(action);

        var actions = registry.getActions("GET /users");
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).name()).isEqualTo("restart");
    }

    @Test
    void registerGlobal_appliestoAllOperations() {
        RemediationAction global = RemediationAction.builder("enable_debug_logging", "*")
                .description("Enable debug logging")
                .type(RemediationAction.ActionType.CUSTOM)
                .requiresApproval(false)
                .build();

        registry.registerGlobal(global);

        var actions = registry.getActions("GET /users");
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).name()).isEqualTo("enable_debug_logging");

        var actions2 = registry.getActions("POST /orders");
        assertThat(actions2).hasSize(1);
    }

    @Test
    void getActions_combinesGlobalAndOperationSpecific() {
        registry.registerGlobal(RemediationAction.builder("global_action", "*")
                .description("Global")
                .build());
        registry.register(RemediationAction.builder("op_action", "GET /users")
                .description("Op specific")
                .build());

        var actions = registry.getActions("GET /users");
        assertThat(actions).hasSize(2);
    }

    @Test
    void findAction_findsRegisteredAction() {
        registry.register(RemediationAction.builder("rollback", "GET /users")
                .description("Rollback deployment")
                .build());

        assertThat(registry.findAction("rollback")).isPresent();
        assertThat(registry.findAction("nonexistent")).isEmpty();
    }

    @Test
    void getAllActions_returnsAll() {
        registry.registerGlobal(RemediationAction.builder("g1", "*").build());
        registry.register(RemediationAction.builder("a1", "op1").build());
        registry.register(RemediationAction.builder("a2", "op2").build());

        assertThat(registry.getAllActions()).hasSize(3);
    }
}
