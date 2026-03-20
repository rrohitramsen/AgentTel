import type { OperationBaseline, RollingSnapshot } from '../interfaces.js';
import type { BaselineProvider } from './provider.js';

/** A ring buffer for latency and error tracking. */
export class RollingWindow {
  private readonly values: Float64Array;
  private readonly times: Float64Array;
  private readonly errors: Uint8Array;
  private readonly capacity: number;
  private writeIdx = 0;
  private count = 0;

  constructor(capacity = 1000) {
    this.capacity = Math.max(1, capacity);
    this.values = new Float64Array(this.capacity);
    this.times = new Float64Array(this.capacity);
    this.errors = new Uint8Array(this.capacity);
  }

  record(value: number): void {
    const idx = this.writeIdx % this.capacity;
    this.values[idx] = value;
    this.times[idx] = Date.now();
    this.errors[idx] = 0;
    this.writeIdx++;
    this.count++;
  }

  recordError(): void {
    const idx = this.writeIdx % this.capacity;
    this.values[idx] = 0;
    this.times[idx] = Date.now();
    this.errors[idx] = 1;
    this.writeIdx++;
    this.count++;
  }

  size(): number {
    return Math.min(this.count, this.capacity);
  }

  snapshot(): RollingSnapshot {
    const n = this.size();
    if (n === 0) {
      return { mean: 0, stddev: 0, p50: 0, p95: 0, p99: 0, errorRate: 0, sampleCount: 0, ageMs: 0 };
    }

    const latencies: number[] = [];
    let errorCount = 0;
    let oldestTime = Infinity;

    for (let i = 0; i < n; i++) {
      if (this.errors[i]) {
        errorCount++;
      } else if (this.values[i] > 0 || this.times[i] > 0) {
        latencies.push(this.values[i]);
      }
      if (this.times[i] > 0 && this.times[i] < oldestTime) {
        oldestTime = this.times[i];
      }
    }

    if (latencies.length === 0) {
      return {
        mean: 0, stddev: 0, p50: 0, p95: 0, p99: 0,
        errorRate: 1.0,
        sampleCount: n,
        ageMs: oldestTime === Infinity ? 0 : Date.now() - oldestTime,
      };
    }

    latencies.sort((a, b) => a - b);
    const m = mean(latencies);
    const sd = stddev(latencies, m);

    return {
      mean: m,
      stddev: sd,
      p50: percentile(latencies, 0.50),
      p95: percentile(latencies, 0.95),
      p99: percentile(latencies, 0.99),
      errorRate: errorCount / n,
      sampleCount: n,
      ageMs: oldestTime === Infinity ? 0 : Date.now() - oldestTime,
    };
  }
}

/** Tracks rolling baselines per operation. */
export class RollingBaselineProvider implements BaselineProvider {
  private readonly windows = new Map<string, RollingWindow>();
  private readonly windowSize: number;
  private readonly minSamples: number;

  constructor(windowSize = 1000, minSamples = 10) {
    this.windowSize = Math.max(1, windowSize);
    this.minSamples = Math.max(1, minSamples);
  }

  recordLatency(operationName: string, latencyMs: number): void {
    this.getOrCreate(operationName).record(latencyMs);
  }

  recordError(operationName: string): void {
    this.getOrCreate(operationName).recordError();
  }

  getBaseline(operationName: string): OperationBaseline | undefined {
    const w = this.windows.get(operationName);
    if (!w) return undefined;

    const snap = w.snapshot();
    if (snap.sampleCount < this.minSamples) return undefined;

    return {
      operationName,
      latencyP50Ms: snap.p50,
      latencyP99Ms: snap.p99,
      errorRate: snap.errorRate,
      source: 'rolling_7d',
      updatedAt: new Date(),
    };
  }

  getSnapshot(operationName: string): RollingSnapshot | undefined {
    const w = this.windows.get(operationName);
    if (!w) return undefined;
    return w.snapshot();
  }

  private getOrCreate(operationName: string): RollingWindow {
    let w = this.windows.get(operationName);
    if (!w) {
      w = new RollingWindow(this.windowSize);
      this.windows.set(operationName, w);
    }
    return w;
  }
}

function mean(vals: number[]): number {
  if (vals.length === 0) return 0;
  let sum = 0;
  for (const v of vals) sum += v;
  return sum / vals.length;
}

function stddev(vals: number[], m: number): number {
  if (vals.length < 2) return 0;
  let sumSq = 0;
  for (const v of vals) {
    const d = v - m;
    sumSq += d * d;
  }
  return Math.sqrt(sumSq / vals.length);
}

function percentile(sorted: number[], p: number): number {
  if (sorted.length === 0) return 0;
  const idx = p * (sorted.length - 1);
  const lower = Math.floor(idx);
  const upper = Math.ceil(idx);
  if (lower === upper || upper >= sorted.length) return sorted[lower];
  const frac = idx - lower;
  return sorted[lower] * (1 - frac) + sorted[upper] * frac;
}
