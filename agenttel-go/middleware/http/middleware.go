// Package http provides net/http middleware for AgentTel span enrichment.
package http

import (
	"net/http"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"

	attrs "go.agenttel.dev/agenttel-go/attributes"
	"go.agenttel.dev/agenttel-go/baseline"
	"go.agenttel.dev/agenttel-go/topology"
)

// Config holds middleware configuration.
type Config struct {
	BaselineProvider baseline.Provider
	Topology         *topology.Registry
	RouteResolver    func(r *http.Request) string
}

// Option configures the middleware.
type Option func(*Config)

// WithBaselineProvider sets the baseline provider for enrichment.
func WithBaselineProvider(p baseline.Provider) Option {
	return func(c *Config) { c.BaselineProvider = p }
}

// WithTopology sets the topology registry for enrichment.
func WithTopology(t *topology.Registry) Option {
	return func(c *Config) { c.Topology = t }
}

// WithRouteResolver sets a custom route pattern resolver.
func WithRouteResolver(fn func(r *http.Request) string) Option {
	return func(c *Config) { c.RouteResolver = fn }
}

// Middleware returns an http.Handler that enriches the current OTel span with AgentTel attributes.
func Middleware(opts ...Option) func(http.Handler) http.Handler {
	cfg := &Config{}
	for _, opt := range opts {
		opt(cfg)
	}

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			start := time.Now()
			span := trace.SpanFromContext(r.Context())

			// Resolve operation name
			opName := resolveOperationName(r, cfg)

			// Enrich with topology
			if cfg.Topology != nil {
				enrichTopology(span, cfg.Topology)
			}

			// Enrich with baselines
			if cfg.BaselineProvider != nil && opName != "" {
				enrichBaseline(span, cfg.BaselineProvider, opName)
			}

			// Wrap response writer to capture status
			rw := &responseWriter{ResponseWriter: w, statusCode: http.StatusOK}
			next.ServeHTTP(rw, r)

			_ = time.Since(start)
			_ = otel.Tracer("agenttel")
		})
	}
}

func resolveOperationName(r *http.Request, cfg *Config) string {
	if cfg.RouteResolver != nil {
		return cfg.RouteResolver(r)
	}
	return r.Method + " " + r.URL.Path
}

func enrichTopology(span trace.Span, t *topology.Registry) {
	if team := t.Team(); team != "" {
		span.SetAttributes(attribute.String(attrs.TopologyTeam, team))
	}
	if tier := t.Tier(); tier != "" {
		span.SetAttributes(attribute.String(attrs.TopologyTier, tier))
	}
	if domain := t.Domain(); domain != "" {
		span.SetAttributes(attribute.String(attrs.TopologyDomain, domain))
	}
}

func enrichBaseline(span trace.Span, provider baseline.Provider, opName string) {
	b, ok := provider.GetBaseline(opName)
	if !ok {
		return
	}
	span.SetAttributes(
		attribute.Float64(attrs.BaselineLatencyP50Ms, b.LatencyP50Ms),
		attribute.Float64(attrs.BaselineLatencyP99Ms, b.LatencyP99Ms),
		attribute.Float64(attrs.BaselineErrorRate, b.ErrorRate),
		attribute.String(attrs.BaselineSource, b.Source),
	)
}

type responseWriter struct {
	http.ResponseWriter
	statusCode int
}

func (rw *responseWriter) WriteHeader(code int) {
	rw.statusCode = code
	rw.ResponseWriter.WriteHeader(code)
}
