import { InteractionTracker } from '../../src/trackers/interaction-tracker';
import { SpanFactory } from '../../src/core/span-factory';
import { BatchProcessor } from '../../src/transport/batch-processor';
import { OtlpHttpExporter } from '../../src/transport/otlp-exporter';
import { AgentTelClientAttributes } from '../../src/core/attribute-keys';
import type { InternalSpan } from '../../src/types/span';

describe('InteractionTracker', () => {
  let spanFactory: SpanFactory;
  let processor: BatchProcessor;
  let capturedSpans: InternalSpan[];
  let rageClickCallback: jest.Mock;

  // Use a single document for all tests but isolate via captured spans
  beforeEach(() => {
    jest.useFakeTimers({ doNotFake: ['performance'] });

    spanFactory = new SpanFactory({
      appName: 'test',
      collectorEndpoint: '/otlp',
    });

    const exporter = new OtlpHttpExporter('/otlp');
    jest.spyOn(exporter, 'export').mockResolvedValue(true);
    jest.spyOn(exporter, 'exportBeacon').mockReturnValue(true);
    processor = new BatchProcessor(exporter, 100, 60000);

    // Use a local const so each spy closure captures its own array
    // (prevents old InteractionTracker listeners from leaking into current test)
    const localSpans: InternalSpan[] = [];
    capturedSpans = localSpans;
    jest.spyOn(processor, 'addSpan').mockImplementation((span: InternalSpan) => {
      localSpans.push(span);
    });

    rageClickCallback = jest.fn();

    new InteractionTracker(
      spanFactory,
      processor,
      3,     // rageClickThreshold
      2000,  // rageClickWindowMs
      rageClickCallback,
    );
  });

  afterEach(() => {
    jest.useRealTimers();
    // Clean up any elements added to the body
    document.body.innerHTML = '';
  });

  describe('click tracking', () => {
    it('creates a span for click events', () => {
      const btn = document.createElement('button');
      btn.id = 'submit';
      document.body.appendChild(btn);
      btn.click();

      expect(capturedSpans).toHaveLength(1);
      expect(capturedSpans[0].attributes[AgentTelClientAttributes.INTERACTION_TYPE]).toBe('click');
      expect(capturedSpans[0].attributes[AgentTelClientAttributes.INTERACTION_TARGET]).toBe('#submit');
    });

    it('uses data-agenttel-target for semantic targets', () => {
      const btn = document.createElement('button');
      btn.setAttribute('data-agenttel-target', 'checkout-submit');
      document.body.appendChild(btn);
      btn.click();

      expect(capturedSpans[0].attributes[AgentTelClientAttributes.INTERACTION_TARGET]).toBe('checkout-submit');
    });

    it('uses tag + text for buttons without id', () => {
      const btn = document.createElement('button');
      btn.textContent = 'Click Me';
      document.body.appendChild(btn);
      btn.click();

      expect(capturedSpans[0].attributes[AgentTelClientAttributes.INTERACTION_TARGET]).toBe('button:"Click Me"');
    });
  });

  describe('rage click detection', () => {
    it('detects rage clicks when threshold is exceeded', () => {
      const btn = document.createElement('button');
      btn.id = 'rage-target';
      document.body.appendChild(btn);

      // Click 3 times rapidly (threshold = 3)
      btn.click();
      btn.click();
      btn.click();

      expect(rageClickCallback).toHaveBeenCalledWith('#rage-target');
      const lastSpan = capturedSpans[capturedSpans.length - 1];
      expect(lastSpan.attributes[AgentTelClientAttributes.ANOMALY_DETECTED]).toBe(true);
      expect(lastSpan.attributes[AgentTelClientAttributes.ANOMALY_PATTERN]).toBe('RAGE_CLICK');
    });

    it('does not trigger below threshold', () => {
      const btn = document.createElement('button');
      btn.id = 'normal';
      document.body.appendChild(btn);

      btn.click();
      btn.click();

      expect(rageClickCallback).not.toHaveBeenCalled();
    });
  });

  describe('submit tracking', () => {
    it('creates a span for form submissions', () => {
      const form = document.createElement('form');
      form.setAttribute('name', 'login-form');
      document.body.appendChild(form);

      const event = new Event('submit', { bubbles: true });
      form.dispatchEvent(event);

      // Filter for submit spans only (click listeners from other InteractionTracker
      // instances may also fire, but they use different processor references)
      const submitSpans = capturedSpans.filter(
        s => s.attributes[AgentTelClientAttributes.INTERACTION_TYPE] === 'submit'
      );
      expect(submitSpans).toHaveLength(1);
      expect(submitSpans[0].attributes[AgentTelClientAttributes.INTERACTION_TARGET]).toBe('login-form');
    });
  });
});
