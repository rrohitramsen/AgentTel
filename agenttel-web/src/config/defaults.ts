import type { AnomalyDetectionConfig, BatchConfig } from './types';

export const DEFAULT_ANOMALY_CONFIG: Required<AnomalyDetectionConfig> = {
  enabled: true,
  rageClickThreshold: 3,
  rageClickWindowMs: 2000,
  slowLoadMultiplier: 3.0,
  apiFailureCascadeThreshold: 3,
  apiFailureCascadeWindowMs: 10000,
  errorLoopThreshold: 5,
  errorLoopWindowMs: 30000,
};

export const DEFAULT_BATCH_CONFIG: Required<BatchConfig> = {
  maxSize: 50,
  flushIntervalMs: 5000,
};
