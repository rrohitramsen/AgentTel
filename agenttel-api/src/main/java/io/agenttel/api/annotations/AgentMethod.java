package io.agenttel.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an agent entry point. The method body is automatically
 * wrapped in an {@code AgentInvocation} scope by the AgentMethodAspect.
 *
 * <p>YAML configuration takes priority over annotation values. Use this
 * annotation for IDE autocomplete and compile-time defaults when YAML
 * config is not provided.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentMethod {

    /** Agent name. Empty means derive from class + method name. */
    String name() default "";

    /** Agent type (e.g., "single", "react", "router"). Empty means unset. */
    String type() default "";

    /** Framework name (e.g., "langchain4j", "spring-ai"). Empty means unset. */
    String framework() default "";

    /** Agent version. Empty means unset. */
    String version() default "";

    /** Maximum steps guardrail. 0 means unlimited. */
    long maxSteps() default 0;

    /** Loop detection threshold. 0 means use default (3). */
    int loopThreshold() default 0;
}
