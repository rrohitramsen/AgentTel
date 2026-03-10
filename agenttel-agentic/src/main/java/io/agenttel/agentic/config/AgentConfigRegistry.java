package io.agenttel.agentic.config;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for per-agent configuration.
 * Populated from YAML config at startup, queried by {@code AgentTracer} at invocation time.
 */
public class AgentConfigRegistry {

    private final ConcurrentHashMap<String, AgentConfig> configs = new ConcurrentHashMap<>();

    /**
     * Registers configuration for a named agent.
     */
    public void register(String agentName, AgentConfig config) {
        configs.put(agentName, config);
    }

    /**
     * Returns the configuration for a named agent, if registered.
     */
    public Optional<AgentConfig> getConfig(String agentName) {
        return Optional.ofNullable(configs.get(agentName));
    }

    /**
     * Returns true if a configuration exists for the given agent name.
     */
    public boolean hasConfig(String agentName) {
        return configs.containsKey(agentName);
    }
}
