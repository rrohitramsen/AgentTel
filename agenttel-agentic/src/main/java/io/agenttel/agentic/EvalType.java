package io.agenttel.agentic;

/**
 * Types of evaluation scoring.
 */
public enum EvalType {
    LLM_JUDGE("llm_judge"),
    HEURISTIC("heuristic"),
    HUMAN("human"),
    CUSTOM("custom");

    private final String value;

    EvalType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EvalType fromValue(String value) {
        for (EvalType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown EvalType: " + value);
    }
}
