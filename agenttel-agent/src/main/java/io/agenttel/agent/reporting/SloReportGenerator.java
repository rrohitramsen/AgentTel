package io.agenttel.agent.reporting;

import io.agenttel.core.slo.SloTracker;
import io.agenttel.core.slo.SloTracker.SloStatus;

import java.time.Instant;
import java.util.List;

/**
 * Generates SLO compliance reports across all tracked operations.
 * Optimized for LLM consumption — concise, structured, actionable.
 */
public class SloReportGenerator {

    private final SloTracker sloTracker;

    public SloReportGenerator(SloTracker sloTracker) {
        this.sloTracker = sloTracker;
    }

    /**
     * Generates a formatted SLO report as text.
     */
    public String generateReport() {
        List<SloStatus> statuses = sloTracker.getStatuses();
        if (statuses.isEmpty()) {
            return "No SLOs configured.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== SLO REPORT ===\n");
        sb.append("Generated: ").append(Instant.now()).append("\n");
        sb.append("Total SLOs: ").append(statuses.size()).append("\n\n");

        long healthy = statuses.stream().filter(s -> s.budgetRemaining() > 0.50).count();
        long atRisk = statuses.stream().filter(s -> s.budgetRemaining() > 0.10 && s.budgetRemaining() <= 0.50).count();
        long violated = statuses.stream().filter(s -> s.budgetRemaining() <= 0.10 && s.totalRequests() > 0).count();

        sb.append("SUMMARY: ").append(healthy).append(" healthy, ")
                .append(atRisk).append(" at risk, ")
                .append(violated).append(" violated\n\n");

        // Violated SLOs first (most urgent)
        List<SloStatus> sortedStatuses = statuses.stream()
                .sorted((a, b) -> Double.compare(a.budgetRemaining(), b.budgetRemaining()))
                .toList();

        for (SloStatus status : sortedStatuses) {
            String severity;
            if (status.totalRequests() == 0) {
                severity = "NO DATA";
            } else if (status.budgetRemaining() <= 0.10) {
                severity = "VIOLATED";
            } else if (status.budgetRemaining() <= 0.25) {
                severity = "WARNING";
            } else if (status.budgetRemaining() <= 0.50) {
                severity = "AT RISK";
            } else {
                severity = "HEALTHY";
            }

            sb.append("  [").append(severity).append("] ").append(status.sloName()).append("\n");
            sb.append("    Target: ").append(formatPercent(status.target()));
            sb.append("  Actual: ").append(formatPercent(status.actual()));
            sb.append("  Budget: ").append(formatPercent(status.budgetRemaining()));
            sb.append("  Burn: ").append(String.format("%.2fx", status.burnRate()));
            sb.append("  Requests: ").append(status.totalRequests());
            sb.append("  Failed: ").append(status.failedRequests());
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Generates a JSON SLO report.
     */
    public String generateReportJson() {
        List<SloStatus> statuses = sloTracker.getStatuses();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"timestamp\":\"").append(Instant.now()).append("\",");
        sb.append("\"total_slos\":").append(statuses.size()).append(",");
        sb.append("\"slos\":[");
        for (int i = 0; i < statuses.size(); i++) {
            if (i > 0) sb.append(",");
            SloStatus s = statuses.get(i);
            sb.append("{\"name\":\"").append(escapeJson(s.sloName())).append("\",");
            sb.append("\"target\":").append(s.target()).append(",");
            sb.append("\"actual\":").append(s.actual()).append(",");
            sb.append("\"budget_remaining\":").append(s.budgetRemaining()).append(",");
            sb.append("\"burn_rate\":").append(s.burnRate()).append(",");
            sb.append("\"total_requests\":").append(s.totalRequests()).append(",");
            sb.append("\"failed_requests\":").append(s.failedRequests()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String formatPercent(double rate) {
        return String.format("%.2f%%", rate * 100);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
