# Full-Stack Vision: AgentTel Ecosystem

AgentTel today provides agent-ready telemetry for the **backend** — enriching OpenTelemetry spans with baselines, topology, and decision metadata so AI agents can detect, diagnose, and act on production incidents.

This document extends that vision to the **full stack** — from the user's browser to the deepest microservice — and defines four phases to get there.

---

## The Gap

```
         Today's Observability                    Agent-Ready (AgentTel Vision)
┌──────────────────────────────────┐    ┌──────────────────────────────────┐
│  Frontend: Datadog RUM / Sentry  │    │  Frontend: agenttel-web          │
│  → page loads, JS errors, clicks │    │  → baselines, user journeys,     │
│  → designed for human dashboards │    │    business context, decisions    │
│  → no agent context              │    │  → designed for AI agents        │
├──────────────────────────────────┤    ├──────────────────────────────────┤
│  Backend: OTel + AgentTel SDK    │    │  Backend: AgentTel SDK           │
│  → enriched spans ✅             │    │  → enriched spans ✅             │
├──────────────────────────────────┤    ├──────────────────────────────────┤
│  Monitoring: PagerDuty / Grafana │    │  AgentTel Monitor                │
│  → threshold-based alerts        │    │  → AI agent consuming full-stack │
│  → human-in-the-loop             │    │    telemetry, reasoning, acting  │
├──────────────────────────────────┤    ├──────────────────────────────────┤
│  Instrumentation: manual setup   │    │  AgentTel Agent                  │
│  → each team figures it out      │    │  → AI-assisted instrumentation   │
│  → inconsistent coverage         │    │    and adoption                  │
└──────────────────────────────────┘    └──────────────────────────────────┘
```

---

## Phase 1: Full-Stack Conventions

**Goal:** Define the `agenttel.client.*` semantic conventions — the attributes, baselines, topology, and decision metadata for client-side (browser/mobile) telemetry. This is a design exercise; no SDK code yet.

### Why Conventions First

The backend `agenttel.*` conventions were defined before code. This ensures:
- Frontend and backend telemetry use the same patterns
- An AI agent consuming both layers sees a consistent schema
- Multiple SDKs (web, mobile) follow the same standard

### Client Telemetry Conventions

#### Resource Attributes (set once per app)

These identify the client application, analogous to backend topology.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.app.name` | string | Application name | `"checkout-web"` |
| `agenttel.client.app.version` | string | Deployed version | `"2.3.1"` |
| `agenttel.client.app.platform` | string | `web`, `ios`, `android` | `"web"` |
| `agenttel.client.app.environment` | string | `production`, `staging` | `"production"` |
| `agenttel.client.topology.team` | string | Owning team | `"frontend-platform"` |
| `agenttel.client.topology.domain` | string | Business domain | `"commerce"` |

#### Page/Screen Attributes

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.page.route` | string | Route pattern (not URL) | `"/checkout/:step"` |
| `agenttel.client.page.title` | string | Human-readable page name | `"Checkout - Payment"` |
| `agenttel.client.page.business_criticality` | string | `revenue`, `engagement`, `internal` | `"revenue"` |

#### User Journey Attributes

User journeys are multi-page flows (e.g., checkout funnel). These attributes let agents understand where a user is in a flow and detect funnel drop-offs.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.journey.name` | string | Journey identifier | `"purchase_flow"` |
| `agenttel.client.journey.step` | int | Current step in the flow | `3` |
| `agenttel.client.journey.total_steps` | int | Total steps in the flow | `4` |
| `agenttel.client.journey.started_at` | string | ISO 8601 timestamp | `"2025-01-15T14:30:00Z"` |

#### Baseline Attributes (per route)

Same philosophy as backend baselines — what "normal" looks like for this page.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.baseline.page_load_p50_ms` | double | Expected median page load | `800.0` |
| `agenttel.client.baseline.page_load_p99_ms` | double | Expected P99 page load | `3000.0` |
| `agenttel.client.baseline.interaction_error_rate` | double | Expected error rate | `0.02` |
| `agenttel.client.baseline.api_call_p50_ms` | double | Expected median API response | `200.0` |
| `agenttel.client.baseline.source` | string | `static`, `rolling` | `"rolling"` |

