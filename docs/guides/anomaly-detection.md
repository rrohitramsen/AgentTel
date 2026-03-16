# Anomaly Detection

Real-time deviation detection on both backend (JVM) and frontend (browser) — using z-score analysis, pattern matching, and rolling baselines.

---

## Why Anomaly Detection for Agents?

Traditional alerting relies on static thresholds ("alert if error rate > 5%"). This creates two problems for AI agents:

- **Alert fatigue** — thresholds are either too sensitive (noisy) or too loose (miss real issues)
- **No context** — an alert says "threshold breached" but not "how abnormal is this compared to recent behavior?"

AgentTel's anomaly detection compares every span against what's **actually normal** for that operation, using rolling baselines learned from live traffic. An agent gets a continuous anomaly score, not a binary alert.

---

## How It Works

### Z-Score Detection

For every operation span, AgentTel computes a z-score — how many standard deviations the observed latency is from the rolling baseline mean:

```
z_score = (observed_latency - baseline_mean) / baseline_stddev
```

If `abs(z_score) > threshold` (default: 3.0), the span is classified as anomalous.

| Z-Score | Meaning | Anomaly Score |
|---------|---------|---------------|
| < 3.0 | Normal | 0.0 |
| 3.0 | Threshold (3 sigma) | 0.75 |
| 4.0 | Significant deviation | 1.0 |
| > 4.0 | Extreme deviation | 1.0 (capped) |

The **anomaly score** is normalized to 0.0–1.0 using `min(1.0, abs(z_score) / 4.0)`, giving agents a continuous severity signal.

### Pattern Matching

Beyond individual span anomalies, the `PatternMatcher` detects higher-level incident patterns from accumulated span data:

| Pattern | Value | How It's Detected |
|---------|-------|-------------------|
| **Cascade Failure** | `cascade_failure` | Multiple dependent services failing simultaneously |
| **Latency Degradation** | `latency_degradation` | Sustained latency increase beyond baseline |
| **Error Rate Spike** | `error_rate_spike` | Sudden increase in error rate beyond baseline |
| **Memory Leak** | `memory_leak` | Steadily increasing latency with increasing error rate |
| **Thundering Herd** | `thundering_herd` | Sudden spike in request rate after recovery |
| **Cold Start** | `cold_start` | High latency on first requests after deployment |

Pattern detection triggers an `agenttel.anomaly.detected` event with the `pattern` field set, which an agent can use to select the right [playbook](incident-response.md).

---

## Baselines

Anomaly detection requires knowing what "normal" looks like. AgentTel provides three baseline sources, chained with fallback:

### Rolling Baselines (recommended)

Learned automatically from live traffic using a lock-free ring buffer sliding window:

```yaml
agenttel:
  baselines:
    source: rolling
    rolling-window-size: 1000    # samples per operation
    rolling-min-samples: 10      # minimum before baseline is valid
```

The `RollingBaselineProvider` tracks per-operation metrics:
- P50 and P99 latency
- Mean and standard deviation (used for z-score)
- Error rate
- Throughput (requests per second)

!!! tip "Cold Start"
    Until `rolling-min-samples` observations are collected, the rolling baseline is not considered valid. During this period, static baselines (from config) are used as fallback.

### Static Baselines

Declared in YAML config or `@AgentOperation` annotations:

```yaml
agenttel:
  operations:
    "[POST /api/payments]":
      expected-latency-p50: "45ms"
      expected-latency-p99: "200ms"
      expected-error-rate: 0.001
```

### Default Baselines

If neither rolling nor static baselines are available, hardcoded defaults are used (P50=100ms, P99=500ms, error_rate=0.01). These are intentionally conservative to avoid false positives.

**Resolution order:** rolling baselines > static baselines > defaults

---

## Configuration

```yaml
agenttel:
  anomaly-detection:
    enabled: true          # master switch (default: true)
    z-score-threshold: 3.0 # deviation threshold (default: 3.0)
  baselines:
    source: rolling        # "rolling" or "static" (default: rolling)
    rolling-window-size: 1000
    rolling-min-samples: 10
```

