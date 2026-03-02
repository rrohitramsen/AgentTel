package io.agenttel.agent.reporting;

import io.agenttel.agent.health.ServiceHealthAggregator;
import io.agenttel.agent.health.ServiceHealthAggregator.OperationSummary;
import io.agenttel.agent.health.ServiceHealthAggregator.DependencySummary;
import io.agenttel.core.slo.SloTracker;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Builds correlated cross-stack context linking frontend and backend telemetry.
 * Currently provides backend context with structured placeholders for frontend data.
 * When agenttel-web data flows through the same OTel Collector, this will be enriched
 * with client-side metrics.
 */
public class CrossStackContextBuilder {

    private final ServiceHealthAggregator healthAggregator;
    private final SloTracker sloTracker;
    private final String serviceName;

    public CrossStackContextBuilder(ServiceHealthAggregator healthAggregator,
                                      SloTracker sloTracker,
                                      String serviceName) {
        this.healthAggregator = healthAggregator;
        this.sloTracker = sloTracker;
        this.serviceName = serviceName;
    }

    /**
     * Returns cross-stack context for a given operation.
     */
    public String buildContext(String operationName) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CROSS-STACK CONTEXT: ").append(operationName).append(" ===\n");
        sb.append("Generated: ").append(Instant.now()).append("\n\n");

        // Frontend section
        sb.append("## FRONTEND (User Experience)\n");
        sb.append("  Status: No frontend telemetry connected\n");
        sb.append("  Note: Connect agenttel-web SDK to enable cross-stack correlation.\n");
        sb.append("  When connected, this section will show:\n");
        sb.append("    - Page load times and baselines for routes calling this operation\n");
        sb.append("    - User journey funnel health\n");
        sb.append("    - Client-side anomalies (rage clicks, slow loads, error loops)\n");
        sb.append("    - Number of affected users\n");
        sb.append("    - W3C Trace Context correlation linking browser → backend traces\n\n");

        // Backend section
        sb.append("## BACKEND (").append(serviceName).append(")\n");
        Optional<OperationSummary> opHealth = healthAggregator.getOperationHealth(operationName);
        if (opHealth.isPresent()) {
            OperationSummary op = opHealth.get();
            sb.append("  Operation: ").append(op.operationName()).append("\n");
            sb.append("  Error Rate: ").append(formatPercent(op.errorRate()));
            if (op.baselineP50Ms() != null) {
                sb.append("  (baseline err not tracked separately)");
            }
            sb.append("\n");
            sb.append("  Latency P50: ").append(formatMs(op.latencyP50Ms()));
            if (op.baselineP50Ms() != null && op.baselineP50Ms() > 0) {
                sb.append(" (baseline: ").append(formatMs(op.baselineP50Ms())).append(")");
            }
            sb.append("\n");
            sb.append("  Latency P99: ").append(formatMs(op.latencyP99Ms()));
            if (op.baselineP99Ms() != null && op.baselineP99Ms() > 0) {
                sb.append(" (baseline: ").append(formatMs(op.baselineP99Ms())).append(")");
            }
            sb.append("\n");
            sb.append("  Total Requests: ").append(op.totalRequests()).append("\n");
            sb.append("  Deviation: ").append(op.deviationStatus().toUpperCase()).append("\n");
        } else {
            sb.append("  No data available for operation: ").append(operationName).append("\n");
        }

        // Dependencies
        var healthSummary = healthAggregator.getHealthSummary(serviceName);
        List<DependencySummary> deps = healthSummary.dependencies();
        if (!deps.isEmpty()) {
            sb.append("\n## DEPENDENCIES\n");
            for (DependencySummary dep : deps) {
                sb.append("  ").append(dep.name())
                        .append(": err=").append(formatPercent(dep.errorRate()))
                        .append(" avg=").append(formatMs(dep.latencyMeanMs()))
                        .append(" (").append(dep.totalCalls()).append(" calls)\n");
            }
        }

        // SLO context
        List<SloTracker.SloStatus> sloStatuses = sloTracker.getStatuses();
        if (!sloStatuses.isEmpty()) {
            sb.append("\n## SLO STATUS\n");
            for (var slo : sloStatuses) {
                sb.append("  ").append(slo.sloName())
                        .append(": ").append(formatPercent(slo.actual()))
                        .append(" (target: ").append(formatPercent(slo.target()))
                        .append(") budget=").append(formatPercent(slo.budgetRemaining()))
                        .append("\n");
            }
        }

        // Correlation note
        sb.append("\n## CORRELATION\n");
        sb.append("  Frontend → Backend trace linking: requires agenttel-web SDK\n");
        sb.append("  Mechanism: W3C Trace Context (traceparent header)\n");
        sb.append("  When enabled: browser interactions will carry backend_trace_id,\n");
        sb.append("  allowing full user-to-database trace correlation.\n");

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
}
