package io.agenttel.agent.reporting;

import io.agenttel.agent.health.ServiceHealthAggregator;
import io.agenttel.agent.health.ServiceHealthAggregator.ServiceHealthSummary;
import io.agenttel.agent.health.ServiceHealthAggregator.OperationSummary;
import io.agenttel.core.slo.SloTracker;
import io.agenttel.core.slo.SloTracker.SloStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a high-level executive summary optimized for LLM context windows.
 * Target: ~300 tokens — enough context to understand the situation at a glance.
 */
public class ExecutiveSummaryBuilder {

    private final ServiceHealthAggregator healthAggregator;
    private final SloTracker sloTracker;
    private final TrendAnalyzer trendAnalyzer;
    private final String serviceName;

    public ExecutiveSummaryBuilder(ServiceHealthAggregator healthAggregator,
                                    SloTracker sloTracker,
                                    TrendAnalyzer trendAnalyzer,
                                    String serviceName) {
        this.healthAggregator = healthAggregator;
        this.sloTracker = sloTracker;
        this.trendAnalyzer = trendAnalyzer;
        this.serviceName = serviceName;
    }

    /**
     * Generates a concise executive summary.
     */
    public String buildSummary() {
        ServiceHealthSummary health = healthAggregator.getHealthSummary(serviceName);
        List<SloStatus> sloStatuses = sloTracker.getStatuses();

        StringBuilder sb = new StringBuilder();
        sb.append("=== EXECUTIVE SUMMARY ===\n");
        sb.append("Service: ").append(serviceName);
        sb.append(" | Status: ").append(health.status());
        sb.append(" | ").append(Instant.now()).append("\n\n");

        // One-liner status
        sb.append("STATUS: ");
        switch (health.status()) {
            case HEALTHY -> sb.append("All systems operational. No issues detected.");
            case DEGRADED -> sb.append("Service degraded. Some operations experiencing elevated errors or latency.");
            case CRITICAL -> sb.append("CRITICAL — Significant impact. Immediate attention required.");
        }
        sb.append("\n\n");

        // Top issues (operations with deviation)
        List<OperationSummary> issues = health.operations().stream()
                .filter(op -> !"normal".equals(op.deviationStatus()))
                .sorted(Comparator.comparingDouble(OperationSummary::errorRate).reversed())
                .limit(3)
                .toList();

        if (!issues.isEmpty()) {
            sb.append("TOP ISSUES:\n");
            for (OperationSummary op : issues) {
                sb.append("  ▸ ").append(op.operationName())
                        .append(" — err=").append(formatPercent(op.errorRate()))
                        .append(" p50=").append(formatMs(op.latencyP50Ms()));
                if (op.baselineP50Ms() != null && op.baselineP50Ms() > 0) {
                    double ratio = op.latencyP50Ms() / op.baselineP50Ms();
                    sb.append(" (").append(String.format("%.1fx", ratio)).append(" baseline)");
                }
                sb.append(" [").append(op.deviationStatus().toUpperCase()).append("]\n");
            }
            sb.append("\n");
        }

        // SLO budget summary
        long totalSlos = sloStatuses.size();
        long violatedSlos = sloStatuses.stream()
                .filter(s -> s.totalRequests() > 0 && s.budgetRemaining() <= 0.10)
                .count();
        long atRiskSlos = sloStatuses.stream()
                .filter(s -> s.totalRequests() > 0 && s.budgetRemaining() > 0.10 && s.budgetRemaining() <= 0.50)
                .count();

        if (totalSlos > 0) {
            sb.append("SLO BUDGET: ");
            sb.append(totalSlos - violatedSlos - atRiskSlos).append("/").append(totalSlos).append(" healthy");
            if (violatedSlos > 0) sb.append(", ").append(violatedSlos).append(" violated");
            if (atRiskSlos > 0) sb.append(", ").append(atRiskSlos).append(" at risk");
            sb.append("\n");

            // Show violated SLOs
            sloStatuses.stream()
                    .filter(s -> s.totalRequests() > 0 && s.budgetRemaining() <= 0.25)
                    .sorted(Comparator.comparingDouble(SloStatus::budgetRemaining))
                    .limit(3)
                    .forEach(slo -> sb.append("  ▸ ").append(slo.sloName())
                            .append(" — budget=").append(formatPercent(slo.budgetRemaining()))
                            .append(" burn=").append(String.format("%.1fx", slo.burnRate()))
                            .append("\n"));
            sb.append("\n");
        }

        // Dependency health
        List<ServiceHealthAggregator.DependencySummary> unhealthyDeps = health.dependencies().stream()
                .filter(d -> d.totalCalls() >= 3 && d.errorRate() > 0.01)
                .toList();

        if (!unhealthyDeps.isEmpty()) {
            sb.append("DEPENDENCY ISSUES:\n");
            for (var dep : unhealthyDeps) {
                sb.append("  ▸ ").append(dep.name())
                        .append(" — err=").append(formatPercent(dep.errorRate()))
                        .append(" avg=").append(formatMs(dep.latencyMeanMs()))
                        .append("\n");
            }
            sb.append("\n");
        }

        // Operations summary
        sb.append("OPERATIONS: ").append(health.operations().size()).append(" tracked, ")
                .append(health.operations().stream().mapToLong(OperationSummary::totalRequests).sum())
                .append(" total requests\n");

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
