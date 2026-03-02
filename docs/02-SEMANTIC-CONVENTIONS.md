# Semantic Conventions

AgentTel defines a set of semantic convention extensions to OpenTelemetry, organized into categories of agent-ready attributes plus structured events. Backend attributes use the `agenttel.*` namespace and frontend attributes use the `agenttel.client.*` namespace. All coexist with standard OTel conventions.

## Design Philosophy

Standard OpenTelemetry conventions answer **"What happened?"** — an HTTP span records the method, URL, status code, and duration. AgentTel adds **"What does an AI agent need to know to reason about and act on this?"** — the behavioral baseline, whether retrying is safe, who to page, and what the dependency graph looks like.

---

## 1. Topology Attributes

Service identity and dependency graph. Set as resource attributes at startup.

### Service Identity

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.topology.team` | string | Owning team identifier | `"payments-platform"` |
| `agenttel.topology.tier` | string | Service criticality tier | `"critical"` |
| `agenttel.topology.domain` | string | Business domain | `"commerce"` |
| `agenttel.topology.on_call_channel` | string | Escalation channel | `"#payments-oncall"` |
| `agenttel.topology.repo_url` | string | Source repository URL | `"https://github.com/org/repo"` |

### Service Tiers

| Tier | Value | Meaning |
|------|-------|---------|
| Critical | `"critical"` | User-facing, revenue-impacting. Pages on-call immediately. |
| Standard | `"standard"` | Important but not immediately revenue-impacting. |
| Internal | `"internal"` | Internal tooling and infrastructure. |
| Experimental | `"experimental"` | Non-production or experimental services. |

### Dependency Graph

| Attribute | Type | Description |
|-----------|------|-------------|
| `agenttel.topology.dependencies` | string (JSON) | JSON array of dependency descriptors |
| `agenttel.topology.consumers` | string (JSON) | JSON array of consumer descriptors |

**Dependency Descriptor Schema:**

```json
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
```

**Dependency Types:** `internal_service`, `external_api`, `database`, `message_broker`, `cache`, `object_store`, `identity_provider`

**Dependency Criticality:**

| Level | Value | Meaning |
|-------|-------|---------|
| Required | `"required"` | Failure causes outage. No fallback. |
| Degraded | `"degraded"` | Failure causes reduced functionality. Partial fallback available. |
| Optional | `"optional"` | Failure has no direct user impact. |

**Consumer Descriptor Schema:**

```json
{
  "name": "checkout-service",
  "consumption_pattern": "synchronous",
  "sla_latency_ms": 200
}
```

**Consumption Patterns:** `synchronous`, `asynchronous`, `batch`, `streaming`

---

## 2. Baseline Attributes

What "normal" looks like for each operation. Set as span attributes by the `AgentTelSpanProcessor`.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.baseline.latency_p50_ms` | double | Expected P50 latency | `45.0` |
| `agenttel.baseline.latency_p99_ms` | double | Expected P99 latency | `200.0` |
| `agenttel.baseline.error_rate` | double | Expected error rate (0.0–1.0) | `0.001` |
| `agenttel.baseline.throughput_rps` | double | Expected requests per second | `150.0` |
| `agenttel.baseline.source` | string | How the baseline was determined | `"static"` |

### Baseline Sources

| Source | Value | Description |
|--------|-------|-------------|
| Static | `"static"` | From `@AgentOperation` annotation or configuration file |
| Rolling | `"rolling"` | Computed from a sliding window of observed traffic |
| Composite | `"composite"` | Static baseline with rolling fallback for gaps |
| Default | `"default"` | System default when no baseline is available |

### Rolling Baseline Metrics

The `RollingBaselineProvider` maintains per-operation sliding windows that compute:

| Metric | Description |
|--------|-------------|
| P50, P95, P99 | Latency percentiles from observed traffic |
| Mean, Stddev | Statistical summary for z-score anomaly detection |
| Error Rate | Observed error rate over the window |
| Sample Count | Number of observations in the current window |

### Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `agenttel.baselines.rolling-window-size` | `1000` | Number of observations per sliding window |
| `agenttel.baselines.rolling-min-samples` | `10` | Minimum samples before a rolling baseline is considered valid |

---

## 3. Decision Attributes

