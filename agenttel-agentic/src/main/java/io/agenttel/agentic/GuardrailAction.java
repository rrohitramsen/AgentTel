package io.agenttel.agentic;

/**
 * Actions taken when a guardrail is triggered.
 */
public enum GuardrailAction {
    BLOCK("block"),
    WARN("warn"),
    LOG("log"),
    ESCALATE("escalate");

    private final String value;

    GuardrailAction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static GuardrailAction fromValue(String value) {
        for (GuardrailAction action : values()) {
            if (action.value.equals(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown GuardrailAction: " + value);
    }
}
