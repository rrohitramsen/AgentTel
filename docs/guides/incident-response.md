# Incident Response

This guide walks through building autonomous incident response with AgentTel. It covers the full **Observe -- Diagnose -- Act -- Verify** workflow, from health aggregation through remediation verification.

> **See also:**
> - [MCP Server & Agent Tools](mcp-server.md) -- setting up the MCP server and AgentContextProvider
> - [MCP Tools Reference](../reference/mcp-tools.md) -- full parameter and output reference for all 15 built-in tools
> - [Multi-Agent Patterns](multi-agent.md) -- coordinating multiple agents for incident response

---

## Agent Decision Loop

The complete autonomous agent workflow:

```
1. OBSERVE  -->  get_service_health, get_executive_summary
       |
2. DIAGNOSE -->  get_incident_context (includes error classification,
       |         change correlation, causality analysis)
       |
3. PLAN     -->  get_playbook (structured decision tree)
       |         get_error_analysis (understand error types)
       |
4. ACT      -->  list_remediation_actions (with parameterized specs)
       |         execute_remediation
       |
5. VERIFY   -->  verify_remediation_effect
       |         (waits 30s, compares health snapshots)
       |
6. REPORT   -->  get_slo_report, get_trend_analysis
```

---

## 1. OBSERVE: Service Health Aggregation

`ServiceHealthAggregator` maintains real-time health metrics computed from span data.

### Recording Metrics

```java
ServiceHealthAggregator health = new ServiceHealthAggregator(rollingBaselines, sloTracker);

// Called from SpanProcessor or interceptor
health.recordSpan("POST /api/payments", 312.0, false);
health.recordDependencyCall("stripe-api", 2100.0, true);
```

### Querying Health

```java
// Full service summary
ServiceHealthSummary summary = health.getHealthSummary("payment-service");
// summary.status()       --> DEGRADED
// summary.operations()   --> List<OperationSummary>
// summary.dependencies() --> List<DependencySummary>

// Single operation
Optional<OperationSummary> op = health.getOperationHealth("POST /api/payments");
// op.errorRate()      --> 0.052
// op.latencyP50Ms()   --> 312.0
// op.deviationStatus() --> "elevated"
```

### Health Status Determination

| Condition | Status |
|-----------|--------|
| Any SLO with < 10% budget remaining | `CRITICAL` |
| Any operation with > 10% error rate (100+ requests) | `CRITICAL` |
| Any operation with > 1% error rate (100+ requests) | `DEGRADED` |
| Any dependency with > 50% error rate (10+ calls) | `DEGRADED` |
| Any SLO with < 50% budget remaining | `DEGRADED` |
| None of the above | `HEALTHY` |

---

## 2. DIAGNOSE: Incident Context Builder

`IncidentContextBuilder` assembles a complete incident package from current system state.

### Structure

Every `IncidentContext` contains four sections designed for LLM reasoning:

| Section | Record | Contents |
|---------|--------|----------|
| What Is Happening | `WhatIsHappening` | Operation name, current vs baseline metrics, detected patterns, anomaly score |
| What Changed | `WhatChanged` | Recent deployments, config changes, with timestamps |
| What Is Affected | `WhatIsAffected` | Affected operations, dependencies, consumers, impact scope, user-facing flag |
| What To Do | `WhatToDo` | Runbook URL, escalation level, suggested actions with confidence and approval requirements |

Plus: severity (LOW/MEDIUM/HIGH/CRITICAL) and similar past incidents.

### Severity Determination

| Condition | Severity |
|-----------|----------|
| Service health is CRITICAL | `CRITICAL` |
| Cascade failure pattern detected | `CRITICAL` |
| Error rate > 10% | `HIGH` |
| Service health is DEGRADED | `MEDIUM` |
| Default | `LOW` |

### Change Tracking

```java
IncidentContextBuilder builder = new IncidentContextBuilder(
    healthAggregator, topology, rollingBaselines, remediationRegistry);

// Record changes for correlation
builder.recordDeployment("v2.1.0", "2025-01-15T14:00:00Z");
builder.recordConfigChange("Updated rate limit to 500 rps");

// Record historical incidents for pattern matching
builder.recordHistoricalIncident("inc-2024-dec-03", "2024-12-03T10:00:00Z",
    "Increased timeout to 10s", "stripe-api timeout");
```

### Change Correlation Engine

`ChangeCorrelationEngine` answers the critical question: *"What changed right before this broke?"*

#### Recording Changes

```java
ChangeCorrelationEngine engine = new ChangeCorrelationEngine();

engine.recordDeployment("deploy-v2.1.0", "v2.1.0", "New payment processing logic");
engine.recordConfigChange("config-123", "Updated rate limit to 500 rps");
engine.recordChange(new ChangeEvent("scale-1", ChangeType.SCALING,
    "Scaled from 3 to 5 instances", Instant.now()));
```

