# The Autonomous Loop

AgentTel is not five separate tools. It is one continuous, self-feeding system that instruments, collects, detects, reports, fixes, and improves itself — without human intervention.

This document describes how the pieces connect into an autonomous loop and what makes it fundamentally different from traditional observability.

---

## The Difference

```
Traditional Observability              AgentTel Autonomous Loop
─────────────────────────              ───────────────────────────
instrument once                        instrument → auto-improve
  ↓                                      ↕
monitor forever                        collect → detect → report
  ↓                                      ↕
manually improve                       fix → learn → re-instrument
  ↓                                      ↕
repeat (human-driven)                  repeat (self-driven)
```

Traditional systems instrument once and monitor forever. When gaps appear — missing baselines, uncovered endpoints, stale thresholds — a human must notice and fix them. AgentTel closes this loop: the reporting layer detects what is missing and feeds it back to the instrumentation layer automatically.

---

## The Six Phases

```
                    THE AGENTTEL AUTONOMOUS LOOP

         ┌──────────────────────────────────────────┐
         │                                          │
         ▼                                          │
    ┌─────────┐     ┌─────────┐     ┌─────────┐    │
    │         │     │         │     │         │    │
    │ 1.INSTR │────▶│ 2.COLL  │────▶│ 3.DETCT │    │
    │ UMENT   │     │ ECT     │     │         │    │
    │         │     │         │     │         │    │
    └─────────┘     └─────────┘     └────┬────┘    │
         ▲                               │         │
         │                               ▼         │
    ┌─────────┐     ┌─────────┐     ┌─────────┐    │
    │         │     │         │     │         │    │
    │ 6.IMPR  │◀────│ 5.FIX   │◀────│ 4.REPO  │    │
    │ OVE     │     │         │     │ RT      │────┘
    │         │     │         │     │         │
    └─────────┘     └─────────┘     └─────────┘
```

### Phase 1: Instrument

The Instrumentation Agent analyzes codebases and injects AgentTel telemetry.

**Today (agenttel-cli):**
```bash
agenttel analyze ./my-service --output agenttel.yml
agenttel validate --config agenttel.yml --source ./my-service
```

**Future (agenttel-instrument MCP server):**
```bash
# From IDE (Claude Code, Cursor):
@agenttel instrument ./my-service

# The agent:
# 1. Scans source code (Spring endpoints, React routes)
# 2. Detects dependencies (postgres, redis, stripe-api)
# 3. Generates agenttel.yml with operations, baselines, SLOs
# 4. Adds @AgentObservable, @AgentOperation to Java code
# 5. Adds AgentTelWeb.init() to frontend
# 6. Adds build dependencies
# 7. Generates docker-compose for observability infrastructure
```

**Key design:** The Instrumentation Agent is itself an MCP server. IDE tools call its tools (`analyze_codebase`, `instrument_backend`, `instrument_frontend`) via JSON-RPC. Every instrumentation decision is traced as an OTel span.

### Phase 2: Collect

Instrumented applications emit rich, agent-ready telemetry.

```
┌──────────────────┐     ┌──────────────────┐
│  Browser          │     │  JVM Backend      │
│  agenttel-web     │     │  agenttel-core    │
│                   │     │                   │
│  Page loads       │     │  Span enrichment  │
│  API calls        │     │  Baselines        │
│  Interactions     │     │  Topology         │
│  Journey tracking │     │  SLO tracking     │
│  Anomaly detect   │     │  Anomaly detect   │
│  W3C traceparent ─┼─────┼─▶ Child spans     │
└────────┬──────────┘     └────────┬──────────┘
         │                         │
         │  OTLP/HTTP              │  OTLP/gRPC
         ▼                         ▼
┌──────────────────────────────────────────────┐
│              OTel Collector                   │
│   Routes telemetry to Jaeger + MCP server     │
└──────────────────────────────────────────────┘
```

The frontend generates the root trace. The browser creates a `traceparent` header and sends it with every API call. The backend creates child spans under the browser's trace. Result: one unified trace tree from button click to database query.

### Phase 3: Detect

Detection happens at three layers:

**Backend (agenttel-core):**
- `AnomalyDetector` computes z-scores per operation
- `PatternMatcher` identifies CASCADE_FAILURE, LATENCY_DEGRADATION, ERROR_RATE_SPIKE
- `SloTracker` monitors error budget burn rates in real-time

**Frontend (agenttel-web):**
- `ClientAnomalyDetector` detects RAGE_CLICK, SLOW_PAGE_LOAD, API_FAILURE_CASCADE
- `JourneyTracker` detects FUNNEL_DROP_OFF when completion rates drop
- `ErrorTracker` detects ERROR_LOOP (same error 5+ times in 30s)

