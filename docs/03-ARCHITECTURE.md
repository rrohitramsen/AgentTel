# AgentTel — Architecture & Module Design

> **Technical architecture for the AgentTel JVM library.**

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Your JVM Application                         │
│                                                                     │
│  ┌──────────────┐  ┌───────────────────┐  ┌─────────────────────┐  │
│  │ @AgentTel    │  │ Auto-Instrumented │  │ Manual AgentTel     │  │
│  │ Annotations  │  │ Libraries         │  │ API Calls           │  │
│  │              │  │ (Spring AI, etc.) │  │                     │  │
│  └──────┬───────┘  └────────┬──────────┘  └──────────┬──────────┘  │
│         │                   │                        │             │
│         └───────────────────┼────────────────────────┘             │
│                             ▼                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    AgentTel Core Engine                       │  │
│  │                                                              │  │
│  │  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐  │  │
│  │  │ Topology    │  │ Baseline     │  │ Anomaly Detection  │  │  │
│  │  │ Registry    │  │ Manager      │  │ (lightweight)      │  │  │
│  │  └─────────────┘  └──────────────┘  └────────────────────┘  │  │
│  │  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐  │  │
│  │  │ Causality   │  │ Decision     │  │ Event Emitter      │  │  │
│  │  │ Tracker     │  │ Enricher     │  │                    │  │  │
│  │  └─────────────┘  └──────────────┘  └────────────────────┘  │  │
│  └──────────────────────────┬───────────────────────────────────┘  │
│                             ▼                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │              OpenTelemetry Java SDK                           │  │
│  │         (Traces, Metrics, Logs — standard OTLP)              │  │
│  └──────────────────────────┬───────────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────────────┘
                              ▼
                   ┌─────────────────────┐
                   │  OTel Collector /   │
                   │  Any OTLP Backend   │
                   └─────────────────────┘
```

---

## 2. Module Structure

The project is organized as a multi-module Gradle build (Kotlin DSL):

```
agenttel/
├── build.gradle.kts                         # Root build
├── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml                   # Version catalog
│
├── agenttel-api/                            # Module 1: Core API
│   └── src/main/java/io/agenttel/api/
│       ├── annotations/                     # @AgentObservable, @DeclareDependency, etc.
│       ├── attributes/                      # AgentTelAttributes constants
│       ├── events/                          # Structured event builders
│       ├── baseline/                        # Baseline interfaces
│       ├── topology/                        # Topology model
│       └── decision/                        # Decision metadata model
│
├── agenttel-core/                           # Module 2: Core Engine
│   └── src/main/java/io/agenttel/core/
│       ├── engine/                          # AgentTelEngine (main orchestrator)
│       ├── topology/                        # TopologyRegistry implementation
│       ├── baseline/                        # BaselineManager (static + rolling)
│       ├── anomaly/                         # Lightweight anomaly detection
│       ├── causality/                       # CausalityTracker
│       ├── enrichment/                      # Span/metric enrichment processors
│       └── events/                          # Structured event emission
│
├── agenttel-spring-boot-starter/            # Module 3: Spring Boot Auto-Config
│   └── src/main/java/io/agenttel/spring/
│       ├── autoconfigure/                   # Auto-configuration classes
│       ├── annotations/                     # Spring-specific annotation processors
│       └── actuator/                        # Actuator endpoint for topology export
│
├── agenttel-genai/                          # Module 4: GenAI Instrumentation
│   └── src/main/java/io/agenttel/genai/
│       ├── conventions/                     # gen_ai.* + agenttel.genai.* constants
│       ├── springai/                        # Spring AI auto-instrumentation
│       ├── langchain4j/                     # LangChain4j auto-instrumentation
│       ├── bedrock/                         # AWS Bedrock SDK instrumentation
│       ├── anthropic/                       # Anthropic Java SDK instrumentation
│       └── openai/                          # OpenAI Java SDK instrumentation
│
├── agenttel-micrometer-bridge/              # Module 5: Micrometer Bridge
│   └── src/main/java/io/agenttel/micrometer/
│       └── AgentTelMicrometerBridge.java    # Bridge for Micrometer-based apps
│
├── agenttel-testing/                        # Module 6: Test Utilities
│   └── src/main/java/io/agenttel/testing/
│       ├── AgentTelAssertions.java          # Assertion helpers
│       └── InMemoryAgentTelCollector.java   # In-memory collector for tests
│
└── examples/
    ├── spring-boot-example/                 # Example Spring Boot app
    ├── spring-ai-example/                   # Example with Spring AI + GenAI instrumentation
    └── quarkus-example/                     # Example Quarkus app
