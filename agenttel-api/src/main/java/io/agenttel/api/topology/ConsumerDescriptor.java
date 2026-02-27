package io.agenttel.api.topology;

import io.agenttel.api.ConsumptionPattern;

/**
 * Describes a declared consumer of this service.
 */
public record ConsumerDescriptor(
        String name,
        ConsumptionPattern pattern,
        int slaLatencyMs
) {
    public static ConsumerDescriptor of(String name, ConsumptionPattern pattern) {
        return new ConsumerDescriptor(name, pattern, 0);
    }

    public static ConsumerDescriptor of(String name, ConsumptionPattern pattern, int slaLatencyMs) {
        return new ConsumerDescriptor(name, pattern, slaLatencyMs);
    }
}
