package io.agenttel.agent.context;

import io.agenttel.agent.health.ServiceHealthAggregator;
import io.agenttel.agent.incident.IncidentContext;

/**
 * Formats telemetry data into prompt-optimized text for LLM consumption.
 * Each format is designed for a specific context window budget.
 */
public class ContextFormatter {

    /**
     * Compact health summary — fits in ~200 tokens.
     * Use as a system prompt prefix or tool result.
     */
    public static String formatHealthCompact(ServiceHealthAggregator.ServiceHealthSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("SERVICE: ").append(summary.serviceName())
                .append(" | STATUS: ").append(summary.status())
                .append(" | ").append(summary.timestamp()).append("\n");

        if (!summary.operations().isEmpty()) {
            sb.append("OPERATIONS:\n");
            for (var op : summary.operations()) {
                sb.append("  ").append(op.operationName())
                        .append(": err=").append(formatPercent(op.errorRate()))
                        .append(" p50=").append(formatMs(op.latencyP50Ms()))
                        .append(" p99=").append(formatMs(op.latencyP99Ms()));
                if (!"normal".equals(op.deviationStatus())) {
                    sb.append(" [").append(op.deviationStatus().toUpperCase()).append("]");
                }
                sb.append("\n");
            }
        }

        if (!summary.dependencies().isEmpty()) {
            sb.append("DEPENDENCIES:\n");
            for (var dep : summary.dependencies()) {
                sb.append("  ").append(dep.name())
                        .append(": err=").append(formatPercent(dep.errorRate()))
                        .append(" avg=").append(formatMs(dep.latencyMeanMs()))
                        .append("\n");
            }
        }

        if (!summary.sloStatuses().isEmpty()) {
            sb.append("SLOs:\n");
            for (var slo : summary.sloStatuses()) {
                sb.append("  ").append(slo.sloName())
                        .append(": budget=").append(formatPercent(slo.budgetRemaining()))
                        .append(" burn=").append(String.format("%.1fx", slo.burnRate()))
                        .append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Full incident context — structured for agent reasoning (~800 tokens).
     * Use when an agent needs to diagnose and act on an incident.
     */
    public static String formatIncidentFull(IncidentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== INCIDENT ").append(ctx.incidentId()).append(" ===\n");
        sb.append("SEVERITY: ").append(ctx.severity()).append("\n");
        sb.append("TIME: ").append(ctx.timestamp()).append("\n");
        sb.append("SUMMARY: ").append(ctx.summary()).append("\n\n");

        // What is happening
        var what = ctx.whatIsHappening();
        sb.append("## WHAT IS HAPPENING\n");
        sb.append("Operation: ").append(what.operationName()).append("\n");
        sb.append("Error Rate: ").append(formatPercent(what.currentErrorRate()))
                .append(" (baseline: ").append(formatPercent(what.baselineErrorRate())).append(")\n");
        sb.append("Latency P50: ").append(formatMs(what.currentLatencyP50Ms()))
                .append(" (baseline: ").append(formatMs(what.baselineLatencyP50Ms())).append(")\n");
        sb.append("Anomaly Score: ").append(String.format("%.2f", what.anomalyScore())).append("\n");
        sb.append("Service Health: ").append(what.serviceHealth()).append("\n");
        if (!what.detectedPatterns().isEmpty()) {
            sb.append("Patterns: ");
            for (int i = 0; i < what.detectedPatterns().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(what.detectedPatterns().get(i).name());
            }
            sb.append("\n");
        }

        // What changed
        var changed = ctx.whatChanged();
        sb.append("\n## WHAT CHANGED\n");
        if (!changed.lastDeploymentVersion().isEmpty()) {
            sb.append("Last Deploy: ").append(changed.lastDeploymentVersion())
                    .append(" at ").append(changed.lastDeploymentTimestamp()).append("\n");
        }
        for (var change : changed.recentChanges()) {
            sb.append("  [").append(change.type()).append("] ")
                    .append(change.description()).append(" (").append(change.timestamp()).append(")\n");
        }

        // What is affected
        var affected = ctx.whatIsAffected();
        sb.append("\n## WHAT IS AFFECTED\n");
        sb.append("Scope: ").append(affected.impactScope()).append("\n");
        sb.append("User-Facing: ").append(affected.isUserFacing() ? "YES" : "no").append("\n");
        if (!affected.affectedOperations().isEmpty()) {
            sb.append("Affected Ops: ").append(String.join(", ", affected.affectedOperations())).append("\n");
        }
        if (!affected.affectedDependencies().isEmpty()) {
            sb.append("Affected Deps: ").append(String.join(", ", affected.affectedDependencies())).append("\n");
        }
        if (!affected.affectedConsumers().isEmpty()) {
            sb.append("Affected Consumers: ").append(String.join(", ", affected.affectedConsumers())).append("\n");
        }

        // What to do
        var todo = ctx.whatToDo();
        sb.append("\n## SUGGESTED ACTIONS\n");
        sb.append("Escalation: ").append(todo.escalationLevel()).append("\n");
        for (var action : todo.suggestedActions()) {
            sb.append("  - [").append(action.confidence().toUpperCase()).append("] ")
                    .append(action.action()).append(": ").append(action.description());
            if (action.requiresApproval()) {
                sb.append(" (NEEDS APPROVAL)");
            }
            sb.append("\n");
        }

        // Similar past incidents
        if (!ctx.similarPastIncidents().isEmpty()) {
            sb.append("\n## SIMILAR PAST INCIDENTS\n");
            for (var past : ctx.similarPastIncidents()) {
                sb.append("  ").append(past.incidentId())
                        .append(": ").append(past.rootCause())
                        .append(" → ").append(past.resolution()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Minimal incident summary — fits in ~100 tokens.
     * Use for notification or quick status checks.
     */
    public static String formatIncidentCompact(IncidentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(ctx.severity()).append("] ")
                .append(ctx.incidentId()).append(": ")
                .append(ctx.summary()).append("\n");

        var what = ctx.whatIsHappening();
        sb.append("err=").append(formatPercent(what.currentErrorRate()))
                .append(" p50=").append(formatMs(what.currentLatencyP50Ms()))
                .append(" health=").append(what.serviceHealth()).append("\n");

        var actions = ctx.whatToDo().suggestedActions();
        if (!actions.isEmpty()) {
            sb.append("Action: ").append(actions.get(0).action())
                    .append(" (").append(actions.get(0).confidence()).append(")\n");
        }

        return sb.toString();
    }

    /**
     * JSON-compatible structured output for tool results.
     */
    public static String formatHealthAsJson(ServiceHealthAggregator.ServiceHealthSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"service\":\"").append(escapeJson(summary.serviceName())).append("\",");
        sb.append("\"status\":\"").append(summary.status()).append("\",");
        sb.append("\"timestamp\":\"").append(escapeJson(summary.timestamp())).append("\",");

        sb.append("\"operations\":[");
        for (int i = 0; i < summary.operations().size(); i++) {
            if (i > 0) sb.append(",");
            var op = summary.operations().get(i);
            sb.append("{\"name\":\"").append(escapeJson(op.operationName())).append("\",");
            sb.append("\"error_rate\":").append(op.errorRate()).append(",");
            sb.append("\"p50_ms\":").append(op.latencyP50Ms()).append(",");
            sb.append("\"p99_ms\":").append(op.latencyP99Ms()).append(",");
            sb.append("\"deviation\":\"").append(op.deviationStatus()).append("\"}");
        }
        sb.append("],");

        sb.append("\"dependencies\":[");
        for (int i = 0; i < summary.dependencies().size(); i++) {
            if (i > 0) sb.append(",");
            var dep = summary.dependencies().get(i);
            sb.append("{\"name\":\"").append(escapeJson(dep.name())).append("\",");
            sb.append("\"error_rate\":").append(dep.errorRate()).append(",");
            sb.append("\"mean_ms\":").append(dep.latencyMeanMs()).append("}");
        }
        sb.append("]}");

        return sb.toString();
    }

    private static String formatPercent(double rate) {
        return String.format("%.1f%%", rate * 100);
    }

    private static String formatMs(double ms) {
        if (ms < 1) return String.format("%.2fms", ms);
        if (ms < 1000) return String.format("%.0fms", ms);
        return String.format("%.1fs", ms / 1000);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
