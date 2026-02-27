package io.agenttel.agent.remediation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of available remediation actions per operation.
 * Agents query this to discover what automated fixes are available.
 */
public class RemediationRegistry {

    private final ConcurrentHashMap<String, List<RemediationAction>> actionsByOperation = new ConcurrentHashMap<>();
    private final List<RemediationAction> globalActions = Collections.synchronizedList(new ArrayList<>());

    /**
     * Registers a remediation action for a specific operation.
     */
    public void register(RemediationAction action) {
        actionsByOperation.computeIfAbsent(action.operationName(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(action);
    }

    /**
     * Registers a global remediation action that applies to all operations.
     */
    public void registerGlobal(RemediationAction action) {
        globalActions.add(action);
    }

    /**
     * Returns all remediation actions applicable to the given operation,
     * including global actions.
     */
    public List<RemediationAction> getActions(String operationName) {
        List<RemediationAction> result = new ArrayList<>(globalActions);
        List<RemediationAction> opActions = actionsByOperation.get(operationName);
        if (opActions != null) {
            result.addAll(opActions);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns all registered actions across all operations.
     */
    public List<RemediationAction> getAllActions() {
        List<RemediationAction> result = new ArrayList<>(globalActions);
        for (List<RemediationAction> actions : actionsByOperation.values()) {
            result.addAll(actions);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Finds a specific action by name.
     */
    public Optional<RemediationAction> findAction(String actionName) {
        for (RemediationAction action : globalActions) {
            if (action.name().equals(actionName)) return Optional.of(action);
        }
        for (List<RemediationAction> actions : actionsByOperation.values()) {
            for (RemediationAction action : actions) {
                if (action.name().equals(actionName)) return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
