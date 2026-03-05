package io.agenttel.core.baseline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RollingWindowConfidenceTest {

    @Test
    void confidenceReturnsLowForLessThanThirtySamples() {
        RollingWindow window = new RollingWindow(1000);

        // Zero samples
        assertEquals("low", window.snapshot().confidence());

        // Add 29 samples (still under 30)
        for (int i = 0; i < 29; i++) {
            window.record(100.0 + i);
        }
        RollingWindow.Snapshot snapshot = window.snapshot();

        assertEquals(29, snapshot.sampleCount());
        assertEquals("low", snapshot.confidence());
    }

    @Test
    void confidenceReturnsMediumForThirtyToTwoHundredSamples() {
        RollingWindow window = new RollingWindow(1000);

        // Add exactly 30 samples
        for (int i = 0; i < 30; i++) {
            window.record(100.0 + i);
        }
        RollingWindow.Snapshot snapshotAt30 = window.snapshot();

        assertEquals(30, snapshotAt30.sampleCount());
        assertEquals("medium", snapshotAt30.confidence());

        // Add more to get to 199 (still medium range)
        for (int i = 30; i < 199; i++) {
            window.record(100.0 + i);
        }
        RollingWindow.Snapshot snapshotAt199 = window.snapshot();

        assertEquals(199, snapshotAt199.sampleCount());
        assertEquals("medium", snapshotAt199.confidence());
    }

    @Test
    void confidenceReturnsHighForTwoHundredOrMoreSamples() {
        RollingWindow window = new RollingWindow(1000);

        // Add exactly 200 samples
        for (int i = 0; i < 200; i++) {
            window.record(50.0 + i);
        }
        RollingWindow.Snapshot snapshotAt200 = window.snapshot();

        assertEquals(200, snapshotAt200.sampleCount());
        assertEquals("high", snapshotAt200.confidence());
    }

    @Test
    void confidenceReturnsHighForLargeNumberOfSamples() {
        RollingWindow window = new RollingWindow(500);

        for (int i = 0; i < 500; i++) {
            window.record(10.0 * i);
        }
        RollingWindow.Snapshot snapshot = window.snapshot();

        assertEquals(500, snapshot.sampleCount());
        assertEquals("high", snapshot.confidence());
    }

    @Test
    void confidenceBoundaryAtExactlyThirty() {
        RollingWindow window = new RollingWindow(1000);

        // 29 samples: low
        for (int i = 0; i < 29; i++) {
            window.record(1.0);
        }
        assertEquals("low", window.snapshot().confidence());

        // Add the 30th sample: medium
        window.record(1.0);
        assertEquals("medium", window.snapshot().confidence());
    }

    @Test
    void confidenceBoundaryAtExactlyTwoHundred() {
        RollingWindow window = new RollingWindow(1000);

        // 199 samples: medium
        for (int i = 0; i < 199; i++) {
            window.record(1.0);
        }
        assertEquals("medium", window.snapshot().confidence());

        // Add the 200th sample: high
        window.record(1.0);
        assertEquals("high", window.snapshot().confidence());
    }

    // ── ageMs tests ─────────────────────────────────────────────────────

    @Test
    void ageMsTracksFirstSampleTime() {
        RollingWindow window = new RollingWindow(100);

        window.record(42.0);

        // Small sleep to ensure ageMs is measurable
        RollingWindow.Snapshot snapshot = window.snapshot();

        // ageMs should be non-negative (could be 0 if very fast, but >= 0)
        assertTrue(snapshot.ageMs() >= 0, "ageMs should be non-negative after first sample");
    }

    @Test
    void ageMsGrowsOverTime() throws InterruptedException {
        RollingWindow window = new RollingWindow(100);

        window.record(42.0);
        Thread.sleep(50); // Wait a bit

        RollingWindow.Snapshot snapshot = window.snapshot();

        // After 50ms sleep, ageMs should be at least 40ms (allowing for timing variance)
        assertTrue(snapshot.ageMs() >= 30,
                "ageMs should reflect elapsed time since first sample, was: " + snapshot.ageMs());
    }

    @Test
    void ageMsDoesNotResetOnSubsequentSamples() throws InterruptedException {
        RollingWindow window = new RollingWindow(100);

        window.record(1.0);
        Thread.sleep(50);
        window.record(2.0); // Second sample should not reset firstSampleTimeMs

        RollingWindow.Snapshot snapshot = window.snapshot();

        // ageMs should still reflect time since FIRST sample
        assertTrue(snapshot.ageMs() >= 30,
                "ageMs should reflect time since first sample, not last sample. Was: " + snapshot.ageMs());
    }

    @Test
    void emptySnapshotReturnsZeroAgeMs() {
        RollingWindow window = new RollingWindow(100);

        RollingWindow.Snapshot snapshot = window.snapshot();

        assertEquals(0, snapshot.ageMs());
        assertTrue(snapshot.isEmpty());
    }

    // ── confidence on EMPTY snapshot ────────────────────────────────────

    @Test
    void emptySnapshotHasLowConfidence() {
        RollingWindow window = new RollingWindow(100);

        RollingWindow.Snapshot snapshot = window.snapshot();

        assertTrue(snapshot.isEmpty());
        assertEquals(0, snapshot.sampleCount());
        assertEquals("low", snapshot.confidence());
    }
}
