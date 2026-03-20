# Express Example

Demonstrates AgentTel Node.js SDK with an Express payment service.

## Features

- Span enrichment with topology, baselines, anomaly detection
- SLO tracking with error budget
- Express middleware integration
- YAML configuration

## Setup

```bash
npm install
```

## Run

```bash
# With OTel Collector running on localhost:4318
npm run dev

# Or with custom endpoint
OTEL_EXPORTER_OTLP_ENDPOINT=http://collector:4318 npm run dev
```

## Test

```bash
# Create payment
curl -X POST http://localhost:3000/api/payments -H "Content-Type: application/json"

# Get payment
curl http://localhost:3000/api/payments/pay_12345

# Health check
curl http://localhost:3000/health
```

## View Traces

Open Jaeger at http://localhost:16686 to see enriched traces with AgentTel attributes.
