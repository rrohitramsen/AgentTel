"""GenAI instrumentation for AgentTel."""

from agenttel.genai.cost import ModelCostCalculator
from agenttel.genai.span_builder import GenAiSpanBuilder

__all__ = [
    "GenAiSpanBuilder",
    "ModelCostCalculator",
]


def instrument_openai(client=None):
    """Instrument an OpenAI client for tracing."""
    from agenttel.genai.openai import instrument_openai as _instrument
    return _instrument(client)


def instrument_anthropic(client=None):
    """Instrument an Anthropic client for tracing."""
    from agenttel.genai.anthropic import instrument_anthropic as _instrument
    return _instrument(client)


def instrument_langchain():
    """Instrument LangChain globally for tracing."""
    from agenttel.genai.langchain import instrument_langchain as _instrument
    return _instrument()


def instrument_bedrock(client=None):
    """Instrument a Bedrock Runtime client for tracing."""
    from agenttel.genai.bedrock import instrument_bedrock as _instrument
    return _instrument(client)
