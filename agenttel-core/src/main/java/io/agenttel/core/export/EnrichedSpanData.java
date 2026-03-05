package io.agenttel.core.export;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;

import java.util.List;
import java.util.Map;

/**
 * Delegating SpanData that merges additional attributes at export time.
 * All methods delegate to the original span except {@link #getAttributes()}.
 */
class EnrichedSpanData implements SpanData {

    private final SpanData delegate;
    private final Attributes enrichedAttributes;

    EnrichedSpanData(SpanData delegate, Map<io.opentelemetry.api.common.AttributeKey<?>, Object> extraAttributes) {
        this.delegate = delegate;
        AttributesBuilder builder = delegate.getAttributes().toBuilder();
        for (var entry : extraAttributes.entrySet()) {
            putAttribute(builder, entry.getKey(), entry.getValue());
        }
        this.enrichedAttributes = builder.build();
    }

    @SuppressWarnings("unchecked")
    private static void putAttribute(AttributesBuilder builder,
                                      io.opentelemetry.api.common.AttributeKey<?> key, Object value) {
        if (value instanceof String s) {
            builder.put((io.opentelemetry.api.common.AttributeKey<String>) key, s);
        } else if (value instanceof Long l) {
            builder.put((io.opentelemetry.api.common.AttributeKey<Long>) key, l);
        } else if (value instanceof Double d) {
            builder.put((io.opentelemetry.api.common.AttributeKey<Double>) key, d);
        } else if (value instanceof Boolean b) {
            builder.put((io.opentelemetry.api.common.AttributeKey<Boolean>) key, b);
        }
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
