# AgentTel Semantic Conventions

> **Proposed extensions to OpenTelemetry semantic conventions for agent-ready telemetry emission from any service.**

---

## 1. Design Philosophy

Today's OTel semantic conventions answer: **"What happened?"**
AgentTel conventions additionally answer: **"What does an AI agent need to know to reason about what happened?"**

We achieve this by defining five categories of agent-ready attributes:

| Category | Purpose | Example |
|----------|---------|---------|
| **Topology** | Who am I, what do I depend on, who depends on me? | `agenttel.dependency.name`, `agenttel.consumer.name` |
| **Baselines** | What does "normal" look like? | `agenttel.baseline.latency_p99_ms`, `agenttel.baseline.error_rate` |
| **Causality** | What probably caused this? | `agenttel.cause.hint`, `agenttel.cause.correlated_event_id` |
| **Decision** | What can an agent do about this? | `agenttel.decision.retryable`, `agenttel.decision.runbook_url` |
| **Severity** | How bad is this, really? | `agenttel.severity.anomaly_score`, `agenttel.severity.pattern` |

These are **additive** — they enrich standard OTel spans, metrics, and events without replacing any existing conventions.

---

## 2. Namespace

All AgentTel-specific attributes live under the `agenttel.*` namespace, following OTel conventions:

```
agenttel.topology.*     — Service topology and dependency declarations
agenttel.baseline.*     — Expected behavior baselines
agenttel.cause.*        — Causal reasoning hints
agenttel.decision.*     — Actionability metadata
agenttel.severity.*     — Severity and anomaly scoring
agenttel.genai.*        — GenAI-specific extensions (supplements gen_ai.*)
```

---

## 3. Topology Conventions (`agenttel.topology.*`)

### 3.1 Resource Attributes (set once per service instance)

These are attached to the OTel `Resource` and describe the service's place in the system.

| Attribute | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `agenttel.topology.team` | string | Recommended | Owning team name | `"payments-platform"` |
| `agenttel.topology.tier` | enum | Recommended | Service criticality tier | `critical`, `standard`, `internal`, `experimental` |
| `agenttel.topology.domain` | string | Optional | Business domain | `"commerce"` |
| `agenttel.topology.on_call_channel` | string | Optional | Escalation channel | `"#payments-oncall"` |
| `agenttel.topology.repo_url` | string | Optional | Source repository URL | `"https://github.com/..."` |

### 3.2 Dependency Declarations (Resource-level array)

Declared dependencies are emitted as a structured resource attribute, enabling agents to understand the service graph without consulting an external CMDB.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `agenttel.topology.dependencies` | string[] (JSON) | Recommended | Array of dependency descriptors |

Each dependency descriptor is a JSON object:

```json
{
  "name": "payment-gateway",
  "type": "external_api",          
  "protocol": "https",
  "criticality": "required",       
  "timeout_ms": 5000,
  "circuit_breaker": true,
  "fallback": "cached_response",
  "health_endpoint": "https://gateway.pay.com/health"
}
```

**Dependency `type` values:**
- `internal_service` — Another service owned by the organization
- `external_api` — Third-party API
- `database` — Database (SQL/NoSQL)
- `message_broker` — Kafka, RabbitMQ, etc.
- `cache` — Redis, Memcached, etc.
- `object_store` — S3, GCS, etc.
- `identity_provider` — Auth0, Okta, etc.

**Dependency `criticality` values:**
- `required` — Service cannot function without it
- `degraded` — Service can function but with reduced capability
- `optional` — Service can function normally without it

### 3.3 Consumer Declarations

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `agenttel.topology.consumers` | string[] (JSON) | Optional | Services that depend on this service |

```json
{
  "name": "notification-service",
  "consumption_pattern": "async",   
  "sla_latency_ms": 500
}
```

---

## 4. Baseline Conventions (`agenttel.baseline.*`)

Baselines allow an agent to immediately assess whether current behavior is anomalous **without needing historical data**.

### 4.1 Span-Level Baselines

Attached to individual spans to provide operation-level context:

| Attribute | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `agenttel.baseline.latency_p50_ms` | double | Recommended | Expected median latency | `45.0` |
| `agenttel.baseline.latency_p99_ms` | double | Recommended | Expected p99 latency | `200.0` |
| `agenttel.baseline.error_rate` | double | Optional | Expected error rate (0.0–1.0) | `0.001` |
| `agenttel.baseline.throughput_rps` | double | Optional | Expected requests/sec | `1500.0` |
| `agenttel.baseline.source` | enum | Recommended | How baseline was determined | `static`, `rolling_7d`, `ml_model`, `slo` |
| `agenttel.baseline.updated_at` | string (ISO 8601) | Optional | When baseline was last computed | `"2026-02-20T00:00:00Z"` |

