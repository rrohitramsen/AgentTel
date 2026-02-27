package io.agenttel.core.slo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks SLO compliance and error budget consumption.
 * Thread-safe for concurrent recording.
 */
public class SloTracker {

    private final Map<String, SloDefinition> sloDefinitions = new ConcurrentHashMap<>();
    private final Map<String, SloState> sloStates = new ConcurrentHashMap<>();

    /**
     * Registers an SLO definition.
     */
    public void register(SloDefinition slo) {
        sloDefinitions.put(slo.name(), slo);
        sloStates.put(slo.name(), new SloState());
    }

    /**
     * Records a successful request for all SLOs matching the operation.
     */
    public void recordSuccess(String operationName) {
        for (var entry : sloDefinitions.entrySet()) {
            if (entry.getValue().operationName().equals(operationName)) {
                sloStates.get(entry.getKey()).recordSuccess();
            }
        }
    }

    /**
     * Records a failed request for all SLOs matching the operation.
     */
    public void recordFailure(String operationName) {
        for (var entry : sloDefinitions.entrySet()) {
            if (entry.getValue().operationName().equals(operationName)) {
                sloStates.get(entry.getKey()).recordFailure();
            }
        }
    }

    /**
     * Records a latency observation for latency-based SLOs.
     */
    public void recordLatency(String operationName, double latencyMs, double thresholdMs) {
        for (var entry : sloDefinitions.entrySet()) {
            SloDefinition slo = entry.getValue();
            if (slo.operationName().equals(operationName)) {
                SloState state = sloStates.get(entry.getKey());
                state.recordSuccess(); // count as total
                if (latencyMs > thresholdMs) {
                    state.recordBudgetViolation();
                }
            }
        }
    }

    /**
     * Returns the current status for all tracked SLOs.
     */
    public List<SloStatus> getStatuses() {
        List<SloStatus> statuses = new ArrayList<>();
        for (var entry : sloDefinitions.entrySet()) {
            SloDefinition slo = entry.getValue();
            SloState state = sloStates.get(entry.getKey());
            if (state != null) {
                statuses.add(state.toStatus(slo));
            }
        }
        return Collections.unmodifiableList(statuses);
    }

    /**
     * Returns the status for a specific SLO, or null if not found.
     */
    public SloStatus getStatus(String sloName) {
        SloDefinition slo = sloDefinitions.get(sloName);
        SloState state = sloStates.get(sloName);
        if (slo == null || state == null) return null;
        return state.toStatus(slo);
    }

    /**
     * Returns alerts for SLOs that have crossed budget thresholds.
     */
    public List<SloAlert> checkAlerts() {
        List<SloAlert> alerts = new ArrayList<>();
        for (var entry : sloDefinitions.entrySet()) {
            SloState state = sloStates.get(entry.getKey());
            if (state == null) continue;
            SloStatus status = state.toStatus(entry.getValue());
            if (status.budgetRemaining() <= 0.10 && status.totalRequests() > 0) {
                alerts.add(new SloAlert(entry.getKey(), AlertSeverity.CRITICAL,
                        status.budgetRemaining(), status.burnRate()));
            } else if (status.budgetRemaining() <= 0.25 && status.totalRequests() > 0) {
                alerts.add(new SloAlert(entry.getKey(), AlertSeverity.WARNING,
                        status.budgetRemaining(), status.burnRate()));
            } else if (status.budgetRemaining() <= 0.50 && status.totalRequests() > 0) {
                alerts.add(new SloAlert(entry.getKey(), AlertSeverity.INFO,
                        status.budgetRemaining(), status.burnRate()));
            }
        }
        return Collections.unmodifiableList(alerts);
    }

    public enum AlertSeverity {
        INFO, WARNING, CRITICAL
    }

    public record SloAlert(String sloName, AlertSeverity severity,
                           double budgetRemaining, double burnRate) {}

    public record SloStatus(
            String sloName,
            double target,
            double actual,
            double budgetRemaining,
            double burnRate,
            long totalRequests,
            long failedRequests
    ) {
        public boolean isWithinBudget() {
            return budgetRemaining > 0;
        }
    }

    private static class SloState {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong failedRequests = new AtomicLong(0);
        private final AtomicLong budgetViolations = new AtomicLong(0);

        void recordSuccess() {
            totalRequests.incrementAndGet();
        }

        void recordFailure() {
            totalRequests.incrementAndGet();
            failedRequests.incrementAndGet();
        }

        void recordBudgetViolation() {
            budgetViolations.incrementAndGet();
        }

        SloStatus toStatus(SloDefinition slo) {
            long total = totalRequests.get();
            long failed = failedRequests.get();

            if (total == 0) {
                return new SloStatus(slo.name(), slo.target(), 1.0, 1.0, 0.0, 0, 0);
            }

            double actual = 1.0 - ((double) failed / total);
            double errorBudget = 1.0 - slo.target(); // e.g., 0.001 for 99.9%
            double consumedBudget = errorBudget > 0
                    ? ((double) failed / total) / errorBudget
                    : (failed > 0 ? 1.0 : 0.0);
            double budgetRemaining = Math.max(0.0, 1.0 - consumedBudget);

            // Burn rate: how fast we're consuming budget relative to window
            // A burn rate of 1.0 means we'll exactly exhaust budget at end of window
            double burnRate = consumedBudget > 0 ? consumedBudget : 0.0;

            return new SloStatus(slo.name(), slo.target(), actual,
                    budgetRemaining, burnRate, total, failed);
        }
    }
}
