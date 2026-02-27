# GenAI Instrumentation

The `agenttel-genai` module provides observability for AI/ML workloads on the JVM. It instruments LLM frameworks and provider SDKs with OpenTelemetry spans following the emerging `gen_ai.*` semantic conventions, enriched with AgentTel extensions for cost tracking, framework identification, and RAG observability.

---

## Overview

| Framework | Instrumentation Approach | What You Get |
|-----------|-------------------------|-------------|
| **Spring AI** | `SpanProcessor` enrichment of existing Micrometer spans | Framework tag, cost calculation |
| **LangChain4j** | Decorator-based full instrumentation | Chat, embeddings, RAG retrieval spans |
| **Anthropic Java SDK** | Client wrapper | Messages API with token/cost tracking |
| **OpenAI Java SDK** | Client wrapper | Chat completions with token/cost tracking |
| **AWS Bedrock SDK** | Client wrapper | Converse API with token/cost tracking |

All GenAI library dependencies are `compileOnly` — they activate only when the corresponding library is on the classpath. Users provide their own runtime versions.

---

## Dependency Setup

**Maven:**

```xml
<dependencies>
    <dependency>
        <groupId>io.agenttel</groupId>
        <artifactId>agenttel-genai</artifactId>
        <version>0.1.0-alpha</version>
    </dependency>

    <!-- Include whichever GenAI libraries you use: -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-core</artifactId>
        <version>1.0.0</version>
    </dependency>
    <!-- or -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-core</artifactId>
        <version>1.0.0</version>
    </dependency>
    <!-- or -->
    <dependency>
        <groupId>com.anthropic</groupId>
        <artifactId>anthropic-java</artifactId>
        <version>2.0.0</version>
    </dependency>
    <dependency>
        <groupId>com.openai</groupId>
        <artifactId>openai-java</artifactId>
        <version>4.0.0</version>
    </dependency>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>bedrockruntime</artifactId>
        <version>2.30.0</version>
    </dependency>
</dependencies>
```

**Gradle:**

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.agenttel:agenttel-genai:0.1.0-alpha")

    // Include whichever GenAI libraries you use:
    implementation("dev.langchain4j:langchain4j-core:1.0.0")
    // or
    implementation("org.springframework.ai:spring-ai-core:1.0.0")
    // or
    implementation("com.anthropic:anthropic-java:2.0.0")
    implementation("com.openai:openai-java:4.0.0")
    implementation("software.amazon.awssdk:bedrockruntime:2.30.0")
}
```

---

## LangChain4j Instrumentation

LangChain4j has no built-in OTel tracing. AgentTel provides full instrumentation via the decorator pattern.

### Chat Model

```java
import io.agenttel.genai.langchain4j.LangChain4jInstrumentation;

ChatLanguageModel model = OpenAiChatModel.builder()
    .apiKey("...")
    .modelName("gpt-4o")
    .build();

// Wrap with tracing — every call creates a span
ChatLanguageModel traced = LangChain4jInstrumentation.instrument(
    model, openTelemetry, "gpt-4o", "openai"
);

// Use as normal
ChatResponse response = traced.chat(ChatRequest.builder()
    .messages(List.of(UserMessage.from("Explain observability")))
    .build());
```

**Span output:**

```
Span: "chat gpt-4o"
  gen_ai.operation.name     = "chat"
  gen_ai.system             = "openai"
  gen_ai.request.model      = "gpt-4o"
  gen_ai.usage.input_tokens = 150
  gen_ai.usage.output_tokens = 42
  gen_ai.response.finish_reasons = ["stop"]
  agenttel.genai.framework  = "langchain4j"
  agenttel.genai.cost_usd   = 0.000795
```

### Embedding Model

```java
EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
    .apiKey("...")
    .modelName("text-embedding-3-small")
    .build();

EmbeddingModel traced = LangChain4jInstrumentation.instrumentEmbedding(
    embeddingModel, openTelemetry, "text-embedding-3-small", "openai"
);

// Span: "embeddings text-embedding-3-small"
Response<List<Embedding>> embeddings = traced.embedAll(
    List.of(TextSegment.from("Agent-ready telemetry"))
);
```

### Streaming Chat Model

```java
StreamingChatLanguageModel streaming = OpenAiStreamingChatModel.builder()
    .apiKey("...")
    .modelName("gpt-4o")
    .build();

StreamingChatLanguageModel traced = LangChain4jInstrumentation.instrumentStreaming(
    streaming, openTelemetry, "gpt-4o", "openai"
);

// Span starts on call, ends when streaming completes or errors
traced.chat(ChatRequest.builder()
    .messages(messages)
    .build(), handler);
```

### RAG Content Retrieval

```java
ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
    .embeddingStore(store)
    .embeddingModel(embeddingModel)
    .build();

ContentRetriever traced = LangChain4jInstrumentation.instrumentRetriever(
    retriever, openTelemetry
);

// Span includes RAG-specific attributes
List<Content> results = traced.retrieve(Query.from("What is AgentTel?"));
```

**RAG span attributes:**

```
Span: "retrieve"
  gen_ai.operation.name              = "retrieve"
  agenttel.genai.framework           = "langchain4j"
  agenttel.genai.rag_source_count    = 5
  agenttel.genai.rag_relevance_score_avg = 0.87
