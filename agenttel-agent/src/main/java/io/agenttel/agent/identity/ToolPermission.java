package io.agenttel.agent.identity;

/**
 * Permission levels for MCP tool access.
 * Each tool requires a minimum permission level; each agent role grants a set of permissions.
 */
public enum ToolPermission {
    READ("read"),
    DIAGNOSE("diagnose"),
    REMEDIATE("remediate"),
    ADMIN("admin");

    private final String value;

    ToolPermission(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ToolPermission fromValue(String value) {
        for (ToolPermission p : values()) {
            if (p.value.equalsIgnoreCase(value)) {
                return p;
            }
        }
        return READ;
    }
}
