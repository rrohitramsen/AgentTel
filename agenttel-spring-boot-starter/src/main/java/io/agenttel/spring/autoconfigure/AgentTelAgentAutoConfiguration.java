package io.agenttel.spring.autoconfigure;

import io.agenttel.agent.action.AgentActionTracker;
import io.agenttel.agent.context.AgentContextProvider;
import io.agenttel.agent.correlation.ChangeCorrelationEngine;
import io.agenttel.agent.health.ServiceHealthAggregator;
import io.agenttel.agent.identity.ToolPermission;
import io.agenttel.agent.identity.ToolPermissionRegistry;
import io.agenttel.agent.incident.IncidentContextBuilder;
import io.agenttel.agent.mcp.AgentTelMcpServerBuilder;
import io.agenttel.agent.mcp.McpServer;
import io.agenttel.agent.playbook.PlaybookRegistry;
import io.agenttel.agent.remediation.ActionFeedbackLoop;
import io.agenttel.agent.remediation.RemediationExecutor;
import io.agenttel.agent.remediation.RemediationRegistry;
import io.agenttel.agent.reporting.CrossStackContextBuilder;
import io.agenttel.agent.reporting.ExecutiveSummaryBuilder;
import io.agenttel.agent.reporting.SloReportGenerator;
import io.agenttel.agent.reporting.TrendAnalyzer;
import io.agenttel.agent.session.SessionManager;
import io.agenttel.core.anomaly.PatternMatcher;
import io.agenttel.core.baseline.RollingBaselineProvider;
import io.agenttel.core.enrichment.AgentTelSpanProcessor;
import io.agenttel.core.slo.SloTracker;
import io.agenttel.core.topology.TopologyRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    public ToolPermissionRegistry agentTelToolPermissionRegistry(AgentTelProperties properties) {
        ToolPermissionRegistry registry = new ToolPermissionRegistry();
        Map<String, List<String>> roleConfig = properties.getAgentRoles();
        if (roleConfig != null) {
            for (Map.Entry<String, List<String>> entry : roleConfig.entrySet()) {
                Set<ToolPermission> permissions = entry.getValue().stream()
                        .map(ToolPermission::fromValue)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(ToolPermission.class)));
                registry.setRolePermissions(entry.getKey(), permissions);
            }
        }
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionManager agentTelSessionManager() {
        return new SessionManager();
    }

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
    public PlaybookRegistry agentTelPlaybookRegistry() {
        return new PlaybookRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ChangeCorrelationEngine agentTelChangeCorrelationEngine() {
        return new ChangeCorrelationEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionFeedbackLoop agentTelActionFeedbackLoop(ServiceHealthAggregator healthAggregator) {
        return new ActionFeedbackLoop(healthAggregator);
    }

    @Bean
    @ConditionalOnMissingBean
    public IncidentContextBuilder agentTelIncidentContextBuilder(
            ServiceHealthAggregator healthAggregator,
            TopologyRegistry topology,
            RollingBaselineProvider rollingBaselines,
            RemediationRegistry remediationRegistry,
            PlaybookRegistry playbookRegistry,
            ChangeCorrelationEngine changeCorrelationEngine) {
        IncidentContextBuilder builder = new IncidentContextBuilder(
                healthAggregator, topology, rollingBaselines, remediationRegistry);
        builder.setEnhancedComponents(playbookRegistry, changeCorrelationEngine);
        return builder;
    }

    @Bean
    @ConditionalOnMissingBean
    public RemediationExecutor agentTelRemediationExecutor(RemediationRegistry registry,
                                                            AgentActionTracker actionTracker,
                                                            ActionFeedbackLoop feedbackLoop) {
        return new RemediationExecutor(registry, actionTracker, feedbackLoop);
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
            CrossStackContextBuilder crossStackContextBuilder,
            PlaybookRegistry playbookRegistry,
            RemediationExecutor remediationExecutor) {
        AgentContextProvider provider = new AgentContextProvider(
                healthAggregator, incidentContextBuilder, remediationRegistry,
                topology, patternMatcher, rollingBaselines, actionTracker);
        provider.setReportingComponents(sloReportGenerator, trendAnalyzer,
                executiveSummaryBuilder, crossStackContextBuilder);
        provider.setAutonomousComponents(playbookRegistry, remediationExecutor);
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
    @ConditionalOnMissingBean
    public McpServer agentTelMcpServer(
            AgentContextProvider contextProvider,
            RemediationExecutor remediationExecutor,
            ToolPermissionRegistry permissionRegistry,
            SessionManager sessionManager,
            @Value("${agenttel.mcp.port:8081}") int mcpPort) {
        return new AgentTelMcpServerBuilder()
                .port(mcpPort)
                .contextProvider(contextProvider)
                .remediationExecutor(remediationExecutor)
                .permissionRegistry(permissionRegistry)
                .sessionManager(sessionManager)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "agentTelMcpServerStarter")
    public ApplicationRunner agentTelMcpServerStarter(McpServer mcpServer) {
        return args -> {
            mcpServer.start();
            log.info("AgentTel MCP server started on port {}", mcpServer.getPort());
        };
    }
}
