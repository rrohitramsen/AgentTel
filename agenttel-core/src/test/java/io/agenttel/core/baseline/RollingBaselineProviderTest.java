package io.agenttel.core.baseline;

import io.agenttel.api.BaselineSource;
import io.agenttel.api.baseline.OperationBaseline;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RollingBaselineProviderTest {

    @Test
    void returnsEmptyWhenNoData() {
        RollingBaselineProvider provider = new RollingBaselineProvider(100, 10);
        assertThat(provider.getBaseline("unknown")).isEmpty();
    }

    @Test
    void returnsEmptyWhenBelowMinSamples() {
        RollingBaselineProvider provider = new RollingBaselineProvider(100, 10);
        for (int i = 0; i < 5; i++) {
            provider.recordLatency("op", 50.0);
        }
        assertThat(provider.getBaseline("op")).isEmpty();
    }

    @Test
    void returnsBaselineWhenEnoughSamples() {
        RollingBaselineProvider provider = new RollingBaselineProvider(100, 10);
        for (int i = 0; i < 20; i++) {
            provider.recordLatency("op", 40.0 + i);
        }

        Optional<OperationBaseline> baseline = provider.getBaseline("op");
        assertThat(baseline).isPresent();
        assertThat(baseline.get().operationName()).isEqualTo("op");
        assertThat(baseline.get().source()).isEqualTo(BaselineSource.ROLLING_7D);
        assertThat(baseline.get().latencyP50Ms()).isCloseTo(49.5, within(1.0));
        assertThat(baseline.get().latencyP99Ms()).isGreaterThan(55.0);
        assertThat(baseline.get().updatedAt()).isNotEmpty();
    }

    @Test
    void tracksErrorRate() {
        RollingBaselineProvider provider = new RollingBaselineProvider(100, 5);
        for (int i = 0; i < 8; i++) {
            provider.recordLatency("op", 50.0);
        }
        provider.recordError("op");
        provider.recordError("op");

        Optional<OperationBaseline> baseline = provider.getBaseline("op");
        assertThat(baseline).isPresent();
        assertThat(baseline.get().errorRate()).isCloseTo(0.2, within(0.01));
    }

    @Test
    void snapshotReturnsStatistics() {
        RollingBaselineProvider provider = new RollingBaselineProvider(100, 5);
        for (int i = 1; i <= 10; i++) {
            provider.recordLatency("op", i * 10.0);
        }

        Optional<RollingWindow.Snapshot> snapshot = provider.getSnapshot("op");
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().mean()).isCloseTo(55.0, within(0.1));
        assertThat(snapshot.get().stddev()).isGreaterThan(0);
    }
}
