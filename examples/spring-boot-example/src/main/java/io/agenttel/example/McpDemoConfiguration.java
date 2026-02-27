package io.agenttel.example;

import io.agenttel.agent.remediation.RemediationAction;
import io.agenttel.agent.remediation.RemediationRegistry;
import io.agenttel.core.slo.SloDefinition;
import io.agenttel.core.slo.SloTracker;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Demo configuration that registers sample remediation actions and SLO definitions
 * so the MCP server returns meaningful output.
 */
@Configuration
public class McpDemoConfiguration {

    @Bean
    ApplicationRunner registerDemoRemediations(RemediationRegistry registry, SloTracker sloTracker) {
        return args -> {
            // Remediation actions for payment processing
            registry.register(RemediationAction.builder("toggle-payment-gateway-circuit-breaker", "POST /api/payments")
                    .description("Toggle circuit breaker on payment gateway dependency")
                    .type(RemediationAction.ActionType.CIRCUIT_BREAKER)
                    .requiresApproval(false)
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

            // Global remediation action
            registry.registerGlobal(RemediationAction.builder("restart-service", "*")
                    .description("Rolling restart of service instances")
                    .type(RemediationAction.ActionType.RESTART)
                    .requiresApproval(true)
                    .build());

            // SLO definitions
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
        };
    }
}