#### Decision Attributes (per route)

What an agent is allowed to do when issues are detected on this page.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.decision.retry_on_failure` | boolean | Should failed actions be retried | `true` |
| `agenttel.client.decision.fallback_page` | string | Fallback route on failure | `"/checkout/simplified"` |
| `agenttel.client.decision.escalation_level` | string | `notify_team`, `page_oncall` | `"page_oncall"` |
| `agenttel.client.decision.runbook_url` | string | Runbook for this page's issues | `"https://wiki/runbooks/checkout"` |
| `agenttel.client.decision.user_facing` | boolean | Always true for client telemetry | `true` |

#### Interaction Attributes

Enriched events for user interactions — not just "click happened" but context an agent can reason about.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.interaction.type` | string | `click`, `submit`, `navigate`, `scroll` | `"submit"` |
| `agenttel.client.interaction.target` | string | Semantic target (not CSS selector) | `"payment_form"` |
| `agenttel.client.interaction.outcome` | string | `success`, `error`, `timeout`, `abandoned` | `"error"` |
| `agenttel.client.interaction.response_time_ms` | double | Time from action to result | `2500.0` |

#### Anomaly Attributes

Client-side anomaly detection, same pattern as backend.

| Attribute | Type | Description | Example |
|-----------|------|-------------|---------|
| `agenttel.client.anomaly.detected` | boolean | Whether this interaction is anomalous | `true` |
| `agenttel.client.anomaly.score` | double | Deviation score (0-1) | `0.85` |
| `agenttel.client.anomaly.pattern` | string | Detected pattern | `"RAGE_CLICK"` |

#### Client-Specific Patterns

| Pattern | Description | Detection Signal |
|---------|-------------|-----------------|
| `RAGE_CLICK` | User repeatedly clicking same element | 3+ clicks on same target within 2s |
| `SLOW_PAGE_LOAD` | Page load significantly above baseline | Page load > 3x P50 baseline |
| `API_FAILURE_CASCADE` | Multiple API calls failing | 3+ failed fetch requests within 10s |
| `FUNNEL_DROP_OFF` | Abnormal user journey abandonment | Drop-off rate > 2x baseline for journey step |
| `ERROR_LOOP` | Repeated JS errors on same page | Same error 5+ times within 30s |
| `FORM_ABANDONMENT` | User starts but doesn't complete form | Form interaction without submit, time > 2min |

#### Backend Correlation

The critical link — connecting frontend spans to backend traces.

| Attribute | Type | Description |
|-----------|------|-------------|
| `agenttel.client.correlation.backend_trace_id` | string | W3C Trace Context trace ID from the API response |
| `agenttel.client.correlation.backend_service` | string | Backend service that handled the API call |
| `agenttel.client.correlation.backend_operation` | string | Backend operation name (e.g., `POST /api/payments`) |

This enables cross-stack reasoning: "Users on `/checkout` are seeing 5s page loads → API call to `POST /api/payments` is taking 3s → payment-service latency is elevated → stripe-api dependency is timing out."

### Client Telemetry Configuration

Same YAML-driven approach as the backend:

```yaml
# agenttel-web.config.yaml
agenttel:
  client:
    app:
      name: checkout-web
      platform: web
    topology:
      team: frontend-platform
      domain: commerce

    routes:
      "/checkout/:step":
        business_criticality: revenue
        baseline:
          page_load_p50_ms: 800
          page_load_p99_ms: 3000
          interaction_error_rate: 0.02
        decision:
          retry_on_failure: true
          fallback_page: "/checkout/simplified"
          escalation_level: page_oncall
          runbook_url: "https://wiki/runbooks/checkout"
      "/products":
        business_criticality: engagement
        baseline:
          page_load_p50_ms: 400
          page_load_p99_ms: 1500

    journeys:
      purchase_flow:
        steps:
          - route: "/products"
          - route: "/cart"
          - route: "/checkout/shipping"
          - route: "/checkout/payment"
          - route: "/order/confirmation"
        baseline:
          completion_rate: 0.65
          avg_duration_s: 180

    anomaly_detection:
      enabled: true
      rage_click_threshold: 3
      slow_load_multiplier: 3.0
```

