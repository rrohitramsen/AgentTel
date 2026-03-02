# User Journeys

Four personas interact with AgentTel differently. This document walks through each journey — from first contact to fully autonomous operation.

---

## Persona 1: New App Developer

> "I'm starting a new Spring Boot + React app. I want AgentTel from day one."

### Today's Journey (Manual)

**Step 1: Create the app**

Standard Spring Boot + React/Vite setup. Nothing AgentTel-specific yet.

**Step 2: Analyze the codebase**

```bash
agenttel analyze ./my-app/backend --output agenttel.yml
```

Output:
```
Analyzing my-app/backend...

Discovered:
  Framework: Spring Boot 3.4.x
  Endpoints: 6 REST endpoints
  Dependencies: postgres (JPA), redis (Lettuce)

Generated: agenttel.yml
  - 6 operations with placeholder baselines
  - 2 dependencies detected
  - Suggested profiles: critical-write (2 ops), read-only (4 ops)

Review agenttel.yml and adjust baselines to match your SLO targets.
```

**Step 3: Add backend instrumentation**

Add to `build.gradle.kts`:
```kotlin
implementation("io.agenttel:agenttel-spring-boot-starter:0.1.0-alpha")
```

Add to main application class:
```java
@AgentObservable(
    team = "my-team",
    tier = "tier-1",
    domain = "commerce"
)
@SpringBootApplication
public class MyApplication { ... }
```

**Step 4: Add frontend instrumentation**

```bash
cd my-app/frontend
npm install @agenttel/web
```

Create `agenttel.config.ts`:
```typescript
import { AgentTelWeb } from '@agenttel/web';

AgentTelWeb.init({
  appName: 'my-app-web',
  collectorEndpoint: '/otlp',
  routes: {
    '/dashboard': { businessCriticality: 'engagement' },
    '/checkout': { businessCriticality: 'revenue' },
  },
});
```

**Step 5: Start observability infrastructure**

```bash
docker compose up  # otel-collector + jaeger
```

**Step 6: See results**

- Open Jaeger at `localhost:16686` — unified traces from browser to database
- MCP server exposes 9 tools for AI consumption
- Monitor agent can start watching immediately

### Future Journey (With Instrumentation Agent)

Steps 2-5 collapse into one command:

```bash
agenttel instrument ./my-app
```

The Instrumentation Agent:
1. Scans both `./my-app/backend` and `./my-app/frontend`
2. Detects Spring Boot + React
3. Adds all dependencies, annotations, SDK init, and config
4. Generates `docker-compose.yml` for infrastructure
5. Presents changes as a git diff for review

The developer reviews, commits, and runs `docker compose up`. Autonomous telemetry from minute one.

---

## Persona 2: Existing App Owner

> "I have a running service with 200K requests/day. I want to add AgentTel without breaking anything."

### Step 1: Analyze

```bash
agenttel analyze ./payment-service --output agenttel.yml
```

The scanner detects:
- 12 REST endpoints across 3 controllers
- Dependencies: postgres (JPA), stripe-api (RestTemplate), kafka (KafkaTemplate)
- 2 consumers: notification-service, analytics-service

### Step 2: Validate

```bash
agenttel validate --config agenttel.yml --source ./payment-service
```

Output:
```
Warnings:
  - POST /api/refunds: no runbook_url configured
  - stripe-api dependency: no circuit_breaker configured
  - /api/admin/* endpoints: no escalation_level set

Suggestions:
  - All baselines are TODO. Deploy and run suggest-baselines after 1 hour.
  - stripe-api is an external HTTP dependency. Consider adding a circuit
    breaker with a fallback description.
```

### Step 3: Add dependency and deploy

Add `agenttel-spring-boot-starter` to `build.gradle.kts`. Add `@AgentObservable` to the main class. Copy `agenttel.yml` into `src/main/resources/`. Deploy normally.

AgentTel enriches spans transparently — zero application code changes beyond the annotation. Existing OTel instrumentation continues to work.

### Step 4: Calibrate baselines

After 1 hour of production traffic:

```bash
agenttel suggest-baselines --mcp-url http://localhost:8081 --config agenttel.yml
```

