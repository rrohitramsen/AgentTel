# Quick Start: Python

Get AgentTel running in a Python service in under 5 minutes.

## Prerequisites

- Python 3.11+
- An OpenTelemetry Collector (or Jaeger) for viewing traces

## Installation

```bash
pip install agenttel[fastapi]
```

## 1. Create `agenttel.yml`

Create a configuration file in your project root:

```yaml
agenttel:
  enabled: true
  topology:
    team: payments
    tier: critical
    domain: fintech
    on-call-channel: "#payments-oncall"

  dependencies:
    - name: postgres
      type: database
      criticality: required
      timeout-ms: 3000
    - name: stripe-api
      type: external_api
      criticality: degraded
      timeout-ms: 5000
      circuit-breaker: true
      fallback: "Queue payment for retry"

  consumers:
    - name: notification-service
      pattern: async

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
      expected-error-rate: 0.001

  baselines:
    rolling-window-size: 1000
    rolling-min-samples: 10

  anomaly-detection:
    enabled: true
    z-score-threshold: 3.0

  deployment:
    emit-on-startup: true
```

## 2. Instrument Your FastAPI App

```python
from fastapi import FastAPI
from agenttel.fastapi import instrument_fastapi

app = FastAPI(title="Payment Service")

# One line to instrument everything
engine = instrument_fastapi(app)


@app.post("/api/transfers")
async def create_transfer(request: dict):
    # This span automatically gets:
    # - agenttel.topology.team = "payments"
    # - agenttel.topology.tier = "critical"
    # - agenttel.operation.retryable = false
    # - agenttel.operation.runbook_url = "https://..."
    # - agenttel.baseline.latency_p50_ms = 80.0
    # - Anomaly detection on span end
    # - SLO budget tracking
    return {"transfer_id": "txn_123", "status": "completed"}


@app.get("/api/accounts/{account_id}")
async def get_account(account_id: str):
    return {"id": account_id, "balance": 1000.00}
```

## 3. Run

```bash
# Start an OTel Collector + Jaeger (if not already running)
docker run -d --name jaeger \
  -p 16686:16686 \
  -p 4317:4317 \
  -p 4318:4318 \
  jaegertracing/all-in-one:latest

# Set OTLP exporter endpoint
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
export OTEL_SERVICE_NAME=payment-service

# Run your app
uvicorn main:app --reload
```

## 4. View Traces

Open [http://localhost:16686](http://localhost:16686) (Jaeger UI) and search for traces from `payment-service`. Each span will have AgentTel enrichment attributes.

## What Happens Automatically

When you call `instrument_fastapi(app)`:

1. **Config Loading** — `agenttel.yml` is parsed and validated
2. **Topology** — Team, tier, domain attributes are set on every span
3. **Operation Context** — Retryable, runbook, escalation level are set per-route
4. **Static Baselines** — Expected latency P50/P99 are attached to spans
5. **Rolling Baselines** — A ring buffer tracks actual latency per operation
6. **Anomaly Detection** — Z-score analysis fires when latency deviates from baseline
7. **SLO Tracking** — Error budgets are computed for registered SLOs
8. **Events** — Anomaly and SLO alerts are emitted as OTel log events

## Next Steps

- [FastAPI Integration Guide](../guides/python-fastapi.md) — Middleware details, decorators, profiles
- [GenAI Instrumentation](../guides/python-genai.md) — Instrument OpenAI, Anthropic, LangChain
- [Configuration Reference](../reference/configuration.md) — Full `agenttel.yml` reference
- [Python API Reference](../reference/python-api.md) — Complete API documentation
