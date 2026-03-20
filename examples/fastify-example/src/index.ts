import Fastify from 'fastify';
import { trace } from '@opentelemetry/api';
import { NodeSDK } from '@opentelemetry/sdk-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { Resource } from '@opentelemetry/resources';
import { ATTR_SERVICE_NAME, ATTR_SERVICE_VERSION } from '@opentelemetry/semantic-conventions';
import {
  AgentTelEngineBuilder,
  fastifyPlugin,
  loadConfig,
} from '@agenttel/node';

// Load AgentTel config
const config = loadConfig('agenttel.yml');

// Set up OTel
const endpoint = process.env.OTEL_EXPORTER_OTLP_ENDPOINT ?? 'http://localhost:4318';
const exporter = new OTLPTraceExporter({ url: `${endpoint}/v1/traces` });

const sdk = new NodeSDK({
  resource: new Resource({
    [ATTR_SERVICE_NAME]: config.topology?.serviceName ?? 'fastify-payment-service',
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

const app = Fastify({ logger: true });

// Apply AgentTel middleware via Fastify plugin
app.register(fastifyPlugin, {
  baselineProvider: engine.baselineProvider,
  topology: engine.topologyRegistry,
});

// Routes
app.post('/api/payments', async (_request, reply) => {
  const span = trace.getActiveSpan();

  // Simulate processing latency (30-70ms)
  await sleep(30 + Math.random() * 40);

  // Simulate occasional errors (~2% rate)
  if (Math.random() < 0.02) {
    span?.recordException(new Error('Payment processing failed: insufficient funds'));
    return reply.status(402).send({ error: 'insufficient_funds' });
  }

  return {
    id: `pay_${Math.floor(Math.random() * 100000)}`,
    status: 'completed',
    amount: 99.99,
  };
});

app.get('/api/payments/:id', async (request, _reply) => {
  const { id } = request.params as { id: string };

  // Simulate read latency (10-25ms)
  await sleep(10 + Math.random() * 15);

  return {
    id,
    status: 'completed',
    amount: 99.99,
  };
});

app.get('/health', async (_request, _reply) => {
  return { status: 'ok' };
});

// Start server
const port = Number(process.env.PORT ?? 3000);
app.listen({ port, host: '0.0.0.0' }, (err) => {
  if (err) {
    app.log.error(err);
    process.exit(1);
  }
  console.log(`Fastify Payment Service starting on :${port}`);
  console.log('  POST /api/payments      - Create payment');
  console.log('  GET  /api/payments/:id  - Get payment');
  console.log('  GET  /health            - Health check');
});

// Graceful shutdown
process.on('SIGINT', async () => {
  await app.close();
  await sdk.shutdown();
  process.exit(0);
});

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