What an AI agent is permitted and equipped to do. Set as span attributes from `@AgentOperation` annotations.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.decision.retryable` | boolean | Whether the operation can be retried | `true` |
| `agenttel.decision.retry_after_ms` | long | Suggested retry delay in milliseconds | `1000` |
| `agenttel.decision.idempotent` | boolean | Whether repeated calls produce the same result | `true` |
| `agenttel.decision.fallback_available` | boolean | Whether a fallback path exists | `true` |
| `agenttel.decision.fallback_description` | string | Human-readable fallback description | `"Return cached pricing"` |
| `agenttel.decision.runbook_url` | string | Link to operational runbook | `"https://wiki/..."` |
| `agenttel.decision.escalation_level` | string | Escalation procedure | `"page_oncall"` |
| `agenttel.decision.safe_to_restart` | boolean | Whether service restart is safe during this operation | `true` |

### Escalation Levels

| Level | Value | Meaning |
|-------|-------|---------|
| Auto-Resolve | `"auto_resolve"` | Agent can handle autonomously without human involvement |
| Notify Team | `"notify_team"` | Send asynchronous notification to the owning team |
| Page On-Call | `"page_oncall"` | Page the on-call engineer immediately |
| Incident Commander | `"incident_commander"` | Escalate to incident management process |

---

## 4. Anomaly Attributes

Real-time deviation detection. Set as span attributes by the `AgentTelSpanProcessor` when anomalous behavior is detected.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.anomaly.detected` | boolean | Whether an anomaly was detected on this span | `true` |
| `agenttel.anomaly.pattern` | string | Identified incident pattern | `"cascade_failure"` |
| `agenttel.anomaly.score` | double | Anomaly severity score (0.0–1.0) | `0.85` |
| `agenttel.anomaly.latency_z_score` | double | Z-score of latency deviation from baseline | `4.2` |

### Incident Patterns

| Pattern | Value | Detection Method | Description |
|---------|-------|-----------------|-------------|
| Cascade Failure | `"cascade_failure"` | 3+ dependencies with errors in recent window | Multiple downstream services failing simultaneously |
| Latency Degradation | `"latency_degradation"` | Current latency > 2x rolling P50 | Sustained latency elevation above baseline |
| Error Rate Spike | `"error_rate_spike"` | Recent error rate > 5x baseline | Sudden increase in error rate |
| Memory Leak | `"memory_leak"` | Positive slope in latency linear regression | Monotonically increasing latency trend |
| Thundering Herd | `"thundering_herd"` | Traffic burst exceeding normal patterns | Sudden traffic spike after recovery |
| Cold Start | `"cold_start"` | High latency with low request count | Elevated latency on fresh instances |

### Detection Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `agenttel.anomaly-detection.z-score-threshold` | `3.0` | Z-score above which latency is anomalous |
| `latencyDegradationThreshold` | `2.0` | Multiplier over P50 to trigger degradation pattern |
| `errorRateSpikeThreshold` | `5.0` | Multiplier over baseline error rate to trigger spike pattern |
| `cascadeFailureMinServices` | `3` | Minimum failing dependencies for cascade detection |

---

## 5. SLO Attributes

