# Design Philosophy

The design decisions behind AgentTel — what trade-offs were considered and why we landed where we did.

---

## Core Principle

**Telemetry should carry enough context for AI agents to reason and act autonomously.** Every design choice flows from this: if an agent receives a span, it should be able to answer "what is this?", "is it healthy?", "who owns it?", and "what should I do?" without additional lookups.

---

## Configuration Over Annotations

### The Trade-off

Operational metadata — runbook URLs, escalation levels, SLO targets — changes more frequently than code. Embedding it in `@AgentOperation` annotations couples operational concerns to the development lifecycle: changing a runbook URL requires a code change, rebuild, and redeploy.

### The Decision

AgentTel supports both YAML configuration and annotations, with **config taking priority**:

```yaml
agenttel:
  operations:
    "[POST /api/payments]":
      retryable: true
      runbook-url: https://wiki/runbooks/process-payment
      escalation-level: page_oncall
```

- **YAML config** is the recommended path — operational metadata lives in `application.yml` or `agenttel.yml`, deployable via ConfigMap without code changes
- **Annotations** remain available for teams that prefer co-located metadata or need compile-time validation
- When both exist, config wins — this lets platform teams override developer-set defaults

### Why Not Annotations Only?

Developers write code, but SREs and platform teams own operational context. Forcing developers to encode escalation policies they don't own creates stale data risk and adoption friction.

---

## Library + Zero-Code: Two Integration Paths

### The Trade-off

A library dependency (Spring Boot starter) enables deep integration — annotations, AOP, auto-configuration — but requires code changes. A javaagent extension requires zero code changes but can't capture application-specific knowledge like "this endpoint is idempotent."

### The Decision

AgentTel provides both:

| Mode | Module | Code Changes | Depth |
|------|--------|-------------|-------|
| **Spring Boot Starter** | `agenttel-spring-boot-starter` | Add dependency + YAML config | Full: annotations, AOP, auto-config |
| **JavaAgent Extension** | `agenttel-javaagent` | Zero — just a JVM flag + YAML | Topology, baselines, decisions from config |

The javaagent extension uses OTel's `AutoConfigurationCustomizerProvider` SPI to register the same `SpanProcessor` and `ResourceProvider` as the Spring Boot starter, but reads config from `agenttel.yml` instead of Spring's property binding.

### Why Both?

Different teams have different constraints. A platform team rolling out observability across 200 services needs zero-code. A payments team building a critical service wants the full annotation-driven experience. AgentTel shouldn't force a choice.

---

## Topology on Resource, Not Spans

### The Trade-off

Putting topology attributes (`team`, `tier`, `domain`, `on_call_channel`) on every span makes each span self-contained — an agent can reason about any span in isolation. But topology is identical for every span from the same service, so this duplicates data on every export.

### The Decision

Topology lives on OTel **Resource attributes**, set once per service instance at startup via `AgentTelResourceProvider`. Baselines, decisions, and anomaly scores remain on **span attributes** because they vary per operation.

| Level | What | Why |
|-------|------|-----|
| Resource (once per service) | Topology: team, tier, domain, dependencies | Same for every span — set once |
| Span (per operation) | Baselines, decisions, anomaly scores | Varies by endpoint |

At 10K spans/second, this avoids duplicating ~150 bytes of topology data per span (~1.5 MB/s saved).

### Why Not All on Spans?

Self-contained spans are convenient but wasteful. OTel backends already associate Resource attributes with every span from that service — the data is available without duplication. Agents querying via MCP tools get the full picture because `AgentContextProvider` merges Resource and span data.

---

## Operation Profiles: Convention Over Configuration

### The Trade-off

Without profiles, every operation needs its own full config block — a service with 50 endpoints would have hundreds of lines of repetitive YAML. But auto-deriving everything from conventions (e.g., "all GETs are retryable") risks making wrong assumptions.

### The Decision

**Profiles** define reusable operational defaults. Operations reference a profile and optionally override specific values:

```yaml
agenttel:
  profiles:
    critical-write:
      retryable: false
      escalation-level: page_oncall
      expected-latency-p99: 500ms
  operations:
    "[POST /api/payments]":
      profile: critical-write
      runbook-url: https://wiki/runbooks/process-payment  # override
```

Resolution order: **profile defaults < per-operation overrides**

### Why Not Pure Convention?

Conventions work for simple cases but break for domain-specific knowledge. A `POST` endpoint might be idempotent (payment with idempotency key) or not (event emission). Only the team that owns the service knows. Profiles balance brevity with explicit intent.

---

## Span Enrichment Architecture

### The Trade-off

Enrichment can happen at three points: SDK level (in-process), export time (in-process but deferred), or collector level (server-side). Each has different trade-offs for latency, accuracy, and coupling.

### The Decision

AgentTel uses a **two-phase enrichment** model:

1. **`AgentTelSpanProcessor.onStart()`** — sets topology, baselines, and decision attributes when the span begins. These are immediately available to application code and downstream processors.

2. **`AgentTelEnrichingSpanExporter`** — runs at export time to add computed attributes that require the full span: error classification, causality analysis, severity assessment, baseline confidence.

Phase 1 runs on the request thread (fast, attribute-setting only). Phase 2 runs on the export thread (can do heavier computation without blocking requests).

### Why Not Collector-Side?

Collector-side enrichment is language-agnostic and centralized, but it requires the collector to know about your service topology, baselines, and operational decisions — which means another config surface to manage. SDK-level enrichment keeps everything in one place (the application's config) and works with any OTel backend without a custom collector pipeline.

---

## Future Considerations

These are areas under consideration but not yet implemented:

- **Service catalog integration** — pull operational metadata from Backstage, OpsLevel, or Cortex instead of YAML config
- **OTel Collector processor** — a Go-based collector processor for language-agnostic enrichment at the collector level
- **Sampling-aware enrichment** — skip enrichment for spans that will be sampled away
- **Convention-over-configuration** — auto-derive operational defaults from HTTP method, route patterns, and framework metadata
