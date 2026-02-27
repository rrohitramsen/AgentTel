package io.agenttel.core.baseline;

import io.agenttel.api.BaselineSource;
import io.agenttel.api.annotations.AgentOperation;
import io.agenttel.api.baseline.OperationBaseline;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides baselines from static annotations ({@link AgentOperation}).
 * Populated once at startup.
 */
public class StaticBaselineProvider implements BaselineProvider {

    private final ConcurrentHashMap<String, OperationBaseline> baselines = new ConcurrentHashMap<>();

    /**
     * Registers a baseline from an {@link AgentOperation} annotation.
     */
    public void registerFromAnnotation(String operationName, AgentOperation annotation) {
        double p50 = DurationParser.parseToMs(annotation.expectedLatencyP50());
        double p99 = DurationParser.parseToMs(annotation.expectedLatencyP99());
        double errorRate = annotation.expectedErrorRate();

        OperationBaseline baseline = OperationBaseline.builder(operationName)
                .latencyP50Ms(p50)
                .latencyP99Ms(p99)
                .errorRate(errorRate)
                .source(BaselineSource.STATIC)
                .build();

        baselines.put(operationName, baseline);
    }

    /**
     * Registers a pre-built baseline directly.
     */
    public void register(String operationName, OperationBaseline baseline) {
        baselines.put(operationName, baseline);
    }

    @Override
    public Optional<OperationBaseline> getBaseline(String operationName) {
        return Optional.ofNullable(baselines.get(operationName));
    }
}
