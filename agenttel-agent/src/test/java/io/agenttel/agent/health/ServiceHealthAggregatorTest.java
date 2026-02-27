package io.agenttel.agent.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ServiceHealthAggregatorTest {

    private ServiceHealthAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new ServiceHealthAggregator(null, null);
    }

    @Test
    void recordSpan_tracksOperationMetrics() {
        aggregator.recordSpan("GET /users", 50.0, false);
        aggregator.recordSpan("GET /users", 100.0, false);
        aggregator.recordSpan("GET /users", 150.0, true);

        var health = aggregator.getOperationHealth("GET /users");
        assertThat(health).isPresent();

        var op = health.get();
        assertThat(op.totalRequests()).isEqualTo(3);
        assertThat(op.errorCount()).isEqualTo(1);
        assertThat(op.errorRate()).isCloseTo(0.333, within(0.01));
        assertThat(op.latencyP50Ms()).isGreaterThan(0);
    }

    @Test
    void recordDependencyCall_tracksDependencyMetrics() {
        aggregator.recordDependencyCall("postgres", 10.0, false);
        aggregator.recordDependencyCall("postgres", 20.0, false);
        aggregator.recordDependencyCall("postgres", 30.0, true);

        var summary = aggregator.getHealthSummary("test-service");
        assertThat(summary.dependencies()).hasSize(1);

        var dep = summary.dependencies().get(0);
        assertThat(dep.name()).isEqualTo("postgres");
        assertThat(dep.totalCalls()).isEqualTo(3);
        assertThat(dep.errorCount()).isEqualTo(1);
    }

    @Test
    void getHealthSummary_returnsHealthyWhenNoIssues() {
        aggregator.recordSpan("GET /users", 50.0, false);

        var summary = aggregator.getHealthSummary("test-service");
        assertThat(summary.status()).isEqualTo(ServiceHealthAggregator.HealthStatus.HEALTHY);
        assertThat(summary.serviceName()).isEqualTo("test-service");
        assertThat(summary.operations()).hasSize(1);
    }

    @Test
    void getHealthSummary_returnsCriticalOnHighErrorRate() {
        // Need >100 requests with >10% error rate for CRITICAL
        for (int i = 0; i < 90; i++) {
            aggregator.recordSpan("GET /users", 50.0, false);
        }
        for (int i = 0; i < 15; i++) {
            aggregator.recordSpan("GET /users", 50.0, true);
        }

        var summary = aggregator.getHealthSummary("test-service");
        assertThat(summary.status()).isEqualTo(ServiceHealthAggregator.HealthStatus.CRITICAL);
    }

    @Test
    void getOperationHealth_returnsEmptyForUnknownOperation() {
        var health = aggregator.getOperationHealth("unknown");
        assertThat(health).isEmpty();
    }

    @Test
    void getHealthSummary_handlesEmptyState() {
        var summary = aggregator.getHealthSummary("test-service");
        assertThat(summary.status()).isEqualTo(ServiceHealthAggregator.HealthStatus.HEALTHY);
        assertThat(summary.operations()).isEmpty();
        assertThat(summary.dependencies()).isEmpty();
    }
}
