package io.agenttel.core.resource;

import io.agenttel.core.topology.TopologyRegistry;

/**
 * Static holder that bridges the SPI world with the Spring/programmatic world.
 * Set during initialization so the ResourceProvider SPI can access topology data.
 */
public final class AgentTelGlobalState {
    private AgentTelGlobalState() {}

    private static volatile TopologyRegistry topologyRegistry;

    public static void initialize(TopologyRegistry registry) {
        topologyRegistry = registry;
    }

    public static TopologyRegistry getTopologyRegistry() {
        return topologyRegistry;
    }

    /** Resets state (for testing). */
    public static void reset() {
        topologyRegistry = null;
    }
}
