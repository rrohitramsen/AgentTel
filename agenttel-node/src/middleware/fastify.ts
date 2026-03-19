import { trace } from '@opentelemetry/api';
import type { BaselineProvider } from '../baseline/provider.js';
import type { TopologyRegistry } from '../topology/registry.js';
import * as attrs from '../attributes.js';

export interface FastifyPluginOptions {
  baselineProvider?: BaselineProvider;
  topology?: TopologyRegistry;
}

interface FastifyRequest {
  method: string;
  routeOptions?: { url: string };
  url: string;
}

interface FastifyReply {
  statusCode: number;
}

interface FastifyInstance {
  addHook(hook: string, handler: (req: FastifyRequest, reply: FastifyReply, done: () => void) => void): void;
}

/**
 * Fastify plugin that enriches the current OTel span with AgentTel attributes.
 *
 * Usage:
 * ```ts
 * import { fastifyPlugin } from '@agenttel/node';
 * fastify.register(fastifyPlugin, { topology, baselineProvider });
 * ```
 */
export function fastifyPlugin(
  fastify: FastifyInstance,
  options: FastifyPluginOptions,
  done: () => void,
): void {
  fastify.addHook('onRequest', (req: FastifyRequest, _reply: FastifyReply, hookDone: () => void) => {
    const span = trace.getActiveSpan();
    if (!span) {
      hookDone();
      return;
    }

    // Resolve route pattern
    const routePattern = req.routeOptions?.url ?? req.url;
    const opName = `${req.method} ${routePattern}`;

    // Topology enrichment
    if (options.topology) {
      const t = options.topology;
      if (t.team) span.setAttribute(attrs.TOPOLOGY_TEAM, t.team);
      if (t.tier) span.setAttribute(attrs.TOPOLOGY_TIER, t.tier);
      if (t.domain) span.setAttribute(attrs.TOPOLOGY_DOMAIN, t.domain);
    }

    // Baseline enrichment
    if (options.baselineProvider) {
      const baseline = options.baselineProvider.getBaseline(opName);
      if (baseline) {
        span.setAttribute(attrs.BASELINE_LATENCY_P50_MS, baseline.latencyP50Ms);
        span.setAttribute(attrs.BASELINE_LATENCY_P99_MS, baseline.latencyP99Ms);
        span.setAttribute(attrs.BASELINE_ERROR_RATE, baseline.errorRate);
        span.setAttribute(attrs.BASELINE_SOURCE, baseline.source);
      }
    }

    hookDone();
  });

  done();
}

// Fastify requires this metadata for plugin registration
// eslint-disable-next-line @typescript-eslint/no-explicit-any
(fastifyPlugin as any)[Symbol.for('skip-override')] = true;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
(fastifyPlugin as any)[Symbol.for('fastify.display-name')] = '@agenttel/node/fastify';
