package io.agenttel.api.annotations;

import io.agenttel.api.ConsumptionPattern;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a consumer of this service — another service that depends on this one.
 * Can be repeated. Serialized in the agenttel.topology.consumers resource attribute.
 */
@Repeatable(DeclareConsumers.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeclareConsumer {

    /** Consumer service name. */
    String name();

    /** How the consumer calls this service. */
    ConsumptionPattern pattern() default ConsumptionPattern.SYNC;

    /** SLA latency in milliseconds the consumer expects. 0 means unset. */
    int slaLatencyMs() default 0;
}
