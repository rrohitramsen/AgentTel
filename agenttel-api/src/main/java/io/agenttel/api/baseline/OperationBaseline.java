package io.agenttel.api.baseline;

import io.agenttel.api.BaselineSource;

/**
 * Represents the expected baseline behavior for an operation.
 */
public record OperationBaseline(
        String operationName,
        double latencyP50Ms,
        double latencyP99Ms,
        double errorRate,
        BaselineSource source,
        String updatedAt
) {
    public static Builder builder(String operationName) {
        return new Builder(operationName);
    }

    public static class Builder {
        private final String operationName;
        private double latencyP50Ms = -1.0;
        private double latencyP99Ms = -1.0;
        private double errorRate = -1.0;
        private BaselineSource source = BaselineSource.STATIC;
        private String updatedAt = "";

        Builder(String operationName) {
            this.operationName = operationName;
        }

        public Builder latencyP50Ms(double latencyP50Ms) {
            this.latencyP50Ms = latencyP50Ms;
            return this;
        }

        public Builder latencyP99Ms(double latencyP99Ms) {
            this.latencyP99Ms = latencyP99Ms;
            return this;
        }

        public Builder errorRate(double errorRate) {
            this.errorRate = errorRate;
            return this;
        }

        public Builder source(BaselineSource source) {
            this.source = source;
            return this;
        }

        public Builder updatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public OperationBaseline build() {
            return new OperationBaseline(operationName, latencyP50Ms, latencyP99Ms,
                    errorRate, source, updatedAt);
        }
    }
}
