package io.agenttel.api.events;

/**
 * Constants for AgentTel structured event names.
 */
public final class AgentTelEvents {
    private AgentTelEvents() {}

    public static final String DEPLOYMENT_INFO = "agenttel.deployment.info";
    public static final String ANOMALY_DETECTED = "agenttel.anomaly.detected";
    public static final String DEPENDENCY_STATE_CHANGE = "agenttel.dependency.state_change";
    public static final String SLO_BUDGET_ALERT = "agenttel.slo.budget_alert";
    public static final String CIRCUIT_BREAKER_STATE_CHANGE = "agenttel.circuit_breaker.state_change";
}
