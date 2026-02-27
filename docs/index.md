# AgentTel

**Agent-Ready Telemetry for JVM Applications**

---

AgentTel is a JVM library that enriches [OpenTelemetry](https://opentelemetry.io) spans with the structured context AI agents need to **autonomously diagnose, reason about, and resolve production incidents** — without human interpretation of dashboards.

Standard observability answers *"What happened?"* AgentTel adds *"What does an AI agent need to know to act on this?"*

[Get Started](getting-started/quick-start.md){ .md-button .md-button--primary }
[View on GitHub](https://github.com/rrohitramsen/AgentTel){ .md-button }

---

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

## What AgentTel Provides

### Enriched Telemetry

Every span is automatically enriched with agent-actionable attributes:

| Category | Attributes | Purpose |
|----------|-----------|---------|
| **Topology** | `agenttel.topology.team`, `tier`, `domain`, `dependencies` | Service identity and dependency graph |
| **Baselines** | `agenttel.baseline.latency_p50_ms`, `error_rate`, `source` | What "normal" looks like for each operation |
| **Decisions** | `agenttel.decision.retryable`, `idempotent`, `runbook_url`, `escalation_level` | What an agent is allowed to do |
| **Anomalies** | `agenttel.anomaly.detected`, `pattern`, `score` | Real-time deviation detection |
| **SLOs** | `agenttel.slo.budget_remaining`, `burn_rate` | Error budget consumption tracking |

### Agent Interface Layer

| Component | Description |
|-----------|-------------|
| **MCP Server** | JSON-RPC server implementing the [Model Context Protocol](https://modelcontextprotocol.io) — exposes telemetry as tools AI agents can call |
| **Health Aggregation** | Real-time service health from span data with operation-level and dependency-level metrics |
| **Incident Context** | Structured incident packages: what's happening, what changed, what's affected, what to do |
| **Remediation Framework** | Registry of executable remediation actions with approval workflows |
| **Action Tracking** | Every agent decision and action recorded as OTel spans for full auditability |

### GenAI Instrumentation

| Framework | Approach | Coverage |
|-----------|----------|----------|
| **Spring AI** | SpanProcessor enrichment | Framework tag, cost calculation |
| **LangChain4j** | Decorator-based instrumentation | Chat, embeddings, RAG retrieval |
| **Anthropic SDK** | Client wrapper | Messages API with token/cost tracking |
| **OpenAI SDK** | Client wrapper | Chat completions with token/cost tracking |
| **AWS Bedrock** | Client wrapper | Converse API with token/cost tracking |

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
| `agenttel-core` | `io.agenttel:agenttel-core` | Runtime engine — span enrichment, baselines, anomaly detection, SLO tracking. |
| `agenttel-genai` | `io.agenttel:agenttel-genai` | GenAI instrumentation — LangChain4j, Spring AI, Anthropic/OpenAI/Bedrock SDKs. |
| `agenttel-agent` | `io.agenttel:agenttel-agent` | Agent interface layer — MCP server, health, incidents, remediation. |
| `agenttel-javaagent-extension` | `io.agenttel:agenttel-javaagent-extension` | Zero-code OTel javaagent extension for any JVM app. |
| `agenttel-spring-boot-starter` | `io.agenttel:agenttel-spring-boot-starter` | Spring Boot auto-configuration. |
| `agenttel-testing` | `io.agenttel:agenttel-testing` | Test utilities for verifying span enrichment. |

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
