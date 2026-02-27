package io.agenttel.core.baseline;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free ring buffer for collecting latency/metric samples.
 * Thread-safe for concurrent writes; snapshot reads are eventually consistent.
 */
public class RollingWindow {

    private final double[] samples;
    private final int capacity;
    private final AtomicInteger writeIndex = new AtomicInteger(0);
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong totalCount = new AtomicLong(0);

    public RollingWindow(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.samples = new double[capacity];
    }

    public void record(double value) {
        int idx = writeIndex.getAndUpdate(i -> (i + 1) % capacity);
        samples[idx] = value;
        int currentCount = count.get();
        if (currentCount < capacity) {
            count.compareAndSet(currentCount, currentCount + 1);
        }
        totalCount.incrementAndGet();
    }

    public void recordError() {
        errorCount.incrementAndGet();
        totalCount.incrementAndGet();
    }

    public Snapshot snapshot() {
        int n = Math.min(count.get(), capacity);
        if (n == 0) {
            return Snapshot.EMPTY;
        }

        double[] copy = new double[n];
        System.arraycopy(samples, 0, copy, 0, n);
        Arrays.sort(copy);

        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += copy[i];
        }
        double mean = sum / n;

        double variance = 0;
        for (int i = 0; i < n; i++) {
            double diff = copy[i] - mean;
            variance += diff * diff;
        }
        double stddev = Math.sqrt(variance / n);

        double p50 = percentile(copy, 0.50);
        double p95 = percentile(copy, 0.95);
        double p99 = percentile(copy, 0.99);

        long total = totalCount.get();
        long errors = errorCount.get();
        double errorRate = total > 0 ? (double) errors / total : 0.0;

        return new Snapshot(mean, stddev, p50, p95, p99, errorRate, n);
    }

    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 1) return sorted[0];
        double rank = p * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = Math.min(lower + 1, sorted.length - 1);
        double fraction = rank - lower;
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
    }

    public int size() {
        return Math.min(count.get(), capacity);
    }

    public record Snapshot(double mean, double stddev, double p50, double p95, double p99,
                           double errorRate, int sampleCount) {
        static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0, 0);

        public boolean isEmpty() {
            return sampleCount == 0;
        }
    }
}
