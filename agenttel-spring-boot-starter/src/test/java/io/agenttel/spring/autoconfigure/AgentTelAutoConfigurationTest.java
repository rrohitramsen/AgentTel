package io.agenttel.spring.autoconfigure;

import io.agenttel.core.anomaly.AnomalyDetector;
import io.agenttel.core.baseline.StaticBaselineProvider;
import io.agenttel.core.causality.CausalityTracker;
import io.agenttel.core.engine.AgentTelEngine;
import io.agenttel.core.enrichment.AgentTelSpanProcessor;
import io.agenttel.core.enrichment.OperationContextRegistry;
import io.agenttel.core.topology.TopologyRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTelAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentTelAutoConfiguration.class))
            .withUserConfiguration(OTelTestConfig.class);

    @Configuration
    static class OTelTestConfig {
        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }
    }

    @Test
    void createsAllRequiredBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TopologyRegistry.class);
            assertThat(context).hasSingleBean(StaticBaselineProvider.class);
            assertThat(context).hasSingleBean(OperationContextRegistry.class);
            assertThat(context).hasSingleBean(AnomalyDetector.class);
            assertThat(context).hasSingleBean(CausalityTracker.class);
            assertThat(context).hasSingleBean(AgentTelEngine.class);
            assertThat(context).hasSingleBean(AgentTelSpanProcessor.class);
        });
    }

    @Test
    void disabledWhenPropertyIsFalse() {
        contextRunner
                .withPropertyValues("agenttel.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AgentTelEngine.class);
                    assertThat(context).doesNotHaveBean(AgentTelSpanProcessor.class);
                });
    }

    @Test
    void bindsTopologyProperties() {
        contextRunner
                .withPropertyValues(
                        "agenttel.topology.team=payments-platform",
                        "agenttel.topology.tier=critical",
                        "agenttel.topology.domain=commerce",
                        "agenttel.topology.on-call-channel=#payments-oncall"
                )
                .run(context -> {
                    TopologyRegistry registry = context.getBean(TopologyRegistry.class);
                    assertThat(registry.getTeam()).isEqualTo("payments-platform");
                    assertThat(registry.getTier().getValue()).isEqualTo("critical");
                    assertThat(registry.getDomain()).isEqualTo("commerce");
                    assertThat(registry.getOnCallChannel()).isEqualTo("#payments-oncall");
                });
    }

    @Test
    void bindsAnomalyDetectionProperties() {
        contextRunner
                .withPropertyValues("agenttel.anomaly-detection.z-score-threshold=4.0")
                .run(context -> {
                    assertThat(context).hasSingleBean(AnomalyDetector.class);
                });
    }
}
