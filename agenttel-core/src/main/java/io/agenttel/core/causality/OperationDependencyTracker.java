package io.agenttel.core.causality;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime tracker mapping operations to their dependencies.
 * Learned from observed span relationships (parent SERVER span → child CLIENT span).
 * Thread-safe for concurrent recording and querying.
 */
public class OperationDependencyTracker {

    private static final int MAX_OPERATIONS = 500;
    private static final int MAX_DEPS_PER_OPERATION = 50;

    private final ConcurrentHashMap<String, Set<String>> operationToDeps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> depToOperations = new ConcurrentHashMap<>();

    /**
     * Records that an operation called a dependency.
     */
    public void recordDependencyCall(String operationName, String dependencyName) {
        if (operationName == null || dependencyName == null) return;

        // Forward mapping: operation → dependencies
        operationToDeps.compute(operationName, (key, existing) -> {
            if (existing == null) {
                if (operationToDeps.size() >= MAX_OPERATIONS) return null;
                existing = ConcurrentHashMap.newKeySet();
            }
            if (existing.size() < MAX_DEPS_PER_OPERATION) {
                existing.add(dependencyName);
            }
            return existing;
        });

        // Reverse mapping: dependency → operations
        depToOperations.compute(dependencyName, (key, existing) -> {
            if (existing == null) {
                existing = ConcurrentHashMap.newKeySet();
            }
            existing.add(operationName);
            return existing;
        });
    }

    /**
     * Returns the dependencies known to be called by an operation.
     */
    public Set<String> getDependencies(String operationName) {
        Set<String> deps = operationToDeps.get(operationName);
        return deps != null ? Collections.unmodifiableSet(deps) : Collections.emptySet();
    }

    /**
     * Returns the operations known to call a given dependency.
     */
    public Set<String> getAffectedOperations(String dependencyName) {
        Set<String> ops = depToOperations.get(dependencyName);
        return ops != null ? Collections.unmodifiableSet(ops) : Collections.emptySet();
    }

    /**
     * Returns the total number of tracked operations.
     */
    public int operationCount() {
        return operationToDeps.size();
    }
}
