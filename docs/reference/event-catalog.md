# Event Catalog

Complete reference for every structured event AgentTel emits via the OpenTelemetry Logs API.

---

## Overview

AgentTel emits events for significant state changes that AI agents should react to. Events are
emitted as OTel log records with a structured JSON body using the `AgentTelEventEmitter` class,
which wraps the OTel Logs Bridge API.

All events use:

- **`event.name` attribute** (OTel standard) to identify the event type
- **Severity level:** INFO, WARN, or ERROR
- **JSON-serialized body** with event-specific fields

Event names are defined as constants in `io.agenttel.api.events.AgentTelEvents`.

### Event Summary

| Event | Severity | Trigger | Agent Action |
|-------|----------|---------|--------------|
| `agenttel.anomaly.detected` | WARN | Span deviates from baseline | Investigate via `get_incident_context` |
| `agenttel.slo.budget_alert` | WARN / ERROR | SLO budget crosses threshold | Check SLO compliance via `get_slo_report` |
| `agenttel.dependency.state_change` | WARN | Dependency health transitions | Correlate with operation health |
| `agenttel.circuit_breaker.state_change` | WARN | Circuit breaker transitions | Monitor self-protection status |
| `agenttel.deployment.info` | INFO | Application starts | Record for change correlation |

### How Events Are Emitted

All events flow through `AgentTelEventEmitter`, which serializes the body map to JSON and
emits an OTel `LogRecord`:

```java
otelLogger.logRecordBuilder()
    .setSeverity(severity)
    .setAttribute(AttributeKey.stringKey("event.name"), eventName)
    .setBody(bodyJson)
    .emit();
```

Events are transported through the standard OTel Logs pipeline, meaning they appear in any
configured OTel log exporter (OTLP, console, etc.).

---

## agenttel.anomaly.detected

Emitted when a span's behavior deviates significantly from baseline. The anomaly detector
uses z-score comparison: if the absolute z-score of the observed latency exceeds the configured
threshold (default: 3.0), the span is classified as anomalous. Pattern matching may also
trigger this event independently when higher-level incident patterns (cascade failure,
thundering herd, etc.) are detected.

!!! warning "Trigger"
    When a span's latency z-score exceeds the configured threshold (default: 3.0), OR when
    the `PatternMatcher` detects a known incident pattern from accumulated span data.

### Emitter

- **Class:** `AgentTelEventEmitter` (called from `AgentTelSpanProcessor.onEnd()`)
- **Constant:** `AgentTelEvents.ANOMALY_DETECTED`

### Fields

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| `operation` | string | Yes | The operation that triggered the anomaly | `"POST /api/payments"` |
| `latency_ms` | double | Yes | Actual latency observed for this span (ms) | `312.0` |
| `anomaly_score` | double | No | Severity score normalized to 0.0-1.0, calculated as `min(1.0, abs(z_score) / 4.0)` | `0.85` |
| `z_score` | double | No | Standard deviations from the rolling baseline mean | `4.2` |
| `pattern` | string | No | Incident pattern identifier (present when pattern matching triggers the event) | `"latency_degradation"` |
| `pattern_description` | string | No | Human-readable description of the detected pattern | `"Sustained latency increase beyond baseline"` |

When the event is triggered by z-score anomaly detection, `anomaly_score` and `z_score` are
present but `pattern` and `pattern_description` are absent. When triggered by pattern matching,
`pattern` and `pattern_description` are present but `anomaly_score` and `z_score` are absent.
Both sets of fields may be present in a combined event.

#### Incident Patterns

The `pattern` field uses one of the following values, defined in `IncidentPattern`:

| Pattern | Value | Description |
|---------|-------|-------------|
| Cascade Failure | `cascade_failure` | Multiple dependent services failing simultaneously |
| Latency Degradation | `latency_degradation` | Sustained latency increase beyond baseline |
| Error Rate Spike | `error_rate_spike` | Sudden increase in error rate beyond baseline |
| Memory Leak | `memory_leak` | Steadily increasing latency with increasing error rate |
| Thundering Herd | `thundering_herd` | Sudden spike in request rate after recovery |
| Cold Start | `cold_start` | High latency on first requests after deployment |

### Example Payload

Z-score triggered anomaly:

```json
{
  "event.name": "agenttel.anomaly.detected",
  "severity": "WARN",
  "body": {
    "operation": "POST /api/payments",
    "latency_ms": 312.0,
    "anomaly_score": 0.85,
    "z_score": 4.2
  }
}
```

Pattern-matching triggered anomaly:

```json
{
  "event.name": "agenttel.anomaly.detected",
  "severity": "WARN",
  "body": {
    "operation": "POST /api/payments",
    "latency_ms": 312.0,
    "pattern": "latency_degradation",
    "pattern_description": "Sustained latency increase beyond baseline"
  }
}
```

