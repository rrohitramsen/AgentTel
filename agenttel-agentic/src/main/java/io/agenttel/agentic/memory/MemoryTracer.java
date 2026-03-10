package io.agenttel.agentic.memory;

import io.agenttel.agentic.MemoryOperation;
import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

/**
 * Traces memory read/write/search operations performed by an agent.
 */
public class MemoryTracer {

    private final Tracer tracer;

    public MemoryTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Records a memory access operation.
     */
    public void trace(MemoryOperation operation, String storeType, long items) {
        Span memSpan = tracer.spanBuilder("agenttel.agentic.memory")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.MEMORY_OPERATION, operation.getValue())
                .setAttribute(AgenticAttributes.MEMORY_STORE_TYPE, storeType)
                .setAttribute(AgenticAttributes.MEMORY_ITEMS, items)
                .startSpan();
        memSpan.end();
    }

    /**
     * Records a memory access as a child of a specific parent context.
     */
    public void trace(MemoryOperation operation, String storeType, long items, Context parentContext) {
        Span memSpan = tracer.spanBuilder("agenttel.agentic.memory")
                .setParent(parentContext)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.MEMORY_OPERATION, operation.getValue())
                .setAttribute(AgenticAttributes.MEMORY_STORE_TYPE, storeType)
                .setAttribute(AgenticAttributes.MEMORY_ITEMS, items)
                .startSpan();
        memSpan.end();
    }
}