---

## Phase 2: AgentTel Web SDK (`agenttel-web`)

**Goal:** TypeScript/JavaScript SDK that instruments browser applications and emits agent-ready telemetry following the conventions from Phase 1.

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                          Browser                             │
│                                                             │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ Page         │  │ Interaction  │  │ Journey           │  │
│  │ Tracker      │  │ Tracker      │  │ Tracker           │  │
│  │              │  │              │  │                   │  │
│  │ - load time  │  │ - clicks     │  │ - funnel steps    │  │
│  │ - CWV        │  │ - submits    │  │ - drop-off rate   │  │
│  │ - JS errors  │  │ - navigation │  │ - completion time │  │
│  │ - resources  │  │ - rage clicks│  │ - abandonment     │  │
│  └──────┬───────┘  └──────┬───────┘  └────────┬──────────┘  │
│         │                 │                    │             │
│  ┌──────▼─────────────────▼────────────────────▼──────────┐  │
│  │              Enrichment Engine                          │  │
│  │  - Baseline comparison (rolling + static)              │  │
│  │  - Anomaly detection (rage click, slow load, etc.)     │  │
│  │  - Route-level topology and decisions                  │  │
│  │  - Backend correlation (W3C Trace Context)             │  │
│  └───────────────────────┬────────────────────────────────┘  │
│                          │                                   │
│  ┌───────────────────────▼────────────────────────────────┐  │
│  │              Transport Layer                            │  │
│  │  - OTLP/HTTP export to OTel Collector                  │  │
│  │  - Batching and sampling                               │  │
│  │  - Offline buffering (for mobile/spotty connections)   │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### SDK API

```typescript
import { AgentTelWeb } from '@agenttel/web';

const agenttel = AgentTelWeb.init({
  appName: 'checkout-web',
  appVersion: '2.3.1',
  team: 'frontend-platform',
  domain: 'commerce',
  collectorEndpoint: 'https://otel-collector.internal:4318',

  routes: {
    '/checkout/:step': {
      businessCriticality: 'revenue',
      baseline: { pageLoadP50Ms: 800, pageLoadP99Ms: 3000 },
      decision: {
        retryOnFailure: true,
        fallbackPage: '/checkout/simplified',
        escalationLevel: 'page_oncall',
      },
    },
  },

  journeys: {
    purchase_flow: {
      steps: ['/products', '/cart', '/checkout/shipping', '/checkout/payment', '/order/confirmation'],
      baseline: { completionRate: 0.65 },
    },
  },
});

// Auto-instruments: page loads, navigation, clicks, errors, API calls
// Manual instrumentation for custom interactions:
agenttel.trackInteraction('add_to_cart', {
  target: 'product_card',
  metadata: { productId: 'SKU-123', price: 29.99 },
});

// Journey tracking:
agenttel.startJourney('purchase_flow');
agenttel.advanceJourney('purchase_flow'); // moves to next step
agenttel.completeJourney('purchase_flow');
```

### Auto-Instrumentation

The SDK automatically captures without any code changes:

| Signal | How | What's Enriched |
|--------|-----|----------------|
| **Page loads** | `PerformanceObserver` + Navigation Timing API | Load time vs baseline, CWV scores, route context |
| **Navigation** | History API / `popstate` listener | Route transitions, SPA navigation timing |
| **API calls** | `fetch` / `XMLHttpRequest` interceptor | Response time, status, backend correlation via `traceparent` header |
| **JS errors** | `window.onerror` + `unhandledrejection` | Error classification, affected route, user journey step |
| **Clicks** | `addEventListener('click')` on document | Rage click detection, interaction timing |
| **Web Vitals** | `web-vitals` library integration | LCP, FID, CLS with baseline comparison |

