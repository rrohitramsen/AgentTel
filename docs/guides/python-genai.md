# Python GenAI Instrumentation

AgentTel instruments GenAI SDK calls to capture model, token usage, cost, and performance as OTel spans.

## Supported Providers

| Provider | Install Extra | Wrapper |
|----------|--------------|---------|
| OpenAI | `agenttel[openai]` | `instrument_openai(client)` |
| Anthropic | `agenttel[anthropic]` | `instrument_anthropic(client)` |
| LangChain | `agenttel[langchain]` | `instrument_langchain()` |
| AWS Bedrock | `agenttel[bedrock]` | `instrument_bedrock(client)` |

## OpenAI

```python
from openai import OpenAI
from agenttel.genai import instrument_openai

client = instrument_openai(OpenAI())

response = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "Explain SLOs"}],
    temperature=0.7,
)
```

Every `chat.completions.create()` call creates a span with:

| Attribute | Example Value |
|-----------|--------------|
| `gen_ai.operation.name` | `"chat"` |
| `gen_ai.system` | `"openai"` |
| `gen_ai.request.model` | `"gpt-4o"` |
| `gen_ai.request.temperature` | `0.7` |
| `gen_ai.usage.input_tokens` | `42` |
| `gen_ai.usage.output_tokens` | `156` |
| `gen_ai.response.finish_reasons` | `["stop"]` |
| `agenttel.genai.cost_usd` | `0.000265` |

### Streaming

Streaming responses are also instrumented. Token usage is captured from the final chunk:

```python
stream = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "Hello"}],
    stream=True,
)
for chunk in stream:
    print(chunk.choices[0].delta.content or "", end="")
```

## Anthropic

```python
from anthropic import Anthropic
from agenttel.genai import instrument_anthropic

client = instrument_anthropic(Anthropic())

response = client.messages.create(
    model="claude-sonnet-4-20250514",
    max_tokens=1024,
    messages=[{"role": "user", "content": "Explain error budgets"}],
)
```

Captures Anthropic-specific fields: `input_tokens`, `output_tokens`, `cache_read_input_tokens`, `stop_reason`.

## LangChain

```python
from agenttel.genai import instrument_langchain

instrument_langchain()  # Patches globally
```

Instruments via LangChain callbacks:

- **ChatModel** calls → `gen_ai chat` spans
- **Retriever** calls → `gen_ai retrieval` spans with document count

## AWS Bedrock

```python
import boto3
from agenttel.genai import instrument_bedrock

client = instrument_bedrock(boto3.client("bedrock-runtime"))

# Both invoke_model() and converse() are traced
response = client.converse(
    modelId="anthropic.claude-3-5-sonnet-20241022-v2:0",
    messages=[{"role": "user", "content": [{"text": "Hello"}]}],
)
```

## Cost Calculation

AgentTel includes a `ModelCostCalculator` with built-in pricing for popular models:

```python
from agenttel.genai.cost import ModelCostCalculator, ModelPricing

calc = ModelCostCalculator()

# Built-in pricing
cost = calc.calculate("gpt-4o", input_tokens=1000, output_tokens=500)

# Custom pricing
calc.register_pricing("my-model", ModelPricing(
    input_per_1m=2.0,   # $2.00 per 1M input tokens
    output_per_1m=8.0,  # $8.00 per 1M output tokens
))
```

### Built-in Model Pricing

| Model | Input (per 1M) | Output (per 1M) |
|-------|---------------|-----------------|
| gpt-4o | $2.50 | $10.00 |
| gpt-4o-mini | $0.15 | $0.60 |
| gpt-4-turbo | $10.00 | $30.00 |
| claude-sonnet-4 | $3.00 | $15.00 |
| claude-opus-4 | $15.00 | $75.00 |
| claude-3.5-sonnet | $3.00 | $15.00 |
| claude-3-haiku | $0.25 | $1.25 |

## Custom Span Builder

For direct span creation without wrapping a client:

```python
from agenttel.genai.span_builder import GenAiSpanBuilder

builder = GenAiSpanBuilder()

span = builder.start_chat_span(model="gpt-4o", system="openai")
# ... make your API call ...
builder.end_span_with_response(
    span=span,
    model="gpt-4o",
    input_tokens=100,
    output_tokens=50,
    finish_reason="stop",
)
```
