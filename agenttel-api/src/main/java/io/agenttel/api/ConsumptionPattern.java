package io.agenttel.api;

public enum ConsumptionPattern {
    SYNC("sync"),
    ASYNC("async"),
    BATCH("batch"),
    STREAMING("streaming");

    private final String value;

    ConsumptionPattern(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ConsumptionPattern fromValue(String value) {
        for (ConsumptionPattern p : values()) {
            if (p.value.equals(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown ConsumptionPattern: " + value);
    }
}
