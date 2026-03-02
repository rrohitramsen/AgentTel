package io.agenttel.agent.context;

import io.agenttel.agent.action.AgentActionTracker;
import io.agenttel.agent.health.ServiceHealthAggregator;
import io.agenttel.agent.incident.IncidentContext;
import io.agenttel.agent.incident.IncidentContextBuilder;
import io.agenttel.agent.remediation.RemediationRegistry;
import io.agenttel.agent.reporting.CrossStackContextBuilder;
import io.agenttel.agent.reporting.ExecutiveSummaryBuilder;
import io.agenttel.agent.reporting.SloReportGenerator;
import io.agenttel.agent.reporting.TrendAnalyzer;
import io.agenttel.core.anomaly.IncidentPattern;
import io.agenttel.core.baseline.RollingBaselineProvider;
import io.agenttel.core.baseline.RollingWindow;
import io.agenttel.core.anomaly.PatternMatcher;
import io.agenttel.core.topology.TopologyRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Central provider that assembles complete context for AI agents.
 * This is the single entry point agents use to understand system state.
 */
public class AgentContextProvider {

    private final ServiceHealthAggregator healthAggregator;
    private final IncidentContextBuilder incidentContextBuilder;
    private final RemediationRegistry remediationRegistry;
    private final TopologyRegistry topology;
    private final PatternMatcher patternMatcher;
    private final RollingBaselineProvider rollingBaselines;
    private final AgentActionTracker actionTracker;

    // Reporting components (nullable for backward compatibility)
    private SloReportGenerator sloReportGenerator;
    private TrendAnalyzer trendAnalyzer;
    private ExecutiveSummaryBuilder executiveSummaryBuilder;
    private CrossStackContextBuilder crossStackContextBuilder;

    public AgentContextProvider(ServiceHealthAggregator healthAggregator,
                                 IncidentContextBuilder incidentContextBuilder,
                                 RemediationRegistry remediationRegistry,
                                 TopologyRegistry topology,
                                 PatternMatcher patternMatcher,
                                 RollingBaselineProvider rollingBaselines,
                                 AgentActionTracker actionTracker) {
        this.healthAggregator = healthAggregator;
        this.incidentContextBuilder = incidentContextBuilder;
        this.remediationRegistry = remediationRegistry;
        this.topology = topology;
        this.patternMatcher = patternMatcher;
        this.rollingBaselines = rollingBaselines;
        this.actionTracker = actionTracker;
    }

    /**
     * Configures reporting components. Called after construction by auto-configuration.
     */
    public void setReportingComponents(SloReportGenerator sloReportGenerator,
                                        TrendAnalyzer trendAnalyzer,
                                        ExecutiveSummaryBuilder executiveSummaryBuilder,
                                        CrossStackContextBuilder crossStackContextBuilder) {
        this.sloReportGenerator = sloReportGenerator;
        this.trendAnalyzer = trendAnalyzer;
        this.executiveSummaryBuilder = executiveSummaryBuilder;
        this.crossStackContextBuilder = crossStackContextBuilder;
    }

    /**
     * Returns a compact health summary string for system prompt injection.
     */
    public String getHealthSummary() {
        var summary = healthAggregator.getHealthSummary(topology.getTeam());
        return ContextFormatter.formatHealthCompact(summary);
    }

    /**
     * Returns a JSON health summary for structured tool results.
     */
    public String getHealthSummaryJson() {
        var summary = healthAggregator.getHealthSummary(topology.getTeam());
        return ContextFormatter.formatHealthAsJson(summary);
    }

    /**
     * Returns a full incident context for the given operation.
     */
    public String getIncidentContext(String operationName) {
        List<IncidentPattern> patterns = detectPatternsForOperation(operationName);
        IncidentContext ctx = incidentContextBuilder.buildContext(operationName, patterns);
        return ContextFormatter.formatIncidentFull(ctx);
    }

    /**
     * Returns a compact incident summary.
     */
    public String getIncidentSummary(String operationName) {
        List<IncidentPattern> patterns = detectPatternsForOperation(operationName);
        IncidentContext ctx = incidentContextBuilder.buildContext(operationName, patterns);
        return ContextFormatter.formatIncidentCompact(ctx);
    }

    /**
     * Returns the raw incident context object for programmatic access.
     */
    public IncidentContext getIncidentContextObject(String operationName) {
        List<IncidentPattern> patterns = detectPatternsForOperation(operationName);
        return incidentContextBuilder.buildContext(operationName, patterns);
    }

    private List<IncidentPattern> detectPatternsForOperation(String operationName) {
        if (patternMatcher == null) return List.of();

        // Get current operation health to feed into pattern detection
        Optional<ServiceHealthAggregator.OperationSummary> opHealth =
                healthAggregator.getOperationHealth(operationName);
        if (opHealth.isEmpty()) return List.of();

        var op = opHealth.get();
        RollingWindow.Snapshot snapshot = rollingBaselines != null
                ? rollingBaselines.getSnapshot(operationName).orElse(null)
                : null;

        return patternMatcher.detectPatterns(operationName, op.latencyP50Ms(), op.errorRate() > 0.01, snapshot);
    }

    /**
     * Returns recent agent action history as formatted text.
     */
    public String getRecentActions() {
        var actions = actionTracker.getRecentActions();
        if (actions.isEmpty()) return "No recent agent actions.";

        StringBuilder sb = new StringBuilder("RECENT AGENT ACTIONS:\n");
        for (var action : actions) {
            sb.append("  [").append(action.status().toUpperCase()).append("] ")
                    .append(action.name())
                    .append(" (").append(action.type()).append(")")
                    .append(" - ").append(action.reason())
                    .append(" @ ").append(action.timestamp())
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * Returns available remediation actions as formatted text.
     */
    public String getAvailableActions(String operationName) {
        var actions = remediationRegistry.getActions(operationName);
        if (actions.isEmpty()) return "No remediation actions available for " + operationName;

        StringBuilder sb = new StringBuilder("AVAILABLE REMEDIATION ACTIONS:\n");
        for (var action : actions) {
            sb.append("  - ").append(action.name())
                    .append(": ").append(action.description())
                    .append(" [").append(action.type()).append("]");
            if (action.requiresApproval()) {
                sb.append(" (NEEDS APPROVAL)");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // --- Reporting Methods ---

    /**
     * Returns an SLO compliance report across all tracked operations.
     */
    public String getSloReport(String format) {
        if (sloReportGenerator == null) return "SLO reporting not configured.";
        if ("json".equals(format)) {
            return sloReportGenerator.generateReportJson();
        }
        return sloReportGenerator.generateReport();
    }

    /**
     * Returns trend analysis for a specific operation over a time window.
     */
    public String getTrendAnalysis(String operationName, int windowMinutes) {
        if (trendAnalyzer == null) return "Trend analysis not configured.";
        return trendAnalyzer.analyzeTrend(operationName, windowMinutes);
    }

    /**
     * Returns a high-level executive summary optimized for LLM context.
     */
    public String getExecutiveSummary() {
        if (executiveSummaryBuilder == null) return "Executive summary not configured.";
        return executiveSummaryBuilder.buildSummary();
    }

    /**
     * Returns correlated cross-stack context for an operation.
     */
    public String getCrossStackContext(String operationName) {
        if (crossStackContextBuilder == null) return "Cross-stack context not configured.";
        return crossStackContextBuilder.buildContext(operationName);
    }
}
