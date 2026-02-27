package io.agenttel.core.anomaly;

import io.agenttel.core.baseline.RollingWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatternMatcherTest {

    private PatternMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new PatternMatcher(2.0, 5.0, 3);
    }

    @Test
    void detectsLatencyDegradation() {
        RollingWindow window = new RollingWindow(100);
        for (int i = 0; i < 50; i++) {
            window.record(50.0);
        }
        RollingWindow.Snapshot snapshot = window.snapshot();

        // Current latency 3x the P50 -> degradation
        List<IncidentPattern> patterns = matcher.detectPatterns(
                "op", 150.0, false, snapshot);
        assertThat(patterns).contains(IncidentPattern.LATENCY_DEGRADATION);
    }

    @Test
    void noPatternForNormalLatency() {
        RollingWindow window = new RollingWindow(100);
        for (int i = 0; i < 50; i++) {
            window.record(50.0);
        }
        RollingWindow.Snapshot snapshot = window.snapshot();

        List<IncidentPattern> patterns = matcher.detectPatterns(
                "op", 55.0, false, snapshot);
        assertThat(patterns).doesNotContain(IncidentPattern.LATENCY_DEGRADATION);
    }

    @Test
    void detectsCascadeFailure() {
        RollingWindow window = new RollingWindow(100);
        for (int i = 0; i < 20; i++) {
            window.record(50.0);
        }
        RollingWindow.Snapshot snapshot = window.snapshot();

        // Record errors from 3 different dependencies
        matcher.recordDependencyError("service-a");
        matcher.recordDependencyError("service-b");
        matcher.recordDependencyError("service-c");

        List<IncidentPattern> patterns = matcher.detectPatterns(
                "op", 50.0, false, snapshot);
        assertThat(patterns).contains(IncidentPattern.CASCADE_FAILURE);
    }

    @Test
    void noCascadeWithFewDependencies() {
        RollingWindow window = new RollingWindow(100);
        for (int i = 0; i < 20; i++) {
            window.record(50.0);
        }
        RollingWindow.Snapshot snapshot = window.snapshot();

        matcher.recordDependencyError("service-a");
        matcher.recordDependencyError("service-b");

        List<IncidentPattern> patterns = matcher.detectPatterns(
                "op", 50.0, false, snapshot);
        assertThat(patterns).doesNotContain(IncidentPattern.CASCADE_FAILURE);
    }

    @Test
    void resetClearsDependencyErrors() {
        matcher.recordDependencyError("service-a");
        matcher.recordDependencyError("service-b");
        matcher.recordDependencyError("service-c");
        matcher.resetDependencyErrors();

        RollingWindow window = new RollingWindow(100);
        for (int i = 0; i < 20; i++) {
            window.record(50.0);
        }

        List<IncidentPattern> patterns = matcher.detectPatterns(
                "op", 50.0, false, window.snapshot());
        assertThat(patterns).doesNotContain(IncidentPattern.CASCADE_FAILURE);
    }

    @Test
    void returnsEmptyForNullSnapshot() {
        List<IncidentPattern> patterns = matcher.detectPatterns("op", 50.0, false, null);
        assertThat(patterns).isEmpty();
    }
}
