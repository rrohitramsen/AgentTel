// Package agenttel provides the top-level AgentTel engine for Go services.
//
// AgentTel enriches OpenTelemetry spans with agent-ready observability metadata
// including baselines, anomaly detection, SLO tracking, error classification,
// and topology context.
//
// Usage:
//
//	engine, err := agenttel.New().
//	    WithConfigFile("agenttel.yml").
//	    Build()
//
//	tp := sdktrace.NewTracerProvider(
//	    sdktrace.WithSpanProcessor(engine.SpanProcessor()),
//	)
package agenttel

import (
	"go.agenttel.dev/agenttel-go/anomaly"
	"go.agenttel.dev/agenttel-go/baseline"
	"go.agenttel.dev/agenttel-go/errorclass"
	"go.agenttel.dev/agenttel-go/events"
	"go.agenttel.dev/agenttel-go/models"
	"go.agenttel.dev/agenttel-go/processor"
	"go.agenttel.dev/agenttel-go/slo"
	"go.agenttel.dev/agenttel-go/topology"
)

// Engine is the top-level orchestrator for all AgentTel components.
type Engine struct {
	Config           AgentTelConfig
	TopologyRegistry *topology.Registry
	BaselineProvider baseline.Provider
	RollingProvider  *baseline.RollingProvider
	AnomalyDetector  *anomaly.Detector
	PatternMatcher   *anomaly.PatternMatcher
	SLOTracker       *slo.Tracker
	ErrorClassifier  *errorclass.Classifier
	EventEmitter     *events.Emitter
	spanProcessor    *processor.AgentTelSpanProcessor
}

// SpanProcessor returns the OTel SpanProcessor for use with TracerProvider.
func (e *Engine) SpanProcessor() *processor.AgentTelSpanProcessor {
	return e.spanProcessor
}

// Builder constructs an Engine with the desired components.
type Builder struct {
	config           *AgentTelConfig
	configFile       string
	topologyRegistry *topology.Registry
	staticBaselines  map[string]models.OperationBaseline
	sloDefinitions   []models.SLODefinition
}

// New creates a new Engine builder.
func New() *Builder {
	return &Builder{}
}

// WithConfig sets the configuration directly.
func (b *Builder) WithConfig(cfg AgentTelConfig) *Builder {
	b.config = &cfg
	return b
}

// WithConfigFile sets the path to load configuration from.
func (b *Builder) WithConfigFile(path string) *Builder {
	b.configFile = path
	return b
}

// WithTopology sets a pre-configured topology registry.
func (b *Builder) WithTopology(r *topology.Registry) *Builder {
	b.topologyRegistry = r
	return b
}

// WithStaticBaselines adds static baseline data.
func (b *Builder) WithStaticBaselines(baselines map[string]models.OperationBaseline) *Builder {
	b.staticBaselines = baselines
	return b
}

// WithSLOs registers SLO definitions.
func (b *Builder) WithSLOs(slos ...models.SLODefinition) *Builder {
	b.sloDefinitions = append(b.sloDefinitions, slos...)
	return b
}

// Build constructs the Engine with all configured components.
func (b *Builder) Build() (*Engine, error) {
	// Load config
	var cfg AgentTelConfig
	if b.config != nil {
		cfg = *b.config
	} else if b.configFile != "" {
		var err error
		cfg, err = LoadConfig(b.configFile)
		if err != nil {
			return nil, err
		}
	} else {
		var err error
		cfg, err = LoadConfigFromEnv()
		if err != nil {
			cfg = DefaultConfig()
		}
	}

	// Topology
	topoReg := b.topologyRegistry
	if topoReg == nil {
		topoReg = topology.NewRegistry()
	}
	if cfg.Topology.Team != "" {
		topoReg.SetTeam(cfg.Topology.Team)
	}
	if cfg.Topology.Tier != "" {
		topoReg.SetTier(cfg.Topology.Tier)
	}
	if cfg.Topology.Domain != "" {
		topoReg.SetDomain(cfg.Topology.Domain)
	}
	if cfg.Topology.OnCallChannel != "" {
		topoReg.SetOnCallChannel(cfg.Topology.OnCallChannel)
	}
	if cfg.Topology.RepoURL != "" {
		topoReg.SetRepoURL(cfg.Topology.RepoURL)
	}
	for _, dep := range cfg.Dependencies {
		topoReg.RegisterDependency(models.DependencyDescriptor{
			Name:           dep.Name,
			Type:           dep.Type,
			Criticality:    dep.Criticality,
			Protocol:       dep.Protocol,
			TimeoutMs:      dep.TimeoutMs,
			CircuitBreaker: dep.CircuitBreaker,
			Fallback:       dep.Fallback,
			HealthEndpoint: dep.HealthEndpoint,
		})
	}
	for _, con := range cfg.Consumers {
		topoReg.RegisterConsumer(models.ConsumerDescriptor{
			Name:         con.Name,
			Pattern:      con.Pattern,
			SLALatencyMs: con.SLALatencyMs,
		})
	}

	// Baselines
	rollingProvider := baseline.NewRollingProvider(
		cfg.Baselines.RollingWindowSize,
		cfg.Baselines.RollingMinSamples,
	)

	var baseProvider baseline.Provider
	if len(b.staticBaselines) > 0 {
		baseProvider = baseline.NewCompositeProvider(
			baseline.NewStaticProvider(b.staticBaselines),
			rollingProvider,
		)
	} else {
		baseProvider = rollingProvider
	}

	// Anomaly
	detector := anomaly.NewDetector(cfg.AnomalyDetection.ZScoreThreshold)
	patternMatcher := anomaly.NewPatternMatcher(2.0, 0.1, 3)

	// SLO
	sloTracker := slo.NewTracker()
	for _, def := range b.sloDefinitions {
		sloTracker.Register(def)
	}

	// Error classifier
	errorClassifier := errorclass.NewClassifier()

	// Events
	eventEmitter := events.NewEmitter()

	// Span processor
	sp := processor.New(
		processor.WithBaseline(baseProvider),
		processor.WithRolling(rollingProvider),
		processor.WithAnomalyDetector(detector),
		processor.WithPatternMatcher(patternMatcher),
		processor.WithSLOTracker(sloTracker),
		processor.WithErrorClassifier(errorClassifier),
		processor.WithTopology(topoReg),
		processor.WithEventEmitter(eventEmitter),
	)

	return &Engine{
		Config:           cfg,
		TopologyRegistry: topoReg,
		BaselineProvider: baseProvider,
		RollingProvider:  rollingProvider,
		AnomalyDetector:  detector,
		PatternMatcher:   patternMatcher,
		SLOTracker:       sloTracker,
		ErrorClassifier:  errorClassifier,
		EventEmitter:     eventEmitter,
		spanProcessor:    sp,
	}, nil
}
