# AgentTel Spring Boot Example

A demo payment service showing AgentTel span enrichment, topology declaration, and the agent interface layer (MCP server).

## One-Click Docker Demo

The fastest way to see AgentTel in action — starts the app, OTel Collector, and Jaeger together:

```bash
cd examples/spring-boot-example
docker compose -f docker/docker-compose.yml up --build
```

Or run the guided demo script (generates traffic and queries MCP tools):

```bash
./docker-demo.sh
```

### Dashboards

| Dashboard | URL |
|-----------|-----|
| Jaeger (traces) | http://localhost:16686 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| MCP Tool Docs | http://localhost:8081/mcp/docs |
| MCP Health | http://localhost:8081/health |
| App Health | http://localhost:8080/actuator/health |

### Try the API

```bash
# Process a payment
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{"amount": 99.99, "currency": "USD"}'

# Get a payment
curl http://localhost:8080/api/payments/txn-12345

# Query MCP: service health
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"get_service_health","arguments":{"format":"text"}},"id":1}'
```

### Teardown

```bash
docker compose -f docker/docker-compose.yml down -v
# or
./docker-demo.sh --down
```

## Local Development

If you prefer running without Docker:

```bash
# From the repository root:
./gradlew :examples:spring-boot-example:bootRun
```

The service starts on `http://localhost:8080`. MCP server starts on `http://localhost:8081`.

To see traces in Jaeger, start the infrastructure first:

```bash
cd examples/spring-boot-example/docker
docker compose up -d otel-collector jaeger
```

Then start the app with the Docker profile to send traces via OTLP:

```bash
SPRING_PROFILES_ACTIVE=docker ./gradlew :examples:spring-boot-example:bootRun
```

## What's Happening

Every request to the payment endpoints is automatically enriched with AgentTel attributes:

- **Topology:** `agenttel.topology.team=payments-platform`, `tier=critical`
- **Baselines:** `agenttel.baseline.latency_p50_ms=45.0` (from config)
- **Decisions:** `agenttel.decision.retryable=true`, `runbook_url=...`
- **Dependencies:** payment-gateway (external API), postgres-orders (database)

## Configuration

See `src/main/resources/application.yml` for the full AgentTel configuration including topology, dependencies, baselines, and anomaly detection settings.