**Cross-stack (agenttel-agent MCP server):**
- `CrossStackContextBuilder` correlates frontend anomalies with backend degradation
- Links browser API spans to backend operation spans via trace context
- Enables reasoning: "Users seeing 5s page loads → API call to POST /api/payments is 3s → stripe-api dependency timing out"

### Phase 4: Report

The Dashboard queries the MCP server's 9 tools in real-time:

| Dashboard Panel | MCP Tool(s) Used | What It Shows |
|-----------------|-----------------|---------------|
| Fleet Overview | `get_service_health` | All services, status, error rates |
| SLO Compliance | `get_slo_report` | Budget remaining, burn rate, compliance |
| Trend Analysis | `get_trend_analysis` | Latency/error/throughput over time |
| Executive Summary | `get_executive_summary` | Top 3 issues, ~300 token LLM-optimized view |
| Cross-Stack View | `get_cross_stack_context` | Frontend → backend trace correlation |
| Monitor Decisions | `get_recent_agent_actions` | What the AI decided and why |
| Incident Context | `get_incident_context` | What happened, what changed, what's affected |
| **Coverage Gaps** | Computed from health + config | **What's NOT instrumented** |
| **Suggestions** | Computed from gaps + trends | **What to improve** |

The last two panels are what close the loop. They don't just display data — they identify what is missing and suggest how to fix it.

### Phase 5: Fix

The Monitor agent runs autonomously:

```
Watch ──▶ Investigate ──▶ Reason ──▶ Act ──▶ Verify ──▶ Learn
  │                        (Claude)     │        │         │
  │   poll health          analyze      │  execute│  check  │  record
  │   every 10s            full-stack   │  action │  recovery  outcome
  │                        context      │         │         │
  │   "Is anything         "What's the  │  auto-  │  "Did   │  incident
  │    degraded?"           root cause?" │  approve│   it    │  history
  │                                     │  or     │   work?"│
  │                                     │  escalate        │
  │                                     │                  │
  ▼                                     ▼                  ▼
```

**Example diagnosis:**
```
ROOT_CAUSE: stripe-api dependency timeout causing payment failures
SEVERITY: critical
CONFIDENCE: 0.92
REASONING: Error rate on POST /api/payments spiked from 0.1% to 12.3%
  20 minutes after deployment v2.1.0. stripe-api dependency shows
  connection timeouts. Similar pattern occurred 2024-12-03 and was
  resolved by circuit-breaking the dependency.
RECOMMENDED_ACTIONS:
  - circuit_break_stripe (auto-approved, least disruptive)
  - rollback_v2.1.0 (needs approval, higher impact)
```

### Phase 6: Improve (The Key Innovation)

This is what makes AgentTel fundamentally different. The Feedback Engine analyzes data from Phases 3-5 and generates improvement suggestions that feed back to Phase 1.

```
┌──────────────────────────────────────────────────────┐
│                  FEEDBACK ENGINE                      │
│                                                      │
│  Inputs:                                             │
│    - Dashboard coverage gaps (Phase 4)               │
│    - Monitor incident history (Phase 5)              │
│    - CLI validation results (Phase 1)                │
│    - Observed vs configured baselines (Phase 3)      │
│                                                      │
│  Outputs: FeedbackEvents                             │
│    - MISSING_BASELINE → auto-fill from observed P50  │
│    - STALE_BASELINE → recalibrate from rolling data  │
│    - UNCOVERED_ENDPOINT → add to agenttel.yml        │
│    - UNCOVERED_ROUTE → add to agenttel-web config    │
│    - UNMONITORED_SERVICE → trigger instrumentation   │
│    - SLO_BURN_RATE_HIGH → adjust thresholds          │
│    - MISSING_RUNBOOK → suggest runbook template      │
│                                                      │
│  Actions:                                            │
│    Low risk  → auto-apply (baseline updates)         │
│    Med risk  → suggest in Dashboard (config changes) │
│    High risk → never auto-apply (code changes)       │
└───────────────────────────┬──────────────────────────┘
                            │
                            ▼
                   Instrumentation Agent
                   (back to Phase 1)
```

**Concrete examples:**

1. **Baseline drift:** After 2 weeks of traffic, `POST /api/payments` P50 is consistently 62ms but the configured baseline is 45ms. The Feedback Engine detects the 1.4x drift and suggests updating the baseline. Since this is a config-only change, it auto-applies.

