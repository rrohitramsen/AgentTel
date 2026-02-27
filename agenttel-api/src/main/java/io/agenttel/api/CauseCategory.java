package io.agenttel.api;

public enum CauseCategory {
    DEPENDENCY_FAILURE("dependency_failure"),
    RESOURCE_EXHAUSTION("resource_exhaustion"),
    CONFIG_CHANGE("config_change"),
    DEPLOYMENT("deployment"),
    TRAFFIC_SPIKE("traffic_spike"),
    DATA_QUALITY("data_quality"),
    UNKNOWN("unknown");

    private final String value;

    CauseCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CauseCategory fromValue(String value) {
        for (CauseCategory c : values()) {
            if (c.value.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown CauseCategory: " + value);
    }
}
