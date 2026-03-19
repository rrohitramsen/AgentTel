import { describe, it, expect } from 'vitest';
import { RollingWindow, RollingBaselineProvider } from '../src/baseline/rolling.js';
import { StaticBaselineProvider } from '../src/baseline/provider.js';
import { CompositeBaselineProvider } from '../src/baseline/composite.js';
import type { OperationBaseline } from '../src/interfaces.js';

describe('RollingWindow', () => {
  it('computes basic stats', () => {
    const w = new RollingWindow(100);
    for (let i = 1; i <= 100; i++) w.record(i);

    const snap = w.snapshot();
    expect(snap.sampleCount).toBe(100);
    expect(snap.mean).toBeCloseTo(50.5, 0);
    expect(snap.stddev).toBeGreaterThan(0);
    expect(snap.p50).toBeCloseTo(50.5, 0);
    expect(snap.errorRate).toBe(0);
  });

  it('computes error rate', () => {
    const w = new RollingWindow(10);
    for (let i = 0; i < 7; i++) w.record(100);
    for (let i = 0; i < 3; i++) w.recordError();

    const snap = w.snapshot();
    expect(snap.sampleCount).toBe(10);
    expect(snap.errorRate).toBeCloseTo(0.3, 1);
  });

  it('ring buffer overflow', () => {
    const w = new RollingWindow(5);
    for (let i = 0; i < 10; i++) w.record(i * 10);

    expect(w.size()).toBe(5);
    const snap = w.snapshot();
    expect(snap.sampleCount).toBe(5);
  });

  it('empty snapshot', () => {
    const w = new RollingWindow(10);
    const snap = w.snapshot();
    expect(snap.sampleCount).toBe(0);
    expect(snap.mean).toBe(0);
  });
});

describe('RollingBaselineProvider', () => {
  it('requires minimum samples', () => {
    const p = new RollingBaselineProvider(100, 5);
    p.recordLatency('op1', 100);
    p.recordLatency('op1', 200);

    expect(p.getBaseline('op1')).toBeUndefined();

    for (let i = 0; i < 5; i++) p.recordLatency('op1', 150);
    expect(p.getBaseline('op1')).toBeDefined();
    expect(p.getBaseline('op1')!.source).toBe('rolling_7d');
  });

  it('returns snapshot', () => {
    const p = new RollingBaselineProvider(100, 1);
    p.recordLatency('op1', 50);
    p.recordLatency('op1', 100);

    const snap = p.getSnapshot('op1');
    expect(snap).toBeDefined();
    expect(snap!.sampleCount).toBe(2);
    expect(snap!.mean).toBeCloseTo(75, 0);
  });
});

describe('CompositeBaselineProvider', () => {
  it('returns from first provider with match', () => {
    const staticBaselines: Record<string, OperationBaseline> = {
      op1: { operationName: 'op1', latencyP50Ms: 10, latencyP99Ms: 50, errorRate: 0, source: 'static', updatedAt: new Date() },
    };
    const staticP = new StaticBaselineProvider(staticBaselines);

    const rolling = new RollingBaselineProvider(100, 1);
    rolling.recordLatency('op1', 50);
    rolling.recordLatency('op2', 100);

    const composite = new CompositeBaselineProvider(staticP, rolling);

    // op1 from static
    const b1 = composite.getBaseline('op1');
    expect(b1).toBeDefined();
    expect(b1!.source).toBe('static');

    // op2 from rolling
    const b2 = composite.getBaseline('op2');
    expect(b2).toBeDefined();
    expect(b2!.source).toBe('rolling_7d');

    // op3 not found
    expect(composite.getBaseline('op3')).toBeUndefined();
  });
});
