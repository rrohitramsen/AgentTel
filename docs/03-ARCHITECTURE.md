# Architecture

This document describes the technical architecture of AgentTel, including module design, data flow, key components, and extension points.

---

## High-Level Architecture

```
                        ┌────────────────────────────────┐
                        │         AI Agent / LLM          │
                        │   (Claude, GPT, custom agent)   │
                        └────────────┬───────────────────┘
                                     │ MCP (JSON-RPC)
                        ┌────────────▼───────────────────┐
                        │       agenttel-agent            │
                        │  MCP Server · Health Aggregator │
                        │  Incident Context · Remediation │
                        └────────────┬───────────────────┘
                                     │ reads from
┌──────────────────┐    ┌────────────▼───────────────────┐
│  Your Application │───▶│       agenttel-core            │
│  @AgentOperation  │    │  SpanProcessor · Baselines     │
│  business logic   │    │  Anomaly Detection · SLOs      │
└──────────────────┘    │  Pattern Matching · Events     │
                        └────────────┬───────────────────┘
                                     │ enriched spans
                        ┌────────────▼───────────────────┐
                        │     OpenTelemetry SDK           │
                        │   OTLP Export to Backend        │
                        └────────────┬───────────────────┘
                                     │
                        ┌────────────▼───────────────────┐
                        │   Observability Backend         │
                        │  Jaeger / Tempo / Datadog / ... │
                        └────────────────────────────────┘
```

---

## Module Architecture

### agenttel-api

**Zero-dependency module** containing the public API surface.

| Component | Description |
|-----------|-------------|
| `@AgentOperation` | Method-level annotation declaring operational semantics |
| `@AgentObservable` | Service-level annotation for topology metadata |
| `@DeclareDependency` | Annotation for declaring service dependencies |
| `@DeclareConsumer` | Annotation for declaring downstream consumers |
| `AgentTelAttributes` | String constants for all `agenttel.*` attribute keys |
| Enums | `ServiceTier`, `DependencyType`, `DependencyCriticality`, `EscalationLevel`, etc. |
| Descriptors | `DependencyDescriptor`, `ConsumerDescriptor` records |

**Design decision:** The API module has zero runtime dependencies. It can be added to any project without pulling in OpenTelemetry, Spring, or any other framework.

### agenttel-core

**Runtime engine** that enriches spans and maintains operational state.

```
agenttel-core/
├── baseline/
│   ├── BaselineProvider (interface)
│   ├── StaticBaselineProvider        # From @AgentOperation annotations
│   ├── RollingBaselineProvider       # Lock-free ring buffer sliding window
│   └── CompositeBaselineProvider     # Chains providers with fallback
├── anomaly/
│   ├── AnomalyDetector              # Z-score based anomaly detection
│   ├── PatternMatcher               # Incident pattern recognition
│   └── IncidentPattern (enum)       # CASCADE_FAILURE, MEMORY_LEAK, etc.
├── slo/
│   ├── SloDefinition                # SLO target configuration
│   └── SloTracker                   # Error budget tracking with alerts
├── topology/
│   ├── TopologyRegistry             # Service dependency graph
│   └── AnnotationTopologyScanner    # Reads topology from annotations
├── enrichment/
│   └── AgentTelSpanProcessor        # Main SpanProcessor — enriches every span
├── engine/
│   └── AgentTelEngine               # Orchestrator — wires all components
├── events/
│   ├── AgentTelEventEmitter         # Structured events via OTel Logs API
│   └── DeploymentEventEmitter       # Deployment tracking events
└── resource/
    └── AgentTelResourceProvider      # Resource attributes for topology
```

#### AgentTelSpanProcessor

The central component. Implements `SpanProcessor` with two phases:

**`onStart(Context, ReadWriteSpan)`** — Mutable enrichment phase:
- Resolves `@AgentOperation` metadata for the current span
- Sets topology attributes (`team`, `tier`, `domain`)
- Sets baseline attributes from `CompositeBaselineProvider`
- Sets decision attributes (`retryable`, `idempotent`, `runbook_url`, etc.)

**`onEnd(ReadableSpan)`** — Read-only analysis phase:
- Feeds observed latency into `RollingBaselineProvider`
- Runs `AnomalyDetector` to compute z-score deviation
- Runs `PatternMatcher` to identify incident patterns
- Records success/failure in `SloTracker`
- Emits `agenttel.anomaly.detected` events via `AgentTelEventEmitter`
- Emits `agenttel.slo.budget_alert` events when thresholds are crossed

> **Note:** Because `ReadableSpan` is immutable in `onEnd()`, anomaly attributes are emitted as structured events rather than span attributes. The `CostEnrichingSpanExporter` demonstrates the delegation pattern for cases where span data must be modified at export time.

#### RollingWindow

Lock-free ring buffer for per-operation latency tracking:

