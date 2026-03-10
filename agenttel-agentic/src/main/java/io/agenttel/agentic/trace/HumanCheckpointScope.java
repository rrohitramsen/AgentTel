package io.agenttel.agentic.trace;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * AutoCloseable scope wrapping a human-in-the-loop checkpoint span.
 * Captures wait time and human decision.
 */
public class HumanCheckpointScope implements AutoCloseable {

    private final Span span;
    private final Scope scope;
    private final long startTimeMs;

    HumanCheckpointScope(Span span, Context parentContext) {
        this.span = span;
        this.scope = parentContext != null
                ? parentContext.with(span).makeCurrent()
                : Context.current().with(span).makeCurrent();
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Returns the underlying OTel span.
     */
    public Span span() {
        return span;
    }

    /**
     * Records the human decision and computes wait duration.
     */
    public HumanCheckpointScope decision(String decision) {
        long waitMs = System.currentTimeMillis() - startTimeMs;
        span.setAttribute(AgenticAttributes.HUMAN_DECISION, decision);
        span.setAttribute(AgenticAttributes.HUMAN_WAIT_MS, waitMs);
        span.setStatus(StatusCode.OK);
        return this;
    }

    @Override
    public void close() {
        scope.close();
        span.end();
    }
}
