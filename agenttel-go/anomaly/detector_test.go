package anomaly

import (
	"testing"

	"go.agenttel.dev/agenttel-go/enums"
	"go.agenttel.dev/agenttel-go/models"
)

func TestDetector_NormalValue(t *testing.T) {
	d := NewDetector(3.0)
	result := d.Evaluate("latency", 100, 100, 10)

	if result.IsAnomaly {
		t.Error("expected no anomaly for value at mean")
	}
	if result.AnomalyScore != 0 {
		t.Errorf("expected anomaly score=0, got %f", result.AnomalyScore)
	}
}

func TestDetector_AnomalousValue(t *testing.T) {
	d := NewDetector(3.0)
	// z-score = (200 - 100) / 10 = 10
	result := d.Evaluate("latency", 200, 100, 10)

	if !result.IsAnomaly {
		t.Error("expected anomaly for z-score=10")
	}
	if result.AnomalyScore <= 0 {
		t.Errorf("expected positive anomaly score, got %f", result.AnomalyScore)
	}
	if result.ZScore != 10.0 {
		t.Errorf("expected z-score=10, got %f", result.ZScore)
	}
}

func TestDetector_ZeroStddev(t *testing.T) {
	d := NewDetector(3.0)
	result := d.Evaluate("latency", 200, 100, 0)

	if result.IsAnomaly {
		t.Error("should not detect anomaly with zero stddev")
	}
}

func TestDetector_NegativeZScore(t *testing.T) {
	d := NewDetector(3.0)
	// Value much lower than mean
	result := d.Evaluate("latency", 10, 100, 10)

	if !result.IsAnomaly {
		t.Error("expected anomaly for z-score=-9")
	}
	if result.ZScore >= 0 {
		t.Error("expected negative z-score")
	}
}

func TestPatternMatcher_CascadeFailure(t *testing.T) {
	pm := NewPatternMatcher(2.0, 0.1, 3)

	pm.RecordDependencyError("svc-a")
	pm.RecordDependencyError("svc-b")
	pm.RecordDependencyError("svc-c")

	patterns := pm.DetectPatterns("op1", 100, false, models.RollingSnapshot{})

	found := false
	for _, p := range patterns {
		if p == enums.IncidentPatternCascadeFailure {
			found = true
		}
	}
	if !found {
		t.Error("expected CASCADE_FAILURE pattern")
	}
}

func TestPatternMatcher_ErrorRateSpike(t *testing.T) {
	pm := NewPatternMatcher(2.0, 0.1, 3)

	snap := models.RollingSnapshot{
		Mean:        100,
		Stddev:      10,
		ErrorRate:   0.15,
		SampleCount: 100,
	}

	patterns := pm.DetectPatterns("op1", 100, false, snap)

	found := false
	for _, p := range patterns {
		if p == enums.IncidentPatternErrorRateSpike {
			found = true
		}
	}
	if !found {
		t.Error("expected ERROR_RATE_SPIKE pattern")
	}
}

func TestPatternMatcher_LatencyDegradation(t *testing.T) {
	pm := NewPatternMatcher(2.0, 0.1, 3)

	snap := models.RollingSnapshot{
		Mean:        100,
		Stddev:      10,
		SampleCount: 100,
	}

	patterns := pm.DetectPatterns("op1", 250, false, snap)

	found := false
	for _, p := range patterns {
		if p == enums.IncidentPatternLatencyDegradation {
			found = true
		}
	}
	if !found {
		t.Error("expected LATENCY_DEGRADATION pattern")
	}
}

func TestPatternMatcher_Reset(t *testing.T) {
	pm := NewPatternMatcher(2.0, 0.1, 3)

	pm.RecordDependencyError("svc-a")
	pm.RecordDependencyError("svc-b")
	pm.RecordDependencyError("svc-c")
	pm.ResetDependencyErrors()

	patterns := pm.DetectPatterns("op1", 100, false, models.RollingSnapshot{})

	for _, p := range patterns {
		if p == enums.IncidentPatternCascadeFailure {
			t.Error("should not detect cascade failure after reset")
		}
	}
}
