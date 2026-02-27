package io.agenttel.core.baseline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RollingWindowTest {

    @Test
    void emptyWindowReturnsEmptySnapshot() {
        RollingWindow window = new RollingWindow(100);
        RollingWindow.Snapshot snapshot = window.snapshot();
        assertThat(snapshot.isEmpty()).isTrue();
        assertThat(snapshot.sampleCount()).isZero();
    }

    @Test
    void singleSample() {
        RollingWindow window = new RollingWindow(100);
        window.record(50.0);
        RollingWindow.Snapshot snapshot = window.snapshot();
        assertThat(snapshot.mean()).isEqualTo(50.0);
        assertThat(snapshot.p50()).isEqualTo(50.0);
        assertThat(snapshot.p99()).isEqualTo(50.0);
        assertThat(snapshot.sampleCount()).isEqualTo(1);
    }

    @Test
    void computesCorrectStatistics() {
        RollingWindow window = new RollingWindow(100);
        for (int i = 1; i <= 100; i++) {
            window.record(i);
        }
        RollingWindow.Snapshot snapshot = window.snapshot();
        assertThat(snapshot.mean()).isCloseTo(50.5, within(0.1));
        assertThat(snapshot.p50()).isCloseTo(50.0, within(1.0));
        assertThat(snapshot.p99()).isCloseTo(99.0, within(1.5));
        assertThat(snapshot.sampleCount()).isEqualTo(100);
    }

    @Test
    void ringBufferOverwritesOldSamples() {
        RollingWindow window = new RollingWindow(10);
        for (int i = 1; i <= 20; i++) {
            window.record(i);
        }
        // Buffer should contain values 11-20 (last 10)
        RollingWindow.Snapshot snapshot = window.snapshot();
        assertThat(snapshot.sampleCount()).isEqualTo(10);
        // Mean of 11..20 = 15.5
        assertThat(snapshot.mean()).isCloseTo(15.5, within(0.1));
    }

    @Test
    void tracksErrorRate() {
        RollingWindow window = new RollingWindow(100);
        for (int i = 0; i < 9; i++) {
            window.record(50.0);
        }
        window.recordError(); // 1 error out of 10 total
        RollingWindow.Snapshot snapshot = window.snapshot();
        assertThat(snapshot.errorRate()).isCloseTo(0.1, within(0.01));
    }

    @Test
    void computesStddev() {
        RollingWindow window = new RollingWindow(100);
        // All same values -> stddev = 0
        for (int i = 0; i < 10; i++) {
            window.record(50.0);
        }
        assertThat(window.snapshot().stddev()).isCloseTo(0.0, within(0.01));

        // Varied values -> non-zero stddev
        RollingWindow window2 = new RollingWindow(100);
        window2.record(10.0);
        window2.record(20.0);
        window2.record(30.0);
        window2.record(40.0);
        window2.record(50.0);
        assertThat(window2.snapshot().stddev()).isGreaterThan(10.0);
    }
}
