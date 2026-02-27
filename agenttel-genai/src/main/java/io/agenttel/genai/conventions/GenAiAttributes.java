package io.agenttel.genai.conventions;

import io.opentelemetry.api.common.AttributeKey;

import java.util.List;

/**
 * Standard OpenTelemetry GenAI semantic convention attribute keys.
 * Based on the gen_ai.* namespace from OTel semantic conventions (incubating).
 */
public final class GenAiAttributes {
    private GenAiAttributes() {}

    // --- Operation ---
    public static final AttributeKey<String> GEN_AI_OPERATION_NAME =
            AttributeKey.stringKey("gen_ai.operation.name");

    // --- Provider ---
    public static final AttributeKey<String> GEN_AI_SYSTEM =
            AttributeKey.stringKey("gen_ai.system");

    // --- Request ---
    public static final AttributeKey<String> GEN_AI_REQUEST_MODEL =
            AttributeKey.stringKey("gen_ai.request.model");
    public static final AttributeKey<Double> GEN_AI_REQUEST_TEMPERATURE =
            AttributeKey.doubleKey("gen_ai.request.temperature");
    public static final AttributeKey<Long> GEN_AI_REQUEST_MAX_TOKENS =
            AttributeKey.longKey("gen_ai.request.max_tokens");
    public static final AttributeKey<Double> GEN_AI_REQUEST_TOP_P =
            AttributeKey.doubleKey("gen_ai.request.top_p");

    // --- Response ---
    public static final AttributeKey<String> GEN_AI_RESPONSE_MODEL =
            AttributeKey.stringKey("gen_ai.response.model");
    public static final AttributeKey<String> GEN_AI_RESPONSE_ID =
            AttributeKey.stringKey("gen_ai.response.id");
    public static final AttributeKey<List<String>> GEN_AI_RESPONSE_FINISH_REASONS =
            AttributeKey.stringArrayKey("gen_ai.response.finish_reasons");

    // --- Usage ---
    public static final AttributeKey<Long> GEN_AI_USAGE_INPUT_TOKENS =
            AttributeKey.longKey("gen_ai.usage.input_tokens");
    public static final AttributeKey<Long> GEN_AI_USAGE_OUTPUT_TOKENS =
            AttributeKey.longKey("gen_ai.usage.output_tokens");
}