Output:
```
Baseline suggestions (from observed traffic):

  POST /api/payments:
    p50: 45ms (observed from 12,340 requests)
    p99: 180ms

  GET /api/payments/{id}:
    p50: 8ms (observed from 45,120 requests)
    p99: 35ms

  POST /api/refunds:
    p50: 62ms (observed from 890 requests)
    p99: 250ms

Updated agenttel.yml with observed baselines.
```

### Step 5: Dashboard lights up

The Dashboard now shows:
- SLO compliance at 99.4%
- All 12 operations with real baselines
- Dependency health (postgres: healthy, stripe-api: healthy, kafka: healthy)
- Zero anomalies

### Step 6: System improves itself

Over the next week:
- Feedback Engine detects that `POST /api/refunds` P50 drifted from 62ms to 78ms → auto-updates baseline
- Gap detector finds new endpoint `PATCH /api/orders/{id}/status` added by another developer but not in config → suggests adding it
- Monitor records 3 minor incidents, all auto-remediated (cache flush on redis latency spike)
- SLO compliance trends from 99.4% to 99.7% as baselines become more accurate

**The system becomes more effective each week without human intervention.**

---

## Persona 3: SRE / Platform Engineer

> "I want fleet-wide visibility and autonomous incident response across 8 microservices."

### Step 1: Fleet Overview

Open the AgentTel Dashboard at `http://agenttel-dashboard:3001`.

The Fleet Overview page shows:

```
┌─────────────────────────────────────────────────────────┐
│  FLEET OVERVIEW                      Last updated: 2s   │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Services: 8 total                                      │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐             │
│  │ payment   │ │ order     │ │ user      │             │
│  │ ● HEALTHY │ │ ● HEALTHY │ │ ⚠ DEGRADED│             │
│  │ SLO: 99.8%│ │ SLO: 99.5%│ │ SLO: 97.2%│             │
│  └───────────┘ └───────────┘ └───────────┘             │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐             │
│  │ inventory │ │ search    │ │ notify    │             │
│  │ ● HEALTHY │ │ ● HEALTHY │ │ ● HEALTHY │             │
│  └───────────┘ └───────────┘ └───────────┘             │
│  ┌───────────┐ ┌───────────┐                            │
│  │ analytics │ │ auth      │                            │
│  │ ● HEALTHY │ │ ● HEALTHY │                            │
│  └───────────┘ └───────────┘                            │
│                                                         │
│  Active Anomalies: 2                                    │
│  Recent Monitor Actions: circuit_break (user-service)   │
│  Overall SLO: 99.1% (target: 99.5%)                    │
└─────────────────────────────────────────────────────────┘
```

### Step 2: Investigate degradation

SRE clicks into `user-service` and sees:

```
USER-SERVICE — Status: DEGRADED

Operations:
  GET /api/users/{id}     P50: 120ms (baseline: 15ms)  ⚠ 8.0x
  POST /api/users         P50: 45ms  (baseline: 30ms)  ↑ 1.5x
  GET /api/users/search   P50: 890ms (baseline: 200ms) ⚠ 4.5x

Dependencies:
  postgres-users    Error: 0.1%    Latency: 5ms     ● HEALTHY
  redis-sessions    Error: 15.2%   Latency: 2100ms  ✖ CRITICAL

Monitor Decision (3 min ago):
  Action: circuit_break redis-sessions (auto-approved)
  Reasoning: "redis-sessions error rate spiked to 15.2% correlating
    with deployment user-service:v3.2.1. Circuit breaker is the
    least-disruptive action. Similar pattern on 2025-01-15."
  Confidence: 0.88
  Verification: Error rate dropped to 0.3% within 2 minutes.
```

### Step 3: Review escalated action

Monitor also escalated: "Rollback deployment user-service:v3.2.1 (NEEDS APPROVAL)"

SRE reviews the reasoning, confirms the deployment correlation, and clicks **Approve** in the Dashboard. The Monitor executes the rollback via `execute_remediation` MCP tool. Dashboard shows real-time verification as latency returns to baseline.

### Step 4: Check Coverage Gaps

The Coverage Gaps panel shows:

