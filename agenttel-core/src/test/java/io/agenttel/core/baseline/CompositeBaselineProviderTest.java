package io.agenttel.core.baseline;

import io.agenttel.api.BaselineSource;
import io.agenttel.api.baseline.OperationBaseline;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeBaselineProviderTest {

    @Test
    void returnsFirstAvailableBaseline() {
        StaticBaselineProvider staticProvider = new StaticBaselineProvider();
        staticProvider.register("op-a", OperationBaseline.builder("op-a")
                .latencyP50Ms(50.0)
                .source(BaselineSource.STATIC)
                .build());

        RollingBaselineProvider rollingProvider = new RollingBaselineProvider(100, 1);
        rollingProvider.recordLatency("op-b", 100.0);
        rollingProvider.recordLatency("op-b", 110.0);

        CompositeBaselineProvider composite = new CompositeBaselineProvider(
                staticProvider, rollingProvider);

        // op-a has static baseline
        Optional<OperationBaseline> a = composite.getBaseline("op-a");
        assertThat(a).isPresent();
        assertThat(a.get().source()).isEqualTo(BaselineSource.STATIC);

        // op-b has rolling baseline
        Optional<OperationBaseline> b = composite.getBaseline("op-b");
        assertThat(b).isPresent();
        assertThat(b.get().source()).isEqualTo(BaselineSource.ROLLING_7D);

        // op-c has nothing
        assertThat(composite.getBaseline("op-c")).isEmpty();
    }

    @Test
    void staticTakesPrecedenceOverRolling() {
        StaticBaselineProvider staticProvider = new StaticBaselineProvider();
        staticProvider.register("op", OperationBaseline.builder("op")
                .latencyP50Ms(50.0)
                .source(BaselineSource.STATIC)
                .build());

        RollingBaselineProvider rollingProvider = new RollingBaselineProvider(100, 1);
        rollingProvider.recordLatency("op", 200.0);
        rollingProvider.recordLatency("op", 210.0);

        CompositeBaselineProvider composite = new CompositeBaselineProvider(
                staticProvider, rollingProvider);

        Optional<OperationBaseline> baseline = composite.getBaseline("op");
        assertThat(baseline).isPresent();
        // Should be static (50ms), not rolling (200ms)
        assertThat(baseline.get().source()).isEqualTo(BaselineSource.STATIC);
        assertThat(baseline.get().latencyP50Ms()).isEqualTo(50.0);
    }
}
