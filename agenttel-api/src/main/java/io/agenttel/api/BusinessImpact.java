package io.agenttel.api;

public enum BusinessImpact {
    NONE("none"),
    DEGRADED_EXPERIENCE("degraded_experience"),
    FEATURE_UNAVAILABLE("feature_unavailable"),
    REVENUE_IMPACTING("revenue_impacting"),
    DATA_LOSS("data_loss");

    private final String value;

    BusinessImpact(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static BusinessImpact fromValue(String value) {
        for (BusinessImpact b : values()) {
            if (b.value.equals(value)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unknown BusinessImpact: " + value);
    }
}
