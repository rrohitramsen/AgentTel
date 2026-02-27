package io.agenttel.api;

public enum ServiceTier {
    CRITICAL("critical"),
    STANDARD("standard"),
    INTERNAL("internal"),
    EXPERIMENTAL("experimental");

    private final String value;

    ServiceTier(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ServiceTier fromValue(String value) {
        for (ServiceTier tier : values()) {
            if (tier.value.equals(value)) {
                return tier;
            }
        }
        throw new IllegalArgumentException("Unknown ServiceTier: " + value);
    }
}
