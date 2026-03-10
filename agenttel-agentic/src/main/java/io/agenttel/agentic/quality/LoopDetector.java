package io.agenttel.agentic.quality;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Detects stuck reasoning loops where an agent calls the same tool
 * with the same arguments repeatedly.
 */
public class LoopDetector {

    private final int threshold;
    private final Map<String, AtomicLong> callCounts = new ConcurrentHashMap<>();

    /**
     * Creates a loop detector that triggers after {@code threshold} identical calls.
     */
    public LoopDetector(int threshold) {
        this.threshold = threshold;
    }

    /**
     * Creates a loop detector with the default threshold of 3.
     */
    public LoopDetector() {
        this(3);
    }

    /**
     * Records a tool call and checks for loops.
     *
     * @param toolName the tool name
     * @param argsHash a hash or string representation of the arguments
     * @param parentSpan the parent invocation span to annotate if a loop is detected
     * @return true if a loop was detected
     */
    public boolean recordCall(String toolName, String argsHash, Span parentSpan) {
        String key = toolName + ":" + Objects.toString(argsHash, "");
        long count = callCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();

        if (count >= threshold) {
            parentSpan.setAttribute(AgenticAttributes.QUALITY_LOOP_DETECTED, true);
            parentSpan.setAttribute(AgenticAttributes.QUALITY_LOOP_ITERATIONS, count);
            return true;
        }
        return false;
    }

    /**
     * Resets the detector state (e.g., between invocations).
     */
    public void reset() {
        callCounts.clear();
    }
}
