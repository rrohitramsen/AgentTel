# Frontend Telemetry

Agent-ready browser observability with the `@agenttel/web` TypeScript SDK — auto-instrumentation, journey tracking, anomaly detection, and cross-stack correlation.

---

## Why Frontend Telemetry for Agents?

Backend telemetry tells an agent what happened in your services. But many incidents are **user-facing** — slow page loads, broken checkout flows, API errors visible in the browser. Without frontend telemetry, an agent diagnosing a user-reported issue is blind to:

- How bad is the user experience right now?
- Which page or user flow is affected?
- Is this a frontend issue, a backend issue, or both?
- Are users rage-clicking because the UI is unresponsive?

AgentTel's browser SDK captures this automatically and links it to backend traces.

---

## Quick Start

```typescript
import { AgentTelWeb } from '@agenttel/web';

AgentTelWeb.init({
  serviceName: 'checkout-frontend',
  otlpEndpoint: 'https://collector.example.com/v1/traces',
  topology: {
    team: 'frontend-platform',
    domain: 'commerce'
  }
});
```

That's it. Page loads, navigation, API calls, clicks, and errors are captured automatically.

---

## Auto-Instrumentation

The SDK automatically tracks five categories of browser activity without any manual code:

| Tracker | What It Captures | Key Attributes |
|---------|-----------------|----------------|
| **PageTracker** | Page load metrics via Navigation Timing API | `page.route`, `page.title`, load timing |
| **NavigationTracker** | SPA route changes (pushState, popState) | Previous/new route, transition duration |
| **ApiTracker** | All `fetch` and `XMLHttpRequest` calls | URL, method, status, response time |
| **InteractionTracker** | Click and submit events | Target element (via `data-agenttel-target`), response time |
| **ErrorTracker** | Uncaught JavaScript errors and promise rejections | Error message, stack trace, error loops |

!!! tip "PII Safety"
    Interaction targets use `data-agenttel-target` attributes rather than CSS selectors or text content, avoiding accidental PII capture. Add `data-agenttel-target="checkout-submit"` to elements you want tracked by name.

---

## Per-Route Baselines and Decisions

Each route can have its own baselines and decision metadata:

```typescript
AgentTelWeb.init({
  serviceName: 'checkout-frontend',
  otlpEndpoint: 'https://collector.example.com/v1/traces',
  routes: {
    '/checkout/:step': {
      baselines: {
        pageLoadP50Ms: 800,
        pageLoadP99Ms: 2500,
        apiCallP50Ms: 200
      },
      decisions: {
        escalationLevel: 'page_oncall',
        businessCriticality: 'critical',
        runbookUrl: 'https://wiki/runbooks/checkout-flow',
        fallbackPage: '/checkout/error'
      }
    },
    '/products': {
      baselines: { pageLoadP50Ms: 400, pageLoadP99Ms: 1200 },
      decisions: { escalationLevel: 'notify_team' }
    }
  }
});
```

An agent receiving a span from `/checkout/:step` immediately knows this is a critical page, what the normal load time is, and where the runbook lives.

---

## Journey Tracking

Track multi-step user flows across page navigations:

```typescript
// Define journeys in config
AgentTelWeb.init({
  // ...
  journeys: {
    checkout: {
      steps: ['/cart', '/checkout/shipping', '/checkout/payment', '/checkout/confirm'],
      name: 'Checkout Flow'
    }
  }
});
```

The `JourneyTracker` automatically detects when a user enters a journey and tracks:

- **Step progression** — which step the user is on, total steps
- **Completion rate** — did the user finish the flow?
- **Abandonment** — which step did they drop off?
- **Duration** — time from journey start to completion

Agents use this to answer: "Is the checkout flow broken? At which step are users dropping off?"

---

## Anomaly Detection

The browser SDK runs its own anomaly detection, identifying patterns that indicate user experience degradation:

| Pattern | Detection Method | What It Means |
|---------|-----------------|---------------|
| **Rage clicks** | 3+ clicks on the same target within 2 seconds | UI is unresponsive or broken |
| **API failure cascade** | 3+ API failures within 5 seconds | Backend is failing and frontend is hammering it |
| **Slow page load** | Load time exceeds P99 baseline | Page is significantly slower than normal |
| **Error loop** | Same JS error 3+ times within 10 seconds | Recurring error, likely a code bug |
| **Funnel drop-off** | Journey abandonment rate exceeds threshold | User flow is broken at a specific step |

Anomaly spans carry `agenttel.client.anomaly.detected=true`, `anomaly.pattern`, and `anomaly.score` — giving agents immediate signal without needing to query aggregated metrics.

---

## Cross-Stack Correlation

The SDK links frontend and backend traces automatically:

1. **Outgoing requests** — `CorrelationEngine` injects a `traceparent` header (W3C Trace Context) on all `fetch`/`XMLHttpRequest` calls
2. **Incoming responses** — Extracts `X-Trace-Id` from response headers to capture the backend trace ID
3. **Linked spans** — Frontend spans carry `agenttel.client.correlation.backend_trace_id` and `backend_service` attributes

This enables an agent to get the full picture via the [`get_cross_stack_context`](../reference/mcp-tools.md) MCP tool — correlating a slow checkout button click in the browser with a database timeout on the backend.

---

## Attributes Reference

The browser SDK sets `agenttel.client.*` attributes on spans. See the full list in the [Attribute Dictionary](../reference/attribute-dictionary.md#frontend).

Key attribute groups:

| Group | Example Attributes | Purpose |
|-------|-------------------|---------|
| App identity | `client.app.name`, `client.app.version`, `client.app.platform` | Which frontend app and version |
| Page context | `client.page.route`, `client.page.title`, `client.page.business_criticality` | What page the user is on |
| Baselines | `client.baseline.page_load_p50_ms`, `client.baseline.api_call_p50_ms` | What "normal" looks like |
| Decisions | `client.decision.escalation_level`, `client.decision.runbook_url` | What an agent can do |
| Anomalies | `client.anomaly.detected`, `client.anomaly.pattern`, `client.anomaly.score` | Is something wrong |
| Correlation | `client.correlation.backend_trace_id`, `client.correlation.backend_service` | Backend trace link |
| Journeys | `client.journey.name`, `client.journey.step`, `client.journey.total_steps` | User flow tracking |

---

## Further Reading

- [Architecture — Frontend Telemetry Flow](../concepts/architecture.md#frontend-telemetry-flow) — how spans flow from browser to backend
- [Architecture — Cross-Stack Correlation](../concepts/architecture.md#cross-stack-correlation-flow) — W3C Trace Context linking
- [Configuration — Frontend SDK](../reference/configuration.md#frontend-sdk) — all configuration properties
- [Attribute Dictionary — Frontend](../reference/attribute-dictionary.md#frontend) — complete attribute reference
