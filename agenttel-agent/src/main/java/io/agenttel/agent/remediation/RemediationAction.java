package io.agenttel.agent.remediation;

/**
 * Describes a remediation action that an AI agent can execute.
 */
public record RemediationAction(
        String name,
        String description,
        String operationName,
        ActionType type,
        boolean requiresApproval,
        String command
) {

    public enum ActionType {
        RESTART,
        SCALE,
        ROLLBACK,
        CIRCUIT_BREAKER,
        RATE_LIMIT,
        CACHE_FLUSH,
        CUSTOM
    }

    /**
     * Builder for creating remediation actions.
     */
    public static Builder builder(String name, String operationName) {
        return new Builder(name, operationName);
    }

    public static class Builder {
        private final String name;
        private final String operationName;
        private String description = "";
        private ActionType type = ActionType.CUSTOM;
        private boolean requiresApproval = true;
        private String command = "";

        Builder(String name, String operationName) {
            this.name = name;
            this.operationName = operationName;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder type(ActionType type) {
            this.type = type;
            return this;
        }

        public Builder requiresApproval(boolean requiresApproval) {
            this.requiresApproval = requiresApproval;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public RemediationAction build() {
            return new RemediationAction(name, description, operationName, type, requiresApproval, command);
        }
    }
}
