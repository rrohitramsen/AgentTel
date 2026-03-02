import type { InternalSpan, SpanKind, AttributeValue } from '../types/span';
import { generateTraceId, generateSpanId, nowNano } from '../types/span';
import type { AgentTelWebConfig, RouteConfig } from '../config/types';
import { AgentTelClientAttributes } from './attribute-keys';
import { buildResourceAttributes } from './resource';

/**
 * Factory for creating enriched internal spans with AgentTel attributes.
 */
export class SpanFactory {
  private readonly resourceAttrs: Record<string, AttributeValue>;
  private readonly config: AgentTelWebConfig;
  private currentTraceId: string;

  constructor(config: AgentTelWebConfig) {
    this.config = config;
    this.resourceAttrs = buildResourceAttributes(config);
    this.currentTraceId = generateTraceId();
  }

  /**
   * Generates a new page-level trace ID (called on navigation).
   */
  newPageTrace(): string {
    this.currentTraceId = generateTraceId();
    return this.currentTraceId;
  }

  getCurrentTraceId(): string {
    return this.currentTraceId;
  }

  /**
   * Creates a page load span.
   */
  createPageLoadSpan(route: string, title: string, loadTimeMs: number): InternalSpan {
    const routeConfig = this.matchRoute(route);
    const attrs: Record<string, AttributeValue> = {
      ...this.resourceAttrs,
      [AgentTelClientAttributes.PAGE_ROUTE]: route,
      [AgentTelClientAttributes.PAGE_TITLE]: title,
      'http.route': route,
    };

    if (routeConfig) {
      this.enrichWithRouteConfig(attrs, routeConfig);
    }

    const startNano = nowNano() - BigInt(Math.round(loadTimeMs * 1e6));
    return this.buildSpan('page_load ' + route, 'INTERNAL', startNano, attrs);
  }

  /**
   * Creates a navigation span (SPA route change).
   */
  createNavigationSpan(fromRoute: string, toRoute: string, durationMs: number): InternalSpan {
    const routeConfig = this.matchRoute(toRoute);
    const attrs: Record<string, AttributeValue> = {
      ...this.resourceAttrs,
      [AgentTelClientAttributes.PAGE_ROUTE]: toRoute,
      'navigation.from': fromRoute,
      'navigation.to': toRoute,
    };

    if (routeConfig) {
      this.enrichWithRouteConfig(attrs, routeConfig);
    }

    const startNano = nowNano() - BigInt(Math.round(durationMs * 1e6));
    return this.buildSpan('navigate ' + toRoute, 'INTERNAL', startNano, attrs);
  }

  /**
   * Creates an API call span with backend correlation.
   */
  createApiSpan(params: {
    url: string;
    method: string;
    status: number;
    durationMs: number;
    backendTraceId?: string;
    parentSpanId?: string;
  }): InternalSpan {
    const pathname = extractPathname(params.url);
    const attrs: Record<string, AttributeValue> = {
      ...this.resourceAttrs,
      'http.method': params.method,
      'http.url': params.url,
      'http.status_code': params.status,
      [AgentTelClientAttributes.INTERACTION_TYPE]: 'api_call',
      [AgentTelClientAttributes.INTERACTION_RESPONSE_TIME_MS]: params.durationMs,
      [AgentTelClientAttributes.INTERACTION_OUTCOME]: params.status < 400 ? 'success' : 'error',
    };

    // Cross-stack correlation
    if (params.backendTraceId) {
      attrs[AgentTelClientAttributes.CORRELATION_BACKEND_TRACE_ID] = params.backendTraceId;
      attrs[AgentTelClientAttributes.CORRELATION_BACKEND_OPERATION] = `${params.method} ${pathname}`;
    }

    const startNano = nowNano() - BigInt(Math.round(params.durationMs * 1e6));
    const span = this.buildSpan(`fetch ${params.method} ${pathname}`, 'CLIENT', startNano, attrs);
    if (params.parentSpanId) {
      span.parentSpanId = params.parentSpanId;
    }
    if (params.status >= 400) {
      span.status = { code: 'ERROR', message: `HTTP ${params.status}` };
    }
    return span;
  }

  /**
   * Creates a user interaction span.
   */
  createInteractionSpan(params: {
    type: string;
    target: string;
    outcome: string;
    durationMs?: number;
    metadata?: Record<string, AttributeValue>;
  }): InternalSpan {
    const attrs: Record<string, AttributeValue> = {
      ...this.resourceAttrs,
      [AgentTelClientAttributes.INTERACTION_TYPE]: params.type,
      [AgentTelClientAttributes.INTERACTION_TARGET]: params.target,
      [AgentTelClientAttributes.INTERACTION_OUTCOME]: params.outcome,
    };

    if (params.durationMs !== undefined) {
      attrs[AgentTelClientAttributes.INTERACTION_RESPONSE_TIME_MS] = params.durationMs;
    }
    if (params.metadata) {
      Object.assign(attrs, params.metadata);
    }

    return this.buildSpan(`${params.type} ${params.target}`, 'INTERNAL', nowNano(), attrs);
  }

