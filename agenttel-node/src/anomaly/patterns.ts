import { IncidentPattern } from '../enums-impl.js';
import type { RollingSnapshot } from '../interfaces.js';

interface LatencyTrend {
  samples: number[];
  times: number[];
}

/** Detects known incident patterns from telemetry observations. */
export class PatternMatcher {
  private readonly latencyDegradationThreshold: number;
  private readonly errorRateSpikeThreshold: number;
  private readonly cascadeFailureMinServices: number;
  private depErrors = new Map<string, number>();
  private latencyTrends = new Map<string, LatencyTrend>();

  constructor(latencyDegradation = 2.0, errorRateSpike = 0.1, cascadeMin = 3) {
    this.latencyDegradationThreshold = latencyDegradation > 0 ? latencyDegradation : 2.0;
    this.errorRateSpikeThreshold = errorRateSpike > 0 ? errorRateSpike : 0.1;
    this.cascadeFailureMinServices = cascadeMin > 0 ? cascadeMin : 3;
  }

  recordDependencyError(dependency: string): void {
    this.depErrors.set(dependency, (this.depErrors.get(dependency) ?? 0) + 1);
  }

  recordLatency(operationName: string, latencyMs: number): void {
    let trend = this.latencyTrends.get(operationName);
    if (!trend) {
      trend = { samples: [], times: [] };
      this.latencyTrends.set(operationName, trend);
    }
    trend.samples.push(latencyMs);
    trend.times.push(Date.now());
    if (trend.samples.length > 100) {
      trend.samples = trend.samples.slice(-100);
      trend.times = trend.times.slice(-100);
    }
  }

  resetDependencyErrors(): void {
    this.depErrors.clear();
  }

  detectPatterns(
    operationName: string,
    currentLatencyMs: number,
    isError: boolean,
    snapshot: RollingSnapshot,
  ): IncidentPattern[] {
    const patterns: IncidentPattern[] = [];

    // CASCADE_FAILURE: multiple dependencies failing
    let failingDeps = 0;
    for (const count of this.depErrors.values()) {
      if (count > 0) failingDeps++;
    }
    if (failingDeps >= this.cascadeFailureMinServices) {
      patterns.push(IncidentPattern.CASCADE_FAILURE);
    }

    if (snapshot.sampleCount === 0) return patterns;

    // ERROR_RATE_SPIKE
    if (snapshot.errorRate > this.errorRateSpikeThreshold) {
      patterns.push(IncidentPattern.ERROR_RATE_SPIKE);
    }

    // LATENCY_DEGRADATION
    if (snapshot.mean > 0 && currentLatencyMs > snapshot.mean * this.latencyDegradationThreshold) {
      patterns.push(IncidentPattern.LATENCY_DEGRADATION);
    }

    // MEMORY_LEAK: monotonically increasing latency with rising errors
    const trend = this.latencyTrends.get(operationName);
    if (trend && trend.samples.length >= 10) {
      const recent = trend.samples.slice(-10);
      if (isMonotonicallyIncreasing(recent) && snapshot.errorRate > 0.01) {
        patterns.push(IncidentPattern.MEMORY_LEAK);
      }
    }

    return patterns;
  }
}

function isMonotonicallyIncreasing(vals: number[]): boolean {
  if (vals.length < 3) return false;
  let increases = 0;
  for (let i = 1; i < vals.length; i++) {
    if (vals[i] > vals[i - 1]) increases++;
  }
  return increases / (vals.length - 1) >= 0.8;
}
