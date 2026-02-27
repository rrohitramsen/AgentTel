package io.agenttel.genai.springai;

import io.agenttel.genai.conventions.AgentTelGenAiAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

/**
 * SpanProcessor that enriches Spring AI's existing Micrometer-generated spans
 * with AgentTel GenAI attributes.
 *
 * <p>Spring AI already emits spans with gen_ai.* attributes via Micrometer observations.
 * This processor detects those spans and adds the framework tag in onStart.
 *
 * <p>For cost calculation, use {@link CostEnrichingSpanExporter} which wraps
 * the SpanExporter and can add cost attributes to the exported span data.
 */
public class SpringAiSpanEnricher implements SpanProcessor {

    private static final String SPRING_AI_SPAN_PREFIX = "gen_ai";

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        if (isSpringAiSpan(span.getName())) {
            span.setAttribute(AgentTelGenAiAttributes.GENAI_FRAMEWORK, "spring_ai");
        }
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        // Cost calculation moved to CostEnrichingSpanExporter
        // ReadableSpan is immutable in onEnd - cannot add attributes here
    }

    @Override
    public boolean isEndRequired() {
        return false;
    }

    private boolean isSpringAiSpan(String spanName) {
        return spanName != null && spanName.startsWith(SPRING_AI_SPAN_PREFIX);
    }
}
