/**
 * User journey (multi-step funnel) state tracking.
 */
export interface JourneyState {
  name: string;
  currentStep: number;
  totalSteps: number;
  startedAt: string; // ISO 8601
  stepRoutes: string[];
  completed: boolean;
  abandoned: boolean;
}
