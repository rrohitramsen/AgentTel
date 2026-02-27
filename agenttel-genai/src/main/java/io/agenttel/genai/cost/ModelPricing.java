package io.agenttel.genai.cost;

/**
 * Pricing information for a model, expressed as cost per million tokens.
 */
public record ModelPricing(double inputCostPerMillionTokens, double outputCostPerMillionTokens) {

    public double calculateCost(long inputTokens, long outputTokens) {
        return (inputTokens * inputCostPerMillionTokens / 1_000_000.0)
                + (outputTokens * outputCostPerMillionTokens / 1_000_000.0);
    }
}
