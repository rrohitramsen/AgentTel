package io.agenttel.agent.health;

import io.agenttel.core.baseline.RollingBaselineProvider;
import io.agenttel.core.baseline.RollingWindow;
import io.agenttel.core.slo.SloTracker;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregates real-time service health from span data into summaries
 * that AI agents can consume without scanning individual spans.
 */
public class ServiceHealthAggregator {

    private final RollingBaselineProvider rollingBaselines;
    private final SloTracker sloTracker;
    private final ConcurrentHashMap<String, OperationHealth> operationHealthMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DependencyHealth> dependencyHealthMap = new ConcurrentHashMap<>();

    public ServiceHealthAggregator(RollingBaselineProvider rollingBaselines, SloTracker sloTracker) {
        this.rollingBaselines = rollingBaselines;
        this.sloTracker = sloTracker;
    }

    /**
     * Records a span completion for health tracking.
     */
    public void recordSpan(String operationName, double latencyMs, boolean isError) {
        operationHealthMap.computeIfAbsent(operationName, k -> new OperationHealth())
                .record(latencyMs, isError);
    }

    /**
     * Records a dependency call result.
     */
    public void recordDependencyCall(String dependencyName, double latencyMs, boolean isError) {
        dependencyHealthMap.computeIfAbsent(dependencyName, k -> new DependencyHealth())
                .record(latencyMs, isError);
    }

    /**
     * Returns a full service health summary — the primary query for agents.
     */
    public ServiceHealthSummary getHealthSummary(String serviceName) {
        List<OperationSummary> operations = new ArrayList<>();
        for (var entry : operationHealthMap.entrySet()) {
            OperationHealth health = entry.getValue();
            RollingWindow.Snapshot baseline = rollingBaselines != null
                    ? rollingBaselines.getSnapshot(entry.getKey()).orElse(null)
                    : null;
            operations.add(health.toSummary(entry.getKey(), baseline));
        }

        List<DependencySummary> dependencies = new ArrayList<>();
        for (var entry : dependencyHealthMap.entrySet()) {
            dependencies.add(entry.getValue().toSummary(entry.getKey()));
        }

        List<SloTracker.SloStatus> sloStatuses = sloTracker != null
                ? sloTracker.getStatuses()
                : Collections.emptyList();

        HealthStatus overallStatus = computeOverallStatus(operations, dependencies, sloStatuses);

        return new ServiceHealthSummary(
                serviceName,
                overallStatus,
                Instant.now().toString(),
                operations,
                dependencies,
                sloStatuses
        );
    }

    /**
     * Returns health for a single operation.
     */
    public Optional<OperationSummary> getOperationHealth(String operationName) {
        OperationHealth health = operationHealthMap.get(operationName);
        if (health == null) return Optional.empty();

        RollingWindow.Snapshot baseline = rollingBaselines != null
                ? rollingBaselines.getSnapshot(operationName).orElse(null)
                : null;
        return Optional.of(health.toSummary(operationName, baseline));
    }

    private HealthStatus computeOverallStatus(List<OperationSummary> ops,
                                               List<DependencySummary> deps,
                                               List<SloTracker.SloStatus> slos) {
        // Any SLO with <10% budget remaining = CRITICAL
        for (var slo : slos) {
            if (slo.totalRequests() > 0 && slo.budgetRemaining() <= 0.10) {
                return HealthStatus.CRITICAL;
            }
        }

        // Any operation with >10% error rate = DEGRADED
        for (var op : ops) {
            if (op.totalRequests() > 100 && op.errorRate() > 0.10) {
                return HealthStatus.CRITICAL;
            }
            if (op.totalRequests() > 100 && op.errorRate() > 0.01) {
                return HealthStatus.DEGRADED;
            }
        }

        // Any dependency down = DEGRADED
        for (var dep : deps) {
            if (dep.totalCalls() > 10 && dep.errorRate() > 0.50) {
                return HealthStatus.DEGRADED;
            }
        }

        // SLO budget <50% = DEGRADED
        for (var slo : slos) {
            if (slo.totalRequests() > 0 && slo.budgetRemaining() <= 0.50) {
                return HealthStatus.DEGRADED;
            }
        }

        return HealthStatus.HEALTHY;
    }

