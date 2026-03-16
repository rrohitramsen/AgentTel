"""Model cost calculator for GenAI operations."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class ModelPricing:
    """Per-model pricing in USD per 1M tokens."""

    input_per_1m: float
    output_per_1m: float


# Pricing as of early 2025
_DEFAULT_PRICING: dict[str, ModelPricing] = {
    # OpenAI
    "gpt-4o": ModelPricing(2.50, 10.00),
    "gpt-4o-mini": ModelPricing(0.15, 0.60),
    "gpt-4-turbo": ModelPricing(10.00, 30.00),
    "gpt-4": ModelPricing(30.00, 60.00),
    "gpt-3.5-turbo": ModelPricing(0.50, 1.50),
    "o1": ModelPricing(15.00, 60.00),
    "o1-mini": ModelPricing(3.00, 12.00),
    "o3-mini": ModelPricing(1.10, 4.40),
    # Anthropic
    "claude-opus-4-20250514": ModelPricing(15.00, 75.00),
    "claude-sonnet-4-20250514": ModelPricing(3.00, 15.00),
    "claude-3-5-sonnet-20241022": ModelPricing(3.00, 15.00),
    "claude-3-5-haiku-20241022": ModelPricing(0.80, 4.00),
    "claude-3-opus-20240229": ModelPricing(15.00, 75.00),
    "claude-3-sonnet-20240229": ModelPricing(3.00, 15.00),
    "claude-3-haiku-20240307": ModelPricing(0.25, 1.25),
    # Aliases
    "claude-3.5-sonnet": ModelPricing(3.00, 15.00),
    "claude-3-haiku": ModelPricing(0.25, 1.25),
    "claude-4-sonnet": ModelPricing(3.00, 15.00),
    # AWS Bedrock model IDs
    "anthropic.claude-3-5-sonnet-20241022-v2:0": ModelPricing(3.00, 15.00),
    "anthropic.claude-3-haiku-20240307-v1:0": ModelPricing(0.25, 1.25),
    "amazon.titan-text-express-v1": ModelPricing(0.20, 0.60),
    "amazon.titan-text-lite-v1": ModelPricing(0.15, 0.20),
    "meta.llama3-70b-instruct-v1:0": ModelPricing(2.65, 3.50),
    "meta.llama3-8b-instruct-v1:0": ModelPricing(0.30, 0.60),
}


class ModelCostCalculator:
    """Calculates cost for GenAI model invocations."""

    def __init__(
        self,
        custom_pricing: dict[str, ModelPricing] | None = None,
    ) -> None:
        self._pricing = dict(_DEFAULT_PRICING)
        if custom_pricing:
            self._pricing.update(custom_pricing)

    def calculate(
        self,
        model: str,
        input_tokens: int,
        output_tokens: int,
    ) -> float:
        """Calculate cost in USD for a model invocation.

        Returns 0.0 if model pricing is unknown.
        """
        pricing = self._resolve_pricing(model)
        if pricing is None:
            return 0.0

        input_cost = (input_tokens / 1_000_000) * pricing.input_per_1m
        output_cost = (output_tokens / 1_000_000) * pricing.output_per_1m
        return round(input_cost + output_cost, 8)

    def register_pricing(self, model: str, pricing: ModelPricing) -> None:
        """Register custom pricing for a model."""
        self._pricing[model] = pricing

    def _resolve_pricing(self, model: str) -> ModelPricing | None:
        """Resolve pricing, trying exact match then prefix match."""
        if model in self._pricing:
            return self._pricing[model]

        # Try prefix match (e.g., "gpt-4o-2024-08-06" -> "gpt-4o")
        for known_model, pricing in self._pricing.items():
            if model.startswith(known_model):
                return pricing
        return None
