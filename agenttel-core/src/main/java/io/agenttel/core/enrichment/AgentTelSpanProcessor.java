package io.agenttel.core.enrichment;

import io.agenttel.api.attributes.AgentTelAttributes;
import io.agenttel.api.events.AgentTelEvents;
import io.agenttel.core.anomaly.AnomalyDetector;
import io.agenttel.core.anomaly.AnomalyResult;
import io.agenttel.core.anomaly.IncidentPattern;
import io.agenttel.core.anomaly.PatternMatcher;
import io.agenttel.core.baseline.BaselineProvider;
import io.agenttel.core.baseline.RollingBaselineProvider;
import io.agenttel.core.baseline.RollingWindow;
import io.agenttel.core.events.AgentTelEventEmitter;
import io.agenttel.core.slo.SloTracker;
import io.agenttel.core.topology.TopologyRegistry;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OTel SpanProcessor that enriches spans with AgentTel attributes.
 *
 * <p>On span start (onStart): attaches topology, baselines, and decision metadata.
 * On span end (onEnd): performs anomaly detection, pattern matching, SLO tracking,
 * and emits structured events for detected anomalies and SLO budget alerts.
 */
public class AgentTelSpanProcessor implements SpanProcessor {

    /**
     * Callback for span completion events. Used to feed health aggregation
     * without creating a dependency from core to the agent module.
     */
    @FunctionalInterface
    public interface SpanCompletionListener {
        void onSpanCompleted(String operationName, double latencyMs, boolean isError);
    }

    private final TopologyRegistry topology;
    private final BaselineProvider baselineProvider;
    private final OperationContextRegistry operationContexts;
    private final AnomalyDetector anomalyDetector;
    private final PatternMatcher patternMatcher;
    private final RollingBaselineProvider rollingBaselines;
    private final SloTracker sloTracker;
    private final AgentTelEventEmitter eventEmitter;
    private volatile SpanCompletionListener spanCompletionListener;

    public AgentTelSpanProcessor(TopologyRegistry topology,
                                  BaselineProvider baselineProvider,
                                  OperationContextRegistry operationContexts) {
        this(topology, baselineProvider, operationContexts, null, null, null, null, null);
    }

    public AgentTelSpanProcessor(TopologyRegistry topology,
                                  BaselineProvider baselineProvider,
                                  OperationContextRegistry operationContexts,
                                  AnomalyDetector anomalyDetector,
                                  PatternMatcher patternMatcher,
                                  RollingBaselineProvider rollingBaselines,
                                  SloTracker sloTracker,
                                  AgentTelEventEmitter eventEmitter) {
        this.topology = topology;
        this.baselineProvider = baselineProvider;
        this.operationContexts = operationContexts;
        this.anomalyDetector = anomalyDetector;
        this.patternMatcher = patternMatcher;
        this.rollingBaselines = rollingBaselines;
        this.sloTracker = sloTracker;
        this.eventEmitter = eventEmitter;
    }

