import { AgentTelWeb } from '../../src/core/agenttel-web';
import { AgentTelClientAttributes } from '../../src/core/attribute-keys';
import type { AgentTelWebConfig } from '../../src/config/types';

describe('AgentTelWeb', () => {
  let fetchMock: jest.Mock;
  let originalFetch: typeof globalThis.fetch;

  const config: AgentTelWebConfig = {
    appName: 'test-app',
    appVersion: '1.0.0',
    collectorEndpoint: 'http://localhost:4318',
    environment: 'test',
    journeys: {
      checkout: {
        steps: ['/cart', '/checkout/:step', '/order/:id'],
      },
    },
  };

  beforeEach(() => {
    AgentTelWeb._reset();
    originalFetch = globalThis.fetch;
    // jsdom doesn't provide fetch/Response; mock directly
    fetchMock = jest.fn().mockResolvedValue({ ok: true, status: 200, headers: { get: () => null } });
    globalThis.fetch = fetchMock;
  });

  afterEach(() => {
    AgentTelWeb._reset();
    globalThis.fetch = originalFetch;
  });

  describe('init', () => {
    it('creates a singleton instance', () => {
      const sdk = AgentTelWeb.init(config);
      expect(sdk).toBeDefined();
      expect(AgentTelWeb.getInstance()).toBe(sdk);
    });

    it('returns existing instance on second init call', () => {
      const first = AgentTelWeb.init(config);
      const second = AgentTelWeb.init({ ...config, appName: 'other' });
      expect(second).toBe(first);
    });

    it('stores the config', () => {
      const sdk = AgentTelWeb.init(config);
      expect(sdk.config.appName).toBe('test-app');
      expect(sdk.config.appVersion).toBe('1.0.0');
    });
  });

  describe('getInstance', () => {
    it('returns null before init', () => {
      expect(AgentTelWeb.getInstance()).toBeNull();
    });

    it('returns the instance after init', () => {
      AgentTelWeb.init(config);
      expect(AgentTelWeb.getInstance()).not.toBeNull();
    });
  });

  describe('_reset', () => {
    it('clears the singleton', () => {
      AgentTelWeb.init(config);
      AgentTelWeb._reset();
      expect(AgentTelWeb.getInstance()).toBeNull();
    });
  });

  describe('shutdown', () => {
    it('clears the singleton instance', () => {
      AgentTelWeb.init(config);
      AgentTelWeb.getInstance()!.shutdown();
      expect(AgentTelWeb.getInstance()).toBeNull();
    });
  });

  describe('trackInteraction', () => {
    it('creates and buffers an interaction span', () => {
      const sdk = AgentTelWeb.init(config);
      const addSpanSpy = jest.spyOn(sdk.processor, 'addSpan');

      sdk.trackInteraction('purchase', {
        target: 'buy-button',
        outcome: 'success',
        durationMs: 150,
      });

      expect(addSpanSpy).toHaveBeenCalledTimes(1);
      const span = addSpanSpy.mock.calls[0][0];
      expect(span.attributes[AgentTelClientAttributes.INTERACTION_TYPE]).toBe('purchase');
      expect(span.attributes[AgentTelClientAttributes.INTERACTION_TARGET]).toBe('buy-button');
      expect(span.attributes[AgentTelClientAttributes.INTERACTION_RESPONSE_TIME_MS]).toBe(150);
    });
  });

  describe('journey management', () => {
    it('startJourney does not throw', () => {
      const sdk = AgentTelWeb.init(config);
      expect(() => sdk.startJourney('checkout')).not.toThrow();
    });

    it('advanceJourney does not throw', () => {
      const sdk = AgentTelWeb.init(config);
      sdk.startJourney('checkout');
      expect(() => sdk.advanceJourney('checkout')).not.toThrow();
    });

    it('completeJourney does not throw', () => {
      const sdk = AgentTelWeb.init(config);
      sdk.startJourney('checkout');
      expect(() => sdk.completeJourney('checkout')).not.toThrow();
    });
  });

  describe('flush', () => {
    it('flushes pending spans', async () => {
      const sdk = AgentTelWeb.init(config);
      const flushSpy = jest.spyOn(sdk.processor, 'flush');

      sdk.trackInteraction('test', { target: 'x' });
      await sdk.flush();

      expect(flushSpy).toHaveBeenCalledTimes(1);
    });
  });

  describe('enrichment engines', () => {
    it('exposes anomalyDetector', () => {
      const sdk = AgentTelWeb.init(config);
      expect(sdk.anomalyDetector).toBeDefined();
    });

    it('exposes correlationEngine', () => {
      const sdk = AgentTelWeb.init(config);
      expect(sdk.correlationEngine).toBeDefined();
    });
  });
});
