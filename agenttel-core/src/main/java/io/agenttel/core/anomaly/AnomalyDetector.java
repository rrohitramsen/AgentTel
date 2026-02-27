package io.agenttel.core.anomaly;

/**
 * Lightweight anomaly detection using z-score comparison against baselines.
 * This is intentionally simple — not an ML system.
 */
public class AnomalyDetector {

    private final double zScoreThreshold;

    public AnomalyDetector(double zScoreThreshold) {
        this.zScoreThreshold = zScoreThreshold;
    }

    /**
     * Evaluates whether the current value is anomalous relative to the baseline.
     *
     * @param metric       name of the metric being evaluated
     * @param currentValue the observed value
     * @param baselineMean the expected mean value
     * @param baselineStddev the expected standard deviation
     * @return anomaly evaluation result
     */
    public AnomalyResult evaluate(String metric, double currentValue,
                                   double baselineMean, double baselineStddev) {
        if (baselineStddev <= 0) {
            return AnomalyResult.normal();
        }
        double zScore = (currentValue - baselineMean) / baselineStddev;
        double anomalyScore = Math.min(1.0, Math.abs(zScore) / 4.0);
        boolean isAnomaly = Math.abs(zScore) > zScoreThreshold;
        return new AnomalyResult(anomalyScore, isAnomaly, zScore);
    }
}
