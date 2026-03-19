import express from 'express';
import { trace } from '@opentelemetry/api';
import { NodeSDK } from '@opentelemetry/sdk-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { Resource } from '@opentelemetry/resources';
import { ATTR_SERVICE_NAME, ATTR_SERVICE_VERSION } from '@opentelemetry/semantic-conventions';
import {
  AgentTelEngineBuilder,
  expressMiddleware,
  loadConfig,
} from '@agenttel/node';

// Load AgentTel config
const config = loadConfig('agenttel.yml');

// Set up OTel
const endpoint = process.env.OTEL_EXPORTER_OTLP_ENDPOINT ?? 'http://localhost:4318';
const exporter = new OTLPTraceExporter({ url: `${endpoint}/v1/traces` });

const sdk = new NodeSDK({
  resource: new Resource({
    [ATTR_SERVICE_NAME]: config.topology?.serviceName ?? 'express-payment-service',
    [ATTR_SERVICE_VERSION]: '1.0.0',
  }),
  traceExporter: exporter,
});

sdk.start();

// Build AgentTel engine
const engine = new AgentTelEngineBuilder()
  .withTeam(config.topology?.team ?? 'payments-platform')
  .withTier(config.topology?.tier ?? 'critical')
  .withDomain(config.topology?.domain ?? 'commerce')
  .build();

const app = express();
app.use(express.json());

// Apply AgentTel middleware
app.use(expressMiddleware({
  baselineProvider: engine.baselineProvider,
  topology: engine.topologyRegistry,
}));

// Routes
app.post('/api/payments', (_req, res) => {
  const span = trace.getActiveSpan();

  // Simulate processing
  const delay = 30 + Math.random() * 40;
  setTimeout(() => {
    // Simulate occasional errors
    if (Math.random() < 0.02) {
      span?.recordException(new Error('Payment processing failed: insufficient funds'));
      res.status(402).json({ error: 'insufficient_funds' });
      return;
    }

    res.json({
      id: `pay_${Math.floor(Math.random() * 100000)}`,
      status: 'completed',
      amount: 99.99,
    });
  }, delay);
});

app.get('/api/payments/:id', (req, res) => {
  const delay = 10 + Math.random() * 15;
  setTimeout(() => {
    res.json({
      id: req.params.id,
      status: 'completed',
      amount: 99.99,
    });
  }, delay);
});

app.get('/health', (_req, res) => {
  res.json({ status: 'ok' });
});

const port = process.env.PORT ?? 3000;
app.listen(port, () => {
  console.log(`Express Payment Service starting on :${port}`);
  console.log('  POST /api/payments      - Create payment');
  console.log('  GET  /api/payments/:id  - Get payment');
  console.log('  GET  /health            - Health check');
});

// Graceful shutdown
process.on('SIGINT', async () => {
  await sdk.shutdown();
  process.exit(0);
});