Error budget consumption tracking. Set as span attributes when SLOs are registered.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.slo.name` | string | SLO identifier | `"payment-availability"` |
| `agenttel.slo.target` | double | SLO target (0.0–1.0) | `0.999` |
| `agenttel.slo.budget_remaining` | double | Remaining error budget fraction (0.0–1.0) | `0.85` |
| `agenttel.slo.burn_rate` | double | Budget consumption rate | `0.15` |

### SLO Types

| Type | Description | Example Target |
|------|-------------|---------------|
| `AVAILABILITY` | Percentage of successful (non-error) requests | 99.9% |
| `LATENCY_P99` | Percentage of requests completing under P99 threshold | 99.0% |
| `LATENCY_P50` | Percentage of requests completing under P50 threshold | 95.0% |
| `ERROR_RATE` | Maximum acceptable error rate | 0.1% |

### Alert Thresholds

Budget alerts are emitted when remaining budget crosses these thresholds:

| Remaining Budget | Severity | Action |
|-----------------|----------|--------|
| <= 50% | `INFO` | Informational — budget consumption is elevated |
| <= 25% | `WARNING` | Warning — budget at risk of exhaustion |
| <= 10% | `CRITICAL` | Critical — budget nearly exhausted, immediate action needed |

---

## 6. GenAI Attributes

Extensions for AI/ML workload observability. Set on spans created by GenAI instrumentation wrappers.

### Standard OTel GenAI Attributes

AgentTel populates the emerging OTel GenAI semantic conventions:

| Attribute | Type | Description |
|-----------|------|-------------|
| `gen_ai.operation.name` | string | `"chat"`, `"text_completion"`, `"embeddings"` |
| `gen_ai.system` | string | Provider: `"openai"`, `"anthropic"`, `"aws_bedrock"` |
| `gen_ai.request.model` | string | Requested model identifier |
| `gen_ai.response.model` | string | Actual model used in response |
| `gen_ai.usage.input_tokens` | long | Input/prompt token count |
| `gen_ai.usage.output_tokens` | long | Output/completion token count |
| `gen_ai.response.finish_reasons` | string[] | Completion stop reasons |

### AgentTel GenAI Extensions

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.genai.framework` | string | Instrumentation source | `"langchain4j"`, `"spring_ai"` |
| `agenttel.genai.cost_usd` | double | Estimated cost in USD | `0.000795` |
| `agenttel.genai.prompt_template_id` | string | Prompt template identifier | `"customer-support-v2"` |
| `agenttel.genai.prompt_template_version` | string | Prompt template version | `"1.3"` |
| `agenttel.genai.rag_source_count` | long | Number of RAG sources retrieved | `5` |
| `agenttel.genai.rag_relevance_score_avg` | double | Average relevance score | `0.87` |
| `agenttel.genai.guardrail_triggered` | boolean | Whether a guardrail fired | `false` |
| `agenttel.genai.guardrail_name` | string | Name of triggered guardrail | `"pii_filter"` |
| `agenttel.genai.cache_hit` | boolean | Whether a cached response was used | `false` |

---

## 7. Frontend Attributes

Client-side telemetry from `agenttel-web` (browser SDK). Set on spans emitted by the browser and exported via OTLP.

### Resource Attributes

Set once per browser application at initialization.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.app.name` | string | Application name | `"checkout-web"` |
| `agenttel.client.app.version` | string | Application version | `"1.0.0"` |
| `agenttel.client.app.platform` | string | Platform identifier | `"browser"` |
| `agenttel.client.app.environment` | string | Deployment environment | `"production"` |
| `agenttel.client.topology.team` | string | Owning team | `"checkout-frontend"` |
| `agenttel.client.topology.domain` | string | Business domain | `"commerce"` |

### Page & Route Attributes

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.page.url` | string | Current page URL (path only, no query/hash) | `"/checkout/payment"` |
| `agenttel.client.page.route` | string | Matched route pattern | `"/checkout/:step"` |
| `agenttel.client.page.title` | string | Document title | `"Checkout - Payment"` |
| `agenttel.client.page.business_criticality` | string | Route business criticality | `"revenue"` |

**Business Criticality Values:** `revenue`, `engagement`, `internal`

### Baseline Attributes

Per-route baselines for frontend operations.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.baseline.page_load_p50_ms` | double | Expected page load P50 | `800.0` |
| `agenttel.client.baseline.page_load_p99_ms` | double | Expected page load P99 | `2000.0` |
| `agenttel.client.baseline.api_call_p50_ms` | double | Expected API response P50 | `300.0` |
| `agenttel.client.baseline.error_rate` | double | Expected error rate (0.0–1.0) | `0.01` |

### Decision Attributes

Per-route decision metadata for agent reasoning.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.decision.escalation_level` | string | Escalation procedure | `"page_oncall"` |
| `agenttel.client.decision.runbook_url` | string | Operational runbook | `"https://wiki/runbooks/checkout"` |
| `agenttel.client.decision.fallback_page` | string | Fallback route on failure | `"/maintenance"` |
| `agenttel.client.decision.retry_on_failure` | boolean | Whether to retry failed page loads | `true` |

### Anomaly Attributes

