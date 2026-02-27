package io.agenttel.core.anomaly;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyDetectorTest {

    private final AnomalyDetector detector = new AnomalyDetector(3.0);

    @Test
    void normalValueIsNotAnomaly() {
        AnomalyResult result = detector.evaluate("latency", 50.0, 45.0, 10.0);
        assertThat(result.isAnomaly()).isFalse();
        assertThat(result.anomalyScore()).isLessThan(0.5);
    }

    @Test
    void extremeValueIsAnomaly() {
        AnomalyResult result = detector.evaluate("latency", 200.0, 45.0, 10.0);
        assertThat(result.isAnomaly()).isTrue();
        assertThat(result.anomalyScore()).isGreaterThan(0.9);
    }

    @Test
    void zeroStddevReturnsNormal() {
        AnomalyResult result = detector.evaluate("latency", 200.0, 45.0, 0.0);
        assertThat(result.isAnomaly()).isFalse();
        assertThat(result.anomalyScore()).isEqualTo(0.0);
    }

    @Test
    void anomalyScoreIsCappedAtOne() {
        AnomalyResult result = detector.evaluate("latency", 10000.0, 45.0, 10.0);
        assertThat(result.anomalyScore()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void negativeDeviationDetected() {
        AnomalyResult result = detector.evaluate("latency", -100.0, 45.0, 10.0);
        assertThat(result.isAnomaly()).isTrue();
        assertThat(result.zScore()).isNegative();
    }
}
