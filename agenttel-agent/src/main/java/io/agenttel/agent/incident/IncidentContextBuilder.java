package io.agenttel.agent.incident;

import io.agenttel.agent.health.ServiceHealthAggregator;
import io.agenttel.agent.remediation.RemediationRegistry;
import io.agenttel.agent.remediation.RemediationAction;
import io.agenttel.core.anomaly.IncidentPattern;
import io.agenttel.core.baseline.RollingBaselineProvider;
import io.agenttel.core.baseline.RollingWindow;
import io.agenttel.core.topology.TopologyRegistry;

import java.time.Instant;
import java.util.*;

/**
 * Builds a complete {@link IncidentContext} from current system state.
 * This is the primary API for AI agents to get a full picture of an incident.
 */
public class IncidentContextBuilder {

    private final ServiceHealthAggregator healthAggregator;
    private final TopologyRegistry topology;
    private final RollingBaselineProvider rollingBaselines;
    private final RemediationRegistry remediationRegistry;
    private final List<IncidentContext.HistoricalIncident> historicalIncidents = Collections.synchronizedList(new ArrayList<>());
    private final List<IncidentContext.RecentChange> recentChanges = Collections.synchronizedList(new ArrayList<>());

    public IncidentContextBuilder(ServiceHealthAggregator healthAggregator,
                                   TopologyRegistry topology,
                                   RollingBaselineProvider rollingBaselines,
                                   RemediationRegistry remediationRegistry) {
        this.healthAggregator = healthAggregator;
        this.topology = topology;
        this.rollingBaselines = rollingBaselines;
        this.remediationRegistry = remediationRegistry;
    }

    /**
     * Records a deployment for change tracking.
     */
    public void recordDeployment(String version, String timestamp) {
        recentChanges.add(new IncidentContext.RecentChange("deployment", "Deployed version " + version, timestamp));
    }

    /**
     * Records a config change for change tracking.
     */
    public void recordConfigChange(String description) {
        recentChanges.add(new IncidentContext.RecentChange("config_change", description, Instant.now().toString()));
    }

    /**
     * Records a historical incident for pattern matching.
     */
    public void recordHistoricalIncident(String incidentId, String timestamp,
                                          String resolution, String rootCause) {
        historicalIncidents.add(new IncidentContext.HistoricalIncident(
                incidentId, timestamp, resolution, rootCause));
        // Keep bounded
        while (historicalIncidents.size() > 100) {
            historicalIncidents.remove(0);
        }
    }

    /**
     * Builds a full incident context for the given operation.
     */
    public IncidentContext buildContext(String operationName, List<IncidentPattern> detectedPatterns) {
        String incidentId = "inc-" + UUID.randomUUID().toString().substring(0, 8);
        String timestamp = Instant.now().toString();

        // What is happening
        ServiceHealthAggregator.ServiceHealthSummary healthSummary =
                healthAggregator.getHealthSummary(topology.getTeam());

        Optional<ServiceHealthAggregator.OperationSummary> opHealth =
                healthAggregator.getOperationHealth(operationName);

        double currentErrorRate = opHealth.map(ServiceHealthAggregator.OperationSummary::errorRate).orElse(0.0);
        double currentP50 = opHealth.map(ServiceHealthAggregator.OperationSummary::latencyP50Ms).orElse(0.0);

        RollingWindow.Snapshot baseline = rollingBaselines != null
                ? rollingBaselines.getSnapshot(operationName).orElse(null) : null;
        double baselineErrorRate = baseline != null ? baseline.errorRate() : 0.0;
        double baselineP50 = baseline != null ? baseline.p50() : 0.0;

        double anomalyScore = 0;
        if (baseline != null && baseline.stddev() > 0) {
            anomalyScore = Math.min(1.0, Math.abs((currentP50 - baseline.mean()) / baseline.stddev()) / 4.0);
        }

        String description = buildDescription(operationName, detectedPatterns, currentErrorRate, currentP50, baselineP50);

        IncidentContext.WhatIsHappening whatIsHappening = new IncidentContext.WhatIsHappening(
                operationName, description, detectedPatterns,
                currentErrorRate, baselineErrorRate,
                currentP50, baselineP50, anomalyScore,
                healthSummary.status());

        // What changed
        String lastDeployVersion = "";
        String lastDeployTime = "";
        for (int i = recentChanges.size() - 1; i >= 0; i--) {
            if ("deployment".equals(recentChanges.get(i).type())) {
                lastDeployVersion = recentChanges.get(i).description();
                lastDeployTime = recentChanges.get(i).timestamp();
                break;
            }
        }
        IncidentContext.WhatChanged whatChanged = new IncidentContext.WhatChanged(
                new ArrayList<>(recentChanges), lastDeployVersion, lastDeployTime);

        // What is affected
        List<String> affectedOps = new ArrayList<>();
        for (var op : healthSummary.operations()) {
            if (!"normal".equals(op.deviationStatus())) {
                affectedOps.add(op.operationName());
            }
        }
        List<String> affectedDeps = new ArrayList<>();
        for (var dep : healthSummary.dependencies()) {
            if (dep.errorRate() > 0.10) {
                affectedDeps.add(dep.name());
            }
        }
        List<String> consumers = topology.getConsumers().stream()
                .map(c -> c.name())
                .toList();

        IncidentContext.WhatIsAffected whatIsAffected = new IncidentContext.WhatIsAffected(
                affectedOps, affectedDeps, consumers,
                affectedOps.size() > 3 ? "service_wide" : "operation_specific",
                topology.getTier().getValue().equals("critical"));

        // What to do
        List<IncidentContext.SuggestedAction> actions = buildSuggestedActions(operationName, detectedPatterns);
        IncidentContext.WhatToDo whatToDo = new IncidentContext.WhatToDo(
                "", // runbook url from operation context would go here
                topology.getTier().getValue().equals("critical") ? "page_oncall" : "notify",
                true, true, "",
                actions);

        // Severity
        IncidentContext.Severity severity = determineSeverity(healthSummary.status(), detectedPatterns, currentErrorRate);

        return new IncidentContext(
                incidentId, timestamp, severity,
                description, whatIsHappening, whatChanged, whatIsAffected, whatToDo,
                findSimilarIncidents(detectedPatterns));
    }

