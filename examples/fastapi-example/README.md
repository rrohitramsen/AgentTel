# FastAPI Payment Service Example

Example payment service using FastAPI with the AgentTel Python SDK for AI-native telemetry enrichment. Includes an MCP server exposing agent tools on port 8091.

## Prerequisites

- Python 3.11+
- An OTLP-compatible collector (e.g., AgentTel Platform, Jaeger, or OpenTelemetry Collector)

## Running

```bash
# From this directory
pip install -r requirements.txt

# If using the local SDK (development)
pip install -e ../../agenttel-python[fastapi,agent]

# Run the service
python app.py

# With custom OTel endpoint
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 python app.py

# With custom port
PORT=9000 python app.py
```

## Endpoints

| Method | Path                  | Description     |
|--------|-----------------------|-----------------|
| POST   | /api/payments         | Create payment  |
| GET    | /api/payments/{id}    | Get payment     |
| GET    | /health               | Health check    |

## MCP Server

The service starts an MCP server on port 8091 with 15 built-in tools for AI agent integration:

- `get_service_health` -- Current health status
- `get_operation_baselines` -- Rolling baselines for all operations
- `get_slo_status` -- SLO compliance and budget
- `get_topology` -- Service topology map
- And 11 more tools (see `/mcp/docs` on port 8091)
