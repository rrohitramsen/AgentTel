# Agent-Autonomous Telemetry

This document describes the agent-autonomous capabilities added in Phase 7 — the features that enable AI agents to move from **observe → suggest** to **observe → diagnose → act → verify** without human intervention.

---

## Philosophy

Standard observability answers "What happened?" AgentTel Phase 1-6 answers "What should an agent know?" Phase 7 answers **"What does an agent need to act autonomously?"**

The gap between "suggesting a runbook URL" and "following a decision tree to resolution" is enormous. Phase 7 closes this gap with:

1. **Error Classification** — agents know *why* something failed, not just *that* it failed
2. **Causal Analysis** — agents know *what caused* the failure with confidence scores
3. **Structured Playbooks** — agents follow machine-readable decision trees, not wiki links
4. **Parameterized Actions** — agents know *exactly* what parameters to use for remediation
5. **Action Feedback** — agents verify if their remediation actually worked
6. **Change Correlation** — agents answer "what changed right before this broke?"

---

## The Decision Loop

```
                    ┌─────────────┐
                    │  1. OBSERVE  │
                    │  Health,     │
                    │  metrics,    │
                    │  SLOs        │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ 2. DIAGNOSE  │
                    │ Error class, │
                    │ causality,   │
                    │ correlation  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  3. PLAN     │
                    │  Playbook,   │
                    │  error       │
                    │  analysis    │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  4. ACT      │
                    │  Parameterized│
                    │  remediation │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ 5. VERIFY    │
                    │ Pre/post     │
                    │ health diff  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ 6. REPORT    │
                    │ SLO, trends, │
                    │ audit trail  │
                    └─────────────┘
```

### MCP Tools per Phase

| Phase | Tools | Purpose |
|-------|-------|---------|
| Observe | `get_service_health`, `get_executive_summary` | Understand current state |
| Diagnose | `get_incident_context` | Error classification, causality, change correlation |
| Plan | `get_playbook`, `get_error_analysis` | Structured decision tree, error breakdown |
| Act | `list_remediation_actions`, `execute_remediation` | Parameterized actions with specs |
| Verify | `verify_remediation_effect` | Pre/post health comparison |
| Report | `get_slo_report`, `get_trend_analysis` | Compliance and trend reporting |

---

## Error Classification

### How It Works

The `ErrorClassifier` examines `SpanData` at export time and classifies errors into actionable categories:

1. **Exception type** — extracted from span events (OTel exception recording)
2. **HTTP status code** — from `http.response.status_code` attribute
3. **Dependency** — extracted from `db.system`, `server.address`, or `rpc.service`

### Classification Rules

```
Exception: *Timeout*, *SocketTimeout*     → DEPENDENCY_TIMEOUT
Exception: *Connection*, *ConnectException* → CONNECTION_ERROR
Exception: NullPointer, ClassCast, etc.    → CODE_BUG
Exception: OutOfMemory, StackOverflow      → RESOURCE_EXHAUSTION
Exception: *Validation*, *IllegalArgument*  → DATA_VALIDATION
HTTP 429                                   → RATE_LIMITED
HTTP 401, 403                              → AUTH_FAILURE
HTTP 400, 422                              → DATA_VALIDATION
Everything else                            → UNKNOWN
```

### Agent Guidance by Category

| Category | Retry? | Scale? | Rollback? | Human? |
|----------|--------|--------|-----------|--------|
| `dependency_timeout` | Yes (with backoff) | No | No | If persistent |
| `connection_error` | No | No | No | Check dependency |
| `code_bug` | Never | No | Yes | Always |
| `rate_limited` | After delay | No | No | Request quota |
| `auth_failure` | Never | No | No | Check credentials |
| `resource_exhaustion` | No | Yes | No | Investigate |
| `data_validation` | Never | No | No | Fix input |
| `unknown` | Maybe | No | Maybe | Investigate |

---

## Baseline Confidence

### Why It Matters

