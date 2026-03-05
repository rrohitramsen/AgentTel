package io.agenttel.api;

/**
 * Predefined agent roles for multi-agent orchestration.
 * Controls what tools each agent can access via role-based permissions.
 */
public enum AgentRole {
    OBSERVER("observer"),
    DIAGNOSTICIAN("diagnostician"),
    REMEDIATOR("remediator"),
    ADMIN("admin");

    private final String value;

    AgentRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AgentRole fromValue(String value) {
        for (AgentRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        return OBSERVER;
    }
}
