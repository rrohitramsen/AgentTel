package io.agenttel.agentic;

/**
 * Types of human-in-the-loop checkpoints.
 */
public enum HumanCheckpointType {
    APPROVAL("approval"),
    FEEDBACK("feedback"),
    CORRECTION("correction"),
    DECISION("decision");

    private final String value;

    HumanCheckpointType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static HumanCheckpointType fromValue(String value) {
        for (HumanCheckpointType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown HumanCheckpointType: " + value);
    }
}
