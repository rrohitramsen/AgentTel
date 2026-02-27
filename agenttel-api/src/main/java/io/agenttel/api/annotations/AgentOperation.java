package io.agenttel.api.annotations;

import io.agenttel.api.EscalationLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an agent-observable operation. Provides baseline expectations
 * and decision metadata that AgentTel attaches to the span for this operation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentOperation {

    /** Expected median latency (e.g., "45ms", "1s"). Empty means unset. */
    String expectedLatencyP50() default "";

    /** Expected p99 latency (e.g., "200ms", "3s"). Empty means unset. */
    String expectedLatencyP99() default "";

    /** Expected error rate (0.0-1.0). Negative means unset. */
    double expectedErrorRate() default -1.0;

    /** Whether this operation can be safely retried. */
    boolean retryable() default false;

    /** Whether this operation is idempotent. */
    boolean idempotent() default false;

    /** URL to the runbook for this operation. */
    String runbookUrl() default "";

    /** Description of fallback behavior if this operation fails. */
    String fallbackDescription() default "";

    /** Suggested escalation level when this operation fails. */
    EscalationLevel escalationLevel() default EscalationLevel.NOTIFY_TEAM;

    /** Whether the service can be safely restarted during this operation. */
    boolean safeToRestart() default true;
}
