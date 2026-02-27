package io.agenttel.core.slo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SloTrackerTest {

    private SloTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new SloTracker();
        tracker.register(SloDefinition.builder("payment-availability")
                .operationName("POST /api/payments")
                .type(SloDefinition.SloType.AVAILABILITY)
                .target(0.999)
                .build());
    }

    @Test
    void initialStatusShowsFullBudget() {
        SloTracker.SloStatus status = tracker.getStatus("payment-availability");
        assertThat(status).isNotNull();
        assertThat(status.budgetRemaining()).isEqualTo(1.0);
        assertThat(status.totalRequests()).isZero();
    }

    @Test
    void allSuccessfulRequestsMaintainBudget() {
        for (int i = 0; i < 100; i++) {
            tracker.recordSuccess("POST /api/payments");
        }

        SloTracker.SloStatus status = tracker.getStatus("payment-availability");
        assertThat(status.actual()).isEqualTo(1.0);
        assertThat(status.budgetRemaining()).isEqualTo(1.0);
        assertThat(status.isWithinBudget()).isTrue();
    }

    @Test
    void failuresConsumeBudget() {
        // 99.9% SLO with 1000 requests, 1 failure = 0.1% error rate
        // Error budget is 0.1%, consuming exactly 100% of budget
        for (int i = 0; i < 999; i++) {
            tracker.recordSuccess("POST /api/payments");
        }
        tracker.recordFailure("POST /api/payments");

        SloTracker.SloStatus status = tracker.getStatus("payment-availability");
        assertThat(status.totalRequests()).isEqualTo(1000);
        assertThat(status.failedRequests()).isEqualTo(1);
        assertThat(status.actual()).isCloseTo(0.999, within(0.001));
        assertThat(status.budgetRemaining()).isCloseTo(0.0, within(0.01));
    }

    @Test
    void excessiveFailuresExhaustBudget() {
        for (int i = 0; i < 90; i++) {
            tracker.recordSuccess("POST /api/payments");
        }
        for (int i = 0; i < 10; i++) {
            tracker.recordFailure("POST /api/payments");
        }

        SloTracker.SloStatus status = tracker.getStatus("payment-availability");
        assertThat(status.budgetRemaining()).isEqualTo(0.0);
        assertThat(status.isWithinBudget()).isFalse();
    }

    @Test
    void alertsTriggeredAtThresholds() {
        // 99.9% SLO, error budget = 0.1%
        // To trigger warning (<=25% remaining), need to consume >75% of budget
        for (int i = 0; i < 998; i++) {
            tracker.recordSuccess("POST /api/payments");
        }
        tracker.recordFailure("POST /api/payments");
        tracker.recordFailure("POST /api/payments");

        List<SloTracker.SloAlert> alerts = tracker.checkAlerts();
        assertThat(alerts).isNotEmpty();
        assertThat(alerts.get(0).sloName()).isEqualTo("payment-availability");
    }

    @Test
    void noAlertsWhenWithinBudget() {
        for (int i = 0; i < 1000; i++) {
            tracker.recordSuccess("POST /api/payments");
        }

        List<SloTracker.SloAlert> alerts = tracker.checkAlerts();
        assertThat(alerts).isEmpty();
    }

    @Test
    void ignoresUnrelatedOperations() {
        tracker.recordSuccess("GET /api/users");
        tracker.recordFailure("GET /api/users");

        SloTracker.SloStatus status = tracker.getStatus("payment-availability");
        assertThat(status.totalRequests()).isZero();
    }

    @Test
    void multipleSloCsTrackedIndependently() {
        tracker.register(SloDefinition.builder("user-availability")
                .operationName("GET /api/users")
                .type(SloDefinition.SloType.AVAILABILITY)
                .target(0.99)
                .build());

        tracker.recordSuccess("POST /api/payments");
        tracker.recordFailure("GET /api/users");

        SloTracker.SloStatus paymentStatus = tracker.getStatus("payment-availability");
        SloTracker.SloStatus userStatus = tracker.getStatus("user-availability");

        assertThat(paymentStatus.actual()).isEqualTo(1.0);
        assertThat(userStatus.actual()).isEqualTo(0.0);
    }
}
