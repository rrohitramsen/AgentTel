package io.agenttel.genai.springai;

import io.agenttel.genai.conventions.AgentTelGenAiAttributes;
import io.agenttel.genai.conventions.GenAiAttributes;
import io.agenttel.genai.cost.ModelCostCalculator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * SpanExporter wrapper that enriches GenAI spans with cost calculation.
 *
 * <p>Since {@code ReadableSpan} is immutable in {@code onEnd()}, cost calculation
 * cannot be done in a SpanProcessor. This exporter wraps the real exporter and
 * adds {@code agenttel.genai.cost_usd} to span data before export.
 *
 * <p>Usage:
 * <pre>
 * SpanExporter realExporter = OtlpGrpcSpanExporter.builder().build();
 * SpanExporter withCost = new CostEnrichingSpanExporter(realExporter);
 * </pre>
 */
public class CostEnrichingSpanExporter implements SpanExporter {

    private final SpanExporter delegate;

    public CostEnrichingSpanExporter(SpanExporter delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        List<SpanData> enriched = new ArrayList<>(spans.size());
        for (SpanData span : spans) {
            enriched.add(enrichWithCost(span));
        }
        return delegate.export(enriched);
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    private SpanData enrichWithCost(SpanData span) {
        Attributes attrs = span.getAttributes();
        Long inputTokens = attrs.get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS);
        Long outputTokens = attrs.get(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS);
        String model = attrs.get(GenAiAttributes.GEN_AI_REQUEST_MODEL);
        if (model == null) {
            model = attrs.get(GenAiAttributes.GEN_AI_RESPONSE_MODEL);
        }

        if (model == null || (inputTokens == null && outputTokens == null)) {
            return span; // nothing to enrich
        }

        // Already has cost? Skip.
        if (attrs.get(AgentTelGenAiAttributes.GENAI_COST_USD) != null) {
            return span;
        }

        long in = inputTokens != null ? inputTokens : 0L;
        long out = outputTokens != null ? outputTokens : 0L;
        double cost = ModelCostCalculator.calculateCost(model, in, out);

        if (cost <= 0) {
            return span; // unknown model, skip
        }

        return new CostEnrichedSpanData(span, cost);
    }

    /**
     * Delegating SpanData that adds cost_usd to attributes.
     */
    private static class CostEnrichedSpanData implements SpanData {
        private final SpanData delegate;
        private final Attributes enrichedAttributes;

        CostEnrichedSpanData(SpanData delegate, double costUsd) {
            this.delegate = delegate;
            AttributesBuilder builder = delegate.getAttributes().toBuilder();
            builder.put(AgentTelGenAiAttributes.GENAI_COST_USD, costUsd);
            this.enrichedAttributes = builder.build();
        }

        @Override public SpanContext getSpanContext() { return delegate.getSpanContext(); }
        @Override public SpanContext getParentSpanContext() { return delegate.getParentSpanContext(); }
        @Override public Resource getResource() { return delegate.getResource(); }
        @Override public InstrumentationScopeInfo getInstrumentationScopeInfo() { return delegate.getInstrumentationScopeInfo(); }
        @SuppressWarnings("deprecation")
        @Override public io.opentelemetry.sdk.common.InstrumentationLibraryInfo getInstrumentationLibraryInfo() { return delegate.getInstrumentationLibraryInfo(); }
        @Override public String getName() { return delegate.getName(); }
        @Override public SpanKind getKind() { return delegate.getKind(); }
        @Override public long getStartEpochNanos() { return delegate.getStartEpochNanos(); }
        @Override public Attributes getAttributes() { return enrichedAttributes; }
        @Override public List<EventData> getEvents() { return delegate.getEvents(); }
        @Override public List<LinkData> getLinks() { return delegate.getLinks(); }
        @Override public StatusData getStatus() { return delegate.getStatus(); }
        @Override public long getEndEpochNanos() { return delegate.getEndEpochNanos(); }
        @Override public boolean hasEnded() { return delegate.hasEnded(); }
        @Override public int getTotalRecordedEvents() { return delegate.getTotalRecordedEvents(); }
        @Override public int getTotalRecordedLinks() { return delegate.getTotalRecordedLinks(); }
        @Override public int getTotalAttributeCount() { return enrichedAttributes.size(); }
    }
}
