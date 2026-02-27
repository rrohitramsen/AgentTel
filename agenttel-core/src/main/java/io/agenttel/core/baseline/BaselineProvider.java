package io.agenttel.core.baseline;

import io.agenttel.api.baseline.OperationBaseline;

import java.util.Optional;

/**
 * Provides baseline expectations for operations.
 * Implementations may source baselines from annotations, rolling statistics, or external systems.
 */
public interface BaselineProvider {

    /**
     * Returns the baseline for the given operation name, if available.
     */
    Optional<OperationBaseline> getBaseline(String operationName);
}
