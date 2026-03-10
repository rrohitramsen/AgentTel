import { OtlpHttpExporter } from '../../src/transport/otlp-exporter';
import type { InternalSpan } from '../../src/types/span';
import { generateTraceId, generateSpanId, nowNano } from '../../src/types/span';

function makeSpan(overrides: Partial<InternalSpan> = {}): InternalSpan {
  return {
    traceId: generateTraceId(),
    spanId: generateSpanId(),
    name: 'test-span',
    kind: 'CLIENT',
    startTimeUnixNano: nowNano(),
    endTimeUnixNano: nowNano(),
    attributes: {
      'service.name': 'test-app',
      'http.method': 'GET',
      'http.status_code': 200,
      'agenttel.client.app.name': 'test-app',
      'custom.flag': true,
      'custom.score': 0.95,
    },
    events: [],
    status: { code: 'OK' },
    ...overrides,
  };
}

describe('OtlpHttpExporter', () => {
  let exporter: OtlpHttpExporter;
  let fetchSpy: jest.Mock;
  let originalFetch: typeof globalThis.fetch;

  beforeEach(() => {
    originalFetch = globalThis.fetch;
    exporter = new OtlpHttpExporter('http://localhost:4318');
    // jsdom doesn't provide Response; use a mock object
    fetchSpy = jest.fn().mockResolvedValue({ ok: true, status: 200 });
    globalThis.fetch = fetchSpy;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  describe('export', () => {
    it('sends POST to /v1/traces', async () => {
      await exporter.export([makeSpan()]);

      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('http://localhost:4318/v1/traces');
      expect(options.method).toBe('POST');
      expect(options.headers['Content-Type']).toBe('application/json');
    });

    it('produces valid OTLP JSON structure', async () => {
      const span = makeSpan({ name: 'fetch GET /api/users', kind: 'CLIENT' });
      await exporter.export([span]);

      const body = JSON.parse(fetchSpy.mock.calls[0][1].body);
      expect(body.resourceSpans).toHaveLength(1);

      const rs = body.resourceSpans[0];
      expect(rs.resource.attributes).toBeInstanceOf(Array);
      expect(rs.scopeSpans).toHaveLength(1);
      expect(rs.scopeSpans[0].scope.name).toBe('@agenttel/web');
      expect(rs.scopeSpans[0].spans).toHaveLength(1);

      const otlpSpan = rs.scopeSpans[0].spans[0];
      expect(otlpSpan.traceId).toBe(span.traceId);
      expect(otlpSpan.spanId).toBe(span.spanId);
      expect(otlpSpan.name).toBe('fetch GET /api/users');
    });

    it('maps span kind correctly (CLIENT=3, INTERNAL=1)', async () => {
      await exporter.export([makeSpan({ kind: 'CLIENT' })]);
      let body = JSON.parse(fetchSpy.mock.calls[0][1].body);
      expect(body.resourceSpans[0].scopeSpans[0].spans[0].kind).toBe(3);

      fetchSpy.mockClear();
      await exporter.export([makeSpan({ kind: 'INTERNAL' })]);
      body = JSON.parse(fetchSpy.mock.calls[0][1].body);
      expect(body.resourceSpans[0].scopeSpans[0].spans[0].kind).toBe(1);
    });

    it('serializes string, int, double, and boolean attributes', async () => {
      await exporter.export([makeSpan()]);
      const body = JSON.parse(fetchSpy.mock.calls[0][1].body);
      const attrs = body.resourceSpans[0].scopeSpans[0].spans[0].attributes;

      const stringAttr = attrs.find((a: any) => a.key === 'http.method');
      expect(stringAttr.value.stringValue).toBe('GET');

      const intAttr = attrs.find((a: any) => a.key === 'http.status_code');
      expect(intAttr.value.intValue).toBe('200');

      const boolAttr = attrs.find((a: any) => a.key === 'custom.flag');
      expect(boolAttr.value.boolValue).toBe(true);

      const doubleAttr = attrs.find((a: any) => a.key === 'custom.score');
      expect(doubleAttr.value.doubleValue).toBe(0.95);
    });

    it('returns true on success', async () => {
      const result = await exporter.export([makeSpan()]);
      expect(result).toBe(true);
    });

    it('returns false on HTTP error', async () => {
      (fetchSpy as jest.Mock).mockResolvedValue({ ok: false, status: 500 });
      const result = await exporter.export([makeSpan()]);
      expect(result).toBe(false);
    });

    it('returns false on network error without throwing', async () => {
      fetchSpy.mockRejectedValue(new Error('Network error'));
      const result = await exporter.export([makeSpan()]);
      expect(result).toBe(false);
    });

    it('returns true for empty spans array', async () => {
      const result = await exporter.export([]);
      expect(result).toBe(true);
      expect(fetchSpy).not.toHaveBeenCalled();
    });
  });

  describe('exportBeacon', () => {
    it('uses navigator.sendBeacon', () => {
      const beaconSpy = jest.spyOn(navigator, 'sendBeacon').mockReturnValue(true);
      const result = exporter.exportBeacon([makeSpan()]);

      expect(result).toBe(true);
      expect(beaconSpy).toHaveBeenCalledTimes(1);
      const [url] = beaconSpy.mock.calls[0];
      expect(url).toBe('http://localhost:4318/v1/traces');

      beaconSpy.mockRestore();
    });

    it('returns true for empty spans', () => {
      const result = exporter.exportBeacon([]);
      expect(result).toBe(true);
    });
  });
});
