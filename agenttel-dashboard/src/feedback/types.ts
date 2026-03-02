export type FeedbackTrigger =
  | 'missing_baseline'
  | 'stale_baseline'
  | 'uncovered_endpoint'
  | 'uncovered_route'
  | 'missing_runbook'
  | 'slo_burn_rate_high'
  | 'unmonitored_service';

export type RiskLevel = 'low' | 'medium' | 'high';

export interface FeedbackEvent {
  trigger: FeedbackTrigger;
  riskLevel: RiskLevel;
  target: string;
  currentValue?: string;
  suggestedValue?: string;
  reasoning: string;
  autoApplicable: boolean;
}

export const triggerLabels: Record<FeedbackTrigger, string> = {
  missing_baseline: 'Missing Baseline',
  stale_baseline: 'Stale Baseline',
  uncovered_endpoint: 'Uncovered Endpoint',
  uncovered_route: 'Uncovered Route',
  missing_runbook: 'Missing Runbook',
  slo_burn_rate_high: 'High SLO Burn Rate',
  unmonitored_service: 'Unmonitored Service',
};

export const riskLabels: Record<RiskLevel, string> = {
  low: 'Auto-apply',
  medium: 'Needs Approval',
  high: 'Manual Only',
};
