package io.agenttel.spring.autoconfigure;

import io.agenttel.core.engine.AgentTelEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Emits an {@code agenttel.deployment.info} event when the application is ready.
 */
@Component
public class AgentTelDeploymentEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(AgentTelDeploymentEventListener.class);

    private final AgentTelEngine engine;
    private final AgentTelProperties properties;

    public AgentTelDeploymentEventListener(AgentTelEngine engine, AgentTelProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.getDeployment().isEmitOnStartup()) {
            return;
        }

        String version = resolveVersion();
        String commitSha = properties.getDeployment().getCommitSha();

        engine.deploymentEvents().emitDeploymentEvent(version, commitSha, null, null);
        LOG.info("AgentTel: Emitted deployment event (version={})", version);
    }

    private String resolveVersion() {
        String version = properties.getDeployment().getVersion();
        if (!version.isEmpty()) {
            return version;
        }

        // Try to get from manifest
        Package pkg = getClass().getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }

        return "unknown";
    }
}
