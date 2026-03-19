# Go Service Example

Demonstrates AgentTel Go SDK with a payment service using `net/http`.

## Features

- Span enrichment with topology, baselines, anomaly detection
- SLO tracking with error budget
- net/http middleware integration
- YAML configuration

## Run

```bash
# With OTel Collector running on localhost:4318
go run .

# Or with custom endpoint
OTEL_EXPORTER_OTLP_ENDPOINT=collector:4318 go run .
```

## Test

```bash
# Create payment
curl -X POST http://localhost:8080/api/payments -H "Content-Type: application/json"

# Get payment
curl http://localhost:8080/api/payments/pay_12345

# Health check
curl http://localhost:8080/health
```

## View Traces

Open Jaeger at http://localhost:16686 to see enriched traces with AgentTel attributes.