A baseline computed from 5 samples is meaningless. A baseline from 5,000 samples is reliable. Without confidence, agents treat both equally — leading to false anomaly alerts on low-traffic operations and missed anomalies on high-traffic ones.

### Confidence Levels

| Samples | Confidence | Agent Behavior |
|---------|------------|----------------|
| < 30 | `"low"` | Treat anomaly detection results with skepticism |
| 30–200 | `"medium"` | Anomaly detection is usable but may not capture edge cases |
| > 200 | `"high"` | Anomaly detection is statistically significant |

### On Spans

```
agenttel.baseline.sample_count = 1250
agenttel.baseline.confidence = "high"
```

---

## Structured Playbooks

### Why Not Runbook URLs?

A `runbook_url` pointing to a Confluence page is useless to an LLM — it can't open URLs or navigate wikis. Structured playbooks provide machine-readable decision trees that agents can follow step by step.

### Playbook Structure

```
Playbook
├── name: "cascade-failure-response"
├── triggerPatterns: [CASCADE_FAILURE]
└── steps:
    ├── [1] CHECK: "Identify which dependencies are failing"
    │   condition: "dependency_error_rate > 0.1"
    │   → success: step 2, failure: step 5
    │
    ├── [2] ACTION: "Enable circuit breaker on failing deps"
    │   actionName: "enable_circuit_breaker"
    │   requiresApproval: false
    │   → success: step 3, failure: step 4
    │
    ├── [3] CHECK: "Verify error rate is decreasing"
    │   condition: "error_rate < previous_error_rate"
    │   → success: step 5, failure: step 4
    │
    ├── [4] ACTION: "Scale up service instances"
    │   actionName: "scale_up"
    │   requiresApproval: true
    │   → success: step 5
    │
    └── [5] CHECK: "Confirm service health is restored"
        condition: "health_status == HEALTHY"
```

### Default Playbooks

AgentTel pre-registers playbooks for 4 common incident patterns:

1. **Cascade Failure Response** — identify deps → circuit break → verify → scale
2. **Error Rate Spike Response** — classify errors → circuit break or rollback → verify
3. **Latency Degradation Response** — check resources → scale → optimize or rollback
4. **Memory Leak Response** — confirm trend → rolling restart → monitor → investigate

### Custom Playbooks

```java
Playbook custom = new Playbook(
    "payment-failover",
    "Failover to backup payment gateway",
    List.of(IncidentPattern.CASCADE_FAILURE),
    List.of(
        Playbook.PlaybookStep.check("1", "Verify primary is down",
            "primary health check fails", "2", null),
        Playbook.PlaybookStep.action("2", "Switch to backup",
            "switch_gateway", false, "3", null),
        Playbook.PlaybookStep.check("3", "Verify backup is healthy",
            "backup returns 200", null, null)
    )
);
playbookRegistry.register(custom);
```

---

## Parameterized Action Specs

### The Problem

"Enable circuit breaker" is vague. An agent needs to know: what failure threshold? How long before half-open? How many successes to close?

### Action Specs

Every remediation action can include a structured `ActionSpec` that provides exact parameters:

```java
// Instead of just "enable circuit breaker":
new ActionSpec.CircuitBreakerSpec(
    5,       // failureThreshold: open after 5 consecutive failures
    30000,   // halfOpenAfterMs: try again after 30 seconds
    3        // successThreshold: close after 3 successes
)

// Instead of just "retry":
new ActionSpec.RetrySpec(
    3,                              // maxAttempts
    List.of(100L, 200L, 400L),     // backoffMs: exponential
    List.of(502, 503),             // retryOnStatusCodes
    List.of("SocketTimeoutException"), // retryOnExceptions
    List.of(400, 401)              // notRetryOnStatusCodes
)
```

---

## Change Correlation

### The Question

"What changed right before this broke?" is the most common question during incidents. The `ChangeCorrelationEngine` answers it automatically.

### Recording Changes