### 4.2 Resource-Level Baselines (SLO declarations)

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `agenttel.baseline.slo` | string (JSON) | Optional | Service-level objectives |

```json
{
  "availability": 0.999,
  "latency_p99_ms": 300,
  "error_budget_remaining": 0.45,
  "error_budget_window": "30d"
}
```

---

## 5. Causality Conventions (`agenttel.cause.*`)

Causal hints give agents a head start on root cause analysis by encoding probable causes directly in the telemetry.

### 5.1 Span-Level Causal Hints

| Attribute | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `agenttel.cause.hint` | string | Optional | Human/agent-readable cause description | `"Upstream payment-gateway returning 503 since 14:03 UTC"` |
| `agenttel.cause.category` | enum | Optional | Broad cause category | `dependency_failure`, `resource_exhaustion`, `config_change`, `deployment`, `traffic_spike`, `data_quality`, `unknown` |
| `agenttel.cause.dependency` | string | Optional | Name of the dependency implicated | `"payment-gateway"` |
| `agenttel.cause.correlated_span_id` | string | Optional | Span ID of a causally related span | `"abc123def456"` |
| `agenttel.cause.correlated_event_id` | string | Optional | Event ID of a causally related event | `"deploy-789"` |
| `agenttel.cause.started_at` | string (ISO 8601) | Optional | When the causal condition began | `"2026-02-26T14:03:00Z"` |

### 5.2 Deployment Context Events

Services SHOULD emit a structured event on startup or deployment:

**Event name:** `agenttel.deployment.info`

| Attribute | Type | Description |
|-----------|------|-------------|
| `agenttel.deployment.id` | string | Deployment identifier |
| `agenttel.deployment.version` | string | Application version |
| `agenttel.deployment.commit_sha` | string | Git commit |
| `agenttel.deployment.previous_version` | string | Version being replaced |
| `agenttel.deployment.strategy` | enum | `rolling`, `blue_green`, `canary` |
| `agenttel.deployment.config_changes` | string[] | List of config keys that changed |
| `agenttel.deployment.timestamp` | string | When deployment started |

---

## 6. Decision Conventions (`agenttel.decision.*`)

Decision metadata tells agents what actions are available and appropriate for a given situation.

### 6.1 Span-Level Decision Metadata

| Attribute | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `agenttel.decision.retryable` | boolean | Recommended | Whether the operation can be retried | `true` |
| `agenttel.decision.retry_after_ms` | int | Optional | Suggested wait before retry | `5000` |
| `agenttel.decision.idempotent` | boolean | Optional | Whether the operation is idempotent | `true` |
| `agenttel.decision.fallback_available` | boolean | Optional | Whether a fallback path exists | `true` |
| `agenttel.decision.fallback_description` | string | Optional | What the fallback does | `"Returns cached pricing from last successful call"` |
| `agenttel.decision.runbook_url` | string | Optional | Link to runbook for this operation | `"https://wiki/runbooks/payment-timeout"` |
| `agenttel.decision.escalation_level` | enum | Optional | Suggested escalation | `auto_resolve`, `notify_team`, `page_oncall`, `incident_commander` |
| `agenttel.decision.known_issue_id` | string | Optional | Links to a known issue / JIRA ticket | `"PAY-4521"` |
| `agenttel.decision.safe_to_restart` | boolean | Optional | Whether the service can be safely restarted | `true` |

### 6.2 Circuit Breaker Events

**Event name:** `agenttel.circuit_breaker.state_change`

| Attribute | Type | Description |
|-----------|------|-------------|
| `agenttel.circuit_breaker.name` | string | Name of the circuit breaker |
| `agenttel.circuit_breaker.previous_state` | enum | `closed`, `open`, `half_open` |
| `agenttel.circuit_breaker.new_state` | enum | `closed`, `open`, `half_open` |
| `agenttel.circuit_breaker.failure_count` | int | Number of failures that triggered the transition |
| `agenttel.circuit_breaker.dependency` | string | The dependency being protected |

---

## 7. Severity Conventions (`agenttel.severity.*`)

Goes beyond simple log levels to provide nuanced severity assessment.

