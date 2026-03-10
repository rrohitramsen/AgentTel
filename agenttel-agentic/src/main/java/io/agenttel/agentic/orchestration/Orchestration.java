package io.agenttel.agentic.orchestration;

import io.agenttel.agentic.OrchestrationPattern;
import io.agenttel.api.attributes.AgenticAttributes;
import io.agenttel.agentic.trace.AgentInvocation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.UUID;

/**
 * Base class for orchestrated multi-agent workflows.
 * Wraps a session-level span and provides agent invocation creation.
 */
public class Orchestration implements AutoCloseable {

    protected final Span span;
    protected final Scope scope;
    protected final Tracer tracer;
    protected final OrchestrationPattern pattern;
    protected final String coordinatorId;

    public Orchestration(Span span, Context parentContext, Tracer tracer,
                         OrchestrationPattern pattern, String coordinatorId) {
        this.span = span;
        this.scope = parentContext != null
                ? parentContext.with(span).makeCurrent()
                : Context.current().with(span).makeCurrent();
        this.tracer = tracer;
        this.pattern = pattern;
        this.coordinatorId = coordinatorId;

        if (coordinatorId != null) {
            span.setAttribute(AgenticAttributes.ORCHESTRATION_COORDINATOR_ID, coordinatorId);
        }
    }

    /**
     * Returns the underlying OTel span.
     */
    public Span span() {
        return span;
    }

    /**
     * Creates a child agent invocation within this orchestration.
     */
    public AgentInvocation invoke(String agentName, String goal) {
        String invocationId = UUID.randomUUID().toString();
        Span invSpan = tracer.spanBuilder("invoke_agent")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.AGENT_NAME, agentName)
                .setAttribute(AgenticAttributes.INVOCATION_ID, invocationId)
                .setAttribute(AgenticAttributes.INVOCATION_GOAL, goal)
                .startSpan();
        return new AgentInvocation(invSpan, Context.current().with(span), tracer, agentName);
    }

    /**
     * Marks this orchestration as completed successfully.
     */
    public void complete() {
        span.setStatus(StatusCode.OK);
    }

    /**
     * Marks this orchestration as failed.
     */
    public void fail(String reason) {
        span.setStatus(StatusCode.ERROR, reason);
    }

    @Override
    public void close() {
        scope.close();
        span.end();
    }
}
