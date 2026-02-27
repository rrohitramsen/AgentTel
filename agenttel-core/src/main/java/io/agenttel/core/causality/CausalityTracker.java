package io.agenttel.core.causality;

import io.agenttel.api.DependencyState;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks dependency health states and provides causal hints for error diagnosis.
 */
public class CausalityTracker {

    private final ConcurrentHashMap<String, DependencyHealthState> dependencyStates = new ConcurrentHashMap<>();

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

    public record DependencyHealthState(
            String name,
            DependencyState state,
            String evidence,
            Instant since
    ) {}
}
