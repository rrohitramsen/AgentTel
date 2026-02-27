# Design Considerations

This document captures open design questions, trade-offs, and future direction for AgentTel's instrumentation approach. It is intended to guide the next iteration of the library.

---

## 1. Library Dependency vs. Zero-Code Agent

### Current State

Users add `agenttel-spring-boot-starter` as a compile-time dependency, configure via `application.yml`, and optionally annotate code with `@AgentOperation`.

### Pros

- Deep integration: annotations capture application-specific knowledge (retryable, idempotent, runbook URLs) that no external agent could infer
- Type-safe: compile-time checks on attribute names, enum values, annotation parameters
- Auto-configuration: Spring Boot starter makes setup low-friction for Spring apps
- No sidecar or extra process: runs in-process alongside the application

### Cons

- Requires code changes: developers must add the dependency and (optionally) annotations — this is more friction than vendor agents (Datadog, New Relic) which attach as javaagents at runtime with zero code changes
- Tied to build lifecycle: upgrading AgentTel requires a code change, rebuild, and redeploy
- Not language-agnostic: the library approach is Java/JVM-specific; each language needs its own implementation

### Future Direction

Consider offering a **hybrid model**:
- **Javaagent mode**: A javaagent that attaches at runtime and auto-discovers topology from OTel resource attributes, Spring bean metadata, and HTTP route patterns. This would provide topology enrichment with zero code changes.
- **Library mode** (current): For teams that want the full annotation-driven experience with compile-time safety
- **OTel Collector processor**: A server-side component (written in Go) that enriches spans at the collector level using external configuration. This would be fully language-agnostic.

---

## 2. Operational Knowledge Hardcoded in Source Code

### Current State

`@AgentOperation` annotations embed operational metadata directly in source code:

```java
@AgentOperation(
    expectedLatencyP50 = "45ms",
    expectedLatencyP99 = "200ms",
    retryable = true,
    runbookUrl = "https://wiki/runbooks/process-payment",
    escalationLevel = EscalationLevel.PAGE_ONCALL,
    safeToRestart = false
)
```

### Pros

- Co-located: operational knowledge lives next to the code it describes, making it easy to discover
- Versioned: annotation values are tracked in git alongside the code
- IDE support: autocomplete, refactoring, and compile-time validation
- Works today: no external infrastructure needed

### Cons

- **Operational metadata changes more frequently than code**: runbook URLs move, SLO targets shift, escalation policies evolve — but code doesn't get redeployed for operational changes
- **Wrong audience**: developers write the code, but SREs and platform teams own the operational context (runbooks, escalation, SLOs). Annotations force developers to encode knowledge they may not have.
- **Stale data risk**: if a runbook URL changes and nobody updates the annotation, agents act on outdated information
- **Coupling**: application code becomes coupled to operational concerns

### Future Direction

**Externalize operational metadata** so it can change without code deployments:

1. **YAML/config-driven approach** (near-term): Extend `application.yml` to accept per-operation decision metadata, similar to how topology and dependencies are already configured:
   ```yaml
   agenttel:
     operations:
       "POST /api/payments":
         retryable: true
         runbook-url: https://wiki/runbooks/process-payment
         escalation-level: page_oncall
   ```
   This keeps metadata in config, deployable via ConfigMap or config service without code changes.

2. **Service catalog integration** (medium-term): Pull operational metadata from an external service catalog (Backstage, OpsLevel, Cortex) at startup or on a refresh interval. The catalog becomes the single source of truth.

3. **Inferred from observed data** (long-term): Baselines should come from actual traffic patterns (the `RollingBaselineProvider` already does this). Decision metadata like "retryable" could potentially be inferred from retry patterns in traces.

4. **Annotations as optional overrides**: Keep annotations for cases where developers want to explicitly declare operational intent, but make them optional — the system works fully from external config.

---

## 3. Source Code Bloat from Instrumentation

### Current State

Each annotated endpoint adds 5-10 lines of annotation metadata:

```java
@AgentOperation(
    expectedLatencyP50 = "45ms",
    expectedLatencyP99 = "200ms",
    expectedErrorRate = 0.001,
    retryable = true,
    idempotent = true,
    runbookUrl = "https://wiki/runbooks/process-payment",
    fallbackDescription = "Returns cached pricing",
    escalationLevel = EscalationLevel.PAGE_ONCALL,
    safeToRestart = false
)
@PostMapping
public ResponseEntity<PaymentResult> processPayment(...) {
```

