# AgentTel

Agent-ready telemetry for Java applications. AgentTel enriches your OpenTelemetry spans with the context AI agents need to autonomously diagnose and resolve production incidents.

## What It Does

AgentTel turns standard OTel spans into **agent-actionable signals** by adding:

- **Topology context** — team ownership, service tier, dependencies, on-call channels
- **Baselines** — static and rolling latency/error baselines per operation
- **Decision metadata** — retryability, idempotency, fallbacks, runbook URLs, escalation levels
- **Anomaly detection** — z-score based detection with pattern matching (cascade failures, latency degradation)
- **SLO tracking** — error budget consumption, burn rate, threshold alerts
- **GenAI instrumentation** — spans for LangChain4j, Spring AI, Anthropic/OpenAI/Bedrock SDKs with cost tracking

## Quick Start

### 1. Add the dependency

```gradle
// build.gradle.kts
dependencies {
    implementation("io.agenttel:agenttel-spring-boot-starter:0.1.0-alpha")

    // Optional: GenAI instrumentation
    implementation("io.agenttel:agenttel-genai:0.1.0-alpha")
}
```

### 2. Configure in application.yml

```yaml
agenttel:
  topology:
    team: payments-platform
    tier: critical
    domain: commerce
    on-call-channel: "#payments-oncall"
  dependencies:
    - name: postgres
      type: database
      criticality: required
      timeout-ms: 5000
      circuit-breaker: true
    - name: stripe-api
      type: rest_api
      criticality: required
      fallback: "Return cached pricing"
  anomaly-detection:
    z-score-threshold: 3.0
  baselines:
    rolling-window-size: 1000
    rolling-min-samples: 10
```

### 3. Annotate your operations

```java
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @AgentOperation(
        expectedLatencyP50 = "45ms",
        expectedLatencyP99 = "200ms",
        expectedErrorRate = 0.001,
        retryable = true,
        idempotent = true,
        runbookUrl = "https://wiki/runbooks/process-payment",
        fallbackDescription = "Return cached pricing",
        escalationLevel = EscalationLevel.PAGE_ONCALL
    )
    @PostMapping
    public ResponseEntity<PaymentResult> processPayment(@RequestBody PaymentRequest request) {
        // Your business logic
    }
}
```

### 4. What you get

Every span is automatically enriched with attributes like:

```
agenttel.topology.team = "payments-platform"
agenttel.topology.tier = "critical"
agenttel.baseline.latency_p50_ms = 45.0
agenttel.baseline.latency_p99_ms = 200.0
agenttel.baseline.source = "static"
agenttel.decision.retryable = true
agenttel.decision.runbook_url = "https://wiki/runbooks/process-payment"
agenttel.decision.escalation_level = "page_oncall"
```

## Modules

| Module | Description |
|--------|-------------|
| `agenttel-api` | Annotations, attribute constants, data models |
| `agenttel-core` | Runtime engine — span enrichment, baselines, anomaly detection, SLO tracking |
| `agenttel-genai` | GenAI instrumentation for LLM frameworks and provider SDKs |
| `agenttel-spring-boot-starter` | Spring Boot auto-configuration |
| `agenttel-testing` | Test utilities |

## GenAI Instrumentation

### LangChain4j

Wrap your models to get full tracing:

```java
ChatLanguageModel model = OpenAiChatModel.builder()
    .apiKey("...")
    .modelName("gpt-4o")
    .build();

// Wrap with tracing
ChatLanguageModel traced = LangChain4jInstrumentation.instrument(model, openTelemetry, "gpt-4o", "openai");

// Use as normal — spans are created automatically
traced.chat(ChatRequest.builder().messages(messages).build());
```

Span output:
```
span.name = "chat gpt-4o"
gen_ai.operation.name = "chat"
gen_ai.system = "openai"
gen_ai.usage.input_tokens = 150
gen_ai.usage.output_tokens = 42
agenttel.genai.framework = "langchain4j"
agenttel.genai.cost_usd = 0.000795
```

### Spring AI

Spring AI already emits `gen_ai.*` spans. AgentTel enriches them:

```java
// Auto-configured — just add agenttel-genai to classpath
// Spans get agenttel.genai.framework = "spring_ai"

// For cost calculation, wrap your SpanExporter:
SpanExporter exporter = new CostEnrichingSpanExporter(yourExporter);
```

### Provider SDKs

Direct instrumentation for Anthropic, OpenAI, and AWS Bedrock:

```java
// Anthropic
AnthropicClient traced = new TracingAnthropicClient(client, openTelemetry);

// OpenAI
OpenAIClient traced = new TracingOpenAIClient(client, openTelemetry);

// Bedrock
ConverseResponse response = BedrockTracing.tracedConverse(bedrockClient, request, openTelemetry);
```

## Rolling Baselines

AgentTel automatically builds baselines from observed traffic:

```java
// Configured via properties
agenttel.baselines.rolling-window-size=1000
agenttel.baselines.rolling-min-samples=10

// Or programmatically
RollingBaselineProvider rolling = new RollingBaselineProvider(1000, 10);

// Composite: static baselines take precedence, rolling fills gaps
CompositeBaselineProvider composite = new CompositeBaselineProvider(staticProvider, rolling);
```

The rolling baseline provider computes P50/P95/P99/mean/stddev from a sliding window ring buffer and feeds into anomaly detection.

## SLO Tracking

Define SLOs and track error budget consumption:

```java
SloTracker tracker = new SloTracker();
tracker.register(SloDefinition.builder("payment-availability")
    .operationName("POST /api/payments")
    .type(SloDefinition.SloType.AVAILABILITY)
    .target(0.999)  // 99.9%
    .build());

// Automatically tracked from spans via AgentTelSpanProcessor
// Check status:
SloTracker.SloStatus status = tracker.getStatus("payment-availability");
// status.budgetRemaining() -> 0.85 (85% budget remaining)
// status.burnRate() -> 0.15

// Get alerts at 50%/25%/10% thresholds
List<SloTracker.SloAlert> alerts = tracker.checkAlerts();
```

## Anomaly Detection

Z-score based detection with pattern matching:

```java
// Automatic via AgentTelSpanProcessor — detects:
// - LATENCY_DEGRADATION: sustained latency above 2x P50
// - ERROR_RATE_SPIKE: error rate above 5x baseline
// - CASCADE_FAILURE: 3+ dependencies failing simultaneously
// - MEMORY_LEAK: monotonically increasing latency trend
```

## Building

```bash
./gradlew clean build
```

Requires JDK 17+. Tested with JDK 17 and 21.

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  Your Application                │
│  @AgentOperation annotations + business logic    │
├─────────────────────────────────────────────────┤
│           agenttel-spring-boot-starter           │
│  Auto-configuration, BeanPostProcessor, AOP      │
├──────────────────────┬──────────────────────────┤
│    agenttel-core     │     agenttel-genai        │
│  SpanProcessor       │  LangChain4j wrappers     │
│  Baselines (static   │  Spring AI enricher       │
│    + rolling)        │  Anthropic/OpenAI/Bedrock  │
│  Anomaly detection   │  Cost calculator           │
│  SLO tracking        │                            │
│  Pattern matching    │                            │
├──────────────────────┴──────────────────────────┤
│                   agenttel-api                    │
│  @AgentOperation, AgentTelAttributes, data models │
├─────────────────────────────────────────────────┤
│              OpenTelemetry SDK                    │
└─────────────────────────────────────────────────┘
```

## License

Apache License 2.0
