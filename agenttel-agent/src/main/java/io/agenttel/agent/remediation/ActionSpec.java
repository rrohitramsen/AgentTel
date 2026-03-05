package io.agenttel.agent.remediation;

import java.util.List;
import java.util.Map;

/**
 * Parameterized action specifications for remediation.
 * Provides agents with precise parameters instead of boolean flags.
 */
public sealed interface ActionSpec {

    String type();

    record RetrySpec(
            int maxAttempts,
            List<Long> backoffMs,
            List<Integer> retryOnStatusCodes,
            List<String> retryOnExceptions,
            List<Integer> notRetryOnStatusCodes
    ) implements ActionSpec {
        @Override
        public String type() { return "retry"; }
    }

    record ScaleSpec(
            String direction,
            int minInstances,
            int maxInstances,
            int cooldownSeconds
    ) implements ActionSpec {
        @Override
        public String type() { return "scale"; }
    }

    record CircuitBreakerSpec(
            int failureThreshold,
            long halfOpenAfterMs,
            int successThreshold
    ) implements ActionSpec {
        @Override
        public String type() { return "circuit_breaker"; }
    }

    record RateLimitSpec(
            int requestsPerSecond,
            int burstSize
    ) implements ActionSpec {
        @Override
        public String type() { return "rate_limit"; }
    }

    record GenericSpec(
            Map<String, String> parameters
    ) implements ActionSpec {
        @Override
        public String type() { return "generic"; }
    }
}
