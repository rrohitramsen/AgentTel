package io.agenttel.agent.action;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AgentActionTrackerTest {

    private InMemorySpanExporter spanExporter;
    private AgentActionTracker tracker;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        OpenTelemetry otel = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        tracker = new AgentActionTracker(otel);
    }

    @Test
    void recordAction_createsSpanAndHistory() {
        tracker.recordAction("scale_up", "High latency detected",
                Map.of("instances", "3"));

        assertThat(spanExporter.getFinishedSpanItems()).hasSize(1);
        var span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getName()).isEqualTo("agent.action:scale_up");

        var history = tracker.getRecentActions();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).name()).isEqualTo("scale_up");
        assertThat(history.get(0).type()).isEqualTo("action");
        assertThat(history.get(0).status()).isEqualTo("completed");
    }

    @Test
    void recordDecision_capturesRationale() {
        tracker.recordDecision("response_strategy", "Error rate is rising but not critical",
                "increase_timeout", List.of("increase_timeout", "add_retry", "circuit_break"));

        assertThat(spanExporter.getFinishedSpanItems()).hasSize(1);
        var span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getName()).isEqualTo("agent.decision:response_strategy");

        var history = tracker.getRecentActions();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).type()).isEqualTo("decision");
    }

    @Test
    void traceAction_capturesSuccessfulExecution() {
        String result = tracker.traceAction("compute_recommendation", "Need action plan", () -> "scale_up");

        assertThat(result).isEqualTo("scale_up");
        assertThat(spanExporter.getFinishedSpanItems()).hasSize(1);

        var history = tracker.getRecentActions();
        assertThat(history.get(0).status()).isEqualTo("success");
    }

    @Test
    void traceAction_capturesFailedExecution() {
        assertThatThrownBy(() ->
                tracker.traceAction("risky_action", "Testing failure", () -> {
                    throw new RuntimeException("Failed!");
                })
        ).isInstanceOf(RuntimeException.class);

        assertThat(spanExporter.getFinishedSpanItems()).hasSize(1);

        var history = tracker.getRecentActions();
        assertThat(history.get(0).status()).isEqualTo("failed");
    }

    @Test
    void getActionsByType_filtersCorrectly() {
        tracker.recordAction("a1", "reason1", Map.of());
        tracker.recordDecision("d1", "reason2", "opt1", List.of("opt1", "opt2"));
        tracker.recordAction("a2", "reason3", Map.of());

        assertThat(tracker.getActionsByType("action")).hasSize(2);
        assertThat(tracker.getActionsByType("decision")).hasSize(1);
    }
}
