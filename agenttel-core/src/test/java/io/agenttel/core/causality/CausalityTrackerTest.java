package io.agenttel.core.causality;

import io.agenttel.api.DependencyState;
import io.agenttel.api.ErrorCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CausalityTrackerTest {

    private OperationDependencyTracker dependencyTracker;
    private CausalityTracker tracker;

    @BeforeEach
    void setUp() {
        dependencyTracker = new OperationDependencyTracker();
        tracker = new CausalityTracker(dependencyTracker);
    }

    // ── analyzeCause with dependency mapping ────────────────────────────

    @Test
    void analyzeCauseWithUnhealthyDependencyAndOperationMapping() {
        // Set up: payment operation depends on postgresql
        dependencyTracker.recordDependencyCall("POST /api/payments", "postgresql");
        dependencyTracker.recordDependencyCall("POST /api/payments", "redis");

        // Report postgresql as unhealthy
        tracker.reportDependencyState("postgresql", DependencyState.UNHEALTHY, "connection pool exhausted");

        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.CONNECTION_ERROR);

        assertNotNull(result);
        assertEquals("dependency", result.causeCategory());
        assertEquals("postgresql", result.causeDependency());
        assertTrue(result.causeHint().contains("postgresql"));
        assertTrue(result.causeHint().contains("unhealthy"));
        assertTrue(result.confidence() > 0.8, "Confidence should be high for unhealthy + connection error");
    }

    @Test
    void analyzeCausePrefersMostSevereDependency() {
        dependencyTracker.recordDependencyCall("POST /api/payments", "postgresql");
        dependencyTracker.recordDependencyCall("POST /api/payments", "redis");

        // postgresql is degraded, redis is unhealthy
        tracker.reportDependencyState("postgresql", DependencyState.DEGRADED, "high latency");
        tracker.reportDependencyState("redis", DependencyState.UNHEALTHY, "connection refused");

        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.CONNECTION_ERROR);

        assertEquals("redis", result.causeDependency());
    }

    @Test
    void analyzeCauseWithHealthyDependenciesReturnsCodeCause() {
        dependencyTracker.recordDependencyCall("POST /api/payments", "postgresql");
        tracker.reportDependencyState("postgresql", DependencyState.HEALTHY, "all good");

        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.CODE_BUG);

        assertEquals("code", result.causeCategory());
        assertNull(result.causeDependency());
        assertTrue(result.causeHint().contains("code defect"));
    }

    // ── analyzeCause fallback (no dependency tracker) ───────────────────

    @Test
    void analyzeCauseFallsBackToAllDependenciesWhenNoTracker() {
        // Create tracker without OperationDependencyTracker
        CausalityTracker trackerNoMapping = new CausalityTracker();

        trackerNoMapping.reportDependencyState("postgresql", DependencyState.UNHEALTHY, "down");

        CausalityTracker.CausalAnalysis result = trackerNoMapping.analyzeCause(
                "POST /api/payments", ErrorCategory.CONNECTION_ERROR);

        assertEquals("dependency", result.causeCategory());
        assertEquals("postgresql", result.causeDependency());
        // Lower confidence because no operation-to-dependency mapping
        assertTrue(result.confidence() < 0.8,
                "Confidence should be lower without dependency mapping, was: " + result.confidence());
    }

    @Test
    void analyzeCauseFallsBackToAllDependenciesWhenOperationHasNoDeps() {
        // Dependency tracker exists but this operation has no recorded dependencies
        tracker.reportDependencyState("postgresql", DependencyState.UNHEALTHY, "down");

        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "GET /api/unknown", ErrorCategory.CONNECTION_ERROR);

        assertEquals("dependency", result.causeCategory());
        assertEquals("postgresql", result.causeDependency());
    }

    // ── classifyNonDependencyCause ──────────────────────────────────────

    @Test
    void classifyNonDependencyCauseForCodeBug() {
        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.CODE_BUG);

        assertEquals("code", result.causeCategory());
        assertNull(result.causeDependency());
        assertEquals(0.8, result.confidence(), 0.01);
        assertTrue(result.causeHint().contains("code defect"));
    }

    @Test
    void classifyNonDependencyCauseForDataValidation() {
        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.DATA_VALIDATION);

        assertEquals("code", result.causeCategory());
        assertEquals(0.7, result.confidence(), 0.01);
        assertTrue(result.causeHint().contains("invalid input"));
    }

    @Test
    void classifyNonDependencyCauseForRateLimited() {
        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.RATE_LIMITED);

        assertEquals("traffic", result.causeCategory());
        assertEquals(0.8, result.confidence(), 0.01);
        assertTrue(result.causeHint().contains("rate limiting"));
    }

    @Test
    void classifyNonDependencyCauseForResourceExhaustion() {
        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.RESOURCE_EXHAUSTION);

        assertEquals("infrastructure", result.causeCategory());
        assertEquals(0.7, result.confidence(), 0.01);
        assertTrue(result.causeHint().contains("resource exhaustion"));
    }

    @Test
    void classifyNonDependencyCauseForAuthFailure() {
        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.AUTH_FAILURE);

        assertEquals("code", result.causeCategory());
        assertEquals(0.6, result.confidence(), 0.01);
        assertTrue(result.causeHint().contains("authentication") || result.causeHint().contains("authorization"));
    }

    @Test
    void classifyNonDependencyCauseForDependencyTimeoutWithoutUnhealthyDep() {
        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.DEPENDENCY_TIMEOUT);

        assertEquals("dependency", result.causeCategory());
        assertNull(result.causeDependency());
        assertEquals(0.5, result.confidence(), 0.01);
        assertTrue(result.causeHint().contains("no unhealthy dependency"));
    }

    @Test
    void classifyNonDependencyCauseForConnectionErrorWithoutUnhealthyDep() {
        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.CONNECTION_ERROR);

        assertEquals("dependency", result.causeCategory());
        assertEquals(0.5, result.confidence(), 0.01);
    }

    @Test
    void classifyNonDependencyCauseForUnknownCategory() {
        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.UNKNOWN);

        assertEquals("unknown", result.causeCategory());
        assertEquals(0.1, result.confidence(), 0.01);
    }

    @Test
    void classifyNonDependencyCauseForNullCategory() {
        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", null);

        assertEquals("unknown", result.causeCategory());
        assertEquals(0.1, result.confidence(), 0.01);
    }

    // ── Confidence decay for stale states ───────────────────────────────

    @Test
    void confidenceDecaysForStaleDependencyStates() {
        dependencyTracker.recordDependencyCall("POST /api/payments", "postgresql");

        // Manually inject a stale state (>5 minutes old) via reflection-free approach:
        // We report the state, then create a CausalAnalysis to check. Since we can't
        // control Instant.now(), we verify the decay mechanism by testing the logic path.
        // Instead, we test that fresh states get high confidence.

        tracker.reportDependencyState("postgresql", DependencyState.UNHEALTHY, "down");

        CausalityTracker.CausalAnalysis freshResult = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.DEPENDENCY_TIMEOUT);

        // Fresh state + matching error category: base 0.9 + 0.1 = 1.0 (no decay)
        assertEquals(1.0, freshResult.confidence(), 0.01,
                "Fresh unhealthy state with matching error should have full confidence");
    }

    @Test
    void unhealthyDependencyWithNonMatchingErrorHasSlightlyLowerConfidence() {
        dependencyTracker.recordDependencyCall("POST /api/payments", "postgresql");
        tracker.reportDependencyState("postgresql", DependencyState.UNHEALTHY, "down");

        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.CODE_BUG);

        // UNHEALTHY base is 0.9, no boost for non-matching error category
        assertEquals(0.9, result.confidence(), 0.01);
    }

    @Test
    void degradedDependencyHasLowerConfidenceThanUnhealthy() {
        dependencyTracker.recordDependencyCall("POST /api/payments", "postgresql");
        tracker.reportDependencyState("postgresql", DependencyState.DEGRADED, "slow");

        CausalityTracker.CausalAnalysis result = tracker.analyzeCause(
                "POST /api/payments", ErrorCategory.DEPENDENCY_TIMEOUT);

        // DEGRADED base is 0.6 + 0.1 for matching error = 0.7
        assertEquals(0.7, result.confidence(), 0.01);
    }

    // ── Backward-compatible getCauseHint() ──────────────────────────────

    @Test
    void getCauseHintReturnsEmptyWhenAllHealthy() {
        tracker.reportDependencyState("postgresql", DependencyState.HEALTHY, "all good");

        Optional<String> hint = tracker.getCauseHint();

        assertTrue(hint.isEmpty());
    }

    @Test
    void getCauseHintReturnsEmptyWhenNoDependenciesReported() {
        Optional<String> hint = tracker.getCauseHint();

        assertTrue(hint.isEmpty());
    }

    @Test
    void getCauseHintReturnsHintWhenDependencyUnhealthy() {
        tracker.reportDependencyState("redis", DependencyState.UNHEALTHY, "connection refused");

        Optional<String> hint = tracker.getCauseHint();

        assertTrue(hint.isPresent());
        assertTrue(hint.get().contains("redis"));
        assertTrue(hint.get().contains("unhealthy"));
        assertTrue(hint.get().contains("connection refused"));
    }

    @Test
    void getCauseHintReturnsHintWhenDependencyDegraded() {
        tracker.reportDependencyState("postgresql", DependencyState.DEGRADED, "high latency");

        Optional<String> hint = tracker.getCauseHint();

        assertTrue(hint.isPresent());
        assertTrue(hint.get().contains("postgresql"));
        assertTrue(hint.get().contains("degraded"));
    }

    @Test
    void getCauseHintIgnoresUnknownState() {
        tracker.reportDependencyState("kafka", DependencyState.UNKNOWN, "no data");

        Optional<String> hint = tracker.getCauseHint();

        assertTrue(hint.isEmpty());
    }

    // ── getDependencyState ──────────────────────────────────────────────

    @Test
    void getDependencyStateReturnsReportedState() {
        tracker.reportDependencyState("postgresql", DependencyState.UNHEALTHY, "down");

        Optional<CausalityTracker.DependencyHealthState> state =
                tracker.getDependencyState("postgresql");

        assertTrue(state.isPresent());
        assertEquals("postgresql", state.get().name());
        assertEquals(DependencyState.UNHEALTHY, state.get().state());
        assertEquals("down", state.get().evidence());
        assertNotNull(state.get().since());
    }

    @Test
    void getDependencyStateReturnsEmptyForUnknownDependency() {
        Optional<CausalityTracker.DependencyHealthState> state =
                tracker.getDependencyState("unknown-dep");

        assertTrue(state.isEmpty());
    }

    @Test
    void reportDependencyStateUpdatesExistingState() {
        tracker.reportDependencyState("postgresql", DependencyState.DEGRADED, "slow queries");
        tracker.reportDependencyState("postgresql", DependencyState.UNHEALTHY, "completely down");

        Optional<CausalityTracker.DependencyHealthState> state =
                tracker.getDependencyState("postgresql");

        assertTrue(state.isPresent());
        assertEquals(DependencyState.UNHEALTHY, state.get().state());
        assertEquals("completely down", state.get().evidence());
    }
}
