# Gin Payment Service Example

Example payment service using the Gin framework with AgentTel Go SDK for AI-native telemetry enrichment.

## Prerequisites

- Go 1.22+
- An OTLP-compatible collector (e.g., AgentTel Platform, Jaeger, or OpenTelemetry Collector)

## Running

```bash
# From this directory
go mod tidy
go run main.go

# With custom OTel endpoint
OTEL_EXPORTER_OTLP_ENDPOINT=localhost:4318 go run main.go

# With custom port
PORT=9090 go run main.go
```

## Endpoints

| Method | Path               | Description     |
|--------|-------------------|-----------------|
| POST   | /api/payments     | Create payment  |
| GET    | /api/payments/:id | Get payment     |
| GET    | /health           | Health check    |