```java
engine.recordDeployment("deploy-v2.1.0", "v2.1.0", "New payment logic");
engine.recordConfigChange("config-123", "Updated rate limit to 500 rps");
engine.recordChange(new ChangeEvent("flag-1", ChangeType.FEATURE_FLAG,
    "Enabled new checkout flow", Instant.now()));
```

### Correlation

When an anomaly is detected, the engine finds changes within a configurable window (default 15 minutes) and ranks them by:

1. **Time proximity** — changes closer to anomaly onset get higher confidence
2. **Change type weight** — deployments (1.0) are weighted higher than scaling events (0.6)

### Output

```
CHANGE CORRELATION:
  Likely cause: DEPLOYMENT (deploy-v2.1.0) — confidence: 0.85
  Time delta: 30 minutes before anomaly
  All correlated changes:
    - [DEPLOYMENT] deploy-v2.1.0: New payment logic (30min ago)
    - [CONFIG] config-123: Updated rate limit (45min ago)
```

---

## Action Feedback Loop

### The Gap

Without feedback, agents can't learn. They execute "enable circuit breaker" and hope it works. The `ActionFeedbackLoop` closes this gap.

### How It Works

1. Agent calls `execute_remediation` → action is dispatched
2. `ActionFeedbackLoop` captures a pre-action health snapshot
3. After 30 seconds (configurable), captures a post-action snapshot
4. Computes whether the action was effective:
   - Error rate decreased by > 0.1%? → effective
   - Average P50 latency decreased by > 5ms? → effective
   - Health status improved? → effective

### Verification

```
REMEDIATION VERIFICATION:
  Action: toggle-payment-gateway-circuit-breaker
  Effective: YES
  Latency delta: -120.5ms
  Error rate delta: -0.0420
  Health: DEGRADED → HEALTHY
  Verified at: 2025-01-15T14:31:30Z
```

---

## Span Enrichment Pipeline

Phase 7 adds computed attributes at export time via the `AgentTelEnrichingSpanExporter`:

```
Span created (application code)
    │
    ▼
AgentTelSpanProcessor.onStart()
    │  ← baselines, decision metadata (mutable)
    ▼
Application code executes
    │
    ▼
AgentTelSpanProcessor.onEnd()
    │  ← rolling baselines, dependency tracking,
    │     causality state, pattern matching (read-only)
    ▼
AgentTelEnrichingSpanExporter.export()
    │  ← error classification, causality analysis,
    │     baseline confidence, severity assessment,
    │     anomaly detection, SLO budget (computed attributes)
    ▼
Downstream exporter (OTLP, Jaeger, etc.)
```

### Why Export Time?

`SpanProcessor.onEnd()` receives a `ReadableSpan` — **immutable**. Attributes cannot be set. The `AgentTelEnrichingSpanExporter` wraps each `SpanData` in an `EnrichedSpanData` that merges original attributes with computed enrichments. This pattern is proven by `CostEnrichingSpanExporter` in `agenttel-genai`.

---

## Configuration

All Phase 7 features are auto-configured in Spring Boot via `AgentTelAutoConfiguration` and `AgentTelAgentAutoConfiguration`. No additional configuration is required beyond what's already in `application.yml`.

### New Beans Auto-Registered

| Bean | Module | Purpose |
|------|--------|---------|
| `ErrorClassifier` | core | Error categorization |
| `OperationDependencyTracker` | core | Runtime op-to-dep mapping |
| `CausalityTracker` | core | Root cause analysis |
| `AgentTelEnrichingSpanExporter` | core | Export-time enrichment |
| `PlaybookRegistry` | agent | Machine-readable playbooks |
| `ChangeCorrelationEngine` | agent | Change → anomaly correlation |
| `ActionFeedbackLoop` | agent | Post-action health verification |

### JavaAgent Extension

The javaagent extension (`agenttel-javaagent-extension`) also registers the enriching exporter automatically. No Spring dependency required.
