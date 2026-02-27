package io.agenttel.core.baseline;

import io.agenttel.api.BaselineSource;
import io.agenttel.api.baseline.OperationBaseline;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticBaselineProviderTest {

    @Test
    void registerAndRetrieveBaseline() {
        var provider = new StaticBaselineProvider();

        var baseline = OperationBaseline.builder("POST /api/payments")
                .latencyP50Ms(45.0)
                .latencyP99Ms(200.0)
                .errorRate(0.001)
                .source(BaselineSource.STATIC)
                .build();

        provider.register("POST /api/payments", baseline);

        var result = provider.getBaseline("POST /api/payments");
        assertThat(result).isPresent();
        assertThat(result.get().latencyP50Ms()).isEqualTo(45.0);
        assertThat(result.get().latencyP99Ms()).isEqualTo(200.0);
        assertThat(result.get().errorRate()).isEqualTo(0.001);
        assertThat(result.get().source()).isEqualTo(BaselineSource.STATIC);
    }

    @Test
    void returnsEmptyForUnknownOperation() {
        var provider = new StaticBaselineProvider();
        assertThat(provider.getBaseline("unknown")).isEmpty();
    }

    @Test
    void durationParserWorksCorrectly() {
        assertThat(DurationParser.parseToMs("200ms")).isEqualTo(200.0);
        assertThat(DurationParser.parseToMs("1s")).isEqualTo(1000.0);
        assertThat(DurationParser.parseToMs("1.5s")).isEqualTo(1500.0);
        assertThat(DurationParser.parseToMs("2m")).isEqualTo(120000.0);
        assertThat(DurationParser.parseToMs("500")).isEqualTo(500.0);
        assertThat(DurationParser.parseToMs("")).isEqualTo(-1.0);
        assertThat(DurationParser.parseToMs(null)).isEqualTo(-1.0);
        assertThat(DurationParser.parseToMs("invalid")).isEqualTo(-1.0);
    }
}
