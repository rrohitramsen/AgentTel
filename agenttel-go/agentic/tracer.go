// Package agentic provides agentic observability: agent tracing, scopes, and orchestration patterns.
package agentic

import (
	"context"
	"sync/atomic"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"

	agattr "go.agenttel.dev/agenttel/attributes"
	"go.agenttel.dev/agenttel/enums"
)

// AgentTracer creates agent invocation traces.
type AgentTracer struct {
	tracer       trace.Tracer
	agentName    string
	agentType    enums.AgentType
	framework    string
	agentVersion string
}

// TracerBuilder builds an AgentTracer.
type TracerBuilder struct {
	tracer       trace.Tracer
	agentName    string
	agentType    enums.AgentType
	framework    string
	agentVersion string
}

// NewTracer creates a TracerBuilder.
func NewTracer(tracer trace.Tracer) *TracerBuilder {
	return &TracerBuilder{tracer: tracer, agentType: enums.AgentTypeSingle}
}

// AgentName sets the agent name.
func (b *TracerBuilder) AgentName(name string) *TracerBuilder { b.agentName = name; return b }

// AgentType sets the agent type.
func (b *TracerBuilder) AgentType(t enums.AgentType) *TracerBuilder { b.agentType = t; return b }

// Framework sets the agent framework.
func (b *TracerBuilder) Framework(f string) *TracerBuilder { b.framework = f; return b }

// Version sets the agent version.
func (b *TracerBuilder) Version(v string) *TracerBuilder { b.agentVersion = v; return b }

// Build creates the AgentTracer.
func (b *TracerBuilder) Build() *AgentTracer {
	return &AgentTracer{
		tracer:       b.tracer,
		agentName:    b.agentName,
		agentType:    b.agentType,
		framework:    b.framework,
		agentVersion: b.agentVersion,
	}
}

// Invoke starts a new agent invocation span.
func (t *AgentTracer) Invoke(ctx context.Context, goal string) (context.Context, *Invocation) {
	ctx, span := t.tracer.Start(ctx, "invoke_agent", trace.WithSpanKind(trace.SpanKindInternal))

	span.SetAttributes(
		attribute.String(agattr.AgenticAgentName, t.agentName),
		attribute.String(agattr.AgenticAgentType, string(t.agentType)),
		attribute.String(agattr.AgenticInvocationGoal, goal),
		attribute.String(agattr.AgenticInvocationStatus, string(enums.InvocationStatusRunning)),
	)
	if t.framework != "" {
		span.SetAttributes(attribute.String(agattr.AgenticAgentFramework, t.framework))
	}
	if t.agentVersion != "" {
		span.SetAttributes(attribute.String(agattr.AgenticAgentVersion, t.agentVersion))
	}

	return ctx, &Invocation{
		ctx:    ctx,
		span:   span,
		tracer: t.tracer,
	}
}

// Invocation represents an agent invocation scope.
type Invocation struct {
	ctx       context.Context
	span      trace.Span
	tracer    trace.Tracer
	stepCount atomic.Int64
}

// Step creates a new reasoning step within the invocation.
func (inv *Invocation) Step(ctx context.Context, stepType enums.StepType, description string) (context.Context, *StepScope) {
	num := inv.stepCount.Add(1)
	ctx, span := inv.tracer.Start(ctx, "agenttel.agentic.step", trace.WithSpanKind(trace.SpanKindInternal))

	span.SetAttributes(
		attribute.Int64(agattr.AgenticStepNumber, num),
		attribute.String(agattr.AgenticStepType, string(stepType)),
	)

	return ctx, &StepScope{span: span, description: description}
}

// Task creates a new task scope within the invocation.
func (inv *Invocation) Task(ctx context.Context, taskName string) (context.Context, *TaskScope) {
	ctx, span := inv.tracer.Start(ctx, "agenttel.agentic.task", trace.WithSpanKind(trace.SpanKindInternal))

	span.SetAttributes(
		attribute.String(agattr.AgenticTaskName, taskName),
		attribute.String(agattr.AgenticTaskStatus, "in_progress"),
		attribute.Int64(agattr.AgenticTaskDepth, 0),
	)

	return ctx, &TaskScope{ctx: ctx, span: span, tracer: inv.tracer, depth: 0}
}

// End completes the invocation with success status.
func (inv *Invocation) End() {
	inv.span.SetAttributes(
		attribute.String(agattr.AgenticInvocationStatus, string(enums.InvocationStatusCompleted)),
		attribute.Int64(agattr.AgenticInvocationSteps, inv.stepCount.Load()),
	)
	inv.span.End()
}

// EndError completes the invocation with error status.
func (inv *Invocation) EndError(err error) {
	inv.span.SetAttributes(
		attribute.String(agattr.AgenticInvocationStatus, string(enums.InvocationStatusFailed)),
		attribute.Int64(agattr.AgenticInvocationSteps, inv.stepCount.Load()),
	)
	inv.span.RecordError(err)
	inv.span.End()
}

// Span returns the underlying OTel span.
func (inv *Invocation) Span() trace.Span { return inv.span }