```
COVERAGE GAPS                                  Priority
─────────────────────────────────────────────  ────────
notification-service has no AgentTel           HIGH
  telemetry (listed as dependency of
  payment-service and order-service)

user-service: DELETE /api/users/{id}           MEDIUM
  exists in source but not in agenttel.yml

search-service: baselines are 3 weeks old     LOW
  (observed P50 drifted 1.8x from config)
```

SRE clicks "Instrument" on notification-service. The Instrumentation Agent scans its codebase and presents changes for review.

### Step 5: Continuous improvement

Over the following month:
- Monitor handles 12 incidents autonomously (8 circuit breakers, 3 cache flushes, 1 approved rollback)
- Feedback Engine auto-updates 23 stale baselines
- 2 new endpoints detected and added to config automatically
- Fleet SLO compliance improves from 99.1% to 99.6%
- SRE intervention drops from daily to weekly

---

## Persona 4: AI/ML Engineer

> "I want to build custom reasoning agents that consume AgentTel data."

### Step 1: Discover available tools

```bash
curl -X POST http://localhost:8081/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

Response includes 9 tools:
- `get_service_health` — Current health with operations, dependencies, SLO budget
- `get_incident_context` — Full incident context for a specific operation
- `list_remediation_actions` — Available actions with risk levels
- `execute_remediation` — Execute an action (with approval flow)
- `get_recent_agent_actions` — Audit trail of AI decisions
- `get_slo_report` — SLO compliance, budget, burn rate
- `get_trend_analysis` — Time-series trends per operation
- `get_executive_summary` — ~300 token LLM-optimized summary
- `get_cross_stack_context` — Frontend + backend correlated view

### Step 2: Build a custom agent

```python
import httpx
import json

class MyCustomAgent:
    def __init__(self, mcp_url: str):
        self.mcp_url = mcp_url

    async def call_tool(self, tool_name: str, args: dict) -> str:
        async with httpx.AsyncClient() as client:
            response = await client.post(
                f"{self.mcp_url}/mcp",
                json={
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "params": {"name": tool_name, "arguments": args},
                    "id": 1,
                },
            )
            result = response.json()
            return result["result"]["content"][0]["text"]

    async def predict_slo_breach(self):
        """Custom ML model to predict SLO breaches before they happen."""
        # Get current trends
        trend = await self.call_tool("get_trend_analysis", {
            "operation_name": "POST /api/payments",
            "window_minutes": "60",
        })
        # Get SLO budget
        slo = await self.call_tool("get_slo_report", {"format": "json"})

        # Apply custom prediction model
        # ...
        # If breach predicted, take preemptive action
        if predicted_breach:
            await self.call_tool("execute_remediation", {
                "action_name": "scale_up",
                "reason": "Predicted SLO breach in 15 minutes based on trend analysis",
            })
```

### Step 3: Extend with custom tools

The engineer can also register custom MCP tools by extending `AgentTelMcpServerBuilder`:

```java
AgentTelMcpServerBuilder.create(contextProvider)
    .addCustomTool(
        "predict_capacity",
        "Predict capacity needs for the next hour",
        Map.of("service", "string"),
        args -> myCapacityPredictor.predict(args.get("service"))
    )
    .build();
```

### Step 4: Contribute to the loop

The custom agent's decisions are traced as OTel spans. If it executes remediation actions, those appear in the Dashboard's Monitor Log. The Feedback Engine can learn from the custom agent's outcomes just like it learns from the built-in Monitor.

---

## Journey Comparison

| | New Developer | Existing Owner | SRE | AI Engineer |
|---|---|---|---|---|
| **Entry point** | `agenttel analyze` | `agenttel analyze` | Dashboard | MCP tools/list |
| **Time to value** | 30 minutes | 1 hour | Immediate | 1 hour |
| **Human effort** | One-time setup | One-time setup + baseline calibration | Review escalations | Build agent |
| **Ongoing work** | None (autonomous) | None (autonomous) | Weekly review | Iterate on models |
| **Loop phase** | Instrument → Collect | Instrument → Collect → Calibrate | Report → Fix | Detect → Fix |

All four personas benefit from the autonomous loop. Once instrumented, the system improves itself. The SRE's weekly review gets shorter each week as the Monitor learns from past incidents and the Feedback Engine fills coverage gaps automatically.
