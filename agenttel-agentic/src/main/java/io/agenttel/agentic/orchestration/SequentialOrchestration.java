package io.agenttel.agentic.orchestration;

import io.agenttel.agentic.OrchestrationPattern;
import io.agenttel.api.attributes.AgenticAttributes;
import io.agenttel.agentic.trace.AgentInvocation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.UUID;

/**
 * Orchestration for sequential/pipeline patterns where agents execute in order.
 * Tracks stage number and total stages.
 */
public class SequentialOrchestration extends Orchestration {

    private final long totalStages;

    public SequentialOrchestration(Span span, Context parentContext, Tracer tracer, String coordinatorId) {
        super(span, parentContext, tracer, OrchestrationPattern.SEQUENTIAL, coordinatorId);
        this.totalStages = 0;
    }

    public SequentialOrchestration(Span span, Context parentContext, Tracer tracer,
                                    String coordinatorId, int totalStages) {
        super(span, parentContext, tracer, OrchestrationPattern.SEQUENTIAL, coordinatorId);
        this.totalStages = totalStages;
    }

    /**
     * Creates a stage invocation with its ordinal position.
     */
    public AgentInvocation stage(String agentName, int stageNumber) {
        String invocationId = UUID.randomUUID().toString();
        var spanBuilder = tracer.spanBuilder("invoke_agent")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.AGENT_NAME, agentName)
                .setAttribute(AgenticAttributes.INVOCATION_ID, invocationId)
                .setAttribute(AgenticAttributes.INVOCATION_GOAL, "Stage " + stageNumber + ": " + agentName)
                .setAttribute(AgenticAttributes.ORCHESTRATION_STAGE, (long) stageNumber);

        if (totalStages > 0) {
            spanBuilder.setAttribute(AgenticAttributes.ORCHESTRATION_TOTAL_STAGES, totalStages);
        }

        Span invSpan = spanBuilder.startSpan();
        return new AgentInvocation(invSpan, Context.current().with(span), tracer, agentName);
    }
}
