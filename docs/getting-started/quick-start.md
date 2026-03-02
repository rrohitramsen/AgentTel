# Quick Start

Get up and running with AgentTel in minutes. Choose your integration path:

| Path | Best For | Effort |
|------|----------|--------|
| [Spring Boot Starter](#backend-spring-boot) | Spring Boot applications | Add dependency + YAML config |
| [JavaAgent Extension](#zero-code-mode-javaagent-extension) | Any JVM app (no code changes) | JVM flag + YAML config |
| [Frontend SDK](#frontend-browser-sdk) | Browser / SPA applications | `npm install` + init call |
| [Instrument Agent](#instrument-agent-ide-tooling) | AI-assisted setup in your IDE | Run MCP server, ask your agent |

## Try the Docker Demo

The fastest way to see AgentTel in action — starts a demo payment service with OTel Collector and Jaeger:

```bash
cd examples/spring-boot-example
docker compose -f docker/docker-compose.yml up --build
```

Or run the guided demo script (generates traffic and queries all MCP tools):

```bash
./docker-demo.sh
```

| Dashboard | URL |
|-----------|-----|
| Jaeger (traces) | [http://localhost:16686](http://localhost:16686) |
| Swagger UI | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| MCP Tool Docs | [http://localhost:8081/mcp/docs](http://localhost:8081/mcp/docs) |

Teardown: `docker compose -f docker/docker-compose.yml down -v`

---

## Backend: Spring Boot

### 1. Add Dependencies

=== "Maven"

    ```xml
    <dependencies>
        <!-- Core: span enrichment, baselines, anomaly detection, SLO tracking -->
        <dependency>
            <groupId>io.agenttel</groupId>
            <artifactId>agenttel-spring-boot-starter</artifactId>
            <version>0.1.0-alpha</version>
        </dependency>

        <!-- Optional: GenAI instrumentation -->
        <dependency>
            <groupId>io.agenttel</groupId>
            <artifactId>agenttel-genai</artifactId>
            <version>0.1.0-alpha</version>
        </dependency>

        <!-- Optional: Agent interface layer (MCP server, incident context, remediation) -->
        <dependency>
            <groupId>io.agenttel</groupId>
            <artifactId>agenttel-agent</artifactId>
            <version>0.1.0-alpha</version>
        </dependency>
    </dependencies>
    ```

=== "Gradle (Kotlin)"

    ```kotlin
    dependencies {
        // Core: span enrichment, baselines, anomaly detection, SLO tracking
        implementation("io.agenttel:agenttel-spring-boot-starter:0.1.0-alpha")

        // Optional: GenAI instrumentation
        implementation("io.agenttel:agenttel-genai:0.1.0-alpha")

        // Optional: Agent interface layer (MCP server, incident context, remediation)
        implementation("io.agenttel:agenttel-agent:0.1.0-alpha")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        // Core: span enrichment, baselines, anomaly detection, SLO tracking
        implementation 'io.agenttel:agenttel-spring-boot-starter:0.1.0-alpha'

        // Optional: GenAI instrumentation
        implementation 'io.agenttel:agenttel-genai:0.1.0-alpha'

        // Optional: Agent interface layer (MCP server, incident context, remediation)
        implementation 'io.agenttel:agenttel-agent:0.1.0-alpha'
    }
    ```

### 2. Configure Your Service

All enrichment is driven by YAML configuration — no code changes needed:

```yaml
# application.yml
agenttel:
  # Topology: set once on the OTel Resource, associated with all telemetry
  topology:
    team: payments-platform
    tier: critical
    domain: commerce
    on-call-channel: "#payments-oncall"
  dependencies:
    - name: postgres
      type: database
      criticality: required
      timeout-ms: 5000
      circuit-breaker: true
    - name: stripe-api
      type: rest_api
      criticality: required
      fallback: "Return cached pricing"

  # Reusable operational profiles — reduce repetition across operations
  profiles:
    critical-write:
      retryable: false
      escalation-level: page_oncall
      safe-to-restart: false
    read-only:
      retryable: true
      idempotent: true
      escalation-level: notify_team

  # Per-operation baselines and decision metadata
  # Use bracket notation [key] for operation names with special characters
  operations:
    "[POST /api/payments]":
      profile: critical-write
      expected-latency-p50: "45ms"
      expected-latency-p99: "200ms"
      expected-error-rate: 0.001
      retryable: true               # overrides profile default
      idempotent: true
      runbook-url: "https://wiki/runbooks/process-payment"
    "[GET /api/payments/{id}]":
      profile: read-only
      expected-latency-p50: "15ms"
      expected-latency-p99: "80ms"

  baselines:
    rolling-window-size: 1000
    rolling-min-samples: 10
  anomaly-detection:
    z-score-threshold: 3.0
```

### 3. Optional: Annotate for IDE Support

Annotations are optional — YAML config above is sufficient. Use `@AgentOperation` when you want IDE autocomplete and compile-time validation:

```java
@AgentOperation(profile = "critical-write")
@PostMapping("/api/payments")
public ResponseEntity<PaymentResult> processPayment(@RequestBody PaymentRequest req) {
    // Your business logic — spans are enriched automatically
}
```

!!! info
    When both YAML config and annotations define the same operation, YAML config takes priority. Per-operation values override profile defaults.

### 4. Start the MCP Server (Optional)

```java
// Expose telemetry to AI agents via MCP
McpServer mcp = new AgentTelMcpServerBuilder()
    .port(8081)
    .contextProvider(agentContextProvider)
    .remediationExecutor(remediationExecutor)
    .build();
mcp.start();
```

AI agents can now call tools like `get_service_health`, `get_incident_context`, `list_remediation_actions`, and `execute_remediation` over JSON-RPC.

### 5. What You Get

**Resource attributes** (set once per service, associated with all telemetry):

```
agenttel.topology.team         = "payments-platform"
agenttel.topology.tier         = "critical"
agenttel.topology.domain       = "commerce"
agenttel.topology.on_call_channel = "#payments-oncall"
agenttel.topology.dependencies = [{"name":"postgres","type":"database",...}]
```

**Span attributes** (per operation, only on operations with registered metadata):

```
agenttel.baseline.latency_p50_ms = 45.0
agenttel.baseline.latency_p99_ms = 200.0
agenttel.baseline.error_rate     = 0.001
agenttel.baseline.source         = "static"
agenttel.decision.retryable      = true
agenttel.decision.runbook_url    = "https://wiki/runbooks/process-payment"
agenttel.decision.escalation_level = "page_oncall"
agenttel.anomaly.detected        = false
agenttel.slo.budget_remaining    = 0.85
```

**Incident context** via MCP when something goes wrong:

```
=== INCIDENT inc-a3f2b1c4 ===
SEVERITY: HIGH
SUMMARY: POST /api/payments experiencing elevated error rate (5.2%)

## WHAT IS HAPPENING
Error Rate: 5.2% (baseline: 0.1%)
Latency P50: 312ms (baseline: 45ms)
Patterns: ERROR_RATE_SPIKE

## WHAT CHANGED
Last Deploy: v2.1.0 at 2025-01-15T14:30:00Z

## WHAT IS AFFECTED
Scope: operation_specific
User-Facing: YES
Affected Deps: stripe-api

## SUGGESTED ACTIONS
  - [HIGH] rollback_deployment: Rollback to previous version (NEEDS APPROVAL)
  - [MEDIUM] enable_circuit_breakers: Circuit break stripe-api
```

---

## Zero-Code Mode (JavaAgent Extension)

For applications where you cannot add a library dependency, use the javaagent extension. No code changes, no Spring dependency:

```bash
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.javaagent.extensions=agenttel-javaagent-extension.jar \
     -Dagenttel.config.file=agenttel.yml \
     -jar myapp.jar
```

The extension reads configuration from `agenttel.yml` (same YAML format as above), system properties (`-Dagenttel.topology.team=payments`), or environment variables (`AGENTTEL_TOPOLOGY_TEAM=payments`).

---

## Frontend: Browser SDK

Add agent-ready telemetry to your browser application with `@agenttel/web`.

### 1. Install

```bash
npm install @agenttel/web
```

### 2. Initialize

```typescript
import { AgentTelWeb } from '@agenttel/web';

AgentTelWeb.init({
  appName: 'checkout-web',
  appVersion: '1.0.0',
  environment: 'production',
  collectorEndpoint: '/otlp',

  // Per-route baselines and decision metadata
  routes: {
    '/checkout/:step': {
      businessCriticality: 'revenue',
      baseline: {
        pageLoadP50Ms: 800,
        pageLoadP99Ms: 2000,
        apiCallP50Ms: 300,
      },
      decision: {
        escalationLevel: 'page_oncall',
        runbookUrl: 'https://wiki/runbooks/checkout',
      },
    },
    '/products': {
      businessCriticality: 'engagement',
      baseline: { pageLoadP50Ms: 500 },
    },
  },

  // Multi-step user journey tracking
  journeys: {
    checkout: {
      steps: ['/products', '/cart', '/checkout/shipping', '/checkout/payment', '/confirmation'],
      baseline: { completionRate: 0.65, avgDurationS: 300 },
    },
  },

  // Client-side anomaly detection
  anomalyDetection: {
    rageClickThreshold: 3,
    rageClickWindowMs: 2000,
    apiFailureCascadeThreshold: 3,
    errorLoopThreshold: 5,
    errorLoopWindowMs: 30000,
  },
});
```

### 3. What You Get

The SDK **automatically instruments** — no further code changes needed:

| Category | What's Captured |
|----------|----------------|
| **Page Loads** | DOM load time, TTFB, transfer size via Navigation Timing API |
| **Navigation** | SPA route changes with timing |
| **API Calls** | `fetch` and `XMLHttpRequest` interception with response status and duration |
| **Interactions** | Clicks and form submits with semantic target identification |
| **Errors** | JavaScript errors with error loop detection |
| **Journeys** | Step completion, abandonment, funnel drop-off rates |
| **Anomalies** | Rage clicks, API failure cascades, slow page loads, error loops |
| **Correlation** | W3C Trace Context (`traceparent`) injected on all outgoing requests for backend linking |

**Span attributes** emitted by the SDK:

```
agenttel.client.page.route              = "/checkout/payment"
agenttel.client.page.business_criticality = "revenue"
agenttel.client.baseline.page_load_p50_ms = 800
agenttel.client.decision.escalation_level = "page_oncall"
agenttel.client.anomaly.detected        = true
agenttel.client.anomaly.pattern         = "rage_click"
agenttel.client.correlation.backend_trace_id = "abc123..."
```

### 4. Manual API (Optional)

```typescript
const agenttel = AgentTelWeb.getInstance();

// Track custom interactions
agenttel.trackInteraction('custom', {
  target: 'button#submit',
  outcome: 'success',
  durationMs: 150,
});

// Control journeys manually
agenttel.startJourney('checkout');
agenttel.advanceJourney('checkout');
agenttel.completeJourney('checkout');

// Flush and shutdown
await agenttel.flush();
agenttel.shutdown();
```

!!! tip
    The SDK uses `data-agenttel-target` attributes to identify interaction targets without capturing PII. Add `data-agenttel-target="pay-button"` to your HTML elements for clean span names.

---

## Instrument Agent (IDE Tooling)

The instrument agent is a Python MCP server that helps AI assistants in your IDE (Cursor, Claude Code, VS Code) analyze your codebase and generate AgentTel configuration automatically.

### 1. Install

```bash
pip install agenttel-instrument
```

### 2. Configure

```yaml
# instrument.yml
server:
  host: 0.0.0.0
  port: 8082
backend_mcp:
  host: localhost
  port: 8081
  timeout_seconds: 30.0
```

### 3. Run

```bash
agenttel-instrument --config instrument.yml
```

### 4. Use with Your IDE Agent

Once running, add it as an MCP server in your IDE and ask your AI assistant:

| Prompt | What Happens |
|--------|-------------|
| *"Analyze my codebase and generate AgentTel config"* | Scans endpoints, detects dependencies, generates `agenttel.yml` |
| *"Instrument the frontend"* | Detects React routes, infers criticality, generates SDK config |
| *"Validate my instrumentation"* | Cross-references config against source code, finds gaps |
| *"Suggest improvements"* | Finds missing baselines, uncovered endpoints, stale thresholds |
| *"Apply low-risk improvements"* | Auto-calibrates baselines from live health data |

### Available MCP Tools

| Tool | Description |
|------|-------------|
| `analyze_codebase` | Scan Java/Spring Boot source — detect endpoints, dependencies, framework |
| `instrument_backend` | Generate backend config — dependencies, annotations, agenttel.yml |
| `instrument_frontend` | Generate frontend config — React route detection, SDK initialization |
| `validate_instrumentation` | Validate agenttel.yml completeness against source code |
| `suggest_improvements` | Detect missing baselines, uncovered endpoints, missing runbooks |
| `apply_improvements` | Auto-apply low-risk improvements using live health data |
| `apply_single` | Apply a single specific improvement |

---

## Examples

| Example | Description | Run Command |
|---------|-------------|-------------|
| [Spring Boot Example](https://github.com/rrohitramsen/AgentTel/tree/main/examples/spring-boot-example) | Payment service with span enrichment, topology, baselines, anomaly detection, and MCP server | `docker compose -f docker/docker-compose.yml up --build` |
| [LangChain4j Example](https://github.com/rrohitramsen/AgentTel/tree/main/examples/langchain4j-example) | GenAI tracing with LangChain4j — chat spans, token tracking, and cost calculation | `./gradlew :examples:langchain4j-example:run` |
| [React Checkout Example](https://github.com/rrohitramsen/AgentTel/tree/main/agenttel-web/examples/react-checkout) | React SPA with frontend telemetry — journey tracking, anomaly detection, cross-stack correlation | `cd agenttel-web/examples/react-checkout && npm start` |

## Next Steps

- [Architecture](../concepts/architecture.md) — understand how AgentTel works under the hood
- [Agent Layer](../guides/agent-layer.md) — deep dive into MCP server, incidents, and remediation
- [API Reference](../reference/api-reference.md) — full configuration and annotation reference
