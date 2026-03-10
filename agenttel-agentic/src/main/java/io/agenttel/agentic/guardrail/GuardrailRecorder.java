package io.agenttel.agentic.guardrail;

import io.agenttel.agentic.GuardrailAction;
import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

/**
 * Records guardrail activations as child spans of the current context.
 */
public class GuardrailRecorder {

    private final Tracer tracer;

    public GuardrailRecorder(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Records a guardrail activation.
     */
    public void record(String name, GuardrailAction action, String reason) {
        Span guardrailSpan = tracer.spanBuilder("agenttel.agentic.guardrail")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.GUARDRAIL_TRIGGERED, true)
                .setAttribute(AgenticAttributes.GUARDRAIL_NAME, name)
                .setAttribute(AgenticAttributes.GUARDRAIL_ACTION, action.getValue())
                .setAttribute(AgenticAttributes.GUARDRAIL_REASON, reason)
                .startSpan();
        guardrailSpan.end();
    }

    /**
     * Records a guardrail activation as a child of a specific parent context.
     */
    public void record(String name, GuardrailAction action, String reason, Context parentContext) {
        Span guardrailSpan = tracer.spanBuilder("agenttel.agentic.guardrail")
                .setParent(parentContext)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.GUARDRAIL_TRIGGERED, true)
                .setAttribute(AgenticAttributes.GUARDRAIL_NAME, name)
                .setAttribute(AgenticAttributes.GUARDRAIL_ACTION, action.getValue())
                .setAttribute(AgenticAttributes.GUARDRAIL_REASON, reason)
                .startSpan();
        guardrailSpan.end();
    }
}
