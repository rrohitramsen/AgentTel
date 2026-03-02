/**
 * Lightweight internal span model — produces OTLP-compatible JSON.
 * No dependency on @opentelemetry/api to keep bundle small.
 */

export type SpanKind = 'CLIENT' | 'INTERNAL';

export type AttributeValue = string | number | boolean;

export interface InternalSpan {
  traceId: string;
  spanId: string;
  parentSpanId?: string;
  name: string;
  kind: SpanKind;
  startTimeUnixNano: bigint;
  endTimeUnixNano: bigint;
  attributes: Record<string, AttributeValue>;
  events: SpanEvent[];
  status: SpanStatus;
}

export interface SpanEvent {
  name: string;
  timeUnixNano: bigint;
  attributes: Record<string, AttributeValue>;
}

export interface SpanStatus {
  code: 'UNSET' | 'OK' | 'ERROR';
  message?: string;
}

/**
 * Generate a random 32-char hex trace ID.
 */
export function generateTraceId(): string {
  return generateHexId(32);
}

/**
 * Generate a random 16-char hex span ID.
 */
export function generateSpanId(): string {
  return generateHexId(16);
}

function generateHexId(length: number): string {
  const bytes = new Uint8Array(length / 2);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Current time in nanoseconds (bigint).
 */
export function nowNano(): bigint {
  return BigInt(Math.round(performance.now() * 1e6)) + BigInt(performance.timeOrigin * 1e6);
}
