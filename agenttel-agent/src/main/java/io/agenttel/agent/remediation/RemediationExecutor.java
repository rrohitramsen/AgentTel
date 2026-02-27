package io.agenttel.agent.remediation;

import io.agenttel.agent.action.AgentActionTracker;

import java.time.Instant;
import java.util.*;

/**
 * Executes remediation actions and tracks their outcomes.
 * Integrates with {@link AgentActionTracker} to record all
 * agent-initiated changes as observable telemetry.
 */
public class RemediationExecutor {

    private final RemediationRegistry registry;
    private final AgentActionTracker actionTracker;
    private final List<RemediationResult> executionHistory = Collections.synchronizedList(new ArrayList<>());

    public RemediationExecutor(RemediationRegistry registry, AgentActionTracker actionTracker) {
        this.registry = registry;
        this.actionTracker = actionTracker;
    }

    /**
     * Executes a remediation action by name.
     * Returns the result of the execution.
     */
    public RemediationResult execute(String actionName, String reason) {
        Optional<RemediationAction> action = registry.findAction(actionName);
        if (action.isEmpty()) {
            return new RemediationResult(actionName, false, "Action not found: " + actionName,
                    Instant.now().toString(), 0);
        }

        RemediationAction ra = action.get();
        if (ra.requiresApproval()) {
            return new RemediationResult(actionName, false,
                    "Action requires approval. Call executeApproved() after approval.",
                    Instant.now().toString(), 0);
        }

        return doExecute(ra, reason);
    }

    /**
     * Executes an action that has been approved.
     */
    public RemediationResult executeApproved(String actionName, String reason, String approvedBy) {
        Optional<RemediationAction> action = registry.findAction(actionName);
        if (action.isEmpty()) {
            return new RemediationResult(actionName, false, "Action not found: " + actionName,
                    Instant.now().toString(), 0);
        }

        return doExecute(action.get(), reason + " (approved by: " + approvedBy + ")");
    }

    private RemediationResult doExecute(RemediationAction action, String reason) {
        long startMs = System.currentTimeMillis();

        // Track the action via agent action tracker
        actionTracker.recordAction(
                "remediation:" + action.name(),
                action.description(),
                Map.of(
                        "action_type", action.type().name(),
                        "operation", action.operationName(),
                        "reason", reason
                )
        );

        // In a real implementation, this would dispatch to actual infrastructure.
        // For now, we record the intent.
        long durationMs = System.currentTimeMillis() - startMs;
        RemediationResult result = new RemediationResult(
                action.name(), true,
                "Action dispatched: " + action.description(),
                Instant.now().toString(), durationMs);

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
            long durationMs
    ) {}
}
