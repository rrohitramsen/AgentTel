# Multi-Agent Patterns

AgentTel supports multi-agent orchestration patterns -- coordinator, parallel, swarm, and hierarchical -- through agent identity, role-based permissions, and shared incident sessions.

This guide covers how to set up and configure multi-agent collaboration for incident response.

> **See also:**
> - [MCP Server & Agent Tools](mcp-server.md) -- setting up the MCP server
> - [Incident Response](incident-response.md) -- the Observe-Diagnose-Act-Verify workflow
> - [MCP Tools Reference](../reference/mcp-tools.md) -- full parameter reference for `create_session`, `add_session_entry`, `get_session`

---

## Agent Identity

Every MCP request can carry agent identity via HTTP headers or tool argument meta-parameters.

### HTTP Headers

| Header | Description |
|--------|-------------|
| `X-Agent-Id` | Unique agent identifier |
| `X-Agent-Role` | Agent role: `observer`, `diagnostician`, `remediator`, `admin` |
| `X-Agent-Session-Id` | Shared session ID for multi-agent collaboration |

### Tool Argument Meta-Parameters

Alternatively, pass identity as `_agent_*` prefixed arguments:

```json
{
  "method": "tools/call",
  "params": {
    "name": "get_incident_context",
    "arguments": {
      "operation_name": "POST /api/payments",
      "_agent_id": "diag-agent-1",
      "_agent_role": "diagnostician",
      "_agent_session_id": "sess-abc"
    }
  }
}
```

Meta-parameters are stripped before reaching the tool handler. Arguments take precedence over headers.

---

## Role-Based Tool Permissions

Each agent role grants a set of permissions that control which MCP tools it can invoke:

| Role | Permissions | Accessible Tools |
|------|------------|-----------------|
| `observer` | READ | `get_service_health`, `get_incident_context`, `get_slo_report`, `get_executive_summary`, `get_trend_analysis`, `get_playbook`, `get_error_analysis`, `get_session` |
| `diagnostician` | READ, DIAGNOSE | All observer tools + `verify_remediation_effect`, `create_session`, `add_session_entry` |
| `remediator` | READ, DIAGNOSE, REMEDIATE | All diagnostician tools + `execute_remediation`, `list_remediation_actions` |
| `admin` | ALL | All tools |

Anonymous agents (no identity) default to `observer` permissions. Denied requests return a JSON-RPC error:

```json
{"error": {"code": -32603, "message": "Permission denied: role 'observer' cannot access tool 'execute_remediation'"}}
```

### YAML Configuration

```yaml
agenttel:
  agent-roles:
    observer: [READ]
    diagnostician: [READ, DIAGNOSE]
    remediator: [READ, DIAGNOSE, REMEDIATE]
    custom-role: [READ, DIAGNOSE]
```

---

## Shared Incident Sessions

The session system implements the **blackboard pattern** for multi-agent collaboration. Agents post observations, diagnoses, and actions to a shared session that all participants can read.

### create_session

Creates a shared session for an incident.

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `incident_id` | string | Yes | The incident to create a session for |

**Permission required:** DIAGNOSE

### add_session_entry

Adds an entry to a shared session. Agent identity is automatically captured.

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `session_id` | string | Yes | Session ID to add entry to |
| `type` | string | Yes | `OBSERVATION`, `DIAGNOSIS`, `ACTION`, or `RECOMMENDATION` |
| `content` | string | Yes | The content of this entry |

**Permission required:** DIAGNOSE

### get_session

Retrieves all entries from a shared session.

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `session_id` | string | Yes | Session ID to retrieve |

**Permission required:** READ

### Example Multi-Agent Session

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

## Multi-Agent Patterns

AgentTel's multi-agent support enables the following patterns from [Anthropic](https://www.anthropic.com/engineering/building-effective-agents) and [Google Cloud](https://docs.cloud.google.com/architecture/choose-design-pattern-agentic-ai-system):

| Pattern | How AgentTel Supports It |
|---------|------------------------|
| **Coordinator** | One admin agent delegates to specialized agents (observer, diagnostician, remediator) via shared sessions |
| **Parallel** | Multiple diagnostician agents analyze different aspects simultaneously, posting to the same session |
| **Swarm** | Agents self-organize around incidents; session entries create shared context without central coordination |
| **Hierarchical** | Admin creates session -- diagnosticians investigate -- remediator executes; role permissions enforce the hierarchy |
| **Human-in-the-Loop** | Remediation actions can require `approved_by`; humans review session entries before approving |

### Coordinator Pattern

One admin agent orchestrates the workflow by delegating to specialist agents:

1. **Admin agent** receives an alert and creates a session via `create_session`
2. **Observer agent** calls `get_service_health` and `get_executive_summary`, posts findings via `add_session_entry` (type: `OBSERVATION`)
3. **Diagnostician agent** reads the session, calls `get_incident_context` and `get_error_analysis`, posts root cause via `add_session_entry` (type: `DIAGNOSIS`)
4. **Remediator agent** reads the session, calls `get_playbook` and `execute_remediation`, posts outcome via `add_session_entry` (type: `ACTION`)
5. **Admin agent** calls `get_session` to review the full timeline and verify resolution

### Parallel Pattern

Multiple diagnostician agents investigate different aspects simultaneously:

- **Agent A** analyzes error patterns via `get_error_analysis`
- **Agent B** checks change correlation via `get_incident_context`
- **Agent C** examines cross-stack impact via `get_cross_stack_context`

All three post to the same session. The coordinator reads consolidated findings.

### Swarm Pattern

No central coordinator. Agents self-organize:

1. Any agent that detects an anomaly creates a session
2. Other agents discover the session and contribute observations
3. When enough evidence accumulates, a remediator agent acts
4. The session serves as the shared blackboard -- no explicit delegation needed

### Hierarchical Pattern

Role permissions enforce a strict hierarchy:

- **Admin** creates sessions and has full tool access
- **Diagnosticians** can investigate and contribute to sessions but cannot execute remediation
- **Observers** can only read data and view sessions
- **Remediators** can execute actions but only after diagnosticians have contributed findings

---

## Next Steps

- **[MCP Tools Reference](../reference/mcp-tools.md)** -- full parameter details for `create_session`, `add_session_entry`, and `get_session`
- **[Incident Response](incident-response.md)** -- the complete Observe-Diagnose-Act-Verify workflow
- **[MCP Server & Agent Tools](mcp-server.md)** -- setting up the MCP server and Spring Boot integration