| Attribute | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `agenttel.severity.anomaly_score` | double | Optional | 0.0 (normal) to 1.0 (highly anomalous) | `0.92` |
| `agenttel.severity.pattern` | string | Optional | Matched incident pattern name | `"cascade-failure"`, `"memory-leak"`, `"thundering-herd"` |
| `agenttel.severity.impact_scope` | enum | Optional | Blast radius | `single_request`, `single_user`, `subset_users`, `all_users`, `multi_service` |
| `agenttel.severity.business_impact` | enum | Optional | Business impact level | `none`, `degraded_experience`, `feature_unavailable`, `revenue_impacting`, `data_loss` |
| `agenttel.severity.user_facing` | boolean | Optional | Whether the issue is visible to end users | `true` |

---

## 8. GenAI Extensions (`agenttel.genai.*`)

These supplement the existing `gen_ai.*` OTel semantic conventions with additional agent-ready attributes specific to JVM GenAI applications.

| Attribute | Type | Description |
|-----------|------|-------------|
| `agenttel.genai.framework` | string | JVM framework used (`spring_ai`, `langchain4j`, `bedrock_sdk`) |
| `agenttel.genai.prompt_template_id` | string | Identifier of the prompt template used |
| `agenttel.genai.prompt_template_version` | string | Version of the prompt template |
| `agenttel.genai.rag_source_count` | int | Number of RAG documents retrieved |
| `agenttel.genai.rag_relevance_score_avg` | double | Average relevance score of retrieved docs |
| `agenttel.genai.guardrail_triggered` | boolean | Whether a safety guardrail was activated |
| `agenttel.genai.guardrail_name` | string | Name of the triggered guardrail |
| `agenttel.genai.cost_usd` | double | Estimated cost of the GenAI operation in USD |
| `agenttel.genai.cache_hit` | boolean | Whether a cached response was used |

---

## 9. Structured Event Schemas

Beyond span attributes, AgentTel defines structured **OTel Log Events** for key operational moments. These use OTel's `LogRecord` with structured body payloads.

### 9.1 `agenttel.anomaly.detected`

Emitted when the library detects behavior outside baselines.

```json
{
  "event.name": "agenttel.anomaly.detected",
  "body": {
    "metric": "latency_p99",
    "current_value": 4500,
    "baseline_value": 200,
    "deviation_factor": 22.5,
    "anomaly_score": 0.97,
    "probable_cause": {
      "category": "dependency_failure",
      "dependency": "payment-gateway",
      "evidence": "payment-gateway health check failing since 14:03 UTC"
    },
    "suggested_actions": [
      { "action": "check_dependency_health", "target": "payment-gateway" },
      { "action": "activate_fallback", "description": "Enable cached pricing" },
      { "action": "notify_team", "channel": "#payments-oncall" }
    ]
  }
}
```

### 9.2 `agenttel.dependency.state_change`

Emitted when a dependency's perceived state changes.

```json
{
  "event.name": "agenttel.dependency.state_change",
  "body": {
    "dependency": "payment-gateway",
    "previous_state": "healthy",
    "new_state": "degraded",
    "evidence": "5xx rate exceeded 10% threshold over 60s window",
    "started_at": "2026-02-26T14:03:00Z",
    "affected_operations": ["/pay", "/refund"],
    "circuit_breaker_state": "half_open"
  }
}
```

### 9.3 `agenttel.slo.budget_alert`

Emitted when error budget consumption crosses a threshold.

```json
{
  "event.name": "agenttel.slo.budget_alert",
  "body": {
    "slo_name": "payment-availability",
    "target": 0.999,
    "current": 0.9975,
    "error_budget_remaining": 0.15,
    "burn_rate": 3.2,
    "projected_exhaustion": "2026-03-01T00:00:00Z",
    "window": "30d"
  }
}
```

---

## 10. Relationship to Existing Standards

| Standard | Relationship |
|----------|-------------|
| **OpenTelemetry Semantic Conventions** | AgentTel is a strict extension — all OTel conventions remain intact and are required |
| **OTel GenAI Semantic Conventions** (`gen_ai.*`) | AgentTel's `agenttel.genai.*` supplements but never replaces `gen_ai.*` attributes |
| **OTel Resource Conventions** | AgentTel adds topology attributes to `Resource` alongside standard `service.*` attributes |
| **OpenTelemetry Collector** | AgentTel telemetry flows through standard OTel pipelines with no special processing required |
| **OCSF / ECS** | AgentTel is complementary — can coexist when telemetry is exported to security platforms |

---

## 11. Versioning

AgentTel semantic conventions follow the same versioning approach as OTel:
- **Experimental** attributes may change between minor versions
- **Stable** attributes follow OTel's stability guarantees
- All attributes start as Experimental and are promoted after community validation

Current status: **All conventions are Experimental (v0.1.0-alpha)**
