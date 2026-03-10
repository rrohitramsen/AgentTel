import { ApiTracker } from '../../src/trackers/api-tracker';
import { SpanFactory } from '../../src/core/span-factory';
import { BatchProcessor } from '../../src/transport/batch-processor';
import { OtlpHttpExporter } from '../../src/transport/otlp-exporter';
import { AgentTelClientAttributes } from '../../src/core/attribute-keys';
import type { InternalSpan } from '../../src/types/span';

/**
 * Minimal mock for the Response object since jsdom doesn't provide the Fetch API Response class.
 */
function mockResponse(status: number, headers: Record<string, string> = {}): any {
  const h = new Map(Object.entries(headers));
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (key: string) => h.get(key) ?? null,
    },
  };
}

describe('ApiTracker', () => {
  let spanFactory: SpanFactory;
  let processor: BatchProcessor;
  let addedSpans: InternalSpan[];
  let onApiError: jest.Mock;
  let originalFetch: typeof fetch;

  beforeEach(() => {
    originalFetch = window.fetch;

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

    onApiError = jest.fn();
  });

  afterEach(() => {
    window.fetch = originalFetch;
  });

  it('injects traceparent header on outgoing requests', async () => {
    let capturedHeaders: Headers | undefined;

    window.fetch = jest.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      capturedHeaders = new Headers(init?.headers);
      return mockResponse(200);
    }) as any;

    new ApiTracker(spanFactory, processor, onApiError);

    await window.fetch('http://localhost:8080/api/users');

    expect(capturedHeaders?.get('traceparent')).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);
  });

  it('creates API span with correct attributes', async () => {
    window.fetch = jest.fn().mockResolvedValue(mockResponse(200));

    new ApiTracker(spanFactory, processor, onApiError);

    await window.fetch('http://localhost:8080/api/orders', { method: 'POST' });

    expect(addedSpans).toHaveLength(1);
    expect(addedSpans[0].attributes['http.method']).toBe('POST');
    expect(addedSpans[0].attributes['http.status_code']).toBe(200);
    expect(addedSpans[0].attributes[AgentTelClientAttributes.INTERACTION_OUTCOME]).toBe('success');
  });

  it('calls onApiError for 4xx/5xx responses', async () => {
    window.fetch = jest.fn().mockResolvedValue(mockResponse(503));

    new ApiTracker(spanFactory, processor, onApiError);

    await window.fetch('http://localhost:8080/api/health');

    expect(onApiError).toHaveBeenCalledTimes(1);
  });

  it('skips /v1/traces requests to avoid infinite loop', async () => {
    const underlyingFetch = jest.fn().mockResolvedValue(mockResponse(200));
    window.fetch = underlyingFetch;

    new ApiTracker(spanFactory, processor, onApiError);

    await window.fetch('http://localhost:4318/v1/traces', {
      method: 'POST',
      body: '{}',
    });

    // Should NOT create a span for OTLP export requests
    expect(addedSpans).toHaveLength(0);
  });

  it('extracts backend traceId from response traceparent header', async () => {
    window.fetch = jest.fn().mockResolvedValue(
      mockResponse(200, {
        traceparent: '00-abcdef1234567890abcdef1234567890-1234567890abcdef-01',
      }),
    );

    new ApiTracker(spanFactory, processor, onApiError);

    await window.fetch('http://localhost:8080/api/data');

    expect(addedSpans).toHaveLength(1);
    expect(addedSpans[0].attributes[AgentTelClientAttributes.CORRELATION_BACKEND_TRACE_ID])
      .toBe('abcdef1234567890abcdef1234567890');
  });

  it('handles fetch errors gracefully', async () => {
    window.fetch = jest.fn().mockRejectedValue(new Error('Network error'));

    new ApiTracker(spanFactory, processor, onApiError);

    await expect(
      window.fetch('http://localhost:8080/api/fail'),
    ).rejects.toThrow('Network error');

    expect(addedSpans).toHaveLength(1);
    expect(addedSpans[0].status.code).toBe('ERROR');
    expect(onApiError).toHaveBeenCalledTimes(1);
  });

  it('defaults to GET when no method specified', async () => {
    window.fetch = jest.fn().mockResolvedValue(mockResponse(200));

    new ApiTracker(spanFactory, processor, onApiError);

    await window.fetch('http://localhost:8080/api/items');

    expect(addedSpans[0].attributes['http.method']).toBe('GET');
  });
});
