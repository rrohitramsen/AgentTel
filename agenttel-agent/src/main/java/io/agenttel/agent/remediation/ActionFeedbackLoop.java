package io.agenttel.agent.remediation;

import io.agenttel.agent.health.ServiceHealthAggregator;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Schedules post-remediation health verification.
 * After an agent executes a remediation action, this component captures pre-action
 * health, waits a configurable delay, then compares post-action health to determine
 * whether the action was effective.
 */
public class ActionFeedbackLoop {

    private final ServiceHealthAggregator healthAggregator;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ActionOutcome> outcomes = new ConcurrentHashMap<>();
    private final List<ActionOutcome> outcomeHistory = Collections.synchronizedList(new ArrayList<>());
    private final Duration defaultDelay;

    public ActionFeedbackLoop(ServiceHealthAggregator healthAggregator) {
        this(healthAggregator, Duration.ofSeconds(30));
    }

    public ActionFeedbackLoop(ServiceHealthAggregator healthAggregator, Duration defaultDelay) {
        this.healthAggregator = healthAggregator;
        this.defaultDelay = defaultDelay;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agenttel-feedback-loop");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Schedules a health verification after the default delay.
     */
    public CompletableFuture<ActionOutcome> scheduleVerification(String actionName) {
        return scheduleVerification(actionName, defaultDelay);
    }

    /**
     * Schedules a health verification after a custom delay.
     */
    public CompletableFuture<ActionOutcome> scheduleVerification(String actionName, Duration delay) {
        // Capture pre-action snapshot
        HealthSnapshot preSnapshot = captureHealthSnapshot();

        CompletableFuture<ActionOutcome> future = new CompletableFuture<>();

        scheduler.schedule(() -> {
            try {
                HealthSnapshot postSnapshot = captureHealthSnapshot();
                ActionOutcome outcome = computeOutcome(actionName, preSnapshot, postSnapshot);
                outcomes.put(actionName, outcome);
                outcomeHistory.add(outcome);
                while (outcomeHistory.size() > 50) {
                    outcomeHistory.remove(0);
                }
                future.complete(outcome);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);

        return future;
    }

    /**
     * Returns the most recent outcome for an action, if verification is complete.
     */
    public Optional<ActionOutcome> getOutcome(String actionName) {
        return Optional.ofNullable(outcomes.get(actionName));
    }

    /**
     * Returns all recent verification outcomes.
     */
    public List<ActionOutcome> getRecentOutcomes() {
        return new ArrayList<>(outcomeHistory);
    }

    /**
     * Shuts down the scheduler.
     */
    public void shutdown() {
        scheduler.shutdown();
    }

    private HealthSnapshot captureHealthSnapshot() {
        var summary = healthAggregator.getHealthSummary("");
        double avgErrorRate = 0;
        double avgLatencyP50 = 0;
        int opCount = 0;

        for (var op : summary.operations()) {
            avgErrorRate += op.errorRate();
            avgLatencyP50 += op.latencyP50Ms();
            opCount++;
        }

        if (opCount > 0) {
            avgErrorRate /= opCount;
            avgLatencyP50 /= opCount;
        }

        return new HealthSnapshot(
                summary.status(),
                avgErrorRate,
                avgLatencyP50,
                opCount,
                Instant.now()
        );
    }

    private ActionOutcome computeOutcome(String actionName,
                                          HealthSnapshot pre, HealthSnapshot post) {
        double errorRateDelta = post.avgErrorRate - pre.avgErrorRate;
        double latencyDeltaMs = post.avgLatencyP50 - pre.avgLatencyP50;

        // Action is effective if error rate decreased or latency improved
        boolean effective = errorRateDelta < -0.001 || latencyDeltaMs < -5.0
                || (post.healthStatus.ordinal() < pre.healthStatus.ordinal());

        return new ActionOutcome(
                actionName,
                effective,
                latencyDeltaMs,
                errorRateDelta,
                pre.healthStatus.name(),
                post.healthStatus.name(),
                Instant.now().toString()
        );
    }

    record HealthSnapshot(
            ServiceHealthAggregator.HealthStatus healthStatus,
            double avgErrorRate,
            double avgLatencyP50,
            int operationCount,
            Instant capturedAt
    ) {}

    /**
     * Result of post-action health verification.
     */
    public record ActionOutcome(
            String actionName,
            boolean effective,
            double latencyDeltaMs,
            double errorRateDelta,
            String preHealthStatus,
            String postHealthStatus,
            String verifiedAt
    ) {}
}
