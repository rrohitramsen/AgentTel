package io.agenttel.agentic.trace;

import io.agenttel.agentic.EvalType;
import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * AutoCloseable scope wrapping a first-class evaluation span.
 * Tracks scorer name, criteria, score (0.0-1.0), feedback, and eval type.
 */
public class EvaluationScope implements AutoCloseable {

    private final Span span;
    private final Scope scope;

    EvaluationScope(Span span, Context parentContext) {
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
     * Sets the evaluation score (0.0 - 1.0).
     */
    public EvaluationScope score(double score) {
        span.setAttribute(AgenticAttributes.EVAL_SCORE, score);
        return this;
    }

    /**
     * Sets the evaluation feedback text.
     */
    public EvaluationScope feedback(String feedback) {
        span.setAttribute(AgenticAttributes.EVAL_FEEDBACK, feedback);
        return this;
    }

    /**
     * Marks the evaluation as passing (score above threshold).
     */
    public EvaluationScope pass() {
        span.setStatus(StatusCode.OK);
        return this;
    }

    /**
     * Marks the evaluation as failing (score below threshold).
     */
    public EvaluationScope fail(String reason) {
        span.setStatus(StatusCode.ERROR, reason);
        return this;
    }

    @Override
    public void close() {
        scope.close();
        span.end();
    }
}
