import { SpanFactory } from '../../src/core/span-factory';
import { AgentTelClientAttributes } from '../../src/core/attribute-keys';
import type { AgentTelWebConfig } from '../../src/config/types';

const baseConfig: AgentTelWebConfig = {
  appName: 'test-app',
  appVersion: '1.0.0',
  collectorEndpoint: 'http://localhost:4318',
  environment: 'test',
  team: 'platform',
  routes: {
    '/checkout/:step': {
      businessCriticality: 'revenue',
      baseline: { pageLoadP50Ms: 800, pageLoadP99Ms: 3000 },
      decision: { retryOnFailure: true, escalationLevel: 'page_oncall' },
    },
    '/products': {
      businessCriticality: 'engagement',
    },
  },
};

describe('SpanFactory', () => {
  let factory: SpanFactory;

  beforeEach(() => {
    factory = new SpanFactory(baseConfig);
  });

  describe('newPageTrace', () => {
    it('generates a new trace ID', () => {
      const first = factory.getCurrentTraceId();
      const second = factory.newPageTrace();
      expect(second).not.toBe(first);
      expect(second).toHaveLength(32);
    });
  });

  describe('createPageLoadSpan', () => {
    it('creates a span with correct name and attributes', () => {
      const span = factory.createPageLoadSpan('/products', 'Products', 1200);
      expect(span.name).toBe('page_load /products');
      expect(span.kind).toBe('INTERNAL');
      expect(span.attributes[AgentTelClientAttributes.PAGE_ROUTE]).toBe('/products');
      expect(span.attributes[AgentTelClientAttributes.PAGE_TITLE]).toBe('Products');
    });

    it('enriches with route config when matched', () => {
      const span = factory.createPageLoadSpan('/checkout/payment', 'Checkout', 500);
      expect(span.attributes[AgentTelClientAttributes.PAGE_BUSINESS_CRITICALITY]).toBe('revenue');
      expect(span.attributes[AgentTelClientAttributes.BASELINE_PAGE_LOAD_P50_MS]).toBe(800);
      expect(span.attributes[AgentTelClientAttributes.BASELINE_PAGE_LOAD_P99_MS]).toBe(3000);
      expect(span.attributes[AgentTelClientAttributes.DECISION_RETRY_ON_FAILURE]).toBe(true);
      expect(span.attributes[AgentTelClientAttributes.DECISION_ESCALATION_LEVEL]).toBe('page_oncall');
      expect(span.attributes[AgentTelClientAttributes.BASELINE_SOURCE]).toBe('static');
    });

    it('includes resource attributes', () => {
      const span = factory.createPageLoadSpan('/', 'Home', 300);
      expect(span.attributes['service.name']).toBe('test-app');
      expect(span.attributes[AgentTelClientAttributes.APP_NAME]).toBe('test-app');
      expect(span.attributes[AgentTelClientAttributes.TOPOLOGY_TEAM]).toBe('platform');
    });
  });

  describe('createNavigationSpan', () => {
    it('creates a span with from/to routes', () => {
      const span = factory.createNavigationSpan('/products', '/checkout/shipping', 150);
      expect(span.name).toBe('navigate /checkout/shipping');
      expect(span.attributes['navigation.from']).toBe('/products');
      expect(span.attributes['navigation.to']).toBe('/checkout/shipping');
    });
  });

  describe('createApiSpan', () => {
    it('creates a span with HTTP attributes', () => {
      const span = factory.createApiSpan({
        url: 'http://localhost:8080/api/orders',
        method: 'POST',
        status: 201,
        durationMs: 250,
      });
      expect(span.name).toBe('fetch POST /api/orders');
      expect(span.kind).toBe('CLIENT');
      expect(span.attributes['http.method']).toBe('POST');
      expect(span.attributes['http.status_code']).toBe(201);
      expect(span.attributes[AgentTelClientAttributes.INTERACTION_OUTCOME]).toBe('success');
    });

    it('sets error status for 4xx/5xx', () => {
      const span = factory.createApiSpan({
        url: 'http://localhost:8080/api/orders',
        method: 'GET',
        status: 500,
        durationMs: 100,
      });
      expect(span.status.code).toBe('ERROR');
      expect(span.attributes[AgentTelClientAttributes.INTERACTION_OUTCOME]).toBe('error');
    });

    it('includes backend correlation when provided', () => {
      const span = factory.createApiSpan({
        url: 'http://localhost:8080/api/orders',
        method: 'GET',
        status: 200,
        durationMs: 100,
        backendTraceId: 'abc123def456',
      });
      expect(span.attributes[AgentTelClientAttributes.CORRELATION_BACKEND_TRACE_ID]).toBe('abc123def456');
    });

    it('sets parentSpanId when provided', () => {
      const span = factory.createApiSpan({
        url: 'http://localhost:8080/api/test',
        method: 'GET',
        status: 200,
        durationMs: 50,
        parentSpanId: 'parent123',
      });
      expect(span.parentSpanId).toBe('parent123');
    });
  });

  describe('createInteractionSpan', () => {
    it('creates a span with interaction attributes', () => {
      const span = factory.createInteractionSpan({
        type: 'click',
        target: '#submit-btn',
        outcome: 'success',
        durationMs: 50,
      });
      expect(span.name).toBe('click #submit-btn');
      expect(span.attributes[AgentTelClientAttributes.INTERACTION_TYPE]).toBe('click');
      expect(span.attributes[AgentTelClientAttributes.INTERACTION_TARGET]).toBe('#submit-btn');
      expect(span.attributes[AgentTelClientAttributes.INTERACTION_RESPONSE_TIME_MS]).toBe(50);
    });

    it('includes custom metadata', () => {
      const span = factory.createInteractionSpan({
        type: 'custom',
        target: 'widget',
        outcome: 'success',
        metadata: { 'custom.key': 'custom-value' },
      });
      expect(span.attributes['custom.key']).toBe('custom-value');
    });
  });

  describe('createErrorSpan', () => {
    it('creates a span with error attributes', () => {
      const span = factory.createErrorSpan('TypeError: null is not an object', 'onerror', '/checkout/payment');
      expect(span.name).toBe('error');
      expect(span.status.code).toBe('ERROR');
      expect(span.attributes['exception.message']).toBe('TypeError: null is not an object');
      expect(span.attributes['exception.source']).toBe('onerror');
      expect(span.attributes[AgentTelClientAttributes.PAGE_ROUTE]).toBe('/checkout/payment');
    });
  });

  describe('addAnomalyAttributes', () => {
    it('adds anomaly attributes and event to span', () => {
      const span = factory.createInteractionSpan({ type: 'click', target: 'btn', outcome: 'success' });
      factory.addAnomalyAttributes(span, 'RAGE_CLICK', 0.8);

      expect(span.attributes[AgentTelClientAttributes.ANOMALY_DETECTED]).toBe(true);
      expect(span.attributes[AgentTelClientAttributes.ANOMALY_PATTERN]).toBe('RAGE_CLICK');
      expect(span.attributes[AgentTelClientAttributes.ANOMALY_SCORE]).toBe(0.8);
      expect(span.events).toHaveLength(1);
      expect(span.events[0].name).toBe('anomaly.detected');
    });
  });

  describe('addJourneyAttributes', () => {
    it('adds journey tracking attributes', () => {
      const span = factory.createInteractionSpan({ type: 'journey_step', target: 'checkout', outcome: 'success' });
      factory.addJourneyAttributes(span, 'checkout', 2, 4, '2024-01-01T00:00:00Z');

      expect(span.attributes[AgentTelClientAttributes.JOURNEY_NAME]).toBe('checkout');
      expect(span.attributes[AgentTelClientAttributes.JOURNEY_STEP]).toBe(2);
      expect(span.attributes[AgentTelClientAttributes.JOURNEY_TOTAL_STEPS]).toBe(4);
      expect(span.attributes[AgentTelClientAttributes.JOURNEY_STARTED_AT]).toBe('2024-01-01T00:00:00Z');
    });
  });
});