```java
RollingWindow window = new RollingWindow(1000); // 1000-sample window
window.record(45.0);    // Record a latency observation
window.recordError();   // Record an error

RollingWindow.Snapshot snapshot = window.snapshot();
// snapshot.p50(), snapshot.p99(), snapshot.mean(), snapshot.stddev(), snapshot.errorRate()
```

- Thread-safe via `AtomicLong` for counters and `synchronized` blocks for array access
- O(1) recording, O(n log n) snapshot computation (sort for percentiles)
- Configurable minimum samples before baseline is considered valid

#### CompositeBaselineProvider

Chains multiple baseline sources with fallback:

```
Static → Rolling → Default
```

The first provider that returns a non-empty baseline for an operation wins. This ensures:
- Explicitly annotated operations use their declared baselines
- Operations without annotations get rolling baselines from observed traffic
- New operations with insufficient data get safe defaults

### agenttel-genai

**GenAI instrumentation module** with optional compile-time dependencies.

```
agenttel-genai/
├── conventions/
│   ├── GenAiAttributes              # gen_ai.* attribute constants
│   ├── AgentTelGenAiAttributes      # agenttel.genai.* constants
│   └── GenAiOperationName           # CHAT, EMBEDDINGS, etc.
├── cost/
│   ├── ModelCostCalculator          # Per-model cost computation
│   └── ModelPricing                 # Pricing data for known models
├── trace/
│   └── GenAiSpanBuilder             # Shared span creation utility
├── springai/
│   ├── SpringAiSpanEnricher         # SpanProcessor enriching Spring AI spans
│   └── CostEnrichingSpanExporter    # SpanExporter adding cost_usd
├── langchain4j/
│   ├── TracingChatLanguageModel     # Decorator for ChatLanguageModel
│   ├── TracingStreamingChatLanguageModel
│   ├── TracingEmbeddingModel
│   ├── TracingContentRetriever      # RAG retrieval instrumentation
│   └── LangChain4jInstrumentation   # Static factory for wrapping models
├── anthropic/
│   └── TracingAnthropicClient       # Anthropic SDK wrapper
├── openai/
│   └── TracingOpenAIClient          # OpenAI SDK wrapper
└── bedrock/
    └── TracingBedrockRuntimeClient  # AWS Bedrock SDK wrapper
```

**Key design decisions:**

1. **Spring AI: Enrich, don't replace.** Spring AI already emits `gen_ai.*` spans via Micrometer. AgentTel adds `agenttel.genai.framework` and `agenttel.genai.cost_usd` to existing spans rather than creating new ones.

2. **LangChain4j: Full instrumentation.** LangChain4j has no built-in OTel tracing, so AgentTel provides complete instrumentation via the decorator pattern.

3. **Provider SDKs: Client wrappers.** Direct instrumentation for Anthropic, OpenAI, and AWS Bedrock Java SDKs via client wrapper classes.

4. **Cost calculation at export time.** Since token counts are only available after model response, cost is computed in a `SpanExporter` wrapper using a delegating `SpanData` pattern.

### agenttel-agent

**Agent interface layer** — everything an AI agent needs to interact with the system.

```
agenttel-agent/
├── health/
│   └── ServiceHealthAggregator      # Real-time health from span data
├── incident/
│   ├── IncidentContext               # Structured incident package
│   └── IncidentContextBuilder        # Builds context from live state
├── remediation/
│   ├── RemediationAction             # Action definition with approval flag
│   ├── RemediationRegistry           # Registry of available actions
│   └── RemediationExecutor           # Executes actions with tracking
├── action/
│   └── AgentActionTracker            # Records agent decisions as OTel spans
├── context/
│   ├── ContextFormatter              # Prompt-optimized output formatters
│   └── AgentContextProvider          # Single entry point for agent queries
└── mcp/
    ├── McpServer                     # JSON-RPC HTTP server
    ├── McpToolDefinition             # Tool schema definition
    ├── McpToolHandler                # Tool execution interface
    └── AgentTelMcpServerBuilder      # Builder with pre-registered tools
```

See [Agent Layer](04-AGENT-LAYER.md) for detailed documentation.

### agenttel-spring-boot-starter

**Auto-configuration** that wires everything together for Spring Boot applications.

| Component | Description |
|-----------|-------------|
| `AgentTelAutoConfiguration` | Creates and configures `AgentTelEngine`, all providers, and SLO tracker |
| `AgentTelProperties` | Type-safe configuration binding for `agenttel.*` properties |
| `AgentTelAnnotationBeanPostProcessor` | Scans beans for `@AgentOperation` and registers metadata |
| `AgentTelHealthIndicator` | Spring Boot Actuator health endpoint integration |

---

## Data Flow

### Span Enrichment Flow

