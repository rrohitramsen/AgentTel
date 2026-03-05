package io.agenttel.core.causality;

import io.agenttel.api.DependencyState;
import io.agenttel.api.ErrorCategory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks dependency health states and provides causal analysis for error diagnosis.
 * Enhanced with operation-to-dependency mapping for targeted root cause identification.
 */
public class CausalityTracker {

    private final ConcurrentHashMap<String, DependencyHealthState> dependencyStates = new ConcurrentHashMap<>();
    private final OperationDependencyTracker dependencyTracker;

    public CausalityTracker() {
        this(null);
    }

    public CausalityTracker(OperationDependencyTracker dependencyTracker) {
        this.dependencyTracker = dependencyTracker;
    }

    /**
     * Reports the current health state of a dependency.
     */
    public void reportDependencyState(String dependency, DependencyState state, String evidence) {
        dependencyStates.put(dependency, new DependencyHealthState(dependency, state, evidence, Instant.now()));
    }

    /**
     * Returns a causal hint if any dependency is unhealthy or degraded.
     */
    public Optional<String> getCauseHint() {
        return dependencyStates.values().stream()
                .filter(s -> s.state() != DependencyState.HEALTHY && s.state() != DependencyState.UNKNOWN)
                .findFirst()
                .map(s -> "Dependency " + s.name() + " is " + s.state().getValue()
                        + " since " + s.since() + ": " + s.evidence());
    }

    /**
     * Returns the state of a specific dependency.
     */
    public Optional<DependencyHealthState> getDependencyState(String dependency) {
        return Optional.ofNullable(dependencyStates.get(dependency));
    }

    /**
     * Performs structured causal analysis for an operation's error.
     * Uses operation-to-dependency mapping to identify which dependencies
     * this operation actually calls, then checks their health.
     */
    public CausalAnalysis analyzeCause(String operationName, ErrorCategory errorCategory) {
        // If we have dependency tracking, use targeted analysis
        if (dependencyTracker != null) {
            Set<String> operationDeps = dependencyTracker.getDependencies(operationName);
            if (!operationDeps.isEmpty()) {
                return analyzeWithDependencyMapping(operationName, operationDeps, errorCategory);
            }
        }

        // Fallback: check all dependencies
        return analyzeAllDependencies(operationName, errorCategory);
    }

    private CausalAnalysis analyzeWithDependencyMapping(
            String operationName, Set<String> operationDeps, ErrorCategory errorCategory) {

        List<DependencyHealthState> unhealthyDeps = new ArrayList<>();
        for (String dep : operationDeps) {
            DependencyHealthState state = dependencyStates.get(dep);
            if (state != null && state.state() != DependencyState.HEALTHY
                    && state.state() != DependencyState.UNKNOWN) {
                unhealthyDeps.add(state);
            }
        }

        if (!unhealthyDeps.isEmpty()) {
            // Sort by severity (UNHEALTHY before DEGRADED) then recency
            unhealthyDeps.sort((a, b) -> {
                int sevCompare = severityWeight(b.state()) - severityWeight(a.state());
                if (sevCompare != 0) return sevCompare;
                return b.since().compareTo(a.since());
            });

            DependencyHealthState primary = unhealthyDeps.get(0);
            double confidence = computeConfidence(primary, errorCategory);

            return new CausalAnalysis(
                    "Dependency " + primary.name() + " is " + primary.state().getValue() + ": " + primary.evidence(),
                    "dependency",
                    primary.name(),
                    confidence
            );
        }

        // Dependencies are healthy; likely a code or traffic issue
        return classifyNonDependencyCause(errorCategory);
    }

    private CausalAnalysis analyzeAllDependencies(String operationName, ErrorCategory errorCategory) {
        Optional<DependencyHealthState> unhealthy = dependencyStates.values().stream()
                .filter(s -> s.state() != DependencyState.HEALTHY && s.state() != DependencyState.UNKNOWN)
                .max(Comparator.comparingInt(s -> severityWeight(s.state())));

        if (unhealthy.isPresent()) {
            DependencyHealthState dep = unhealthy.get();
            // Lower confidence since we don't know if this operation uses this dependency
            double confidence = computeConfidence(dep, errorCategory) * 0.6;

            return new CausalAnalysis(
                    "Dependency " + dep.name() + " is " + dep.state().getValue() + ": " + dep.evidence(),
                    "dependency",
                    dep.name(),
                    confidence
            );
        }

        return classifyNonDependencyCause(errorCategory);
    }

    private CausalAnalysis classifyNonDependencyCause(ErrorCategory errorCategory) {
        if (errorCategory == null) {
            return new CausalAnalysis("No clear root cause identified", "unknown", null, 0.1);
        }

        if (errorCategory == ErrorCategory.CODE_BUG) {
            return new CausalAnalysis("Error indicates a code defect", "code", null, 0.8);
        } else if (errorCategory == ErrorCategory.DATA_VALIDATION) {
            return new CausalAnalysis("Error indicates invalid input data", "code", null, 0.7);
        } else if (errorCategory == ErrorCategory.RESOURCE_EXHAUSTION) {
            return new CausalAnalysis("Error indicates resource exhaustion", "infrastructure", null, 0.7);
        } else if (errorCategory == ErrorCategory.RATE_LIMITED) {
            return new CausalAnalysis("Error indicates rate limiting from upstream", "traffic", null, 0.8);
        } else if (errorCategory == ErrorCategory.AUTH_FAILURE) {
            return new CausalAnalysis("Error indicates authentication/authorization failure", "code", null, 0.6);
        } else if (errorCategory == ErrorCategory.DEPENDENCY_TIMEOUT || errorCategory == ErrorCategory.CONNECTION_ERROR) {
            return new CausalAnalysis("Error indicates dependency issue but no unhealthy dependency reported",
                    "dependency", null, 0.5);
        } else {
            return new CausalAnalysis("No clear root cause identified", "unknown", null, 0.1);
        }
    }

    private double computeConfidence(DependencyHealthState dep, ErrorCategory errorCategory) {
        double base = dep.state() == DependencyState.UNHEALTHY ? 0.9 : 0.6;

        // Higher confidence if error category matches dependency errors
        if (errorCategory == ErrorCategory.DEPENDENCY_TIMEOUT
                || errorCategory == ErrorCategory.CONNECTION_ERROR) {
            base = Math.min(1.0, base + 0.1);
        }

        // Decay confidence for stale states (>5 minutes old)
        Duration age = Duration.between(dep.since(), Instant.now());
        if (age.toMinutes() > 5) {
            base *= 0.8;
        }

        return Math.round(base * 100.0) / 100.0;
    }

    private int severityWeight(DependencyState state) {
        return switch (state) {
            case UNHEALTHY -> 3;
            case DEGRADED -> 2;
            case UNKNOWN -> 1;
            case HEALTHY -> 0;
        };
    }

    public record DependencyHealthState(
            String name,
            DependencyState state,
            String evidence,
            Instant since
    ) {}

    /**
     * Structured causal analysis result for agent consumption.
     *
     * @param causeHint      Human-readable description of the likely cause
     * @param causeCategory  One of: dependency, code, infrastructure, traffic, unknown
     * @param causeDependency The specific dependency if cause is dependency-related (nullable)
     * @param confidence     Confidence in this analysis (0.0-1.0)
     */
    public record CausalAnalysis(
            String causeHint,
            String causeCategory,
            String causeDependency,
            double confidence
    ) {}
}
