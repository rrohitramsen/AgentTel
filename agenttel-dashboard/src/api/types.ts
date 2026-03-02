/**
 * TypeScript types for MCP tool responses.
 */

export interface OperationHealth {
  operation: string;
  status: string;
  error_rate: number;
  latency_p50_ms?: number;
  latency_p99_ms: number;
  throughput_rpm?: number;
  baseline_p50_ms?: number;
  baseline_p99_ms?: number;
  deviation_status?: string;
}

export interface DependencyHealth {
  name: string;
  error_rate: number;
  latency_mean_ms: number;
  total_calls?: number;
}

export interface SloStatus {
  name: string;
  target: number;
  actual: number;
  budget_remaining: number;
  burn_rate: number;
  status: string;
}

export interface ServiceHealth {
  service: string;
  overall_status: string;
  operations: OperationHealth[];
  dependencies?: DependencyHealth[];
  slo_statuses?: SloStatus[];
  timestamp?: string;
}

export interface SloReport {
  total_slos: number;
  healthy: number;
  at_risk: number;
  violated: number;
  slos: SloStatus[];
  raw_text?: string;
}

export interface TrendPoint {
  timestamp: string;
  value: number;
}

export interface TrendAnalysis {
  operation: string;
  latency_p50: { current: number; trend: string; data: TrendPoint[] };
  error_rate: { current: number; trend: string; data: TrendPoint[] };
  throughput: { current: number; trend: string; data: TrendPoint[] };
  overall: string;
  raw_text?: string;
}

export interface AgentAction {
  name: string;
  type: string;
  reason: string;
  status: string;
  timestamp: string;
}

export interface IncidentContext {
  incident_id: string;
  severity: string;
  summary: string;
  what_is_happening: string;
  what_changed: string;
  what_is_affected: string;
  what_to_do: string;
  raw_text?: string;
}

export interface CrossStackContext {
  frontend: string;
  backend: string;
  correlation: string;
  raw_text?: string;
}

export interface FeedbackEvent {
  trigger: string;
  risk_level: 'low' | 'medium' | 'high';
  target: string;
  current_value?: string;
  suggested_value?: string;
  reasoning: string;
  auto_applicable: boolean;
}
