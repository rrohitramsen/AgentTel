# Fastify Payment Service Example

Example payment service using Fastify with the AgentTel Node SDK for AI-native telemetry enrichment.

## Prerequisites

- Node.js 18+
- An OTLP-compatible collector (e.g., AgentTel Platform, Jaeger, or OpenTelemetry Collector)

## Running

```bash
# From this directory
npm install
npm run dev

# With custom OTel endpoint
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 npm run dev

# With custom port
PORT=3001 npm run dev
```

## Endpoints

| Method | Path               | Description     |
|--------|-------------------|-----------------|
| POST   | /api/payments     | Create payment  |
| GET    | /api/payments/:id | Get payment     |
| GET    | /health           | Health check    |
