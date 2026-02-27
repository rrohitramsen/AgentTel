package io.agenttel.api.annotations;

import io.agenttel.api.DependencyCriticality;
import io.agenttel.api.DependencyType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a dependency of this service. Can be repeated to declare multiple dependencies.
 * Dependencies are serialized as JSON in the agenttel.topology.dependencies resource attribute.
 */
@Repeatable(DeclareDependencies.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeclareDependency {

    /** Dependency name (e.g., "payment-gateway", "postgres-orders"). */
    String name();

    /** Type of dependency. */
    DependencyType type();

    /** How critical this dependency is to the service. */
    DependencyCriticality criticality() default DependencyCriticality.REQUIRED;

    /** Protocol used to communicate (e.g., "https", "grpc", "jdbc"). */
    String protocol() default "";

    /** Timeout in milliseconds for calls to this dependency. 0 means unset. */
    int timeoutMs() default 0;

    /** Whether a circuit breaker protects calls to this dependency. */
    boolean circuitBreaker() default false;

    /** Fallback behavior description when dependency is unavailable. */
    String fallback() default "";

    /** Health check endpoint URL for this dependency. */
    String healthEndpoint() default "";
}
