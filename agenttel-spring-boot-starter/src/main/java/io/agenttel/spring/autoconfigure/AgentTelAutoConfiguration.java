package io.agenttel.spring.autoconfigure;

import io.agenttel.api.ConsumptionPattern;
import io.agenttel.api.DependencyCriticality;
import io.agenttel.api.DependencyType;
import io.agenttel.api.ServiceTier;
import io.agenttel.api.topology.ConsumerDescriptor;
import io.agenttel.api.topology.DependencyDescriptor;
import io.agenttel.core.anomaly.AnomalyDetector;
import io.agenttel.core.anomaly.PatternMatcher;
import io.agenttel.core.baseline.BaselineProvider;
import io.agenttel.core.baseline.RollingBaselineProvider;
import io.agenttel.core.baseline.StaticBaselineProvider;
import io.agenttel.core.causality.CausalityTracker;
import io.agenttel.core.engine.AgentTelEngine;
import io.agenttel.core.enrichment.AgentTelSpanProcessor;
import io.agenttel.core.enrichment.OperationContextRegistry;
import io.agenttel.core.resource.AgentTelGlobalState;
import io.agenttel.core.slo.SloTracker;
import io.agenttel.core.topology.TopologyRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for AgentTel in Spring Boot applications.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "agenttel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AgentTelProperties.class)
public class AgentTelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TopologyRegistry agentTelTopologyRegistry(AgentTelProperties props) {
        TopologyRegistry registry = new TopologyRegistry();

        // Populate from properties
        var topo = props.getTopology();
        if (!topo.getTeam().isEmpty()) {
            registry.setTeam(topo.getTeam());
        }
        try {
            registry.setTier(ServiceTier.fromValue(topo.getTier()));
        } catch (IllegalArgumentException e) {
            registry.setTier(ServiceTier.STANDARD);
        }
        if (!topo.getDomain().isEmpty()) {
            registry.setDomain(topo.getDomain());
        }
        if (!topo.getOnCallChannel().isEmpty()) {
            registry.setOnCallChannel(topo.getOnCallChannel());
        }
        if (!topo.getRepoUrl().isEmpty()) {
            registry.setRepoUrl(topo.getRepoUrl());
        }

        // Register dependencies from config
        for (var dep : props.getDependencies()) {
            registry.registerDependency(new DependencyDescriptor(
                    dep.getName(),
                    DependencyType.fromValue(dep.getType()),
                    DependencyCriticality.fromValue(dep.getCriticality()),
                    dep.getProtocol(),
                    dep.getTimeoutMs(),
                    dep.isCircuitBreaker(),
                    dep.getFallback(),
                    dep.getHealthEndpoint()
            ));
        }

        // Register consumers from config
        for (var consumer : props.getConsumers()) {
            registry.registerConsumer(new ConsumerDescriptor(
                    consumer.getName(),
                    ConsumptionPattern.fromValue(consumer.getPattern()),
                    consumer.getSlaLatencyMs()
            ));
        }

        // Set global state for SPI-based ResourceProvider
        AgentTelGlobalState.initialize(registry);

        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public StaticBaselineProvider agentTelBaselineProvider() {
        return new StaticBaselineProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public RollingBaselineProvider agentTelRollingBaselineProvider(AgentTelProperties props) {
        var baselineProps = props.getBaselines();
        return new RollingBaselineProvider(
                baselineProps.getRollingWindowSize(),
                baselineProps.getRollingMinSamples());
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationContextRegistry agentTelOperationContextRegistry() {
        return new OperationContextRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public AnomalyDetector agentTelAnomalyDetector(AgentTelProperties props) {
        return new AnomalyDetector(props.getAnomalyDetection().getZScoreThreshold());
    }

    @Bean
    @ConditionalOnMissingBean
    public PatternMatcher agentTelPatternMatcher() {
        return new PatternMatcher();
    }

    @Bean
    @ConditionalOnMissingBean
    public SloTracker agentTelSloTracker() {
        return new SloTracker();
    }

    @Bean
    @ConditionalOnMissingBean
    public CausalityTracker agentTelCausalityTracker() {
        return new CausalityTracker();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentTelEngine agentTelEngine(TopologyRegistry topology,
                                          StaticBaselineProvider baselines,
                                          RollingBaselineProvider rollingBaselines,
                                          OperationContextRegistry operationContexts,
                                          AnomalyDetector anomalyDetector,
                                          PatternMatcher patternMatcher,
                                          SloTracker sloTracker,
                                          CausalityTracker causalityTracker,
                                          OpenTelemetry otel) {
        return AgentTelEngine.builder()
                .topologyRegistry(topology)
                .baselineProvider(baselines)
                .rollingBaselines(rollingBaselines)
                .operationContexts(operationContexts)
                .anomalyDetector(anomalyDetector)
                .patternMatcher(patternMatcher)
                .sloTracker(sloTracker)
                .causalityTracker(causalityTracker)
                .openTelemetry(otel)
                .build();
    }

    @Bean
    public AgentTelSpanProcessor agentTelSpanProcessor(AgentTelEngine engine) {
        return engine.createSpanProcessor();
    }
}
