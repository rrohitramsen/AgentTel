package io.agenttel.api.topology;

import io.agenttel.api.DependencyCriticality;
import io.agenttel.api.DependencyType;

/**
 * Describes a declared dependency of a service.
 */
public record DependencyDescriptor(
        String name,
        DependencyType type,
        DependencyCriticality criticality,
        String protocol,
        int timeoutMs,
        boolean circuitBreaker,
        String fallback,
        String healthEndpoint
) {
    public static Builder builder(String name, DependencyType type) {
        return new Builder(name, type);
    }

    public static class Builder {
        private final String name;
        private final DependencyType type;
        private DependencyCriticality criticality = DependencyCriticality.REQUIRED;
        private String protocol = "";
        private int timeoutMs = 0;
        private boolean circuitBreaker = false;
        private String fallback = "";
        private String healthEndpoint = "";

        Builder(String name, DependencyType type) {
            this.name = name;
            this.type = type;
        }

        public Builder criticality(DependencyCriticality criticality) {
            this.criticality = criticality;
            return this;
        }

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder circuitBreaker(boolean circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        public Builder fallback(String fallback) {
            this.fallback = fallback;
            return this;
        }

        public Builder healthEndpoint(String healthEndpoint) {
            this.healthEndpoint = healthEndpoint;
            return this;
        }

        public DependencyDescriptor build() {
            return new DependencyDescriptor(name, type, criticality, protocol,
                    timeoutMs, circuitBreaker, fallback, healthEndpoint);
        }
    }
}
