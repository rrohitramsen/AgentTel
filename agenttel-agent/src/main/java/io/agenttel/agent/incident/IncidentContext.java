package io.agenttel.agent.incident;

import io.agenttel.agent.health.ServiceHealthAggregator;
import io.agenttel.core.anomaly.IncidentPattern;

import java.util.List;
import java.util.Map;

/**
 * A complete incident context package — everything an AI agent needs
 * to understand and respond to a production incident in one object.
 */
public record IncidentContext(
        String incidentId,
        String timestamp,
        Severity severity,
        String summary,
        WhatIsHappening whatIsHappening,
        WhatChanged whatChanged,
        WhatIsAffected whatIsAffected,
        WhatToDo whatToDo,
        List<HistoricalIncident> similarPastIncidents
) {

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public record WhatIsHappening(
            String operationName,
            String description,
            List<IncidentPattern> detectedPatterns,
            double currentErrorRate,
            double baselineErrorRate,
            double currentLatencyP50Ms,
            double baselineLatencyP50Ms,
            double anomalyScore,
            ServiceHealthAggregator.HealthStatus serviceHealth
    ) {}

    public record WhatChanged(
            List<RecentChange> recentChanges,
            String lastDeploymentVersion,
            String lastDeploymentTimestamp
    ) {}

    public record RecentChange(
            String type,
            String description,
            String timestamp
    ) {}

    public record WhatIsAffected(
            List<String> affectedOperations,
            List<String> affectedDependencies,
            List<String> affectedConsumers,
            String impactScope,
            boolean isUserFacing
    ) {}

    public record WhatToDo(
            String runbookUrl,
            String escalationLevel,
            boolean isRetryable,
            boolean isIdempotent,
            String fallbackDescription,
            List<SuggestedAction> suggestedActions
    ) {}

    public record SuggestedAction(
            String action,
            String description,
            String confidence,
            boolean requiresApproval
    ) {}

    public record HistoricalIncident(
            String incidentId,
            String timestamp,
            String resolution,
            String rootCause
    ) {}
}
