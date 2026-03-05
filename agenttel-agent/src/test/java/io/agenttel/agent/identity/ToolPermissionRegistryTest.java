package io.agenttel.agent.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ToolPermissionRegistryTest {

    private ToolPermissionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolPermissionRegistry();
    }

    @Test
    void defaultProfiles_observerCanOnlyRead() {
        var observer = new AgentIdentity("obs-1", "observer", null);

        assertThat(registry.isAllowed(observer, "get_service_health")).isTrue();
        assertThat(registry.isAllowed(observer, "get_incident_context")).isTrue();
        assertThat(registry.isAllowed(observer, "get_session")).isTrue();

        assertThat(registry.isAllowed(observer, "verify_remediation_effect")).isFalse();
        assertThat(registry.isAllowed(observer, "create_session")).isFalse();
        assertThat(registry.isAllowed(observer, "execute_remediation")).isFalse();
    }

    @Test
    void defaultProfiles_diagnosticianCanReadAndDiagnose() {
        var diag = new AgentIdentity("diag-1", "diagnostician", null);

        assertThat(registry.isAllowed(diag, "get_service_health")).isTrue();
        assertThat(registry.isAllowed(diag, "verify_remediation_effect")).isTrue();
        assertThat(registry.isAllowed(diag, "create_session")).isTrue();
        assertThat(registry.isAllowed(diag, "add_session_entry")).isTrue();

        assertThat(registry.isAllowed(diag, "execute_remediation")).isFalse();
    }

    @Test
    void defaultProfiles_remediatorCanReadDiagnoseAndRemediate() {
        var remed = new AgentIdentity("remed-1", "remediator", null);

        assertThat(registry.isAllowed(remed, "get_service_health")).isTrue();
        assertThat(registry.isAllowed(remed, "verify_remediation_effect")).isTrue();
        assertThat(registry.isAllowed(remed, "execute_remediation")).isTrue();
        assertThat(registry.isAllowed(remed, "list_remediation_actions")).isTrue();
    }

    @Test
    void defaultProfiles_adminCanDoEverything() {
        var admin = new AgentIdentity("admin-1", "admin", null);

        assertThat(registry.isAllowed(admin, "get_service_health")).isTrue();
        assertThat(registry.isAllowed(admin, "verify_remediation_effect")).isTrue();
        assertThat(registry.isAllowed(admin, "execute_remediation")).isTrue();
    }

    @Test
    void anonymousAgent_getsObserverPermissions() {
        assertThat(registry.isAllowed(AgentIdentity.ANONYMOUS, "get_service_health")).isTrue();
        assertThat(registry.isAllowed(AgentIdentity.ANONYMOUS, "execute_remediation")).isFalse();
    }

    @Test
    void nullAgent_getsObserverPermissions() {
        assertThat(registry.isAllowed(null, "get_service_health")).isTrue();
        assertThat(registry.isAllowed(null, "execute_remediation")).isFalse();
    }

    @Test
    void customRolePermissions_override() {
        registry.setRolePermissions("custom_role",
                EnumSet.of(ToolPermission.READ, ToolPermission.REMEDIATE));

        var agent = new AgentIdentity("custom-1", "custom_role", null);

        assertThat(registry.isAllowed(agent, "get_service_health")).isTrue();
        assertThat(registry.isAllowed(agent, "execute_remediation")).isTrue();
        // DIAGNOSE not granted
        assertThat(registry.isAllowed(agent, "verify_remediation_effect")).isFalse();
    }

    @Test
    void unknownTool_defaultsToReadPermission() {
        var observer = new AgentIdentity("obs-1", "observer", null);

        assertThat(registry.isAllowed(observer, "some_future_tool")).isTrue();
    }

    @Test
    void getPermissions_returnsCorrectSet() {
        Set<ToolPermission> observerPerms = registry.getPermissions("observer");
        assertThat(observerPerms).containsExactly(ToolPermission.READ);

        Set<ToolPermission> adminPerms = registry.getPermissions("admin");
        assertThat(adminPerms).containsExactlyInAnyOrder(
                ToolPermission.READ, ToolPermission.DIAGNOSE,
                ToolPermission.REMEDIATE, ToolPermission.ADMIN);
    }

    @Test
    void getPermissions_unknownRole_defaultsToRead() {
        Set<ToolPermission> perms = registry.getPermissions("unknown_role");
        assertThat(perms).containsExactly(ToolPermission.READ);
    }

    @Test
    void setToolRequirement_customizesToolPermission() {
        registry.setToolRequirement("get_service_health", ToolPermission.ADMIN);

        var observer = new AgentIdentity("obs-1", "observer", null);
        assertThat(registry.isAllowed(observer, "get_service_health")).isFalse();

        var admin = new AgentIdentity("admin-1", "admin", null);
        assertThat(registry.isAllowed(admin, "get_service_health")).isTrue();
    }
}
