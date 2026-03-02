/**
 * Client-side anomaly patterns detected by the enrichment engine.
 */
export enum AnomalyPattern {
  RAGE_CLICK = 'RAGE_CLICK',
  SLOW_PAGE_LOAD = 'SLOW_PAGE_LOAD',
  API_FAILURE_CASCADE = 'API_FAILURE_CASCADE',
  FUNNEL_DROP_OFF = 'FUNNEL_DROP_OFF',
  ERROR_LOOP = 'ERROR_LOOP',
  FORM_ABANDONMENT = 'FORM_ABANDONMENT',
}

export interface AnomalyResult {
  detected: boolean;
  pattern: AnomalyPattern;
  score: number; // 0.0 - 1.0
}
