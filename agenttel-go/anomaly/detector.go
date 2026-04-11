// Package anomaly provides z-score anomaly detection and incident pattern matching.
package anomaly

import (
	"math"

	"go.agenttel.dev/agenttel-go/models"
)

// Detector performs z-score-based anomaly detection.
type Detector struct {
	zScoreThreshold float64
}

// NewDetector creates an anomaly detector with the given z-score threshold.
func NewDetector(zScoreThreshold float64) *Detector {
	if zScoreThreshold <= 0 {
		zScoreThreshold = 3.0
	}
	return &Detector{zScoreThreshold: zScoreThreshold}
}

// Evaluate determines if the current value is anomalous given baseline stats.
func (d *Detector) Evaluate(metric string, current, baselineMean, baselineStddev float64) models.AnomalyResult {
	if baselineStddev <= 0 {
		return models.Normal()
	}

	zScore := (current - baselineMean) / baselineStddev
	absZ := math.Abs(zScore)
	isAnomaly := absZ >= d.zScoreThreshold

	// Normalize anomaly score to 0-1 range
	anomalyScore := 0.0
	if isAnomaly {
		anomalyScore = math.Min(1.0, absZ/d.zScoreThreshold/2.0)
	}

	return models.AnomalyResult{
		AnomalyScore: anomalyScore,
		IsAnomaly:    isAnomaly,
		ZScore:       zScore,
	}
}
