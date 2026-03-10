package io.agenttel.agentic.trace;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * AutoCloseable scope wrapping a handoff span when one agent delegates to another.
 */
public class HandoffScope implements AutoCloseable {

    private final Span span;
    private final Scope scope;

    HandoffScope(Span span, Context parentContext) {
        this.span = span;
        this.scope = parentContext != null
                ? parentContext.with(span).makeCurrent()
                : Context.current().with(span).makeCurrent();
    }

    /**
     * Returns the underlying OTel span.
     */
    public Span span() {
        return span;
    }

    /**
     * Marks the handoff as successful.
     */
    public HandoffScope success() {
        span.setStatus(StatusCode.OK);
        return this;
    }

    /**
     * Marks the handoff as failed.
     */
    public HandoffScope error(Throwable t) {
        span.setStatus(StatusCode.ERROR, t.getMessage());
        span.recordException(t);
        return this;
    }

    @Override
    public void close() {
        scope.close();
        span.end();
    }
}
