package io.agenttel.agent.mcp;

import io.agenttel.agent.identity.AgentIdentity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registry mapping API keys (bearer tokens) to agent identities.
 * Supports both static key registration and a custom validator function.
 *
 * <p>When no keys are registered and no validator is set, all lookups return empty
 * (the MCP server treats this as "auth not configured" and skips checks).
 */
public class ApiKeyRegistry {

    private final Map<String, AgentIdentity> apiKeys = new ConcurrentHashMap<>();
    private volatile Function<String, AgentIdentity> customValidator;

    /**
     * Register a static API key mapped to an agent identity.
     */
    public void registerKey(String apiKey, AgentIdentity identity) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key must not be null or blank");
        }
        if (identity == null) {
            throw new IllegalArgumentException("Agent identity must not be null");
        }
        apiKeys.put(apiKey.trim(), identity);
    }

    /**
     * Set an optional custom validator function.
     * Called when a key is not found in the static map.
     * Return null from the function to reject the key.
     */
    public void setCustomValidator(Function<String, AgentIdentity> validator) {
        this.customValidator = validator;
    }

    /**
     * Resolve an API key to an agent identity.
     * Checks the static map first, then falls back to the custom validator.
     *
     * @param apiKey the bearer token to resolve
     * @return the identity if found, or empty if the key is invalid
     */
    public Optional<AgentIdentity> resolveKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        String trimmed = apiKey.trim();

        AgentIdentity identity = apiKeys.get(trimmed);
        if (identity != null) {
            return Optional.of(identity);
        }

        if (customValidator != null) {
            AgentIdentity result = customValidator.apply(trimmed);
            return Optional.ofNullable(result);
        }

        return Optional.empty();
    }

    /**
     * Returns true if this registry has any keys registered or a custom validator set.
     */
    public boolean isConfigured() {
        return !apiKeys.isEmpty() || customValidator != null;
    }

    /**
     * Returns the number of statically registered keys.
     */
    public int size() {
        return apiKeys.size();
    }
}
