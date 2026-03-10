import { JourneyTracker } from '../../src/enrichment/journey-tracker';
import { SpanFactory } from '../../src/core/span-factory';
import { BatchProcessor } from '../../src/transport/batch-processor';
import { OtlpHttpExporter } from '../../src/transport/otlp-exporter';
import { AgentTelClientAttributes } from '../../src/core/attribute-keys';
import type { InternalSpan } from '../../src/types/span';

describe('JourneyTracker', () => {
  let tracker: JourneyTracker;
  let spanFactory: SpanFactory;
  let processor: BatchProcessor;
  let addedSpans: InternalSpan[];

  beforeEach(() => {
    jest.useFakeTimers({ doNotFake: ['performance'] });

    spanFactory = new SpanFactory({
      appName: 'test',
      collectorEndpoint: '/otlp',
    });

    const exporter = new OtlpHttpExporter('/otlp');
    processor = new BatchProcessor(exporter, 100, 60000);

    // Capture spans added to processor
    addedSpans = [];
    const originalAddSpan = processor.addSpan.bind(processor);
    processor.addSpan = (span: InternalSpan) => {
      addedSpans.push(span);
      originalAddSpan(span);
    };

    tracker = new JourneyTracker(spanFactory, processor, {
      checkout: {
        steps: ['/cart', '/checkout/:step', '/checkout/confirm', '/order/:id'],
      },
    });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  describe('startJourney', () => {
    it('starts tracking a named journey', () => {
      tracker.startJourney('checkout');
      const active = tracker.getActiveJourneys();
      expect(active).toHaveLength(1);
      expect(active[0].name).toBe('checkout');
      expect(active[0].currentStep).toBe(0);
      expect(active[0].completed).toBe(false);
      expect(active[0].abandoned).toBe(false);
    });

    it('ignores unknown journey names', () => {
      tracker.startJourney('nonexistent');
      expect(tracker.getActiveJourneys()).toHaveLength(0);
    });
  });

  describe('advanceJourney', () => {
    it('advances the step counter and emits a span', () => {
      tracker.startJourney('checkout');
      tracker.advanceJourney('checkout');

      const active = tracker.getActiveJourneys();
      expect(active[0].currentStep).toBe(1);

      expect(addedSpans).toHaveLength(1);
      expect(addedSpans[0].attributes[AgentTelClientAttributes.JOURNEY_NAME]).toBe('checkout');
      expect(addedSpans[0].attributes[AgentTelClientAttributes.JOURNEY_STEP]).toBe(1);
    });

    it('does nothing for unknown journey', () => {
      tracker.advanceJourney('nonexistent');
      expect(addedSpans).toHaveLength(0);
    });
  });

  describe('completeJourney', () => {
    it('marks journey as completed and emits span', () => {
      tracker.startJourney('checkout');
      tracker.completeJourney('checkout');

      expect(tracker.getActiveJourneys()).toHaveLength(0);
      expect(addedSpans).toHaveLength(1);
      const span = addedSpans[0];
      expect(span.attributes[AgentTelClientAttributes.INTERACTION_TYPE]).toBe('journey_complete');
    });
  });

  describe('abandonJourney', () => {
    it('marks journey as abandoned with anomaly attributes', () => {
      tracker.startJourney('checkout');
      tracker.abandonJourney('checkout');

      expect(tracker.getActiveJourneys()).toHaveLength(0);
      expect(addedSpans).toHaveLength(1);
      const span = addedSpans[0];
      expect(span.attributes[AgentTelClientAttributes.INTERACTION_TYPE]).toBe('journey_abandon');
      expect(span.attributes[AgentTelClientAttributes.ANOMALY_DETECTED]).toBe(true);
      expect(span.attributes[AgentTelClientAttributes.ANOMALY_PATTERN]).toBe('FUNNEL_DROP_OFF');
    });
  });

  describe('onNavigate (auto-detection)', () => {
    it('auto-starts journey when first step matches', () => {
      tracker.onNavigate('/cart');
      expect(tracker.getActiveJourneys()).toHaveLength(1);
      expect(tracker.getActiveJourneys()[0].name).toBe('checkout');
    });

    it('auto-advances journey when next step matches', () => {
      tracker.onNavigate('/cart');
      tracker.onNavigate('/checkout/shipping');

      const active = tracker.getActiveJourneys();
      expect(active[0].currentStep).toBe(1);
    });
  });
});
