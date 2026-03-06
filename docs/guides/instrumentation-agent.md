# Instrumentation Agent

An MCP server for your IDE that analyzes your codebase, generates AgentTel configuration, validates instrumentation, and auto-applies improvements.

---

## What It Does

The `agenttel-instrument` module is a Python MCP server designed for IDE-based AI assistants (Cursor, Claude Code, VS Code Copilot). It provides 7 tools that automate the instrumentation workflow:

| Tool | What It Does |
|------|-------------|
| `analyze_codebase` | Scans Java/Spring Boot source code — finds controllers, endpoints, dependencies, existing annotations |
| `instrument_backend` | Generates `agenttel` YAML config for your backend based on discovered endpoints |
| `instrument_frontend` | Generates `@agenttel/web` TypeScript config with auto-detected routes (React) |
| `validate_instrumentation` | Cross-references generated config against actual source code for completeness |
| `suggest_improvements` | Detects missing baselines, stale configs, coverage gaps, and optimization opportunities |
| `apply_improvements` | Batch-applies low-risk improvements automatically; flags medium/high-risk for human review |
| `apply_single` | Applies a single specific improvement |

---

## Setup

### Prerequisites

- Python 3.11+
- Access to your project source code

### Running the Server

```bash
cd agenttel-instrument
pip install -r requirements.txt
python -m agenttel_instrument.server --port 8080
```

### IDE Configuration

Add the MCP server to your IDE's MCP configuration:

```json
{
  "mcpServers": {
    "agenttel-instrument": {
      "url": "http://localhost:8080"
    }
  }
}
```

Your IDE agent can now invoke the tools directly.

---

## Workflow

A typical instrumentation workflow with an IDE agent:

### 1. Analyze

The agent scans your codebase to understand its structure:

```
> analyze_codebase --path /path/to/your/project

Found:
  - 12 REST controllers
  - 47 endpoints (23 GET, 15 POST, 6 PUT, 3 DELETE)
  - 3 external dependencies (stripe-api, postgres, redis)
  - 2 @AgentObservable annotations (existing)
  - 8 @AgentOperation annotations (existing)
```

### 2. Generate Config

Based on the analysis, generate the full AgentTel config:

```
> instrument_backend --path /path/to/your/project

Generated application.yml snippet:
  agenttel:
    topology:
      team: payments-platform
      tier: critical
    dependencies:
      - name: stripe-api
        type: external_api
        criticality: required
    operations:
      "[POST /api/payments]":
        profile: critical-write
        expected-latency-p50: "45ms"
        ...
```

The tool **proposes** changes without modifying files — the IDE agent decides what to apply.

### 3. Generate Frontend Config

If your project has a React frontend:

```
> instrument_frontend --path /path/to/your/frontend

Detected routes:
  /checkout/:step, /products, /cart, /account

Generated AgentTelWeb.init() config with:
  - Per-route baselines
  - Journey definitions (checkout flow)
  - Decision metadata
```

### 4. Validate

Cross-reference the config against source code:

```
> validate_instrumentation --path /path/to/your/project

Validation results:
  ✓ 47/47 endpoints covered
  ✓ All dependencies declared
  ⚠ 3 endpoints missing baselines (using defaults)
  ⚠ POST /api/refunds not marked as retryable (review recommended)
```

### 5. Suggest and Apply Improvements

After the service has been running and collecting telemetry:

```
> suggest_improvements --path /path/to/your/project

Improvements found:
  [LOW RISK] Calibrate baselines for POST /api/payments from observed P50=42ms, P99=190ms
  [LOW RISK] Calibrate baselines for GET /api/prices from observed P50=8ms, P99=35ms
  [MEDIUM RISK] Add circuit breaker config for stripe-api (12% error rate in last hour)
  [HIGH RISK] POST /api/payments runbook URL returns 404 — update needed
```

```
> apply_improvements --risk-level low

Applied 2 low-risk improvements:
  ✓ Updated POST /api/payments baselines: P50=42ms, P99=190ms
  ✓ Updated GET /api/prices baselines: P50=8ms, P99=35ms

Skipped 2 improvements (medium/high risk — review recommended)
```

---

## Design Principles

### Read-Then-Propose

Tools like `instrument_backend` and `instrument_frontend` return proposed config without modifying files. This keeps the IDE agent in control — it can review, adjust, and apply changes with user consent.

### Risk-Based Auto-Apply

`apply_improvements` categorizes changes by risk level:

| Risk | Auto-Applied | Examples |
|------|-------------|----------|
| **Low** | Yes | Baseline calibration from observed data, adding missing optional fields |
| **Medium** | No (flagged) | Adding circuit breaker config, changing escalation levels |
| **High** | No (flagged) | Changing retry policies, modifying dependency declarations |

### Live Health Integration

The instrument server can connect to the runtime [MCP server](mcp-server.md) to fetch real health and SLO data. This enables baseline calibration from actual traffic patterns rather than arbitrary defaults.

```bash
python -m agenttel_instrument.server \
  --port 8080 \
  --backend-mcp-url http://localhost:3000
```

---

## Further Reading

- [Architecture — agenttel-instrument](../concepts/architecture.md#agenttel-instrument) — module structure and design decisions
- [Quick Start](../getting-started/quick-start.md) — getting started with AgentTel
- [Configuration Reference](../reference/configuration.md) — the config format that the instrument agent generates
