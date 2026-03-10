package io.agenttel.agentic.quality;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks quality signals for an agent invocation.
 * Thread-safe for concurrent access.
 */
public class QualityTracker {

    private final AtomicBoolean goalAchieved = new AtomicBoolean(false);
    private final AtomicLong humanInterventions = new AtomicLong(0);
    private final AtomicBoolean loopDetected = new AtomicBoolean(false);
    private final AtomicReference<Double> evalScore = new AtomicReference<>(null);

    public void setGoalAchieved(boolean achieved) {
        goalAchieved.set(achieved);
    }

    public void recordHumanIntervention() {
        humanInterventions.incrementAndGet();
    }

    public void setLoopDetected(boolean detected) {
        loopDetected.set(detected);
    }

    public void setEvalScore(double score) {
        evalScore.set(score);
    }

    /**
     * Applies all tracked quality signals to the given span.
     */
    public void applyTo(Span span) {
        span.setAttribute(AgenticAttributes.QUALITY_GOAL_ACHIEVED, goalAchieved.get());
        span.setAttribute(AgenticAttributes.QUALITY_HUMAN_INTERVENTIONS, humanInterventions.get());
        span.setAttribute(AgenticAttributes.QUALITY_LOOP_DETECTED, loopDetected.get());

        Double score = evalScore.get();
        if (score != null) {
            span.setAttribute(AgenticAttributes.QUALITY_EVAL_SCORE, score);
        }
    }

    public boolean isGoalAchieved() { return goalAchieved.get(); }
    public long getHumanInterventions() { return humanInterventions.get(); }
    public boolean isLoopDetected() { return loopDetected.get(); }
}
