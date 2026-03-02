import { AnomalyPattern } from '../types/anomaly';
import type { AnomalyResult } from '../types/anomaly';

/**
 * Client-side anomaly detection engine.
 * Detects 6 interaction patterns that indicate problems.
 */
export class ClientAnomalyDetector {
  private apiErrorBuffer: number[] = [];
  private readonly apiFailureCascadeThreshold: number;
  private readonly apiFailureCascadeWindowMs: number;
  private readonly slowLoadMultiplier: number;

  constructor(params?: {
    apiFailureCascadeThreshold?: number;
    apiFailureCascadeWindowMs?: number;
    slowLoadMultiplier?: number;
  }) {
    this.apiFailureCascadeThreshold = params?.apiFailureCascadeThreshold ?? 3;
    this.apiFailureCascadeWindowMs = params?.apiFailureCascadeWindowMs ?? 10000;
    this.slowLoadMultiplier = params?.slowLoadMultiplier ?? 3.0;
  }

  /**
   * Detect slow page load (load time > multiplier x P50 baseline).
   */
  detectSlowPageLoad(loadTimeMs: number, baselineP50Ms?: number): AnomalyResult | null {
    if (!baselineP50Ms || baselineP50Ms <= 0) return null;

    const ratio = loadTimeMs / baselineP50Ms;
    if (ratio > this.slowLoadMultiplier) {
      return {
        detected: true,
        pattern: AnomalyPattern.SLOW_PAGE_LOAD,
        score: Math.min(ratio / (this.slowLoadMultiplier * 2), 1.0),
      };
    }
    return null;
  }

  /**
   * Record an API error and check for cascade pattern.
   */
  recordApiError(timestamp: number): AnomalyResult | null {
    this.apiErrorBuffer.push(timestamp);
    this.apiErrorBuffer = this.apiErrorBuffer.filter(
      (t) => timestamp - t < this.apiFailureCascadeWindowMs,
    );

    if (this.apiErrorBuffer.length >= this.apiFailureCascadeThreshold) {
      return {
        detected: true,
        pattern: AnomalyPattern.API_FAILURE_CASCADE,
        score: Math.min(this.apiErrorBuffer.length / (this.apiFailureCascadeThreshold * 2), 1.0),
      };
    }
    return null;
  }

  /**
   * Detect funnel drop-off (journey abandonment above baseline).
   */
  detectFunnelDropOff(
    currentDropOffRate: number,
    baselineDropOffRate: number,
    multiplier: number = 2.0,
  ): AnomalyResult | null {
    if (baselineDropOffRate <= 0) return null;

    const ratio = currentDropOffRate / baselineDropOffRate;
    if (ratio > multiplier) {
      return {
        detected: true,
        pattern: AnomalyPattern.FUNNEL_DROP_OFF,
        score: Math.min(ratio / (multiplier * 2), 1.0),
      };
    }
    return null;
  }
}