```
1. HTTP Request arrives
   │
2. Spring AOP intercepts @AgentOperation method
   │
3. AgentTelSpanProcessor.onStart()
   ├── Read @AgentOperation metadata
   ├── Set topology attributes
   ├── Set baseline attributes (static → rolling → default)
   └── Set decision attributes
   │
4. Application code executes
   ├── GenAI calls auto-instrumented (if using LangChain4j/Spring AI)
   ├── Dependency calls tracked
   └── Errors captured
   │
5. AgentTelSpanProcessor.onEnd()
   ├── Feed latency to RollingBaselineProvider
   ├── Run AnomalyDetector (z-score computation)
   ├── Run PatternMatcher (cascade, leak, spike detection)
   ├── Record in SloTracker
   ├── Emit anomaly events (if anomalous)
   └── Emit SLO budget alerts (if thresholds crossed)
   │
6. SpanExporter exports enriched span
   ├── CostEnrichingSpanExporter adds cost_usd (for GenAI spans)
   └── OTLP export to backend
```

### Agent Query Flow

```
1. AI Agent calls MCP tool (e.g., get_incident_context)
   │
2. McpServer routes JSON-RPC request to handler
   │
3. AgentContextProvider assembles context
   ├── ServiceHealthAggregator → current operation/dependency metrics
   ├── IncidentContextBuilder → structured incident package
   ├── PatternMatcher → detected patterns
   └── RemediationRegistry → available actions
   │
4. ContextFormatter produces prompt-optimized output
   │
5. MCP response returned to agent
```

---

## Extension Points

| Extension Point | Interface | Description |
|----------------|-----------|-------------|
| Baseline Provider | `BaselineProvider` | Custom baseline sources (ML models, external systems) |
| MCP Tools | `McpToolHandler` | Register custom tools on the MCP server |
| Remediation Actions | `RemediationAction` | Register domain-specific remediation actions |
| Span Processing | `SpanProcessor` | Additional span enrichment via standard OTel API |
| Event Handling | `AgentTelEventEmitter` | Custom structured event emission |

### Adding a Custom Baseline Provider

```java
public class MlBaselineProvider implements BaselineProvider {
    @Override
    public Optional<OperationBaseline> getBaseline(String operationName) {
        // Query your ML model for predicted baselines
        return Optional.of(new OperationBaseline(predictedP50, predictedP99, predictedErrorRate));
    }
}

// Wire into composite chain
CompositeBaselineProvider composite = new CompositeBaselineProvider(
    staticProvider, mlProvider, rollingProvider
);
```

### Adding a Custom MCP Tool

```java
McpServer server = new AgentTelMcpServerBuilder()
    .contextProvider(contextProvider)
    .build();

server.registerTool(
    new McpToolDefinition("query_logs", "Search recent logs",
        Map.of("query", new ParameterDefinition("string", "Log search query")),
        List.of("query")),
    args -> logService.search(args.get("query"))
);
```

---

## Performance Characteristics

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| Span enrichment (onStart) | O(1) | HashMap lookups for annotations and baselines |
| Latency recording | O(1) | Ring buffer write |
| Baseline snapshot | O(n log n) | Sort for percentiles (n = window size) |
| Anomaly detection | O(1) | Z-score computation from pre-computed stats |
| Pattern matching | O(k) | k = number of tracked dependencies |
| SLO tracking | O(m) | m = number of registered SLOs |
| Health aggregation | O(1) per span | ConcurrentHashMap + AtomicLong |

**Memory footprint per operation:**
- Rolling window: ~8KB per operation (1000 doubles)
- Latency trend: ~800B per operation (50 doubles + 50 booleans)
- Health aggregation: ~16KB per operation (1000 recent latencies)

All data structures are bounded with configurable limits to prevent unbounded growth.

---

## Thread Safety

All components are designed for concurrent access:

- `TopologyRegistry`: `ConcurrentHashMap` + `volatile` fields. Written at startup, read concurrently.
- `RollingWindow`: `AtomicLong` counters + `synchronized` array access.
- `ServiceHealthAggregator`: `ConcurrentHashMap` with `AtomicLong` counters per operation.
- `SloTracker`: `ConcurrentHashMap` with `AtomicLong` counters per SLO.
- `AgentActionTracker`: `ConcurrentLinkedDeque` for bounded history.
- Bounded collections use `Collections.synchronizedList` with periodic pruning.

---

## Security Considerations

- **No secrets in telemetry.** AgentTel does not capture request/response bodies, headers, or any PII. Only operational metadata is recorded.
- **MCP server authentication.** The built-in MCP server does not include authentication. In production, deploy behind a reverse proxy or API gateway with appropriate auth.
- **Remediation approval workflow.** Actions marked `requiresApproval = true` cannot be executed without explicit approval, preventing unauthorized automated changes.
- **Action audit trail.** All agent actions are recorded as OTel spans, providing a complete audit log of what any agent did and why.
