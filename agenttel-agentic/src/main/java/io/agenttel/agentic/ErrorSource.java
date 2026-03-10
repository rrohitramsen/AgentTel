package io.agenttel.agentic;

/**
 * Classification of where an error originated in the agent pipeline.
 */
public enum ErrorSource {
    LLM("llm"),
    TOOL("tool"),
    AGENT("agent"),
    GUARDRAIL("guardrail"),
    TIMEOUT("timeout"),
    NETWORK("network");

    private final String value;

    ErrorSource(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ErrorSource fromValue(String value) {
        for (ErrorSource source : values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown ErrorSource: " + value);
    }
}