#### Correlation

```java
CorrelationResult result = engine.correlate(Instant.now());
// result.likelyCause()     --> DEPLOYMENT
// result.changeId()        --> "deploy-v2.1.0"
// result.timeDeltaMs()     --> 1800000 (30 minutes ago)
// result.confidence()      --> 0.85
```

#### Confidence Scoring

Confidence is computed from:

1. **Time proximity** -- changes closer to anomaly onset score higher
2. **Change type weight** -- deployments (1.0) > config changes (0.8) > scaling (0.6) > feature flags (0.7) > dependency updates (0.9)
3. Bounded to 200 recent changes, configurable correlation window (default 15 minutes)

---

## 3. PLAN: Structured Playbooks

`PlaybookRegistry` provides machine-readable, step-by-step remediation playbooks that replace opaque runbook URLs. Default playbooks are pre-registered for common incident patterns.

### Playbook Structure

Each `Playbook` contains:

- **name** -- Playbook identifier
- **description** -- What this playbook addresses
- **triggerPatterns** -- Which `IncidentPattern`s activate it
- **steps** -- Ordered list of `PlaybookStep` records

### Step Types

| Type | Description | Example |
|------|-------------|---------|
| `CHECK` | Evaluate a condition | "Check if error rate > baseline * 5" |
| `ACTION` | Execute a remediation action | "Enable circuit breaker" |
| `DECISION` | Branch based on condition | "Is this a dependency issue?" |

### Default Playbooks

| Playbook | Trigger Pattern | Key Steps |
|----------|----------------|-----------|
| Cascade Failure Response | `CASCADE_FAILURE` | Identify failing deps -- circuit break -- verify -- scale if needed |
| Error Rate Spike Response | `ERROR_RATE_SPIKE` | Classify errors -- circuit break or rollback -- verify |
| Latency Degradation Response | `LATENCY_DEGRADATION` | Check resource usage -- scale -- optimize or rollback |
| Memory Leak Response | `MEMORY_LEAK` | Confirm trend -- rolling restart -- monitor -- investigate |

### Custom Playbooks

```java
PlaybookRegistry registry = new PlaybookRegistry(); // defaults pre-registered

Playbook custom = new Playbook(
    "payment-gateway-failover",
    "Failover to backup payment gateway",
    List.of(IncidentPattern.CASCADE_FAILURE),
    List.of(
        Playbook.PlaybookStep.check("1", "Verify primary gateway is down",
            "payment-gateway health check fails", "2", null),
        Playbook.PlaybookStep.action("2", "Switch to backup gateway",
            "switch_gateway", false, "3", null),
        Playbook.PlaybookStep.check("3", "Verify backup gateway is healthy",
            "backup-gateway health check passes", null, null)
    )
);
registry.register(custom);
```

---

## 4. ACT: Remediation Framework

### Defining Actions

```java
RemediationAction rollback = RemediationAction.builder("rollback_deployment", "POST /api/payments")
    .description("Rollback to previous known-good version")
    .type(RemediationAction.ActionType.ROLLBACK)
    .requiresApproval(true)
    .command("kubectl rollout undo deployment/payment-service")
    .build();

RemediationAction circuitBreak = RemediationAction.builder("circuit_break_stripe", "POST /api/payments")
    .description("Enable circuit breaker on stripe-api dependency")
    .type(RemediationAction.ActionType.CIRCUIT_BREAKER)
    .requiresApproval(false)
    .build();
```

### Registering Actions

```java
RemediationRegistry registry = new RemediationRegistry();

// Operation-specific actions
registry.register(rollback);
registry.register(circuitBreak);

// Global actions (apply to all operations)
registry.registerGlobal(RemediationAction.builder("enable_debug_logging", "*")
    .description("Enable DEBUG logging for 5 minutes")
    .type(RemediationAction.ActionType.CUSTOM)
    .requiresApproval(false)
    .build());
```

### Executing Actions

```java
RemediationExecutor executor = new RemediationExecutor(registry, actionTracker, feedbackLoop);

// Auto-approved action (verification auto-scheduled if feedbackLoop is set)
RemediationResult result = executor.execute("circuit_break_stripe", "stripe-api error rate at 12%");
// result.verificationScheduled() --> true

// Action requiring approval
RemediationResult result = executor.executeApproved(
    "rollback_deployment",
    "Error rate spike after v2.1.0 deployment",
    "oncall-engineer@company.com"
);

// Check verification outcome later
Optional<ActionOutcome> outcome = executor.getActionOutcome("circuit_break_stripe");
```

### Action Types

