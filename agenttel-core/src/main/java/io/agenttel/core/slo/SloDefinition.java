package io.agenttel.core.slo;

/**
 * Defines a Service Level Objective.
 *
 * @param name            SLO name (e.g., "payment-latency-p99")
 * @param operationName   operation this SLO applies to
 * @param type            what the SLO measures
 * @param target          target value (e.g., 0.999 for 99.9% availability)
 * @param windowSeconds   evaluation window in seconds (e.g., 2592000 for 30 days)
 */
public record SloDefinition(
        String name,
        String operationName,
        SloType type,
        double target,
        long windowSeconds
) {

    public enum SloType {
        AVAILABILITY,
        LATENCY_P99,
        LATENCY_P50,
        ERROR_RATE
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String operationName = "";
        private SloType type = SloType.AVAILABILITY;
        private double target = 0.999;
        private long windowSeconds = 30 * 24 * 3600; // 30 days

        Builder(String name) {
            this.name = name;
        }

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder type(SloType type) {
            this.type = type;
            return this;
        }

        public Builder target(double target) {
            this.target = target;
            return this;
        }

        public Builder windowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
            return this;
        }

        public SloDefinition build() {
            return new SloDefinition(name, operationName, type, target, windowSeconds);
        }
    }
}
