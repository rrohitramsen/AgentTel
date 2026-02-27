package io.agenttel.spring.autoconfigure;

import io.agenttel.api.attributes.AgentTelAttributes;
import io.agenttel.core.baseline.BaselineProvider;
import io.agenttel.core.enrichment.OperationContext;
import io.agenttel.core.enrichment.OperationContextRegistry;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Spring MVC interceptor that enriches the current OTel span with baseline
 * and decision metadata once the route template is resolved.
 *
 * <p>This is necessary because the OTel span name is only set to the full
 * "HTTP_METHOD /route/template" format AFTER handler mapping resolution,
 * but the SpanProcessor's onStart() fires before that — when the span name
 * is just the HTTP method (e.g., "POST").
 */
public class AgentTelEnrichmentInterceptor implements HandlerInterceptor {

    private final BaselineProvider baselineProvider;
    private final OperationContextRegistry operationContexts;

    public AgentTelEnrichmentInterceptor(BaselineProvider baselineProvider,
                                          OperationContextRegistry operationContexts) {
        this.baselineProvider = baselineProvider;
        this.operationContexts = operationContexts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        Span span = Span.current();
        if (!span.getSpanContext().isValid()) {
            return;
        }

        String operationName = resolveOperationName(request);
        if (operationName == null) {
            return;
        }

        enrichWithBaseline(span, operationName);
        enrichWithDecisionMetadata(span, operationName);
    }

    private String resolveOperationName(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null) {
            return null;
        }
        return request.getMethod() + " " + pattern;
    }

    private void enrichWithBaseline(Span span, String operationName) {
        baselineProvider.getBaseline(operationName).ifPresent(baseline -> {
            if (baseline.latencyP50Ms() > 0) {
                span.setAttribute(AgentTelAttributes.BASELINE_LATENCY_P50_MS, baseline.latencyP50Ms());
            }
            if (baseline.latencyP99Ms() > 0) {
                span.setAttribute(AgentTelAttributes.BASELINE_LATENCY_P99_MS, baseline.latencyP99Ms());
            }
            if (baseline.errorRate() >= 0) {
                span.setAttribute(AgentTelAttributes.BASELINE_ERROR_RATE, baseline.errorRate());
            }
            span.setAttribute(AgentTelAttributes.BASELINE_SOURCE, baseline.source().getValue());
        });
    }

    private void enrichWithDecisionMetadata(Span span, String operationName) {
        operationContexts.getContext(operationName).ifPresent(ctx -> {
            span.setAttribute(AgentTelAttributes.DECISION_RETRYABLE, ctx.isRetryable());
            span.setAttribute(AgentTelAttributes.DECISION_IDEMPOTENT, ctx.isIdempotent());
            if (!ctx.getRunbookUrl().isEmpty()) {
                span.setAttribute(AgentTelAttributes.DECISION_RUNBOOK_URL, ctx.getRunbookUrl());
            }
            if (!ctx.getFallbackDescription().isEmpty()) {
                span.setAttribute(AgentTelAttributes.DECISION_FALLBACK_AVAILABLE, true);
                span.setAttribute(AgentTelAttributes.DECISION_FALLBACK_DESCRIPTION, ctx.getFallbackDescription());
            }
            span.setAttribute(AgentTelAttributes.DECISION_ESCALATION_LEVEL,
                    ctx.getEscalationLevel().getValue());
            span.setAttribute(AgentTelAttributes.DECISION_SAFE_TO_RESTART, ctx.isSafeToRestart());
        });
    }
}
