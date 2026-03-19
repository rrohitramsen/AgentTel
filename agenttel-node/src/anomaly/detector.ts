import type { AnomalyResult } from '../interfaces.js';
import { normalResult } from '../interfaces.js';

/** Z-score-based anomaly detector. */
export class AnomalyDetector {
  private readonly zScoreThreshold: number;

  constructor(zScoreThreshold = 3.0) {
    this.zScoreThreshold = zScoreThreshold > 0 ? zScoreThreshold : 3.0;
  }

  /** Evaluates whether the current value is anomalous given baseline stats. */
  evaluate(metric: string, current: number, baselineMean: number, baselineStddev: number): AnomalyResult {
    if (baselineStddev <= 0) return normalResult();

    const zScore = (current - baselineMean) / baselineStddev;
    const absZ = Math.abs(zScore);
    const isAnomaly = absZ >= this.zScoreThreshold;

    let anomalyScore = 0;
    if (isAnomaly) {
      anomalyScore = Math.min(1.0, absZ / this.zScoreThreshold / 2.0);
    }

    return { anomalyScore, isAnomaly, zScore };
  }
}