    public enum HealthStatus {
        HEALTHY, DEGRADED, CRITICAL
    }

    public record ServiceHealthSummary(
            String serviceName,
            HealthStatus status,
            String timestamp,
            List<OperationSummary> operations,
            List<DependencySummary> dependencies,
            List<SloTracker.SloStatus> sloStatuses
    ) {}

    public record OperationSummary(
            String operationName,
            long totalRequests,
            long errorCount,
            double errorRate,
            double latencyP50Ms,
            double latencyP99Ms,
            double latencyMeanMs,
            Double baselineP50Ms,
            Double baselineP99Ms,
            String deviationStatus
    ) {}

    public record DependencySummary(
            String name,
            long totalCalls,
            long errorCount,
            double errorRate,
            double latencyMeanMs
    ) {}

    private static class OperationHealth {
        private final AtomicLong total = new AtomicLong();
        private final AtomicLong errors = new AtomicLong();
        private double latencySum = 0;
        private double latencyMin = Double.MAX_VALUE;
        private double latencyMax = 0;
        private final List<Double> recentLatencies = Collections.synchronizedList(new ArrayList<>());

        void record(double latencyMs, boolean isError) {
            total.incrementAndGet();
            if (isError) errors.incrementAndGet();
            synchronized (this) {
                latencySum += latencyMs;
                latencyMin = Math.min(latencyMin, latencyMs);
                latencyMax = Math.max(latencyMax, latencyMs);
            }
            recentLatencies.add(latencyMs);
            // Keep bounded
            while (recentLatencies.size() > 1000) {
                recentLatencies.remove(0);
            }
        }

        OperationSummary toSummary(String name, RollingWindow.Snapshot baseline) {
            long t = total.get();
            long e = errors.get();
            double errorRate = t > 0 ? (double) e / t : 0;
            double mean;
            double p50 = 0, p99 = 0;

            synchronized (this) {
                mean = t > 0 ? latencySum / t : 0;
            }

            if (!recentLatencies.isEmpty()) {
                List<Double> sorted;
                synchronized (recentLatencies) {
                    sorted = new ArrayList<>(recentLatencies);
                }
                Collections.sort(sorted);
                p50 = percentile(sorted, 0.50);
                p99 = percentile(sorted, 0.99);
            }

            Double baseP50 = baseline != null ? baseline.p50() : null;
            Double baseP99 = baseline != null ? baseline.p99() : null;
            String deviation = "normal";
            if (baseline != null && baseline.p50() > 0 && p50 > baseline.p50() * 2) {
                deviation = "elevated";
            }
            if (baseline != null && baseline.p50() > 0 && p50 > baseline.p50() * 5) {
                deviation = "critical";
            }

            return new OperationSummary(name, t, e, errorRate, p50, p99, mean,
                    baseP50, baseP99, deviation);
        }

        private static double percentile(List<Double> sorted, double p) {
            if (sorted.isEmpty()) return 0;
            if (sorted.size() == 1) return sorted.get(0);
            double rank = p * (sorted.size() - 1);
            int lower = (int) Math.floor(rank);
            int upper = Math.min(lower + 1, sorted.size() - 1);
            double frac = rank - lower;
            return sorted.get(lower) + frac * (sorted.get(upper) - sorted.get(lower));
        }
    }

    private static class DependencyHealth {
        private final AtomicLong total = new AtomicLong();
        private final AtomicLong errors = new AtomicLong();
        private double latencySum = 0;

        void record(double latencyMs, boolean isError) {
            total.incrementAndGet();
            if (isError) errors.incrementAndGet();
            synchronized (this) {
                latencySum += latencyMs;
            }
        }

        DependencySummary toSummary(String name) {
            long t = total.get();
            long e = errors.get();
            double mean;
            synchronized (this) {
                mean = t > 0 ? latencySum / t : 0;
            }
            return new DependencySummary(name, t, e, t > 0 ? (double) e / t : 0, mean);
        }
    }
}
