package io.agenttel.extension;

import io.agenttel.api.BaselineSource;
import io.agenttel.api.ConsumptionPattern;
import io.agenttel.api.DependencyCriticality;
import io.agenttel.api.DependencyType;
import io.agenttel.api.EscalationLevel;
import io.agenttel.api.ServiceTier;
import io.agenttel.api.baseline.OperationBaseline;
import io.agenttel.api.topology.ConsumerDescriptor;
import io.agenttel.api.topology.DependencyDescriptor;
import io.agenttel.core.anomaly.AnomalyDetector;
import io.agenttel.core.anomaly.PatternMatcher;
import io.agenttel.core.baseline.DurationParser;
import io.agenttel.core.baseline.RollingBaselineProvider;
import io.agenttel.core.baseline.StaticBaselineProvider;
import io.agenttel.core.enrichment.AgentTelSpanProcessor;
import io.agenttel.core.enrichment.OperationContext;
import io.agenttel.core.enrichment.OperationContextRegistry;
import io.agenttel.core.resource.AgentTelGlobalState;
import io.agenttel.core.slo.SloTracker;
import io.agenttel.core.topology.TopologyRegistry;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;

import java.util.logging.Logger;

/**
 * OTel javaagent extension that enriches spans with AgentTel attributes.
 *
 * <p>This is the zero-code entry point for AgentTel. Users add this jar to the
 * OTel javaagent's extensions path:
 * <pre>
 * java -javaagent:opentelemetry-javaagent.jar \
 *      -Dotel.javaagent.extensions=agenttel-javaagent-extension.jar \
 *      -Dagenttel.config.file=agenttel.yml \
 *      -jar myapp.jar
 * </pre>
 *
 * <p>Loaded via SPI ({@link AutoConfigurationCustomizerProvider}).
 */
public class AgentTelExtensionCustomizer implements AutoConfigurationCustomizerProvider {

    private static final Logger logger = Logger.getLogger(AgentTelExtensionCustomizer.class.getName());

    @Override
    public void customize(AutoConfigurationCustomizer customizer) {
        AgentTelConfig config = AgentTelConfigLoader.load();

        if (!config.isEnabled()) {
            logger.info("AgentTel extension disabled via config");
            return;
        }

        // 1. Build TopologyRegistry from config
        TopologyRegistry topology = buildTopology(config);
        AgentTelGlobalState.initialize(topology);

        // 2. Build baselines + operation contexts (with profile resolution)
        StaticBaselineProvider baselines = new StaticBaselineProvider();
        OperationContextRegistry opContexts = new OperationContextRegistry();
        registerOperations(config, baselines, opContexts);

        // 3. Build SpanProcessor
        AgentTelSpanProcessor processor = new AgentTelSpanProcessor(
                baselines, opContexts,
                new AnomalyDetector(config.getAnomalyDetection().getZScoreThreshold()),
                new PatternMatcher(),
                new RollingBaselineProvider(
                        config.getBaselines().getRollingWindowSize(),
                        config.getBaselines().getRollingMinSamples()),
                new SloTracker(), null);

        // 4. Register with OTel SDK
        customizer.addTracerProviderCustomizer(
                (builder, cfg) -> builder.addSpanProcessor(processor));

        logger.info("AgentTel extension initialized — topology: team=" + topology.getTeam()
                + ", tier=" + topology.getTier().getValue()
                + ", operations=" + config.getOperations().size());
    }

    private static TopologyRegistry buildTopology(AgentTelConfig config) {
        TopologyRegistry registry = new TopologyRegistry();
        var topo = config.getTopology();

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

        // Register dependencies
        for (var dep : config.getDependencies()) {
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

        // Register consumers
        for (var consumer : config.getConsumers()) {
            registry.registerConsumer(new ConsumerDescriptor(
                    consumer.getName(),
                    ConsumptionPattern.fromValue(consumer.getPattern()),
                    consumer.getSlaLatencyMs()
            ));
        }

        return registry;
    }

    private static void registerOperations(AgentTelConfig config,
                                            StaticBaselineProvider baselines,
                                            OperationContextRegistry opContexts) {
        for (var entry : config.getOperations().entrySet()) {
            String operationName = entry.getKey();
            var op = entry.getValue();

            // Resolve profile defaults
            AgentTelConfig.ProfileConfig profile = null;
            if (!op.getProfile().isEmpty()) {
                profile = config.getProfiles().get(op.getProfile());
            }

            // Register baseline
            double p50 = DurationParser.parseToMs(op.getExpectedLatencyP50());
            double p99 = DurationParser.parseToMs(op.getExpectedLatencyP99());
            double errorRate = op.getExpectedErrorRate();
            if (p50 > 0 || p99 > 0 || errorRate >= 0) {
                baselines.register(operationName, OperationBaseline.builder(operationName)
                        .latencyP50Ms(Math.max(p50, 0))
                        .latencyP99Ms(Math.max(p99, 0))
                        .errorRate(Math.max(errorRate, 0))
                        .source(BaselineSource.STATIC)
                        .build());
            }

            // Resolve decision context with profile merge
            boolean retryable, idempotent, safeToRestart;
            String runbookUrl, fallbackDesc, escalationStr;
            if (profile != null) {
                retryable = op.isRetryable() || profile.isRetryable();
                idempotent = op.isIdempotent() || profile.isIdempotent();
                safeToRestart = !op.isSafeToRestart() ? false : profile.isSafeToRestart();
                runbookUrl = !op.getRunbookUrl().isEmpty() ? op.getRunbookUrl() : profile.getRunbookUrl();
                fallbackDesc = !op.getFallbackDescription().isEmpty() ? op.getFallbackDescription() : profile.getFallbackDescription();
                escalationStr = !"auto_resolve".equals(op.getEscalationLevel()) ? op.getEscalationLevel() : profile.getEscalationLevel();
            } else {
                retryable = op.isRetryable();
                idempotent = op.isIdempotent();
                safeToRestart = op.isSafeToRestart();
                runbookUrl = op.getRunbookUrl();
                fallbackDesc = op.getFallbackDescription();
                escalationStr = op.getEscalationLevel();
            }

            EscalationLevel escalation;
            try {
                escalation = EscalationLevel.fromValue(escalationStr);
            } catch (IllegalArgumentException e) {
                escalation = EscalationLevel.AUTO_RESOLVE;
            }

            opContexts.register(operationName, new OperationContext(
                    retryable, idempotent, runbookUrl,
                    fallbackDesc, escalation, safeToRestart));
        }
    }
}
