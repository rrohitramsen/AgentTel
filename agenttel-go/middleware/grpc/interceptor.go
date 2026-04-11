// Package grpc provides gRPC server interceptors for AgentTel span enrichment.
package grpc

import (
	"context"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"
	"google.golang.org/grpc"

	attrs "go.agenttel.dev/agenttel-go/attributes"
	"go.agenttel.dev/agenttel-go/baseline"
	"go.agenttel.dev/agenttel-go/topology"
)

// Config holds interceptor configuration.
type Config struct {
	BaselineProvider baseline.Provider
	Topology         *topology.Registry
}

// Option configures the interceptor.
type Option func(*Config)

// WithBaselineProvider sets the baseline provider.
func WithBaselineProvider(p baseline.Provider) Option {
	return func(c *Config) { c.BaselineProvider = p }
}

// WithTopology sets the topology registry.
func WithTopology(t *topology.Registry) Option {
	return func(c *Config) { c.Topology = t }
}

// UnaryServerInterceptor returns a gRPC unary server interceptor that
// enriches the current span with AgentTel attributes.
func UnaryServerInterceptor(opts ...Option) grpc.UnaryServerInterceptor {
	cfg := buildConfig(opts)

	return func(
		ctx context.Context,
		req interface{},
		info *grpc.UnaryServerInfo,
		handler grpc.UnaryHandler,
	) (interface{}, error) {
		span := trace.SpanFromContext(ctx)
		enrichSpan(span, info.FullMethod, cfg)
		return handler(ctx, req)
	}
}

// StreamServerInterceptor returns a gRPC stream server interceptor that
// enriches the current span with AgentTel attributes.
func StreamServerInterceptor(opts ...Option) grpc.StreamServerInterceptor {
	cfg := buildConfig(opts)

	return func(
		srv interface{},
		ss grpc.ServerStream,
		info *grpc.StreamServerInfo,
		handler grpc.StreamHandler,
	) error {
		span := trace.SpanFromContext(ss.Context())
		enrichSpan(span, info.FullMethod, cfg)
		return handler(srv, ss)
	}
}

func buildConfig(opts []Option) *Config {
	cfg := &Config{}
	for _, opt := range opts {
		opt(cfg)
	}
	return cfg
}

func enrichSpan(span trace.Span, fullMethod string, cfg *Config) {
	// Topology
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

	// Baselines
	if cfg.BaselineProvider != nil {
		if b, ok := cfg.BaselineProvider.GetBaseline(fullMethod); ok {
			span.SetAttributes(
				attribute.Float64(attrs.BaselineLatencyP50Ms, b.LatencyP50Ms),
				attribute.Float64(attrs.BaselineLatencyP99Ms, b.LatencyP99Ms),
				attribute.Float64(attrs.BaselineErrorRate, b.ErrorRate),
				attribute.String(attrs.BaselineSource, b.Source),
			)
		}
	}
}
