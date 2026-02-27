package io.agenttel.core.enrichment;

import io.agenttel.api.BaselineSource;
import io.agenttel.api.EscalationLevel;
import io.agenttel.api.attributes.AgentTelAttributes;
import io.agenttel.api.baseline.OperationBaseline;
import io.agenttel.core.baseline.StaticBaselineProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTelSpanProcessorTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();

        // Set up baselines
        StaticBaselineProvider baselineProvider = new StaticBaselineProvider();
        baselineProvider.register("POST /api/payments", OperationBaseline.builder("POST /api/payments")
                .latencyP50Ms(45.0)
                .latencyP99Ms(200.0)
                .errorRate(0.001)
                .source(BaselineSource.STATIC)
                .build());

        // Set up operation contexts
        OperationContextRegistry opContexts = new OperationContextRegistry();
        opContexts.register("POST /api/payments", new OperationContext(
                true, true, "https://wiki/runbooks/payment",
                "cached pricing", EscalationLevel.PAGE_ONCALL, false
        ));

        AgentTelSpanProcessor processor = new AgentTelSpanProcessor(baselineProvider, opContexts);

        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(processor)
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        tracer = tracerProvider.get("test");
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void enrichesSpanWithBaselineAttributes() {
        tracer.spanBuilder("POST /api/payments").startSpan().end();

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AgentTelAttributes.BASELINE_LATENCY_P50_MS))
                .isEqualTo(45.0);
        assertThat(span.getAttributes().get(AgentTelAttributes.BASELINE_LATENCY_P99_MS))
                .isEqualTo(200.0);
        assertThat(span.getAttributes().get(AgentTelAttributes.BASELINE_ERROR_RATE))
                .isEqualTo(0.001);
        assertThat(span.getAttributes().get(AgentTelAttributes.BASELINE_SOURCE))
                .isEqualTo("static");
    }

    @Test
    void enrichesSpanWithDecisionMetadata() {
        tracer.spanBuilder("POST /api/payments").startSpan().end();

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AgentTelAttributes.DECISION_RETRYABLE)).isTrue();
        assertThat(span.getAttributes().get(AgentTelAttributes.DECISION_IDEMPOTENT)).isTrue();
        assertThat(span.getAttributes().get(AgentTelAttributes.DECISION_RUNBOOK_URL))
                .isEqualTo("https://wiki/runbooks/payment");
        assertThat(span.getAttributes().get(AgentTelAttributes.DECISION_FALLBACK_AVAILABLE)).isTrue();
        assertThat(span.getAttributes().get(AgentTelAttributes.DECISION_ESCALATION_LEVEL))
                .isEqualTo("page_oncall");
        assertThat(span.getAttributes().get(AgentTelAttributes.DECISION_SAFE_TO_RESTART)).isFalse();
    }

    @Test
    void spanWithUnknownOperationGetsNoBaselineOrDecision() {
        tracer.spanBuilder("GET /unknown").startSpan().end();

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        // Topology is on the OTel Resource (not per-span), so no topology attributes here
        assertThat(span.getAttributes().get(AgentTelAttributes.BASELINE_LATENCY_P99_MS)).isNull();
        assertThat(span.getAttributes().get(AgentTelAttributes.DECISION_RETRYABLE)).isNull();
    }
}
