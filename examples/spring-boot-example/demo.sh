#!/usr/bin/env bash
#
# AgentTel Demo Script
# Boots the payment-service example, generates traffic, and queries the MCP server.
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
APP_PID=""
MCP_PORT=8081
APP_PORT=8080

cleanup() {
    echo ""
    echo "=== Cleaning up ==="
    if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        kill "$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
    fi
    echo "Done."
}
trap cleanup EXIT

echo "============================================"
echo "  AgentTel Demo — Payment Service Example"
echo "============================================"
echo ""

# ── 1. Build ──────────────────────────────────────────────
echo "=== Building project ==="
cd "$ROOT_DIR"
./gradlew :examples:spring-boot-example:build -x test --quiet 2>&1
echo "Build complete."
echo ""

# ── 2. Start the application ──────────────────────────────
echo "=== Starting payment-service ==="
cd "$ROOT_DIR/examples/spring-boot-example"
../../gradlew bootRun --quiet 2>&1 &
APP_PID=$!

echo "Waiting for application to start..."
for i in $(seq 1 30); do
    if curl -s "http://localhost:${APP_PORT}/actuator/health" >/dev/null 2>&1; then
        echo "Application started (PID: $APP_PID)"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "ERROR: Application failed to start within 30 seconds"
        exit 1
    fi
    sleep 1
done
echo ""

# ── 3. Generate traffic ──────────────────────────────────
echo "=== Generating traffic ==="

echo "→ POST /api/payments (5 requests)"
for i in $(seq 1 5); do
    curl -s -X POST "http://localhost:${APP_PORT}/api/payments" \
        -H "Content-Type: application/json" \
        -d "{\"amount\":$((i * 25)),\"currency\":\"USD\",\"recipient\":\"user-${i}\"}" \
        -o /dev/null
    echo "  Request $i: sent"
done

echo "→ GET /api/payments/{id} (3 requests)"
for id in txn-abc txn-def txn-ghi; do
    curl -s "http://localhost:${APP_PORT}/api/payments/${id}" -o /dev/null
    echo "  GET $id: sent"
done

echo ""
echo "Traffic generated. Waiting for spans to process..."
sleep 2
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

# Tool: tools/list
echo "--- tools/list (all available tools) ---"
curl -s -X POST "http://localhost:${MCP_PORT}/mcp" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"tools/list","params":{},"id":5}' \
    | python3 -m json.tool 2>/dev/null || \
    curl -s -X POST "http://localhost:${MCP_PORT}/mcp" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"tools/list","params":{},"id":5}'
echo ""

echo "============================================"
echo "  Demo complete!"
echo "============================================"
