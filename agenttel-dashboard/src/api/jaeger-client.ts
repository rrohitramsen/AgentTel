import { config } from '../config';

export interface JaegerTrace {
  traceID: string;
  spans: JaegerSpan[];
  processes: Record<string, { serviceName: string; tags: JaegerTag[] }>;
}

export interface JaegerSpan {
  traceID: string;
  spanID: string;
  operationName: string;
  references: { refType: string; traceID: string; spanID: string }[];
  startTime: number; // microseconds
  duration: number; // microseconds
  tags: JaegerTag[];
  logs: { timestamp: number; fields: JaegerTag[] }[];
  processID: string;
}

export interface JaegerTag {
  key: string;
  type: string;
  value: string | number | boolean;
}

export async function searchTraces(
  service: string,
  options?: { limit?: number; operation?: string }
): Promise<JaegerTrace[]> {
  const params = new URLSearchParams({
    service,
    limit: String(options?.limit ?? 10),
    lookback: '1h',
  });
  if (options?.operation) params.set('operation', options.operation);

  try {
    const res = await fetch(`${config.jaegerApiUrl}/traces?${params}`);
    if (!res.ok) return [];
    const json = await res.json();
    return json.data || [];
  } catch {
    return [];
  }
}

export async function getServices(): Promise<string[]> {
  try {
    const res = await fetch(`${config.jaegerApiUrl}/services`);
    if (!res.ok) return [];
    const json = await res.json();
    return json.data || [];
  } catch {
    return [];
  }
}
