# MCP Tools Reference

Complete reference for all 15 built-in MCP tools provided by the `agenttel-agent` module. Each tool is invocable via the MCP server's JSON-RPC `tools/call` method.

> **See also:**
> - [MCP Server & Agent Tools](../guides/mcp-server.md) -- setting up the MCP server, custom tools, and Spring Boot integration
> - [Incident Response](../guides/incident-response.md) -- the Observe-Diagnose-Act-Verify workflow
> - [Multi-Agent Patterns](../guides/multi-agent.md) -- agent identity, permissions, and shared sessions

---

## Tool Categories

| Category | Tools | Purpose |
|----------|-------|---------|
| **Core** | [get_service_health](#get_service_health), [get_incident_context](#get_incident_context), [list_remediation_actions](#list_remediation_actions), [execute_remediation](#execute_remediation), [get_recent_agent_actions](#get_recent_agent_actions) | Health, incidents, remediation, audit |
| **Reporting** | [get_slo_report](#get_slo_report), [get_executive_summary](#get_executive_summary), [get_trend_analysis](#get_trend_analysis), [get_cross_stack_context](#get_cross_stack_context) | SLOs, trends, summaries |
| **Agent-Autonomous** | [get_playbook](#get_playbook), [verify_remediation_effect](#verify_remediation_effect), [get_error_analysis](#get_error_analysis) | Playbooks, verification, error classification |
| **Multi-Agent** | [create_session](#create_session), [add_session_entry](#add_session_entry), [get_session](#get_session) | Shared incident sessions |

---

## Core Tools

### get_service_health

Returns current service health including operation metrics, dependency status, and SLO budget.

**Permission required:** READ

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `format` | string | No | `"text"` (default) or `"json"` |

**When to use:** As the first tool call in any agent workflow. Provides a quick overview of whether the service is healthy, degraded, or critical.

**Example text output:**

```
SERVICE: payment-service | STATUS: DEGRADED | 2025-01-15T14:30:00Z
OPERATIONS:
  POST /api/payments: err=5.2% p50=312ms p99=1200ms [ELEVATED]
  GET /api/prices: err=0.1% p50=12ms p99=45ms
DEPENDENCIES:
  postgres: err=0.0% avg=8ms
  stripe-api: err=12.3% avg=2100ms
SLOs:
  payment-availability: budget=22.0% burn=0.8x
```

---

### get_incident_context

Returns a complete incident diagnosis for a specific operation -- what's happening, what changed, what's affected, and what to do.

**Permission required:** READ

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `operation_name` | string | Yes | The operation to diagnose |

**When to use:** After `get_service_health` indicates a problem. Provides the full context an agent needs to understand and act on an incident.

**Example output:**

```
=== INCIDENT inc-a3f2b1c4 ===
SEVERITY: HIGH
TIME: 2025-01-15T14:30:00Z
SUMMARY: POST /api/payments experiencing elevated error rate (5.2%)

## WHAT IS HAPPENING
Operation: POST /api/payments
Error Rate: 5.2% (baseline: 0.1%)
Latency P50: 312ms (baseline: 45ms)
Anomaly Score: 0.85
Service Health: DEGRADED
Patterns: ERROR_RATE_SPIKE

## WHAT CHANGED
Last Deploy: v2.1.0 at 2025-01-15T14:00:00Z
  [deployment] Deployed version v2.1.0 (2025-01-15T14:00:00Z)
  [config_change] Updated rate limit to 500 rps (2025-01-15T13:45:00Z)

## WHAT IS AFFECTED
Scope: operation_specific
User-Facing: YES
Affected Ops: POST /api/payments
Affected Deps: stripe-api
Affected Consumers: checkout-service

## SUGGESTED ACTIONS
Escalation: page_oncall
  - [HIGH] rollback_deployment: Rollback to previous version (NEEDS APPROVAL)
  - [MEDIUM] enable_circuit_breakers: Circuit break stripe-api

## SIMILAR PAST INCIDENTS
  inc-2024-dec-03: stripe-api timeout -> Increased timeout to 10s
```

---

### list_remediation_actions

Lists available remediation actions for a specific operation.

**Permission required:** REMEDIATE

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `operation_name` | string | Yes | Operation to get actions for |

**When to use:** After diagnosing an incident, to see what actions are available before executing one.

---

### execute_remediation

Executes a remediation action. Actions requiring approval need the `approved_by` field.

**Permission required:** REMEDIATE

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `action_name` | string | Yes | Name of the action to execute |
| `reason` | string | Yes | Reason for executing this action |
| `approved_by` | string | No | Required for actions needing approval |

**When to use:** After reviewing available actions via `list_remediation_actions` and deciding on the appropriate remediation. Always provide a clear reason for audit trail purposes.

---

### get_recent_agent_actions

Returns the audit trail of recent agent decisions and actions.

**Permission required:** READ

**Parameters:** None.

**When to use:** To review what actions have already been taken, especially when multiple agents are working on the same incident. Prevents duplicate remediation attempts.

---

## Reporting Tools

### get_slo_report

Returns SLO compliance report across all tracked operations -- budget remaining, burn rate, and compliance status.

**Permission required:** READ

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `format` | string | No | `"text"` (default) or `"json"` |

**When to use:** During the REPORT phase of the decision loop, or to understand the business impact of an ongoing incident.

**Example text output:**

```
=== SLO REPORT ===
Generated: 2025-01-15T14:30:00Z
Total SLOs: 2

SUMMARY: 1 healthy, 1 at risk, 0 violated

  [HEALTHY] payment-availability
    Target: 99.90%  Actual: 99.95%  Budget: 50.0%  Burn: 0.5x  Requests: 10000  Failed: 5
  [AT_RISK] payment-latency-p99
    Target: 200ms  Actual: 312ms  Budget: 22.0%  Burn: 0.8x  Requests: 10000  Failed: 520
```

---

### get_executive_summary

Returns a high-level executive summary of service health (~300 tokens), optimized for LLM context windows.

**Permission required:** READ

**Parameters:** None.

**When to use:** When you need a concise overview for system prompt injection or quick triage. More compact than `get_service_health`.

**Example output:**

```
=== EXECUTIVE SUMMARY ===
Service: payment-service | Status: DEGRADED | 2025-01-15T14:30:00Z

STATUS: 1 operation degraded. POST /api/payments error rate elevated (5.2%).

TOP ISSUES:
  1. POST /api/payments: err=5.2% (baseline 0.1%), p50=312ms (baseline 45ms)

SLO BUDGET: 1/2 healthy, 1 at risk (payment-latency-p99: 22% remaining)

OPERATIONS: 2 tracked, 10,000 total requests
```

---

### get_trend_analysis

Returns latency, error rate, and throughput trends for an operation over a time window with direction indicators.

**Permission required:** READ

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `operation_name` | string | Yes | Operation name to analyze trends for |
| `window_minutes` | string | No | Time window in minutes (default: `"30"`) |

**When to use:** To understand whether a problem is getting worse or stabilizing. Useful both during active incidents and for post-incident analysis.

**Example output:**

```
=== TREND ANALYSIS: POST /api/payments ===
Window: 30 minutes | Samples: 12

LATENCY P50: 45ms -> 312ms  RISING (+593%)
LATENCY P99: 200ms -> 1200ms  RISING (+500%)
ERROR RATE: 0.1% -> 5.2%  RISING (+5100%)
THROUGHPUT: 180 rpm -> 165 rpm  FALLING (-8%)

ASSESSMENT: Operation is degrading. Latency and error rate are both rising sharply.
```

---

### get_cross_stack_context

Returns correlated frontend and backend context for an operation -- traces the full user-to-database path when `agenttel-web` is connected.

**Permission required:** READ

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `operation_name` | string | Yes | Backend operation name to get cross-stack context for |

**When to use:** When you need to understand the full user impact of a backend issue. Requires the `agenttel-web` frontend SDK for frontend metrics; falls back gracefully without it.

**Example output (with frontend connected):**

```
=== CROSS-STACK CONTEXT: POST /api/payments ===

## FRONTEND (User Experience)
  Route: /checkout/payment
  Page Load P50: 850ms (baseline: 800ms)
  API Call P50: 520ms (baseline: 300ms)
  Journey: checkout (step 4/5)
  Funnel Health: 62% completion (baseline: 65%)
  Anomalies: slow_page_load
  Affected Users: ~120 in last 15 min

## BACKEND (payment-service)
  Operation: POST /api/payments
  Error Rate: 5.2% (baseline: 0.1%)
  Latency P50: 312ms (baseline: 45ms)
  Deviation: ELEVATED

## SLO STATUS
  payment-availability: 99.95% (target: 99.9%) budget=50.0%
  payment-latency-p99: 312ms (target: 200ms) budget=22.0%

## CORRELATION
  Frontend -> Backend trace linking: active
  Browser trace IDs correlated with backend spans via W3C Trace Context
```

**Example output (without frontend):**

```
## FRONTEND (User Experience)
  Status: No frontend telemetry connected
  Note: Connect agenttel-web SDK to enable cross-stack correlation.
```

---

## Agent-Autonomous Tools

### get_playbook

Returns a structured, machine-readable playbook with step-by-step remediation instructions for an incident pattern. Playbooks replace opaque runbook URLs with actionable decision trees that agents can follow autonomously.

**Permission required:** READ

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `operation_name` | string | No | Operation name to get playbook for |
| `pattern` | string | No | Incident pattern name (e.g., `"cascade_failure"`, `"error_rate_spike"`) |

**When to use:** During the PLAN phase, after diagnosing the incident pattern. Provides a step-by-step decision tree the agent can follow.

**Example output:**

```
PLAYBOOK: Error Rate Spike Response
Trigger Patterns: error_rate_spike

Steps:
  [1] CHECK: Check error category breakdown
      Condition: error_rate > baseline * 5
      -> success: step 2 | failure: step 5

  [2] DECISION: Is this a dependency issue?
      Condition: error_category == dependency_timeout OR connection_error
      -> yes: step 3 | no: step 4

  [3] ACTION: Enable circuit breaker on failing dependency
      Action: enable_circuit_breaker
      -> success: step 5 | failure: step 4

  [4] ACTION: Rollback to previous version (REQUIRES APPROVAL)
      Action: rollback_deployment
      -> success: step 5

  [5] CHECK: Verify error rate has decreased
      Condition: current_error_rate < baseline * 2
```

---

### verify_remediation_effect

Verifies whether a previously executed remediation action was effective by comparing pre- and post-action health snapshots. Default verification delay is 30 seconds.

**Permission required:** DIAGNOSE

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `action_name` | string | Yes | Name of the remediation action to verify |

**When to use:** After executing a remediation action, to confirm it was effective. Wait at least 30 seconds after execution before calling this tool.

**Example output:**

```
REMEDIATION VERIFICATION:
  Action: toggle-payment-gateway-circuit-breaker
  Effective: YES
  Latency delta: -120.5ms
  Error rate delta: -0.0420
  Health: DEGRADED -> HEALTHY
  Verified at: 2025-01-15T14:31:30Z
```

---

### get_error_analysis

Returns error category breakdown and baseline confidence for an operation. Helps agents choose the right remediation by understanding *why* errors are occurring.

**Permission required:** READ

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `operation_name` | string | Yes | The operation name to analyze errors for |

**When to use:** During the PLAN phase, to understand the distribution of error types before choosing a remediation strategy. For example, if 90% of errors are `dependency_timeout`, a circuit breaker is more appropriate than a rollback.

**Example output:**

```
ERROR ANALYSIS: POST /api/payments
  Total requests: 10000
  Error count: 520
  Error rate: 5.20%
  Deviation: elevated
  Baseline confidence: high (1250 samples)
```

---

## Multi-Agent Tools

For detailed multi-agent setup, see the [Multi-Agent Patterns guide](../guides/multi-agent.md).

### create_session

Creates a shared session for an incident. Sessions implement the blackboard pattern for multi-agent collaboration.

**Permission required:** DIAGNOSE

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `incident_id` | string | Yes | The incident to create a session for |

**When to use:** When an incident is detected and multiple agents need to collaborate on diagnosis and remediation.

---

### add_session_entry

Adds an entry to a shared session. Agent identity is automatically captured from headers or meta-parameters.

**Permission required:** DIAGNOSE

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `session_id` | string | Yes | Session ID to add entry to |
| `type` | string | Yes | `OBSERVATION`, `DIAGNOSIS`, `ACTION`, or `RECOMMENDATION` |
| `content` | string | Yes | The content of this entry |

**When to use:** To share findings, diagnoses, recommendations, or action outcomes with other agents participating in the same incident session.

---

### get_session

Retrieves all entries from a shared session.

**Permission required:** READ

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `session_id` | string | Yes | Session ID to retrieve |

**When to use:** To review the current state of a multi-agent investigation, check what other agents have found, and decide on next steps.

**Example output:**

```
=== SESSION a3f2b1c4 ===
Incident: inc-payment-spike
Created: 2025-01-15T14:30:00Z
Entries: 4

[14:30:05Z] OBSERVATION by monitor-agent (observer): Error rate on POST /api/payments
  spiked to 5.2% (baseline: 0.1%)
[14:30:08Z] DIAGNOSIS by diag-agent (diagnostician): Root cause is stripe-api timeout.
  62% of errors are dependency_timeout, correlated with deploy v2.1.0 (confidence: 0.85)
[14:30:12Z] RECOMMENDATION by diag-agent (diagnostician): Enable circuit breaker on
  stripe-api, then verify error rate in 60s
[14:30:15Z] ACTION by remediation-bot (remediator): Executed toggle_circuit_breaker.
  Verification scheduled.
```

---

## Registering Custom Tools

Extend the MCP server with domain-specific tools:

```java
McpServer server = builder.build();

server.registerTool(
    new McpToolDefinition(
        "search_logs",
        "Search recent application logs for a pattern",
        Map.of("query", new ParameterDefinition("string", "Search query"),
               "timeframe", new ParameterDefinition("string", "Time range (e.g., '1h', '30m')")),
        List.of("query")
    ),
    args -> logService.search(args.get("query"), args.get("timeframe"))
);
```

Custom tools inherit the permission system. Set the required permission when registering:

```java
server.registerTool(toolDefinition, handler, Permission.DIAGNOSE);
```

---

## Quick Reference Table

| # | Tool | Permission | Required Params | Optional Params |
|---|------|-----------|----------------|-----------------|
| 1 | `get_service_health` | READ | -- | `format` |
| 2 | `get_incident_context` | READ | `operation_name` | -- |
| 3 | `list_remediation_actions` | REMEDIATE | `operation_name` | -- |
| 4 | `execute_remediation` | REMEDIATE | `action_name`, `reason` | `approved_by` |
| 5 | `get_recent_agent_actions` | READ | -- | -- |
| 6 | `get_slo_report` | READ | -- | `format` |
| 7 | `get_executive_summary` | READ | -- | -- |
| 8 | `get_trend_analysis` | READ | `operation_name` | `window_minutes` |
| 9 | `get_cross_stack_context` | READ | `operation_name` | -- |
| 10 | `get_playbook` | READ | -- | `operation_name`, `pattern` |
| 11 | `verify_remediation_effect` | DIAGNOSE | `action_name` | -- |
| 12 | `get_error_analysis` | READ | `operation_name` | -- |
| 13 | `create_session` | DIAGNOSE | `incident_id` | -- |
| 14 | `add_session_entry` | DIAGNOSE | `session_id`, `type`, `content` | -- |
| 15 | `get_session` | READ | `session_id` | -- |