    private String buildDescription(String op, List<IncidentPattern> patterns,
                                     double errorRate, double currentP50, double baselineP50) {
        StringBuilder sb = new StringBuilder();
        sb.append("Operation '").append(op).append("' is experiencing ");
        if (!patterns.isEmpty()) {
            sb.append(patterns.get(0).getDescription().toLowerCase());
        } else if (errorRate > 0.01) {
            sb.append(String.format("elevated error rate (%.1f%%)", errorRate * 100));
        } else if (baselineP50 > 0 && currentP50 > baselineP50 * 2) {
            sb.append(String.format("latency degradation (P50: %.0fms vs baseline %.0fms)", currentP50, baselineP50));
        } else {
            sb.append("anomalous behavior");
        }
        return sb.toString();
    }

    private List<IncidentContext.SuggestedAction> buildSuggestedActions(
            String operationName, List<IncidentPattern> patterns) {
        List<IncidentContext.SuggestedAction> actions = new ArrayList<>();

        // Add remediation actions from registry
        if (remediationRegistry != null) {
            for (RemediationAction action : remediationRegistry.getActions(operationName)) {
                actions.add(new IncidentContext.SuggestedAction(
                        action.name(), action.description(),
                        "high", action.requiresApproval()));
            }
        }

        // Pattern-specific suggestions
        for (IncidentPattern pattern : patterns) {
            switch (pattern) {
                case CASCADE_FAILURE -> actions.add(new IncidentContext.SuggestedAction(
                        "enable_circuit_breakers",
                        "Enable circuit breakers on failing dependencies to prevent cascade",
                        "high", false));
                case LATENCY_DEGRADATION -> actions.add(new IncidentContext.SuggestedAction(
                        "scale_horizontally",
                        "Scale up service instances to handle load",
                        "medium", true));
                case ERROR_RATE_SPIKE -> actions.add(new IncidentContext.SuggestedAction(
                        "rollback_deployment",
                        "Rollback to previous known-good version",
                        "medium", true));
                case MEMORY_LEAK -> actions.add(new IncidentContext.SuggestedAction(
                        "restart_instances",
                        "Rolling restart of service instances to reclaim memory",
                        "high", true));
                default -> {}
            }
        }

        if (actions.isEmpty()) {
            actions.add(new IncidentContext.SuggestedAction(
                    "investigate",
                    "Check recent logs and traces for root cause",
                    "low", false));
        }

        return actions;
    }

    private IncidentContext.Severity determineSeverity(
            ServiceHealthAggregator.HealthStatus health,
            List<IncidentPattern> patterns, double errorRate) {
        if (health == ServiceHealthAggregator.HealthStatus.CRITICAL) return IncidentContext.Severity.CRITICAL;
        if (patterns.contains(IncidentPattern.CASCADE_FAILURE)) return IncidentContext.Severity.CRITICAL;
        if (errorRate > 0.10) return IncidentContext.Severity.HIGH;
        if (health == ServiceHealthAggregator.HealthStatus.DEGRADED) return IncidentContext.Severity.MEDIUM;
        return IncidentContext.Severity.LOW;
    }

    private List<IncidentContext.HistoricalIncident> findSimilarIncidents(List<IncidentPattern> patterns) {
        if (patterns.isEmpty() || historicalIncidents.isEmpty()) return Collections.emptyList();
        // Simple: return recent historical incidents (in production, match by pattern)
        int count = Math.min(3, historicalIncidents.size());
        return new ArrayList<>(historicalIncidents.subList(
                historicalIncidents.size() - count, historicalIncidents.size()));
    }
}
