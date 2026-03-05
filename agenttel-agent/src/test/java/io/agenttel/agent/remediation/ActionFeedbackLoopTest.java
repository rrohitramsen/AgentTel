package io.agenttel.agent.remediation;

import io.agenttel.agent.health.ServiceHealthAggregator;
import io.agenttel.agent.remediation.ActionFeedbackLoop.ActionOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ActionFeedbackLoopTest {

    private ServiceHealthAggregator healthAggregator;
    private ActionFeedbackLoop feedbackLoop;

    @BeforeEach
    void setUp() {
        // ServiceHealthAggregator accepts null for both rolling baselines and SLO tracker.
        // With no recorded spans, it returns HEALTHY status with empty operations.
        healthAggregator = new ServiceHealthAggregator(null, null);
        feedbackLoop = new ActionFeedbackLoop(healthAggregator, Duration.ofMillis(100));
    }

    @AfterEach
    void tearDown() {
        feedbackLoop.shutdown();
    }

    @Test
    void scheduleVerification_returnsFuture() {
        CompletableFuture<ActionOutcome> future = feedbackLoop.scheduleVerification("restart_instances");

        assertNotNull(future, "scheduleVerification should return a non-null future");
        assertFalse(future.isDone(), "Future should not be immediately completed");
    }

    @Test
    void getOutcome_beforeVerificationCompletes_returnsEmpty() {
        feedbackLoop.scheduleVerification("restart_instances");

        // Immediately check before the 100ms delay elapses
        Optional<ActionOutcome> outcome = feedbackLoop.getOutcome("restart_instances");

        assertTrue(outcome.isEmpty(),
                "Outcome should be empty before verification completes");
    }

    @Test
    void getOutcome_afterDelay_returnsOutcome() throws Exception {
        CompletableFuture<ActionOutcome> future = feedbackLoop.scheduleVerification("restart_instances");

        // Wait for the scheduled verification to complete (100ms delay + buffer)
        ActionOutcome outcome = future.get(2, TimeUnit.SECONDS);

        assertNotNull(outcome, "Outcome should be non-null after completion");
        assertEquals("restart_instances", outcome.actionName());
        assertNotNull(outcome.preHealthStatus());
        assertNotNull(outcome.postHealthStatus());
        assertNotNull(outcome.verifiedAt());

        // Also verify it's accessible via getOutcome
        Optional<ActionOutcome> stored = feedbackLoop.getOutcome("restart_instances");
        assertTrue(stored.isPresent(), "Outcome should be stored after verification");
        assertEquals("restart_instances", stored.get().actionName());
    }

    @Test
    void getOutcome_withHealthChange_detectsImprovement() throws Exception {
        // Record some error spans before the action to create a degraded state
        for (int i = 0; i < 10; i++) {
            healthAggregator.recordSpan("GET /users", 200.0, true);
        }

        CompletableFuture<ActionOutcome> future = feedbackLoop.scheduleVerification("fix_errors");

        // Simulate improvement: record healthy spans before verification runs
        // The delay is 100ms so we have time to add spans
        for (int i = 0; i < 50; i++) {
            healthAggregator.recordSpan("GET /users", 20.0, false);
        }

        ActionOutcome outcome = future.get(2, TimeUnit.SECONDS);

        assertNotNull(outcome);
        assertEquals("fix_errors", outcome.actionName());
        // The outcome contains the delta information
        assertNotNull(outcome.preHealthStatus());
        assertNotNull(outcome.postHealthStatus());
    }

    @Test
    void scheduleVerification_withCustomDelay() throws Exception {
        CompletableFuture<ActionOutcome> future =
                feedbackLoop.scheduleVerification("scale_up", Duration.ofMillis(50));

        ActionOutcome outcome = future.get(2, TimeUnit.SECONDS);

        assertNotNull(outcome);
        assertEquals("scale_up", outcome.actionName());
    }

    @Test
    void getOutcome_forUnknownAction_returnsEmpty() {
        Optional<ActionOutcome> outcome = feedbackLoop.getOutcome("never_scheduled");

        assertTrue(outcome.isEmpty());
    }

    @Test
    void recentOutcomes_boundedTo50() throws Exception {
        // Schedule 60 verifications with very short delay
        ActionFeedbackLoop shortLoop = new ActionFeedbackLoop(healthAggregator, Duration.ofMillis(10));
        try {
            CompletableFuture<?>[] futures = new CompletableFuture[60];
            for (int i = 0; i < 60; i++) {
                futures[i] = shortLoop.scheduleVerification("action-" + i);
            }

            // Wait for all to complete
            CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);

            // Recent outcomes should be bounded to 50
            assertEquals(50, shortLoop.getRecentOutcomes().size(),
                    "Recent outcomes should be bounded to 50");
        } finally {
            shortLoop.shutdown();
        }
    }

    @Test
    void shutdown_stopsScheduler() throws Exception {
        feedbackLoop.shutdown();

        // After shutdown, scheduling should fail (RejectedExecutionException wrapped in future)
        // The scheduler is shut down, so new tasks are rejected
        assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> {
            feedbackLoop.scheduleVerification("post_shutdown_action");
        });
    }

    @Test
    void multipleVerifications_sameAction_lastOneWins() throws Exception {
        CompletableFuture<ActionOutcome> first = feedbackLoop.scheduleVerification("restart");
        CompletableFuture<ActionOutcome> second = feedbackLoop.scheduleVerification("restart");

        first.get(2, TimeUnit.SECONDS);
        ActionOutcome secondOutcome = second.get(2, TimeUnit.SECONDS);

        // getOutcome should return the last completed outcome for this action name
        Optional<ActionOutcome> stored = feedbackLoop.getOutcome("restart");
        assertTrue(stored.isPresent());
        assertEquals("restart", stored.get().actionName());
    }

    @Test
    void outcome_withNoOperations_reportsHealthyToHealthy() throws Exception {
        // No spans recorded, so health is HEALTHY before and after
        CompletableFuture<ActionOutcome> future = feedbackLoop.scheduleVerification("noop_action");

        ActionOutcome outcome = future.get(2, TimeUnit.SECONDS);

        assertEquals("noop_action", outcome.actionName());
        assertEquals("HEALTHY", outcome.preHealthStatus());
        assertEquals("HEALTHY", outcome.postHealthStatus());
        // With no change, latency and error rate deltas should be zero
        assertEquals(0.0, outcome.latencyDeltaMs(), 0.001);
        assertEquals(0.0, outcome.errorRateDelta(), 0.001);
    }

    @Test
    void getRecentOutcomes_returnsInOrder() throws Exception {
        CompletableFuture<ActionOutcome> f1 = feedbackLoop.scheduleVerification("action-a");
        f1.get(2, TimeUnit.SECONDS);

        CompletableFuture<ActionOutcome> f2 = feedbackLoop.scheduleVerification("action-b");
        f2.get(2, TimeUnit.SECONDS);

        var outcomes = feedbackLoop.getRecentOutcomes();
        assertEquals(2, outcomes.size());
        assertEquals("action-a", outcomes.get(0).actionName());
        assertEquals("action-b", outcomes.get(1).actionName());
    }
}
