package io.agenttel.api.annotations;

import io.agenttel.api.ServiceTier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class (typically a Spring Boot application or service) as agent-observable.
 * Provides topology metadata that AgentTel attaches to all telemetry emitted by this service.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentObservable {

    /** Service name. Overrides OTel service.name if set. */
    String service() default "";

    /** Owning team name. Maps to agenttel.topology.team. */
    String team() default "";

    /** Service criticality tier. Maps to agenttel.topology.tier. */
    ServiceTier tier() default ServiceTier.STANDARD;

    /** Business domain. Maps to agenttel.topology.domain. */
    String domain() default "";

    /** Escalation channel. Maps to agenttel.topology.on_call_channel. */
    String onCallChannel() default "";

    /** Source repository URL. Maps to agenttel.topology.repo_url. */
    String repoUrl() default "";
}
