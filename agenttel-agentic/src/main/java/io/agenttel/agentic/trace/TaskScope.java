package io.agenttel.agentic.trace;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.UUID;

/**
 * AutoCloseable scope wrapping a task span for task decomposition tracking.
 * Tasks can nest to form decomposition trees.
 */
public class TaskScope implements AutoCloseable {

    private final Span span;
    private final Scope scope;
    private final Tracer tracer;
    private final String taskId;
    private final long depth;

    TaskScope(Span span, Context parentContext, Tracer tracer, String taskId, long depth) {
        this.span = span;
        this.scope = parentContext != null
                ? parentContext.with(span).makeCurrent()
                : Context.current().with(span).makeCurrent();
        this.tracer = tracer;
        this.taskId = taskId;
        this.depth = depth;
    }

    /**
     * Returns the underlying OTel span.
     */
    public Span span() {
        return span;
    }

    /**
     * Returns this task's ID for use as a parent reference.
     */
    public String taskId() {
        return taskId;
    }

    /**
     * Creates a nested subtask within this task.
     */
    public TaskScope subtask(String name) {
        String subtaskId = UUID.randomUUID().toString();
        Span subtaskSpan = tracer.spanBuilder("agenttel.agentic.task")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.TASK_ID, subtaskId)
                .setAttribute(AgenticAttributes.TASK_NAME, name)
                .setAttribute(AgenticAttributes.TASK_STATUS, "in_progress")
                .setAttribute(AgenticAttributes.TASK_PARENT_ID, taskId)
                .setAttribute(AgenticAttributes.TASK_DEPTH, depth + 1)
                .startSpan();
        return new TaskScope(subtaskSpan, Context.current().with(span), tracer, subtaskId, depth + 1);
    }

    /**
     * Marks this task as completed.
     */
    public TaskScope complete() {
        span.setAttribute(AgenticAttributes.TASK_STATUS, "completed");
        span.setStatus(StatusCode.OK);
        return this;
    }

    /**
     * Marks this task as failed.
     */
    public TaskScope fail(String reason) {
        span.setAttribute(AgenticAttributes.TASK_STATUS, "failed");
        span.setStatus(StatusCode.ERROR, reason);
        return this;
    }

    /**
     * Marks this task as delegated to another agent.
     */
    public TaskScope delegated() {
        span.setAttribute(AgenticAttributes.TASK_STATUS, "delegated");
        return this;
    }

    @Override
    public void close() {
        scope.close();
        span.end();
    }
}
