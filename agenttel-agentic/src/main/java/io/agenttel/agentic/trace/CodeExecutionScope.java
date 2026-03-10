package io.agenttel.agentic.trace;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * AutoCloseable scope wrapping a code execution span.
 * Tracks language, exit code, sandbox status, and execution output.
 */
public class CodeExecutionScope implements AutoCloseable {

    private final Span span;
    private final Scope scope;

    CodeExecutionScope(Span span, Context parentContext) {
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
     * Marks the code execution as successful with exit code 0.
     */
    public CodeExecutionScope success() {
        span.setAttribute(AgenticAttributes.CODE_STATUS, "success");
        span.setAttribute(AgenticAttributes.CODE_EXIT_CODE, 0L);
        span.setStatus(StatusCode.OK);
        return this;
    }

    /**
     * Marks the code execution as successful with a specific exit code.
     */
    public CodeExecutionScope success(int exitCode) {
        span.setAttribute(AgenticAttributes.CODE_STATUS, "success");
        span.setAttribute(AgenticAttributes.CODE_EXIT_CODE, (long) exitCode);
        span.setStatus(StatusCode.OK);
        return this;
    }

    /**
     * Marks the code execution as failed.
     */
    public CodeExecutionScope error(Throwable t, int exitCode) {
        span.setAttribute(AgenticAttributes.CODE_STATUS, "error");
        span.setAttribute(AgenticAttributes.CODE_EXIT_CODE, (long) exitCode);
        span.setStatus(StatusCode.ERROR, t.getMessage());
        span.recordException(t);
        return this;
    }

    /**
     * Marks the code execution as timed out.
     */
    public CodeExecutionScope timeout() {
        span.setAttribute(AgenticAttributes.CODE_STATUS, "timeout");
        span.setStatus(StatusCode.ERROR, "Code execution timed out");
        return this;
    }

    /**
     * Records output from the code execution as a span event.
     */
    public CodeExecutionScope output(String output) {
        span.addEvent(output);
        return this;
    }

    @Override
    public void close() {
        scope.close();
        span.end();
    }
}
