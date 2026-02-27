package io.agenttel.genai.conventions;

import io.opentelemetry.api.common.AttributeKey;

/**
 * AgentTel extension attributes for GenAI telemetry.
 * These extend the standard gen_ai.* attributes with agent-ready context.
 */
public final class AgentTelGenAiAttributes {
    private AgentTelGenAiAttributes() {}

    // --- Framework ---
    public static final AttributeKey<String> GENAI_FRAMEWORK =
            AttributeKey.stringKey("agenttel.genai.framework");

    // --- Prompt Template ---
    public static final AttributeKey<String> GENAI_PROMPT_TEMPLATE_ID =
            AttributeKey.stringKey("agenttel.genai.prompt_template_id");
    public static final AttributeKey<String> GENAI_PROMPT_TEMPLATE_VERSION =
            AttributeKey.stringKey("agenttel.genai.prompt_template_version");

    // --- RAG ---
    public static final AttributeKey<Long> GENAI_RAG_SOURCE_COUNT =
            AttributeKey.longKey("agenttel.genai.rag_source_count");
    public static final AttributeKey<Double> GENAI_RAG_RELEVANCE_SCORE_AVG =
            AttributeKey.doubleKey("agenttel.genai.rag_relevance_score_avg");

    // --- Guardrails ---
    public static final AttributeKey<Boolean> GENAI_GUARDRAIL_TRIGGERED =
            AttributeKey.booleanKey("agenttel.genai.guardrail_triggered");
    public static final AttributeKey<String> GENAI_GUARDRAIL_NAME =
            AttributeKey.stringKey("agenttel.genai.guardrail_name");

    // --- Cost ---
    public static final AttributeKey<Double> GENAI_COST_USD =
            AttributeKey.doubleKey("agenttel.genai.cost_usd");

    // --- Cache ---
    public static final AttributeKey<Boolean> GENAI_CACHE_HIT =
            AttributeKey.booleanKey("agenttel.genai.cache_hit");
}
