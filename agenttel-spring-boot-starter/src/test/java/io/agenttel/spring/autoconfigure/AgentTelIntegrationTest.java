package io.agenttel.spring.autoconfigure;

import io.agenttel.api.EscalationLevel;
import io.agenttel.api.attributes.AgentTelAttributes;
import io.agenttel.core.anomaly.AnomalyDetector;
import io.agenttel.core.anomaly.PatternMatcher;
import io.agenttel.core.baseline.RollingBaselineProvider;
import io.agenttel.core.baseline.StaticBaselineProvider;
import io.agenttel.core.engine.AgentTelEngine;
import io.agenttel.core.enrichment.AgentTelSpanProcessor;
import io.agenttel.core.slo.SloDefinition;
import io.agenttel.core.slo.SloTracker;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that boots a real Spring context with AgentTel auto-configuration
 * and verifies the full span enrichment pipeline works end-to-end.
 */
class AgentTelIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentTelAutoConfiguration.class))
            .withUserConfiguration(OTelTestConfig.class)
            .withPropertyValues(
                    "agenttel.topology.team=payments-platform",
                    "agenttel.topology.tier=critical",
                    "agenttel.topology.domain=commerce",
                    "agenttel.topology.on-call-channel=#payments-oncall"
            );

    @Configuration
    static class OTelTestConfig {
        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }
    }

    @Test
    void fullPipelineEnrichesSpans() {
        contextRunner.run(context -> {
            AgentTelEngine engine = context.getBean(AgentTelEngine.class);
            AgentTelSpanProcessor processor = context.getBean(AgentTelSpanProcessor.class);

            InMemorySpanExporter exporter = InMemorySpanExporter.create();
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(processor)
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build();

            Tracer tracer = tracerProvider.get("integration-test");

            // Create and end a span
            tracer.spanBuilder("POST /api/payments").startSpan().end();

            List<SpanData> spans = exporter.getFinishedSpanItems();
            assertThat(spans).hasSize(1);

            SpanData span = spans.get(0);
            // Verify topology enrichment
            assertThat(span.getAttributes().get(AgentTelAttributes.TOPOLOGY_TEAM))
                    .isEqualTo("payments-platform");
            assertThat(span.getAttributes().get(AgentTelAttributes.TOPOLOGY_TIER))
                    .isEqualTo("critical");
            assertThat(span.getAttributes().get(AgentTelAttributes.TOPOLOGY_DOMAIN))
                    .isEqualTo("commerce");
            assertThat(span.getAttributes().get(AgentTelAttributes.TOPOLOGY_ON_CALL_CHANNEL))
                    .isEqualTo("#payments-oncall");

            tracerProvider.close();
        });
    }

    @Test
    void allPhase3BeansCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RollingBaselineProvider.class);
            assertThat(context).hasSingleBean(PatternMatcher.class);
            assertThat(context).hasSingleBean(SloTracker.class);
            assertThat(context).hasSingleBean(AnomalyDetector.class);
            assertThat(context).hasSingleBean(AgentTelEngine.class);
            assertThat(context).hasSingleBean(AgentTelSpanProcessor.class);
        });
    }

    @Test
    void rollingBaselinesFeedFromSpans() {
        contextRunner.run(context -> {
            AgentTelSpanProcessor processor = context.getBean(AgentTelSpanProcessor.class);
            RollingBaselineProvider rollingBaselines = context.getBean(RollingBaselineProvider.class);

            InMemorySpanExporter exporter = InMemorySpanExporter.create();
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(processor)
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build();

            Tracer tracer = tracerProvider.get("integration-test");

            // Create enough spans to build rolling baselines
            for (int i = 0; i < 15; i++) {
                var span = tracer.spanBuilder("POST /api/payments").startSpan();
                // Simulate some duration (spans complete almost instantly in tests)
                span.end();
            }

            // Rolling baselines should have recorded the spans
            assertThat(rollingBaselines.getSnapshot("POST /api/payments")).isPresent();

            tracerProvider.close();
        });
    }

    @Test
    void sloTrackingFromSpans() {
        contextRunner.run(context -> {
            AgentTelSpanProcessor processor = context.getBean(AgentTelSpanProcessor.class);
            SloTracker sloTracker = context.getBean(SloTracker.class);

            // Register an SLO
            sloTracker.register(SloDefinition.builder("payment-availability")
                    .operationName("POST /api/payments")
                    .type(SloDefinition.SloType.AVAILABILITY)
                    .target(0.999)
                    .build());

            InMemorySpanExporter exporter = InMemorySpanExporter.create();
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(processor)
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build();

            Tracer tracer = tracerProvider.get("integration-test");

            // Create successful spans
            for (int i = 0; i < 10; i++) {
                tracer.spanBuilder("POST /api/payments").startSpan().end();
            }

            // Create an error span
            var errorSpan = tracer.spanBuilder("POST /api/payments").startSpan();
            errorSpan.setStatus(StatusCode.ERROR, "test error");
            errorSpan.end();

            // Verify SLO tracked the requests
            SloTracker.SloStatus status = sloTracker.getStatus("payment-availability");
            assertThat(status).isNotNull();
            assertThat(status.totalRequests()).isEqualTo(11);
            assertThat(status.failedRequests()).isEqualTo(1);

            tracerProvider.close();
        });
    }
}
