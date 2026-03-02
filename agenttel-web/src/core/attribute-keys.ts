/**
 * AgentTel client-side semantic attribute keys.
 * Mirrors the backend AgentTelAttributes.java — identical string keys
 * ensure cross-stack correlation works seamlessly.
 */
export const AgentTelClientAttributes = {
  // Resource — set once per app
  APP_NAME: 'agenttel.client.app.name',
  APP_VERSION: 'agenttel.client.app.version',
  APP_PLATFORM: 'agenttel.client.app.platform',
  APP_ENVIRONMENT: 'agenttel.client.app.environment',
  TOPOLOGY_TEAM: 'agenttel.client.topology.team',
  TOPOLOGY_DOMAIN: 'agenttel.client.topology.domain',

  // Page/Screen — set per page view
  PAGE_ROUTE: 'agenttel.client.page.route',
  PAGE_TITLE: 'agenttel.client.page.title',
  PAGE_BUSINESS_CRITICALITY: 'agenttel.client.page.business_criticality',

  // Journey — multi-step funnel tracking
  JOURNEY_NAME: 'agenttel.client.journey.name',
  JOURNEY_STEP: 'agenttel.client.journey.step',
  JOURNEY_TOTAL_STEPS: 'agenttel.client.journey.total_steps',
  JOURNEY_STARTED_AT: 'agenttel.client.journey.started_at',

  // Baseline — what "normal" looks like for this route
  BASELINE_PAGE_LOAD_P50_MS: 'agenttel.client.baseline.page_load_p50_ms',
  BASELINE_PAGE_LOAD_P99_MS: 'agenttel.client.baseline.page_load_p99_ms',
  BASELINE_INTERACTION_ERROR_RATE: 'agenttel.client.baseline.interaction_error_rate',
  BASELINE_API_CALL_P50_MS: 'agenttel.client.baseline.api_call_p50_ms',
  BASELINE_SOURCE: 'agenttel.client.baseline.source',

  // Decision — what an agent is allowed to do
  DECISION_RETRY_ON_FAILURE: 'agenttel.client.decision.retry_on_failure',
  DECISION_FALLBACK_PAGE: 'agenttel.client.decision.fallback_page',
  DECISION_ESCALATION_LEVEL: 'agenttel.client.decision.escalation_level',
  DECISION_RUNBOOK_URL: 'agenttel.client.decision.runbook_url',
  DECISION_USER_FACING: 'agenttel.client.decision.user_facing',

  // Interaction — enriched user interaction events
  INTERACTION_TYPE: 'agenttel.client.interaction.type',
  INTERACTION_TARGET: 'agenttel.client.interaction.target',
  INTERACTION_OUTCOME: 'agenttel.client.interaction.outcome',
  INTERACTION_RESPONSE_TIME_MS: 'agenttel.client.interaction.response_time_ms',

  // Anomaly — client-side anomaly detection
  ANOMALY_DETECTED: 'agenttel.client.anomaly.detected',
  ANOMALY_SCORE: 'agenttel.client.anomaly.score',
  ANOMALY_PATTERN: 'agenttel.client.anomaly.pattern',

  // Correlation — frontend ↔ backend trace linking
  CORRELATION_BACKEND_TRACE_ID: 'agenttel.client.correlation.backend_trace_id',
  CORRELATION_BACKEND_SERVICE: 'agenttel.client.correlation.backend_service',
  CORRELATION_BACKEND_OPERATION: 'agenttel.client.correlation.backend_operation',
} as const;

export type AgentTelClientAttributeKey = typeof AgentTelClientAttributes[keyof typeof AgentTelClientAttributes];
