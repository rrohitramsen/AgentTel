import { describe, it, expect, vi } from 'vitest';
import { AgentTelEventEmitter } from '../src/events/emitter.js';

describe('AgentTelEventEmitter', () => {
  it('emits event to registered handler', () => {
    const emitter = new AgentTelEventEmitter();
    const handler = vi.fn();

    emitter.onEvent(handler);
    emitter.emit('agenttel.anomaly.detected', { operation: 'GET /api/test', score: 0.9 });

    expect(handler).toHaveBeenCalledTimes(1);
    expect(handler).toHaveBeenCalledWith('agenttel.anomaly.detected', {
      operation: 'GET /api/test',
      score: 0.9,
    });
  });

  it('handler exceptions do not crash emitter', () => {
    const emitter = new AgentTelEventEmitter();
    const badHandler = vi.fn(() => {
      throw new Error('handler crashed');
    });
    const goodHandler = vi.fn();

    emitter.onEvent(badHandler);
    emitter.onEvent(goodHandler);

    // Should not throw even though first handler throws
    expect(() =>
      emitter.emit('agenttel.test.event', { key: 'value' }),
    ).not.toThrow();

    // The good handler should still be called
    expect(goodHandler).toHaveBeenCalledTimes(1);
  });

  it('preserves JSON event data', () => {
    const emitter = new AgentTelEventEmitter();
    const receivedData: Array<Record<string, unknown>> = [];

    emitter.onEvent((_name, data) => {
      receivedData.push(data);
    });

    const eventData = {
      operation: 'POST /api/orders',
      anomalyScore: 0.95,
      zScore: 4.2,
      nested: { deep: true },
    };

    emitter.emit('agenttel.anomaly.detected', eventData);

    expect(receivedData).toHaveLength(1);
    expect(receivedData[0]).toEqual(eventData);
    expect(receivedData[0].nested).toEqual({ deep: true });
  });

  it('delivers events to multiple handlers', () => {
    const emitter = new AgentTelEventEmitter();
    const handler1 = vi.fn();
    const handler2 = vi.fn();
    const handler3 = vi.fn();

    emitter.onEvent(handler1);
    emitter.onEvent(handler2);
    emitter.onEvent(handler3);

    emitter.emit('agenttel.slo.budget_alert', { slo: 'availability', remaining: 0.1 });

    expect(handler1).toHaveBeenCalledTimes(1);
    expect(handler2).toHaveBeenCalledTimes(1);
    expect(handler3).toHaveBeenCalledTimes(1);

    // All should receive the same event name
    expect(handler1).toHaveBeenCalledWith('agenttel.slo.budget_alert', expect.any(Object));
    expect(handler2).toHaveBeenCalledWith('agenttel.slo.budget_alert', expect.any(Object));
    expect(handler3).toHaveBeenCalledWith('agenttel.slo.budget_alert', expect.any(Object));
  });
});
