package io.agenttel.agentic;

/**
 * Agent orchestration patterns identified across Microsoft, Google, and Anthropic architectures.
 */
public enum OrchestrationPattern {
    REACT("react"),
    SEQUENTIAL("sequential"),
    PARALLEL("parallel"),
    HANDOFF("handoff"),
    ORCHESTRATOR_WORKERS("orchestrator_workers"),
    EVALUATOR_OPTIMIZER("evaluator_optimizer"),
    GROUP_CHAT("group_chat"),
    SWARM("swarm"),
    HIERARCHICAL("hierarchical");

    private final String value;

    OrchestrationPattern(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OrchestrationPattern fromValue(String value) {
        for (OrchestrationPattern pattern : values()) {
            if (pattern.value.equals(value)) {
                return pattern;
            }
        }
        throw new IllegalArgumentException("Unknown OrchestrationPattern: " + value);
    }
}
