// Package models defines data structures shared across the AgentTel Go SDK.
package models

import "time"

// OperationBaseline holds expected operational norms for an operation.
type OperationBaseline struct {
	OperationName string
	LatencyP50Ms  float64
	LatencyP99Ms  float64
	ErrorRate     float64
	Source        string // BaselineSource value
	UpdatedAt     time.Time
}

// DependencyDescriptor describes a service dependency with its metadata.
type DependencyDescriptor struct {
	Name           string
	Type           string // DependencyType value
	Criticality    string // DependencyCriticality value
	Protocol       string
	TimeoutMs      int
	CircuitBreaker bool
	Fallback       string
	HealthEndpoint string
}

// ConsumerDescriptor describes a downstream consumer of a service.
type ConsumerDescriptor struct {
	Name         string
	Pattern      string // ConsumptionPattern value
	SLALatencyMs int
}

// SLODefinition defines a service level objective.
type SLODefinition struct {
	Name          string
	OperationName string
	Type          string // SLOType value
	Target        float64
	WindowSeconds int64
}

// AnomalyResult holds the result of an anomaly evaluation.
type AnomalyResult struct {
	AnomalyScore float64
	IsAnomaly    bool
	ZScore       float64
}

// Normal returns an AnomalyResult indicating no anomaly.
func Normal() AnomalyResult {
	return AnomalyResult{AnomalyScore: 0, IsAnomaly: false, ZScore: 0}
}

// SLOStatus holds the current status of an SLO.
type SLOStatus struct {
	SLOName         string
	Target          float64
	Actual          float64
	BudgetRemaining float64
	BurnRate        float64
	TotalRequests   int64
	FailedRequests  int64
}

// IsWithinBudget returns true if the SLO budget has not been exhausted.
func (s SLOStatus) IsWithinBudget() bool {
	return s.BudgetRemaining > 0
}

// SLOAlert represents an SLO budget alert.
type SLOAlert struct {
	SLOName         string
	Severity        string // AlertSeverity value
	BudgetRemaining float64
	BurnRate        float64
}

// ErrorClassification holds the result of classifying a span error.
type ErrorClassification struct {
	Category      string // ErrorCategory value
	RootException string
	Dependency    string
}

// RollingSnapshot holds a point-in-time snapshot of a rolling window.
type RollingSnapshot struct {
	Mean        float64
	Stddev      float64
	P50         float64
	P95         float64
	P99         float64
	ErrorRate   float64
	SampleCount int
	AgeMs       int64
}

// IsEmpty returns true if no samples have been recorded.
func (s RollingSnapshot) IsEmpty() bool {
	return s.SampleCount == 0
}

// Confidence returns the confidence level based on sample count.
func (s RollingSnapshot) Confidence() string {
	switch {
	case s.SampleCount < 30:
		return "low"
	case s.SampleCount < 200:
		return "medium"
	default:
		return "high"
	}
}
