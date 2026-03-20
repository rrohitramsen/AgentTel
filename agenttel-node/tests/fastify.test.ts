import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fastifyPlugin } from '../src/middleware/fastify.js';
import { TopologyRegistry } from '../src/topology/registry.js';
import { StaticBaselineProvider } from '../src/baseline/provider.js';
import * as attrs from '../src/attributes.js';
import * as otelApi from '@opentelemetry/api';

describe('Fastify Plugin', () => {
  let mockSpan: {
    setAttribute: ReturnType<typeof vi.fn>;
    _attributes: Map<string, unknown>;
  };
  let capturedHook: Function | null;

  beforeEach(() => {
    mockSpan = {
      setAttribute: vi.fn(),
      _attributes: new Map(),
    };
    capturedHook = null;
    vi.spyOn(otelApi.trace, 'getActiveSpan').mockReturnValue(mockSpan as any);
  });

  function createMockFastify() {
    return {
      addHook: vi.fn((hookName: string, handler: Function) => {
        if (hookName === 'onRequest') {
          capturedHook = handler;
        }
      }),
    };
  }

  it('registers onRequest hook during plugin registration', () => {
    const fastify = createMockFastify();
    const done = vi.fn();

    fastifyPlugin(fastify as any, {}, done);

    expect(fastify.addHook).toHaveBeenCalledWith('onRequest', expect.any(Function));
    expect(done).toHaveBeenCalled();
  });

  it('enriches span with topology in onRequest hook', () => {
    const topology = new TopologyRegistry();
    topology.team = 'api-team';
    topology.tier = 'standard';
    topology.domain = 'backend';

    const fastify = createMockFastify();
    const done = vi.fn();

    fastifyPlugin(fastify as any, { topology }, done);

    // Simulate a request through the captured hook
    const req = { method: 'GET', url: '/api/users', routeOptions: { url: '/api/users/:id' } } as any;
    const reply = { statusCode: 200 } as any;
    const hookDone = vi.fn();

    capturedHook!(req, reply, hookDone);

    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.TOPOLOGY_TEAM, 'api-team');
    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.TOPOLOGY_TIER, 'standard');
    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.TOPOLOGY_DOMAIN, 'backend');
    expect(hookDone).toHaveBeenCalled();
  });

  it('resolves route from routeOptions.url', () => {
    const baselineProvider = new StaticBaselineProvider({
      'GET /api/users/:id': {
        operationName: 'GET /api/users/:id',
        latencyP50Ms: 30,
        latencyP99Ms: 150,
        errorRate: 0.005,
        source: 'static',
        updatedAt: new Date(),
      },
    });

    const fastify = createMockFastify();
    const done = vi.fn();

    fastifyPlugin(fastify as any, { baselineProvider }, done);

    const req = { method: 'GET', url: '/api/users/42', routeOptions: { url: '/api/users/:id' } } as any;
    const reply = { statusCode: 200 } as any;
    const hookDone = vi.fn();

    capturedHook!(req, reply, hookDone);

    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_LATENCY_P50_MS, 30);
  });

  it('enriches span with baseline data', () => {
    const baselineProvider = new StaticBaselineProvider({
      'POST /api/orders': {
        operationName: 'POST /api/orders',
        latencyP50Ms: 100,
        latencyP99Ms: 500,
        errorRate: 0.03,
        source: 'rolling_7d',
        updatedAt: new Date(),
      },
    });

    const fastify = createMockFastify();
    const done = vi.fn();

    fastifyPlugin(fastify as any, { baselineProvider }, done);

    const req = { method: 'POST', url: '/api/orders', routeOptions: { url: '/api/orders' } } as any;
    const reply = { statusCode: 200 } as any;
    const hookDone = vi.fn();

    capturedHook!(req, reply, hookDone);

    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_LATENCY_P50_MS, 100);
    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_LATENCY_P99_MS, 500);
    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_ERROR_RATE, 0.03);
    expect(mockSpan.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_SOURCE, 'rolling_7d');
  });
});
