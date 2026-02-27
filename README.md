<p align="center">
  <strong>AgentTel</strong><br/>
  <em>Agent-Ready Telemetry for Java</em>
</p>

<p align="center">
  <a href="https://github.com/rrohitramsen/AgentTel/actions"><img src="https://github.com/rrohitramsen/AgentTel/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License"></a>
  <a href="https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-17%2B-orange.svg" alt="JDK 17+"></a>
  <a href="https://opentelemetry.io"><img src="https://img.shields.io/badge/OpenTelemetry-1.59.0-blueviolet.svg" alt="OpenTelemetry"></a>
</p>

---

AgentTel is a Java library that enriches [OpenTelemetry](https://opentelemetry.io) spans with the structured context AI agents need to **autonomously diagnose, reason about, and resolve production incidents** — without human interpretation of dashboards.

Standard observability answers *"What happened?"* AgentTel adds *"What does an AI agent need to know to act on this?"*

## The Problem

Modern observability tools generate massive volumes of telemetry — traces, metrics, logs — optimized for human consumption through dashboards and alert rules. AI agents tasked with autonomous incident response face critical gaps:

- **No behavioral context** — Spans lack baselines, so agents can't distinguish normal from anomalous
- **No topology awareness** — Agents don't know which services are critical, who owns them, or what depends on what
- **No decision metadata** — Is this operation retryable? Is there a fallback? What's the runbook?
- **No actionable interface** — Agents can read telemetry but can't query live system state or execute remediation

AgentTel closes these gaps at the instrumentation layer.

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

```yaml
# application.yml
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
  baselines:
    rolling-window-size: 1000
    rolling-min-samples: 10
  anomaly-detection:
    z-score-threshold: 3.0
```

### 3. Annotate Operations

```java
@AgentOperation(
    expectedLatencyP50 = "45ms",
    expectedLatencyP99 = "200ms",
    expectedErrorRate = 0.001,
    retryable = true,
    idempotent = true,
    runbookUrl = "https://wiki/runbooks/process-payment",
    escalationLevel = EscalationLevel.PAGE_ONCALL
)
@PostMapping("/api/payments")
public ResponseEntity<PaymentResult> processPayment(@RequestBody PaymentRequest req) {
    // Your business logic — spans are enriched automatically
}
```

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

Every span is enriched with agent-actionable context:

```
agenttel.topology.team        = "payments-platform"
agenttel.topology.tier        = "critical"
agenttel.baseline.latency_p50 = 45.0
agenttel.baseline.source      = "composite"
agenttel.decision.retryable   = true
agenttel.decision.runbook_url = "https://wiki/runbooks/process-payment"
agenttel.anomaly.detected     = false
agenttel.slo.budget_remaining = 0.85
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
│      @AgentOperation annotations + business logic           │
├─────────────────────────────────────────────────────────────┤
│                agenttel-spring-boot-starter                  │
│        Auto-configuration · BeanPostProcessor · AOP         │
├───────────────┬──────────────────┬──────────────────────────┤
│ agenttel-core │  agenttel-genai  │     agenttel-agent       │
│               │                  │                          │
│ SpanProcessor │ LangChain4j      │ MCP Server (JSON-RPC)    │
│ Baselines     │ Spring AI        │ Health Aggregation       │
│  (static +    │ Anthropic SDK    │ Incident Context Builder │
│   rolling)    │ OpenAI SDK       │ Remediation Framework    │
│ Anomaly       │ Bedrock SDK      │ Agent Action Tracker     │
│  Detection    │ Cost Calculator  │ Context Formatters       │
│ SLO Tracking  │                  │                          │
│ Pattern       │                  │                          │
│  Matching     │                  │                          │
├───────────────┴──────────────────┴──────────────────────────┤
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
| `io.agenttel` | `agenttel-spring-boot-starter` | Spring Boot auto-configuration |
| `io.agenttel` | `agenttel-testing` | Test utilities |

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
