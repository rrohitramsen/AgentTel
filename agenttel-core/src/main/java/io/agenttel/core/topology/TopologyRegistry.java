package io.agenttel.core.topology;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agenttel.api.DependencyCriticality;
import io.agenttel.api.DependencyType;
import io.agenttel.api.ServiceTier;
import io.agenttel.api.topology.ConsumerDescriptor;
import io.agenttel.api.topology.DependencyDescriptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of the service's dependency graph and topology metadata.
 * Thread-safe: written at startup, read concurrently at request time.
 */
public class TopologyRegistry {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private volatile String team = "";
    private volatile ServiceTier tier = ServiceTier.STANDARD;
    private volatile String domain = "";
    private volatile String onCallChannel = "";
    private volatile String repoUrl = "";

    private final ConcurrentHashMap<String, DependencyDescriptor> dependencies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConsumerDescriptor> consumers = new ConcurrentHashMap<>();

    public void setTeam(String team) {
        this.team = team;
    }

    public String getTeam() {
        return team;
    }

    public void setTier(ServiceTier tier) {
        this.tier = tier;
    }

    public ServiceTier getTier() {
        return tier;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDomain() {
        return domain;
    }

    public void setOnCallChannel(String onCallChannel) {
        this.onCallChannel = onCallChannel;
    }

    public String getOnCallChannel() {
        return onCallChannel;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void registerDependency(DependencyDescriptor descriptor) {
        dependencies.put(descriptor.name(), descriptor);
    }

    public void registerConsumer(ConsumerDescriptor descriptor) {
        consumers.put(descriptor.name(), descriptor);
    }

    public Optional<DependencyDescriptor> getDependency(String name) {
        return Optional.ofNullable(dependencies.get(name));
    }

    public List<DependencyDescriptor> getDependencies() {
        return new ArrayList<>(dependencies.values());
    }

    public List<ConsumerDescriptor> getConsumers() {
        return new ArrayList<>(consumers.values());
    }

    /**
     * Serializes all dependencies to a JSON array string for the
     * agenttel.topology.dependencies resource attribute.
     */
    public String serializeDependenciesToJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (DependencyDescriptor dep : dependencies.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", dep.name());
            map.put("type", dep.type().getValue());
            map.put("criticality", dep.criticality().getValue());
            if (!dep.protocol().isEmpty()) {
                map.put("protocol", dep.protocol());
            }
            if (dep.timeoutMs() > 0) {
                map.put("timeout_ms", dep.timeoutMs());
            }
            if (dep.circuitBreaker()) {
                map.put("circuit_breaker", true);
            }
            if (!dep.fallback().isEmpty()) {
                map.put("fallback", dep.fallback());
            }
            if (!dep.healthEndpoint().isEmpty()) {
                map.put("health_endpoint", dep.healthEndpoint());
            }
            list.add(map);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * Serializes all consumers to a JSON array string for the
     * agenttel.topology.consumers resource attribute.
     */
    public String serializeConsumersToJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ConsumerDescriptor consumer : consumers.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", consumer.name());
            map.put("consumption_pattern", consumer.pattern().getValue());
            if (consumer.slaLatencyMs() > 0) {
                map.put("sla_latency_ms", consumer.slaLatencyMs());
            }
            list.add(map);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
