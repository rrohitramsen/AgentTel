package io.agenttel.core.enrichment;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps operation names to their decision metadata ({@link OperationContext}).
 * Populated at startup by annotation scanning.
 */
public class OperationContextRegistry {

    private final ConcurrentHashMap<String, OperationContext> contexts = new ConcurrentHashMap<>();

    public void register(String operationName, OperationContext context) {
        contexts.put(operationName, context);
    }

    public Optional<OperationContext> getContext(String operationName) {
        return Optional.ofNullable(contexts.get(operationName));
    }
}
