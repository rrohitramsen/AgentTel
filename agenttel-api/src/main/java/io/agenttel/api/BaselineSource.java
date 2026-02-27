package io.agenttel.api;

public enum BaselineSource {
    STATIC("static"),
    ROLLING_7D("rolling_7d"),
    ML_MODEL("ml_model"),
    SLO("slo");

    private final String value;

    BaselineSource(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static BaselineSource fromValue(String value) {
        for (BaselineSource s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown BaselineSource: " + value);
    }
}
