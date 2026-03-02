/**
 * @agenttel/web — Agent-ready telemetry for browser applications.
 *
 * Automatically instruments page loads, navigation, API calls,
 * user interactions, and errors — enriched with baselines, anomaly
 * detection, and W3C Trace Context for frontend-to-backend correlation.
 *
 * @example
 * ```typescript
 * import { AgentTelWeb } from '@agenttel/web';
 *
 * AgentTelWeb.init({
 *   appName: 'checkout-web',
 *   collectorEndpoint: '/otlp',
 *   routes: {
 *     '/checkout/:step': {
 *       businessCriticality: 'revenue',
 *       baseline: { pageLoadP50Ms: 800 },
 *       decision: { escalationLevel: 'page_oncall' },
 *     },
 *   },
 * });
 * ```
 */

// Main SDK
export { AgentTelWeb } from './core/agenttel-web';

// Config types
export type {
  AgentTelWebConfig,
  RouteConfig,
  RouteBaselineConfig,
  RouteDecisionConfig,
  JourneyConfig,
  JourneyBaselineConfig,
  AnomalyDetectionConfig,
  BatchConfig,
} from './config/types';

// Attribute keys
export { AgentTelClientAttributes } from './core/attribute-keys';
export type { AgentTelClientAttributeKey } from './core/attribute-keys';

// Types
export type { InternalSpan, SpanEvent, SpanKind, AttributeValue } from './types/span';
export { AnomalyPattern } from './types/anomaly';
export type { AnomalyResult } from './types/anomaly';
export type { JourneyState } from './types/journey';
export type { RouteBaseline } from './types/baseline';
