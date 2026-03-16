"""Tests for GenAI instrumentation."""

from agenttel.genai.cost import ModelCostCalculator, ModelPricing


class TestModelCostCalculator:
    def test_known_model(self):
        calc = ModelCostCalculator()
        cost = calc.calculate("gpt-4o", input_tokens=1000, output_tokens=500)
        assert cost > 0

    def test_unknown_model(self):
        calc = ModelCostCalculator()
        cost = calc.calculate("unknown-model", input_tokens=1000, output_tokens=500)
        assert cost == 0.0

    def test_custom_pricing(self):
        custom = {"my-model": ModelPricing(1.0, 2.0)}
        calc = ModelCostCalculator(custom_pricing=custom)
        cost = calc.calculate("my-model", input_tokens=1_000_000, output_tokens=1_000_000)
        assert cost == 3.0  # 1.0 + 2.0

    def test_prefix_match(self):
        calc = ModelCostCalculator()
        cost = calc.calculate("gpt-4o-2024-08-06", input_tokens=1000, output_tokens=500)
        assert cost > 0  # Should match "gpt-4o"

    def test_anthropic_pricing(self):
        calc = ModelCostCalculator()
        cost = calc.calculate("claude-3.5-sonnet", input_tokens=1_000_000, output_tokens=1_000_000)
        assert cost == 18.0  # 3.0 + 15.0

    def test_register_pricing(self):
        calc = ModelCostCalculator()
        calc.register_pricing("new-model", ModelPricing(5.0, 10.0))
        cost = calc.calculate("new-model", input_tokens=1_000_000, output_tokens=1_000_000)
        assert cost == 15.0


class TestGenAiAttributes:
    def test_attribute_constants(self):
        from agenttel.genai import attributes as genai_attr

        assert genai_attr.SYSTEM == "gen_ai.system"
        assert genai_attr.REQUEST_MODEL == "gen_ai.request.model"
        assert genai_attr.USAGE_INPUT_TOKENS == "gen_ai.usage.input_tokens"
        assert genai_attr.COST_USD == "agenttel.genai.cost_usd"
