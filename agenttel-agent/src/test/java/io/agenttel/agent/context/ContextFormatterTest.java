package io.agenttel.agent.context;

import io.agenttel.agent.health.ServiceHealthAggregator;
import io.agenttel.agent.incident.IncidentContext;
import io.agenttel.core.anomaly.IncidentPattern;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ContextFormatterTest {

    @Test
    void formatHealthCompact_includesKeyMetrics() {
        var summary = new ServiceHealthAggregator.ServiceHealthSummary(
                "order-service",
                ServiceHealthAggregator.HealthStatus.DEGRADED,
                "2024-01-01T00:00:00Z",
                List.of(new ServiceHealthAggregator.OperationSummary(
                        "POST /orders", 1000, 50, 0.05,
                        150.0, 500.0, 200.0, 100.0, 300.0, "elevated")),
                List.of(new ServiceHealthAggregator.DependencySummary(
                        "postgres", 500, 10, 0.02, 15.0)),
                List.of()
        );

        String result = ContextFormatter.formatHealthCompact(summary);

        assertThat(result).contains("order-service");
        assertThat(result).contains("DEGRADED");
        assertThat(result).contains("POST /orders");
        assertThat(result).contains("5.0%"); // error rate
        assertThat(result).contains("150ms"); // p50
        assertThat(result).contains("[ELEVATED]");
        assertThat(result).contains("postgres");
    }

    @Test
    void formatIncidentFull_containsAllSections() {
        var incident = new IncidentContext(
                "inc-abc12345",
                "2024-01-01T00:00:00Z",
                IncidentContext.Severity.HIGH,
                "POST /orders experiencing elevated error rate",
                new IncidentContext.WhatIsHappening(
                        "POST /orders", "Elevated error rate",
                        List.of(IncidentPattern.ERROR_RATE_SPIKE),
                        0.05, 0.001, 150.0, 100.0, 0.8,
                        ServiceHealthAggregator.HealthStatus.DEGRADED),
                new IncidentContext.WhatChanged(
                        List.of(new IncidentContext.RecentChange("deployment", "v2.1.0", "2024-01-01T00:00:00Z")),
                        "v2.1.0", "2024-01-01T00:00:00Z"),
                new IncidentContext.WhatIsAffected(
                        List.of("POST /orders"), List.of("postgres"),
                        List.of("checkout-service"), "operation_specific", true),
                new IncidentContext.WhatToDo(
                        "", "page_oncall", true, true, "",
                        List.of(new IncidentContext.SuggestedAction(
                                "rollback", "Rollback to previous version", "high", true))),
                List.of()
        );

        String result = ContextFormatter.formatIncidentFull(incident);

        assertThat(result).contains("INCIDENT inc-abc12345");
        assertThat(result).contains("SEVERITY: HIGH");
        assertThat(result).contains("WHAT IS HAPPENING");
        assertThat(result).contains("ERROR_RATE_SPIKE");
        assertThat(result).contains("WHAT CHANGED");
        assertThat(result).contains("v2.1.0");
        assertThat(result).contains("WHAT IS AFFECTED");
        assertThat(result).contains("User-Facing: YES");
        assertThat(result).contains("SUGGESTED ACTIONS");
        assertThat(result).contains("rollback");
        assertThat(result).contains("NEEDS APPROVAL");
    }

    @Test
    void formatIncidentCompact_isBrief() {
        var incident = new IncidentContext(
                "inc-xyz",
                "2024-01-01T00:00:00Z",
                IncidentContext.Severity.CRITICAL,
                "Service is down",
                new IncidentContext.WhatIsHappening(
                        "GET /health", "Service down", List.of(),
                        0.50, 0.001, 5000.0, 100.0, 1.0,
                        ServiceHealthAggregator.HealthStatus.CRITICAL),
                new IncidentContext.WhatChanged(List.of(), "", ""),
                new IncidentContext.WhatIsAffected(List.of(), List.of(), List.of(), "service_wide", true),
                new IncidentContext.WhatToDo("", "page_oncall", false, false, "",
                        List.of(new IncidentContext.SuggestedAction("restart", "Restart", "high", true))),
                List.of()
        );

        String result = ContextFormatter.formatIncidentCompact(incident);

        assertThat(result).contains("[CRITICAL]");
        assertThat(result).contains("inc-xyz");
        assertThat(result).contains("restart");
        // Should be compact — just a few lines
        assertThat(result.split("\n").length).isLessThanOrEqualTo(5);
    }

    @Test
    void formatHealthAsJson_producesValidJsonStructure() {
        var summary = new ServiceHealthAggregator.ServiceHealthSummary(
                "my-service",
                ServiceHealthAggregator.HealthStatus.HEALTHY,
                "2024-01-01T00:00:00Z",
                List.of(new ServiceHealthAggregator.OperationSummary(
                        "GET /api", 100, 1, 0.01, 50.0, 200.0, 75.0,
                        null, null, "normal")),
                List.of(),
                List.of()
        );

        String json = ContextFormatter.formatHealthAsJson(summary);

        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}");
        assertThat(json).contains("\"service\":\"my-service\"");
        assertThat(json).contains("\"status\":\"HEALTHY\"");
        assertThat(json).contains("\"GET /api\"");
    }
}
