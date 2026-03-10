package io.agenttel.agentic.quality;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QualityTrackerTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void tracksQualitySignals() {
        QualityTracker tracker = new QualityTracker();
        tracker.setGoalAchieved(true);
        tracker.recordHumanIntervention();
        tracker.recordHumanIntervention();
        tracker.setEvalScore(0.85);

        Span span = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build()
                .getTracer("test")
                .spanBuilder("test").startSpan();
        tracker.applyTo(span);
        span.end();

        SpanData spanData = spanExporter.getFinishedSpanItems().get(0);
        assertThat(spanData.getAttributes().get(AgenticAttributes.QUALITY_GOAL_ACHIEVED)).isTrue();
        assertThat(spanData.getAttributes().get(AgenticAttributes.QUALITY_HUMAN_INTERVENTIONS)).isEqualTo(2L);
        assertThat(spanData.getAttributes().get(AgenticAttributes.QUALITY_EVAL_SCORE)).isEqualTo(0.85);
        assertThat(spanData.getAttributes().get(AgenticAttributes.QUALITY_LOOP_DETECTED)).isFalse();
    }

    @Test
    void loopDetectedSignal() {
        QualityTracker tracker = new QualityTracker();
        tracker.setLoopDetected(true);

        assertThat(tracker.isLoopDetected()).isTrue();
        assertThat(tracker.isGoalAchieved()).isFalse();
        assertThat(tracker.getHumanInterventions()).isEqualTo(0L);
    }
}
