package io.agenttel.agentic.orchestration;

import io.agenttel.agentic.OrchestrationPattern;
import io.agenttel.api.attributes.AgenticAttributes;
import io.agenttel.agentic.trace.AgentInvocation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestration for parallel/fan-out-fan-in patterns where multiple agents
 * execute concurrently.
 */
public class ParallelOrchestration extends Orchestration {

    private final AtomicLong branchCount = new AtomicLong(0);

    public ParallelOrchestration(Span span, Context parentContext, Tracer tracer, String coordinatorId) {
        super(span, parentContext, tracer, OrchestrationPattern.PARALLEL, coordinatorId);
    }

    /**
     * Creates a branch invocation for parallel execution.
     * Thread-safe — can be called from multiple threads.
     */
    public AgentInvocation branch(String agentName) {
        branchCount.incrementAndGet();
        String invocationId = UUID.randomUUID().toString();
        Span invSpan = tracer.spanBuilder("invoke_agent")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.AGENT_NAME, agentName)
                .setAttribute(AgenticAttributes.INVOCATION_ID, invocationId)
                .setAttribute(AgenticAttributes.INVOCATION_GOAL, "Branch: " + agentName)
                .startSpan();
        return new AgentInvocation(invSpan, Context.current().with(span), tracer, agentName);
    }

    /**
     * Records the aggregation strategy used to combine branch results.
     */
    public void aggregate(String strategy) {
        span.setAttribute(AgenticAttributes.ORCHESTRATION_AGGREGATION, strategy);
        span.setAttribute(AgenticAttributes.ORCHESTRATION_PARALLEL_BRANCHES, branchCount.get());
    }
}
