/**
 * Baseline definitions for client-side metrics.
 */
export interface RouteBaseline {
  pageLoadP50Ms?: number;
  pageLoadP99Ms?: number;
  interactionErrorRate?: number;
  apiCallP50Ms?: number;
  source: 'static' | 'rolling';
}
