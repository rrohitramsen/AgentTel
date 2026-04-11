package agentic

import (
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"

	agattr "go.agenttel.dev/agenttel-go/attributes"
)

// StepScope represents a single reasoning step.
type StepScope struct {
	span        trace.Span
	description string
}

// ToolName sets the tool name for action steps.
func (s *StepScope) ToolName(name string) *StepScope {
	s.span.SetAttributes(attribute.String(agattr.AgenticStepToolName, name))
	return s
}

// ToolStatus sets the tool execution status.
func (s *StepScope) ToolStatus(status string) *StepScope {
	s.span.SetAttributes(attribute.String(agattr.AgenticStepToolStatus, status))
	return s
}

// Error records an error on the step.
func (s *StepScope) Error(err error) {
	s.span.RecordError(err)
}

// End completes the step scope.
func (s *StepScope) End() {
	s.span.End()
}

// Span returns the underlying OTel span.
func (s *StepScope) Span() trace.Span { return s.span }

// TaskScope represents a task or subtask in agent decomposition.
type TaskScope struct {
	ctx    interface{} // context.Context, stored opaquely
	span   trace.Span
	tracer trace.Tracer
	depth  int64
}

// Complete marks the task as completed.
func (t *TaskScope) Complete() {
	t.span.SetAttributes(attribute.String(agattr.AgenticTaskStatus, "completed"))
}

// Fail marks the task as failed.
func (t *TaskScope) Fail(reason string) {
	t.span.SetAttributes(attribute.String(agattr.AgenticTaskStatus, "failed"))
	t.span.SetAttributes(attribute.String("agenttel.agentic.task.failure_reason", reason))
}

// End completes the task scope.
func (t *TaskScope) End() {
	t.span.End()
}

// Span returns the underlying OTel span.
func (t *TaskScope) Span() trace.Span { return t.span }

// HandoffScope represents an agent-to-agent handoff.
type HandoffScope struct {
	span trace.Span
}

// End completes the handoff scope.
func (h *HandoffScope) End() { h.span.End() }

// Span returns the underlying OTel span.
func (h *HandoffScope) Span() trace.Span { return h.span }

// GuardrailScope records a guardrail evaluation.
type GuardrailScope struct {
	span trace.Span
}

// Triggered marks the guardrail as triggered.
func (g *GuardrailScope) Triggered(action, reason string) {
	g.span.SetAttributes(
		attribute.Bool(agattr.AgenticGuardrailTriggered, true),
		attribute.String(agattr.AgenticGuardrailAction, action),
		attribute.String(agattr.AgenticGuardrailReason, reason),
	)
}

// End completes the guardrail scope.
func (g *GuardrailScope) End() { g.span.End() }

// Span returns the underlying OTel span.
func (g *GuardrailScope) Span() trace.Span { return g.span }
