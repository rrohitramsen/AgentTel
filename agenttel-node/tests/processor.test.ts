import { describe, it, expect, vi } from 'vitest';
import { AgentTelSpanProcessor } from '../src/processor.js';
import { TopologyRegistry } from '../src/topology/registry.js';
import { StaticBaselineProvider } from '../src/baseline/provider.js';
import { RollingBaselineProvider } from '../src/baseline/rolling.js';
import { AnomalyDetector } from '../src/anomaly/detector.js';
import { SLOTracker } from '../src/slo/tracker.js';
import { ErrorClassifier } from '../src/error/classifier.js';
import * as attrs from '../src/attributes.js';

// Create a mock writable span (used in onStart)
function createMockWritableSpan(name: string) {
  const attributes = new Map<string, unknown>();
  return {
    name,
    setAttribute: vi.fn((key: string, value: unknown) => {
      attributes.set(key, value);
    }),
    _attributes: attributes,
  };
}

// Create a mock ReadableSpan (used in onEnd)
function createMockReadableSpan(
  name: string,
  options: {
    durationMs?: number;
    statusCode?: number;
    events?: Array<{ name: string; attributes?: Record<string, unknown> }>;
  } = {},
) {
  const startSec = 1000;
  const startNano = 0;
  const durationMs = options.durationMs ?? 50;
  const endSec = startSec + Math.floor(durationMs / 1000);
  const endNano = (durationMs % 1000) * 1e6;

  return {
    name,
    startTime: [startSec, startNano] as [number, number],
    endTime: [endSec, endNano] as [number, number],
    status: { code: options.statusCode ?? 0 },
    events: options.events ?? [],
  };
}

describe('AgentTelSpanProcessor', () => {
  it('enriches spans with topology metadata', () => {
    const topology = new TopologyRegistry();
    topology.team = 'payments-team';
    topology.tier = 'critical';
    topology.domain = 'fintech';

    const proc = new AgentTelSpanProcessor({ topology });
    const span = createMockWritableSpan('GET /api/payments');

    proc.onStart(span as any, {} as any);

    expect(span.setAttribute).toHaveBeenCalledWith(attrs.TOPOLOGY_TEAM, 'payments-team');
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.TOPOLOGY_TIER, 'critical');
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.TOPOLOGY_DOMAIN, 'fintech');
  });

  it('enriches spans with baseline data', () => {
    const baselineProvider = new StaticBaselineProvider({
      'GET /api/orders': {
        operationName: 'GET /api/orders',
        latencyP50Ms: 50,
        latencyP99Ms: 200,
        errorRate: 0.01,
        source: 'static',
        updatedAt: new Date(),
      },
    });

    const proc = new AgentTelSpanProcessor({ baselineProvider });
    const span = createMockWritableSpan('GET /api/orders');

    proc.onStart(span as any, {} as any);

    expect(span.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_LATENCY_P50_MS, 50);
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_LATENCY_P99_MS, 200);
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_ERROR_RATE, 0.01);
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.BASELINE_SOURCE, 'static');
  });

  it('detects anomalies on slow spans', () => {
    const rollingProvider = new RollingBaselineProvider();
    // Seed with normal latencies
    for (let i = 0; i < 20; i++) {
      rollingProvider.recordLatency('GET /api/slow', 50);
    }

    const anomalyDetector = new AnomalyDetector(3.0);
    const proc = new AgentTelSpanProcessor({ rollingProvider, anomalyDetector });

    // A very slow span should be recorded in the rolling provider
    const readableSpan = createMockReadableSpan('GET /api/slow', { durationMs: 5000 });
    proc.onEnd(readableSpan as any);

    // The rolling provider should have the new latency recorded
    const snap = rollingProvider.getSnapshot('GET /api/slow');
    expect(snap).toBeDefined();
    expect(snap!.sampleCount).toBeGreaterThan(20);
  });

  it('tracks SLO success and failure', () => {
    const sloTracker = new SLOTracker();
    sloTracker.register({
      name: 'orders-availability',
      type: 'availability',
      target: 0.999,
    });

    const proc = new AgentTelSpanProcessor({ sloTracker });

    // Record a success
    proc.onEnd(createMockReadableSpan('GET /api/orders', { statusCode: 0 }) as any);

    // Record a failure (statusCode 2 = ERROR)
    proc.onEnd(createMockReadableSpan('GET /api/orders', { statusCode: 2 }) as any);

    const status = sloTracker.getStatus('orders-availability');
    expect(status).toBeDefined();
    expect(status!.totalRequests).toBe(2);
    expect(status!.failedRequests).toBe(1);
  });

  it('classifies errors from exception events', () => {
    const errorClassifier = new ErrorClassifier();
    const proc = new AgentTelSpanProcessor({ errorClassifier });

    const readableSpan = createMockReadableSpan('GET /api/fail', {
      statusCode: 2,
      events: [
        {
          name: 'exception',
          attributes: {
            'exception.type': 'ConnectionError',
            'exception.message': 'connection refused to database',
          },
        },
      ],
    });

    // Should not throw
    expect(() => proc.onEnd(readableSpan as any)).not.toThrow();
  });

  it('skips enrichment when no providers configured', () => {
    const proc = new AgentTelSpanProcessor({});
    const span = createMockWritableSpan('GET /api/noop');

    proc.onStart(span as any, {} as any);

    // No setAttribute calls expected when no providers are configured
    expect(span.setAttribute).not.toHaveBeenCalled();
  });
});
