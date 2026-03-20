package anomaly

import (
	"sync"
	"time"

	"go.agenttel.dev/agenttel/enums"
	"go.agenttel.dev/agenttel/models"
)

// PatternMatcher detects known incident patterns from telemetry observations.
type PatternMatcher struct {
	mu sync.Mutex

	latencyDegradationThreshold float64
	errorRateSpikeThreshold     float64
	cascadeFailureMinServices   int

	// dependency error tracking
	depErrors map[string]int

	// latency trend tracking per operation
	latencyTrends map[string]*latencyTrend
}

type latencyTrend struct {
	samples []float64
	times   []time.Time
}

// NewPatternMatcher creates a pattern matcher with configurable thresholds.
func NewPatternMatcher(latencyDegradation, errorRateSpike float64, cascadeMin int) *PatternMatcher {
	if latencyDegradation <= 0 {
		latencyDegradation = 2.0
	}
	if errorRateSpike <= 0 {
		errorRateSpike = 0.1
	}
	if cascadeMin <= 0 {
		cascadeMin = 3
	}
	return &PatternMatcher{
		latencyDegradationThreshold: latencyDegradation,
		errorRateSpikeThreshold:     errorRateSpike,
		cascadeFailureMinServices:   cascadeMin,
		depErrors:                   make(map[string]int),
		latencyTrends:               make(map[string]*latencyTrend),
	}
}

// RecordDependencyError records an error for a dependency.
func (p *PatternMatcher) RecordDependencyError(dependency string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.depErrors[dependency]++
}

// RecordLatency records a latency observation for trend analysis.
func (p *PatternMatcher) RecordLatency(operationName string, latencyMs float64) {
	p.mu.Lock()
	defer p.mu.Unlock()
	t, ok := p.latencyTrends[operationName]
	if !ok {
		t = &latencyTrend{}
		p.latencyTrends[operationName] = t
	}
	t.samples = append(t.samples, latencyMs)
	t.times = append(t.times, time.Now())
	// Keep bounded
	if len(t.samples) > 100 {
		t.samples = t.samples[len(t.samples)-100:]
		t.times = t.times[len(t.times)-100:]
	}
}

// ResetDependencyErrors clears dependency error counters.
func (p *PatternMatcher) ResetDependencyErrors() {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.depErrors = make(map[string]int)
}

// DetectPatterns analyzes current telemetry to identify incident patterns.
func (p *PatternMatcher) DetectPatterns(
	operationName string,
	currentLatencyMs float64,
	isError bool,
	snapshot models.RollingSnapshot,
) []enums.IncidentPattern {
	p.mu.Lock()
	defer p.mu.Unlock()

	var patterns []enums.IncidentPattern

	// CASCADE_FAILURE: multiple dependencies failing
	failingDeps := 0
	for _, count := range p.depErrors {
		if count > 0 {
			failingDeps++
		}
	}
	if failingDeps >= p.cascadeFailureMinServices {
		patterns = append(patterns, enums.IncidentPatternCascadeFailure)
	}

	if snapshot.IsEmpty() {
		return patterns
	}

	// ERROR_RATE_SPIKE: sudden increase beyond threshold
	if snapshot.ErrorRate > p.errorRateSpikeThreshold {
		patterns = append(patterns, enums.IncidentPatternErrorRateSpike)
	}

	// LATENCY_DEGRADATION: sustained latency increase
	if snapshot.Mean > 0 && currentLatencyMs > snapshot.Mean*p.latencyDegradationThreshold {
		patterns = append(patterns, enums.IncidentPatternLatencyDegradation)
	}

	// MEMORY_LEAK: monotonically increasing latency with rising errors
	if t, ok := p.latencyTrends[operationName]; ok && len(t.samples) >= 10 {
		if isMonotonicallyIncreasing(t.samples[len(t.samples)-10:]) && snapshot.ErrorRate > 0.01 {
			patterns = append(patterns, enums.IncidentPatternMemoryLeak)
		}
	}

	return patterns
}

func isMonotonicallyIncreasing(vals []float64) bool {
	if len(vals) < 3 {
		return false
	}
	increases := 0
	for i := 1; i < len(vals); i++ {
		if vals[i] > vals[i-1] {
			increases++
		}
	}
	return float64(increases)/float64(len(vals)-1) >= 0.8
}