| Type | Description |
|------|-------------|
| `RESTART` | Rolling restart of service instances |
| `SCALE` | Horizontal or vertical scaling |
| `ROLLBACK` | Deployment rollback |
| `CIRCUIT_BREAKER` | Enable/modify circuit breaker |
| `RATE_LIMIT` | Adjust rate limiting |
| `CACHE_FLUSH` | Flush application caches |
| `CUSTOM` | Domain-specific action |

### Parameterized Action Specs

Remediation actions can include structured specifications that tell agents *exactly* what parameters to use.

#### Spec Types

| Spec Type | Fields | Example |
|-----------|--------|---------|
| `RetrySpec` | maxAttempts, backoffMs, retryOnStatusCodes, retryOnExceptions | `retry(max=3, backoff=[100, 200, 400])` |
| `ScaleSpec` | direction, minInstances, maxInstances, cooldownSeconds | `scale(up, min=3, max=10, cooldown=300s)` |
| `CircuitBreakerSpec` | failureThreshold, halfOpenAfterMs, successThreshold | `circuit_breaker(threshold=5, half_open_after=30000ms)` |
| `RateLimitSpec` | requestsPerSecond, burstSize | `rate_limit(100 rps, burst=20)` |
| `GenericSpec` | parameters (Map) | `params={key=value}` |

#### Usage

```java
RemediationAction action = RemediationAction.builder("retry_payment", "POST /api/payments")
    .description("Retry failed payment with exponential backoff")
    .type(RemediationAction.ActionType.CUSTOM)
    .spec(new ActionSpec.RetrySpec(3, List.of(100L, 200L, 400L),
        List.of(502, 503), List.of("SocketTimeoutException"), List.of(400, 401)))
    .build();
```

### Agent Action Tracking

Every decision and action taken by an AI agent is recorded as an OpenTelemetry span for full auditability.

#### Recording Actions

```java
AgentActionTracker tracker = new AgentActionTracker(openTelemetry);

// Simple action record
tracker.recordAction("scale_up", "High latency detected",
    Map.of("instances", "3", "reason", "p50 > 2x baseline"));

// Decision with rationale
tracker.recordDecision(
    "response_strategy",
    "Error rate rising but not critical -- prefer conservative approach",
    "increase_timeout",
    List.of("increase_timeout", "add_retry", "circuit_break", "rollback")
);

// Traced action (captures success/failure)
String result = tracker.traceAction("compute_recommendation", "Need action plan", () -> {
    // Complex computation...
    return "scale_up";
});
```

#### Span Attributes

Each tracked action creates a span with:

| Attribute | Description |
|-----------|-------------|
| `agenttel.agent.action.name` | Action identifier |
| `agenttel.agent.action.reason` | Why the action was taken |
| `agenttel.agent.action.status` | `"completed"`, `"success"`, or `"failed"` |
| `agenttel.agent.action.type` | `"action"`, `"decision"`, or `"traced_action"` |
| `agenttel.agent.decision.rationale` | Reasoning (for decisions) |
| `agenttel.agent.decision.chosen` | Selected option (for decisions) |
| `agenttel.agent.decision.options` | All options considered (for decisions) |

---

## 5. VERIFY: Action Feedback Loop

`ActionFeedbackLoop` closes the observe-act-verify cycle by automatically verifying whether a remediation action was effective.

### How It Works

1. Agent executes remediation via `RemediationExecutor`
2. `ActionFeedbackLoop` captures a pre-action health snapshot
3. After a configurable delay (default 30s), captures a post-action snapshot
4. Computes `ActionOutcome` -- was the action effective?

### Outcome Fields

| Field | Type | Description |
|-------|------|-------------|
| `actionName` | string | The remediation action that was executed |
| `effective` | boolean | Whether the action improved health |
| `latencyDeltaMs` | double | Change in average P50 latency (negative = improved) |
| `errorRateDelta` | double | Change in average error rate (negative = improved) |
| `preHealthStatus` | string | Health status before action |
| `postHealthStatus` | string | Health status after action |
| `verifiedAt` | string | Timestamp of verification |

### Effectiveness Criteria

An action is considered effective if any of:

- Error rate decreased by > 0.1%
- Average P50 latency decreased by > 5ms
- Health status improved (e.g., DEGRADED --> HEALTHY)

---

## Next Steps

- **[MCP Tools Reference](../reference/mcp-tools.md)** -- full details for `get_incident_context`, `get_playbook`, `execute_remediation`, and `verify_remediation_effect`
- **[Multi-Agent Patterns](multi-agent.md)** -- coordinate multiple agents around a shared incident session
- **[MCP Server & Agent Tools](mcp-server.md)** -- set up the MCP server and Spring Boot integration
