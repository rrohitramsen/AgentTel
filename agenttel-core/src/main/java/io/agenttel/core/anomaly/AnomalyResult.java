package io.agenttel.core.anomaly;

/**
 * Result of an anomaly evaluation.
 */
public record AnomalyResult(double anomalyScore, boolean isAnomaly, double zScore) {

    public static AnomalyResult normal() {
        return new AnomalyResult(0.0, false, 0.0);
    }
}