### Agent Workflow

When this event fires, the recommended agent workflow is:

1. Call `get_incident_context` with the operation name to get full diagnosis including
   baselines, dependencies, and change correlation
2. Call `get_error_analysis` with the operation name to understand error category breakdown
3. Call `get_playbook` with the pattern name to get a structured remediation plan
4. If the playbook confidence is high enough, call `list_remediation_actions` to see
   available automated fixes
5. Execute remediation via `execute_remediation` if the action does not require human
   approval, or escalate otherwise

### Related

- **Span attributes:** `agenttel.anomaly.detected` (boolean), `agenttel.anomaly.pattern` (string), `agenttel.anomaly.score` (double), `agenttel.anomaly.latency_z_score` (double)
- **MCP tools:** `get_incident_context`, `get_error_analysis`, `get_playbook`, `execute_remediation`
- **Configuration:** `agenttel.anomaly-detection.z-score-threshold` (default: `3.0`)
- **Source:** `AgentTelSpanProcessor` in `agenttel-core`

---

## agenttel.slo.budget_alert

Emitted when an SLO's error budget crosses a threshold. The `SloTracker` checks all registered
SLOs after every span and fires an alert when budget remaining falls below 50%, 25%, or 10%.
The OTel severity escalates with the alert level: INFO at 50%, WARN at 25%, ERROR at 10%.

!!! warning "Trigger"
    When the remaining error budget for any registered SLO drops below 50%, 25%, or 10%.

### Emitter

- **Class:** `AgentTelEventEmitter` (called from `AgentTelSpanProcessor.emitSloAlerts()` via `SloTracker`)
- **Constant:** `AgentTelEvents.SLO_BUDGET_ALERT`

### Fields

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| `slo_name` | string | Yes | SLO identifier as registered in configuration | `"payment-availability"` |
| `severity` | string | Yes | Alert tier: `CRITICAL` (<=10%), `WARNING` (<=25%), `INFO` (<=50%) | `"WARNING"` |
| `budget_remaining` | double | Yes | Remaining budget as a fraction 0.0-1.0 | `0.22` |
| `burn_rate` | double | Yes | Current burn rate: how fast the budget is being consumed. A value of 1.0 means the budget will be exactly exhausted at the end of the window. | `0.78` |

#### Alert Severity Tiers

| Tier | Budget Remaining | OTel Severity | Recommended Response |
|------|-----------------|---------------|---------------------|
| `INFO` | <= 50% | INFO | Monitor, no immediate action |
| `WARNING` | <= 25% | WARN | Investigate proactively |
| `CRITICAL` | <= 10% | ERROR | Immediate investigation required |

### Example Payload

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

Critical budget exhaustion:

```json
{
  "event.name": "agenttel.slo.budget_alert",
  "severity": "ERROR",
  "body": {
    "slo_name": "payment-availability",
    "severity": "CRITICAL",
    "budget_remaining": 0.05,
    "burn_rate": 1.42
  }
}
```

### Agent Workflow

When this event fires, the recommended agent workflow depends on the severity tier:

**INFO (budget <= 50%):**
1. Call `get_slo_report` to review overall SLO posture
2. Log the observation; no immediate action required

**WARNING (budget <= 25%):**
1. Call `get_slo_report` to confirm budget trajectory
2. Call `get_trend_analysis` for the affected operation to understand if the trend is
   worsening or stabilizing
3. If worsening, call `get_incident_context` to begin proactive investigation

**CRITICAL (budget <= 10%):**
1. Call `get_incident_context` immediately for the affected operation
2. Call `get_error_analysis` to identify the primary error contributors
3. Call `get_playbook` for the relevant pattern
4. Execute remediation or escalate to on-call

### Related

- **Span attributes:** `agenttel.slo.name` (string), `agenttel.slo.target` (double), `agenttel.slo.budget_remaining` (double), `agenttel.slo.burn_rate` (double)
- **MCP tools:** `get_slo_report`, `get_trend_analysis`, `get_incident_context`
- **Configuration:** SLO definitions in `agenttel.slos` configuration block
- **Source:** `SloTracker` in `agenttel-core`

---

## agenttel.dependency.state_change

Emitted when a dependency's observed health transitions from one state to another (e.g.,
healthy to degraded, degraded to unhealthy, or back to healthy). State transitions are tracked
by the `CausalityTracker`, which monitors client-span error rates per dependency.

!!! warning "Trigger"
    When the observed health state of a dependency changes based on client-span error rates.

### Emitter

- **Class:** `AgentTelEventEmitter`
- **Constant:** `AgentTelEvents.DEPENDENCY_STATE_CHANGE`

