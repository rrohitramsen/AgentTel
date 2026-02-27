package io.agenttel.core.topology;

import io.agenttel.api.annotations.AgentObservable;
import io.agenttel.api.annotations.DeclareConsumer;
import io.agenttel.api.annotations.DeclareDependency;
import io.agenttel.api.topology.ConsumerDescriptor;
import io.agenttel.api.topology.DependencyDescriptor;

/**
 * Scans class-level annotations and populates a TopologyRegistry.
 * Called once at startup — no reflection at request time.
 */
public class AnnotationTopologyScanner {

    /**
     * Scans the given class for AgentTel annotations and registers topology data.
     */
    public void scan(Class<?> annotatedClass, TopologyRegistry registry) {
        scanAgentObservable(annotatedClass, registry);
        scanDependencies(annotatedClass, registry);
        scanConsumers(annotatedClass, registry);
    }

    private void scanAgentObservable(Class<?> clazz, TopologyRegistry registry) {
        AgentObservable annotation = clazz.getAnnotation(AgentObservable.class);
        if (annotation == null) {
            return;
        }
        if (!annotation.team().isEmpty()) {
            registry.setTeam(annotation.team());
        }
        registry.setTier(annotation.tier());
        if (!annotation.domain().isEmpty()) {
            registry.setDomain(annotation.domain());
        }
        if (!annotation.onCallChannel().isEmpty()) {
            registry.setOnCallChannel(annotation.onCallChannel());
        }
        if (!annotation.repoUrl().isEmpty()) {
            registry.setRepoUrl(annotation.repoUrl());
        }
    }

    private void scanDependencies(Class<?> clazz, TopologyRegistry registry) {
        DeclareDependency[] deps = clazz.getAnnotationsByType(DeclareDependency.class);
        for (DeclareDependency dep : deps) {
            registry.registerDependency(new DependencyDescriptor(
                    dep.name(),
                    dep.type(),
                    dep.criticality(),
                    dep.protocol(),
                    dep.timeoutMs(),
                    dep.circuitBreaker(),
                    dep.fallback(),
                    dep.healthEndpoint()
            ));
        }
    }

    private void scanConsumers(Class<?> clazz, TopologyRegistry registry) {
        DeclareConsumer[] consumers = clazz.getAnnotationsByType(DeclareConsumer.class);
        for (DeclareConsumer consumer : consumers) {
            registry.registerConsumer(new ConsumerDescriptor(
                    consumer.name(),
                    consumer.pattern(),
                    consumer.slaLatencyMs()
            ));
        }
    }
}
