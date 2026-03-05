package io.agenttel.example;

import io.agenttel.agent.correlation.ChangeCorrelationEngine;
import io.agenttel.agent.incident.IncidentContextBuilder;
import io.agenttel.agent.remediation.ActionSpec;
import io.agenttel.agent.remediation.RemediationAction;
import io.agenttel.agent.remediation.RemediationRegistry;
import io.agenttel.core.slo.SloDefinition;
import io.agenttel.core.slo.SloTracker;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Demo configuration that registers sample remediation actions, SLO definitions,
 * parameterized action specs, and change events so the MCP server returns
 * meaningful output for agent-autonomous workflows.
 */
@Configuration
public class McpDemoConfiguration {

    @Bean
    ApplicationRunner registerDemoRemediations(RemediationRegistry registry,
                                                SloTracker sloTracker,
                                                IncidentContextBuilder incidentContextBuilder,
                                                ChangeCorrelationEngine changeCorrelationEngine) {
        return args -> {
            // --- Remediation actions with parameterized specs ---

            registry.register(RemediationAction.builder("toggle-payment-gateway-circuit-breaker", "POST /api/payments")
                    .description("Toggle circuit breaker on payment gateway dependency")
                    .type(RemediationAction.ActionType.CIRCUIT_BREAKER)
                    .requiresApproval(false)
                    .spec(new ActionSpec.CircuitBreakerSpec(5, 30000, 3))
                    .build());

            registry.register(RemediationAction.builder("retry-payment-with-backoff", "POST /api/payments")
                    .description("Retry failed payment with exponential backoff")
                    .type(RemediationAction.ActionType.CUSTOM)
                    .requiresApproval(false)
                    .spec(new ActionSpec.RetrySpec(3, List.of(100L, 200L, 400L),
                            List.of(502, 503), List.of("SocketTimeoutException"),
                            List.of(400, 401)))
                    .build());

            registry.register(RemediationAction.builder("flush-payment-cache", "POST /api/payments")
                    .description("Flush cached payment gateway pricing data")
                    .type(RemediationAction.ActionType.CACHE_FLUSH)
                    .requiresApproval(false)
                    .build());

            registry.register(RemediationAction.builder("rollback-payment-service", "POST /api/payments")
                    .description("Rollback payment service to previous version")
                    .type(RemediationAction.ActionType.ROLLBACK)
                    .requiresApproval(true)
                    .build());

            registry.register(RemediationAction.builder("scale-payment-service", "POST /api/payments")
                    .description("Scale payment service horizontally")
                    .type(RemediationAction.ActionType.SCALE)
                    .requiresApproval(true)
                    .spec(new ActionSpec.ScaleSpec("up", 2, 10, 300))
                    .build());

            // Global remediation actions
            registry.registerGlobal(RemediationAction.builder("restart-service", "*")
                    .description("Rolling restart of service instances")
                    .type(RemediationAction.ActionType.RESTART)
                    .requiresApproval(true)
                    .build());

            registry.registerGlobal(RemediationAction.builder("rate-limit-traffic", "*")
                    .description("Apply rate limiting to reduce load")
                    .type(RemediationAction.ActionType.RATE_LIMIT)
                    .requiresApproval(false)
                    .spec(new ActionSpec.RateLimitSpec(100, 20))
                    .build());

            // --- SLO definitions ---

            sloTracker.register(SloDefinition.builder("payment-availability")
                    .operationName("POST /api/payments")
                    .type(SloDefinition.SloType.AVAILABILITY)
                    .target(0.999)
                    .build());

            sloTracker.register(SloDefinition.builder("payment-latency-p99")
                    .operationName("POST /api/payments")
                    .type(SloDefinition.SloType.LATENCY_P99)
                    .target(200.0)
                    .build());

            // --- Sample change events for correlation demo ---

            incidentContextBuilder.recordDeployment("v2.1.0", "2025-01-15T14:00:00Z");
            changeCorrelationEngine.recordConfigChange(
                    "config-ratelimit", "Updated rate limit from 200 to 500 rps");
        };
    }
}
