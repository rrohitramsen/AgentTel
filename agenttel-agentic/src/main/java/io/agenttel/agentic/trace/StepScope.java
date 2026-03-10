package io.agenttel.agentic.trace;

import io.agenttel.agentic.StepType;
import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * AutoCloseable scope wrapping a single reasoning step span
 * (thought, action, observation, evaluation, revision).
 */
public class StepScope implements AutoCloseable {

    private final Span span;
    private final Scope scope;

    StepScope(Span span, Context parentContext) {
        this.span = span;
        this.scope = parentContext != null
                ? parentContext.with(span).makeCurrent()
                : Context.current().with(span).makeCurrent();
    }

    /**
     * Returns the underlying OTel span for advanced usage.
     */
    public Span span() {
        return span;
    }

    /**
     * Marks this step as having a tool call with the given name.
     */
    public StepScope toolName(String toolName) {
        span.setAttribute(AgenticAttributes.STEP_TOOL_NAME, toolName);
        return this;
    }

    /**
     * Sets the tool call status for this step.
     */
    public StepScope toolStatus(String status) {
        span.setAttribute(AgenticAttributes.STEP_TOOL_STATUS, status);
        return this;
    }

    /**
     * Marks this step as failed with an error.
     */
    public StepScope error(Throwable t) {
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