```

---

## Spring AI Enrichment

Spring AI already emits `gen_ai.*` spans via Micrometer. AgentTel **enriches** these existing spans rather than replacing them.

### SpanProcessor Enrichment

`SpringAiSpanEnricher` is a `SpanProcessor` that detects Spring AI spans and adds AgentTel attributes:

```java
// Auto-configured via Spring Boot — no code needed
// Adds to every Spring AI span:
//   agenttel.genai.framework = "spring_ai"
```

### Cost Calculation

Since token counts are only available after the model responds, cost is computed at export time using a delegating `SpanExporter`:

```java
// Wraps your existing exporter
SpanExporter costAware = new CostEnrichingSpanExporter(yourOtlpExporter);

// Adds agenttel.genai.cost_usd to spans that have:
//   gen_ai.request.model
//   gen_ai.usage.input_tokens
//   gen_ai.usage.output_tokens
```

**Why a SpanExporter?** In OpenTelemetry's `SpanProcessor.onEnd()`, the span is `ReadableSpan` (immutable). Token usage attributes are set by Spring AI during span execution. The `CostEnrichingSpanExporter` wraps `SpanData` with a delegate that injects the cost attribute at export time — the only point where the data can be modified.

---

## Provider SDK Instrumentation

### Anthropic Java SDK

```java
import io.agenttel.genai.anthropic.TracingAnthropicClient;

AnthropicClient client = AnthropicOkHttpClient.builder()
    .apiKey("...")
    .build();

// Wrap with tracing
AnthropicClient traced = new TracingAnthropicClient(client, openTelemetry);

// Spans created for every messages.create() call
MessageCreateParams params = MessageCreateParams.builder()
    .model("claude-sonnet-4-20250514")
    .maxTokens(1024)
    .messages(List.of(...))
    .build();

Message response = traced.messages().create(params);
```

**Span attributes:**

```
Span: "chat claude-sonnet-4-20250514"
  gen_ai.system             = "anthropic"
  gen_ai.request.model      = "claude-sonnet-4-20250514"
  gen_ai.usage.input_tokens = 200
  gen_ai.usage.output_tokens = 150
  agenttel.genai.cost_usd   = 0.00165
```

### OpenAI Java SDK

```java
import io.agenttel.genai.openai.TracingOpenAIClient;

OpenAIClient client = OpenAIOkHttpClient.builder()
    .apiKey("...")
    .build();

OpenAIClient traced = new TracingOpenAIClient(client, openTelemetry);

ChatCompletion completion = traced.chat().completions().create(params);
```

### AWS Bedrock SDK

```java
import io.agenttel.genai.bedrock.TracingBedrockRuntimeClient;

BedrockRuntimeClient client = BedrockRuntimeClient.builder()
    .region(Region.US_EAST_1)
    .build();

BedrockRuntimeClient traced = new TracingBedrockRuntimeClient(client, openTelemetry);

ConverseResponse response = traced.converse(ConverseRequest.builder()
    .modelId("anthropic.claude-3-sonnet-20240229-v1:0")
    .messages(...)
    .build());
```

---

## Cost Calculation

`ModelCostCalculator` computes estimated costs based on model and token counts.

### Supported Models

| Provider | Models | Input Cost (per 1M tokens) | Output Cost (per 1M tokens) |
|----------|--------|---------------------------|----------------------------|
| Anthropic | Claude Opus 4 | $15.00 | $75.00 |
| Anthropic | Claude Sonnet 4 | $3.00 | $15.00 |
| Anthropic | Claude Haiku 3.5 | $0.80 | $4.00 |
| OpenAI | GPT-4o | $5.00 | $15.00 |
| OpenAI | GPT-4o mini | $0.15 | $0.60 |
| OpenAI | GPT-4 Turbo | $10.00 | $30.00 |
| OpenAI | text-embedding-3-small | $0.02 | — |
| AWS Bedrock | Claude models (via Bedrock) | Same as Anthropic | Same as Anthropic |

### Programmatic Usage

```java
double cost = ModelCostCalculator.calculateCost("gpt-4o", 1000, 500);
// cost = 0.0125 (USD)

// Returns 0.0 for unknown models (graceful fallback)
double unknown = ModelCostCalculator.calculateCost("custom-model", 1000, 500);
// unknown = 0.0
```

---

## Auto-Configuration

When using Spring Boot, GenAI instrumentation is auto-configured based on classpath detection:

| Condition | Configuration Class | What It Does |
|-----------|-------------------|-------------|
| `ChatLanguageModel` on classpath | `LangChain4jGenAiAutoConfiguration` | Wraps LangChain4j model beans with tracing decorators |
| `ChatModel` on classpath | `SpringAiGenAiAutoConfiguration` | Registers `SpringAiSpanEnricher` as SpanProcessor |
| `AnthropicClient` on classpath | `AnthropicGenAiAutoConfiguration` | Wraps Anthropic client beans |
| `OpenAIClient` on classpath | `OpenAiGenAiAutoConfiguration` | Wraps OpenAI client beans |
| `BedrockRuntimeClient` on classpath | `BedrockGenAiAutoConfiguration` | Wraps Bedrock client beans |

Auto-configuration classes are registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

---

## Span Naming Convention

All GenAI spans follow the pattern: `"{operation} {model}"`.

| Operation | Span Name Example |
|-----------|------------------|
| Chat completion | `"chat gpt-4o"` |
| Text completion | `"text_completion gpt-3.5-turbo"` |
| Embedding | `"embeddings text-embedding-3-small"` |
| RAG retrieval | `"retrieve"` |

This follows the emerging OTel GenAI semantic conventions for span naming.
