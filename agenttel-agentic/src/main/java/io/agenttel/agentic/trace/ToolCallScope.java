package io.agenttel.agentic.trace;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * AutoCloseable scope wrapping a tool call span within an agent invocation.
 */
public class ToolCallScope implements AutoCloseable {

    private final Span span;
    private final Scope scope;

    ToolCallScope(Span span, Context parentContext) {
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
     * Marks the tool call as successful.
     */
    public ToolCallScope success() {
        span.setAttribute(AgenticAttributes.STEP_TOOL_STATUS, "success");
        span.setStatus(StatusCode.OK);
        return this;
    }

    /**
     * Marks the tool call as failed with an error.
     */
    public ToolCallScope error(Throwable t) {
        span.setAttribute(AgenticAttributes.STEP_TOOL_STATUS, "error");
        span.setStatus(StatusCode.ERROR, t.getMessage());
        span.recordException(t);
        return this;
    }

    /**
     * Marks the tool call as timed out.
     */
    public ToolCallScope timeout() {
        span.setAttribute(AgenticAttributes.STEP_TOOL_STATUS, "timeout");
        span.setStatus(StatusCode.ERROR, "Tool call timed out");
        return this;
    }

    @Override
    public void close() {
        scope.close();
        span.end();
    }
}
