package io.agenttel.agent.action;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

/**
 * Tracks AI agent actions as OpenTelemetry spans and events.
 * Every decision and action an agent takes becomes observable telemetry,
 * enabling auditability and debugging of autonomous behavior.
 */
public class AgentActionTracker {

    private static final AttributeKey<String> AGENT_ACTION_NAME = AttributeKey.stringKey("agenttel.agent.action.name");
    private static final AttributeKey<String> AGENT_ACTION_REASON = AttributeKey.stringKey("agenttel.agent.action.reason");
    private static final AttributeKey<String> AGENT_ACTION_STATUS = AttributeKey.stringKey("agenttel.agent.action.status");
    private static final AttributeKey<String> AGENT_ACTION_TYPE = AttributeKey.stringKey("agenttel.agent.action.type");
    private static final AttributeKey<String> AGENT_DECISION_RATIONALE = AttributeKey.stringKey("agenttel.agent.decision.rationale");

    private final Tracer tracer;
    private final Deque<ActionRecord> recentActions = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY = 200;

    public AgentActionTracker(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("io.agenttel.agent");
    }

    /**
     * Records a simple agent action (fire-and-forget).
     */
    public void recordAction(String actionName, String reason, Map<String, String> metadata) {
        Span span = tracer.spanBuilder("agent.action:" + actionName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AGENT_ACTION_NAME, actionName)
                .setAttribute(AGENT_ACTION_REASON, reason)
                .setAttribute(AGENT_ACTION_TYPE, "action")
                .startSpan();

        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            span.setAttribute(AttributeKey.stringKey("agenttel.agent.metadata." + entry.getKey()), entry.getValue());
        }

        span.setAttribute(AGENT_ACTION_STATUS, "completed");
        span.end();

        addToHistory(actionName, "action", reason, "completed");
    }

    /**
     * Records an agent decision with rationale.
     */
    public void recordDecision(String decisionName, String rationale, String chosenOption,
                                List<String> consideredOptions) {
        Span span = tracer.spanBuilder("agent.decision:" + decisionName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AGENT_ACTION_NAME, decisionName)
                .setAttribute(AGENT_ACTION_TYPE, "decision")
                .setAttribute(AGENT_DECISION_RATIONALE, rationale)
                .setAttribute(AttributeKey.stringKey("agenttel.agent.decision.chosen"), chosenOption)
                .setAttribute(AttributeKey.stringArrayKey("agenttel.agent.decision.options"),
                        consideredOptions)
                .startSpan();

        span.setAttribute(AGENT_ACTION_STATUS, "completed");
        span.end();

        addToHistory(decisionName, "decision", rationale, "completed");
    }

    /**
     * Wraps an agent action in a traced span, capturing success or failure.
     */
    public <T> T traceAction(String actionName, String reason, Supplier<T> action) {
        Span span = tracer.spanBuilder("agent.action:" + actionName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AGENT_ACTION_NAME, actionName)
                .setAttribute(AGENT_ACTION_REASON, reason)
                .setAttribute(AGENT_ACTION_TYPE, "traced_action")
                .startSpan();

        try {
            T result = action.get();
            span.setAttribute(AGENT_ACTION_STATUS, "success");
            span.setStatus(StatusCode.OK);
            addToHistory(actionName, "traced_action", reason, "success");
            return result;
        } catch (Exception e) {
            span.setAttribute(AGENT_ACTION_STATUS, "failed");
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            addToHistory(actionName, "traced_action", reason, "failed");
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Wraps a void agent action in a traced span.
     */
    public void traceAction(String actionName, String reason, Runnable action) {
        traceAction(actionName, reason, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Returns the recent action history for context building.
     */
    public List<ActionRecord> getRecentActions() {
        return new ArrayList<>(recentActions);
    }

    /**
     * Returns only actions of a specific type.
     */
    public List<ActionRecord> getActionsByType(String type) {
        List<ActionRecord> result = new ArrayList<>();
        for (ActionRecord record : recentActions) {
            if (type.equals(record.type())) {
                result.add(record);
            }
        }
        return result;
    }

    private void addToHistory(String name, String type, String reason, String status) {
        recentActions.addLast(new ActionRecord(name, type, reason, status, Instant.now().toString()));
        while (recentActions.size() > MAX_HISTORY) {
            recentActions.pollFirst();
        }
    }

    public record ActionRecord(
            String name,
            String type,
            String reason,
            String status,
            String timestamp
    ) {}
}
