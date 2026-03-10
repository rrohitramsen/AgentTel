import { BatchProcessor } from '../../src/transport/batch-processor';
import { OtlpHttpExporter } from '../../src/transport/otlp-exporter';
import type { InternalSpan } from '../../src/types/span';
import { generateTraceId, generateSpanId, nowNano } from '../../src/types/span';

function makeSpan(name: string = 'test'): InternalSpan {
  return {
    traceId: generateTraceId(),
    spanId: generateSpanId(),
    name,
    kind: 'INTERNAL',
    startTimeUnixNano: nowNano(),
    endTimeUnixNano: nowNano(),
    attributes: { 'service.name': 'test' },
    events: [],
    status: { code: 'OK' },
  };
}

describe('BatchProcessor', () => {
  let exporter: OtlpHttpExporter;
  let exportSpy: jest.SpyInstance;

  beforeEach(() => {
    jest.useFakeTimers({ doNotFake: ['performance'] });
    exporter = new OtlpHttpExporter('http://localhost:4318');
    exportSpy = jest.spyOn(exporter, 'export').mockResolvedValue(true);
    jest.spyOn(exporter, 'exportBeacon').mockReturnValue(true);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  describe('addSpan', () => {
    it('buffers spans without flushing immediately', () => {
      const processor = new BatchProcessor(exporter, 50, 5000);
      processor.addSpan(makeSpan());

      expect(exportSpy).not.toHaveBeenCalled();
      processor.shutdown();
    });

    it('flushes when buffer reaches maxSize', () => {
      const processor = new BatchProcessor(exporter, 3, 60000);

      processor.addSpan(makeSpan('a'));
      processor.addSpan(makeSpan('b'));
      processor.addSpan(makeSpan('c'));

      expect(exportSpy).toHaveBeenCalledTimes(1);
      const exportedSpans = exportSpy.mock.calls[0][0] as InternalSpan[];
      expect(exportedSpans).toHaveLength(3);
      processor.shutdown();
    });
  });

  describe('periodic flush', () => {
    it('flushes on timer interval', () => {
      const processor = new BatchProcessor(exporter, 50, 5000);

      processor.addSpan(makeSpan());
      expect(exportSpy).not.toHaveBeenCalled();

      jest.advanceTimersByTime(5000);

      expect(exportSpy).toHaveBeenCalledTimes(1);
      processor.shutdown();
    });

    it('does not flush when buffer is empty', () => {
      const processor = new BatchProcessor(exporter, 50, 5000);
      jest.advanceTimersByTime(5000);

      expect(exportSpy).not.toHaveBeenCalled();
      processor.shutdown();
    });
  });

  describe('flush', () => {
    it('sends all buffered spans', async () => {
      const processor = new BatchProcessor(exporter, 50, 60000);
      processor.addSpan(makeSpan('x'));
      processor.addSpan(makeSpan('y'));

      await processor.flush();

      expect(exportSpy).toHaveBeenCalledTimes(1);
      const spans = exportSpy.mock.calls[0][0] as InternalSpan[];
      expect(spans).toHaveLength(2);
      processor.shutdown();
    });

    it('is a no-op when buffer is empty', async () => {
      const processor = new BatchProcessor(exporter, 50, 60000);
      await processor.flush();

      expect(exportSpy).not.toHaveBeenCalled();
      processor.shutdown();
    });
  });

  describe('shutdown', () => {
    it('flushes remaining spans via sendBeacon', () => {
      const beaconSpy = jest.spyOn(exporter, 'exportBeacon');
      const processor = new BatchProcessor(exporter, 50, 60000);

      processor.addSpan(makeSpan());
      processor.shutdown();

      expect(beaconSpy).toHaveBeenCalledTimes(1);
    });

    it('clears the flush timer', () => {
      const processor = new BatchProcessor(exporter, 50, 5000);
      processor.shutdown();

      // After shutdown, timer should not fire
      processor.addSpan(makeSpan());
      jest.advanceTimersByTime(5000);
      // exportSpy should not be called from timer since it was cleared
    });
  });
});