### Fields

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| `dependency` | string | Yes | Dependency name as declared in configuration | `"postgres"` |
| `previous_state` | string | Yes | Previous health state: `healthy`, `degraded`, or `unhealthy` | `"healthy"` |
| `current_state` | string | Yes | New health state | `"degraded"` |
| `error_rate` | double | Yes | Current error rate observed for this dependency (0.0-1.0) | `0.15` |

### Example Payload

Dependency degradation:

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

Dependency recovery:

```json
{
  "event.name": "agenttel.dependency.state_change",
  "severity": "WARN",
  "body": {
    "dependency": "postgres",
    "previous_state": "degraded",
    "current_state": "healthy",
    "error_rate": 0.01
  }
}
```

### Agent Workflow

When this event fires, the recommended agent workflow is:

1. Call `get_service_health` to see the full dependency map and which operations are affected
2. Correlate with any concurrent `agenttel.anomaly.detected` events -- if postgres goes
   degraded and `POST /api/payments` errors spike simultaneously, the dependency is likely
   the root cause
3. Call `get_incident_context` for any operations that depend on the affected dependency
4. If the dependency transitions to `unhealthy`, check whether a circuit breaker is protecting
   the system by looking for a corresponding `agenttel.circuit_breaker.state_change` event

### Related

- **MCP tools:** `get_service_health`, `get_incident_context`, `get_cross_stack_context`
- **Configuration:** Dependency declarations via `@DeclareDependency` annotation or `agenttel.dependencies` YAML block
- **Source:** `CausalityTracker` in `agenttel-core`

---

## agenttel.circuit_breaker.state_change

Emitted when a circuit breaker transitions between states. Circuit breakers protect operations
from cascading failures by temporarily stopping calls to an unhealthy dependency.

!!! warning "Trigger"
    When a circuit breaker transitions state: closed to open, open to half_open, or
    half_open to closed/open.

### Emitter

- **Class:** `AgentTelEventEmitter`
- **Constant:** `AgentTelEvents.CIRCUIT_BREAKER_STATE_CHANGE`

### Fields

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| `name` | string | Yes | Circuit breaker identifier | `"postgres-cb"` |
| `previous_state` | string | Yes | Previous state: `closed`, `open`, or `half_open` | `"closed"` |
| `new_state` | string | Yes | New state after transition | `"open"` |
| `failure_count` | long | Yes | Accumulated failure count that triggered the transition | `15` |
| `dependency` | string | Yes | Name of the associated dependency | `"postgres"` |

#### Circuit Breaker States

| State | Meaning |
|-------|---------|
| `closed` | Normal operation. Requests flow through to the dependency. |
| `open` | Dependency is unhealthy. Requests are rejected immediately (fast-fail). |
| `half_open` | Testing recovery. A limited number of requests are allowed through to probe the dependency. |

### Example Payload

Circuit breaker opens due to failures:

```json
{
  "event.name": "agenttel.circuit_breaker.state_change",
  "severity": "WARN",
  "body": {
    "name": "postgres-cb",
    "previous_state": "closed",
    "new_state": "open",
    "failure_count": 15,
    "dependency": "postgres"
  }
}
```

Circuit breaker starts recovery probe:

```json
{
  "event.name": "agenttel.circuit_breaker.state_change",
  "severity": "WARN",
  "body": {
    "name": "postgres-cb",
    "previous_state": "open",
    "new_state": "half_open",
    "failure_count": 15,
    "dependency": "postgres"
  }
}
```

Circuit breaker recovery succeeds:

```json
{
  "event.name": "agenttel.circuit_breaker.state_change",
  "severity": "WARN",
  "body": {
    "name": "postgres-cb",
    "previous_state": "half_open",
    "new_state": "closed",
    "failure_count": 0,
    "dependency": "postgres"
  }
}
```

### Agent Workflow

When this event fires, the recommended agent workflow is:

1. If the transition is to `open`: the system is self-protecting. Call `get_service_health`
   to assess impact on dependent operations. No remediation is needed for the circuit breaker
   itself -- focus on the underlying dependency.
2. If the transition is to `half_open`: recovery is being tested. Monitor for a subsequent
   transition back to `closed` (success) or `open` (failure).
3. If the transition is to `closed`: the dependency has recovered. Call `get_trend_analysis`
   to verify the recovery is stable and not a brief respite.
4. Correlate with `agenttel.dependency.state_change` events for the same dependency to build
   a complete timeline of the incident.

### Related

- **MCP tools:** `get_service_health`, `get_incident_context`, `get_trend_analysis`
- **Configuration:** `circuitBreaker = true` on `@DeclareDependency` annotation or in YAML dependency declaration
- **Source:** `AgentTelEventEmitter` in `agenttel-core`

---

