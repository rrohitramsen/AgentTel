package io.agenttel.agentic;

/**
 * Types of reasoning steps in an agent's thought-action-observation loop.
 */
public enum StepType {
    THOUGHT("thought"),
    ACTION("action"),
    OBSERVATION("observation"),
    EVALUATION("evaluation"),
    REVISION("revision");

    private final String value;

    StepType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static StepType fromValue(String value) {
        for (StepType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown StepType: " + value);
    }
}