### Framework Integrations

| Framework | Integration | What It Adds |
|-----------|------------|--------------|
| React | `AgentTelProvider` component + hooks | Route-aware tracking via React Router, component error boundaries |
| Next.js | Plugin / middleware | Server-side rendering correlation, route-based auto-config |
| Vue | Vue plugin | Vue Router integration, component error tracking |
| Angular | Angular module | Router events, HTTP interceptor for correlation |

### Bundle Size Target

| Component | Target |
|-----------|--------|
| Core (auto-instrumentation) | < 15 KB gzipped |
| React integration | < 3 KB gzipped |
| Full bundle with all integrations | < 25 KB gzipped |

---

## Phase 3: AgentTel Monitor

**Goal:** An AI agent that consumes full-stack AgentTel telemetry (frontend + backend) and autonomously detects, diagnoses, and responds to incidents.

### What Makes This Different

Traditional monitoring: `metric > threshold → alert → human investigates`

AgentTel Monitor:
```
full-stack telemetry → AI reasoning → diagnosis → action → verification → learning
```

The monitor is **not** a rules engine. It's an LLM-powered agent that:
- Reasons about **context**, not just thresholds
- Correlates signals **across the stack** (frontend slowness + backend latency + dependency failure)
- Explains its reasoning in **natural language**
- Learns from **past incidents** to improve future detection

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     AgentTel Monitor                         │
│                                                             │
│  ┌─────────────┐                                            │
│  │ Watch Loop   │ ← polls health every N seconds            │
│  │              │   (frontend + backend)                    │
│  └──────┬───────┘                                           │
│         │ health degraded                                    │
│  ┌──────▼───────┐                                           │
│  │ Investigator  │ ← calls MCP tools to gather context      │
│  │              │   (incident context, recent changes,      │
│  │              │    frontend anomalies, backend health)    │
│  └──────┬───────┘                                           │
│         │ context assembled                                  │
│  ┌──────▼───────┐                                           │
│  │ Reasoner     │ ← LLM analyzes full-stack context         │
│  │ (LLM)       │   "What's the root cause?"                │
│  │              │   "What should we do?"                    │
│  └──────┬───────┘                                           │
│         │ diagnosis + recommendation                         │
│  ┌──────▼───────┐                                           │
│  │ Actor        │ ← executes auto-approved actions          │
│  │              │   escalates approval-needed actions       │
│  │              │   posts updates to webhook/Slack          │
│  └──────┬───────┘                                           │
│         │ action taken                                       │
│  ┌──────▼───────┐                                           │
│  │ Verifier     │ ← monitors recovery after action          │
│  │              │   "Did the fix work?"                     │
│  │              │   rolls back if situation worsens         │
│  └──────┬───────┘                                           │
│         │ outcome recorded                                   │
│  ┌──────▼───────┐                                           │
│  │ Learner      │ ← records incident + resolution           │
│  │              │   enriches future similar incidents       │
│  └──────────────┘                                           │
└─────────────────────────────────────────────────────────────┘
```

### Cross-Stack Reasoning

This is the key capability that requires full-stack telemetry. Example:

```
MONITOR DIAGNOSIS:
════════════════════════════════════════════════════════
Users on /checkout/payment are experiencing 5.2s page loads (baseline: 800ms).

Root cause chain:
  1. [FRONTEND] /checkout/payment page load 6.5x baseline
  2. [FRONTEND] API call POST /api/payments taking 4.8s (baseline: 200ms)
  3. [BACKEND]  POST /api/payments latency P50=3200ms (baseline: 45ms)
  4. [BACKEND]  stripe-api dependency error rate 12.3%
  5. [CHANGE]   Deployment v2.1.0 rolled out 20 minutes ago

