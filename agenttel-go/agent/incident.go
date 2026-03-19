package agent

import (
	"time"

	"go.agenttel.dev/agenttel/enums"
	"go.agenttel.dev/agenttel/models"
)

// IncidentContext holds all context needed for incident investigation.
type IncidentContext struct {
	ServiceName  string                    `json:"service_name"`
	Patterns     []enums.IncidentPattern   `json:"patterns"`
	Anomalies    []models.AnomalyResult    `json:"anomalies"`
	SLOStatuses  []models.SLOStatus        `json:"slo_statuses"`
	SLOAlerts    []models.SLOAlert         `json:"slo_alerts"`
	Health       ServiceHealth             `json:"health"`
	StartedAt    time.Time                 `json:"started_at"`
	Dependencies []models.DependencyDescriptor `json:"dependencies"`
}

// IncidentContextBuilder constructs incident context from multiple sources.
type IncidentContextBuilder struct {
	ctx IncidentContext
}

// NewIncidentContextBuilder creates a new builder.
func NewIncidentContextBuilder(serviceName string) *IncidentContextBuilder {
	return &IncidentContextBuilder{
		ctx: IncidentContext{
			ServiceName: serviceName,
			StartedAt:   time.Now(),
		},
	}
}

// WithPatterns adds detected incident patterns.
func (b *IncidentContextBuilder) WithPatterns(patterns []enums.IncidentPattern) *IncidentContextBuilder {
	b.ctx.Patterns = patterns
	return b
}

// WithAnomalies adds detected anomalies.
func (b *IncidentContextBuilder) WithAnomalies(anomalies []models.AnomalyResult) *IncidentContextBuilder {
	b.ctx.Anomalies = anomalies
	return b
}

// WithSLOStatuses adds SLO compliance data.
func (b *IncidentContextBuilder) WithSLOStatuses(statuses []models.SLOStatus) *IncidentContextBuilder {
	b.ctx.SLOStatuses = statuses
	return b
}

// WithSLOAlerts adds SLO alerts.
func (b *IncidentContextBuilder) WithSLOAlerts(alerts []models.SLOAlert) *IncidentContextBuilder {
	b.ctx.SLOAlerts = alerts
	return b
}

// WithHealth adds health status.
func (b *IncidentContextBuilder) WithHealth(health ServiceHealth) *IncidentContextBuilder {
	b.ctx.Health = health
	return b
}

// WithDependencies adds dependency information.
func (b *IncidentContextBuilder) WithDependencies(deps []models.DependencyDescriptor) *IncidentContextBuilder {
	b.ctx.Dependencies = deps
	return b
}

// Build returns the completed incident context.
func (b *IncidentContextBuilder) Build() IncidentContext {
	return b.ctx
}
