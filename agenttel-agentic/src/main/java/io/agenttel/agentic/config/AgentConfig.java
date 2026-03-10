package io.agenttel.agentic.config;

/**
 * Immutable configuration for a named agent.
 * Holds identity metadata and safety guardrail settings.
 */
public class AgentConfig {

    private final String name;
    private final String type;
    private final String framework;
    private final String version;
    private final long maxSteps;
    private final int loopThreshold;
    private final double costBudgetUsd;

    private AgentConfig(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.framework = builder.framework;
        this.version = builder.version;
        this.maxSteps = builder.maxSteps;
        this.loopThreshold = builder.loopThreshold;
        this.costBudgetUsd = builder.costBudgetUsd;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() { return name; }
    public String type() { return type; }
    public String framework() { return framework; }
    public String version() { return version; }
    public long maxSteps() { return maxSteps; }
    public int loopThreshold() { return loopThreshold; }
    public double costBudgetUsd() { return costBudgetUsd; }

    public static class Builder {
        private final String name;
        private String type = "";
        private String framework = "";
        private String version = "";
        private long maxSteps = 0;
        private int loopThreshold = 0;
        private double costBudgetUsd = 0;

        Builder(String name) {
            this.name = name;
        }

        public Builder type(String type) { this.type = type; return this; }
        public Builder framework(String framework) { this.framework = framework; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder maxSteps(long maxSteps) { this.maxSteps = maxSteps; return this; }
        public Builder loopThreshold(int loopThreshold) { this.loopThreshold = loopThreshold; return this; }
        public Builder costBudgetUsd(double costBudgetUsd) { this.costBudgetUsd = costBudgetUsd; return this; }

        public AgentConfig build() {
            return new AgentConfig(this);
        }
    }
}