Impact:
  - 340 users affected in last 5 minutes
  - Checkout completion rate dropped from 65% to 12%
  - Revenue-critical path

Recommendation:
  - Rollback deployment v2.1.0 (NEEDS APPROVAL)
  - Circuit-break stripe-api immediately (auto-approved)

Confidence: HIGH — deployment timing correlates with degradation onset,
stripe-api errors are the bottleneck, and similar pattern occurred on
2024-12-03 (resolved by increasing timeout).
════════════════════════════════════════════════════════
```

No human could correlate frontend page load metrics → API call timing → backend span latency → dependency health → deployment history this quickly. No rules engine could reason about the confidence level or recall similar past incidents.

### MCP Tools (Extended)

The monitor uses existing backend MCP tools plus new frontend-aware tools:

| Tool | Description |
|------|-------------|
| `get_service_health` | Backend health (existing) |
| `get_incident_context` | Backend incident context (existing) |
| `get_client_health` | **NEW** — Frontend health by route |
| `get_journey_health` | **NEW** — User journey completion rates and drop-offs |
| `get_cross_stack_context` | **NEW** — Correlated frontend + backend incident view |
| `list_remediation_actions` | Available actions (existing) |
| `execute_remediation` | Execute action (existing) |

### Configuration

```yaml
# monitor.yml
agenttel:
  monitor:
    llm:
      provider: anthropic          # or openai, bedrock
      model: claude-sonnet-4-5-20250929
      api_key_env: ANTHROPIC_API_KEY

    watch:
      interval_seconds: 10
      health_sources:
        - backend_mcp: http://localhost:8081
        - frontend_collector: http://otel-collector:4318

    actions:
      auto_approve:
        - circuit_breaker
        - cache_flush
      require_approval:
        - rollback
        - restart
        - scale

    notifications:
      webhook: https://hooks.slack.com/services/...
      on_events:
        - anomaly_detected
        - action_taken
        - recovery_confirmed

    learning:
      store: ./incident-history.json    # or database
      similarity_threshold: 0.8
```

### Observability of the Monitor

The monitor itself is instrumented with AgentTel. Every decision creates spans:

```
agenttel.agent.action.type     = "diagnosis"
agenttel.agent.decision.chosen = "circuit_break_stripe"
agenttel.agent.decision.rationale = "stripe-api error rate 12.3%, deployment v2.1.0 correlates with onset"
agenttel.agent.decision.options = ["rollback", "circuit_break", "increase_timeout", "monitor"]
agenttel.agent.decision.confidence = 0.92
agenttel.genai.model = "claude-sonnet-4-5-20250929"
agenttel.genai.input_tokens = 1847
agenttel.genai.output_tokens = 312
agenttel.genai.cost_usd = 0.0089
```

You can see in Jaeger exactly what the AI agent thought, why it made each decision, and what it cost.

---

## Phase 4: AgentTel Agent (Implementation Helper)

**Goal:** An AI agent that helps teams adopt AgentTel in new or existing services — analyzing codebases, generating configuration, and validating instrumentation.

### Capabilities

#### 1. Codebase Analysis

Analyze a service's source code and generate an AgentTel configuration:

```bash
$ agenttel-agent analyze ./my-service

Analyzing my-service...

Discovered:
  Framework: Spring Boot 3.4.2
  Endpoints: 12 REST endpoints
  Dependencies: postgres (JDBC), redis (Lettuce), stripe-api (HTTP)
  Consumers: notification-service (Kafka), analytics-service (HTTP)

Generated: agenttel.yml
  - 12 operations with inferred baselines
  - 3 dependencies with detected types and criticality
  - 2 consumers identified from outbound calls
  - Suggested profiles: critical-write (3 ops), read-only (7 ops), async (2 ops)

Review agenttel.yml and adjust baselines to match your SLO targets.
```

#### 2. Configuration Validation

Validate that AgentTel configuration is complete and consistent:

```bash
$ agenttel-agent validate

