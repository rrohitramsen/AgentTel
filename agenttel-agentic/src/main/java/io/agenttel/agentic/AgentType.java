package io.agenttel.agentic;

/**
 * Types of AI agents based on their role in an orchestration.
 */
public enum AgentType {
    SINGLE("single"),
    ORCHESTRATOR("orchestrator"),
    WORKER("worker"),
    EVALUATOR("evaluator"),
    CRITIC("critic"),
    ROUTER("router");

    private final String value;

    AgentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AgentType fromValue(String value) {
        for (AgentType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown AgentType: " + value);
    }
}
