#!/usr/bin/env bash
#
# AgentTel Docker Demo
# One-click demo: builds and starts all services, generates traffic,
# queries MCP tools, and prints dashboard URLs.
#
# Usage:
#   ./docker-demo.sh          # Start demo
#   ./docker-demo.sh --down   # Tear down all containers
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker/docker-compose.yml"
MCP_PORT=8081
APP_PORT=8080

# ── Tear down ──────────────────────────────────────────────
if [ "${1:-}" = "--down" ]; then
    echo "=== Tearing down AgentTel demo ==="
    docker compose -f "$COMPOSE_FILE" down -v
    echo "Done."
    exit 0
fi

echo "============================================"
echo "  AgentTel Docker Demo"
echo "  One-click observability for AI agents"
echo "============================================"
echo ""

# ── 1. Build and start ────────────────────────────────────
echo "=== Building and starting services ==="
echo "This may take a few minutes on first run (Gradle build inside Docker)..."
echo ""
docker compose -f "$COMPOSE_FILE" up --build -d

# ── 2. Wait for payment-service ───────────────────────────
echo ""
echo "=== Waiting for payment-service to be ready ==="
for i in $(seq 1 60); do
    if curl -sf "http://localhost:${APP_PORT}/actuator/health" >/dev/null 2>&1; then
        echo "payment-service is ready!"
        break
    fi
    if [ "$i" -eq 60 ]; then
        echo "ERROR: payment-service failed to start within 60 seconds"
        echo "Check logs: docker compose -f $COMPOSE_FILE logs payment-service"
        exit 1
    fi
    printf "."
    sleep 2
done
echo ""

# ── 3. Generate traffic ──────────────────────────────────
echo ""
echo "=== Generating traffic ==="

echo "-> POST /api/payments (5 requests)"
for i in $(seq 1 5); do
    curl -s -X POST "http://localhost:${APP_PORT}/api/payments" \
        -H "Content-Type: application/json" \
        -d "{\"amount\":$((i * 25)),\"currency\":\"USD\"}" \
        -o /dev/null
    echo "  Request $i: sent"
done

echo "-> GET /api/payments/{id} (3 requests)"
for id in txn-abc txn-def txn-ghi; do
    curl -s "http://localhost:${APP_PORT}/api/payments/${id}" -o /dev/null
    echo "  GET $id: sent"
done

echo ""
echo "Waiting for spans to process..."
sleep 3
echo ""

# ── 4. Query MCP Server ──────────────────────────────────
echo "============================================"
echo "  MCP Server Tools (port ${MCP_PORT})"
echo "============================================"
echo ""

# Health check
echo "--- MCP Health Check ---"
curl -s "http://localhost:${MCP_PORT}/health" | python3 -m json.tool 2>/dev/null || \
    curl -s "http://localhost:${MCP_PORT}/health"
echo ""
echo ""

# Tool: get_service_health
echo "--- get_service_health ---"
curl -s -X POST "http://localhost:${MCP_PORT}/mcp" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"get_service_health","arguments":{"format":"text"}},"id":1}' \
    | python3 -c "import sys,json; r=json.load(sys.stdin); print(r['result']['content'][0]['text'])" 2>/dev/null || \
    curl -s -X POST "http://localhost:${MCP_PORT}/mcp" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"get_service_health","arguments":{"format":"text"}},"id":1}'
echo ""
echo ""

# Tool: get_incident_context
echo "--- get_incident_context (POST /api/payments) ---"
curl -s -X POST "http://localhost:${MCP_PORT}/mcp" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"get_incident_context","arguments":{"operation_name":"POST /api/payments"}},"id":2}' \
    | python3 -c "import sys,json; r=json.load(sys.stdin); print(r['result']['content'][0]['text'])" 2>/dev/null || \
    curl -s -X POST "http://localhost:${MCP_PORT}/mcp" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"get_incident_context","arguments":{"operation_name":"POST /api/payments"}},"id":2}'
echo ""
echo ""

# Tool: list_remediation_actions
echo "--- list_remediation_actions (POST /api/payments) ---"
curl -s -X POST "http://localhost:${MCP_PORT}/mcp" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"list_remediation_actions","arguments":{"operation_name":"POST /api/payments"}},"id":3}' \
    | python3 -c "import sys,json; r=json.load(sys.stdin); print(r['result']['content'][0]['text'])" 2>/dev/null || \
    curl -s -X POST "http://localhost:${MCP_PORT}/mcp" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"list_remediation_actions","arguments":{"operation_name":"POST /api/payments"}},"id":3}'
echo ""
echo ""

# Tool: get_recent_agent_actions
echo "--- get_recent_agent_actions ---"
curl -s -X POST "http://localhost:${MCP_PORT}/mcp" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"get_recent_agent_actions","arguments":{}},"id":4}' \
    | python3 -c "import sys,json; r=json.load(sys.stdin); print(r['result']['content'][0]['text'])" 2>/dev/null || \
    curl -s -X POST "http://localhost:${MCP_PORT}/mcp" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"get_recent_agent_actions","arguments":{}},"id":4}'
echo ""
echo ""

# ── 5. Dashboard URLs ────────────────────────────────────
echo "============================================"
echo "  Dashboards"
echo "============================================"
echo ""
echo "  Jaeger (traces):    http://localhost:16686"
echo "  Swagger UI:         http://localhost:8080/swagger-ui.html"
echo "  MCP Tool Docs:      http://localhost:8081/mcp/docs"
echo "  MCP Health:         http://localhost:8081/health"
echo "  App Health:         http://localhost:8080/actuator/health"
echo ""
echo "============================================"
echo "  Demo running! Press Ctrl+C or run:"
echo "  ./docker-demo.sh --down"
echo "============================================"
