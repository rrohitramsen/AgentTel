# AgentTel Spring Boot Example

A demo payment service showing AgentTel span enrichment, topology declaration, and the agent interface layer (MCP server).

## Quick Start

```bash
# From the repository root:
./gradlew :examples:spring-boot-example:bootRun
```

The service starts on `http://localhost:8080`.

## Try It

### 1. Make a Payment

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{"amount": 99.99, "currency": "USD"}'
```

### 2. Get a Payment

```bash
curl http://localhost:8080/api/payments/txn-12345
```

### 3. Health Check

```bash
curl http://localhost:8080/actuator/health
```

## What's Happening

Every request to the payment endpoints is automatically enriched with AgentTel attributes:

- **Topology:** `agenttel.topology.team=payments-platform`, `tier=critical`
- **Baselines:** `agenttel.baseline.latency_p50_ms=45.0` (from `@AgentOperation`)
- **Decisions:** `agenttel.decision.retryable=true`, `runbook_url=...`
- **Dependencies:** payment-gateway (external API), postgres-orders (database)

## With OTel Collector (Optional)

To see traces in Jaeger:

```bash
cd examples/spring-boot-example/docker
docker compose up -d
```

Then start the app and make requests. View traces at `http://localhost:16686`.

## Configuration

See `src/main/resources/application.yml` for the full AgentTel configuration including topology, dependencies, baselines, and anomaly detection settings.
