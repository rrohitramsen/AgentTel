package io.agenttel.genai.cost;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculates estimated cost for LLM API calls based on model and token usage.
 * Returns 0.0 for unknown models (graceful fallback).
 */
public final class ModelCostCalculator {
    private ModelCostCalculator() {}

    private static final Map<String, ModelPricing> PRICING = new ConcurrentHashMap<>();

    static {
        // Claude models (Anthropic)
        register("claude-opus-4-20250514", 15.0, 75.0);
        register("claude-sonnet-4-20250514", 3.0, 15.0);
        register("claude-3-5-sonnet-20241022", 3.0, 15.0);
        register("claude-3-5-haiku-20241022", 0.80, 4.0);
        register("claude-3-opus-20240229", 15.0, 75.0);
        register("claude-3-sonnet-20240229", 3.0, 15.0);
        register("claude-3-haiku-20240307", 0.25, 1.25);

        // GPT models (OpenAI)
        register("gpt-4o", 2.50, 10.0);
        register("gpt-4o-2024-11-20", 2.50, 10.0);
        register("gpt-4o-mini", 0.15, 0.60);
        register("gpt-4-turbo", 10.0, 30.0);
        register("gpt-4", 30.0, 60.0);
        register("gpt-3.5-turbo", 0.50, 1.50);
        register("o1", 15.0, 60.0);
        register("o1-mini", 3.0, 12.0);

        // Embedding models
        register("text-embedding-3-small", 0.02, 0.0);
        register("text-embedding-3-large", 0.13, 0.0);
        register("text-embedding-ada-002", 0.10, 0.0);

        // Bedrock / Amazon models
        register("amazon.titan-text-express-v1", 0.20, 0.60);
        register("amazon.titan-text-lite-v1", 0.15, 0.20);
        register("amazon.titan-embed-text-v1", 0.10, 0.0);
        register("amazon.titan-embed-text-v2:0", 0.02, 0.0);

        // Bedrock cross-region model IDs (Claude on Bedrock)
        register("anthropic.claude-3-5-sonnet-20241022-v2:0", 3.0, 15.0);
        register("anthropic.claude-3-haiku-20240307-v1:0", 0.25, 1.25);
        register("anthropic.claude-3-opus-20240229-v1:0", 15.0, 75.0);
    }

    /**
     * Register custom model pricing.
     */
    public static void register(String model, double inputCostPerMillionTokens, double outputCostPerMillionTokens) {
        PRICING.put(model, new ModelPricing(inputCostPerMillionTokens, outputCostPerMillionTokens));
    }

    /**
     * Calculate the estimated cost in USD for a model invocation.
     *
     * @param model       the model identifier
     * @param inputTokens number of input/prompt tokens
     * @param outputTokens number of output/completion tokens
     * @return estimated cost in USD, or 0.0 if model pricing is unknown
     */
    public static double calculateCost(String model, long inputTokens, long outputTokens) {
        if (model == null) {
            return 0.0;
        }
        ModelPricing pricing = PRICING.get(model);
        if (pricing == null) {
            // Try prefix matching for versioned model names
            pricing = findByPrefix(model);
        }
        if (pricing == null) {
            return 0.0;
        }
        return pricing.calculateCost(inputTokens, outputTokens);
    }

    /**
     * Check if pricing is available for a given model.
     */
    public static boolean hasPricing(String model) {
        if (model == null) return false;
        return PRICING.containsKey(model) || findByPrefix(model) != null;
    }

    private static ModelPricing findByPrefix(String model) {
        for (Map.Entry<String, ModelPricing> entry : PRICING.entrySet()) {
            if (model.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
