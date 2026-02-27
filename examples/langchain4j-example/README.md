# AgentTel LangChain4j Example

A demo showing AgentTel GenAI tracing with LangChain4j. Uses a mock chat model to demonstrate span creation with `gen_ai.*` attributes and cost calculation.

## Quick Start

```bash
# From the repository root:
./gradlew :examples:langchain4j-example:run
```

## What You'll See

Console output showing OpenTelemetry spans with:

```
gen_ai.operation.name     = "chat"
gen_ai.system             = "openai"
gen_ai.request.model      = "gpt-4o"
gen_ai.usage.input_tokens = 50
gen_ai.usage.output_tokens = 30
agenttel.genai.framework  = "langchain4j"
agenttel.genai.cost_usd   = 0.000700
```

## Using a Real Model

Replace `MockChatModel` with your actual LangChain4j model:

```java
ChatLanguageModel model = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-4o")
    .build();

ChatLanguageModel traced = LangChain4jInstrumentation.instrument(
    model, openTelemetry, "gpt-4o", "openai");
```