Warnings:
  - POST /api/refunds: no runbook_url configured
  - payment-gateway dependency: no circuit_breaker configured
  - /api/admin/* endpoints: no escalation_level set

Suggestions:
  - POST /api/payments has 45ms P50 baseline but observed traffic shows 62ms median.
    Consider updating baseline or investigating latency.
  - stripe-api dependency has no fallback configured. Consider adding a fallback
    description for agent decision-making.
```

#### 3. Migration Assistant

Help teams migrate from existing instrumentation:

```bash
$ agenttel-agent migrate --from datadog

Found:
  - 8 Datadog APM annotations → can map to @AgentOperation
  - 3 custom metrics → can map to agenttel.operations config
  - Datadog RUM setup → can migrate to agenttel-web

Generated migration plan: migration.md
```

#### 4. MCP Tool for IDEs

Expose the implementation helper as an MCP server that IDE agents (Cursor, Claude Code, Copilot) can use:

| Tool | Description |
|------|-------------|
| `analyze_service` | Analyze codebase and suggest AgentTel config |
| `validate_config` | Check configuration for gaps |
| `suggest_baselines` | Suggest baselines from observed traffic |
| `generate_runbook` | Generate runbook template for an operation |

---

## Implementation Priority

| Phase | What | Depends On | Estimated Effort |
|-------|------|-----------|-----------------|
| **Phase 1** | Full-stack conventions | Nothing | Design only — this document |
| **Phase 2** | `agenttel-web` SDK | Phase 1 conventions | Large (new language, browser APIs) |
| **Phase 3** | AgentTel Monitor | Phase 2 for full-stack, but can start with backend-only | Medium |
| **Phase 4** | AgentTel Agent | Nothing (standalone tool) | Medium |

Phases 3 and 4 can start in parallel with Phase 2 since they work with backend telemetry today.

---

## Module Map (Full Ecosystem)

```
agenttel/
├── agenttel-api                    # Java — annotations, constants, models
├── agenttel-core                   # Java — span enrichment, baselines, anomaly detection
├── agenttel-genai                  # Java — GenAI instrumentation
├── agenttel-agent                  # Java — MCP server, health, incidents, remediation
├── agenttel-javaagent-extension    # Java — zero-code OTel extension
├── agenttel-spring-boot-starter    # Java — Spring Boot auto-config
├── agenttel-testing                # Java — test utilities
├── agenttel-web/                   # TypeScript — browser SDK (Phase 2)
│   ├── packages/
│   │   ├── core/                   #   auto-instrumentation, enrichment
│   │   ├── react/                  #   React integration
│   │   ├── vue/                    #   Vue integration
│   │   └── angular/                #   Angular integration
│   └── examples/
│       └── react-checkout/         #   example React app
├── agenttel-mobile/                # Future — React Native / Swift / Kotlin
├── agenttel-monitor/               # Python or Java — AI monitoring agent (Phase 3)
└── agenttel-cli/                   # Go or Java — implementation helper CLI (Phase 4)
```

---

## Design Principles (Carried Forward)

These principles from the backend SDK apply to the entire ecosystem:

1. **Telemetry should carry enough context for AI agents to reason and act autonomously** — true for frontend telemetry too, not just backend spans.

2. **Convention over configuration** — auto-detect routes, interactions, and dependencies. Manual config should be optional, not required.

3. **Zero-code where possible** — the web SDK should auto-instrument page loads, navigation, errors, and API calls without manual code. Manual tracking is available for custom interactions.

4. **Observable observers** — the monitor agent's own decisions are traced. You can audit what the AI did and why.

5. **Open standards** — built on OpenTelemetry. Frontend telemetry exports via OTLP, uses W3C Trace Context for backend correlation.

6. **Minimal overhead** — the web SDK must be small (< 15 KB gzipped) and must not impact page performance. Enrichment logic should be lightweight.
