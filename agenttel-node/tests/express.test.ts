import { describe, it, expect, vi, beforeEach } from 'vitest';
import { expressMiddleware } from '../src/middleware/express.js';
import { TopologyRegistry } from '../src/topology/registry.js';
import { StaticBaselineProvider } from '../src/baseline/provider.js';
import * as attrs from '../src/attributes.js';
import * as otelApi from '@opentelemetry/api';

describe('Express Middleware', () => {
  let mockSpan: {
    setAttribute: ReturnType<typeof vi.fn>;
    _attributes: Map<string, unknown>;
  };

  beforeEach(() => {
    mockSpan = {
      setAttribute: vi.fn(),
      _attributes: new Map(),
    };
    // Mock trace.getActiveSpan to return our mock span
    vi.spyOn(otelApi.trace, 'getActiveSpan').mockReturnValue(mockSpan as any);
  });

  it('enriches span with topology attributes', () => {
    const topology = new TopologyRegistry();
    topology.team = 'payments-team';
    topology.tier = 'critical';
    topology.domain = 'fintech';

    const middleware = expressMiddleware({ topology });
    const req = { method: 'GET', path: '/api/payments', originalUrl: '/api/payments' } as any;
    const res = { statusCode: 200 } as any;
    const next = vi.fn();

    middleware(req, res, next);

    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.TOPOLOGY_TEAM, 'payments-team');
    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.TOPOLOGY_TIER, 'critical');
    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.TOPOLOGY_DOMAIN, 'fintech');
  });

  it('resolves route pattern from req.route.path', () => {
    const baselineProvider = new StaticBaselineProvider({
      'GET /api/orders/:id': {
        operationName: 'GET /api/orders/:id',
        latencyP50Ms: 50,
        latencyP99Ms: 200,
        errorRate: 0.01,
        source: 'static',
        updatedAt: new Date(),
      },
    });

    const middleware = expressMiddleware({ baselineProvider });
    const req = {
      method: 'GET',
      route: { path: '/api/orders/:id' },
      path: '/api/orders/123',
      originalUrl: '/api/orders/123',
    } as any;
    const res = { statusCode: 200 } as any;
    const next = vi.fn();

    middleware(req, res, next);

    // Baseline should be looked up by "GET /api/orders/:id"
    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_LATENCY_P50_MS, 50);
  });

  it('enriches span with baseline data', () => {
    const baselineProvider = new StaticBaselineProvider({
      'POST /api/orders': {
        operationName: 'POST /api/orders',
        latencyP50Ms: 80,
        latencyP99Ms: 350,
        errorRate: 0.02,
        source: 'rolling_7d',
        updatedAt: new Date(),
      },
    });

    const middleware = expressMiddleware({ baselineProvider });
    const req = {
      method: 'POST',
      route: { path: '/api/orders' },
      path: '/api/orders',
      originalUrl: '/api/orders',
    } as any;
    const res = { statusCode: 200 } as any;
    const next = vi.fn();

    middleware(req, res, next);

    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_LATENCY_P50_MS, 80);
    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_LATENCY_P99_MS, 350);
    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_ERROR_RATE, 0.02);
    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_SOURCE, 'rolling_7d');
  });

  it('always calls next()', () => {
    const middleware = expressMiddleware({});
    const req = { method: 'GET', path: '/', originalUrl: '/' } as any;
    const res = { statusCode: 200 } as any;
    const next = vi.fn();

    middleware(req, res, next);

    expect(next).toHaveBeenCalledTimes(1);
  });
});
