package io.agenttel.genai.cost;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ModelCostCalculatorTest {

    @Test
    void calculateCostForClaude35Sonnet() {
        // Claude 3.5 Sonnet: $3/MTok input, $15/MTok output
        double cost = ModelCostCalculator.calculateCost("claude-3-5-sonnet-20241022", 1000, 500);
        // Expected: (1000 * 3.0 / 1_000_000) + (500 * 15.0 / 1_000_000) = 0.003 + 0.0075 = 0.0105
        assertThat(cost).isCloseTo(0.0105, within(0.0001));
    }

    @Test
    void calculateCostForGpt4o() {
        // GPT-4o: $2.50/MTok input, $10.0/MTok output
        double cost = ModelCostCalculator.calculateCost("gpt-4o", 10000, 2000);
        // Expected: (10000 * 2.5 / 1_000_000) + (2000 * 10.0 / 1_000_000) = 0.025 + 0.02 = 0.045
        assertThat(cost).isCloseTo(0.045, within(0.0001));
    }

    @Test
    void calculateCostForEmbeddingModel() {
        // text-embedding-3-small: $0.02/MTok input, $0 output
        double cost = ModelCostCalculator.calculateCost("text-embedding-3-small", 5000, 0);
        // Expected: (5000 * 0.02 / 1_000_000) = 0.0001
        assertThat(cost).isCloseTo(0.0001, within(0.00001));
    }

    @Test
    void calculateCostForUnknownModelReturnsZero() {
        double cost = ModelCostCalculator.calculateCost("unknown-model-xyz", 1000, 500);
        assertThat(cost).isEqualTo(0.0);
    }

    @Test
    void calculateCostForNullModelReturnsZero() {
        double cost = ModelCostCalculator.calculateCost(null, 1000, 500);
        assertThat(cost).isEqualTo(0.0);
    }

    @Test
    void hasPricingReturnsTrueForKnownModels() {
        assertThat(ModelCostCalculator.hasPricing("gpt-4o")).isTrue();
        assertThat(ModelCostCalculator.hasPricing("claude-3-5-sonnet-20241022")).isTrue();
        assertThat(ModelCostCalculator.hasPricing("text-embedding-3-small")).isTrue();
    }

    @Test
    void hasPricingReturnsFalseForUnknownModels() {
        assertThat(ModelCostCalculator.hasPricing("nonexistent-model")).isFalse();
        assertThat(ModelCostCalculator.hasPricing(null)).isFalse();
    }

    @Test
    void customModelCanBeRegistered() {
        ModelCostCalculator.register("my-custom-model", 1.0, 2.0);
        double cost = ModelCostCalculator.calculateCost("my-custom-model", 1_000_000, 1_000_000);
        // Expected: (1_000_000 * 1.0 / 1_000_000) + (1_000_000 * 2.0 / 1_000_000) = 1.0 + 2.0 = 3.0
        assertThat(cost).isCloseTo(3.0, within(0.001));
    }

    @Test
    void zeroTokensResultsInZeroCost() {
        double cost = ModelCostCalculator.calculateCost("gpt-4o", 0, 0);
        assertThat(cost).isEqualTo(0.0);
    }

    @Test
    void bedrockModelPricingExists() {
        assertThat(ModelCostCalculator.hasPricing("amazon.titan-text-express-v1")).isTrue();
        assertThat(ModelCostCalculator.hasPricing("anthropic.claude-3-5-sonnet-20241022-v2:0")).isTrue();
    }
}
