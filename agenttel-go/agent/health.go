// Package agent provides the agent interface layer: health aggregation, incident context,
// remediation registry, and MCP server for AI agent interaction.
package agent

import (
	"sync"
	"time"
)

// HealthStatus represents the health of a service component.
type HealthStatus string

const (
	HealthStatusHealthy   HealthStatus = "healthy"
	HealthStatusDegraded  HealthStatus = "degraded"
	HealthStatusUnhealthy HealthStatus = "unhealthy"
	HealthStatusUnknown   HealthStatus = "unknown"
)

// ComponentHealth holds health data for a single component.
type ComponentHealth struct {
	Name      string       `json:"name"`
	Status    HealthStatus `json:"status"`
	Message   string       `json:"message,omitempty"`
	UpdatedAt time.Time    `json:"updated_at"`
}

// ServiceHealth holds aggregated health for a service.
type ServiceHealth struct {
	Overall    HealthStatus              `json:"overall"`
	Components map[string]ComponentHealth `json:"components"`
	UpdatedAt  time.Time                 `json:"updated_at"`
}

// HealthAggregator aggregates component health into overall service health.
type HealthAggregator struct {
	mu         sync.RWMutex
	components map[string]ComponentHealth
}

// NewHealthAggregator creates a new health aggregator.
func NewHealthAggregator() *HealthAggregator {
	return &HealthAggregator{
		components: make(map[string]ComponentHealth),
	}
}

// Report reports the health of a component.
func (h *HealthAggregator) Report(name string, status HealthStatus, message string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.components[name] = ComponentHealth{
		Name:      name,
		Status:    status,
		Message:   message,
		UpdatedAt: time.Now(),
	}
}

// GetHealth returns the aggregated service health.
func (h *HealthAggregator) GetHealth() ServiceHealth {
	h.mu.RLock()
	defer h.mu.RUnlock()

	components := make(map[string]ComponentHealth, len(h.components))
	overall := HealthStatusHealthy

	for k, v := range h.components {
		components[k] = v
		switch {
		case v.Status == HealthStatusUnhealthy:
			overall = HealthStatusUnhealthy
		case v.Status == HealthStatusDegraded && overall != HealthStatusUnhealthy:
			overall = HealthStatusDegraded
		}
	}

	return ServiceHealth{
		Overall:    overall,
		Components: components,
		UpdatedAt:  time.Now(),
	}
}
