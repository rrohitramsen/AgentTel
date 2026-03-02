/**
 * Configuration types for @agenttel/web SDK.
 */

export interface AgentTelWebConfig {
  /** Application name */
  appName: string;
  /** Application version */
  appVersion?: string;
  /** Platform: 'web' | 'mobile' */
  platform?: string;
  /** Environment: 'production' | 'staging' | 'development' */
  environment?: string;
  /** Owning team */
  team?: string;
  /** Business domain */
  domain?: string;

  /** OTLP collector endpoint (e.g., 'http://localhost:4318' or '/otlp') */
  collectorEndpoint: string;

  /** Route configurations with baselines and decisions */
  routes?: Record<string, RouteConfig>;

  /** User journey/funnel definitions */
  journeys?: Record<string, JourneyConfig>;

  /** Anomaly detection settings */
  anomalyDetection?: AnomalyDetectionConfig;

  /** Sampling rate: 0.0 to 1.0 (default: 1.0 = send everything) */
  samplingRate?: number;

  /** Batch processor settings */
  batch?: BatchConfig;

  /** Enable debug logging */
  debug?: boolean;
}

export interface RouteConfig {
  /** Business criticality: 'revenue' | 'engagement' | 'internal' */
  businessCriticality?: string;
  /** Expected baselines for this route */
  baseline?: RouteBaselineConfig;
  /** Decision metadata for agents */
  decision?: RouteDecisionConfig;
}

export interface RouteBaselineConfig {
  pageLoadP50Ms?: number;
  pageLoadP99Ms?: number;
  interactionErrorRate?: number;
  apiCallP50Ms?: number;
}

export interface RouteDecisionConfig {
  retryOnFailure?: boolean;
  fallbackPage?: string;
  escalationLevel?: string;
  runbookUrl?: string;
}

export interface JourneyConfig {
  /** Ordered list of route patterns that make up this journey */
  steps: string[];
  /** Expected baselines */
  baseline?: JourneyBaselineConfig;
}

export interface JourneyBaselineConfig {
  completionRate?: number;
  avgDurationS?: number;
}

export interface AnomalyDetectionConfig {
  enabled?: boolean;
  rageClickThreshold?: number;
  rageClickWindowMs?: number;
  slowLoadMultiplier?: number;
  apiFailureCascadeThreshold?: number;
  apiFailureCascadeWindowMs?: number;
  errorLoopThreshold?: number;
  errorLoopWindowMs?: number;
}

export interface BatchConfig {
  maxSize?: number;
  flushIntervalMs?: number;
}
