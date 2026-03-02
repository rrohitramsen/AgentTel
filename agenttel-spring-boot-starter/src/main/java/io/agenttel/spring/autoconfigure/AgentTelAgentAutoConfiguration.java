package io.agenttel.spring.autoconfigure;

import io.agenttel.agent.action.AgentActionTracker;
import io.agenttel.agent.context.AgentContextProvider;
import io.agenttel.agent.health.ServiceHealthAggregator;
import io.agenttel.agent.incident.IncidentContextBuilder;
import io.agenttel.agent.mcp.AgentTelMcpServerBuilder;
import io.agenttel.agent.mcp.McpServer;
import io.agenttel.agent.remediation.RemediationExecutor;
import io.agenttel.agent.remediation.RemediationRegistry;
import io.agenttel.agent.reporting.CrossStackContextBuilder;
import io.agenttel.agent.reporting.ExecutiveSummaryBuilder;
import io.agenttel.agent.reporting.SloReportGenerator;
import io.agenttel.agent.reporting.TrendAnalyzer;
import io.agenttel.core.anomaly.PatternMatcher;
import io.agenttel.core.baseline.RollingBaselineProvider;
import io.agenttel.core.enrichment.AgentTelSpanProcessor;
import io.agenttel.core.slo.SloTracker;
import io.agenttel.core.topology.TopologyRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the AgentTel agent layer (MCP server, health aggregation,
 * incident context, remediation). Only activates when agenttel-agent is on the classpath.
 */
@AutoConfiguration(after = AgentTelAutoConfiguration.class)
@ConditionalOnClass(name = "io.agenttel.agent.mcp.AgentTelMcpServerBuilder")
public class AgentTelAgentAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentTelAgentAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ServiceHealthAggregator agentTelHealthAggregator(RollingBaselineProvider rollingBaselines,
                                                              SloTracker sloTracker) {
        return new ServiceHealthAggregator(rollingBaselines, sloTracker);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentActionTracker agentTelActionTracker(OpenTelemetry otel) {
        return new AgentActionTracker(otel);
    }

    @Bean
    @ConditionalOnMissingBean
    public RemediationRegistry agentTelRemediationRegistry() {
        return new RemediationRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public IncidentContextBuilder agentTelIncidentContextBuilder(
            ServiceHealthAggregator healthAggregator,
            TopologyRegistry topology,
            RollingBaselineProvider rollingBaselines,
            RemediationRegistry remediationRegistry) {
        return new IncidentContextBuilder(healthAggregator, topology, rollingBaselines, remediationRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public RemediationExecutor agentTelRemediationExecutor(RemediationRegistry registry,
                                                            AgentActionTracker actionTracker) {
        return new RemediationExecutor(registry, actionTracker);
    }

    @Bean
    @ConditionalOnMissingBean
    public SloReportGenerator agentTelSloReportGenerator(SloTracker sloTracker) {
        return new SloReportGenerator(sloTracker);
    }

    @Bean
    @ConditionalOnMissingBean
    public TrendAnalyzer agentTelTrendAnalyzer(ServiceHealthAggregator healthAggregator,
                                                 TopologyRegistry topology) {
        return new TrendAnalyzer(healthAggregator, topology.getTeam());
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutiveSummaryBuilder agentTelExecutiveSummaryBuilder(
            ServiceHealthAggregator healthAggregator,
            SloTracker sloTracker,
            TrendAnalyzer trendAnalyzer,
            TopologyRegistry topology) {
        return new ExecutiveSummaryBuilder(healthAggregator, sloTracker, trendAnalyzer, topology.getTeam());
    }

    @Bean
    @ConditionalOnMissingBean
    public CrossStackContextBuilder agentTelCrossStackContextBuilder(
            ServiceHealthAggregator healthAggregator,
            SloTracker sloTracker,
            TopologyRegistry topology) {
        return new CrossStackContextBuilder(healthAggregator, sloTracker, topology.getTeam());
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentContextProvider agentTelContextProvider(
            ServiceHealthAggregator healthAggregator,
            IncidentContextBuilder incidentContextBuilder,
            RemediationRegistry remediationRegistry,
            TopologyRegistry topology,
            PatternMatcher patternMatcher,
            RollingBaselineProvider rollingBaselines,
            AgentActionTracker actionTracker,
            SloReportGenerator sloReportGenerator,
            TrendAnalyzer trendAnalyzer,
            ExecutiveSummaryBuilder executiveSummaryBuilder,
            CrossStackContextBuilder crossStackContextBuilder) {
        AgentContextProvider provider = new AgentContextProvider(
                healthAggregator, incidentContextBuilder, remediationRegistry,
                topology, patternMatcher, rollingBaselines, actionTracker);
        provider.setReportingComponents(sloReportGenerator, trendAnalyzer,
                executiveSummaryBuilder, crossStackContextBuilder);
        return provider;
    }

    @Bean
    public ApplicationRunner agentTelHealthAggregatorWiring(
            AgentTelSpanProcessor spanProcessor,
            ServiceHealthAggregator healthAggregator) {
        return args -> {
            spanProcessor.setSpanCompletionListener(healthAggregator::recordSpan);
            log.info("AgentTel health aggregation wired to span processor");
        };
    }

    @Bean
    public ApplicationRunner agentTelMcpServerStarter(
            AgentContextProvider contextProvider,
            RemediationExecutor remediationExecutor) {
        return args -> {
            McpServer server = new AgentTelMcpServerBuilder()
                    .port(8081)
                    .contextProvider(contextProvider)
                    .remediationExecutor(remediationExecutor)
                    .build();
            server.start();
            log.info("AgentTel MCP server started on port 8081");
        };
    }
}