### Pros

- Explicit: every enrichment is visible and intentional
- Discoverable: grep for `@AgentOperation` to find all instrumented endpoints
- Familiar: follows the same pattern as `@Transactional`, `@Cacheable`, etc.

### Cons

- **Visual noise**: a service with 50+ endpoints would have hundreds of lines of annotation metadata
- **Repetitive**: many operations share similar operational profiles (same escalation level, same retry policy)
- **Maintenance burden**: more annotation parameters = more things to keep current
- **Barrier to adoption**: teams may resist adding "yet another annotation" to their controllers

### Future Direction

1. **Config-driven (no annotations needed)**: As described in section 2, move to YAML configuration. Zero source code changes needed.
2. **Profile-based annotations**: Define operation profiles (e.g., `@AgentOperation(profile = "critical-write")`) that map to a set of defaults, reducing per-method annotation verbosity.
3. **Convention-over-configuration**: Auto-derive operational defaults from existing metadata. For example, a `POST` endpoint is likely not idempotent, a `GET` is likely retryable.
4. **Auto-detection**: The `AgentTelAnnotationBeanPostProcessor` already scans Spring MVC annotations. It could go further — inferring operation names without any AgentTel-specific annotations at all.

---

## 4. Span Size and Telemetry Overhead

### Current State

AgentTel adds up to 15 extra attributes per span:

| Category | Attributes | Approx. Bytes |
|----------|-----------|---------------|
| Topology | team, tier, domain, on_call_channel | ~150 |
| Baseline | latency_p50_ms, latency_p99_ms, error_rate, source | ~120 |
| Decision | retryable, idempotent, runbook_url, fallback_available, fallback_description, escalation_level, safe_to_restart | ~300 |
| **Total** | **15 attributes** | **~500-800 bytes/span** |

At 10K spans/second, this is an additional **5-8 MB/s** of telemetry data.

### Pros

- Agent-actionable: every span carries enough context for an AI agent to reason about it without additional lookups
- Self-contained: no need to join span data with external metadata at query time
- Standard OTel: uses native span attributes, compatible with any OTel backend

### Cons

- **Redundant data**: topology attributes (team, tier, domain, on_call_channel) are identical for every span from the same service. They are already available as OTel Resource attributes — duplicating them on every span wastes bandwidth and storage.
- **Storage cost at scale**: at high throughput, the extra bytes per span compound significantly in backends like Jaeger, Tempo, or Elasticsearch
- **Collector/exporter bandwidth**: more data per span means more network traffic to the collector
- **Query performance**: larger spans can slow down trace search and analysis in some backends

### Future Direction

1. **Move topology to Resource attributes** (near-term): OTel Resource attributes are set once per service instance and attached to every span by the SDK — not repeated per span. Topology (team, tier, domain, on_call_channel) belongs here. This removes 4 redundant attributes from every span.

2. **Selective enrichment** (near-term): Not every span needs decision metadata. Enrich only spans for operations that have registered `@AgentOperation` metadata. Internal framework spans (Spring dispatcher, Tomcat, etc.) should only get topology via Resource.

3. **Enrichment at the collector** (medium-term): Instead of adding attributes at the SDK level, an OTel Collector processor could enrich spans server-side using external configuration. This moves the cost from the application to the collector infrastructure and allows centralized management.

4. **Sampling-aware enrichment** (long-term): If spans are going to be sampled away, there's no point enriching them. Integrate with OTel's sampling decisions to skip enrichment for spans that won't be exported.

5. **Compression**: Some backends compress span data. The text-heavy attributes (runbook URLs, fallback descriptions) compress well. Measure actual wire-size impact rather than assuming worst case.

---

## Summary: Evolution Path

```
Current (v0.1)                    Near-term (v0.2)                 Long-term (v1.0)
─────────────────────────────────────────────────────────────────────────────────────
Library dependency        →   Library + javaagent mode     →   + OTel Collector processor
Annotations in code       →   YAML config (no code)        →   Service catalog integration
All attrs on every span   →   Topology as Resource attrs   →   Selective + collector-side
Full annotation params    →   Operation profiles           →   Convention-over-config
```

The core principle remains: **telemetry should carry enough context for AI agents to reason and act autonomously**. The question is how that context gets into the telemetry — and the answer should evolve from "developers annotate code" to "the platform injects it automatically."
