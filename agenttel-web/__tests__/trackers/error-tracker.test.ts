import { ErrorTracker } from '../../src/trackers/error-tracker';
import { SpanFactory } from '../../src/core/span-factory';
import { BatchProcessor } from '../../src/transport/batch-processor';
import { OtlpHttpExporter } from '../../src/transport/otlp-exporter';
import { AgentTelClientAttributes } from '../../src/core/attribute-keys';
import type { InternalSpan } from '../../src/types/span';

describe('ErrorTracker', () => {
  let spanFactory: SpanFactory;
  let processor: BatchProcessor;
  let addedSpans: InternalSpan[];
  let errorLoopCallback: jest.Mock;
  let tracker: ErrorTracker;

  beforeEach(() => {
    spanFactory = new SpanFactory({
      appName: 'test',
      collectorEndpoint: '/otlp',
    });

    const exporter = new OtlpHttpExporter('/otlp');
    jest.spyOn(exporter, 'export').mockResolvedValue(true);
    jest.spyOn(exporter, 'exportBeacon').mockReturnValue(true);
    processor = new BatchProcessor(exporter, 100, 60000);

    addedSpans = [];
    const originalAddSpan = processor.addSpan.bind(processor);
    processor.addSpan = (span: InternalSpan) => {
      addedSpans.push(span);
      originalAddSpan(span);
    };

    errorLoopCallback = jest.fn();

    tracker = new ErrorTracker(
      spanFactory,
      processor,
      5,      // errorLoopThreshold
      30000,  // errorLoopWindowMs
      () => '/current-route',
      errorLoopCallback,
    );
  });

  describe('error tracking via direct handleError', () => {
    // Access the private handleError method for reliable testing,
    // since jsdom's error event dispatching behaves differently from browsers.
    function triggerError(message: string, source: string = 'onerror'): void {
      // Use bracket notation to access private method for test purposes
      (tracker as any).handleError(message, source);
    }

    it('creates an error span', () => {
      triggerError('TypeError: undefined');

      expect(addedSpans).toHaveLength(1);
      expect(addedSpans[0].attributes['exception.message']).toBe('TypeError: undefined');
      expect(addedSpans[0].attributes['exception.source']).toBe('onerror');
      expect(addedSpans[0].status.code).toBe('ERROR');
    });

    it('includes current route in error spans', () => {
      triggerError('Test error');

      expect(addedSpans[0].attributes[AgentTelClientAttributes.PAGE_ROUTE]).toBe('/current-route');
    });

    it('handles unhandled rejection source', () => {
      triggerError('Async failure', 'unhandledrejection');

      expect(addedSpans).toHaveLength(1);
      expect(addedSpans[0].attributes['exception.message']).toBe('Async failure');
      expect(addedSpans[0].attributes['exception.source']).toBe('unhandledrejection');
    });
  });

  describe('error loop detection', () => {
    function triggerError(message: string): void {
      (tracker as any).handleError(message, 'onerror');
    }

    it('detects error loops when threshold is exceeded', () => {
      for (let i = 0; i < 5; i++) {
        triggerError('Repeated error');
      }

      expect(errorLoopCallback).toHaveBeenCalledWith('Repeated error');
      const lastSpan = addedSpans[addedSpans.length - 1];
      expect(lastSpan.attributes[AgentTelClientAttributes.ANOMALY_DETECTED]).toBe(true);
      expect(lastSpan.attributes[AgentTelClientAttributes.ANOMALY_PATTERN]).toBe('ERROR_LOOP');
    });

    it('does not trigger for different error messages', () => {
      for (let i = 0; i < 5; i++) {
        triggerError(`Error ${i}`);
      }

      expect(errorLoopCallback).not.toHaveBeenCalled();
    });

    it('does not trigger below threshold', () => {
      for (let i = 0; i < 4; i++) {
        triggerError('Same error');
      }

      expect(errorLoopCallback).not.toHaveBeenCalled();
    });

    it('anomaly score is clamped to 1.0', () => {
      for (let i = 0; i < 15; i++) {
        triggerError('Many errors');
      }

      const lastSpan = addedSpans[addedSpans.length - 1];
      expect(lastSpan.attributes[AgentTelClientAttributes.ANOMALY_SCORE]).toBeLessThanOrEqual(1.0);
    });
  });
});
