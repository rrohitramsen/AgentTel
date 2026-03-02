package io.agenttel.agent.reporting;

import io.agenttel.agent.health.ServiceHealthAggregator;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tracks operation metrics over time and produces trend analysis.
 * Samples from ServiceHealthAggregator every 30 seconds and maintains
 * a ring buffer of snapshots per operation.
 */
public class TrendAnalyzer {

    private static final int MAX_SNAPSHOTS = 120; // 1 hour at 30s intervals
    private static final int SNAPSHOT_INTERVAL_SECONDS = 30;

    private final ServiceHealthAggregator healthAggregator;
    private final String serviceName;
    private final ConcurrentHashMap<String, LinkedList<MetricSnapshot>> snapshotHistory = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public TrendAnalyzer(ServiceHealthAggregator healthAggregator, String serviceName) {
        this.healthAggregator = healthAggregator;
        this.serviceName = serviceName;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agenttel-trend-analyzer");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::captureSnapshot,
                SNAPSHOT_INTERVAL_SECONDS, SNAPSHOT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Captures a snapshot of current metrics for all tracked operations.
     */
    void captureSnapshot() {
        var summary = healthAggregator.getHealthSummary(serviceName);
        Instant now = Instant.now();

        for (var op : summary.operations()) {
            LinkedList<MetricSnapshot> history = snapshotHistory.computeIfAbsent(
                    op.operationName(), k -> new LinkedList<>());

            synchronized (history) {
                history.addLast(new MetricSnapshot(
                        now,
                        op.latencyP50Ms(),
                        op.latencyP99Ms(),
                        op.errorRate(),
                        op.totalRequests()
                ));
                while (history.size() > MAX_SNAPSHOTS) {
                    history.removeFirst();
                }
            }
        }
    }

    /**
     * Returns trend analysis for a specific operation over the given time window.
     */
    public String analyzeTrend(String operationName, int windowMinutes) {
        LinkedList<MetricSnapshot> history = snapshotHistory.get(operationName);
        if (history == null || history.isEmpty()) {
            return "No trend data available for " + operationName + ". Data collection starts after first requests.";
        }

        Instant cutoff = Instant.now().minusSeconds(windowMinutes * 60L);
        List<MetricSnapshot> window;
        synchronized (history) {
            window = history.stream()
                    .filter(s -> s.timestamp().isAfter(cutoff))
                    .toList();
        }

        if (window.isEmpty()) {
            return "No data points in the last " + windowMinutes + " minutes for " + operationName;
        }

        MetricSnapshot first = window.get(0);
        MetricSnapshot last = window.get(window.size() - 1);

        // Calculate trends
        double latencyP50Trend = trendDirection(window.stream().map(MetricSnapshot::latencyP50Ms).toList());
        double errorRateTrend = trendDirection(window.stream().map(MetricSnapshot::errorRate).toList());

        double avgP50 = window.stream().mapToDouble(MetricSnapshot::latencyP50Ms).average().orElse(0);
        double avgP99 = window.stream().mapToDouble(MetricSnapshot::latencyP99Ms).average().orElse(0);
        double avgErrorRate = window.stream().mapToDouble(MetricSnapshot::errorRate).average().orElse(0);
        double maxP50 = window.stream().mapToDouble(MetricSnapshot::latencyP50Ms).max().orElse(0);
        double minP50 = window.stream().mapToDouble(MetricSnapshot::latencyP50Ms).min().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append("=== TREND ANALYSIS: ").append(operationName).append(" ===\n");
        sb.append("Window: last ").append(windowMinutes).append(" minutes (")
                .append(window.size()).append(" data points)\n");
        sb.append("Period: ").append(first.timestamp()).append(" → ").append(last.timestamp()).append("\n\n");

        sb.append("LATENCY P50:\n");
        sb.append("  Current: ").append(formatMs(last.latencyP50Ms()));
        sb.append("  Avg: ").append(formatMs(avgP50));
        sb.append("  Min: ").append(formatMs(minP50));
        sb.append("  Max: ").append(formatMs(maxP50));
        sb.append("  Trend: ").append(trendLabel(latencyP50Trend)).append("\n");

        sb.append("LATENCY P99:\n");
        sb.append("  Current: ").append(formatMs(last.latencyP99Ms()));
        sb.append("  Avg: ").append(formatMs(avgP99)).append("\n");

        sb.append("ERROR RATE:\n");
        sb.append("  Current: ").append(formatPercent(last.errorRate()));
        sb.append("  Avg: ").append(formatPercent(avgErrorRate));
        sb.append("  Trend: ").append(trendLabel(errorRateTrend)).append("\n");

        sb.append("THROUGHPUT:\n");
        sb.append("  Total requests: ").append(last.totalRequests()).append("\n");

        sb.append("\nOVERALL: ");
        if (latencyP50Trend > 0.1 || errorRateTrend > 0.1) {
            sb.append("DEGRADING — metrics trending upward");
        } else if (latencyP50Trend < -0.1 || errorRateTrend < -0.1) {
            sb.append("IMPROVING — metrics trending downward");
        } else {
            sb.append("STABLE — no significant change");
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Returns a simple trend direction using linear regression slope.
     * Positive = increasing, negative = decreasing, near-zero = stable.
     */
    private double trendDirection(List<Double> values) {
        if (values.size() < 2) return 0;

        int n = values.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values.get(i);
            sumXY += i * values.get(i);
            sumX2 += (double) i * i;
        }
        double denominator = n * sumX2 - sumX * sumX;
        if (denominator == 0) return 0;

        double slope = (n * sumXY - sumX * sumY) / denominator;
        double mean = sumY / n;
        // Normalize slope relative to mean
        return mean > 0 ? slope / mean : 0;
    }

    private String trendLabel(double normalizedSlope) {
        if (normalizedSlope > 0.1) return "INCREASING ↑";
        if (normalizedSlope < -0.1) return "DECREASING ↓";
        return "STABLE →";
    }

    private static String formatMs(double ms) {
        if (ms < 1) return String.format("%.2fms", ms);
        if (ms < 1000) return String.format("%.0fms", ms);
        return String.format("%.1fs", ms / 1000);
    }

    private static String formatPercent(double rate) {
        return String.format("%.2f%%", rate * 100);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    public record MetricSnapshot(
            Instant timestamp,
            double latencyP50Ms,
            double latencyP99Ms,
            double errorRate,
            long totalRequests
    ) {}
}
