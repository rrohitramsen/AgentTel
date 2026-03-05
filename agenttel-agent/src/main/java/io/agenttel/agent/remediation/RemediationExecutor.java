package io.agenttel.agent.remediation;

import io.agenttel.agent.action.AgentActionTracker;
import io.agenttel.agent.identity.AgentIdentity;

import java.time.Instant;
import java.util.*;

/**
 * Executes remediation actions, tracks outcomes, and schedules verification.
 * Integrates with {@link AgentActionTracker} to record all agent-initiated changes
 * as observable telemetry and {@link ActionFeedbackLoop} to verify effectiveness.
 */
public class RemediationExecutor {

    private final RemediationRegistry registry;
    private final AgentActionTracker actionTracker;
    private final ActionFeedbackLoop feedbackLoop;
    private final List<RemediationResult> executionHistory = Collections.synchronizedList(new ArrayList<>());

    public RemediationExecutor(RemediationRegistry registry, AgentActionTracker actionTracker) {
        this(registry, actionTracker, null);
    }

    public RemediationExecutor(RemediationRegistry registry, AgentActionTracker actionTracker,
                                ActionFeedbackLoop feedbackLoop) {
        this.registry = registry;
        this.actionTracker = actionTracker;
        this.feedbackLoop = feedbackLoop;
    }

    /**
     * Executes a remediation action by name.
     * Returns the result of the execution.
     */
    public RemediationResult execute(String actionName, String reason) {
        return execute(actionName, reason, null);
    }

    /**
     * Executes a remediation action with agent identity tracking.
     */
    public RemediationResult execute(String actionName, String reason, AgentIdentity agent) {
        Optional<RemediationAction> action = registry.findAction(actionName);
        if (action.isEmpty()) {
            return new RemediationResult(actionName, false, "Action not found: " + actionName,
                    Instant.now().toString(), 0, false,
                    agent != null ? agent.agentId() : null);
        }

        RemediationAction ra = action.get();
        if (ra.requiresApproval()) {
            return new RemediationResult(actionName, false,
                    "Action requires approval. Call executeApproved() after approval.",
                    Instant.now().toString(), 0, false,
                    agent != null ? agent.agentId() : null);
        }

        return doExecute(ra, reason, agent);
    }

    /**
     * Executes an action that has been approved.
     */
    public RemediationResult executeApproved(String actionName, String reason, String approvedBy) {
        return executeApproved(actionName, reason, approvedBy, null);
    }

    /**
     * Executes an approved action with agent identity tracking.
     */
    public RemediationResult executeApproved(String actionName, String reason, String approvedBy,
                                              AgentIdentity agent) {
        Optional<RemediationAction> action = registry.findAction(actionName);
        if (action.isEmpty()) {
            return new RemediationResult(actionName, false, "Action not found: " + actionName,
                    Instant.now().toString(), 0, false,
                    agent != null ? agent.agentId() : null);
        }

        return doExecute(action.get(), reason + " (approved by: " + approvedBy + ")", agent);
    }

    /**
     * Returns the feedback outcome for a previously executed action.
     */
    public Optional<ActionFeedbackLoop.ActionOutcome> getActionOutcome(String actionName) {
        if (feedbackLoop == null) return Optional.empty();
        return feedbackLoop.getOutcome(actionName);
    }

    /**
     * Returns all recent feedback outcomes.
     */
    public List<ActionFeedbackLoop.ActionOutcome> getRecentOutcomes() {
        if (feedbackLoop == null) return List.of();
        return feedbackLoop.getRecentOutcomes();
    }

    private RemediationResult doExecute(RemediationAction action, String reason) {
        return doExecute(action, reason, null);
    }

    private RemediationResult doExecute(RemediationAction action, String reason, AgentIdentity agent) {
        long startMs = System.currentTimeMillis();

        // Track the action via agent action tracker with identity
        actionTracker.recordAction(
                "remediation:" + action.name(),
                action.description(),
                Map.of(
                        "action_type", action.type().name(),
                        "operation", action.operationName(),
                        "reason", reason
                ),
                agent
        );

        // In a real implementation, this would dispatch to actual infrastructure.
        // For now, we record the intent.
        long durationMs = System.currentTimeMillis() - startMs;

        // Schedule verification if feedback loop is available
        boolean verificationScheduled = false;
        if (feedbackLoop != null) {
            feedbackLoop.scheduleVerification(action.name());
            verificationScheduled = true;
        }

        RemediationResult result = new RemediationResult(
                action.name(), true,
                "Action dispatched: " + action.description(),
                Instant.now().toString(), durationMs, verificationScheduled,
                agent != null ? agent.agentId() : null);

        executionHistory.add(result);
        while (executionHistory.size() > 100) {
            executionHistory.remove(0);
        }

        return result;
    }

    /**
     * Returns recent execution history.
     */
    public List<RemediationResult> getHistory() {
        return new ArrayList<>(executionHistory);
    }

    public record RemediationResult(
            String actionName,
            boolean success,
            String message,
            String timestamp,
            long durationMs,
            boolean verificationScheduled,
            String agentId
    ) {}
}
