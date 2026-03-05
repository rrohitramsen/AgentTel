package io.agenttel.agent.correlation;

import io.agenttel.agent.correlation.ChangeCorrelationEngine.ChangeEvent;
import io.agenttel.agent.correlation.ChangeCorrelationEngine.ChangeType;
import io.agenttel.agent.correlation.ChangeCorrelationEngine.CorrelatedChange;
import io.agenttel.agent.correlation.ChangeCorrelationEngine.CorrelationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChangeCorrelationEngineTest {

    private ChangeCorrelationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ChangeCorrelationEngine();
    }

    @Test
    void correlate_noChanges_returnsNone() {
        CorrelationResult result = engine.correlate(Instant.now());

        assertSame(CorrelationResult.NONE, result);
        assertFalse(result.hasCorrelation());
        assertNull(result.likelyCause());
        assertNull(result.changeId());
        assertEquals(0, result.timeDeltaMs());
        assertEquals(0.0, result.confidence());
        assertTrue(result.allCorrelatedChanges().isEmpty());
    }

    @Test
    void correlate_recentDeployment_highConfidence() {
        Instant now = Instant.now();
        // Deployment 30 seconds before anomaly (< 1 min => base 0.95, DEPLOYMENT weight 1.0)
        ChangeEvent deployment = new ChangeEvent(
                "deploy-v2.1", ChangeType.DEPLOYMENT, "Deploy version 2.1",
                now.minus(Duration.ofSeconds(30))
        );
        engine.recordChange(deployment);

        CorrelationResult result = engine.correlate(now);

        assertTrue(result.hasCorrelation());
        assertEquals("deployment", result.likelyCause());
        assertEquals("deploy-v2.1", result.changeId());
        // 0.95 * 1.0 = 0.95
        assertEquals(0.95, result.confidence(), 0.01);
        assertEquals(1, result.allCorrelatedChanges().size());
    }

    @Test
    void correlate_timeProximity_closerIsHigher() {
        Instant now = Instant.now();

        // Close change: 30 seconds ago => <1 min => base 0.95
        ChangeEvent close = new ChangeEvent(
                "deploy-close", ChangeType.DEPLOYMENT, "Close deployment",
                now.minus(Duration.ofSeconds(30))
        );
        // Far change: 12 minutes ago => >10 min => base 0.40
        ChangeEvent far = new ChangeEvent(
                "deploy-far", ChangeType.DEPLOYMENT, "Far deployment",
                now.minus(Duration.ofMinutes(12))
        );

        engine.recordChange(far);
        engine.recordChange(close);

        CorrelationResult result = engine.correlate(now);

        assertTrue(result.hasCorrelation());
        // The close deployment should be primary (higher confidence)
        assertEquals("deploy-close", result.changeId());
        assertEquals(2, result.allCorrelatedChanges().size());

        // Verify ordering: first has higher confidence than second
        List<CorrelatedChange> correlated = result.allCorrelatedChanges();
        assertTrue(correlated.get(0).confidence() > correlated.get(1).confidence(),
                "First correlated change should have higher confidence");
    }

    @Test
    void correlate_timeProximity_buckets() {
        Instant now = Instant.now();

        // Under 1 minute => base 0.95
        ChangeEvent under1m = new ChangeEvent(
                "d1", ChangeType.DEPLOYMENT, "Under 1 min",
                now.minus(Duration.ofSeconds(45))
        );
        engine.recordChange(under1m);
        CorrelationResult result1 = engine.correlate(now);
        assertEquals(0.95, result1.confidence(), 0.01, "Under 1 min should have 0.95 confidence");

        // Reset and test 3 minutes => base 0.80
        engine = new ChangeCorrelationEngine();
        ChangeEvent under5m = new ChangeEvent(
                "d2", ChangeType.DEPLOYMENT, "Under 5 min",
                now.minus(Duration.ofMinutes(3))
        );
        engine.recordChange(under5m);
        CorrelationResult result2 = engine.correlate(now);
        assertEquals(0.80, result2.confidence(), 0.01, "Under 5 min should have 0.80 confidence");

        // Reset and test 8 minutes => base 0.60
        engine = new ChangeCorrelationEngine();
        ChangeEvent under10m = new ChangeEvent(
                "d3", ChangeType.DEPLOYMENT, "Under 10 min",
                now.minus(Duration.ofMinutes(8))
        );
        engine.recordChange(under10m);
        CorrelationResult result3 = engine.correlate(now);
        assertEquals(0.60, result3.confidence(), 0.01, "Under 10 min should have 0.60 confidence");

        // Reset and test 12 minutes => base 0.40
        engine = new ChangeCorrelationEngine();
        ChangeEvent over10m = new ChangeEvent(
                "d4", ChangeType.DEPLOYMENT, "Over 10 min",
                now.minus(Duration.ofMinutes(12))
        );
        engine.recordChange(over10m);
        CorrelationResult result4 = engine.correlate(now);
        assertEquals(0.40, result4.confidence(), 0.01, "Over 10 min should have 0.40 confidence");
    }

    @Test
    void correlate_changeType_deploymentWeightedHighest() {
        Instant now = Instant.now();
        Instant changeTime = now.minus(Duration.ofSeconds(30)); // <1 min => base 0.95

        // DEPLOYMENT: weight 1.0 => 0.95 * 1.0 = 0.95
        engine = new ChangeCorrelationEngine();
        engine.recordChange(new ChangeEvent("d1", ChangeType.DEPLOYMENT, "deploy", changeTime));
        double deployConfidence = engine.correlate(now).confidence();

        // CONFIG: weight 0.9 => 0.95 * 0.9 = 0.855 => rounded to 0.86
        engine = new ChangeCorrelationEngine();
        engine.recordChange(new ChangeEvent("c1", ChangeType.CONFIG, "config", changeTime));
        double configConfidence = engine.correlate(now).confidence();

        // SCALING: weight 0.7 => 0.95 * 0.7 = 0.665 => rounded to 0.66
        engine = new ChangeCorrelationEngine();
        engine.recordChange(new ChangeEvent("s1", ChangeType.SCALING, "scale", changeTime));
        double scalingConfidence = engine.correlate(now).confidence();

        assertTrue(deployConfidence > configConfidence,
                "DEPLOYMENT (" + deployConfidence + ") should have higher confidence than CONFIG (" + configConfidence + ")");
        assertTrue(configConfidence > scalingConfidence,
                "CONFIG (" + configConfidence + ") should have higher confidence than SCALING (" + scalingConfidence + ")");

        // Verify exact values
        assertEquals(0.95, deployConfidence, 0.01);
        assertEquals(0.86, configConfidence, 0.01);
        assertEquals(0.66, scalingConfidence, 0.01);
    }

    @Test
    void correlate_changeType_featureFlagAndDependencyUpdate() {
        Instant now = Instant.now();
        Instant changeTime = now.minus(Duration.ofSeconds(30)); // <1 min => base 0.95

        // FEATURE_FLAG: weight 0.8 => 0.95 * 0.8 = 0.76
        engine = new ChangeCorrelationEngine();
        engine.recordChange(new ChangeEvent("f1", ChangeType.FEATURE_FLAG, "flag", changeTime));
        double flagConfidence = engine.correlate(now).confidence();

        // DEPENDENCY_UPDATE: weight 0.85 => 0.95 * 0.85 = 0.8075 => rounded to 0.81
        engine = new ChangeCorrelationEngine();
        engine.recordChange(new ChangeEvent("du1", ChangeType.DEPENDENCY_UPDATE, "dep update", changeTime));
        double depUpdateConfidence = engine.correlate(now).confidence();

        assertEquals(0.76, flagConfidence, 0.01);
        assertEquals(0.81, depUpdateConfidence, 0.01);
        assertTrue(depUpdateConfidence > flagConfidence,
                "DEPENDENCY_UPDATE should have higher confidence than FEATURE_FLAG");
    }

    @Test
    void recordChange_boundedTo200() {
        for (int i = 0; i < 250; i++) {
            engine.recordChange(new ChangeEvent(
                    "change-" + i, ChangeType.CONFIG, "Config change " + i,
                    Instant.now()
            ));
        }

        List<ChangeEvent> recent = engine.getRecentChanges();
        assertEquals(200, recent.size(), "Should be bounded to MAX_CHANGES=200");

        // The oldest 50 should have been evicted; first remaining should be change-50
        assertEquals("change-50", recent.get(0).id(),
                "Oldest changes should have been evicted");
        assertEquals("change-249", recent.get(199).id(),
                "Most recent change should be last");
    }

    @Test
    void correlate_changesOutsideWindow_notCorrelated() {
        // Use a short 5-minute window
        engine = new ChangeCorrelationEngine(Duration.ofMinutes(5));

        Instant now = Instant.now();

        // Change 10 minutes ago is outside the 5-minute window
        ChangeEvent oldChange = new ChangeEvent(
                "old-deploy", ChangeType.DEPLOYMENT, "Old deployment",
                now.minus(Duration.ofMinutes(10))
        );
        engine.recordChange(oldChange);

        CorrelationResult result = engine.correlate(now);

        assertFalse(result.hasCorrelation(),
                "Changes outside the correlation window should not be correlated");
        assertSame(CorrelationResult.NONE, result);
    }

    @Test
    void correlate_changesAfterAnomaly_notCorrelated() {
        Instant anomalyTime = Instant.now().minus(Duration.ofMinutes(5));

        // Change occurred after the anomaly
        ChangeEvent futureChange = new ChangeEvent(
                "future-deploy", ChangeType.DEPLOYMENT, "Future deployment",
                anomalyTime.plus(Duration.ofMinutes(1))
        );
        engine.recordChange(futureChange);

        CorrelationResult result = engine.correlate(anomalyTime);

        assertFalse(result.hasCorrelation(),
                "Changes after anomaly onset should not be correlated");
    }

    @Test
    void correlate_multipleChanges_sortedByConfidence() {
        Instant now = Instant.now();

        // Scaling 12 minutes ago (base 0.40, weight 0.7 => 0.28)
        engine.recordChange(new ChangeEvent(
                "scale-1", ChangeType.SCALING, "Scaling event",
                now.minus(Duration.ofMinutes(12))
        ));

        // Config 3 minutes ago (base 0.80, weight 0.9 => 0.72)
        engine.recordChange(new ChangeEvent(
                "config-1", ChangeType.CONFIG, "Config change",
                now.minus(Duration.ofMinutes(3))
        ));

        // Deployment 30 seconds ago (base 0.95, weight 1.0 => 0.95)
        engine.recordChange(new ChangeEvent(
                "deploy-1", ChangeType.DEPLOYMENT, "Deployment",
                now.minus(Duration.ofSeconds(30))
        ));

        CorrelationResult result = engine.correlate(now);

        assertTrue(result.hasCorrelation());
        assertEquals(3, result.allCorrelatedChanges().size());

        // Primary should be the deployment (highest confidence)
        assertEquals("deploy-1", result.changeId());
        assertEquals("deployment", result.likelyCause());

        // Verify sorted descending by confidence
        List<CorrelatedChange> all = result.allCorrelatedChanges();
        for (int i = 0; i < all.size() - 1; i++) {
            assertTrue(all.get(i).confidence() >= all.get(i + 1).confidence(),
                    "Results should be sorted by confidence descending");
        }
    }

    @Test
    void recordDeployment_convenienceMethod() {
        engine.recordDeployment("deploy-1", "v2.0", "Version 2.0 deployment");

        List<ChangeEvent> changes = engine.getRecentChanges();
        assertEquals(1, changes.size());
        assertEquals("deploy-1", changes.get(0).id());
        assertEquals(ChangeType.DEPLOYMENT, changes.get(0).type());
        assertEquals("Version 2.0 deployment", changes.get(0).description());
    }

    @Test
    void recordConfigChange_convenienceMethod() {
        engine.recordConfigChange("cfg-1", "Updated timeout to 30s");

        List<ChangeEvent> changes = engine.getRecentChanges();
        assertEquals(1, changes.size());
        assertEquals("cfg-1", changes.get(0).id());
        assertEquals(ChangeType.CONFIG, changes.get(0).type());
    }

    @Test
    void recordScalingEvent_convenienceMethod() {
        engine.recordScalingEvent("scale-1", "Scaled to 5 instances");

        List<ChangeEvent> changes = engine.getRecentChanges();
        assertEquals(1, changes.size());
        assertEquals("scale-1", changes.get(0).id());
        assertEquals(ChangeType.SCALING, changes.get(0).type());
    }

    @Test
    void correlate_changeExactlyAtWindowBoundary_notCorrelated() {
        Duration window = Duration.ofMinutes(5);
        engine = new ChangeCorrelationEngine(window);

        Instant now = Instant.now();
        // Change exactly at window start boundary
        Instant exactBoundary = now.minus(window);

        engine.recordChange(new ChangeEvent(
                "boundary", ChangeType.DEPLOYMENT, "At boundary", exactBoundary
        ));

        CorrelationResult result = engine.correlate(now);

        // isAfter is exclusive, so exactly at boundary should NOT be included
        assertFalse(result.hasCorrelation(),
                "Change exactly at window boundary should not be correlated (isAfter is exclusive)");
    }
}
