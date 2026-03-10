# AgentTel Grafana Dashboard Templates

Pre-built Grafana dashboards for visualizing AgentTel telemetry data.

## Dashboards

| Dashboard | File | Description |
|-----------|------|-------------|
| **Overview** | `agenttel-overview.json` | Topology, baselines, anomalies, SLOs, decision metadata |
| **GenAI** | `agenttel-genai.json` | LLM calls, token usage, costs, RAG retrieval |
| **Agentic** | `agenttel-agentic.json` | Agent invocations, orchestration, tool calls, costs, guardrails |
| **Frontend** | `agenttel-frontend.json` | Page loads, user journeys, API calls, anomalies, correlation |

## Prerequisites

- **Grafana** 10.0+
- **Tempo** data source configured (for TraceQL queries)

## Import Instructions

1. In Grafana, go to **Dashboards > Import**
2. Click **Upload JSON file** and select the dashboard JSON
3. In the import dialog, select your **Tempo** data source for `DS_TEMPO`
4. Click **Import**

Alternatively, use the Grafana API:

```bash
curl -X POST http://localhost:3000/api/dashboards/import \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $GRAFANA_API_KEY" \
  -d @dashboards/agenttel-overview.json
```

## Template Variables

The dashboards use Grafana data source template variables:

| Variable | Type | Description |
|----------|------|-------------|
| `DS_TEMPO` | Data source | Tempo instance for TraceQL queries |

These are declared in the `__inputs` section of each JSON file and are mapped during import.

## Query Language

All dashboards use **TraceQL** for querying Tempo. Example patterns:

```
# Find all agent invocations
{ name = "invoke_agent" }

# Find anomalies on a specific route
{ span.agenttel.client.anomaly.detected = true && span.agenttel.client.page.route = "/checkout/payment" }

# Find expensive LLM calls
{ span.agenttel.genai.cost_usd > 0.1 }
```
