package io.agenttel.agent.context;

import io.agenttel.agent.action.AgentActionTracker;
import io.agenttel.agent.health.ServiceHealthAggregator;
import io.agenttel.agent.incident.IncidentContext;
import io.agenttel.agent.incident.IncidentContextBuilder;
import io.agenttel.agent.playbook.Playbook;
import io.agenttel.agent.playbook.PlaybookRegistry;
import io.agenttel.agent.remediation.ActionFeedbackLoop;
import io.agenttel.agent.remediation.RemediationAction;
import io.agenttel.agent.remediation.RemediationExecutor;
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
 * Enhanced with playbooks, action feedback, and error analysis.
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

    // Agent-autonomous components (nullable for backward compatibility)
    private PlaybookRegistry playbookRegistry;
    private RemediationExecutor remediationExecutor;

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
     * Configures reporting components.
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
     * Configures agent-autonomous components.
     */
    public void setAutonomousComponents(PlaybookRegistry playbookRegistry,
                                         RemediationExecutor remediationExecutor) {
        this.playbookRegistry = playbookRegistry;
        this.remediationExecutor = remediationExecutor;
    }

    // --- Core Methods ---

    public String getHealthSummary() {
        var summary = healthAggregator.getHealthSummary(topology.getTeam());
        return ContextFormatter.formatHealthCompact(summary);
    }

    public String getHealthSummaryJson() {
        var summary = healthAggregator.getHealthSummary(topology.getTeam());
        return ContextFormatter.formatHealthAsJson(summary);
    }

    public String getIncidentContext(String operationName) {
        List<IncidentPattern> patterns = detectPatternsForOperation(operationName);
        IncidentContext ctx = incidentContextBuilder.buildContext(operationName, patterns);
        return ContextFormatter.formatIncidentFull(ctx);
    }

    public String getIncidentSummary(String operationName) {
        List<IncidentPattern> patterns = detectPatternsForOperation(operationName);
        IncidentContext ctx = incidentContextBuilder.buildContext(operationName, patterns);
        return ContextFormatter.formatIncidentCompact(ctx);
    }

    public IncidentContext getIncidentContextObject(String operationName) {
        List<IncidentPattern> patterns = detectPatternsForOperation(operationName);
        return incidentContextBuilder.buildContext(operationName, patterns);
    }

    private List<IncidentPattern> detectPatternsForOperation(String operationName) {
        if (patternMatcher == null) return List.of();

        Optional<ServiceHealthAggregator.OperationSummary> opHealth =
                healthAggregator.getOperationHealth(operationName);
        if (opHealth.isEmpty()) return List.of();

        var op = opHealth.get();
        RollingWindow.Snapshot snapshot = rollingBaselines != null
                ? rollingBaselines.getSnapshot(operationName).orElse(null)
                : null;

        return patternMatcher.detectPatterns(operationName, op.latencyP50Ms(), op.errorRate() > 0.01, snapshot);
    }

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
            if (action.spec() != null) {
                sb.append("\n    Spec: ").append(formatSpec(action.spec()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // --- Agent-Autonomous Methods ---

    /**
     * Returns a structured playbook for the given operation and/or pattern.
     */
    public String getPlaybook(String operationName, String patternName) {
        if (playbookRegistry == null) return "Playbooks not configured.";

        Optional<Playbook> playbook;
        if (patternName != null && !patternName.isEmpty()) {
            try {
                IncidentPattern pattern = IncidentPattern.fromValue(patternName);
                playbook = playbookRegistry.findForPattern(pattern);
            } catch (IllegalArgumentException e) {
                playbook = playbookRegistry.findByName(patternName);
            }
        } else {
            // Find playbook based on current detected patterns
            List<IncidentPattern> patterns = detectPatternsForOperation(operationName);
            playbook = playbookRegistry.findForPatterns(patterns);
        }

        return playbook.map(Playbook::toFormattedText)
                .orElse("No matching playbook found for " + operationName);
    }

    /**
     * Returns the verification result of a previously executed remediation action.
     */
    public String verifyRemediationEffect(String actionName) {
        if (remediationExecutor == null) return "Remediation executor not configured.";

        Optional<ActionFeedbackLoop.ActionOutcome> outcome =
                remediationExecutor.getActionOutcome(actionName);

        if (outcome.isEmpty()) {
            return "No verification result available for '" + actionName
                    + "'. Verification may still be pending (default delay: 30s).";
        }

        var o = outcome.get();
        StringBuilder sb = new StringBuilder("REMEDIATION VERIFICATION:\n");
        sb.append("  Action: ").append(o.actionName()).append("\n");
        sb.append("  Effective: ").append(o.effective() ? "YES" : "NO").append("\n");
        sb.append("  Latency delta: ").append(String.format("%.1fms", o.latencyDeltaMs())).append("\n");
        sb.append("  Error rate delta: ").append(String.format("%.4f", o.errorRateDelta())).append("\n");
        sb.append("  Health: ").append(o.preHealthStatus())
                .append(" → ").append(o.postHealthStatus()).append("\n");
        sb.append("  Verified at: ").append(o.verifiedAt()).append("\n");
        return sb.toString();
    }

    /**
     * Returns error category breakdown for an operation.
     */
    public String getErrorAnalysis(String operationName) {
        var opHealth = healthAggregator.getOperationHealth(operationName);
        if (opHealth.isEmpty()) {
            return "No data available for operation: " + operationName;
        }

        var op = opHealth.get();
        StringBuilder sb = new StringBuilder("ERROR ANALYSIS: ").append(operationName).append("\n");
        sb.append("  Total requests: ").append(op.totalRequests()).append("\n");
        sb.append("  Error count: ").append(op.errorCount()).append("\n");
        sb.append("  Error rate: ").append(String.format("%.2f%%", op.errorRate() * 100)).append("\n");
        sb.append("  Deviation: ").append(op.deviationStatus()).append("\n");

        // Baseline confidence
        if (rollingBaselines != null) {
            rollingBaselines.getSnapshot(operationName).ifPresent(snapshot -> {
                sb.append("  Baseline confidence: ").append(snapshot.confidence())
                        .append(" (").append(snapshot.sampleCount()).append(" samples)\n");
            });
        }

        return sb.toString();
    }

    // --- Reporting Methods ---

    public String getSloReport(String format) {
        if (sloReportGenerator == null) return "SLO reporting not configured.";
        if ("json".equals(format)) {
            return sloReportGenerator.generateReportJson();
        }
        return sloReportGenerator.generateReport();
    }

    public String getTrendAnalysis(String operationName, int windowMinutes) {
        if (trendAnalyzer == null) return "Trend analysis not configured.";
        return trendAnalyzer.analyzeTrend(operationName, windowMinutes);
    }

    public String getExecutiveSummary() {
        if (executiveSummaryBuilder == null) return "Executive summary not configured.";
        return executiveSummaryBuilder.buildSummary();
    }

    public String getCrossStackContext(String operationName) {
        if (crossStackContextBuilder == null) return "Cross-stack context not configured.";
        return crossStackContextBuilder.buildContext(operationName);
    }

    private String formatSpec(io.agenttel.agent.remediation.ActionSpec spec) {
        if (spec instanceof io.agenttel.agent.remediation.ActionSpec.RetrySpec r) {
            return String.format("retry(max=%d, backoff=%s)", r.maxAttempts(), r.backoffMs());
        } else if (spec instanceof io.agenttel.agent.remediation.ActionSpec.ScaleSpec s) {
            return String.format("scale(%s, min=%d, max=%d, cooldown=%ds)",
                    s.direction(), s.minInstances(), s.maxInstances(), s.cooldownSeconds());
        } else if (spec instanceof io.agenttel.agent.remediation.ActionSpec.CircuitBreakerSpec c) {
            return String.format("circuit_breaker(threshold=%d, half_open_after=%dms)",
                    c.failureThreshold(), c.halfOpenAfterMs());
        } else if (spec instanceof io.agenttel.agent.remediation.ActionSpec.RateLimitSpec r) {
            return String.format("rate_limit(%d rps, burst=%d)", r.requestsPerSecond(), r.burstSize());
        } else if (spec instanceof io.agenttel.agent.remediation.ActionSpec.GenericSpec g) {
            return "params=" + g.parameters();
        }
        return spec.type();
    }
}
