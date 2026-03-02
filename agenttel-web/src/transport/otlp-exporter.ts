import type { InternalSpan, AttributeValue } from '../types/span';

/**
 * Exports spans to an OTel Collector via OTLP/HTTP JSON.
 * No protobuf dependency — uses JSON for minimal bundle size.
 */
export class OtlpHttpExporter {
  private readonly endpoint: string;

  constructor(endpoint: string) {
    // Ensure endpoint ends without trailing slash
    this.endpoint = endpoint.replace(/\/$/, '');
  }

  /**
   * Exports a batch of spans to the collector.
   */
  async export(spans: InternalSpan[]): Promise<boolean> {
    if (spans.length === 0) return true;

    const payload = this.toOtlpJson(spans);

    try {
      const response = await fetch(`${this.endpoint}/v1/traces`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        keepalive: true, // Ensures delivery even on page unload
      });
      return response.ok;
    } catch {
      // Silently fail — telemetry should never break the app
      return false;
    }
  }

  /**
   * Uses navigator.sendBeacon for reliable delivery on page unload.
   */
  exportBeacon(spans: InternalSpan[]): boolean {
    if (spans.length === 0) return true;

    const payload = this.toOtlpJson(spans);
    const blob = new Blob([JSON.stringify(payload)], { type: 'application/json' });

    try {
      return navigator.sendBeacon(`${this.endpoint}/v1/traces`, blob);
    } catch {
      return false;
    }
  }

  /**
   * Converts internal spans to OTLP JSON format.
   */
  private toOtlpJson(spans: InternalSpan[]): OtlpTracePayload {
    // Group spans by resource (all share the same resource in our case)
    const resourceSpans: OtlpResourceSpans = {
      resource: this.extractResourceAttrs(spans[0]),
      scopeSpans: [
        {
          scope: { name: '@agenttel/web', version: '0.1.0-alpha' },
          spans: spans.map((s) => this.toOtlpSpan(s)),
        },
      ],
    };

    return { resourceSpans: [resourceSpans] };
  }

  private toOtlpSpan(span: InternalSpan): OtlpSpan {
    return {
      traceId: span.traceId,
      spanId: span.spanId,
      parentSpanId: span.parentSpanId ?? '',
      name: span.name,
      kind: span.kind === 'CLIENT' ? 3 : 1, // OTLP: CLIENT=3, INTERNAL=1
      startTimeUnixNano: span.startTimeUnixNano.toString(),
      endTimeUnixNano: span.endTimeUnixNano.toString(),
      attributes: this.toOtlpAttrs(span.attributes),
      events: span.events.map((e) => ({
        name: e.name,
        timeUnixNano: e.timeUnixNano.toString(),
        attributes: this.toOtlpAttrs(e.attributes),
      })),
      status: {
        code: span.status.code === 'ERROR' ? 2 : span.status.code === 'OK' ? 1 : 0,
        message: span.status.message ?? '',
      },
    };
  }

  private toOtlpAttrs(attrs: Record<string, AttributeValue>): OtlpAttribute[] {
    return Object.entries(attrs)
      .filter(([, v]) => v !== undefined && v !== null)
      .map(([key, value]) => {
        if (typeof value === 'string') {
          return { key, value: { stringValue: value } };
        } else if (typeof value === 'number') {
          if (Number.isInteger(value)) {
            return { key, value: { intValue: value.toString() } };
          }
          return { key, value: { doubleValue: value } };
        } else if (typeof value === 'boolean') {
          return { key, value: { boolValue: value } };
        }
        return { key, value: { stringValue: String(value) } };
      });
  }

  private extractResourceAttrs(span: InternalSpan): OtlpResource {
    // Resource attributes are keys starting with 'service.' or 'agenttel.client.app.' or 'agenttel.client.topology.'
    const resourceKeys = new Set([
      'service.name', 'service.version', 'deployment.environment',
    ]);
    const resourcePrefixes = ['agenttel.client.app.', 'agenttel.client.topology.'];

    const resourceAttrs: Record<string, AttributeValue> = {};
    for (const [key, value] of Object.entries(span.attributes)) {
      if (resourceKeys.has(key) || resourcePrefixes.some((p) => key.startsWith(p))) {
        resourceAttrs[key] = value;
      }
    }

    return { attributes: this.toOtlpAttrs(resourceAttrs) };
  }
}

// OTLP JSON types
interface OtlpTracePayload {
  resourceSpans: OtlpResourceSpans[];
}

interface OtlpResourceSpans {
  resource: OtlpResource;
  scopeSpans: OtlpScopeSpans[];
}

interface OtlpResource {
  attributes: OtlpAttribute[];
}

interface OtlpScopeSpans {
  scope: { name: string; version: string };
  spans: OtlpSpan[];
}

interface OtlpSpan {
  traceId: string;
  spanId: string;
  parentSpanId: string;
  name: string;
  kind: number;
  startTimeUnixNano: string;
  endTimeUnixNano: string;
  attributes: OtlpAttribute[];
  events: OtlpSpanEvent[];
  status: { code: number; message: string };
}

interface OtlpSpanEvent {
  name: string;
  timeUnixNano: string;
  attributes: OtlpAttribute[];
}

interface OtlpAttribute {
  key: string;
  value: { stringValue?: string; intValue?: string; doubleValue?: number; boolValue?: boolean };
}