```

---

## 3. Module Details

### 3.1 `agenttel-api` — Core API (Zero Dependencies)

**Purpose:** Annotation definitions and attribute constants. This module has **zero dependencies** beyond the JDK so it can be used anywhere.

**Key Classes:**

```java
// Annotations
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentObservable {
    String service() default "";
    String team() default "";
    ServiceTier tier() default ServiceTier.STANDARD;
    String domain() default "";
    String onCallChannel() default "";
}

@Repeatable(DeclareDependencies.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeclareDependency {
    String name();
    DependencyType type();
    DependencyCriticality criticality() default DependencyCriticality.REQUIRED;
    String protocol() default "";
    int timeoutMs() default 0;
    boolean circuitBreaker() default false;
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentOperation {
    String expectedLatencyP99() default "";
    String expectedLatencyP50() default "";
    double expectedErrorRate() default -1.0;
    boolean retryable() default false;
    boolean idempotent() default false;
    String runbookUrl() default "";
    String fallbackDescription() default "";
    EscalationLevel escalationLevel() default EscalationLevel.NOTIFY_TEAM;
}

// Attribute constants (mirrors OTel SemanticAttributes pattern)
public final class AgentTelAttributes {
    // Topology
    public static final AttributeKey<String> TOPOLOGY_TEAM =
        AttributeKey.stringKey("agenttel.topology.team");
    public static final AttributeKey<String> TOPOLOGY_TIER =
        AttributeKey.stringKey("agenttel.topology.tier");
    // ... all attributes from the semantic conventions
}
```

### 3.2 `agenttel-core` — Core Engine

**Purpose:** The runtime that reads annotations, manages baselines, detects anomalies, and enriches OTel signals.

**Key Components:**

```java
// Main engine — initialized once per application
public class AgentTelEngine {
    private final TopologyRegistry topology;
    private final BaselineManager baselines;
    private final AnomalyDetector anomalyDetector;
    private final CausalityTracker causality;
    private final AgentTelEventEmitter events;
    private final OpenTelemetry otel;

    // Enriches a span with agent-ready attributes
    public void enrichSpan(Span span, AgentOperationContext context) { ... }

    // Emits a structured agent-ready event
    public void emitAnomalyEvent(AnomalyContext context) { ... }

    // Registers dependency state change
    public void reportDependencyStateChange(String dependency,
                                             DependencyState newState,
                                             String evidence) { ... }
}
```

**Baseline Manager:**

```java
public interface BaselineProvider {
    Optional<OperationBaseline> getBaseline(String operationName);
}

// Static baselines from annotations
public class AnnotationBaselineProvider implements BaselineProvider { ... }

// Rolling baselines computed from recent data (optional)
public class RollingBaselineProvider implements BaselineProvider {
    // Maintains a lightweight sliding window (e.g., P50/P99 over last 7 days)
    // Uses OTel metrics internally — no external dependency
}

// Composite: check static first, fall back to rolling
public class CompositeBaselineProvider implements BaselineProvider { ... }
```

**Lightweight Anomaly Detection:**

```java
// NOT a full ML system — just simple statistical detection
public class AnomalyDetector {
    // Z-score based detection against baselines
    public AnomalyResult evaluate(String metric, double currentValue,
                                   OperationBaseline baseline) {
        double zScore = (currentValue - baseline.mean()) / baseline.stddev();
        double anomalyScore = Math.min(1.0, Math.abs(zScore) / 4.0);
        return new AnomalyResult(anomalyScore, zScore > 3.0);
    }
}
```

### 3.3 `agenttel-spring-boot-starter` — Spring Boot Integration

**Purpose:** Auto-configuration that wires everything up with zero code for Spring Boot apps.

```java
@AutoConfiguration
@ConditionalOnClass(OpenTelemetry.class)
@EnableConfigurationProperties(AgentTelProperties.class)
public class AgentTelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentTelEngine agentTelEngine(OpenTelemetry otel,
                                          AgentTelProperties props) {
        return AgentTelEngine.builder()
            .openTelemetry(otel)
            .baselineProvider(new CompositeBaselineProvider(...))
            .anomalyDetectionEnabled(props.isAnomalyDetectionEnabled())
            .build();
    }

    @Bean
    public AgentTelSpanProcessor agentTelSpanProcessor(AgentTelEngine engine) {
        return new AgentTelSpanProcessor(engine);
    }
}
```

**Configuration via `application.yml`:**

```yaml
agenttel:
  enabled: true
  topology:
    team: "payments-platform"
    tier: critical
    domain: "commerce"
    on-call-channel: "#payments-oncall"
  baselines:
    source: static            # static | rolling | composite
    rolling-window: 7d
  anomaly-detection:
    enabled: true
    z-score-threshold: 3.0
  dependencies:
    - name: payment-gateway
      type: external_api
      criticality: required
      timeout-ms: 5000
      circuit-breaker: true
    - name: order-service
      type: internal_service
      criticality: required
  consumers:
    - name: notification-service
      pattern: async
```

### 3.4 `agenttel-genai` — GenAI Instrumentation Enrichment

**Purpose:** Enriches existing JVM GenAI observability with agent-ready context, and fills instrumentation gaps for SDKs that have no tracing today.

**Relationship to existing work:**
- **Spring AI** already emits `gen_ai.client.operation` observations via Micrometer. AgentTel does **not** replace this — it enriches those spans with `agenttel.genai.*` attributes (cost, prompt template tracking, RAG quality, guardrails) via a `SpanProcessor`.
- **`otel-genai-bridges`** (community project) provides SNAPSHOT-quality LangChain4j instrumentation. AgentTel can coexist or provide a more robust alternative.
- **Anthropic Java SDK, OpenAI Java SDK, AWS Bedrock SDK** have no GenAI tracing — AgentTel provides full instrumentation for these.

**Auto-instrumentation targets:**

| Library | Approach |
|---------|----------|
| **Spring AI** | `SpanProcessor` that enriches existing Micrometer-generated spans with `agenttel.genai.*` attributes |
| **LangChain4j** | `ChatLanguageModel`, `EmbeddingModel`, `ContentRetriever` wrapping (full instrumentation) |
| **AWS Bedrock SDK** | `BedrockRuntimeClient` interceptor (full instrumentation) |
| **Anthropic Java SDK** | `AnthropicClient` wrapper (full instrumentation) |
| **OpenAI Java SDK** | `OpenAIClient` wrapper (full instrumentation) |

**What gets captured (per OTel GenAI semconv + AgentTel extensions):**

```java
// Standard gen_ai.* attributes (OTel semconv)
gen_ai.operation.name = "chat"
gen_ai.provider.name = "anthropic"
gen_ai.request.model = "claude-sonnet-4-5-20250929"
gen_ai.usage.input_tokens = 1523
gen_ai.usage.output_tokens = 847
gen_ai.response.finish_reason = "stop"

// AgentTel extensions
agenttel.genai.framework = "spring_ai"
agenttel.genai.cost_usd = 0.0089
agenttel.genai.prompt_template_id = "customer-support-v3"
agenttel.genai.rag_source_count = 5
agenttel.genai.rag_relevance_score_avg = 0.82
agenttel.genai.cache_hit = false

// Plus standard AgentTel enrichments
agenttel.baseline.latency_p99_ms = 3000
agenttel.decision.retryable = true
```

**Spring AI Enrichment (not replacement):**

```java
@Configuration
public class GenAiEnrichmentConfig {

    @Bean
    public AgentTelSpringAiEnricher agentTelEnricher(AgentTelEngine engine) {
        return new AgentTelSpringAiEnricher(engine);
        // Detects Spring AI's existing Micrometer spans and enriches them
        // with agenttel.genai.* attributes — does NOT replace Spring AI tracing
    }
}
```

### 3.5 `agenttel-testing` — Test Utilities

```java
@ExtendWith(AgentTelTestExtension.class)
class PaymentServiceTest {

    @Inject
    InMemoryAgentTelCollector collector;

    @Test
    void shouldEmitAgentReadyTelemetry() {
        // ... invoke your service ...

        // Assert agent-ready attributes are present
        AgentTelAssertions.assertThat(collector.getSpans())
            .hasSpanWithName("POST /pay")
            .hasAttribute(AgentTelAttributes.BASELINE_LATENCY_P99_MS)
            .hasAttribute(AgentTelAttributes.DECISION_RETRYABLE, true)
            .hasAttribute(AgentTelAttributes.TOPOLOGY_TEAM, "payments-platform");

        // Assert structured events were emitted
        AgentTelAssertions.assertThat(collector.getEvents())
            .hasEventWithName("agenttel.deployment.info")
            .hasBodyField("version", "2.3.1");
    }
}
```

---

## 4. Dependencies

### Minimum Requirements

| Requirement | Version |
|-------------|---------|
| Java | 17+ (LTS) |
| OpenTelemetry Java SDK | 1.55+ |
| OpenTelemetry Semantic Conventions | 1.36+ |

### Optional Dependencies

| Dependency | Module | Purpose |
|------------|--------|---------|
| Spring Boot | `agenttel-spring-boot-starter` | Auto-configuration |
| Spring AI | `agenttel-genai` | GenAI instrumentation |
| LangChain4j | `agenttel-genai` | GenAI instrumentation |
| AWS Bedrock SDK | `agenttel-genai` | GenAI instrumentation |
| Micrometer | `agenttel-micrometer-bridge` | Bridge for Micrometer users |

---

## 5. Data Flow

### 5.1 Request Processing Flow

```
1. HTTP Request arrives at Spring Boot controller

2. @AgentOperation annotation detected by AgentTelSpanProcessor
   → Creates OTel Span with standard attributes
   → Enriches with topology from TopologyRegistry
   → Attaches baseline from BaselineManager

3. Request processed by application code
   → If GenAI calls made: agenttel-genai wraps and instruments
   → If dependency calls made: standard OTel HTTP/DB spans created

4. Response returned
   → AgentTelSpanProcessor calculates actual latency
   → AnomalyDetector compares against baseline
   → If anomalous: emits agenttel.anomaly.detected event
   → If dependency error: CausalityTracker adds cause hints
   → Decision metadata attached from annotations

5. Span, metrics, and events exported via standard OTLP
   → Any OTel Collector / backend receives enriched telemetry
   → AI agents consuming this data get structured context
```

### 5.2 Startup Flow

```
1. Application starts with agenttel-spring-boot-starter

2. AgentTelAutoConfiguration reads:
   → @AgentObservable annotation on main class
   → @DeclareDependency annotations
   → application.yml agenttel.* properties

3. TopologyRegistry populated with dependency graph

4. BaselineManager initialized:
   → Static baselines from @AgentOperation annotations
   → Rolling baselines start collecting (if enabled)

5. Deployment event emitted: agenttel.deployment.info

6. Resource attributes set:
   → agenttel.topology.team, tier, domain, etc.
   → agenttel.topology.dependencies (JSON)
   → agenttel.topology.consumers (JSON)
```

---

## 6. Extension Points

AgentTel is designed to be extensible:

| Extension Point | Interface | Purpose |
|----------------|-----------|---------|
| Custom baseline sources | `BaselineProvider` | Plug in ML-based baselines, SLO platforms, etc. |
| Custom anomaly detection | `AnomalyDetector` | Replace Z-score with custom algorithms |
| Custom causality | `CausalityHintProvider` | Plug in correlation engines |
| Custom events | `AgentTelEventEmitter` | Add custom structured events |
| Custom enrichment | `SpanEnrichmentProcessor` | Add domain-specific attributes |
| Framework integration | `AgentTelFrameworkAdapter` | Support non-Spring frameworks |

---

## 7. Performance Considerations

- **Annotations are read once at startup** — no reflection at request time
- **Baselines use lock-free data structures** — concurrent reads during request processing
- **Anomaly detection is O(1)** — simple Z-score comparison, not ML inference
- **Structured events use object pooling** — minimize GC pressure
- **All enrichment is async** — uses OTel's SpanProcessor pipeline, not in the request path
- **Configurable sampling** — anomaly events can be sampled to reduce volume
- **No-op when disabled** — if `agenttel.enabled=false`, all processors are no-ops
