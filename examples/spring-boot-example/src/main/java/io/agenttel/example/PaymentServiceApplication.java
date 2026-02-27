package io.agenttel.example;

import io.agenttel.api.ConsumptionPattern;
import io.agenttel.api.DependencyCriticality;
import io.agenttel.api.DependencyType;
import io.agenttel.api.ServiceTier;
import io.agenttel.api.annotations.AgentObservable;
import io.agenttel.api.annotations.DeclareConsumer;
import io.agenttel.api.annotations.DeclareDependency;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@AgentObservable(
        service = "payment-service",
        team = "payments-platform",
        tier = ServiceTier.CRITICAL,
        domain = "commerce",
        onCallChannel = "#payments-oncall"
)
@DeclareDependency(
        name = "payment-gateway",
        type = DependencyType.EXTERNAL_API,
        criticality = DependencyCriticality.REQUIRED,
        timeoutMs = 5000,
        circuitBreaker = true
)
@DeclareDependency(
        name = "postgres-orders",
        type = DependencyType.DATABASE,
        criticality = DependencyCriticality.REQUIRED
)
@DeclareConsumer(
        name = "notification-service",
        pattern = ConsumptionPattern.ASYNC
)
@SpringBootApplication
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
