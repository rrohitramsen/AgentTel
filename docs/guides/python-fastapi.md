# FastAPI Integration Guide

AgentTel provides first-class FastAPI support with middleware, decorators, and auto-configuration.

## Setup

```bash
pip install agenttel[fastapi]
```

## One-Line Integration

```python
from fastapi import FastAPI
from agenttel.fastapi import instrument_fastapi

app = FastAPI()
engine = instrument_fastapi(app, config_path="agenttel.yml")
```

This single call:

1. Loads configuration from `agenttel.yml`
2. Creates an `AgentTelEngine` with all components
3. Installs the `AgentTelSpanProcessor` into the OTel SDK
4. Adds `AgentTelMiddleware` to your FastAPI app
5. Emits a deployment event (if configured)

## Middleware

The `AgentTelMiddleware` runs on every request and:

- **Resolves route templates** — Converts `/users/123` to `/users/{id}` using FastAPI's router
- **Sets `http.route`** on the OTel span for proper operation grouping
- **Attaches topology attributes** — Team, tier, domain, on-call channel
- **Applies operation context** — Looks up the route in `agenttel.yml` operations and sets retryable, runbook, escalation level, etc.

## `@agent_operation` Decorator

For fine-grained control, use the decorator directly on route handlers:

```python
from agenttel.fastapi import agent_operation

@app.post("/api/transfers")
@agent_operation(
    expected_latency_p50="80ms",
    expected_latency_p99="300ms",
    expected_error_rate=0.002,
    retryable=False,
    idempotent=False,
    runbook_url="https://wiki.example.com/runbooks/transfers",
    escalation_level="page_oncall",
    safe_to_restart=False,
    profile="critical-write",
)
async def create_transfer(request: dict):
    return {"status": "completed"}
```

The decorator sets all attributes on the current span. It works with both sync and async functions.

### Decorator Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `expected_latency_p50` | `str` | Expected P50 latency (e.g., `"100ms"`, `"2s"`) |
| `expected_latency_p99` | `str` | Expected P99 latency |
| `expected_error_rate` | `float` | Expected error rate (0.0 - 1.0) |
| `retryable` | `bool` | Whether the operation can be safely retried |
| `idempotent` | `bool` | Whether repeated calls produce the same result |
| `runbook_url` | `str` | Link to the operational runbook |
| `escalation_level` | `str` | Escalation level (`notify_team`, `page_oncall`, etc.) |
| `safe_to_restart` | `bool` | Whether it's safe to restart the service mid-operation |
| `profile` | `str` | Named profile from `agenttel.yml` |

## Auto-Configuration

For full control over the OTel `TracerProvider` setup:

```python
from agenttel.fastapi import auto_configure

engine = auto_configure(
    app,
    config_path="agenttel.yml",
    service_name="payment-service",  # Override service.name
)
```

This creates a `TracerProvider` with topology as resource attributes and registers the span processor.

## Configuration via `agenttel.yml`

### Operation Profiles

Define reusable profiles:

```yaml
agenttel:
  profiles:
    critical-write:
      retryable: false
      idempotent: false
      escalation-level: page_oncall
      safe-to-restart: false
    read-only:
      retryable: true
      idempotent: true
      escalation-level: notify_team
      safe-to-restart: true
```

### Per-Operation Config

Map routes to profiles and baselines:

```yaml
agenttel:
  operations:
    "[POST /api/transfers]":
      profile: critical-write
      expected-latency-p50: "80ms"
      expected-latency-p99: "300ms"
      expected-error-rate: 0.002
      runbook-url: "https://wiki.example.com/runbooks/transfers"
    "[GET /api/accounts/{id}]":
      profile: read-only
      expected-latency-p50: "10ms"
      expected-latency-p99: "50ms"
```

The operation key format is `[METHOD /path/template]`, matching the `http.route` attribute.

## Accessing the Engine

After instrumentation, you can access all AgentTel components:

```python
engine = instrument_fastapi(app)

# Access components
engine.topology                # TopologyRegistry
engine.baseline_provider       # CompositeBaselineProvider
engine.rolling_baselines       # RollingBaselineProvider
engine.anomaly_detector        # AnomalyDetector
engine.slo_tracker             # SloTracker
engine.error_classifier        # ErrorClassifier
engine.causality_tracker       # CausalityTracker
engine.event_emitter           # AgentTelEventEmitter
```
