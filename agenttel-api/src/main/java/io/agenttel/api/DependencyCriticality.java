package io.agenttel.api;

public enum DependencyCriticality {
    REQUIRED("required"),
    DEGRADED("degraded"),
    OPTIONAL("optional");

    private final String value;

    DependencyCriticality(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DependencyCriticality fromValue(String value) {
        for (DependencyCriticality c : values()) {
            if (c.value.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown DependencyCriticality: " + value);
    }
}