    /**
     * Sets a listener that will be notified on every span completion.
     * Used by the agent module to feed ServiceHealthAggregator.
     */
    public void setSpanCompletionListener(SpanCompletionListener listener) {
        this.spanCompletionListener = listener;
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // 1. Attach topology attributes
        enrichWithTopology(span);

        // 2. Attach baselines if available
        String operationName = span.getName();
        enrichWithBaseline(span, operationName);

        // 3. Attach decision metadata if available
        enrichWithDecisionMetadata(span, operationName);
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        String operationName = span.getName();
        long durationNanos = span.getLatencyNanos();
        double latencyMs = (double) durationNanos / TimeUnit.MILLISECONDS.toNanos(1);
        boolean isError = span.toSpanData().getStatus().getStatusCode() == StatusCode.ERROR;

        // Feed rolling baselines
        if (rollingBaselines != null) {
            if (isError) {
                rollingBaselines.recordError(operationName);
            } else {
                rollingBaselines.recordLatency(operationName, latencyMs);
            }
        }

        // Notify health aggregation listener
        SpanCompletionListener listener = this.spanCompletionListener;
        if (listener != null) {
            listener.onSpanCompleted(operationName, latencyMs, isError);
        }

        // SLO tracking
        if (sloTracker != null) {
            if (isError) {
                sloTracker.recordFailure(operationName);
            } else {
                sloTracker.recordSuccess(operationName);
            }

            // Check and emit SLO budget alerts
            if (eventEmitter != null) {
                emitSloAlerts();
            }
        }

        // Anomaly detection + pattern matching
        if (anomalyDetector != null && rollingBaselines != null) {
            rollingBaselines.getSnapshot(operationName).ifPresent(snapshot -> {
                // Z-score anomaly detection
                if (snapshot.stddev() > 0) {
                    AnomalyResult result = anomalyDetector.evaluate(
                            "latency", latencyMs, snapshot.mean(), snapshot.stddev());
                    if (result.isAnomaly() && eventEmitter != null) {
                        emitAnomalyEvent(operationName, latencyMs, result, null);
                    }
                }
            });
        }

        // Pattern matching
        if (patternMatcher != null && rollingBaselines != null) {
            patternMatcher.recordLatency(operationName, latencyMs);
            RollingWindow.Snapshot snapshot = rollingBaselines.getSnapshot(operationName)
                    .orElse(null);
            List<IncidentPattern> patterns = patternMatcher.detectPatterns(
                    operationName, latencyMs, isError, snapshot);

            if (!patterns.isEmpty() && eventEmitter != null) {
                for (IncidentPattern pattern : patterns) {
                    emitAnomalyEvent(operationName, latencyMs, null, pattern);
                }
            }
        }
    }

    @Override
    public boolean isEndRequired() {
        return anomalyDetector != null || rollingBaselines != null || sloTracker != null || spanCompletionListener != null;
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode forceFlush() {
        return CompletableResultCode.ofSuccess();
    }

    private void emitAnomalyEvent(String operationName, double latencyMs,
                                   AnomalyResult anomalyResult, IncidentPattern pattern) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operation", operationName);
        body.put("latency_ms", latencyMs);

        if (anomalyResult != null) {
            body.put("anomaly_score", anomalyResult.anomalyScore());
            body.put("z_score", anomalyResult.zScore());
        }
        if (pattern != null) {
            body.put("pattern", pattern.getValue());
            body.put("pattern_description", pattern.getDescription());
        }

        eventEmitter.emitEvent(AgentTelEvents.ANOMALY_DETECTED, body, Severity.WARN);
    }

    private void emitSloAlerts() {
        for (SloTracker.SloAlert alert : sloTracker.checkAlerts()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("slo_name", alert.sloName());
            body.put("severity", alert.severity().name());
            body.put("budget_remaining", alert.budgetRemaining());
            body.put("burn_rate", alert.burnRate());

            Severity severity = switch (alert.severity()) {
                case CRITICAL -> Severity.ERROR;
                case WARNING -> Severity.WARN;
                case INFO -> Severity.INFO;
            };

            eventEmitter.emitEvent(AgentTelEvents.SLO_BUDGET_ALERT, body, severity);
        }
    }

    private void enrichWithTopology(ReadWriteSpan span) {
        if (!topology.getTeam().isEmpty()) {
            span.setAttribute(AgentTelAttributes.TOPOLOGY_TEAM, topology.getTeam());
        }
        span.setAttribute(AgentTelAttributes.TOPOLOGY_TIER, topology.getTier().getValue());
        if (!topology.getDomain().isEmpty()) {
            span.setAttribute(AgentTelAttributes.TOPOLOGY_DOMAIN, topology.getDomain());
        }
        if (!topology.getOnCallChannel().isEmpty()) {
            span.setAttribute(AgentTelAttributes.TOPOLOGY_ON_CALL_CHANNEL, topology.getOnCallChannel());
        }
    }

    private void enrichWithBaseline(ReadWriteSpan span, String operationName) {
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

    private void enrichWithDecisionMetadata(ReadWriteSpan span, String operationName) {
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
