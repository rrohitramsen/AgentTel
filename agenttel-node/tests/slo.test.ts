import { describe, it, expect } from 'vitest';
import { SLOTracker } from '../src/slo/tracker.js';

describe('SLOTracker', () => {
  it('tracks basic availability', () => {
    const tracker = new SLOTracker();
    tracker.register({ name: 'payment-avail', operationName: 'POST /pay', type: 'availability', target: 0.999 });

    for (let i = 0; i < 999; i++) tracker.recordSuccess('POST /pay');
    tracker.recordFailure('POST /pay');

    const status = tracker.getStatus('payment-avail');
    expect(status).toBeDefined();
    expect(status!.totalRequests).toBe(1000);
    expect(status!.failedRequests).toBe(1);
    expect(status!.actual).toBeCloseTo(0.999, 3);
  });

  it('detects budget exhaustion', () => {
    const tracker = new SLOTracker();
    tracker.register({ name: 'api-avail', operationName: 'op', type: 'availability', target: 0.99 });

    for (let i = 0; i < 90; i++) tracker.recordSuccess('op');
    for (let i = 0; i < 10; i++) tracker.recordFailure('op');

    const alerts = tracker.checkAlerts();
    expect(alerts.length).toBeGreaterThan(0);
    expect(alerts[0].severity).toBe('critical');
  });

  it('no alert when healthy', () => {
    const tracker = new SLOTracker();
    tracker.register({ name: 'healthy', operationName: 'op', type: 'availability', target: 0.99 });

    for (let i = 0; i < 1000; i++) tracker.recordSuccess('op');

    const alerts = tracker.checkAlerts();
    expect(alerts).toHaveLength(0);
  });

  it('empty SLO returns 1.0 actual', () => {
    const tracker = new SLOTracker();
    tracker.register({ name: 'empty', type: 'availability', target: 0.99 });

    const status = tracker.getStatus('empty');
    expect(status).toBeDefined();
    expect(status!.actual).toBe(1.0);
    expect(status!.budgetRemaining).toBe(1.0);
  });

  it('getStatuses returns all SLOs', () => {
    const tracker = new SLOTracker();
    tracker.register({ name: 'slo-1', operationName: 'op1', type: 'availability', target: 0.99 });
    tracker.register({ name: 'slo-2', operationName: 'op2', type: 'availability', target: 0.999 });

    const statuses = tracker.getStatuses();
    expect(statuses).toHaveLength(2);
  });
});
