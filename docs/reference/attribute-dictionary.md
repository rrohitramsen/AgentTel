# Attribute Dictionary

Complete reference for every attribute AgentTel adds to OpenTelemetry spans and resources. Each entry describes what the attribute is, why an AI agent needs it, and when it appears.

> **Quick navigation:** [Topology](#topology) | [Baselines](#baselines) | [Decisions](#decisions) | [Anomaly](#anomaly) | [Error Classification](#error-classification) | [Causality](#causality) | [Severity](#severity) | [Change Correlation](#change-correlation) | [SLO](#slo) | [Deployment](#deployment) | [GenAI](#genai) | [Agent Identity](#agent-identity) | [Sessions](#sessions) | [Circuit Breaker](#circuit-breaker) | [Frontend](#frontend)

---

## Alphabetical Index

All `agenttel.*` attributes sorted alphabetically. Click any key to jump to its category.

| Attribute Key | Category |
|---------------|----------|
| `agenttel.agent.id` | [Agent Identity](#agent-identity) |
| `agenttel.agent.role` | [Agent Identity](#agent-identity) |
| `agenttel.agent.session_id` | [Agent Identity](#agent-identity) |
| `agenttel.anomaly.detected` | [Anomaly](#anomaly) |
| `agenttel.anomaly.latency_z_score` | [Anomaly](#anomaly) |
| `agenttel.anomaly.pattern` | [Anomaly](#anomaly) |
| `agenttel.anomaly.score` | [Anomaly](#anomaly) |
| `agenttel.baseline.confidence` | [Baselines](#baselines) |
| `agenttel.baseline.error_rate` | [Baselines](#baselines) |
| `agenttel.baseline.latency_p50_ms` | [Baselines](#baselines) |
| `agenttel.baseline.latency_p99_ms` | [Baselines](#baselines) |
| `agenttel.baseline.sample_count` | [Baselines](#baselines) |
| `agenttel.baseline.slo` | [Baselines](#baselines) |
| `agenttel.baseline.source` | [Baselines](#baselines) |
| `agenttel.baseline.throughput_rps` | [Baselines](#baselines) |
| `agenttel.baseline.updated_at` | [Baselines](#baselines) |
| `agenttel.cause.category` | [Causality](#causality) |
| `agenttel.cause.correlated_event_id` | [Causality](#causality) |
| `agenttel.cause.correlated_span_id` | [Causality](#causality) |
| `agenttel.cause.dependency` | [Causality](#causality) |
| `agenttel.cause.hint` | [Causality](#causality) |
| `agenttel.cause.started_at` | [Causality](#causality) |
| `agenttel.circuit_breaker.dependency` | [Circuit Breaker](#circuit-breaker) |
| `agenttel.circuit_breaker.failure_count` | [Circuit Breaker](#circuit-breaker) |
| `agenttel.circuit_breaker.name` | [Circuit Breaker](#circuit-breaker) |
| `agenttel.circuit_breaker.new_state` | [Circuit Breaker](#circuit-breaker) |
| `agenttel.circuit_breaker.previous_state` | [Circuit Breaker](#circuit-breaker) |
| `agenttel.client.anomaly.detected` | [Frontend](#frontend) |
| `agenttel.client.anomaly.pattern` | [Frontend](#frontend) |
| `agenttel.client.anomaly.score` | [Frontend](#frontend) |
| `agenttel.client.app.environment` | [Frontend](#frontend) |
| `agenttel.client.app.name` | [Frontend](#frontend) |
| `agenttel.client.app.platform` | [Frontend](#frontend) |
| `agenttel.client.app.version` | [Frontend](#frontend) |
| `agenttel.client.baseline.api_call_p50_ms` | [Frontend](#frontend) |
| `agenttel.client.baseline.interaction_error_rate` | [Frontend](#frontend) |
| `agenttel.client.baseline.page_load_p50_ms` | [Frontend](#frontend) |
| `agenttel.client.baseline.page_load_p99_ms` | [Frontend](#frontend) |
| `agenttel.client.baseline.source` | [Frontend](#frontend) |
| `agenttel.client.correlation.backend_operation` | [Frontend](#frontend) |
| `agenttel.client.correlation.backend_service` | [Frontend](#frontend) |
| `agenttel.client.correlation.backend_trace_id` | [Frontend](#frontend) |
| `agenttel.client.decision.escalation_level` | [Frontend](#frontend) |
| `agenttel.client.decision.fallback_page` | [Frontend](#frontend) |
| `agenttel.client.decision.retry_on_failure` | [Frontend](#frontend) |
| `agenttel.client.decision.runbook_url` | [Frontend](#frontend) |
| `agenttel.client.decision.user_facing` | [Frontend](#frontend) |
| `agenttel.client.interaction.outcome` | [Frontend](#frontend) |
| `agenttel.client.interaction.response_time_ms` | [Frontend](#frontend) |
| `agenttel.client.interaction.target` | [Frontend](#frontend) |
| `agenttel.client.interaction.type` | [Frontend](#frontend) |
| `agenttel.client.journey.name` | [Frontend](#frontend) |
| `agenttel.client.journey.started_at` | [Frontend](#frontend) |
| `agenttel.client.journey.step` | [Frontend](#frontend) |
| `agenttel.client.journey.total_steps` | [Frontend](#frontend) |
| `agenttel.client.page.business_criticality` | [Frontend](#frontend) |
| `agenttel.client.page.route` | [Frontend](#frontend) |
| `agenttel.client.page.title` | [Frontend](#frontend) |
| `agenttel.client.topology.domain` | [Frontend](#frontend) |
| `agenttel.client.topology.team` | [Frontend](#frontend) |
| `agenttel.correlation.change_id` | [Change Correlation](#change-correlation) |
| `agenttel.correlation.confidence` | [Change Correlation](#change-correlation) |
| `agenttel.correlation.likely_cause` | [Change Correlation](#change-correlation) |
| `agenttel.correlation.time_delta_ms` | [Change Correlation](#change-correlation) |
| `agenttel.decision.escalation_level` | [Decisions](#decisions) |
| `agenttel.decision.fallback_available` | [Decisions](#decisions) |
| `agenttel.decision.fallback_description` | [Decisions](#decisions) |
| `agenttel.decision.idempotent` | [Decisions](#decisions) |
| `agenttel.decision.known_issue_id` | [Decisions](#decisions) |
| `agenttel.decision.retryable` | [Decisions](#decisions) |
| `agenttel.decision.retry_after_ms` | [Decisions](#decisions) |
| `agenttel.decision.runbook_url` | [Decisions](#decisions) |
| `agenttel.decision.safe_to_restart` | [Decisions](#decisions) |
| `agenttel.deployment.commit_sha` | [Deployment](#deployment) |
| `agenttel.deployment.id` | [Deployment](#deployment) |
| `agenttel.deployment.previous_version` | [Deployment](#deployment) |
| `agenttel.deployment.strategy` | [Deployment](#deployment) |
| `agenttel.deployment.timestamp` | [Deployment](#deployment) |
| `agenttel.deployment.version` | [Deployment](#deployment) |
| `agenttel.error.category` | [Error Classification](#error-classification) |
| `agenttel.error.dependency` | [Error Classification](#error-classification) |
| `agenttel.error.root_exception` | [Error Classification](#error-classification) |
| `agenttel.genai.cache_hit` | [GenAI](#genai) |
| `agenttel.genai.cost_usd` | [GenAI](#genai) |
| `agenttel.genai.framework` | [GenAI](#genai) |
| `agenttel.genai.guardrail_name` | [GenAI](#genai) |
| `agenttel.genai.guardrail_triggered` | [GenAI](#genai) |
| `agenttel.genai.rag_relevance_score_avg` | [GenAI](#genai) |
| `agenttel.genai.rag_source_count` | [GenAI](#genai) |
| `agenttel.session.id` | [Sessions](#sessions) |
| `agenttel.session.incident_id` | [Sessions](#sessions) |
| `agenttel.severity.anomaly_score` | [Severity](#severity) |
| `agenttel.severity.business_impact` | [Severity](#severity) |
| `agenttel.severity.impact_scope` | [Severity](#severity) |
| `agenttel.severity.pattern` | [Severity](#severity) |
| `agenttel.severity.user_facing` | [Severity](#severity) |
| `agenttel.slo.budget_remaining` | [SLO](#slo) |
| `agenttel.slo.burn_rate` | [SLO](#slo) |
| `agenttel.slo.name` | [SLO](#slo) |
| `agenttel.slo.target` | [SLO](#slo) |
| `agenttel.topology.consumers` | [Topology](#topology) |
| `agenttel.topology.dependencies` | [Topology](#topology) |
| `agenttel.topology.domain` | [Topology](#topology) |
| `agenttel.topology.on_call_channel` | [Topology](#topology) |
| `agenttel.topology.repo_url` | [Topology](#topology) |
| `agenttel.topology.team` | [Topology](#topology) |
| `agenttel.topology.tier` | [Topology](#topology) |
| `gen_ai.operation.name` | [GenAI (OTel Standard)](#standard-otel-genai-attributes) |
| `gen_ai.request.max_tokens` | [GenAI (OTel Standard)](#standard-otel-genai-attributes) |
| `gen_ai.request.model` | [GenAI (OTel Standard)](#standard-otel-genai-attributes) |
| `gen_ai.request.temperature` | [GenAI (OTel Standard)](#standard-otel-genai-attributes) |
| `gen_ai.request.top_p` | [GenAI (OTel Standard)](#standard-otel-genai-attributes) |
| `gen_ai.response.finish_reasons` | [GenAI (OTel Standard)](#standard-otel-genai-attributes) |
| `gen_ai.response.id` | [GenAI (OTel Standard)](#standard-otel-genai-attributes) |
| `gen_ai.response.model` | [GenAI (OTel Standard)](#standard-otel-genai-attributes) |
| `gen_ai.system` | [GenAI (OTel Standard)](#standard-otel-genai-attributes) |
| `gen_ai.usage.input_tokens` | [GenAI (OTel Standard)](#standard-otel-genai-attributes) |
| `gen_ai.usage.output_tokens` | [GenAI (OTel Standard)](#standard-otel-genai-attributes) |

---

## Topology {#topology}

Service identity and dependency graph. Set **once per service** as OTel Resource attributes at startup. These attributes travel with every span exported by the service, giving agents immediate context about ownership, criticality, and the dependency graph without requiring a separate lookup.

> **Set by:** `AgentTelResourceProvider` (OTel SPI `ResourceProvider`) -- runs at SDK initialization, reads from `AgentTelGlobalState` which is populated by `@AgentObservable` annotations, YAML configuration, or programmatic registration via `TopologyRegistry`.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.topology.team` | string | Free-form, e.g. `"payments-platform"` | Agent knows who to page when something breaks |
| `agenttel.topology.tier` | string | `critical`, `standard`, `internal`, `experimental` | Agent prioritizes critical services over internal tooling |
| `agenttel.topology.domain` | string | Free-form, e.g. `"commerce"` | Agent scopes blast radius to the right business domain |
| `agenttel.topology.on_call_channel` | string | Free-form, e.g. `"#payments-oncall"` | Agent knows where to escalate when human intervention is needed |
| `agenttel.topology.repo_url` | string | URL, e.g. `"https://github.com/org/repo"` | Agent can link alerts to source code for faster diagnosis |
| `agenttel.topology.dependencies` | string (JSON) | JSON array of dependency descriptors | Agent understands the upstream dependency graph |
| `agenttel.topology.consumers` | string (JSON) | JSON array of consumer descriptors | Agent understands downstream impact of failures |

### Detailed Reference

#### `agenttel.topology.tier`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelResourceProvider` |
| **Appears on** | Resource attributes |
| **Default** | Not set (attribute absent if not configured) |

**Why:** An AI agent responding to an incident must prioritize. A failure in a `critical` service (user-facing, revenue-impacting) demands an immediate page, while the same failure in an `experimental` service might only warrant a log entry. Without tier information, the agent treats all services equally, leading to alert fatigue or missed critical issues.

**Use case:** Agent receives anomaly alerts from both `payment-service` (tier=`critical`) and `internal-report-generator` (tier=`internal`). It pages on-call for the payment service immediately but only sends a Slack notification for the report generator.

**Example value:** `"critical"`

**Possible values:**

| Tier | Meaning |
|------|---------|
| `critical` | User-facing, revenue-impacting. Pages on-call immediately. |
| `standard` | Important but not immediately revenue-impacting. |
| `internal` | Internal tooling and infrastructure. |
| `experimental` | Non-production or experimental services. |

#### `agenttel.topology.dependencies`

| Property | Value |
|----------|-------|
| **Type** | `string` (JSON-encoded array) |
| **Set by** | `AgentTelResourceProvider` |
| **Appears on** | Resource attributes |
| **Default** | Not set (attribute absent if no dependencies declared) |

**Why:** When an agent detects a failure, it needs to understand whether the root cause is in this service or in a dependency. The dependency graph -- including criticality, timeout configuration, circuit breaker status, and fallback availability -- lets the agent trace failures upstream and determine the correct remediation path.

**Use case:** Agent sees `payment-service` throwing `SocketTimeoutException`. It checks `agenttel.topology.dependencies`, finds that `postgres` is a `required` dependency with `circuit_breaker: true` and `timeout_ms: 5000`. The agent knows to check postgres health and that a circuit breaker should eventually protect the service.

**Example value:**
```json
[
  {
    "name": "postgres",
    "type": "database",
    "criticality": "required",
    "protocol": "postgresql",
    "timeout_ms": 5000,
    "circuit_breaker": true,
    "fallback": "Return cached data",
    "health_endpoint": "/health/postgres"
  }
]
```

#### `agenttel.topology.consumers`

| Property | Value |
|----------|-------|
| **Type** | `string` (JSON-encoded array) |
| **Set by** | `AgentTelResourceProvider` |
| **Appears on** | Resource attributes |
| **Default** | Not set (attribute absent if no consumers declared) |

**Why:** When a service degrades, the agent needs to know which downstream services are affected. Consumer descriptors encode who calls this service, whether those calls are synchronous (blocking the caller) or asynchronous (buffered), and what SLA expectations exist. This lets the agent accurately scope the blast radius of an incident.

**Use case:** Agent detects latency degradation in `pricing-service`. It reads `agenttel.topology.consumers` and finds that `checkout-service` calls it synchronously with a 200ms SLA. The agent knows checkout will be directly impacted and escalates accordingly.

**Example value:**
```json
[
  {
    "name": "checkout-service",
    "consumption_pattern": "synchronous",
    "sla_latency_ms": 200
  }
]
```

---

## Baselines {#baselines}

What "normal" looks like for each operation. Set as **span attributes** on every span for a registered operation. Baselines are the foundation for anomaly detection -- without knowing what "normal" is, an agent cannot determine whether current behavior is problematic.

> **Set by:** `AgentTelSpanProcessor` (static/rolling baselines from `@AgentOperation` annotations or YAML config) and `AgentTelEnrichingSpanExporter` (confidence metrics added at export time).

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.baseline.latency_p50_ms` | double | >= 0, e.g. `45.0` | Agent knows the median expected latency |
| `agenttel.baseline.latency_p99_ms` | double | >= 0, e.g. `200.0` | Agent knows the tail latency expectation |
| `agenttel.baseline.error_rate` | double | 0.0--1.0, e.g. `0.001` | Agent knows the expected background error rate |
| `agenttel.baseline.throughput_rps` | double | >= 0, e.g. `150.0` | Agent knows expected traffic volume |
| `agenttel.baseline.source` | string | `static`, `rolling`, `composite`, `default` | Agent knows how the baseline was determined |
| `agenttel.baseline.updated_at` | string | ISO 8601 timestamp | Agent knows how fresh the baseline is |
| `agenttel.baseline.slo` | string | SLO identifier, e.g. `"payment-availability"` | Agent links the baseline to a specific SLO |
| `agenttel.baseline.sample_count` | long | >= 0, e.g. `250` | Agent gauges statistical significance |
| `agenttel.baseline.confidence` | string | `low`, `medium`, `high` | Agent weighs how much to trust the baseline |

### Detailed Reference

#### `agenttel.baseline.latency_p50_ms`

| Property | Value |
|----------|-------|
| **Type** | `double` |
| **Set by** | `AgentTelSpanProcessor` |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when no baseline is registered for the operation) |

**Why:** The P50 (median) latency is the single most useful baseline metric for an AI agent. It represents what a typical request looks like. When the agent observes a span whose duration is 5x or 10x the P50, it can immediately flag a latency degradation anomaly. Without this number, the agent has no frame of reference for whether 312ms is good, bad, or catastrophic for a given operation.

**Use case:** Agent detects that the current span for `POST /api/payments` took 312ms while `agenttel.baseline.latency_p50_ms` is 45ms. This is a 6.9x deviation, clearly indicating a latency degradation anomaly. The agent checks the dependency graph and finds the root cause is elevated postgres latency.

**Example value:** `45.0`

#### `agenttel.baseline.confidence`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelEnrichingSpanExporter` |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when no rolling baseline data exists) |

**Why:** Not all baselines are equally trustworthy. A rolling baseline computed from 5 observations is far less reliable than one computed from 500. The confidence level tells the agent whether to act decisively on a deviation or to treat it as uncertain. An agent should never page on-call based on a `low`-confidence baseline.

**Use case:** Agent detects a 3x latency deviation on a newly deployed endpoint. It checks `agenttel.baseline.confidence` and finds `low` (only 12 samples). Instead of paging on-call, the agent logs the anomaly and continues collecting data. Once confidence reaches `high`, the same deviation would trigger an immediate escalation.

**Example value:** `"high"`

**Confidence thresholds:**

| Sample Count | Confidence | Meaning |
|-------------|------------|---------|
| < 30 | `low` | Baseline is unreliable -- insufficient data |
| 30--200 | `medium` | Baseline is usable but may not capture edge cases |
| > 200 | `high` | Baseline is statistically significant and reliable |

#### `agenttel.baseline.source`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelSpanProcessor` |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when no baseline is registered) |

**Why:** An agent's response should vary depending on how the baseline was determined. A `static` baseline from configuration reflects an intentional SLA target. A `rolling` baseline computed from live traffic reflects actual behavior (which may have drifted). A `default` baseline is a system-provided fallback with minimal confidence. Knowing the source lets the agent calibrate its anomaly detection thresholds appropriately.

**Use case:** Agent detects elevated latency. The baseline source is `rolling`, meaning it was computed from recent traffic. The agent knows this baseline adapts over time and checks the `updated_at` timestamp to ensure it is fresh enough to be meaningful.

**Example value:** `"static"`

**Possible values:**

| Source | Meaning |
|--------|---------|
| `static` | From `@AgentOperation` annotation or YAML configuration file |
| `rolling` | Computed from a sliding window of observed traffic |
| `composite` | Static baseline with rolling fallback for unset fields |
| `default` | System default when no baseline is available |

---

## Decisions {#decisions}

What an AI agent is permitted and equipped to do when a problem occurs. Set as **span attributes** from `@AgentOperation` annotations or YAML configuration. Decision attributes encode human operator intent -- they are the guardrails that prevent an agent from taking harmful actions.

> **Set by:** `AgentTelSpanProcessor` -- reads from `OperationContextRegistry`, which is populated by `@AgentOperation` annotations (scanned by `AgentTelAnnotationBeanPostProcessor` in Spring Boot) or YAML config (loaded by `AgentTelConfigLoader` in the javaagent extension).

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.decision.retryable` | boolean | `true` / `false` | Agent knows if retrying the operation is safe |
| `agenttel.decision.retry_after_ms` | long | >= 0, e.g. `1000` | Agent knows how long to wait before retrying |
| `agenttel.decision.idempotent` | boolean | `true` / `false` | Agent knows if duplicate calls are safe |
| `agenttel.decision.fallback_available` | boolean | `true` / `false` | Agent knows an alternative path exists |
| `agenttel.decision.fallback_description` | string | Free-form, e.g. `"Return cached pricing"` | Agent knows what the fallback does |
| `agenttel.decision.runbook_url` | string | URL | Agent can reference operational documentation |
| `agenttel.decision.escalation_level` | string | `auto_resolve`, `notify_team`, `page_oncall`, `incident_commander` | Agent knows the correct escalation path |
| `agenttel.decision.known_issue_id` | string | Issue ID, e.g. `"JIRA-1234"` | Agent links the problem to a known issue |
| `agenttel.decision.safe_to_restart` | boolean | `true` / `false` | Agent knows if restarting the service is safe |

### Detailed Reference

#### `agenttel.decision.retryable`

| Property | Value |
|----------|-------|
| **Type** | `boolean` |
| **Set by** | `AgentTelSpanProcessor` |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent -- agent should assume not retryable) |

**Why:** Retrying a failed operation is one of the most common automated remediation actions, but it is also one of the most dangerous. Retrying a non-idempotent payment operation could charge a customer twice. This attribute encodes human operator knowledge about whether retry is safe for each specific operation.

**Use case:** Agent detects a `dependency_timeout` error on `POST /api/payments`. It checks `agenttel.decision.retryable` and finds `false`. Even though the error is transient, the agent does not retry because the operation is not marked as safe to retry. Instead, it follows the escalation path.

**Example value:** `true`

#### `agenttel.decision.escalation_level`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelSpanProcessor` |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent -- agent should default to `notify_team`) |

**Why:** Different operations warrant different levels of human involvement when they fail. A background data sync job might be safe for the agent to handle autonomously, while a payment processing failure requires an immediate page to the on-call engineer. The escalation level encodes this operational judgment so the agent responds proportionally.

**Use case:** Agent detects cascading failures in the payment service. It checks `agenttel.decision.escalation_level` and finds `page_oncall`. It immediately pages the on-call engineer via the channel specified in `agenttel.topology.on_call_channel`, rather than attempting autonomous remediation.

**Example value:** `"page_oncall"`

**Possible values:**

| Level | Meaning |
|-------|---------|
| `auto_resolve` | Agent can handle autonomously without human involvement |
| `notify_team` | Send asynchronous notification to the owning team |
| `page_oncall` | Page the on-call engineer immediately |
| `incident_commander` | Escalate to incident management process |

#### `agenttel.decision.fallback_description`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelSpanProcessor` |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when no fallback is described) |

**Why:** When `agenttel.decision.fallback_available` is `true`, the agent needs to know what the fallback actually does so it can decide whether activating it is appropriate for the current failure mode. A fallback that returns cached data is suitable for a dependency timeout but not for a data corruption issue.

**Use case:** Agent detects that the pricing service dependency is down. It checks `agenttel.decision.fallback_available` (true) and reads `agenttel.decision.fallback_description`: "Return cached pricing from Redis, stale up to 5 minutes." The agent activates the fallback and notifies the team that cached pricing is being served.

**Example value:** `"Return cached pricing from Redis, stale up to 5 minutes"`

---

## Anomaly {#anomaly}

Real-time deviation detection results. Set as **span attributes** by the `AgentTelSpanProcessor` when a span's behavior deviates significantly from the registered baseline. Anomaly attributes are only present on spans where anomalous behavior was detected -- their absence means the span is behaving normally.

> **Set by:** `AgentTelSpanProcessor` via the `AnomalyDetector` and `PatternMatcher` components -- runs during `onEnd()` span processing, comparing observed behavior against baselines from `OperationContextRegistry`.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.anomaly.detected` | boolean | `true` / `false` | Agent knows this span is anomalous |
| `agenttel.anomaly.pattern` | string | `cascade_failure`, `latency_degradation`, `error_rate_spike`, `memory_leak`, `thundering_herd`, `cold_start` | Agent knows the type of incident |
| `agenttel.anomaly.score` | double | 0.0--1.0 | Agent gauges the severity of the anomaly |
| `agenttel.anomaly.latency_z_score` | double | Any positive value, typically 0--10+ | Agent measures how many standard deviations from normal |

### Detailed Reference

#### `agenttel.anomaly.pattern`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelSpanProcessor` (via `PatternMatcher`) |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when no pattern is detected) |

**Why:** Knowing that something is anomalous is necessary but insufficient. An agent needs to know *what kind* of anomaly it is to take the right action. A `cascade_failure` requires checking multiple dependencies, a `memory_leak` requires restarting instances, and a `cold_start` requires patience. The pattern classification maps directly to different remediation playbooks.

**Use case:** Agent sees `agenttel.anomaly.pattern` = `cascade_failure` on the payment service. It checks `agenttel.topology.dependencies` and finds that 3 of 4 downstream dependencies are returning errors. The agent identifies the common upstream cause (a failing load balancer) and creates an incident linking all affected services.

**Example value:** `"cascade_failure"`

**Pattern detection methods:**

| Pattern | Detection | Typical Remediation |
|---------|-----------|-------------------|
| `cascade_failure` | 3+ dependencies with errors in recent window | Identify common upstream cause, circuit break |
| `latency_degradation` | Current latency > 2x rolling P50 | Check dependency latency, scale up |
| `error_rate_spike` | Recent error rate > 5x baseline | Check recent deployments, rollback if needed |
| `memory_leak` | Positive slope in latency linear regression | Restart instances, investigate heap usage |
| `thundering_herd` | Traffic burst exceeding normal patterns | Rate limit, shed load, scale out |
| `cold_start` | High latency with low request count | Wait for warm-up, pre-warm caches |

#### `agenttel.anomaly.score`

| Property | Value |
|----------|-------|
| **Type** | `double` |
| **Set by** | `AgentTelSpanProcessor` (via `AnomalyDetector`) |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when no anomaly is detected) |

**Why:** The anomaly score provides a normalized severity metric (0.0 to 1.0) that lets agents compare anomalies across different operations and services. A score of 0.3 might warrant monitoring, while 0.9 demands immediate action. This score feeds into the severity assessment and business impact calculation.

**Use case:** Agent receives anomaly alerts from two services simultaneously. `payment-service` has `agenttel.anomaly.score` = 0.92 and `notification-service` has score = 0.35. The agent triages the payment service first because the higher score indicates a more severe deviation from normal behavior.

**Example value:** `0.85`

---

## Error Classification {#error-classification}

Structured error categorization that tells agents *why* a span failed, not just *that* it failed. Set as **span attributes** at export time for spans with error status.

> **Set by:** `AgentTelEnrichingSpanExporter` (via `ErrorClassifier`) -- runs during span export, analyzes exception types, HTTP status codes, and exception messages to classify errors.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.error.category` | string | `dependency_timeout`, `connection_error`, `code_bug`, `rate_limited`, `auth_failure`, `resource_exhaustion`, `data_validation`, `unknown` | Agent knows the failure class and appropriate response |
| `agenttel.error.root_exception` | string | Java exception class name, e.g. `"java.net.SocketTimeoutException"` | Agent classifies the root cause at the code level |
| `agenttel.error.dependency` | string | Dependency name, e.g. `"postgres"` | Agent knows which dependency caused the failure |

### Detailed Reference

#### `agenttel.error.category`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelEnrichingSpanExporter` (via `ErrorClassifier`) |
| **Appears on** | Span attributes |
| **Default** | `"unknown"` (set on all error spans; defaults to `unknown` when classification rules do not match) |

**Why:** Standard OTel error status tells the agent that a span failed, but the same "error" status covers both a `NullPointerException` (code bug, do not retry) and a `SocketTimeoutException` (transient dependency issue, retry is appropriate). Error classification maps raw exceptions to actionable categories, each with a distinct remediation strategy.

**Use case:** Agent sees error spans on `POST /api/payments`. It reads `agenttel.error.category` = `dependency_timeout` and `agenttel.error.dependency` = `postgres`. Instead of investigating application code, the agent checks postgres health, finds connection pool exhaustion, and triggers a scaling action.

**Example value:** `"dependency_timeout"`

**Classification rules:**

| Category | Triggering Conditions | Agent Action |
|----------|----------------------|--------------|
| `dependency_timeout` | Exception contains `Timeout` / `SocketTimeout` | Retry with backoff, check dependency health |
| `connection_error` | Exception contains `Connection` / `ConnectException` | Check dependency availability, circuit break |
| `code_bug` | `NullPointerException`, `ClassCastException`, `IndexOutOfBoundsException`, `IllegalStateException` | Do not retry -- needs code fix |
| `rate_limited` | HTTP 429 | Back off, reduce traffic, request quota increase |
| `auth_failure` | HTTP 401 / 403 | Check credentials/tokens, do not retry |
| `resource_exhaustion` | `OutOfMemoryError`, `StackOverflowError` | Scale up, restart instances |
| `data_validation` | HTTP 400 / 422, `ValidationException`, `IllegalArgumentException` | Do not retry -- fix input |
| `unknown` | Everything else | Investigate manually |

#### `agenttel.error.root_exception`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelEnrichingSpanExporter` (via `ErrorClassifier`) |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when no exception is recorded on the span) |

**Why:** While `agenttel.error.category` provides high-level classification, the root exception class name gives agents the precision to match against known issues, search issue trackers, and correlate with specific code paths. It records the deepest cause in the exception chain, stripping away wrapper exceptions.

**Use case:** Agent sees `agenttel.error.root_exception` = `org.postgresql.util.PSQLException` and cross-references it with `agenttel.decision.known_issue_id` = `"JIRA-5678"`. It finds the known issue is a connection pool sizing bug with a documented workaround and applies the fix automatically.

**Example value:** `"java.net.SocketTimeoutException"`

---

## Causality {#causality}

Root cause analysis attributes that help agents trace failures back to their origin. Set as **span attributes** at export time.

> **Set by:** `AgentTelEnrichingSpanExporter` (via `CausalityTracker` and `OperationDependencyTracker`) -- runs during span export, correlates error spans with dependency health data and recent events.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.cause.hint` | string | Human-readable description | Agent gets a concise root cause explanation |
| `agenttel.cause.category` | string | `dependency`, `code`, `infrastructure`, `traffic`, `unknown` | Agent categorizes the root cause domain |
| `agenttel.cause.dependency` | string | Dependency name | Agent identifies the specific failing dependency |
| `agenttel.cause.correlated_span_id` | string | Span ID (hex) | Agent traces to the root cause span |
| `agenttel.cause.correlated_event_id` | string | Event ID | Agent links to the triggering event |
| `agenttel.cause.started_at` | string | ISO 8601 timestamp | Agent knows when the issue first appeared |

### Detailed Reference

#### `agenttel.cause.hint`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelEnrichingSpanExporter` (via `CausalityTracker`) |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when causality cannot be determined) |

**Why:** The cause hint is a human-readable, agent-consumable explanation of why a span failed. It synthesizes information from error classification, dependency health, and recent events into a single actionable sentence. This is the attribute agents include in incident summaries and escalation messages.

**Use case:** Agent constructs an incident report and includes the cause hint: "Dependency postgres is unhealthy: Connection refused on host db-primary:5432. First observed 3 minutes ago." This gives the on-call engineer immediate context without needing to dig through logs.

**Example value:** `"Dependency postgres is unhealthy: Connection refused on host db-primary:5432"`

#### `agenttel.cause.category`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelEnrichingSpanExporter` (via `CausalityTracker`) |
| **Appears on** | Span attributes |
| **Default** | `"unknown"` (set when causality analysis runs but cannot determine a specific category) |

**Why:** The cause category tells the agent which domain the root cause belongs to, driving the selection of the appropriate remediation playbook. A `dependency` cause means the agent should investigate upstream services. A `code` cause means no automated fix is possible. An `infrastructure` cause points to compute, network, or storage issues.

**Use case:** Agent sees `agenttel.cause.category` = `infrastructure` combined with `agenttel.anomaly.pattern` = `latency_degradation`. It checks node-level metrics, finds elevated CPU on the host, and triggers an auto-scaling action rather than investigating application code.

**Example value:** `"dependency"`

**Possible values:**

| Category | Meaning |
|----------|---------|
| `dependency` | Failure caused by an upstream dependency |
| `code` | Failure caused by application code (bugs, unhandled cases) |
| `infrastructure` | Failure caused by compute, network, or storage issues |
| `traffic` | Failure caused by traffic patterns (overload, thundering herd) |
| `unknown` | Root cause could not be determined |

---

## Severity {#severity}

Business impact assessment that helps agents prioritize their response. Set as **span attributes** at export time.

> **Set by:** `AgentTelEnrichingSpanExporter` -- synthesizes anomaly scores, service tier, and error status into a business impact assessment.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.severity.anomaly_score` | double | 0.0--1.0 | Agent gauges the overall severity magnitude |
| `agenttel.severity.pattern` | string | Incident pattern name | Agent knows the type of incident for playbook selection |
| `agenttel.severity.impact_scope` | string | `operation_specific`, `service_wide`, `cross_service` | Agent scopes the blast radius |
| `agenttel.severity.business_impact` | string | `critical`, `high`, `medium`, `low` | Agent prioritizes response based on business impact |
| `agenttel.severity.user_facing` | boolean | `true` / `false` | Agent knows if end users are affected |

### Detailed Reference

#### `agenttel.severity.business_impact`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelEnrichingSpanExporter` |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when no anomaly or error is detected) |

**Why:** The business impact level is the single most important triage signal for an agent. It combines anomaly severity with service tier to produce a unified priority. An error on a `critical`-tier service is automatically `high` impact even if the anomaly score is moderate, while the same error on an `internal`-tier service is `low` impact.

**Use case:** Agent is handling three simultaneous alerts. It sorts by `agenttel.severity.business_impact`: the `critical` alert (payment processing down, score > 0.8) gets immediate attention, the `high` alert (error on critical-tier order service) gets queued, and the `low` alert (validation error on internal admin tool) gets logged for later review.

**Example value:** `"critical"`

**Determination rules:**

| Impact | Condition |
|--------|-----------|
| `critical` | Anomaly score > 0.8 |
| `high` | Error on critical-tier service |
| `medium` | Error on standard-tier service or moderate anomaly |
| `low` | Minor anomaly or data validation error |

#### `agenttel.severity.impact_scope`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelEnrichingSpanExporter` |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when no severity assessment is performed) |

**Why:** An issue affecting a single operation requires a different response than one affecting the entire service or multiple services. The impact scope tells the agent whether to investigate narrowly (one endpoint) or broadly (service-wide or cross-service), and whether to coordinate with agents monitoring other services.

**Use case:** Agent sees `agenttel.severity.impact_scope` = `cross_service` on an anomaly in the API gateway. It queries health data for all downstream services, correlates the timing with a recent deployment event, and coordinates a multi-service incident response.

**Example value:** `"service_wide"`

---

## Change Correlation {#change-correlation}

Correlates anomalies with recent changes to help agents identify the probable trigger. Set on **incident context** objects constructed by the agent layer.

> **Set by:** `ChangeCorrelationEngine` in the `agenttel-agent` module -- analyzes recent deployment events, configuration changes, and scaling events against anomaly onset timestamps.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.correlation.likely_cause` | string | `deployment`, `config`, `scaling`, `feature_flag`, `dependency_update` | Agent identifies the probable trigger for the incident |
| `agenttel.correlation.change_id` | string | Change identifier, e.g. `"deploy-v2.1.0"` | Agent links to the specific change for rollback decisions |
| `agenttel.correlation.time_delta_ms` | long | >= 0, e.g. `1800000` | Agent gauges temporal proximity between change and anomaly |
| `agenttel.correlation.confidence` | double | 0.0--1.0 | Agent weighs the strength of the correlation |

### Detailed Reference

#### `agenttel.correlation.likely_cause`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `ChangeCorrelationEngine` |
| **Appears on** | Incident context (used in MCP tool responses and incident reports) |
| **Default** | Not set (attribute absent when no correlated change is found) |

**Why:** The most common cause of production incidents is a recent change. When an agent can automatically correlate an anomaly with a deployment that happened 10 minutes ago, it can suggest or execute a rollback without human investigation. This attribute identifies the type of change most likely responsible.

**Use case:** Agent detects an error rate spike starting 12 minutes ago. `ChangeCorrelationEngine` finds a deployment (`deploy-v2.1.0`) that completed 15 minutes ago with confidence 0.92. The agent recommends rollback to `v2.0.9` and includes the deployment diff link in the incident report.

**Example value:** `"deployment"`

#### `agenttel.correlation.confidence`

| Property | Value |
|----------|-------|
| **Type** | `double` |
| **Set by** | `ChangeCorrelationEngine` |
| **Appears on** | Incident context |
| **Default** | Not set (attribute absent when no correlated change is found) |

**Why:** Not all correlations are meaningful -- a config change 6 hours ago is less likely to be the cause than a deployment 5 minutes ago. The confidence score encodes temporal proximity, change scope, and historical correlation patterns so the agent can decide whether to recommend rollback (high confidence) or just flag the correlation for human review (low confidence).

**Use case:** Agent finds two correlated changes: a deployment 3 minutes before the anomaly (confidence 0.95) and a config change 2 hours before (confidence 0.15). It recommends rolling back the deployment and ignores the config change.

**Example value:** `0.85`

---

## SLO {#slo}

Error budget consumption tracking. Set as **span attributes** when SLOs are registered for the operation.

> **Set by:** `AgentTelSpanProcessor` (via `SloTracker`) -- evaluates each span against registered SLO definitions and computes budget consumption in real time.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.slo.name` | string | SLO identifier, e.g. `"payment-availability"` | Agent tracks which SLO is being measured |
| `agenttel.slo.target` | double | 0.0--1.0, e.g. `0.999` | Agent knows the SLO target to compare against |
| `agenttel.slo.budget_remaining` | double | 0.0--1.0, e.g. `0.85` | Agent knows how much error budget remains |
| `agenttel.slo.burn_rate` | double | >= 0, e.g. `0.15` | Agent detects accelerating budget consumption |

### Detailed Reference

#### `agenttel.slo.budget_remaining`

| Property | Value |
|----------|-------|
| **Type** | `double` |
| **Set by** | `AgentTelSpanProcessor` (via `SloTracker`) |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when no SLO is registered for the operation) |

**Why:** Error budget remaining is the key metric for SLO-driven incident response. When the budget is > 50%, minor anomalies can be monitored. When it drops below 25%, the agent should restrict risky changes. Below 10%, the agent should escalate aggressively and consider freezing deployments. This single number drives a graduated response strategy.

**Use case:** Agent detects intermittent errors on the payment service. It checks `agenttel.slo.budget_remaining` = 0.12 (12% remaining) and `agenttel.slo.burn_rate` = 0.78 (consuming budget at 78% of the sustainable rate). The agent emits a critical SLO budget alert and recommends pausing non-essential deployments until the error rate stabilizes.

**Example value:** `0.85`

#### `agenttel.slo.burn_rate`

| Property | Value |
|----------|-------|
| **Type** | `double` |
| **Set by** | `AgentTelSpanProcessor` (via `SloTracker`) |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when no SLO is registered for the operation) |

**Why:** While `budget_remaining` is a snapshot, `burn_rate` shows the velocity of budget consumption. A burn rate of 1.0 means the budget is being consumed at exactly the sustainable rate. A burn rate of 10.0 means the budget will be exhausted 10x faster than expected. This lets agents predict budget exhaustion and escalate proactively before the budget runs out.

**Use case:** Agent sees `agenttel.slo.burn_rate` = 5.2, meaning the error budget is being consumed 5x faster than sustainable. Even though `budget_remaining` is still 0.45 (healthy), the agent projects budget exhaustion within hours and proactively notifies the team.

**Example value:** `0.15`

---

## Deployment {#deployment}

Deployment tracking attributes for change correlation. Set on **span events** and **startup events** by the deployment event emitter.

> **Set by:** `DeploymentEventEmitter` (in `agenttel-core`) and `AgentTelDeploymentEventListener` (in `agenttel-spring-boot-starter`) -- emits a structured event at service startup containing deployment metadata.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.deployment.id` | string | Deployment ID, e.g. `"deploy-20240115-1430"` | Agent tracks individual deployments |
| `agenttel.deployment.version` | string | Version string, e.g. `"2.1.0"` | Agent knows the current running version |
| `agenttel.deployment.commit_sha` | string | Git SHA, e.g. `"a1b2c3d4"` | Agent can link to exact code changes |
| `agenttel.deployment.previous_version` | string | Version string, e.g. `"2.0.9"` | Agent knows what to rollback to |
| `agenttel.deployment.strategy` | string | `blue-green`, `canary`, `rolling` | Agent understands the deployment mechanism |
| `agenttel.deployment.timestamp` | string | ISO 8601, e.g. `"2024-01-15T14:30:00Z"` | Agent knows when the deployment happened |

### Detailed Reference

#### `agenttel.deployment.previous_version`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `DeploymentEventEmitter` |
| **Appears on** | Span events (deployment event) |
| **Default** | Not set (attribute absent on first deployment or when previous version is unknown) |

**Why:** When an agent decides to recommend or execute a rollback, it needs to know which version to rollback *to*. Without `previous_version`, the agent can identify that the current deployment is problematic but cannot specify the safe target version, requiring human intervention to determine the rollback target.

**Use case:** Agent correlates a latency spike with the deployment of version `2.1.0` (deployed 8 minutes ago). It reads `agenttel.deployment.previous_version` = `"2.0.9"` and recommends: "Rollback payment-service from v2.1.0 to v2.0.9 -- latency degradation correlated with deployment (confidence: 0.93)."

**Example value:** `"2.0.9"`

#### `agenttel.deployment.strategy`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `DeploymentEventEmitter` |
| **Appears on** | Span events (deployment event) |
| **Default** | Not set (attribute absent when deployment strategy is not configured) |

**Why:** The deployment strategy determines the blast radius of a bad deployment and the rollback mechanism. A `canary` deployment means only a fraction of traffic is affected -- the agent can halt the canary rather than doing a full rollback. A `blue-green` deployment allows instant rollback by switching traffic. A `rolling` deployment may require waiting for all instances to be replaced.

**Use case:** Agent detects errors in a canary deployment (strategy=`canary`). Instead of triggering a full rollback, it halts canary promotion and routes all traffic back to the stable version, minimizing user impact.

**Example value:** `"canary"`

---

## GenAI {#genai}

Attributes for AI/ML workload observability. Combines the standard OTel `gen_ai.*` namespace with AgentTel extensions in the `agenttel.genai.*` namespace.

> **Set by:** GenAI instrumentation wrappers in the `agenttel-genai` module -- `TracingChatLanguageModel` (LangChain4j), `SpringAiSpanEnricher` (Spring AI), `TracingAnthropicClient`, `TracingOpenAIClient`, `BedrockTracing`, and `CostEnrichingSpanExporter`.

### AgentTel GenAI Extensions

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.genai.framework` | string | `langchain4j`, `spring_ai`, `anthropic`, `openai`, `bedrock` | Agent knows the instrumentation source framework |
| `agenttel.genai.cost_usd` | double | >= 0, e.g. `0.000795` | Agent tracks per-request cost for budget monitoring |
| `agenttel.genai.rag_source_count` | long | >= 0, e.g. `5` | Agent monitors RAG retrieval volume |
| `agenttel.genai.rag_relevance_score_avg` | double | 0.0--1.0, e.g. `0.87` | Agent assesses retrieval quality |
| `agenttel.genai.guardrail_triggered` | boolean | `true` / `false` | Agent monitors safety guardrail activations |
| `agenttel.genai.guardrail_name` | string | Guardrail identifier, e.g. `"pii_filter"` | Agent knows which guardrail was triggered |
| `agenttel.genai.cache_hit` | boolean | `true` / `false` | Agent tracks cache efficiency for cost optimization |

### Standard OTel GenAI Attributes

These follow the emerging OTel GenAI semantic conventions (`gen_ai.*` namespace). AgentTel populates them for all supported providers.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `gen_ai.operation.name` | string | `chat`, `text_completion`, `embeddings` | Agent identifies the GenAI operation type |
| `gen_ai.system` | string | `openai`, `anthropic`, `aws_bedrock` | Agent knows which provider is being called |
| `gen_ai.request.model` | string | Model identifier, e.g. `"gpt-4"` | Agent knows which model was requested |
| `gen_ai.response.model` | string | Model identifier, e.g. `"gpt-4-0125-preview"` | Agent knows the actual model that responded |
| `gen_ai.usage.input_tokens` | long | >= 0 | Agent monitors input token consumption |
| `gen_ai.usage.output_tokens` | long | >= 0 | Agent monitors output token consumption |
| `gen_ai.request.temperature` | double | 0.0--2.0 | Agent sees the sampling temperature used |
| `gen_ai.request.max_tokens` | long | >= 0 | Agent sees the max output token limit |
| `gen_ai.request.top_p` | double | 0.0--1.0 | Agent sees the nucleus sampling parameter |
| `gen_ai.response.id` | string | Response identifier | Agent correlates responses across retries |
| `gen_ai.response.finish_reasons` | string[] | `stop`, `length`, `tool_calls` | Agent knows why generation stopped |

### Detailed Reference

#### `agenttel.genai.cost_usd`

| Property | Value |
|----------|-------|
| **Type** | `double` |
| **Set by** | `CostEnrichingSpanExporter` (via `ModelCostCalculator`) |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when model pricing is not configured) |

**Why:** GenAI API costs can spike unexpectedly -- a prompt injection or retry loop can burn through budget in minutes. By attaching per-request cost to every span, agents can detect cost anomalies in real time, enforce budget limits, and alert when spending exceeds thresholds.

**Use case:** Agent aggregates `agenttel.genai.cost_usd` over a 5-minute window and detects that spending is 10x the rolling average. It investigates and finds a retry loop caused by a downstream timeout, halts the retry, and reports the cost impact ($47.30 in unnecessary spending).

**Example value:** `0.000795`

#### `agenttel.genai.rag_relevance_score_avg`

| Property | Value |
|----------|-------|
| **Type** | `double` |
| **Set by** | `TracingContentRetriever` (LangChain4j) or `SpringAiSpanEnricher` |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when RAG is not used or relevance scores are not available) |

**Why:** Low retrieval quality directly impacts LLM response quality. When the average relevance score drops below a threshold, it means the retrieval pipeline is returning irrelevant documents, leading to hallucinations or poor answers. Agents can detect this degradation and alert on retrieval quality before users notice response quality issues.

**Use case:** Agent monitors `agenttel.genai.rag_relevance_score_avg` across requests to a customer support chatbot. It detects a drop from 0.87 to 0.42 after a vector index rebuild. The agent alerts the ML engineering team that retrieval quality has degraded and the index rebuild may need to be reverted.

**Example value:** `0.87`

#### `gen_ai.response.finish_reasons`

| Property | Value |
|----------|-------|
| **Type** | `string[]` (string array) |
| **Set by** | GenAI instrumentation wrappers (`TracingChatLanguageModel`, `TracingAnthropicClient`, etc.) |
| **Appears on** | Span attributes |
| **Default** | Not set (attribute absent when the provider does not return finish reasons) |

**Why:** The finish reason tells the agent why the LLM stopped generating. A `stop` finish is normal. A `length` finish means the output was truncated at `max_tokens`, which may indicate the response is incomplete and the user got a degraded experience. A `tool_calls` finish means the LLM wants to invoke a tool. Agents can detect elevated `length` finishes as a quality degradation signal.

**Use case:** Agent detects that 40% of responses from the code generation endpoint finish with reason `length`. It recommends increasing `max_tokens` from 1024 to 2048 for that operation and estimates the cost impact using `agenttel.genai.cost_usd` data.

**Example value:** `["stop"]`

---

## Agent Identity {#agent-identity}

Tracks which AI agent performed each action. Set on **action spans** created when agents interact with the system through MCP tools.

> **Set by:** `AgentActionTracker` in the `agenttel-agent` module -- wraps each agent action (MCP tool invocation, remediation execution) in a span with identity attributes.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.agent.id` | string | Agent identifier, e.g. `"diag-agent-1"` | Track which agent performed each action |
| `agenttel.agent.role` | string | `observer`, `diagnostician`, `remediator`, `admin` | Track the agent's role and permission level |
| `agenttel.agent.session_id` | string | Session UUID | Link actions to a collaboration session |

### Detailed Reference

#### `agenttel.agent.role`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentActionTracker` |
| **Appears on** | Span attributes (on agent action spans) |
| **Default** | Not set (attribute absent when agent identity is not registered) |

**Why:** Different agents have different permission levels. An `observer` agent can read telemetry but cannot take remediation actions. A `remediator` can execute approved playbooks. An `admin` can perform any action. By recording the role on each span, the system maintains an audit trail of who did what, and the `ToolPermissionRegistry` can enforce role-based access control on MCP tools.

**Use case:** Audit review reveals that a `remediator`-role agent restarted a payment service instance during an incident. The role on the span confirms the agent was authorized for this action class. If an `observer`-role agent had attempted the same action, the `ToolPermissionRegistry` would have denied it.

**Example value:** `"diagnostician"`

**Predefined roles:**

| Role | Permissions |
|------|-------------|
| `observer` | Read-only access to telemetry data, health status, and incident context |
| `diagnostician` | Observer permissions plus ability to run diagnostic queries and trace analysis |
| `remediator` | Diagnostician permissions plus ability to execute approved remediation playbooks |
| `admin` | Full access to all tools including service restarts and configuration changes |

---

## Sessions {#sessions}

Shared incident session tracking for multi-agent collaboration. Set on **session-related operations** managed by the `SessionManager`.

> **Set by:** `SessionManager` and `IncidentSession` in the `agenttel-agent` module -- creates and manages collaborative sessions where multiple agents can work on the same incident.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.session.id` | string | UUID, e.g. `"a3f2b1c4-d5e6-7890-abcd-ef1234567890"` | Uniquely identifies the collaboration session |
| `agenttel.session.incident_id` | string | Incident identifier, e.g. `"inc-payment-spike-20240115"` | Links the session to a specific incident |

### Detailed Reference

#### `agenttel.session.id`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `SessionManager` |
| **Appears on** | Span attributes (on session-scoped operations) |
| **Default** | Not set (attribute absent when no session is active) |

**Why:** When multiple agents collaborate on an incident, they need a shared context. The session ID links all agent actions, diagnostic queries, and remediation steps to the same incident investigation. This enables post-incident review of the full agent collaboration timeline and prevents duplicate work.

**Use case:** A diagnostician agent and a remediator agent are both investigating a payment service outage. Both record `agenttel.session.id` = `"a3f2b1c4"` on their spans. In post-incident review, the team can trace the complete investigation: the diagnostician identified postgres as the root cause at T+2min, and the remediator executed a connection pool scaling action at T+4min.

**Example value:** `"a3f2b1c4-d5e6-7890-abcd-ef1234567890"`

---

## Circuit Breaker {#circuit-breaker}

Circuit breaker state change tracking. Set on **event attributes** when circuit breakers transition between states.

> **Set by:** `AgentTelEventEmitter` in the `agenttel-core` module -- emits structured events when circuit breaker state changes are recorded.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.circuit_breaker.name` | string | Breaker identifier, e.g. `"postgres-breaker"` | Agent identifies which circuit breaker changed |
| `agenttel.circuit_breaker.previous_state` | string | `closed`, `open`, `half_open` | Agent knows the state before the transition |
| `agenttel.circuit_breaker.new_state` | string | `closed`, `open`, `half_open` | Agent knows the current state |
| `agenttel.circuit_breaker.failure_count` | long | >= 0 | Agent knows how many failures triggered the transition |
| `agenttel.circuit_breaker.dependency` | string | Dependency name, e.g. `"postgres"` | Agent links the breaker to a specific dependency |

### Detailed Reference

#### `agenttel.circuit_breaker.new_state`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `AgentTelEventEmitter` |
| **Appears on** | Event attributes (on circuit breaker state change events) |
| **Default** | Not set (only present on circuit breaker events) |

**Why:** Circuit breaker state transitions are critical operational signals. When a breaker opens, it means a dependency has exceeded its failure threshold and the service is now returning fallback responses (or failing fast). When it transitions to half-open, the service is testing whether the dependency has recovered. The agent needs to know these transitions to understand service behavior and correlate them with anomalies.

**Use case:** Agent sees `agenttel.circuit_breaker.new_state` = `open` for the postgres breaker. It correlates this with the `dependency_timeout` errors on `POST /api/payments` and confirms that the circuit breaker is protecting the service. The agent monitors for the `half_open` transition to verify recovery.

**Example value:** `"open"`

**State machine:**

| State | Meaning |
|-------|---------|
| `closed` | Normal operation -- requests are forwarded to the dependency |
| `open` | Failure threshold exceeded -- requests are short-circuited (fallback or fail-fast) |
| `half_open` | Testing recovery -- a limited number of requests are forwarded to check if the dependency has recovered |

---

## Frontend {#frontend}

Client-side telemetry from the `@agenttel/web` browser SDK. These attributes provide full-stack observability by tracking user-facing behavior, client-side anomalies, and cross-stack trace correlation.

Frontend attributes use the `agenttel.client.*` namespace to distinguish them from server-side attributes.

> **Set by:** `@agenttel/web` browser SDK -- instruments page loads, API calls, user interactions, and user journeys. Exports spans via OTLP HTTP to any OTel-compatible collector.

### Resource Attributes

Set once per browser application at SDK initialization.

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.client.app.name` | string | App name, e.g. `"checkout-web"` | Agent identifies the frontend application |
| `agenttel.client.app.version` | string | Semver, e.g. `"1.0.0"` | Agent tracks frontend version for change correlation |
| `agenttel.client.app.platform` | string | `browser` | Agent knows the runtime platform |
| `agenttel.client.app.environment` | string | `production`, `staging`, etc. | Agent filters by environment |
| `agenttel.client.topology.team` | string | Team name, e.g. `"checkout-frontend"` | Agent routes frontend issues to the right team |
| `agenttel.client.topology.domain` | string | Business domain, e.g. `"commerce"` | Agent groups frontend with related backend services |

### Page and Route Attributes

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.client.page.route` | string | Route pattern, e.g. `"/checkout/:step"` | Agent groups spans by route for baseline comparison |
| `agenttel.client.page.title` | string | Document title, e.g. `"Checkout - Payment"` | Agent includes human-readable page context in alerts |
| `agenttel.client.page.business_criticality` | string | `revenue`, `engagement`, `internal` | Agent prioritizes revenue-impacting pages |

### Baseline Attributes

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.client.baseline.page_load_p50_ms` | double | >= 0, e.g. `800.0` | Agent knows expected page load time |
| `agenttel.client.baseline.page_load_p99_ms` | double | >= 0, e.g. `2000.0` | Agent knows tail page load expectation |
| `agenttel.client.baseline.api_call_p50_ms` | double | >= 0, e.g. `300.0` | Agent knows expected API response time from the browser |
| `agenttel.client.baseline.interaction_error_rate` | double | 0.0--1.0, e.g. `0.01` | Agent knows expected client-side error rate |
| `agenttel.client.baseline.source` | string | `static`, `rolling` | Agent knows how the baseline was determined |

### Decision Attributes

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.client.decision.escalation_level` | string | `auto_resolve`, `notify_team`, `page_oncall`, `incident_commander` | Agent knows the client-side escalation path |
| `agenttel.client.decision.runbook_url` | string | URL | Agent references frontend operational docs |
| `agenttel.client.decision.fallback_page` | string | Route path, e.g. `"/maintenance"` | Agent knows where to redirect on failure |
| `agenttel.client.decision.retry_on_failure` | boolean | `true` / `false` | Agent knows if page reload is safe |
| `agenttel.client.decision.user_facing` | boolean | `true` / `false` | Agent confirms this affects real users |

### Anomaly Attributes

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.client.anomaly.detected` | boolean | `true` / `false` | Agent knows a client-side anomaly was detected |
| `agenttel.client.anomaly.pattern` | string | `rage_click`, `api_failure_cascade`, `slow_page_load`, `error_loop`, `funnel_dropoff` | Agent knows the type of user-facing issue |
| `agenttel.client.anomaly.score` | double | 0.0--1.0 | Agent gauges client-side anomaly severity |

**Client-side anomaly patterns:**

| Pattern | Detection | Impact |
|---------|-----------|--------|
| `rage_click` | N+ clicks on same element within time window | User frustration -- UI is unresponsive |
| `api_failure_cascade` | N+ API failures within time window | Backend instability visible to user |
| `slow_page_load` | Load time exceeds baseline by multiplier | Performance degradation on route |
| `error_loop` | N+ errors on same route within time window | Repeating failure preventing user progress |
| `funnel_dropoff` | Journey abandonment above baseline | User journey failing at specific step |

### Journey Attributes

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.client.journey.name` | string | Journey name, e.g. `"checkout"` | Agent tracks critical user journeys |
| `agenttel.client.journey.step` | int | 0-based step index | Agent knows which step the user is on |
| `agenttel.client.journey.total_steps` | int | Total steps in journey | Agent knows journey completion progress |
| `agenttel.client.journey.started_at` | string | ISO 8601 timestamp | Agent measures total journey duration |

### Interaction Attributes

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.client.interaction.type` | string | `click`, `submit`, `custom` | Agent categorizes user interaction type |
| `agenttel.client.interaction.target` | string | Element identifier, e.g. `"button#submit-payment"` | Agent identifies the UI element involved |
| `agenttel.client.interaction.outcome` | string | `success`, `error` | Agent knows if the interaction succeeded |
| `agenttel.client.interaction.response_time_ms` | double | >= 0 | Agent measures interaction responsiveness |

### Correlation Attributes

| Attribute | Type | Possible Values | Why an Agent Needs This |
|-----------|------|----------------|------------------------|
| `agenttel.client.correlation.backend_trace_id` | string | 32-char hex trace ID | Agent links browser spans to backend traces |
| `agenttel.client.correlation.backend_service` | string | Service name | Agent knows which backend service handled the request |
| `agenttel.client.correlation.backend_operation` | string | Operation name | Agent traces to the specific backend operation |

### Detailed Reference

#### `agenttel.client.anomaly.pattern`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `@agenttel/web` browser SDK (anomaly detector) |
| **Appears on** | Span attributes (on client-side spans) |
| **Default** | Not set (attribute absent when no anomaly is detected) |

**Why:** Client-side anomalies like rage clicks and error loops are signals of user frustration that backend metrics may not capture. A backend service can return 200 OK while the JavaScript rendering is broken, leaving users unable to complete their task. Client-side pattern detection catches these user-facing issues that would otherwise go unnoticed.

**Use case:** Agent detects `agenttel.client.anomaly.pattern` = `rage_click` on the checkout page's "Submit Payment" button. It checks `agenttel.client.correlation.backend_trace_id` and finds the backend call succeeded (200 OK, 45ms). The issue is a frontend rendering bug where the button appears clickable but the form submission is blocked by a JavaScript error. The agent alerts the frontend team with the specific element identifier.

**Example value:** `"rage_click"`

#### `agenttel.client.correlation.backend_trace_id`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `@agenttel/web` browser SDK (from `traceparent` response header or server-timing header) |
| **Appears on** | Span attributes (on client-side API call spans) |
| **Default** | Not set (attribute absent when the backend does not return trace context in response headers) |

**Why:** Full-stack incident investigation requires linking what the user sees in the browser to what happened on the server. The backend trace ID lets an agent follow a user's API call from the browser, through the API gateway, to the backend service, and into its dependencies. Without this link, frontend and backend incidents are investigated in isolation, missing the full picture.

**Use case:** Agent detects slow page load on the checkout page. It reads `agenttel.client.correlation.backend_trace_id` = `"abc123def456"` and queries the backend tracing system. It finds that the backend span for `POST /api/payments` shows a `dependency_timeout` on the fraud detection service, confirming the slow page load is caused by a backend dependency issue, not a frontend problem.

**Example value:** `"abc123def456789012345678abcdef01"`

#### `agenttel.client.page.business_criticality`

| Property | Value |
|----------|-------|
| **Type** | `string` |
| **Set by** | `@agenttel/web` browser SDK (from route configuration) |
| **Appears on** | Span attributes (on page-scoped spans) |
| **Default** | Not set (attribute absent when business criticality is not configured for the route) |

**Why:** Not all pages are equally important. The checkout page directly impacts revenue, while the blog page impacts engagement but not transactions. Business criticality lets the agent prioritize frontend issues the same way `agenttel.topology.tier` prioritizes backend services -- revenue-impacting pages get immediate attention.

**Use case:** Agent receives anomaly alerts from both the checkout page (criticality=`revenue`) and the help center page (criticality=`engagement`). It pages on-call for the checkout page issue immediately because errors there directly lose revenue, while sending a Slack notification for the help center issue.

**Example value:** `"revenue"`

---

## Java Constant Reference

All backend attribute keys are defined as typed `AttributeKey<T>` constants in `io.agenttel.api.attributes.AgentTelAttributes`. GenAI attributes have additional constants in `io.agenttel.genai.conventions.AgentTelGenAiAttributes` and `io.agenttel.genai.conventions.GenAiAttributes`.

Using these constants instead of raw strings provides compile-time type safety:

```java
import io.agenttel.api.attributes.AgentTelAttributes;

// Type-safe attribute access
Double p50 = span.getAttribute(AgentTelAttributes.BASELINE_LATENCY_P50_MS);  // Double
String tier = span.getAttribute(AgentTelAttributes.TOPOLOGY_TIER);           // String
Boolean retryable = span.getAttribute(AgentTelAttributes.DECISION_RETRYABLE); // Boolean
Long retryAfter = span.getAttribute(AgentTelAttributes.DECISION_RETRY_AFTER_MS); // Long
```

### Constant Naming Convention

The constant name follows the pattern: `CATEGORY_FIELD_NAME`

| Namespace | Constant Prefix | Example |
|-----------|----------------|---------|
| `agenttel.topology.*` | `TOPOLOGY_` | `TOPOLOGY_TIER` |
| `agenttel.baseline.*` | `BASELINE_` | `BASELINE_LATENCY_P50_MS` |
| `agenttel.decision.*` | `DECISION_` | `DECISION_RETRYABLE` |
| `agenttel.anomaly.*` | `ANOMALY_` | `ANOMALY_DETECTED` |
| `agenttel.error.*` | `ERROR_` | `ERROR_CATEGORY` |
| `agenttel.cause.*` | `CAUSE_` | `CAUSE_HINT` |
| `agenttel.severity.*` | `SEVERITY_` | `SEVERITY_BUSINESS_IMPACT` |
| `agenttel.correlation.*` | `CORRELATION_` | `CORRELATION_LIKELY_CAUSE` |
| `agenttel.slo.*` | `SLO_` | `SLO_BUDGET_REMAINING` |
| `agenttel.deployment.*` | `DEPLOYMENT_` | `DEPLOYMENT_VERSION` |
| `agenttel.genai.*` | `GENAI_` | `GENAI_COST_USD` |
| `agenttel.agent.*` | `AGENT_` | `AGENT_ROLE` |
| `agenttel.session.*` | `SESSION_` | `SESSION_ID` |
| `agenttel.circuit_breaker.*` | `CIRCUIT_BREAKER_` | `CIRCUIT_BREAKER_NEW_STATE` |

---

## Attribute Lifecycle Summary

The following table summarizes when and where each category of attributes is set:

| Category | Set By | Set When | Appears On |
|----------|--------|----------|------------|
| Topology | `AgentTelResourceProvider` | SDK initialization (once per service) | Resource attributes |
| Baselines | `AgentTelSpanProcessor` | `onStart()` for every span of a registered operation | Span attributes |
| Baseline Confidence | `AgentTelEnrichingSpanExporter` | Export time | Span attributes |
| Decisions | `AgentTelSpanProcessor` | `onStart()` for every span of a registered operation | Span attributes |
| Anomaly | `AgentTelSpanProcessor` | `onEnd()` when deviation from baseline is detected | Span attributes |
| Error Classification | `AgentTelEnrichingSpanExporter` | Export time, for error spans only | Span attributes |
| Causality | `AgentTelEnrichingSpanExporter` | Export time, for error/anomalous spans | Span attributes |
| Severity | `AgentTelEnrichingSpanExporter` | Export time, for error/anomalous spans | Span attributes |
| Change Correlation | `ChangeCorrelationEngine` | During incident context construction | Incident context |
| SLO | `AgentTelSpanProcessor` (via `SloTracker`) | `onEnd()` for spans with registered SLOs | Span attributes |
| Deployment | `DeploymentEventEmitter` | Service startup | Event attributes |
| GenAI | GenAI wrappers + `CostEnrichingSpanExporter` | Span creation (wrappers) and export (cost) | Span attributes |
| Agent Identity | `AgentActionTracker` | Agent action execution | Span attributes |
| Sessions | `SessionManager` | Session creation | Span attributes |
| Circuit Breaker | `AgentTelEventEmitter` | Circuit breaker state transition | Event attributes |
| Frontend | `@agenttel/web` SDK | Various (page load, API call, interaction, journey) | Span + Resource attributes |
