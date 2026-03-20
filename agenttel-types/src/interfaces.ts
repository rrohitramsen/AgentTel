/** Expected operational norms for an operation. */
export interface OperationBaseline {
  operationName: string;
  latencyP50Ms: number;
  latencyP99Ms: number;
  errorRate: number;
  source: string;
  updatedAt: Date;
}

/** Describes a service dependency with its metadata. */
export interface DependencyDescriptor {
  name: string;
  type: string;
  criticality: string;
  protocol?: string;
  timeoutMs?: number;
  circuitBreaker?: boolean;
  fallback?: string;
  healthEndpoint?: string;
}

/** Describes a downstream consumer of a service. */
export interface ConsumerDescriptor {
  name: string;
  pattern: string;
  slaLatencyMs: number;
}

/** Defines a service level objective. */
export interface SLODefinition {
  name: string;
  operationName?: string;
  type: string;
  target: number;
  windowSeconds?: number;
}

/** Result of an anomaly evaluation. */
export interface AnomalyResult {
  anomalyScore: number;
  isAnomaly: boolean;
  zScore: number;
}

/** Current status of an SLO. */
export interface SLOStatus {
  sloName: string;
  target: number;
  actual: number;
  budgetRemaining: number;
  burnRate: number;
  totalRequests: number;
  failedRequests: number;
}

/** An SLO budget alert. */
export interface SLOAlert {
  sloName: string;
  severity: string;
  budgetRemaining: number;
  burnRate: number;
}

/** Result of classifying a span error. */
export interface ErrorClassification {
  category: string;
  rootException: string;
  dependency: string;
}

/** Point-in-time snapshot of a rolling window. */
export interface RollingSnapshot {
  mean: number;
  stddev: number;
  p50: number;
  p95: number;
  p99: number;
  errorRate: number;
  sampleCount: number;
  ageMs: number;
}