Client-side anomaly detection results.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.anomaly.detected` | boolean | Whether a client-side anomaly was detected | `true` |
| `agenttel.client.anomaly.pattern` | string | Detected anomaly pattern | `"rage_click"` |
| `agenttel.client.anomaly.score` | double | Anomaly severity (0.0–1.0) | `0.75` |

**Client-Side Anomaly Patterns:**

| Pattern | Value | Detection | Description |
|---------|-------|-----------|-------------|
| Rage Click | `"rage_click"` | N+ clicks on same element within time window | User frustration — UI is unresponsive |
| API Failure Cascade | `"api_failure_cascade"` | N+ API failures within time window | Backend instability visible to user |
| Slow Page Load | `"slow_page_load"` | Load time exceeds baseline by multiplier | Performance degradation on route |
| Error Loop | `"error_loop"` | N+ errors on same route within time window | Repeating failure preventing user progress |
| Funnel Drop-off | `"funnel_dropoff"` | Journey abandonment above baseline | User journey failing at specific step |

### Journey Attributes

Multi-step user journey tracking.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.journey.name` | string | Journey identifier | `"checkout"` |
| `agenttel.client.journey.step` | int | Current step index (0-based) | `3` |
| `agenttel.client.journey.step_name` | string | Step route/name | `"/checkout/payment"` |
| `agenttel.client.journey.status` | string | Journey status | `"in_progress"` |
| `agenttel.client.journey.duration_ms` | double | Time since journey start | `45000.0` |

**Journey Status Values:** `in_progress`, `completed`, `abandoned`

### Correlation Attributes

Cross-stack trace linking between frontend and backend.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.correlation.backend_trace_id` | string | Backend trace ID from response | `"abc123def456"` |
| `agenttel.client.correlation.traceparent` | string | W3C Trace Context header sent | `"00-abc...-01"` |

### Page Load Attributes

Captured from the Navigation Timing API on page load spans.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.page_load.dom_load_ms` | double | DOM content loaded time | `450.0` |
| `agenttel.client.page_load.ttfb_ms` | double | Time to first byte | `120.0` |
| `agenttel.client.page_load.transfer_size_bytes` | long | Page transfer size | `245000` |

### API Call Attributes

Captured from intercepted `fetch` and `XMLHttpRequest` calls.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.api.method` | string | HTTP method | `"POST"` |
| `agenttel.client.api.url` | string | Request URL (path only) | `"/api/payments"` |
| `agenttel.client.api.status_code` | int | Response status code | `200` |
| `agenttel.client.api.duration_ms` | double | Response time | `312.0` |

### Anomaly Detection Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `rageClickThreshold` | `3` | Clicks on same element to trigger rage click |
| `rageClickWindowMs` | `2000` | Time window for rage click detection |
| `apiFailureCascadeThreshold` | `3` | API failures to trigger cascade |
| `apiFailureCascadeWindowMs` | `10000` | Time window for cascade detection |
| `slowPageLoadMultiplier` | `2.0` | Multiplier over baseline P50 to trigger slow load |
| `errorLoopThreshold` | `5` | Errors on same route to trigger error loop |
| `errorLoopWindowMs` | `30000` | Time window for error loop detection |

---

## 8. Structured Events

AgentTel emits structured events via the OTel Logs API for significant state changes that agents should react to.

### agenttel.anomaly.detected

Emitted when a span's behavior deviates significantly from baseline.

```json
{
  "event.name": "agenttel.anomaly.detected",
  "severity": "WARN",
  "body": {
    "operation": "POST /api/payments",
    "pattern": "latency_degradation",
    "anomaly_score": 0.85,
    "z_score": 4.2,
    "current_latency_ms": 312.0,
    "baseline_p50_ms": 45.0
  }
}
```

### agenttel.slo.budget_alert

Emitted when an SLO's error budget crosses a threshold (50%, 25%, 10%).

```json
{
  "event.name": "agenttel.slo.budget_alert",
  "severity": "WARN",
  "body": {
    "slo_name": "payment-availability",
    "severity": "WARNING",
    "budget_remaining": 0.22,
    "burn_rate": 0.78
  }
}
```

### agenttel.dependency.state_change

Emitted when a dependency's observed health transitions.

```json
{
  "event.name": "agenttel.dependency.state_change",
  "severity": "WARN",
  "body": {
    "dependency": "postgres",
    "previous_state": "healthy",
    "current_state": "degraded",
    "error_rate": 0.15
  }
}
```

---

## Relationship to OpenTelemetry

AgentTel is a **strict extension** of OpenTelemetry. Backend attributes use the `agenttel.*` namespace, frontend attributes use `agenttel.client.*`, and GenAI attributes use the emerging `gen_ai.*` conventions. AgentTel-enriched spans remain fully compatible with any OTel backend — Jaeger, Zipkin, Grafana Tempo, Datadog, Splunk, New Relic, and others.

The backend library implements standard OTel interfaces (`SpanProcessor`, `SpanExporter`, `Resource`) and composes cleanly with any other OTel instrumentation. The frontend SDK exports spans via OTLP HTTP to any OTel-compatible collector.
