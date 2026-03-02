import type { SpanFactory } from '../core/span-factory';
import type { BatchProcessor } from '../transport/batch-processor';
import type { JourneyConfig } from '../config/types';
import type { JourneyState } from '../types/journey';

/**
 * Tracks multi-step user journeys (funnels) and detects drop-offs.
 */
export class JourneyTracker {
  private readonly spanFactory: SpanFactory;
  private readonly processor: BatchProcessor;
  private readonly journeys: Record<string, JourneyConfig>;
  private activeJourneys: Map<string, JourneyState> = new Map();

  constructor(
    spanFactory: SpanFactory,
    processor: BatchProcessor,
    journeys: Record<string, JourneyConfig>,
  ) {
    this.spanFactory = spanFactory;
    this.processor = processor;
    this.journeys = journeys;
  }

  /**
   * Called on every navigation — automatically detects journey start/advance/complete.
   */
  onNavigate(route: string): void {
    // Check if this route starts any journey
    for (const [name, config] of Object.entries(this.journeys)) {
      if (!this.activeJourneys.has(name) && this.matchesStep(route, config.steps[0])) {
        this.startJourney(name);
      }
    }

    // Advance active journeys
    for (const [name, state] of this.activeJourneys) {
      const config = this.journeys[name];
      if (!config) continue;

      const nextStep = state.currentStep + 1;
      if (nextStep < config.steps.length && this.matchesStep(route, config.steps[nextStep])) {
        this.advanceJourney(name);
      }
    }
  }

  /**
   * Starts tracking a user journey.
   */
  startJourney(name: string): void {
    const config = this.journeys[name];
    if (!config) return;

    const state: JourneyState = {
      name,
      currentStep: 0,
      totalSteps: config.steps.length,
      startedAt: new Date().toISOString(),
      stepRoutes: config.steps,
      completed: false,
      abandoned: false,
    };

    this.activeJourneys.set(name, state);
  }

  /**
   * Advances to the next step in a journey.
   */
  advanceJourney(name: string): void {
    const state = this.activeJourneys.get(name);
    if (!state) return;

    state.currentStep++;

    // Create a journey step span
    const span = this.spanFactory.createInteractionSpan({
      type: 'journey_step',
      target: name,
      outcome: 'success',
    });

    this.spanFactory.addJourneyAttributes(
      span,
      name,
      state.currentStep,
      state.totalSteps,
      state.startedAt,
    );

    this.processor.addSpan(span);

    // Check if journey is complete
    if (state.currentStep >= state.totalSteps - 1) {
      this.completeJourney(name);
    }
  }

  /**
   * Marks a journey as completed.
   */
  completeJourney(name: string): void {
    const state = this.activeJourneys.get(name);
    if (!state) return;

    state.completed = true;

    const span = this.spanFactory.createInteractionSpan({
      type: 'journey_complete',
      target: name,
      outcome: 'success',
    });

    this.spanFactory.addJourneyAttributes(
      span,
      name,
      state.totalSteps,
      state.totalSteps,
      state.startedAt,
    );

    this.processor.addSpan(span);
    this.activeJourneys.delete(name);
  }

  /**
   * Marks a journey as abandoned (e.g., user navigates away from funnel).
   */
  abandonJourney(name: string): void {
    const state = this.activeJourneys.get(name);
    if (!state) return;

    state.abandoned = true;

    const span = this.spanFactory.createInteractionSpan({
      type: 'journey_abandon',
      target: name,
      outcome: 'abandoned',
    });

    this.spanFactory.addJourneyAttributes(
      span,
      name,
      state.currentStep,
      state.totalSteps,
      state.startedAt,
    );

    this.spanFactory.addAnomalyAttributes(
      span,
      'FUNNEL_DROP_OFF',
      (state.totalSteps - state.currentStep) / state.totalSteps,
    );

    this.processor.addSpan(span);
    this.activeJourneys.delete(name);
  }

  /**
   * Get active journey states (for external queries).
   */
  getActiveJourneys(): JourneyState[] {
    return Array.from(this.activeJourneys.values());
  }

  private matchesStep(route: string, stepPattern: string): boolean {
    const regex = new RegExp(
      '^' + stepPattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/:\\w+/g, '[^/]+') + '$',
    );
    return regex.test(route);
  }
}
