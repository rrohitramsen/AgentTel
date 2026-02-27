package io.agenttel.core.baseline;

import io.agenttel.api.BaselineSource;
import io.agenttel.api.baseline.OperationBaseline;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baseline provider that computes baselines from a sliding window of observed span latencies.
 * Each operation gets its own {@link RollingWindow} ring buffer.
 */
public class RollingBaselineProvider implements BaselineProvider {

    private final int windowSize;
    private final int minSamples;
    private final ConcurrentHashMap<String, RollingWindow> windows = new ConcurrentHashMap<>();

    public RollingBaselineProvider() {
        this(1000, 10);
    }

    public RollingBaselineProvider(int windowSize, int minSamples) {
        this.windowSize = windowSize;
        this.minSamples = minSamples;
    }

    /**
     * Records a latency sample for the given operation.
     */
    public void recordLatency(String operationName, double latencyMs) {
        windows.computeIfAbsent(operationName, k -> new RollingWindow(windowSize))
                .record(latencyMs);
    }

    /**
     * Records an error for the given operation.
     */
    public void recordError(String operationName) {
        windows.computeIfAbsent(operationName, k -> new RollingWindow(windowSize))
                .recordError();
    }

    @Override
    public Optional<OperationBaseline> getBaseline(String operationName) {
        RollingWindow window = windows.get(operationName);
        if (window == null) {
            return Optional.empty();
        }

        RollingWindow.Snapshot snapshot = window.snapshot();
        if (snapshot.isEmpty() || snapshot.sampleCount() < minSamples) {
            return Optional.empty();
        }

        return Optional.of(OperationBaseline.builder(operationName)
                .latencyP50Ms(snapshot.p50())
                .latencyP99Ms(snapshot.p99())
                .errorRate(snapshot.errorRate())
                .source(BaselineSource.ROLLING_7D)
                .updatedAt(Instant.now().toString())
                .build());
    }

    /**
     * Returns the rolling window snapshot for an operation, or empty if not enough samples.
     */
    public Optional<RollingWindow.Snapshot> getSnapshot(String operationName) {
        RollingWindow window = windows.get(operationName);
        if (window == null) {
            return Optional.empty();
        }
        RollingWindow.Snapshot snapshot = window.snapshot();
        return snapshot.isEmpty() ? Optional.empty() : Optional.of(snapshot);
    }
}
