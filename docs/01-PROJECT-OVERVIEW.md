# AgentTel — Agent-Ready Telemetry for the JVM

> **An open-source library and semantic convention extension that makes JVM services emit telemetry designed for AI agents to consume — not just humans to read.**

---

## The Problem

Modern observability is built for human eyeballs. Services emit logs like:

```
2026-02-26 14:03:22 ERROR PaymentService - Payment failed for order-123: timeout connecting to payment-gateway
```

And metrics like:

```
http_request_duration_seconds{service="payment-svc", endpoint="/pay", status="500"} 4.5
```

A human SRE can look at these, check a dashboard, correlate with their mental model of the system, remember that `payment-gateway` had issues last Tuesday too, and diagnose the problem.

**An AI agent cannot do any of that.** It sees disconnected strings, unnamed relationships, and zero context about what "normal" looks like, what depends on what, or what to do next.

The result: AI-powered AIOps tools today spend most of their effort on **data cleaning and context reconstruction** rather than actual reasoning. Telemetry volumes grow ~30% annually, and human teams can't keep pace — but neither can AI agents when the signals they receive weren't designed for them.

## The Insight

What if services emitted telemetry that was **primarily designed for AI agents** to consume?

Not replacing human-readable signals, but **augmenting** them with structured, semantically rich telemetry that carries:

- **Dependency context** — what this service depends on, and who depends on it
- **Behavioral baselines** — what "normal" looks like, embedded in every signal
- **Causal hints** — structured links between effects and probable causes
- **Decision metadata** — is this retryable? is this a known transient issue? what's the runbook?
- **Severity reasoning** — not just "error" but "anomaly score 0.92, matches pattern: cascade-failure"

## What Is AgentTel?

AgentTel is three things:

### 1. A Semantic Convention Extension
A proposed extension to OpenTelemetry's semantic conventions that defines **agent-ready attributes and event schemas** for any service — not just GenAI systems. These conventions standardize how services communicate context that AI agents need for reasoning.

### 2. A JVM Library (Java / Kotlin)
A lightweight, idiomatic library built on `opentelemetry-java` that makes it trivial for JVM developers to emit agent-ready telemetry from Spring Boot, Micronaut, Quarkus, and other frameworks. Annotations-first API design.

### 3. A GenAI Instrumentation Enrichment Layer for JVM
An enrichment layer that builds on top of existing JVM GenAI observability (Spring AI's built-in Micrometer tracing, community projects like `otel-genai-bridges`) to add agent-ready context — cost tracking, prompt template versioning, RAG quality signals, guardrail events — and fills gaps where no instrumentation exists (Anthropic Java SDK, AWS Bedrock SDK, raw OpenAI Java SDK).

> **Note on existing work:** Spring AI already emits basic `gen_ai.*` observations via Micrometer. The community `otel-genai-bridges` project provides SNAPSHOT-quality LangChain4j instrumentation. AgentTel extends and enriches these rather than replacing them, and fills the gaps they don't cover.

## Why Now?

1. **OpenTelemetry's GenAI semantic conventions are still in Development status** — we can influence them before they stabilize
2. **JVM GenAI instrumentation is fragmented** — Spring AI has basic Micrometer-based tracing; a community SNAPSHOT project (`otel-genai-bridges`) covers LangChain4j; but there's no comprehensive, production-quality, agent-enriched solution and no coverage for Anthropic/Bedrock/OpenAI Java SDKs
3. **Enterprise Java is a massive installed base** — most enterprise backends run on JVM; they're underserved by the Python-centric AI observability tooling
4. **Industry validation** — Logz.io, Mezmo, Sawmills, Splunk, and Datadog have all validated that agent-consumable telemetry is the next frontier, but they're building proprietary solutions at the pipeline layer. Nobody is building the open-source **emission** layer
5. **The agent-ready gap** — current approaches try to retrofit agent-readiness *after* emission (in pipelines). Emitting it at the source is fundamentally better: richer context, lower latency, no information loss

## Project Principles

- **Built on OpenTelemetry** — not a competing standard. AgentTel is an OTel extension, not a replacement
- **JVM-first, multi-language later** — start with Java/Kotlin; design for eventual Go, .NET, and TypeScript ports
- **Annotations-driven** — minimize boilerplate; developers annotate, the library instruments
- **Zero-overhead when unused** — if no OTel collector is configured, AgentTel adds zero runtime cost
- **Backwards compatible** — enriches existing telemetry; never breaks standard OTel pipelines
- **Open governance** — Apache 2.0 license; accepting contributions from Day 1

## Target Users

| User | What They Get |
|------|--------------|
| **JVM Service Developers** | Annotations and APIs to emit agent-ready telemetry from their Spring Boot / Micronaut / Quarkus services |
| **AI/ML Platform Teams** | GenAI instrumentation for JVM-based LLM applications (Spring AI, LangChain4j, Bedrock SDK) |
| **AIOps Agent Builders** | A standardized, structured telemetry format they can rely on for automated reasoning |
| **SRE / Platform Teams** | Richer signals that make both human dashboards and AI agents more effective |

## Project Status

🟡 **Pre-Alpha / Design Phase**

We are currently defining semantic conventions and designing the library API. See the companion documents for details:

- `02-SEMANTIC-CONVENTIONS.md` — The proposed agent-ready semantic conventions
- `03-ARCHITECTURE.md` — Library architecture and module design
- `04-IMPLEMENTATION-ROADMAP.md` — Phased build plan with milestones

## Quick Taste — What Code Will Look Like

```java
// Declaring a service with agent-ready context
@AgentObservable(
    service = "payment-service",
    team = "payments-platform",
    tier = ServiceTier.CRITICAL
)
@DeclareDependency(name = "payment-gateway", type = DependencyType.EXTERNAL_API)
@DeclareDependency(name = "order-service", type = DependencyType.INTERNAL_SERVICE)
@DeclareConsumer(name = "notification-service")
@SpringBootApplication
public class PaymentServiceApplication { ... }

// Emitting agent-ready signals from an endpoint
@AgentOperation(
    expectedLatencyP99 = "200ms",
    retryable = true,
    runbookUrl = "https://wiki.internal/runbooks/payment-processing"
)
@PostMapping("/pay")
public PaymentResult processPayment(@RequestBody PaymentRequest req) {
    // AgentTel automatically emits:
    // - Span with dependency context, baseline, and decision metadata
    // - Metric with anomaly detection hint if latency exceeds baseline
    // - Structured event if error occurs, with causal context
}
```

```kotlin
// GenAI instrumentation for Spring AI
@AgentObservable(service = "customer-support-agent")
@RestController
class SupportController(
    private val chatClient: ChatClient  // Spring AI — auto-instrumented by AgentTel
) {
    @PostMapping("/chat")
    fun chat(@RequestBody message: String): String {
        // AgentTel auto-captures: model, tokens, latency, tool calls,
        // conversation context, confidence scores — all as structured
        // OTel spans/events following GenAI semantic conventions
        return chatClient.prompt().user(message).call().content()
    }
}
```

## License

Apache License 2.0

## Contributing

We welcome contributions! See `CONTRIBUTING.md` (coming soon).

Join the discussion:
- GitHub Discussions (coming soon)
- OpenTelemetry GenAI SIG — `#otel-genai-instrumentation` on CNCF Slack
