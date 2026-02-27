package io.agenttel.api;

public enum EscalationLevel {
    AUTO_RESOLVE("auto_resolve"),
    NOTIFY_TEAM("notify_team"),
    PAGE_ONCALL("page_oncall"),
    INCIDENT_COMMANDER("incident_commander");

    private final String value;

    EscalationLevel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EscalationLevel fromValue(String value) {
        for (EscalationLevel level : values()) {
            if (level.value.equals(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown EscalationLevel: " + value);
    }
}
