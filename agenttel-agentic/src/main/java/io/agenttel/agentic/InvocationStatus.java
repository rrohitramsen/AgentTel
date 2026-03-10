package io.agenttel.agentic;

/**
 * Terminal status of an agent invocation.
 */
public enum InvocationStatus {
    SUCCESS("success"),
    FAILURE("failure"),
    TIMEOUT("timeout"),
    ESCALATED("escalated"),
    HUMAN_INTERVENED("human_intervened");

    private final String value;

    InvocationStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static InvocationStatus fromValue(String value) {
        for (InvocationStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown InvocationStatus: " + value);
    }
}