## agenttel.deployment.info

Emitted once at application startup when `agenttel.deployment.emit-on-startup` is true
(the default). Captures deployment metadata that the `ChangeCorrelationEngine` uses to
correlate anomalies with recent deployments.

!!! info "Trigger"
    At application startup, if deployment event emission is enabled.

### Emitter

- **Class:** `DeploymentEventEmitter`
- **Constant:** `AgentTelEvents.DEPLOYMENT_INFO`

### Fields

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| `version` | string | No | Application version | `"2.3.1"` |
| `commit_sha` | string | No | Git commit SHA of the deployed code | `"a1b2c3d"` |
| `previous_version` | string | No | Version of the previous deployment | `"2.3.0"` |
| `strategy` | string | No | Deployment strategy used | `"blue-green"` |
| `timestamp` | string | Yes | ISO 8601 timestamp of when the event was emitted | `"2026-03-06T14:30:00Z"` |

All fields except `timestamp` are optional. Only non-empty values are included in the body.
The `timestamp` field is always present and set to `Instant.now().toString()` at emission time.

### Example Payload

Full deployment event:

```json
{
  "event.name": "agenttel.deployment.info",
  "severity": "INFO",
  "body": {
    "version": "2.3.1",
    "commit_sha": "a1b2c3d",
    "previous_version": "2.3.0",
    "strategy": "blue-green",
    "timestamp": "2026-03-06T14:30:00Z"
  }
}
```

Minimal deployment event (only version known):

```json
{
  "event.name": "agenttel.deployment.info",
  "severity": "INFO",
  "body": {
    "version": "2.3.1",
    "timestamp": "2026-03-06T14:30:00Z"
  }
}
```

### Agent Workflow

When this event fires, the recommended agent workflow is:

1. Record the deployment metadata for future correlation
2. If an `agenttel.anomaly.detected` event fires within minutes of a deployment, call
   `get_incident_context` -- the `ChangeCorrelationEngine` will automatically flag the
   deployment as a probable cause
3. Compare `version` and `previous_version` to understand whether this is a major or
   minor release, which affects rollback risk assessment

### Related

- **Span attributes:** `agenttel.deployment.id`, `agenttel.deployment.version`, `agenttel.deployment.commit_sha`, `agenttel.deployment.previous_version`, `agenttel.deployment.strategy`, `agenttel.deployment.timestamp`
- **MCP tools:** `get_incident_context` (includes change correlation data)
- **Configuration:** `agenttel.deployment.emit-on-startup` (default: `true`), `agenttel.deployment.version`, `agenttel.deployment.commit-sha`, `agenttel.deployment.previous-version`, `agenttel.deployment.strategy`
- **Source:** `DeploymentEventEmitter` in `agenttel-core`

---

## Configuration Reference

Events are controlled through the standard AgentTel configuration. Below are the properties
that affect event emission.

### Anomaly Detection

```yaml
agenttel:
  anomaly-detection:
    z-score-threshold: 3.0    # Z-score above which a span is anomalous (default: 3.0)
```

Lowering the threshold produces more events (higher sensitivity); raising it reduces noise.

### SLO Definitions

SLO budget alerts require SLOs to be registered. Registration happens through the
`agenttel.slos` configuration block:

```yaml
agenttel:
  slos:
    payment-availability:
      operation-name: "POST /api/payments"
      target: 0.999            # 99.9% availability target
```

### Deployment Events

```yaml
agenttel:
  deployment:
    emit-on-startup: true      # Emit deployment.info at startup (default: true)
    version: "2.3.1"
    commit-sha: "a1b2c3d"
    previous-version: "2.3.0"
    strategy: "blue-green"
```

---

## Consuming Events

### OTel Log Exporter

Events are emitted as OTel log records and are exported through the configured OTel log
exporter. To see events in the console during development:

```yaml
otel:
  logs:
    exporter: logging
```

To export to an OTLP-compatible backend:

```yaml
otel:
  exporter:
    otlp:
      endpoint: "http://localhost:4317"
```

### Programmatic Subscription

Events flow through the standard OTel Logs pipeline. To process events programmatically,
configure a custom `LogRecordProcessor` in your OTel SDK setup and filter on the `event.name`
attribute.

### MCP Agent Integration

AI agents connected via the MCP server do not receive events directly through the event
pipeline. Instead, events trigger state changes that agents observe through MCP tool calls:

- Anomaly events update the `ServiceHealthAggregator`, visible via `get_service_health`
- SLO budget alerts update `SloTracker` state, visible via `get_slo_report`
- Dependency state changes update the `CausalityTracker`, visible via `get_incident_context`

For real-time event-driven agent workflows, configure your OTel log exporter to push events
to a message queue or webhook that your agent framework consumes.
