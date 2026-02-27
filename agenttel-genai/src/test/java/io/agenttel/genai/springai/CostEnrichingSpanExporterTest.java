package io.agenttel.genai.springai;

import io.agenttel.genai.conventions.AgentTelGenAiAttributes;
import io.agenttel.genai.conventions.GenAiAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CostEnrichingSpanExporterTest {

    @Test
    void enrichesGenAiSpanWithCost() {
        List<SpanData> captured = new ArrayList<>();
        SpanExporter mockExporter = new CapturingExporter(captured);

        CostEnrichingSpanExporter exporter = new CostEnrichingSpanExporter(mockExporter);

        SpanData span = createGenAiSpan("claude-sonnet-4-20250514", 1000L, 500L);
        exporter.export(List.of(span));

        assertThat(captured).hasSize(1);
        Double cost = captured.get(0).getAttributes().get(AgentTelGenAiAttributes.GENAI_COST_USD);
        assertThat(cost).isNotNull();
        assertThat(cost).isGreaterThan(0.0);
    }

    @Test
    void skipsSpansWithoutTokens() {
        List<SpanData> captured = new ArrayList<>();
        SpanExporter mockExporter = new CapturingExporter(captured);

        CostEnrichingSpanExporter exporter = new CostEnrichingSpanExporter(mockExporter);

        // Span without token attributes
        SpanData span = createSimpleSpan();
        exporter.export(List.of(span));

        assertThat(captured).hasSize(1);
        Double cost = captured.get(0).getAttributes().get(AgentTelGenAiAttributes.GENAI_COST_USD);
        assertThat(cost).isNull(); // no enrichment
    }

    @Test
    void delegatesFlushAndShutdown() {
        CapturingExporter mockExporter = new CapturingExporter(new ArrayList<>());
        CostEnrichingSpanExporter exporter = new CostEnrichingSpanExporter(mockExporter);

        exporter.flush();
        assertThat(mockExporter.flushed).isTrue();

        exporter.shutdown();
        assertThat(mockExporter.shutdown).isTrue();
    }

    private SpanData createGenAiSpan(String model, long inputTokens, long outputTokens) {
        return new TestSpanData(
                Attributes.builder()
                        .put(GenAiAttributes.GEN_AI_REQUEST_MODEL, model)
                        .put(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS, inputTokens)
                        .put(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, outputTokens)
                        .build(),
                "gen_ai.client.operation"
        );
    }

    private SpanData createSimpleSpan() {
        return new TestSpanData(Attributes.empty(), "HTTP GET /api/users");
    }

    private static class CapturingExporter implements SpanExporter {
        final List<SpanData> captured;
        boolean flushed = false;
        boolean shutdown = false;

        CapturingExporter(List<SpanData> captured) {
            this.captured = captured;
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            captured.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            flushed = true;
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            shutdown = true;
            return CompletableResultCode.ofSuccess();
        }
    }

    private static class TestSpanData implements SpanData {
        private final Attributes attributes;
        private final String name;

        TestSpanData(Attributes attributes, String name) {
            this.attributes = attributes;
            this.name = name;
        }

        @Override public SpanContext getSpanContext() {
            return SpanContext.create(
                    "00000000000000000000000000000001",
                    "0000000000000001",
                    TraceFlags.getSampled(), TraceState.getDefault());
        }
        @Override public SpanContext getParentSpanContext() { return SpanContext.getInvalid(); }
        @Override public Resource getResource() { return Resource.getDefault(); }
        @Override public InstrumentationScopeInfo getInstrumentationScopeInfo() { return InstrumentationScopeInfo.empty(); }
        @SuppressWarnings("deprecation")
        @Override public io.opentelemetry.sdk.common.InstrumentationLibraryInfo getInstrumentationLibraryInfo() { return io.opentelemetry.sdk.common.InstrumentationLibraryInfo.empty(); }
        @Override public String getName() { return name; }
        @Override public SpanKind getKind() { return SpanKind.CLIENT; }
        @Override public long getStartEpochNanos() { return 0; }
        @Override public Attributes getAttributes() { return attributes; }
        @Override public List<EventData> getEvents() { return Collections.emptyList(); }
        @Override public List<LinkData> getLinks() { return Collections.emptyList(); }
        @Override public StatusData getStatus() { return StatusData.ok(); }
        @Override public long getEndEpochNanos() { return 1000000; }
        @Override public boolean hasEnded() { return true; }
        @Override public int getTotalRecordedEvents() { return 0; }
        @Override public int getTotalRecordedLinks() { return 0; }
        @Override public int getTotalAttributeCount() { return attributes.size(); }
    }
}
