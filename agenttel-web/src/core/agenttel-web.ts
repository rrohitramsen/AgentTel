import type { AgentTelWebConfig } from '../config/types';
import type { AttributeValue } from '../types/span';
import { DEFAULT_ANOMALY_CONFIG, DEFAULT_BATCH_CONFIG } from '../config/defaults';
import { SpanFactory } from './span-factory';
import { OtlpHttpExporter } from '../transport/otlp-exporter';
import { BatchProcessor } from '../transport/batch-processor';
import { PageTracker } from '../trackers/page-tracker';
import { NavigationTracker } from '../trackers/navigation-tracker';
import { ApiTracker } from '../trackers/api-tracker';
import { InteractionTracker } from '../trackers/interaction-tracker';
import { ErrorTracker } from '../trackers/error-tracker';
import { ClientAnomalyDetector } from '../enrichment/anomaly-detector';
import { JourneyTracker } from '../enrichment/journey-tracker';
import { CorrelationEngine } from '../enrichment/correlation-engine';

/**
 * Main entry point for the AgentTel Web SDK.
 *
 * ```typescript
 * import { AgentTelWeb } from '@agenttel/web';
 *
 * const agenttel = AgentTelWeb.init({
 *   appName: 'checkout-web',
 *   collectorEndpoint: '/otlp',
 *   routes: { '/checkout/:step': { businessCriticality: 'revenue' } },
 * });
 * ```
 */
export class AgentTelWeb {
  private static instance: AgentTelWeb | null = null;

  readonly config: AgentTelWebConfig;
  readonly spanFactory: SpanFactory;
  readonly processor: BatchProcessor;
  readonly anomalyDetector: ClientAnomalyDetector;
  readonly correlationEngine: CorrelationEngine;

  private navigationTracker?: NavigationTracker;
  private journeyTracker?: JourneyTracker;

  private constructor(config: AgentTelWebConfig) {
    this.config = config;

    // Core
    this.spanFactory = new SpanFactory(config);
    const exporter = new OtlpHttpExporter(config.collectorEndpoint);
    const batchConfig = { ...DEFAULT_BATCH_CONFIG, ...config.batch };
    this.processor = new BatchProcessor(exporter, batchConfig.maxSize, batchConfig.flushIntervalMs);

    // Enrichment
    const anomalyConfig = { ...DEFAULT_ANOMALY_CONFIG, ...config.anomalyDetection };
    this.anomalyDetector = new ClientAnomalyDetector({
      apiFailureCascadeThreshold: anomalyConfig.apiFailureCascadeThreshold,
      apiFailureCascadeWindowMs: anomalyConfig.apiFailureCascadeWindowMs,
      slowLoadMultiplier: anomalyConfig.slowLoadMultiplier,
    });
    this.correlationEngine = new CorrelationEngine();

    if (typeof window !== 'undefined') {
      this.initTrackers(anomalyConfig);
    }

    if (config.debug) {
      console.log('[AgentTel] Initialized', config.appName);
    }
  }

  /**
   * Initializes the SDK. Call once at app startup.
   */
  static init(config: AgentTelWebConfig): AgentTelWeb {
    if (AgentTelWeb.instance) {
      console.warn('[AgentTel] Already initialized. Returning existing instance.');
      return AgentTelWeb.instance;
    }
    AgentTelWeb.instance = new AgentTelWeb(config);
    return AgentTelWeb.instance;
  }

  /**
   * Returns the singleton instance.
   */
  static getInstance(): AgentTelWeb | null {
    return AgentTelWeb.instance;
  }

  /**
   * Manually track a custom interaction.
   */
  trackInteraction(type: string, params: {
    target: string;
    outcome?: string;
    durationMs?: number;
    metadata?: Record<string, AttributeValue>;
  }): void {
    const span = this.spanFactory.createInteractionSpan({
      type,
      target: params.target,
      outcome: params.outcome ?? 'success',
      durationMs: params.durationMs,
      metadata: params.metadata,
    });
    this.processor.addSpan(span);
  }

  /**
   * Manually start a user journey.
   */
  startJourney(name: string): void {
    this.journeyTracker?.startJourney(name);
  }

  /**
   * Manually advance a user journey to the next step.
   */
  advanceJourney(name: string): void {
    this.journeyTracker?.advanceJourney(name);
  }

  /**
   * Manually complete a user journey.
   */
  completeJourney(name: string): void {
    this.journeyTracker?.completeJourney(name);
  }

  /**
   * Flush all pending spans immediately.
   */
  async flush(): Promise<void> {
    await this.processor.flush();
  }

  /**
   * Shut down the SDK.
   */
  shutdown(): void {
    this.processor.shutdown();
    AgentTelWeb.instance = null;
  }

  /**
   * Reset for testing.
   */
  static _reset(): void {
    AgentTelWeb.instance?.shutdown();
    AgentTelWeb.instance = null;
  }

  private initTrackers(anomalyConfig: Required<typeof DEFAULT_ANOMALY_CONFIG>): void {
    // Page load tracking
    new PageTracker(this.spanFactory, this.processor);

    // Navigation tracking (SPA)
    this.navigationTracker = new NavigationTracker(this.spanFactory, this.processor);

    // API call tracking with W3C Trace Context correlation
    new ApiTracker(
      this.spanFactory,
      this.processor,
      (timestamp) => {
        const result = this.anomalyDetector.recordApiError(timestamp);
        if (result && this.config.debug) {
          console.warn('[AgentTel] API failure cascade detected');
        }
      },
    );

    // Click/submit interaction tracking with rage click detection
    new InteractionTracker(
      this.spanFactory,
      this.processor,
      anomalyConfig.rageClickThreshold,
      anomalyConfig.rageClickWindowMs,
      (target) => {
        if (this.config.debug) {
          console.warn(`[AgentTel] Rage click detected on: ${target}`);
        }
      },
    );

    // Error tracking with error loop detection
    new ErrorTracker(
      this.spanFactory,
      this.processor,
      anomalyConfig.errorLoopThreshold,
      anomalyConfig.errorLoopWindowMs,
      () => this.navigationTracker?.getCurrentRoute() ?? window.location.pathname,
      (message) => {
        if (this.config.debug) {
          console.warn(`[AgentTel] Error loop detected: ${message}`);
        }
      },
    );

    // Journey/funnel tracking
    if (this.config.journeys && Object.keys(this.config.journeys).length > 0) {
      this.journeyTracker = new JourneyTracker(
        this.spanFactory,
        this.processor,
        this.config.journeys,
      );

      // Wire navigation events to journey tracker
      this.navigationTracker.onNavigate((route) => {
        this.journeyTracker?.onNavigate(route);
      });
    }
  }
}
