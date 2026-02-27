package io.agenttel.api;

public enum ImpactScope {
    SINGLE_REQUEST("single_request"),
    SINGLE_USER("single_user"),
    SUBSET_USERS("subset_users"),
    ALL_USERS("all_users"),
    MULTI_SERVICE("multi_service");

    private final String value;

    ImpactScope(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ImpactScope fromValue(String value) {
        for (ImpactScope s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown ImpactScope: " + value);
    }
}
