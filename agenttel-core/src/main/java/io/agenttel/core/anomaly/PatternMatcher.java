package io.agenttel.core.anomaly;

import io.agenttel.core.baseline.RollingWindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Detects known incident patterns from span telemetry data.
 * Uses rolling statistics and recent observations to identify patterns like
 * cascade failures, latency degradation, and error rate spikes.
 */
public class PatternMatcher {

    private final double latencyDegradationThreshold;
    private final double errorRateSpikeThreshold;
    private final int cascadeFailureMinServices;

    // Tracks recent errors per dependency for cascade detection
    private final ConcurrentHashMap<String, AtomicLong> recentErrors = new ConcurrentHashMap<>();
    // Tracks recent latency trend (operation -> list of recent latencies)
    private final ConcurrentHashMap<String, LatencyTrend> latencyTrends = new ConcurrentHashMap<>();

    public PatternMatcher() {
        this(2.0, 5.0, 3);
    }

    /**
     * @param latencyDegradationThreshold multiplier over P50 to consider degraded
     * @param errorRateSpikeThreshold     multiplier over baseline error rate to consider a spike
     * @param cascadeFailureMinServices   minimum services failing to trigger cascade pattern
     */
    public PatternMatcher(double latencyDegradationThreshold, double errorRateSpikeThreshold,
                          int cascadeFailureMinServices) {
        this.latencyDegradationThreshold = latencyDegradationThreshold;
        this.errorRateSpikeThreshold = errorRateSpikeThreshold;
        this.cascadeFailureMinServices = cascadeFailureMinServices;
    }

    /**
     * Records a dependency error for cascade failure detection.
     */
    public void recordDependencyError(String dependency) {
        recentErrors.computeIfAbsent(dependency, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Records a latency observation for trend analysis.
     */
    public void recordLatency(String operationName, double latencyMs) {
        latencyTrends.computeIfAbsent(operationName, k -> new LatencyTrend(50))
                .record(latencyMs);
    }

    /**
     * Clears dependency error counts (call periodically, e.g., every minute).
     */
    public void resetDependencyErrors() {
        recentErrors.clear();
    }

    /**
     * Detects patterns based on current observation and rolling baseline.
     *
     * @param operationName the operation being analyzed
     * @param currentLatencyMs the observed latency
     * @param isError whether the span was an error
     * @param snapshot rolling baseline snapshot (may be null)
     * @return list of detected patterns (may be empty)
     */
    public List<IncidentPattern> detectPatterns(String operationName, double currentLatencyMs,
                                                 boolean isError,
                                                 RollingWindow.Snapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return Collections.emptyList();
        }

        List<IncidentPattern> detected = new ArrayList<>();

        // Latency degradation: current latency significantly above P50
        if (snapshot.p50() > 0 && currentLatencyMs > snapshot.p50() * latencyDegradationThreshold) {
            detected.add(IncidentPattern.LATENCY_DEGRADATION);
        }

        // Error rate spike: current error rate significantly above baseline
        if (snapshot.errorRate() > 0 && isError) {
            // Check if trend shows increasing errors
            double recentErrorRate = estimateRecentErrorRate(operationName);
            if (recentErrorRate > snapshot.errorRate() * errorRateSpikeThreshold) {
                detected.add(IncidentPattern.ERROR_RATE_SPIKE);
            }
        }

        // Cascade failure: multiple dependencies reporting errors
        long failingServices = recentErrors.values().stream()
                .filter(c -> c.get() > 0)
                .count();
        if (failingServices >= cascadeFailureMinServices) {
            detected.add(IncidentPattern.CASCADE_FAILURE);
        }

        // Memory leak pattern: steadily increasing latency trend
        LatencyTrend trend = latencyTrends.get(operationName);
        if (trend != null && trend.isMonotonicallyIncreasing()) {
            detected.add(IncidentPattern.MEMORY_LEAK);
        }

        return detected;
    }

    private double estimateRecentErrorRate(String operationName) {
        LatencyTrend trend = latencyTrends.get(operationName);
        if (trend == null) return 0.0;
        return trend.errorRate();
    }

    /**
     * Tracks recent latency observations for trend detection.
     */
    static class LatencyTrend {
        private final double[] values;
        private final boolean[] errors;
        private final int capacity;
        private int writeIndex = 0;
        private int count = 0;
        private int errorCount = 0;

        LatencyTrend(int capacity) {
            this.capacity = capacity;
            this.values = new double[capacity];
            this.errors = new boolean[capacity];
        }

        synchronized void record(double latencyMs) {
            values[writeIndex] = latencyMs;
            errors[writeIndex] = false;
            writeIndex = (writeIndex + 1) % capacity;
            if (count < capacity) count++;
        }

        synchronized void recordError() {
            errors[writeIndex > 0 ? writeIndex - 1 : capacity - 1] = true;
            errorCount++;
        }

        synchronized double errorRate() {
            return count > 0 ? (double) errorCount / count : 0.0;
        }

        /**
         * Checks if the last N observations show a monotonically increasing trend.
         * Uses simple linear regression slope.
         */
        synchronized boolean isMonotonicallyIncreasing() {
            if (count < 10) return false;

            int n = Math.min(count, capacity);
            // Calculate slope using least squares
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            for (int i = 0; i < n; i++) {
                int idx = (writeIndex - n + i + capacity) % capacity;
                sumX += i;
                sumY += values[idx];
                sumXY += i * values[idx];
                sumX2 += (double) i * i;
            }
            double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
            // Positive slope with meaningful magnitude
            double mean = sumY / n;
            return mean > 0 && slope > 0 && (slope / mean) > 0.01;
        }
    }
}
