package io.agenttel.genai.trace;

import io.agenttel.genai.conventions.AgentTelGenAiAttributes;
import io.agenttel.genai.conventions.GenAiAttributes;
import io.agenttel.genai.cost.ModelCostCalculator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.List;

/**
 * Utility for creating properly-formed GenAI spans following OTel semantic conventions.
 * Used by all provider instrumentations to ensure consistency.
 */
public class GenAiSpanBuilder {

    private final Tracer tracer;

    public GenAiSpanBuilder(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Start a new GenAI span.
     *
     * @param operationName the GenAI operation (e.g. "chat", "embeddings")
     * @param model         the model name
     * @param system        the GenAI system/provider (e.g. "openai", "anthropic")
     * @param framework     the framework used (e.g. "langchain4j", "spring_ai"), nullable
     * @return the started Span
     */
    public Span startSpan(String operationName, String model, String system, String framework) {
        String spanName = operationName + " " + (model != null ? model : "unknown");

        SpanBuilder builder = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.CLIENT)
                .setParent(Context.current())
                .setAttribute(GenAiAttributes.GEN_AI_OPERATION_NAME, operationName)
                .setAttribute(GenAiAttributes.GEN_AI_SYSTEM, system);

        if (model != null) {
            builder.setAttribute(GenAiAttributes.GEN_AI_REQUEST_MODEL, model);
        }
        if (framework != null) {
            builder.setAttribute(AgentTelGenAiAttributes.GENAI_FRAMEWORK, framework);
        }

        return builder.startSpan();
    }

    /**
     * Enrich a span with response attributes and end it successfully.
     */
    public void endSpanSuccess(Span span, String responseModel, String responseId,
                               long inputTokens, long outputTokens,
                               List<String> finishReasons) {
        if (responseModel != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_RESPONSE_MODEL, responseModel);
        }
        if (responseId != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_RESPONSE_ID, responseId);
        }
        if (inputTokens > 0) {
            span.setAttribute(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS, inputTokens);
        }
        if (outputTokens > 0) {
            span.setAttribute(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, outputTokens);
        }
        if (finishReasons != null && !finishReasons.isEmpty()) {
            span.setAttribute(GenAiAttributes.GEN_AI_RESPONSE_FINISH_REASONS, finishReasons);
        }

        // Calculate cost
        String model = responseModel != null ? responseModel : getRequestModel(span);
        if (model != null && (inputTokens > 0 || outputTokens > 0)) {
            double cost = ModelCostCalculator.calculateCost(model, inputTokens, outputTokens);
            if (cost > 0) {
                span.setAttribute(AgentTelGenAiAttributes.GENAI_COST_USD, cost);
            }
        }

        span.end();
    }

    /**
     * End a span with an error.
     */
    public void endSpanError(Span span, Throwable error) {
        span.setStatus(StatusCode.ERROR, error.getMessage());
        span.recordException(error);
        span.end();
    }

    /**
     * Set optional request parameters on a span.
     */
    public static void setRequestParams(Span span, Double temperature, Long maxTokens, Double topP) {
        if (temperature != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE, temperature);
        }
        if (maxTokens != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_REQUEST_MAX_TOKENS, maxTokens);
        }
        if (topP != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_REQUEST_TOP_P, topP);
        }
    }

    private String getRequestModel(Span span) {
        // Span interface doesn't expose getAttribute directly,
        // so we rely on responseModel being passed in.
        return null;
    }
}