2. **Uncovered endpoint:** A developer adds `DELETE /api/users/{id}` to the source code but doesn't add it to `agenttel.yml`. The gap detector (already built in `agenttel-cli`) catches this and emits a suggestion. The Instrumentation Agent adds the operation with inferred profile (`critical-write`) and placeholder baselines.

3. **New service discovery:** The topology registry shows `notification-service` as a dependency of `payment-service`, but it has no AgentTel instrumentation. The Dashboard's Coverage Gaps panel shows: "notification-service is listed as a dependency but has no telemetry." An SRE clicks "Instrument" and the Instrumentation Agent analyzes and instruments the service.

4. **Anomaly threshold tuning:** After 50 incidents, the Monitor's learner notices that `GET /api/prices` triggers anomaly detection at z-score 3.0 but is always a false positive (the operation has naturally variable latency). The Feedback Engine suggests raising the z-score threshold to 4.0 for that specific operation.

---

## What Exists vs What Is Planned

### Built (Available Now)

| Component | Module | Tech | Status |
|-----------|--------|------|--------|
| Backend SDK | agenttel-core, agenttel-api | Java | Production-ready |
| Spring Boot Starter | agenttel-spring-boot-starter | Java | Production-ready |
| GenAI Instrumentation | agenttel-genai | Java | Production-ready |
| MCP Server | agenttel-agent (9 tools) | Java | Production-ready |
| Web SDK | agenttel-web (6.3KB gzipped) | TypeScript | Alpha |
| Monitor Agent | agenttel-monitor | Python | Alpha |
| CLI Tool | agenttel-cli | Python | Alpha |
| React Example | react-checkout (5 pages) | React | Demo |
| Full-Stack Demo | docker-compose (4 services) | Docker | Demo |

### Planned (Completing the Loop)

| Component | Module | Tech | Purpose |
|-----------|--------|------|---------|
| Instrumentation Agent | agenttel-instrument | Python | MCP server for AI-driven instrumentation |
| Dashboard | agenttel-dashboard | React | Real-time SLO/health/gaps/suggestions UI |
| Feedback Engine | (built into dashboard + instrument) | Python | Gap detection + auto-improvement triggers |

---

## Observable Observers

Every component in the loop traces its own decisions:

```
Service             Span Example
─────────────────   ──────────────────────────────────────────────
checkout-web        page_load /checkout/payment (800ms)
payment-service     POST /api/payments (45ms)
agenttel-monitor    monitor.tick: root_cause="stripe timeout", confidence=0.92
agenttel-instrument agenttel.instrument: target="POST /api/refunds", action="add_baseline"
```

You can open Jaeger and see the AI's reasoning chain — what it detected, what it decided, why, and what happened next. The observers are themselves observed.

---

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                     │
│  ┌──────────────┐                           ┌──────────────┐       │
│  │ Instrument   │    ┌──────────────┐       │  Dashboard   │       │
│  │ Agent (MCP)  │───▶│  Apps        │       │  (React)     │       │
│  │              │    │  (web+jvm)   │       │              │       │
│  │ analyze      │    └──────┬───────┘       │ SLO status   │       │
│  │ instrument   │           │ OTLP          │ Anomaly feed │       │
│  │ apply_fix    │           ▼               │ Coverage gaps│       │
│  └──────▲───────┘    ┌──────────────┐       │ Suggestions  │       │
│         │            │ OTel         │       └──────┬───────┘       │
│         │            │ Collector    │              │               │
│         │            └──────┬───────┘              │               │
│         │                   │                      │               │
│         │                   ▼                      │               │
│         │            ┌──────────────┐              │               │
│         │            │ MCP Server   │◀─────────────┘               │
│         │            │ (9 tools)    │                               │
│         │            │ + Reporting  │                               │
│         │            └──────┬───────┘                               │
│         │                   │                                       │
│         │                   ▼                                       │
│         │            ┌──────────────┐                               │
│         │            │ Monitor      │                               │
│         │            │ Agent        │                               │
│  ┌──────┴───────┐    │ Watch→Fix   │                               │
│  │ Feedback     │◀───│ Learn       │                               │
│  │ Engine       │    └─────────────┘                               │
│  │              │                                                   │
│  │ gap detect   │                                                   │
│  │ baseline cal │                                                   │
│  │ threshold    │                                                   │
│  │ tuning       │                                                   │
│  └──────────────┘                                                   │
│                                                                     │
│              THE AGENTTEL AUTONOMOUS LOOP                           │
└─────────────────────────────────────────────────────────────────────┘
```

The system starts with instrumentation, collects telemetry, detects anomalies, reports health and gaps, fixes incidents autonomously, and improves its own instrumentation based on what it learns. Each cycle makes the next cycle better.
