package io.agenttel.core.anomaly;

/**
 * Known incident patterns that can be detected from span telemetry.
 */
public enum IncidentPattern {

    CASCADE_FAILURE("cascade_failure", "Multiple dependent services failing simultaneously"),
    MEMORY_LEAK("memory_leak", "Steadily increasing latency with increasing error rate"),
    THUNDERING_HERD("thundering_herd", "Sudden spike in request rate after recovery"),
    COLD_START("cold_start", "High latency on first requests after deployment"),
    ERROR_RATE_SPIKE("error_rate_spike", "Sudden increase in error rate beyond baseline"),
    LATENCY_DEGRADATION("latency_degradation", "Sustained latency increase beyond baseline");

    private final String value;
    private final String description;

    IncidentPattern(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
