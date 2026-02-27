package io.agenttel.core.baseline;

import io.agenttel.api.baseline.OperationBaseline;

import java.util.List;
import java.util.Optional;

/**
 * Chains multiple {@link BaselineProvider}s with fallback semantics.
 * Returns the first non-empty baseline found in provider order.
 * Typical chain: static -> rolling -> default.
 */
public class CompositeBaselineProvider implements BaselineProvider {

    private final List<BaselineProvider> providers;

    public CompositeBaselineProvider(BaselineProvider... providers) {
        this.providers = List.of(providers);
    }

    public CompositeBaselineProvider(List<BaselineProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    @Override
    public Optional<OperationBaseline> getBaseline(String operationName) {
        for (BaselineProvider provider : providers) {
            Optional<OperationBaseline> baseline = provider.getBaseline(operationName);
            if (baseline.isPresent()) {
                return baseline;
            }
        }
        return Optional.empty();
    }
}
