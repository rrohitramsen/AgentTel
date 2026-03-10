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
 * Orchestration for evaluator-optimizer (generator-critic) loops where
 * output is iteratively generated and evaluated until quality thresholds are met.
 */
public class EvalLoopOrchestration extends Orchestration {

    public EvalLoopOrchestration(Span span, Context parentContext, Tracer tracer, String coordinatorId) {
        super(span, parentContext, tracer, OrchestrationPattern.EVALUATOR_OPTIMIZER, coordinatorId);
    }

    /**
     * Creates a generator invocation for a specific iteration.
     */
    public AgentInvocation generate(String agentName, int iteration) {
        String invocationId = UUID.randomUUID().toString();
        Span invSpan = tracer.spanBuilder("invoke_agent")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.AGENT_NAME, agentName)
                .setAttribute(AgenticAttributes.INVOCATION_ID, invocationId)
                .setAttribute(AgenticAttributes.INVOCATION_GOAL, "Generate iteration " + iteration)
                .setAttribute(AgenticAttributes.AGENT_TYPE, "worker")
                .setAttribute(AgenticAttributes.STEP_ITERATION, (long) iteration)
                .startSpan();
        return new AgentInvocation(invSpan, Context.current().with(span), tracer, agentName);
    }

    /**
     * Creates an evaluator invocation for a specific iteration.
     * The returned invocation supports setting evaluation scores.
     */
    public EvalInvocation evaluate(String agentName, int iteration) {
        String invocationId = UUID.randomUUID().toString();
        Span invSpan = tracer.spanBuilder("agenttel.agentic.evaluate")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.AGENT_NAME, agentName)
                .setAttribute(AgenticAttributes.INVOCATION_ID, invocationId)
                .setAttribute(AgenticAttributes.AGENT_TYPE, "evaluator")
                .setAttribute(AgenticAttributes.STEP_ITERATION, (long) iteration)
                .startSpan();
        return new EvalInvocation(invSpan, Context.current().with(span), tracer, agentName);
    }

    /**
     * Extended invocation that supports eval scoring and feedback.
     */
    public static class EvalInvocation extends AgentInvocation {

        EvalInvocation(Span span, Context parentContext, Tracer tracer, String agentName) {
            super(span, parentContext, tracer, agentName);
        }

        /**
         * Records the evaluation score (0.0–1.0).
         */
        public EvalInvocation score(double score) {
            span().setAttribute(AgenticAttributes.QUALITY_EVAL_SCORE, score);
            return this;
        }

        /**
         * Records evaluator feedback for the generator.
         */
        public EvalInvocation feedback(String feedback) {
            span().addEvent(feedback);
            return this;
        }
    }
}
