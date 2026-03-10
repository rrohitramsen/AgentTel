package io.agenttel.agentic;

/**
 * Types of memory access operations performed by an agent.
 */
public enum MemoryOperation {
    READ("read"),
    WRITE("write"),
    DELETE("delete"),
    SEARCH("search");

    private final String value;

    MemoryOperation(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MemoryOperation fromValue(String value) {
        for (MemoryOperation op : values()) {
            if (op.value.equals(value)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown MemoryOperation: " + value);
    }
}
