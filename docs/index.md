---
hide:
  - navigation
  - toc
---

<div class="hero" markdown>

# AgentTel

<p class="hero-tagline">Agent-Ready Telemetry</p>

<p class="hero-description">
Enrich OpenTelemetry spans — backend and frontend — with the structured context
AI agents need to autonomously diagnose and resolve production incidents.
</p>

[Get Started](getting-started/quick-start.md){ .md-button .md-button--primary }
[Try the Docker Demo](getting-started/quick-start.md#try-the-docker-demo){ .md-button }

</div>

---

## What is AgentTel?

<div class="what-is" markdown>

Standard observability answers *"What happened?"*
AgentTel adds *"What does an AI agent need to know to act on this?"*

Modern observability tools generate massive volumes of telemetry — traces, metrics, logs — optimized for **human consumption** through dashboards and alert rules. AI agents tasked with autonomous incident response face critical gaps:

- **No behavioral context** — Spans lack baselines, so agents can't distinguish normal from anomalous
- **No topology awareness** — Agents don't know which services are critical, who owns them, or what depends on what
- **No decision metadata** — Is this operation retryable? Is there a fallback? What's the runbook?
- **No actionable interface** — Agents can read telemetry but can't query live system state or execute remediation

AgentTel closes these gaps at the instrumentation layer — enriching every span across the full stack (JVM backends and browser frontends) with baselines, topology, and decision metadata so AI agents can reason and act autonomously.

</div>

---

## How It Works

AgentTel enriches telemetry across the full stack — all configurable via YAML or code, no manual instrumentation required:

```mermaid
graph LR
    B1["Your Backend<br/>(JVM)"] --> AT1["AgentTel Core"]
    B2["Your Frontend<br/>(Browser)"] --> AT2["AgentTel Web SDK"]
    AT1 --> C["OpenTelemetry SDK"]
    AT2 --> C
    C --> D["OTel Collector / Backend"]
    D --> E["AI Agent"]

    AT1 -->|"Topology + Baselines<br/>+ Decisions"| C
    AT2 -->|"Journeys + Anomalies<br/>+ Correlation"| C
    E -->|"MCP Tools<br/>(9 tools)"| AT1
    B2 -->|"W3C Trace Context"| B1

    style B1 fill:#4a1d96,stroke:#7c3aed,color:#fff
    style B2 fill:#4a1d96,stroke:#7c3aed,color:#fff
    style AT1 fill:#7c3aed,stroke:#a78bfa,color:#fff
    style AT2 fill:#7c3aed,stroke:#a78bfa,color:#fff
    style C fill:#6366f1,stroke:#818cf8,color:#fff
    style D fill:#4f46e5,stroke:#6366f1,color:#fff
    style E fill:#4338ca,stroke:#6366f1,color:#fff
```

| Level | Where | What It Adds | Example |
|-------|-------|-------------|---------|
| **Topology** | OTel Resource (once per service) | Service identity, ownership, dependencies | team, tier, on-call channel |
| **Baselines** | Span attributes (per operation) | What "normal" looks like — backend and frontend | P50/P99 latency, error rate, page load time |
| **Decisions** | Span attributes (per operation) | What an agent is allowed to do | retryable, runbook URL, escalation level |
| **Journeys** | Frontend spans (per user flow) | Multi-step funnel tracking | checkout completion rate, step abandonment |
| **Anomalies** | Both backend and frontend spans | Real-time deviation detection | z-score spikes, rage clicks, error loops |
| **Correlation** | Cross-stack span linking | Frontend-to-backend trace linking | W3C Trace Context, backend trace IDs |

---

## Key Features

<div class="feature-grid" markdown>

<div class="feature-card" markdown>

### Enriched Spans

Every operation span carries baselines (P50/P99 latency, error rate), decision metadata (retryable, idempotent, runbook URL), and anomaly scores — all the context an AI agent needs.

</div>

<div class="feature-card" markdown>

### MCP Server

Built-in [Model Context Protocol](https://modelcontextprotocol.io) server exposes tools like `get_service_health`, `get_incident_context`, and `execute_remediation` over JSON-RPC.

</div>

<div class="feature-card" markdown>

### Zero-Code Mode

Drop-in OTel javaagent extension for any JVM app. No Spring dependency, no code changes — just a YAML config file and a JVM flag.

</div>

<div class="feature-card" markdown>

### GenAI Instrumentation

Full observability for LangChain4j, Spring AI, Anthropic SDK, OpenAI SDK, and AWS Bedrock — with token tracking and cost calculation.

</div>

<div class="feature-card" markdown>

### Frontend Telemetry

Browser SDK (`@agenttel/web`) with auto-instrumentation of page loads, navigation, API calls, and errors — plus journey tracking, anomaly detection, and W3C cross-stack correlation.

</div>

<div class="feature-card" markdown>

### Anomaly Detection

Real-time z-score anomaly detection on latency and error rates — backend and frontend. Rolling baselines learn from live traffic; static baselines come from config.

</div>

<div class="feature-card" markdown>

### Incident Context

Structured incident packages: what's happening, what changed, what's affected, and what to do — with cross-stack context linking frontend and backend telemetry.

</div>

<div class="feature-card" markdown>

### Instrumentation Agent

IDE MCP server that analyzes your codebase, generates AgentTel config, validates instrumentation, and auto-applies improvements — for both backend and frontend.

</div>

</div>

---

## Module Architecture

```mermaid
graph TB
    subgraph App["Your Application"]
        YML["application.yml / agenttel.yml"]
        ANN["@AgentOperation (optional)"]
    end

    subgraph Frontend["Frontend"]
        WEB["agenttel-web<br/><small>Browser SDK (TypeScript)<br/>Auto-instrumentation, Journeys,<br/>Anomaly Detection, Correlation</small>"]
    end

    subgraph Integration["Integration Layer"]
        SBS["agenttel-spring-boot-starter<br/><small>Auto-config, BPP, AOP</small>"]
        JAE["agenttel-javaagent-extension<br/><small>Zero-code OTel extension</small>"]
    end

    subgraph Core["Core Libraries"]
        COR["agenttel-core<br/><small>SpanProcessor, Baselines,<br/>Anomaly Detection, SLO Tracking</small>"]
        GEN["agenttel-genai<br/><small>LangChain4j, Spring AI,<br/>Anthropic, OpenAI, Bedrock</small>"]
        AGT["agenttel-agent<br/><small>MCP Server, Health, Incidents,<br/>Remediation, Reporting</small>"]
    end

    subgraph Tooling["IDE Tooling"]
        INS["agenttel-instrument<br/><small>MCP Server (Python)<br/>Codebase Analysis, Config Gen,<br/>Validation, Auto-Improvements</small>"]
    end

    subgraph Foundation["Foundation"]
        API["agenttel-api<br/><small>Annotations, Attributes, Models</small>"]
        OTEL["OpenTelemetry SDK"]
    end

    App --> Integration
    SBS --> COR
    SBS --> GEN
    SBS --> AGT
    JAE --> COR
    COR --> API
    GEN --> API
    AGT --> COR
    API --> OTEL
    WEB --> OTEL
    INS -.->|"generates config"| App

    style App fill:#1e1b4b,stroke:#4338ca,color:#e0e7ff
    style Frontend fill:#312e81,stroke:#4f46e5,color:#e0e7ff
    style Integration fill:#312e81,stroke:#4f46e5,color:#e0e7ff
    style Core fill:#3730a3,stroke:#6366f1,color:#e0e7ff
    style Tooling fill:#3730a3,stroke:#6366f1,color:#e0e7ff
    style Foundation fill:#4338ca,stroke:#818cf8,color:#e0e7ff
    style SBS fill:#7c3aed,stroke:#a78bfa,color:#fff
    style JAE fill:#7c3aed,stroke:#a78bfa,color:#fff
    style WEB fill:#7c3aed,stroke:#a78bfa,color:#fff
    style INS fill:#6366f1,stroke:#818cf8,color:#fff
    style COR fill:#6366f1,stroke:#818cf8,color:#fff
    style GEN fill:#6366f1,stroke:#818cf8,color:#fff
    style AGT fill:#6366f1,stroke:#818cf8,color:#fff
    style API fill:#818cf8,stroke:#a5b4fc,color:#1e1b4b
    style OTEL fill:#818cf8,stroke:#a5b4fc,color:#1e1b4b
```

---

## What an Agent Sees

When an incident occurs, an AI agent gets structured context via MCP:

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

---

## Compatibility

**Backend (JVM)**

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

**Frontend (Browser)**

| Component | Supported Versions |
|-----------|--------------------|
| TypeScript | 4.7+ |
| Modern browsers | Chrome, Firefox, Safari, Edge (ES2020+) |

**Tooling**

| Component | Supported Versions |
|-----------|--------------------|
| Python (instrument agent) | 3.11+ |

<div style="text-align: center; margin-top: 3rem;" markdown>

[Get Started](getting-started/quick-start.md){ .md-button .md-button--primary }
[View on GitHub](https://github.com/rrohitramsen/AgentTel){ .md-button }

</div>
