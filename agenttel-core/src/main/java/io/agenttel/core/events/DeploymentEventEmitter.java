package io.agenttel.core.events;

import io.agenttel.api.events.AgentTelEvents;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits the {@code agenttel.deployment.info} event on application startup.
 */
public class DeploymentEventEmitter {

    private final AgentTelEventEmitter eventEmitter;

    public DeploymentEventEmitter(AgentTelEventEmitter eventEmitter) {
        this.eventEmitter = eventEmitter;
    }

    /**
     * Emits a deployment info event with the given metadata.
     */
    public void emitDeploymentEvent(String version, String commitSha,
                                     String previousVersion, String strategy) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (version != null && !version.isEmpty()) {
            body.put("version", version);
        }
        if (commitSha != null && !commitSha.isEmpty()) {
            body.put("commit_sha", commitSha);
        }
        if (previousVersion != null && !previousVersion.isEmpty()) {
            body.put("previous_version", previousVersion);
        }
        if (strategy != null && !strategy.isEmpty()) {
            body.put("strategy", strategy);
        }
        body.put("timestamp", Instant.now().toString());

        eventEmitter.emitEvent(AgentTelEvents.DEPLOYMENT_INFO, body);
    }
}
