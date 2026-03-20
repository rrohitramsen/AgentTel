import { trace } from '@opentelemetry/api';
import type { BaselineProvider } from '../baseline/provider.js';
import type { TopologyRegistry } from '../topology/registry.js';
import * as attrs from '../attributes.js';

export interface ExpressMiddlewareOptions {
  baselineProvider?: BaselineProvider;
  topology?: TopologyRegistry;
  routeResolver?: (req: ExpressRequest) => string;
}

interface ExpressRequest {
  method: string;
  route?: { path: string };
  originalUrl: string;
  path: string;
}

interface ExpressResponse {
  statusCode: number;
}

type NextFunction = (err?: unknown) => void;

/**
 * Express middleware that enriches the current OTel span with AgentTel attributes.
 *
 * Usage:
 * ```ts
 * import { expressMiddleware } from '@agenttel/node';
 * app.use(expressMiddleware({ topology, baselineProvider }));
 * ```
 */
export function expressMiddleware(options: ExpressMiddlewareOptions = {}) {
  return (req: ExpressRequest, _res: ExpressResponse, next: NextFunction): void => {
    const span = trace.getActiveSpan();
    if (!span) {
      next();
      return;
    }

    // Resolve operation name
    const routePattern = options.routeResolver
      ? options.routeResolver(req)
      : req.route?.path ?? req.path;
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

    next();
  };
}
