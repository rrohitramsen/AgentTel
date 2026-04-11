// Package slo provides SLO tracking with budget and burn rate calculations.
package slo

import (
	"sync"

	"go.agenttel.dev/agenttel-go/enums"
	"go.agenttel.dev/agenttel-go/models"
)

type sloState struct {
	definition    models.SLODefinition
	totalRequests int64
	failedRequests int64
}

// Tracker tracks SLO compliance, budget, and burn rate for registered SLOs.
type Tracker struct {
	mu    sync.RWMutex
	slos  map[string]*sloState
	order []string // insertion order
}

// NewTracker creates an empty SLO tracker.
func NewTracker() *Tracker {
	return &Tracker{
		slos: make(map[string]*sloState),
	}
}

// Register adds an SLO definition to track.
func (t *Tracker) Register(slo models.SLODefinition) {
	t.mu.Lock()
	defer t.mu.Unlock()

	if _, exists := t.slos[slo.Name]; !exists {
		t.order = append(t.order, slo.Name)
	}
	t.slos[slo.Name] = &sloState{definition: slo}
}

// RecordSuccess records a successful request for all matching SLOs.
func (t *Tracker) RecordSuccess(operationName string) {
	t.mu.Lock()
	defer t.mu.Unlock()

	for _, s := range t.slos {
		if s.definition.OperationName == operationName || s.definition.OperationName == "" {
			s.totalRequests++
		}
	}
}

// RecordFailure records a failed request for all matching SLOs.
func (t *Tracker) RecordFailure(operationName string) {
	t.mu.Lock()
	defer t.mu.Unlock()

	for _, s := range t.slos {
		if s.definition.OperationName == operationName || s.definition.OperationName == "" {
			s.totalRequests++
			s.failedRequests++
		}
	}
}

// RecordLatency records a latency observation, failing latency SLOs if threshold exceeded.
func (t *Tracker) RecordLatency(operationName string, latencyMs, thresholdMs float64) {
	t.mu.Lock()
	defer t.mu.Unlock()

	for _, s := range t.slos {
		if s.definition.OperationName != operationName && s.definition.OperationName != "" {
			continue
		}
		sloType := enums.SLOType(s.definition.Type)
		if sloType == enums.SLOTypeLatencyP99 || sloType == enums.SLOTypeLatencyP50 {
			s.totalRequests++
			if latencyMs > thresholdMs {
				s.failedRequests++
			}
		}
	}
}

// GetStatuses returns the current status of all tracked SLOs.
func (t *Tracker) GetStatuses() []models.SLOStatus {
	t.mu.RLock()
	defer t.mu.RUnlock()

	statuses := make([]models.SLOStatus, 0, len(t.order))
	for _, name := range t.order {
		s := t.slos[name]
		statuses = append(statuses, t.computeStatus(s))
	}
	return statuses
}

// GetStatus returns the status of a specific SLO by name.
func (t *Tracker) GetStatus(sloName string) (models.SLOStatus, bool) {
	t.mu.RLock()
	defer t.mu.RUnlock()

	s, ok := t.slos[sloName]
	if !ok {
		return models.SLOStatus{}, false
	}
	return t.computeStatus(s), true
}

// CheckAlerts returns alerts for SLOs with budget below thresholds.
func (t *Tracker) CheckAlerts() []models.SLOAlert {
	t.mu.RLock()
	defer t.mu.RUnlock()

	var alerts []models.SLOAlert
	for _, name := range t.order {
		s := t.slos[name]
		status := t.computeStatus(s)

		var severity enums.AlertSeverity
		switch {
		case status.BudgetRemaining <= 0.10:
			severity = enums.AlertSeverityCritical
		case status.BudgetRemaining <= 0.25:
			severity = enums.AlertSeverityWarning
		case status.BudgetRemaining <= 0.50:
			severity = enums.AlertSeverityInfo
		default:
			continue
		}

		alerts = append(alerts, models.SLOAlert{
			SLOName:         name,
			Severity:        string(severity),
			BudgetRemaining: status.BudgetRemaining,
			BurnRate:        status.BurnRate,
		})
	}
	return alerts
}

func (t *Tracker) computeStatus(s *sloState) models.SLOStatus {
	if s.totalRequests == 0 {
		return models.SLOStatus{
			SLOName:         s.definition.Name,
			Target:          s.definition.Target,
			Actual:          1.0,
			BudgetRemaining: 1.0,
			BurnRate:        0,
			TotalRequests:   0,
			FailedRequests:  0,
		}
	}

	actual := 1.0 - float64(s.failedRequests)/float64(s.totalRequests)
	errorBudgetTotal := 1.0 - s.definition.Target
	errorBudgetUsed := float64(s.failedRequests) / float64(s.totalRequests)

	budgetRemaining := 1.0
	burnRate := 0.0
	if errorBudgetTotal > 0 {
		budgetRemaining = 1.0 - (errorBudgetUsed / errorBudgetTotal)
		if budgetRemaining < 0 {
			budgetRemaining = 0
		}
		burnRate = errorBudgetUsed / errorBudgetTotal
	}

	return models.SLOStatus{
		SLOName:         s.definition.Name,
		Target:          s.definition.Target,
		Actual:          actual,
		BudgetRemaining: budgetRemaining,
		BurnRate:        burnRate,
		TotalRequests:   s.totalRequests,
		FailedRequests:  s.failedRequests,
	}
}
