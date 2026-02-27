package io.agenttel.api;

public enum DependencyState {
    HEALTHY("healthy"),
    DEGRADED("degraded"),
    UNHEALTHY("unhealthy"),
    UNKNOWN("unknown");

    private final String value;

    DependencyState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DependencyState fromValue(String value) {
        for (DependencyState s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown DependencyState: " + value);
    }
}
