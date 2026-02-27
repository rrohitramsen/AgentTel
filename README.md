<p align="center">
  <strong>AgentTel</strong><br/>
  <em>Agent-Ready Telemetry for JVM Applications</em>
</p>

<p align="center">
  <a href="https://github.com/rrohitramsen/AgentTel/actions"><img src="https://github.com/rrohitramsen/AgentTel/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License"></a>
  <a href="https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-17%2B-orange.svg" alt="JDK 17+"></a>
  <a href="https://opentelemetry.io"><img src="https://img.shields.io/badge/OpenTelemetry-1.59.0-blueviolet.svg" alt="OpenTelemetry"></a>
</p>

---

AgentTel is a JVM library that enriches [OpenTelemetry](https://opentelemetry.io) spans with the structured context AI agents need to **autonomously diagnose, reason about, and resolve production incidents** — without human interpretation of dashboards. Works with Java, Kotlin, Scala, and any JVM language.

Standard observability answers *"What happened?"* AgentTel adds *"What does an AI agent need to know to act on this?"*

## The Problem

Modern observability tools generate massive volumes of telemetry — traces, metrics, logs — optimized for human consumption through dashboards and alert rules. AI agents tasked with autonomous incident response face critical gaps:

- **No behavioral context** — Spans lack baselines, so agents can't distinguish normal from anomalous
- **No topology awareness** — Agents don't know which services are critical, who owns them, or what depends on what
- **No decision metadata** — Is this operation retryable? Is there a fallback? What's the runbook?
- **No actionable interface** — Agents can read telemetry but can't query live system state or execute remediation

AgentTel closes these gaps at the instrumentation layer.

## Design Philosophy

**Core principle: telemetry should carry enough context for AI agents to reason and act autonomously.**

AgentTel enriches telemetry at three levels — all configurable via YAML, no code changes required:

| Level | Where | What | Example |
|-------|-------|------|---------|
| **Topology** | OTel Resource (once per service) | Service identity, ownership, dependencies | team, tier, on-call channel |
| **Baselines** | Span attributes (per operation) | What "normal" looks like | P50/P99 latency, error rate |
| **Decisions** | Span attributes (per operation) | What an agent is allowed to do | retryable, runbook URL, escalation level |

Topology is set once on the OTel Resource and automatically associated with all telemetry by the SDK. Baselines and decision metadata are attached per-operation on spans. This avoids redundant data on every span while ensuring agents always have the full context.

## What AgentTel Provides

### Enriched Telemetry (agenttel-core)

Every span is automatically enriched with agent-actionable attributes:

| Category | Attributes | Purpose |
|----------|-----------|---------|
| **Topology** | `agenttel.topology.team`, `tier`, `domain`, `dependencies` | Service identity and dependency graph |
| **Baselines** | `agenttel.baseline.latency_p50_ms`, `error_rate`, `source` | What "normal" looks like for each operation |
| **Decisions** | `agenttel.decision.retryable`, `idempotent`, `runbook_url`, `escalation_level` | What an agent is allowed to do |
| **Anomalies** | `agenttel.anomaly.detected`, `pattern`, `score` | Real-time deviation detection |
| **SLOs** | `agenttel.slo.budget_remaining`, `burn_rate` | Error budget consumption tracking |

### Agent Interface Layer (agenttel-agent)

A complete toolkit for AI agent interaction with production systems:

| Component | Description |
|-----------|-------------|
| **MCP Server** | JSON-RPC server implementing the [Model Context Protocol](https://modelcontextprotocol.io) — exposes telemetry as tools AI agents can call |
| **Health Aggregation** | Real-time service health from span data with operation-level and dependency-level metrics |
| **Incident Context** | Structured incident packages: what's happening, what changed, what's affected, what to do |
| **Remediation Framework** | Registry of executable remediation actions with approval workflows |
| **Action Tracking** | Every agent decision and action recorded as OTel spans for full auditability |
| **Context Formatters** | Prompt-optimized output formats (compact, full, JSON) tuned for LLM context windows |

### GenAI Instrumentation (agenttel-genai)

Full observability for AI/ML workloads on the JVM:

| Framework | Approach | Coverage |
|-----------|----------|----------|
| **Spring AI** | SpanProcessor enrichment of existing Micrometer spans | Framework tag, cost calculation |
| **LangChain4j** | Decorator-based full instrumentation | Chat, embeddings, RAG retrieval |
| **Anthropic SDK** | Client wrapper | Messages API with token/cost tracking |
| **OpenAI SDK** | Client wrapper | Chat completions with token/cost tracking |
| **AWS Bedrock** | Client wrapper | Converse API with token/cost tracking |

## Quick Start

### 1. Add Dependencies

**Maven:**

```xml
<dependencies>
    <!-- Core: span enrichment, baselines, anomaly detection, SLO tracking -->
    <dependency>
        <groupId>io.agenttel</groupId>
        <artifactId>agenttel-spring-boot-starter</artifactId>
        <version>0.1.0-alpha</version>
    </dependency>

    <!-- Optional: GenAI instrumentation -->
    <dependency>
        <groupId>io.agenttel</groupId>
        <artifactId>agenttel-genai</artifactId>
        <version>0.1.0-alpha</version>
    </dependency>

    <!-- Optional: Agent interface layer (MCP server, incident context, remediation) -->
    <dependency>
        <groupId>io.agenttel</groupId>
        <artifactId>agenttel-agent</artifactId>
        <version>0.1.0-alpha</version>
    </dependency>
</dependencies>
```

**Gradle:**

```kotlin
// build.gradle.kts
dependencies {
    // Core: span enrichment, baselines, anomaly detection, SLO tracking
    implementation("io.agenttel:agenttel-spring-boot-starter:0.1.0-alpha")

    // Optional: GenAI instrumentation
    implementation("io.agenttel:agenttel-genai:0.1.0-alpha")

    // Optional: Agent interface layer (MCP server, incident context, remediation)
    implementation("io.agenttel:agenttel-agent:0.1.0-alpha")
}
```

### 2. Configure Your Service

All enrichment is driven by YAML configuration -- no code changes needed:

```yaml
# application.yml
agenttel:
  # Topology: set once on the OTel Resource, associated with all telemetry
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

  # Reusable operational profiles — reduce repetition across operations
  profiles:
    critical-write:
      retryable: false
      escalation-level: page_oncall
      safe-to-restart: false
    read-only:
      retryable: true
      idempotent: true
      escalation-level: notify_team

  # Per-operation baselines and decision metadata
  # Use bracket notation [key] for operation names with special characters
  operations:
    "[POST /api/payments]":
      profile: critical-write
      expected-latency-p50: "45ms"
      expected-latency-p99: "200ms"
      expected-error-rate: 0.001
      retryable: true               # overrides profile default
      idempotent: true
      runbook-url: "https://wiki/runbooks/process-payment"
    "[GET /api/payments/{id}]":
      profile: read-only
      expected-latency-p50: "15ms"
      expected-latency-p99: "80ms"

  baselines:
    rolling-window-size: 1000
    rolling-min-samples: 10
  anomaly-detection:
    z-score-threshold: 3.0
```

### 3. Optional: Annotate for IDE Support

Annotations are optional -- YAML config above is sufficient. Use `@AgentOperation` when you want IDE autocomplete and compile-time validation. Reference profiles to avoid repeating values:

```java
@AgentOperation(profile = "critical-write")
@PostMapping("/api/payments")
public ResponseEntity<PaymentResult> processPayment(@RequestBody PaymentRequest req) {
    // Your business logic — spans are enriched automatically
}
```

> When both YAML config and annotations define the same operation, YAML config takes priority. Per-operation values override profile defaults.

### 4. Start the MCP Server (Optional)

```java
// Expose telemetry to AI agents via MCP
McpServer mcp = new AgentTelMcpServerBuilder()
    .port(8081)
    .contextProvider(agentContextProvider)
    .remediationExecutor(remediationExecutor)
    .build();
mcp.start();
```

AI agents can now call tools like `get_service_health`, `get_incident_context`, `list_remediation_actions`, and `execute_remediation` over JSON-RPC.

### 5. What You Get

**Resource attributes** (set once per service, associated with all telemetry):

```
agenttel.topology.team         = "payments-platform"
agenttel.topology.tier         = "critical"
agenttel.topology.domain       = "commerce"
agenttel.topology.on_call_channel = "#payments-oncall"
agenttel.topology.dependencies = [{"name":"postgres","type":"database",...}]
```

**Span attributes** (per operation, only on operations with registered metadata):

```
agenttel.baseline.latency_p50_ms = 45.0
agenttel.baseline.latency_p99_ms = 200.0
agenttel.baseline.error_rate     = 0.001
agenttel.baseline.source         = "static"
agenttel.decision.retryable      = true
agenttel.decision.runbook_url    = "https://wiki/runbooks/process-payment"
agenttel.decision.escalation_level = "page_oncall"
agenttel.anomaly.detected        = false
agenttel.slo.budget_remaining    = 0.85
```

When an incident occurs, agents get structured context via MCP:

```
=== INCIDENT inc-a3f2b1c4 ===
SEVERITY: HIGH
SUMMARY: POST /api/payments experiencing elevated error rate (5.2%)

## WHAT IS HAPPENING
Error Rate: 5.2% (baseline: 0.1%)
Latency P50: 312ms (baseline: 45ms)
Patterns: ERROR_RATE_SPIKE

## WHAT CHANGED
Last Deploy: v2.1.0 at 2025-01-15T14:30:00Z

## WHAT IS AFFECTED
Scope: operation_specific
User-Facing: YES
Affected Deps: stripe-api

## SUGGESTED ACTIONS
  - [HIGH] rollback_deployment: Rollback to previous version (NEEDS APPROVAL)
  - [MEDIUM] enable_circuit_breakers: Circuit break stripe-api
```

## Module Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      Your Application                       │
│    application.yml config + optional @AgentOperation        │
├──────────────────────────────┬──────────────────────────────┤
│  agenttel-spring-boot-starter│  agenttel-javaagent-extension│
│  Auto-config · BPP · AOP    │  Zero-code OTel javaagent    │
│  (Spring Boot apps)         │  extension (any JVM app)     │
├───────────────┬──────────────┴─┬────────────────────────────┤
│ agenttel-core │  agenttel-genai │     agenttel-agent        │
│               │                 │                           │
│ SpanProcessor │ LangChain4j     │ MCP Server (JSON-RPC)     │
│ Baselines     │ Spring AI       │ Health Aggregation        │
│  (static +    │ Anthropic SDK   │ Incident Context Builder  │
│   rolling)    │ OpenAI SDK      │ Remediation Framework     │
│ Anomaly       │ Bedrock SDK     │ Agent Action Tracker      │
│  Detection    │ Cost Calculator │ Context Formatters        │
│ SLO Tracking  │                 │                           │
│ Pattern       │                 │                           │
│  Matching     │                 │                           │
├───────────────┴─────────────────┴───────────────────────────┤
│                        agenttel-api                          │
│     @AgentOperation · AgentTelAttributes · Data Models      │
├─────────────────────────────────────────────────────────────┤
│                    OpenTelemetry SDK                         │
└─────────────────────────────────────────────────────────────┘
```

| Module | Artifact | Description |
|--------|----------|-------------|
| `agenttel-api` | `io.agenttel:agenttel-api` | Annotations, attribute constants, enums, data models. Zero runtime dependencies. |
| `agenttel-core` | `io.agenttel:agenttel-core` | Runtime engine — span enrichment, static + rolling baselines, z-score anomaly detection, pattern matching, SLO tracking, structured events. |
| `agenttel-genai` | `io.agenttel:agenttel-genai` | GenAI instrumentation — LangChain4j wrappers, Spring AI enrichment, Anthropic/OpenAI/Bedrock SDK instrumentation, cost calculation. |
| `agenttel-agent` | `io.agenttel:agenttel-agent` | Agent interface layer — MCP server, real-time health aggregation, incident context builder, remediation framework, agent action tracking. |
| `agenttel-javaagent-extension` | `io.agenttel:agenttel-javaagent-extension` | Zero-code OTel javaagent extension. Drop-in enrichment for any JVM app — no Spring dependency. |
| `agenttel-spring-boot-starter` | `io.agenttel:agenttel-spring-boot-starter` | Spring Boot auto-configuration. Single dependency for Spring Boot apps. |
| `agenttel-testing` | `io.agenttel:agenttel-testing` | Test utilities for verifying span enrichment. |

## Documentation

| Document | Description |
|----------|-------------|
| [Project Overview](docs/01-PROJECT-OVERVIEW.md) | Vision, motivation, and design philosophy |
| [Semantic Conventions](docs/02-SEMANTIC-CONVENTIONS.md) | Complete attribute and event schema reference |
| [Architecture](docs/03-ARCHITECTURE.md) | Technical architecture, data flow, and extension points |
| [Agent Layer](docs/04-AGENT-LAYER.md) | MCP server, incident context, remediation, and agent interaction |
| [GenAI Instrumentation](docs/05-GENAI-INSTRUMENTATION.md) | LLM framework instrumentation and cost tracking |
| [API Reference](docs/06-API-REFERENCE.md) | Annotations, programmatic API, and configuration reference |
| [Roadmap](docs/07-ROADMAP.md) | Implementation phases and release plan |
| [Design Considerations](docs/08-DESIGN-CONSIDERATIONS.md) | Trade-offs, evolution path, and future direction |

## Examples

Working examples to get you started quickly:

| Example | Description | Run Command |
|---------|-------------|-------------|
| [Spring Boot Example](examples/spring-boot-example) | Payment service with span enrichment, topology, baselines, anomaly detection, and MCP server | `./gradlew :examples:spring-boot-example:bootRun` |
| [LangChain4j Example](examples/langchain4j-example) | GenAI tracing with LangChain4j — chat spans, token tracking, and cost calculation | `./gradlew :examples:langchain4j-example:run` |

Each example includes a README with step-by-step instructions and curl commands to exercise the instrumentation.

## Compatibility

| Component | Supported Versions |
|-----------|--------------------|
| Java | 17, 21 |
| OpenTelemetry SDK | 1.59.0+ |
| Spring Boot | 3.4.x |
| Spring AI | 1.0.0+ (optional) |
| LangChain4j | 1.0.0+ (optional) |
| Anthropic Java SDK | 2.0.0+ (optional) |
| OpenAI Java SDK | 4.0.0+ (optional) |
| AWS Bedrock SDK | 2.30.0+ (optional) |

## Build Tool Support

AgentTel publishes standard Maven artifacts to Maven Central. Your application can use **any build tool** — Maven, Gradle, sbt, Bazel, or anything that resolves Maven dependencies.

### Maven

```xml
<dependency>
    <groupId>io.agenttel</groupId>
    <artifactId>agenttel-spring-boot-starter</artifactId>
    <version>0.1.0-alpha</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("io.agenttel:agenttel-spring-boot-starter:0.1.0-alpha")
```

### Gradle (Groovy DSL)

```groovy
implementation 'io.agenttel:agenttel-spring-boot-starter:0.1.0-alpha'
```

### All Available Artifacts

| Group ID | Artifact ID | Description |
|----------|------------|-------------|
| `io.agenttel` | `agenttel-api` | Annotations and constants (zero dependencies) |
| `io.agenttel` | `agenttel-core` | Runtime engine |
| `io.agenttel` | `agenttel-genai` | GenAI instrumentation |
| `io.agenttel` | `agenttel-agent` | Agent interface layer (MCP, health, incidents) |
| `io.agenttel` | `agenttel-javaagent-extension` | Zero-code OTel javaagent extension |
| `io.agenttel` | `agenttel-spring-boot-starter` | Spring Boot auto-configuration |
| `io.agenttel` | `agenttel-testing` | Test utilities |

## Zero-Code Mode (JavaAgent Extension)

For applications where you cannot add a library dependency, use the javaagent extension. No code changes, no Spring dependency:

```bash
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.javaagent.extensions=agenttel-javaagent-extension.jar \
     -Dagenttel.config.file=agenttel.yml \
     -jar myapp.jar
```

The extension reads configuration from `agenttel.yml` (same YAML format as above), system properties (`-Dagenttel.topology.team=payments`), or environment variables (`AGENTTEL_TOPOLOGY_TEAM=payments`). It registers as an OTel `AutoConfigurationCustomizerProvider` via SPI, adding topology to the Resource and enriching spans with baselines and decisions.

## FAQ

**Does AgentTel require code changes?**
No. All enrichment can be driven entirely from YAML configuration (`application.yml` for Spring Boot, or `agenttel.yml` for the javaagent extension). Annotations like `@AgentOperation` are optional and only needed when you want IDE autocomplete and compile-time validation.

**How does this differ from Datadog/New Relic agents?**
Vendor agents collect metrics, traces, and logs. AgentTel enriches telemetry with *agent-actionable context* — baselines (what "normal" looks like), decision metadata (retryable, runbook URL, escalation level), and topology (who owns this, what depends on what). This is the structured context AI agents need to autonomously reason about and resolve incidents, not just observe them.

**Will this increase my telemetry size?**
Topology attributes are set once on the OTel Resource (not repeated on every span). Baselines and decision metadata add ~300-500 bytes per enriched operation span. Only operations with registered metadata are enriched — internal framework spans (Spring dispatcher, Tomcat, etc.) are not affected.

**Can I use this without Spring Boot?**
Yes. The javaagent extension works with any JVM application — no Spring dependency. The core library can also be used programmatically via `AgentTelEngine.builder()` for custom integration.

**What's the performance overhead?**
Span enrichment adds sub-millisecond latency per span (hash map lookups). Rolling baselines and anomaly detection use O(1) sliding windows. The library is designed for high-throughput production use.

**Does it work with my existing OTel setup?**
Yes. AgentTel registers as a standard OTel `SpanProcessor` and `ResourceProvider`. It is fully compatible with any OTel-compatible backend (Jaeger, Grafana Tempo, Datadog, New Relic, Honeycomb, etc.) and does not modify existing spans — it only adds attributes.

**What are operation profiles?**
Predefined sets of operational defaults (retry policy, escalation level, etc.) that reduce config repetition. Define a profile once, reference it from multiple operations. Per-operation values override profile defaults.

**How does YAML config interact with annotations?**
YAML config takes priority. When both YAML config and `@AgentOperation` annotations define the same operation, the YAML values are used. Annotations fill in gaps for operations not defined in config.

## Building from Source

```bash
# Requires JDK 17+
./gradlew clean build

# Run tests only
./gradlew test

# Build a specific module
./gradlew :agenttel-agent:build
```

## Contributing

Contributions are welcome. Please read the [Contributing Guide](CONTRIBUTING.md) for build instructions, PR guidelines, and code style conventions. See the [Architecture](docs/03-ARCHITECTURE.md) document for design guidance.

For security issues, please see our [Security Policy](SECURITY.md).

## License

AgentTel is released under the [Apache License 2.0](LICENSE).