| Property | Default | Description |
|----------|---------|-------------|
| `anomaly-detection.enabled` | `true` | Enable/disable anomaly detection |
| `anomaly-detection.z-score-threshold` | `3.0` | Z-score threshold for anomaly classification |
| `baselines.source` | `rolling` | Primary baseline source |
| `baselines.rolling-window-size` | `1000` | Ring buffer size per operation |
| `baselines.rolling-min-samples` | `10` | Minimum samples before baseline is valid |

See [Configuration Reference](../reference/configuration.md#anomaly-detection) for the full list.

### Python Usage

The Python SDK provides the same anomaly detection:

```python
from agenttel.anomaly.detector import AnomalyDetector
from agenttel.anomaly.patterns import PatternMatcher
from agenttel.baseline.rolling import RollingBaselineProvider

# Anomaly detector with configurable threshold
detector = AnomalyDetector(z_score_threshold=3.0)
result = detector.detect(current_value=312.0, baseline_mean=45.0, baseline_stddev=20.0)
# result.is_anomaly = True, result.z_score = 13.35, result.score = 1.0

# Rolling baselines learned from traffic
baselines = RollingBaselineProvider(window_size=1000, min_samples=10)
baselines.record("POST /api/payments", latency_ms=45.0, is_error=False)

# Pattern matching
matcher = PatternMatcher()
patterns = matcher.detect(
    current_latency=312.0, baseline_p50=45.0,
    current_error_rate=0.052, baseline_error_rate=0.001,
    failing_dependencies=["stripe-api", "postgres"]
)
# patterns = [AnomalyPattern.LATENCY_DEGRADATION, AnomalyPattern.CASCADE_FAILURE]
```

When using FastAPI integration, anomaly detection is automatic:

```python
from agenttel.fastapi import instrument_fastapi

app = FastAPI()
instrument_fastapi(app)  # Anomaly detection runs on every span
```

---

## Span Attributes

When an anomaly is detected, these attributes appear on the span:

| Attribute | Type | Example | Description |
|-----------|------|---------|-------------|
| `agenttel.anomaly.detected` | boolean | `true` | Whether this span is anomalous |
| `agenttel.anomaly.score` | double | `0.85` | Severity score (0.0–1.0) |
| `agenttel.anomaly.latency_z_score` | double | `4.2` | Z-score deviation |
| `agenttel.anomaly.pattern` | string | `error_rate_spike` | Detected incident pattern (if any) |

See [Attribute Dictionary — Anomaly](../reference/attribute-dictionary.md#anomaly) for the complete reference.

---

## Events

Anomaly detection emits structured events via the OTel Logs API:

### `agenttel.anomaly.detected`

Emitted when a span's latency z-score exceeds the threshold OR when the `PatternMatcher` detects an incident pattern.

```json
{
  "operation": "POST /api/payments",
  "latency_ms": 312.0,
  "anomaly_score": 0.85,
  "z_score": 4.2
}
```

An agent subscribed to these events can immediately invoke [`get_incident_context`](../reference/mcp-tools.md#get_incident_context) to get the full diagnosis.

See [Event Catalog — anomaly.detected](../reference/event-catalog.md#agenttelanomaly-detected) for the full event specification.

---

## Frontend Anomaly Detection

The browser SDK runs its own anomaly detection for user-experience patterns:

| Pattern | Detection | Attributes |
|---------|-----------|------------|
| Rage clicks | 3+ clicks on same target in 2s | `client.anomaly.pattern=rage_click` |
| API failure cascade | 3+ API failures in 5s | `client.anomaly.pattern=api_cascade` |
| Slow page load | Exceeds P99 baseline | `client.anomaly.pattern=slow_load` |
| Error loop | Same error 3+ times in 10s | `client.anomaly.pattern=error_loop` |

See [Frontend Telemetry](frontend-telemetry.md) for the full browser SDK guide.

---

## Further Reading

- [Architecture — Span Enrichment Flow](../concepts/architecture.md#span-enrichment-flow) — where anomaly detection runs in the pipeline
- [Incident Response](incident-response.md) — the Observe-Diagnose-Act-Verify workflow agents use after detection
- [Event Catalog](../reference/event-catalog.md) — all structured events including anomaly events
- [Configuration — Anomaly Detection](../reference/configuration.md#anomaly-detection) — tuning parameters
