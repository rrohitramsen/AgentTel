package io.agenttel.core.causality;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OperationDependencyTrackerTest {

    private OperationDependencyTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new OperationDependencyTracker();
    }

    @Test
    void recordDependencyCallAndRetrieve() {
        tracker.recordDependencyCall("POST /api/payments", "postgresql");

        Set<String> deps = tracker.getDependencies("POST /api/payments");

        assertEquals(1, deps.size());
        assertTrue(deps.contains("postgresql"));
    }

    @Test
    void recordMultipleDependenciesForSameOperation() {
        tracker.recordDependencyCall("POST /api/payments", "postgresql");
        tracker.recordDependencyCall("POST /api/payments", "redis");
        tracker.recordDependencyCall("POST /api/payments", "stripe-api");

        Set<String> deps = tracker.getDependencies("POST /api/payments");

        assertEquals(3, deps.size());
        assertTrue(deps.contains("postgresql"));
        assertTrue(deps.contains("redis"));
        assertTrue(deps.contains("stripe-api"));
    }

    @Test
    void duplicateDependencyNotAddedTwice() {
        tracker.recordDependencyCall("POST /api/payments", "postgresql");
        tracker.recordDependencyCall("POST /api/payments", "postgresql");

        Set<String> deps = tracker.getDependencies("POST /api/payments");

        assertEquals(1, deps.size());
    }

    @Test
    void getDependenciesForUnknownOperationReturnsEmptySet() {
        Set<String> deps = tracker.getDependencies("unknown-operation");

        assertNotNull(deps);
        assertTrue(deps.isEmpty());
    }

    @Test
    void bidirectionalLookupGetAffectedOperations() {
        tracker.recordDependencyCall("POST /api/payments", "postgresql");
        tracker.recordDependencyCall("GET /api/users", "postgresql");
        tracker.recordDependencyCall("POST /api/payments", "redis");

        Set<String> affectedByPostgres = tracker.getAffectedOperations("postgresql");

        assertEquals(2, affectedByPostgres.size());
        assertTrue(affectedByPostgres.contains("POST /api/payments"));
        assertTrue(affectedByPostgres.contains("GET /api/users"));
    }

    @Test
    void getAffectedOperationsForUnknownDependencyReturnsEmptySet() {
        Set<String> affected = tracker.getAffectedOperations("unknown-dep");

        assertNotNull(affected);
        assertTrue(affected.isEmpty());
    }

    @Test
    void operationCountReflectsDistinctOperations() {
        tracker.recordDependencyCall("op-1", "dep-a");
        tracker.recordDependencyCall("op-2", "dep-b");
        tracker.recordDependencyCall("op-1", "dep-c");  // same operation, different dep

        assertEquals(2, tracker.operationCount());
    }

    @Test
    void boundedOperationsMaxFiveHundred() {
        // Register exactly 500 operations
        for (int i = 0; i < 500; i++) {
            tracker.recordDependencyCall("operation-" + i, "dep-a");
        }
        assertEquals(500, tracker.operationCount());

        // Attempt to add operation #501
        tracker.recordDependencyCall("operation-500", "dep-a");

        // Should not exceed the 500 limit
        assertTrue(tracker.operationCount() <= 500);
        // The 501st operation should not be tracked
        assertTrue(tracker.getDependencies("operation-500").isEmpty());
    }

    @Test
    void boundedDependenciesPerOperationMaxFifty() {
        // Register 50 dependencies for one operation
        for (int i = 0; i < 50; i++) {
            tracker.recordDependencyCall("POST /api/payments", "dep-" + i);
        }
        assertEquals(50, tracker.getDependencies("POST /api/payments").size());

        // Attempt to add a 51st dependency
        tracker.recordDependencyCall("POST /api/payments", "dep-overflow");

        // Should not exceed 50
        assertEquals(50, tracker.getDependencies("POST /api/payments").size());
        assertFalse(tracker.getDependencies("POST /api/payments").contains("dep-overflow"));
    }

    @Test
    void nullOperationNameIsIgnored() {
        tracker.recordDependencyCall(null, "postgresql");

        assertEquals(0, tracker.operationCount());
    }

    @Test
    void nullDependencyNameIsIgnored() {
        tracker.recordDependencyCall("POST /api/payments", null);

        assertTrue(tracker.getDependencies("POST /api/payments").isEmpty());
    }

    @Test
    void returnedDependencySetIsUnmodifiable() {
        tracker.recordDependencyCall("POST /api/payments", "postgresql");

        Set<String> deps = tracker.getDependencies("POST /api/payments");

        assertThrows(UnsupportedOperationException.class, () -> deps.add("redis"));
    }

    @Test
    void returnedAffectedOperationsSetIsUnmodifiable() {
        tracker.recordDependencyCall("POST /api/payments", "postgresql");

        Set<String> ops = tracker.getAffectedOperations("postgresql");

        assertThrows(UnsupportedOperationException.class, () -> ops.add("GET /api/users"));
    }

    @Test
    void threadSafetyConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Throwable> errors = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String op = "thread-" + threadId + "-op-" + i;
                        tracker.recordDependencyCall(op, "shared-dep");
                        tracker.recordDependencyCall("shared-op", "dep-" + threadId + "-" + i);

                        // Also read concurrently
                        tracker.getDependencies(op);
                        tracker.getAffectedOperations("shared-dep");
                        tracker.operationCount();
                    }
                } catch (Throwable e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Threads did not complete in time");
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Concurrent access produced errors: " + errors);

        // Verify shared-dep has operations from all threads
        Set<String> affectedBySharedDep = tracker.getAffectedOperations("shared-dep");
        assertFalse(affectedBySharedDep.isEmpty());

        // shared-op should have some dependencies recorded
        Set<String> sharedOpDeps = tracker.getDependencies("shared-op");
        assertFalse(sharedOpDeps.isEmpty());
    }
}