  /**
   * Creates an error span.
   */
  createErrorSpan(message: string, source: string, route?: string): InternalSpan {
    const attrs: Record<string, AttributeValue> = {
      ...this.resourceAttrs,
      'exception.message': message,
      'exception.source': source,
      [AgentTelClientAttributes.INTERACTION_TYPE]: 'error',
      [AgentTelClientAttributes.INTERACTION_OUTCOME]: 'error',
    };

    if (route) {
      attrs[AgentTelClientAttributes.PAGE_ROUTE] = route;
    }

    const span = this.buildSpan('error', 'INTERNAL', nowNano(), attrs);
    span.status = { code: 'ERROR', message };
    return span;
  }

  /**
   * Adds anomaly attributes to an existing span.
   */
  addAnomalyAttributes(span: InternalSpan, pattern: string, score: number): void {
    span.attributes[AgentTelClientAttributes.ANOMALY_DETECTED] = true;
    span.attributes[AgentTelClientAttributes.ANOMALY_PATTERN] = pattern;
    span.attributes[AgentTelClientAttributes.ANOMALY_SCORE] = score;
    span.events.push({
      name: 'anomaly.detected',
      timeUnixNano: nowNano(),
      attributes: { pattern, score },
    });
  }

  /**
   * Adds journey attributes to a span.
   */
  addJourneyAttributes(span: InternalSpan, journeyName: string, step: number, totalSteps: number, startedAt: string): void {
    span.attributes[AgentTelClientAttributes.JOURNEY_NAME] = journeyName;
    span.attributes[AgentTelClientAttributes.JOURNEY_STEP] = step;
    span.attributes[AgentTelClientAttributes.JOURNEY_TOTAL_STEPS] = totalSteps;
    span.attributes[AgentTelClientAttributes.JOURNEY_STARTED_AT] = startedAt;
  }

  private matchRoute(path: string): RouteConfig | undefined {
    if (!this.config.routes) return undefined;

    // Direct match first
    if (this.config.routes[path]) return this.config.routes[path];

    // Pattern match (e.g., "/checkout/:step" matches "/checkout/payment")
    for (const [pattern, config] of Object.entries(this.config.routes)) {
      const regex = patternToRegex(pattern);
      if (regex.test(path)) return config;
    }
    return undefined;
  }

  private enrichWithRouteConfig(attrs: Record<string, AttributeValue>, routeConfig: RouteConfig): void {
    if (routeConfig.businessCriticality) {
      attrs[AgentTelClientAttributes.PAGE_BUSINESS_CRITICALITY] = routeConfig.businessCriticality;
    }
    if (routeConfig.baseline) {
      const b = routeConfig.baseline;
      if (b.pageLoadP50Ms !== undefined) attrs[AgentTelClientAttributes.BASELINE_PAGE_LOAD_P50_MS] = b.pageLoadP50Ms;
      if (b.pageLoadP99Ms !== undefined) attrs[AgentTelClientAttributes.BASELINE_PAGE_LOAD_P99_MS] = b.pageLoadP99Ms;
      if (b.interactionErrorRate !== undefined) attrs[AgentTelClientAttributes.BASELINE_INTERACTION_ERROR_RATE] = b.interactionErrorRate;
      if (b.apiCallP50Ms !== undefined) attrs[AgentTelClientAttributes.BASELINE_API_CALL_P50_MS] = b.apiCallP50Ms;
      attrs[AgentTelClientAttributes.BASELINE_SOURCE] = 'static';
    }
    if (routeConfig.decision) {
      const d = routeConfig.decision;
      if (d.retryOnFailure !== undefined) attrs[AgentTelClientAttributes.DECISION_RETRY_ON_FAILURE] = d.retryOnFailure;
      if (d.fallbackPage) attrs[AgentTelClientAttributes.DECISION_FALLBACK_PAGE] = d.fallbackPage;
      if (d.escalationLevel) attrs[AgentTelClientAttributes.DECISION_ESCALATION_LEVEL] = d.escalationLevel;
      if (d.runbookUrl) attrs[AgentTelClientAttributes.DECISION_RUNBOOK_URL] = d.runbookUrl;
    }
  }

  private buildSpan(name: string, kind: SpanKind, startNano: bigint, attrs: Record<string, AttributeValue>): InternalSpan {
    return {
      traceId: this.currentTraceId,
      spanId: generateSpanId(),
      name,
      kind,
      startTimeUnixNano: startNano,
      endTimeUnixNano: nowNano(),
      attributes: attrs,
      events: [],
      status: { code: 'OK' },
    };
  }
}

function patternToRegex(pattern: string): RegExp {
  const escaped = pattern
    .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    .replace(/:\\w+/g, '[^/]+');
  return new RegExp(`^${escaped}$`);
}

function extractPathname(url: string): string {
  try {
    return new URL(url, window.location.origin).pathname;
  } catch {
    return url;
  }
}
