package io.agenttel.genai.bedrock;

import io.agenttel.genai.conventions.GenAiOperationName;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import java.util.Collections;

/**
 * Utility for instrumenting AWS Bedrock Runtime API calls with OTel tracing.
 *
 * Since BedrockRuntimeClient is a complex AWS SDK interface, we provide
 * a static instrumentation utility rather than a full client wrapper.
 */
public final class BedrockTracing {
    private BedrockTracing() {}

    private static final String INSTRUMENTATION_NAME = "io.agenttel.genai.bedrock";

    /**
     * Execute a Bedrock Converse API call with tracing.
     */
    public static ConverseResponse tracedConverse(BedrockRuntimeClient client,
                                                   ConverseRequest request,
                                                   OpenTelemetry openTelemetry) {
        GenAiSpanBuilder spanBuilder = new GenAiSpanBuilder(
                openTelemetry.getTracer(INSTRUMENTATION_NAME));

        String modelId = request.modelId();
        Span span = spanBuilder.startSpan(GenAiOperationName.CHAT, modelId, "aws_bedrock", null);

        try (Scope scope = span.makeCurrent()) {
            ConverseResponse response = client.converse(request);

            TokenUsage usage = response.usage();
            long inputTokens = usage != null && usage.inputTokens() != null ? usage.inputTokens() : 0;
            long outputTokens = usage != null && usage.outputTokens() != null ? usage.outputTokens() : 0;

            String stopReason = response.stopReasonAsString();

            spanBuilder.endSpanSuccess(span, modelId, null,
                    inputTokens, outputTokens,
                    stopReason != null ? Collections.singletonList(stopReason.toLowerCase()) : null);

            return response;
        } catch (Throwable t) {
            spanBuilder.endSpanError(span, t);
            throw t;
        }
    }
}
