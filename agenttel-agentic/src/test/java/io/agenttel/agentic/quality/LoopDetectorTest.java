package io.agenttel.agentic.quality;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoopDetectorTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        tracer = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build()
                .getTracer("test");
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void detectsLoopAfterThreshold() {
        LoopDetector detector = new LoopDetector(3);
        Span parentSpan = tracer.spanBuilder("test").startSpan();

        assertThat(detector.recordCall("get_data", "arg1", parentSpan)).isFalse();
        assertThat(detector.recordCall("get_data", "arg1", parentSpan)).isFalse();
        assertThat(detector.recordCall("get_data", "arg1", parentSpan)).isTrue();

        parentSpan.end();

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AgenticAttributes.QUALITY_LOOP_DETECTED)).isTrue();
        assertThat(span.getAttributes().get(AgenticAttributes.QUALITY_LOOP_ITERATIONS)).isEqualTo(3L);
    }

    @Test
    void differentCallsDoNotTrigger() {
        LoopDetector detector = new LoopDetector(3);
        Span parentSpan = tracer.spanBuilder("test").startSpan();

        assertThat(detector.recordCall("tool_a", "arg1", parentSpan)).isFalse();
        assertThat(detector.recordCall("tool_b", "arg1", parentSpan)).isFalse();
        assertThat(detector.recordCall("tool_a", "arg2", parentSpan)).isFalse();

        parentSpan.end();
    }

    @Test
    void resetClearsState() {
        LoopDetector detector = new LoopDetector(3);
        Span parentSpan = tracer.spanBuilder("test").startSpan();

        detector.recordCall("get_data", "arg1", parentSpan);
        detector.recordCall("get_data", "arg1", parentSpan);
        detector.reset();
        assertThat(detector.recordCall("get_data", "arg1", parentSpan)).isFalse();

        parentSpan.end();
    }

    @Test
    void defaultThresholdIsThree() {
        LoopDetector detector = new LoopDetector();
        Span parentSpan = tracer.spanBuilder("test").startSpan();

        detector.recordCall("tool", "x", parentSpan);
        detector.recordCall("tool", "x", parentSpan);
        assertThat(detector.recordCall("tool", "x", parentSpan)).isTrue();

        parentSpan.end();
    }
}
