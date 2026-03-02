import type { SpanFactory } from '../core/span-factory';
import type { BatchProcessor } from '../transport/batch-processor';
import { generateSpanId } from '../types/span';

/**
 * Intercepts fetch() and XMLHttpRequest to:
 * 1. Inject W3C Trace Context (traceparent) on outgoing requests
 * 2. Extract traceparent from response headers for backend correlation
 * 3. Create enriched API call spans
 *
 * This is the critical cross-stack correlation link.
 */
export class ApiTracker {
  private readonly spanFactory: SpanFactory;
  private readonly processor: BatchProcessor;
  private readonly onApiError: (timestamp: number) => void;

  constructor(
    spanFactory: SpanFactory,
    processor: BatchProcessor,
    onApiError: (timestamp: number) => void,
  ) {
    this.spanFactory = spanFactory;
    this.processor = processor;
    this.onApiError = onApiError;

    this.patchFetch();
    this.patchXhr();
  }

  private patchFetch(): void {
    if (typeof window === 'undefined' || !window.fetch) return;

    const originalFetch = window.fetch.bind(window);
    const self = this;

    window.fetch = async function (input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;

      // Skip OTLP export requests to avoid infinite loop
      if (url.includes('/v1/traces')) {
        return originalFetch(input, init);
      }

      // Generate trace context for this request
      const traceId = self.spanFactory.getCurrentTraceId();
      const spanId = generateSpanId();
      const traceparent = `00-${traceId}-${spanId}-01`;

      // Inject traceparent header
      const headers = new Headers(init?.headers);
      headers.set('traceparent', traceparent);

      const startTime = performance.now();
      let response: Response;

      try {
        response = await originalFetch(input, { ...init, headers });
      } catch (error) {
        const durationMs = performance.now() - startTime;
        const span = self.spanFactory.createApiSpan({
          url,
          method: init?.method ?? 'GET',
          status: 0,
          durationMs,
          parentSpanId: spanId,
        });
        span.status = { code: 'ERROR', message: String(error) };
        self.processor.addSpan(span);
        self.onApiError(Date.now());
        throw error;
      }

      const durationMs = performance.now() - startTime;

      // Extract backend trace from response (W3C Trace Context)
      let backendTraceId: string | undefined;
      const responseTraceparent = response.headers.get('traceparent');
      if (responseTraceparent) {
        const parts = responseTraceparent.split('-');
        if (parts.length >= 3) {
          backendTraceId = parts[1];
        }
      }

      const span = self.spanFactory.createApiSpan({
        url,
        method: init?.method ?? 'GET',
        status: response.status,
        durationMs,
        backendTraceId,
        parentSpanId: spanId,
      });

      self.processor.addSpan(span);

      if (response.status >= 400) {
        self.onApiError(Date.now());
      }

      return response;
    };
  }

  private patchXhr(): void {
    if (typeof window === 'undefined' || !window.XMLHttpRequest) return;

    const OriginalXHR = window.XMLHttpRequest;
    const self = this;

    // Store reference for XHR patching
    const originalOpen = OriginalXHR.prototype.open;
    const originalSend = OriginalXHR.prototype.send;

    OriginalXHR.prototype.open = function (method: string, url: string | URL, async?: boolean, username?: string | null, password?: string | null) {
      (this as XMLHttpRequestWithMeta).__agenttel = { method, url: String(url) };
      return originalOpen.call(this, method, url, async ?? true, username ?? null, password ?? null);
    };

    OriginalXHR.prototype.send = function (body?: Document | XMLHttpRequestBodyInit | null) {
      const meta = (this as XMLHttpRequestWithMeta).__agenttel;
      if (!meta || meta.url.includes('/v1/traces')) {
        return originalSend.call(this, body);
      }

      const traceId = self.spanFactory.getCurrentTraceId();
      const spanId = generateSpanId();
      this.setRequestHeader('traceparent', `00-${traceId}-${spanId}-01`);

      const startTime = performance.now();

      this.addEventListener('loadend', () => {
        const durationMs = performance.now() - startTime;

        let backendTraceId: string | undefined;
        try {
          const responseTraceparent = this.getResponseHeader('traceparent');
          if (responseTraceparent) {
            const parts = responseTraceparent.split('-');
            if (parts.length >= 3) backendTraceId = parts[1];
          }
        } catch {
          // CORS may prevent reading headers
        }

        const span = self.spanFactory.createApiSpan({
          url: meta.url,
          method: meta.method,
          status: this.status,
          durationMs,
          backendTraceId,
          parentSpanId: spanId,
        });

        self.processor.addSpan(span);

        if (this.status >= 400 || this.status === 0) {
          self.onApiError(Date.now());
        }
      });

      return originalSend.call(this, body);
    };
  }
}

interface XMLHttpRequestWithMeta extends XMLHttpRequest {
  __agenttel?: { method: string; url: string };
}
