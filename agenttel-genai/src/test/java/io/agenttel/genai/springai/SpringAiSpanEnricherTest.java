package io.agenttel.genai.springai;

import io.agenttel.genai.conventions.AgentTelGenAiAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiSpanEnricherTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        SpringAiSpanEnricher enricher = new SpringAiSpanEnricher();

        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(enricher)
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void enrichesSpringAiSpanWithFrameworkAttribute() {
        var tracer = tracerProvider.get("test");
        var span = tracer.spanBuilder("gen_ai.client.operation")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        span.end();

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData spanData = spans.get(0);
        assertThat(spanData.getAttributes().get(AgentTelGenAiAttributes.GENAI_FRAMEWORK))
                .isEqualTo("spring_ai");
    }

    @Test
    void doesNotEnrichNonSpringAiSpans() {
        var tracer = tracerProvider.get("test");
        var span = tracer.spanBuilder("http.request")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        span.end();

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData spanData = spans.get(0);
        assertThat(spanData.getAttributes().get(AgentTelGenAiAttributes.GENAI_FRAMEWORK)).isNull();
    }
}
