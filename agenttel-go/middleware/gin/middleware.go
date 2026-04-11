// Package gin provides Gin framework middleware for AgentTel span enrichment.
package gin

import (
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"

	attrs "go.agenttel.dev/agenttel-go/attributes"
	"go.agenttel.dev/agenttel-go/baseline"
	"go.agenttel.dev/agenttel-go/topology"
)

// GinContext is a minimal interface for the gin.Context methods we need,
// to avoid a hard dependency on the gin package.
type GinContext interface {
	Request() interface{ Method() string }
	FullPath() string
	Next()
}

// Config holds middleware configuration.
type Config struct {
	BaselineProvider baseline.Provider
	Topology         *topology.Registry
}

// Option configures the middleware.
type Option func(*Config)

// WithBaselineProvider sets the baseline provider.
func WithBaselineProvider(p baseline.Provider) Option {
	return func(c *Config) { c.BaselineProvider = p }
}

// WithTopology sets the topology registry.
func WithTopology(t *topology.Registry) Option {
	return func(c *Config) { c.Topology = t }
}

// EnrichSpan enriches an OTel span with AgentTel topology and baseline attributes.
// This is a helper that can be called from any Gin middleware.
func EnrichSpan(span trace.Span, method, routePattern string, cfg *Config) {
	if cfg == nil {
		return
	}

	// Topology enrichment
	if cfg.Topology != nil {
		if team := cfg.Topology.Team(); team != "" {
			span.SetAttributes(attribute.String(attrs.TopologyTeam, team))
		}
		if tier := cfg.Topology.Tier(); tier != "" {
			span.SetAttributes(attribute.String(attrs.TopologyTier, tier))
		}
		if domain := cfg.Topology.Domain(); domain != "" {
			span.SetAttributes(attribute.String(attrs.TopologyDomain, domain))
		}
	}

	// Baseline enrichment
	opName := method + " " + routePattern
	if cfg.BaselineProvider != nil && opName != "" {
		if b, ok := cfg.BaselineProvider.GetBaseline(opName); ok {
			span.SetAttributes(
				attribute.Float64(attrs.BaselineLatencyP50Ms, b.LatencyP50Ms),
				attribute.Float64(attrs.BaselineLatencyP99Ms, b.LatencyP99Ms),
				attribute.Float64(attrs.BaselineErrorRate, b.ErrorRate),
				attribute.String(attrs.BaselineSource, b.Source),
			)
		}
	}
}
