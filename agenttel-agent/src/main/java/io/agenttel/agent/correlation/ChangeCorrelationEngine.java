package io.agenttel.agent.correlation;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Correlates anomaly onset with recent changes to identify likely causes.
 * Answers the critical question: "What changed right before this broke?"
 */
public class ChangeCorrelationEngine {

    private static final int MAX_CHANGES = 200;
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);

    private final List<ChangeEvent> changes = Collections.synchronizedList(new ArrayList<>());
    private final Duration correlationWindow;

    public ChangeCorrelationEngine() {
        this(DEFAULT_WINDOW);
    }

    public ChangeCorrelationEngine(Duration correlationWindow) {
        this.correlationWindow = correlationWindow;
    }

    /**
     * Records a change event.
     */
    public void recordChange(ChangeEvent event) {
        changes.add(event);
        while (changes.size() > MAX_CHANGES) {
            changes.remove(0);
        }
    }

    /**
     * Records a deployment change.
     */
    public void recordDeployment(String id, String version, String description) {
        recordChange(new ChangeEvent(id, ChangeType.DEPLOYMENT, description, Instant.now()));
    }

    /**
     * Records a configuration change.
     */
    public void recordConfigChange(String id, String description) {
        recordChange(new ChangeEvent(id, ChangeType.CONFIG, description, Instant.now()));
    }

    /**
     * Records a scaling event.
     */
    public void recordScalingEvent(String id, String description) {
        recordChange(new ChangeEvent(id, ChangeType.SCALING, description, Instant.now()));
    }

    /**
     * Correlates an anomaly onset with recent changes.
     */
    public CorrelationResult correlate(Instant anomalyOnset) {
        Instant windowStart = anomalyOnset.minus(correlationWindow);

        List<CorrelatedChange> correlated = new ArrayList<>();
        for (ChangeEvent change : changes) {
            if (change.timestamp().isAfter(windowStart) && change.timestamp().isBefore(anomalyOnset)) {
                long timeDeltaMs = Duration.between(change.timestamp(), anomalyOnset).toMillis();
                double confidence = computeConfidence(timeDeltaMs, change.type());
                correlated.add(new CorrelatedChange(change, timeDeltaMs, confidence));
            }
        }

        if (correlated.isEmpty()) {
            return CorrelationResult.NONE;
        }

        // Sort by confidence (highest first)
        correlated.sort(Comparator.comparingDouble(CorrelatedChange::confidence).reversed());

        CorrelatedChange primary = correlated.get(0);
        return new CorrelationResult(
                primary.change().type().getValue(),
                primary.change().id(),
                primary.timeDeltaMs(),
                primary.confidence(),
                correlated
        );
    }

    /**
     * Returns recent changes for display.
     */
    public List<ChangeEvent> getRecentChanges() {
        return new ArrayList<>(changes);
    }

    private double computeConfidence(long timeDeltaMs, ChangeType type) {
        // Base confidence from time proximity (closer = higher)
        double base;
        if (timeDeltaMs < 60_000) {        // <1 min
            base = 0.95;
        } else if (timeDeltaMs < 300_000) { // <5 min
            base = 0.80;
        } else if (timeDeltaMs < 600_000) { // <10 min
            base = 0.60;
        } else {
            base = 0.40;
        }

        // Weight by change type (deployments are more likely to cause issues)
        double typeWeight = switch (type) {
            case DEPLOYMENT -> 1.0;
            case CONFIG -> 0.9;
            case DEPENDENCY_UPDATE -> 0.85;
            case FEATURE_FLAG -> 0.8;
            case SCALING -> 0.7;
        };

        return Math.round(base * typeWeight * 100.0) / 100.0;
    }

    public enum ChangeType {
        DEPLOYMENT("deployment"),
        CONFIG("config"),
        SCALING("scaling"),
        FEATURE_FLAG("feature_flag"),
        DEPENDENCY_UPDATE("dependency_update");

        private final String value;

        ChangeType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public record ChangeEvent(
            String id,
            ChangeType type,
            String description,
            Instant timestamp
    ) {}

    public record CorrelatedChange(
            ChangeEvent change,
            long timeDeltaMs,
            double confidence
    ) {}

    public record CorrelationResult(
            String likelyCause,
            String changeId,
            long timeDeltaMs,
            double confidence,
            List<CorrelatedChange> allCorrelatedChanges
    ) {
        public static final CorrelationResult NONE =
                new CorrelationResult(null, null, 0, 0, List.of());

        public boolean hasCorrelation() {
            return likelyCause != null;
        }
    }
}
