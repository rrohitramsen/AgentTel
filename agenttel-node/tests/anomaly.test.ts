import { describe, it, expect } from 'vitest';
import { AnomalyDetector } from '../src/anomaly/detector.js';
import { PatternMatcher } from '../src/anomaly/patterns.js';
import { IncidentPattern } from '../src/enums-impl.js';
import type { RollingSnapshot } from '../src/interfaces.js';

const emptySnapshot: RollingSnapshot = { mean: 0, stddev: 0, p50: 0, p95: 0, p99: 0, errorRate: 0, sampleCount: 0, ageMs: 0 };

describe('AnomalyDetector', () => {
  it('normal value is not anomalous', () => {
    const d = new AnomalyDetector(3.0);
    const result = d.evaluate('latency', 100, 100, 10);
    expect(result.isAnomaly).toBe(false);
    expect(result.anomalyScore).toBe(0);
  });

  it('detects anomalous value', () => {
    const d = new AnomalyDetector(3.0);
    const result = d.evaluate('latency', 200, 100, 10);
    expect(result.isAnomaly).toBe(true);
    expect(result.anomalyScore).toBeGreaterThan(0);
    expect(result.zScore).toBeCloseTo(10, 1);
  });

  it('handles zero stddev', () => {
    const d = new AnomalyDetector(3.0);
    const result = d.evaluate('latency', 200, 100, 0);
    expect(result.isAnomaly).toBe(false);
  });

  it('detects negative z-score', () => {
    const d = new AnomalyDetector(3.0);
    const result = d.evaluate('latency', 10, 100, 10);
    expect(result.isAnomaly).toBe(true);
    expect(result.zScore).toBeLessThan(0);
  });
});

describe('PatternMatcher', () => {
  it('detects cascade failure', () => {
    const pm = new PatternMatcher(2.0, 0.1, 3);
    pm.recordDependencyError('svc-a');
    pm.recordDependencyError('svc-b');
    pm.recordDependencyError('svc-c');

    const patterns = pm.detectPatterns('op1', 100, false, emptySnapshot);
    expect(patterns).toContain(IncidentPattern.CASCADE_FAILURE);
  });

  it('detects error rate spike', () => {
    const pm = new PatternMatcher(2.0, 0.1, 3);
    const snap: RollingSnapshot = { ...emptySnapshot, mean: 100, stddev: 10, errorRate: 0.15, sampleCount: 100 };
    const patterns = pm.detectPatterns('op1', 100, false, snap);
    expect(patterns).toContain(IncidentPattern.ERROR_RATE_SPIKE);
  });

  it('detects latency degradation', () => {
    const pm = new PatternMatcher(2.0, 0.1, 3);
    const snap: RollingSnapshot = { ...emptySnapshot, mean: 100, stddev: 10, sampleCount: 100 };
    const patterns = pm.detectPatterns('op1', 250, false, snap);
    expect(patterns).toContain(IncidentPattern.LATENCY_DEGRADATION);
  });

  it('reset clears dependency errors', () => {
    const pm = new PatternMatcher(2.0, 0.1, 3);
    pm.recordDependencyError('svc-a');
    pm.recordDependencyError('svc-b');
    pm.recordDependencyError('svc-c');
    pm.resetDependencyErrors();

    const patterns = pm.detectPatterns('op1', 100, false, emptySnapshot);
    expect(patterns).not.toContain(IncidentPattern.CASCADE_FAILURE);
  });
});
