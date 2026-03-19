// Package genai provides GenAI span builders and cost calculators for LLM instrumentation.
package genai

import (
	"context"
	"fmt"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"

	attrs "go.agenttel.dev/agenttel/attributes"
)

// SpanBuilder creates properly-formed GenAI spans following OTel semantic conventions.
type SpanBuilder struct {
	tracer trace.Tracer
}

// NewSpanBuilder creates a GenAI span builder.
func NewSpanBuilder(tracer trace.Tracer) *SpanBuilder {
	return &SpanBuilder{tracer: tracer}
}

// ChatResult holds the response data for ending a chat span.
type ChatResult struct {
	ResponseModel string
	ResponseID    string
	InputTokens   int64
	OutputTokens  int64
	FinishReasons []string
}

// TokenDetails holds extended token usage data.
type TokenDetails struct {
	ReasoningTokens  int64
	CachedReadTokens int64
	CachedWriteTokens int64
}

// StartChatSpan creates a new GenAI chat span.
func (b *SpanBuilder) StartChatSpan(ctx context.Context, model, system string) (context.Context, trace.Span) {
	spanName := fmt.Sprintf("chat %s", model)
	ctx, span := b.tracer.Start(ctx, spanName, trace.WithSpanKind(trace.SpanKindClient))

	span.SetAttributes(
		attribute.String(attrs.GenAIOperationName, "chat"),
		attribute.String(attrs.GenAISystem, system),
		attribute.String(attrs.GenAIRequestModel, model),
	)

	return ctx, span
}

// SetRequestParams sets optional request parameters on a span.
func SetRequestParams(span trace.Span, temperature float64, maxTokens int64, topP float64) {
	if temperature > 0 {
		span.SetAttributes(attribute.Float64(attrs.GenAIRequestTemperature, temperature))
	}
	if maxTokens > 0 {
		span.SetAttributes(attribute.Int64(attrs.GenAIRequestMaxTokens, maxTokens))
	}
	if topP > 0 {
		span.SetAttributes(attribute.Float64(attrs.GenAIRequestTopP, topP))
	}
}

// EndSpanSuccess completes a GenAI span with response data and cost calculation.
func EndSpanSuccess(span trace.Span, result ChatResult) {
	span.SetAttributes(
		attribute.String(attrs.GenAIResponseModel, result.ResponseModel),
		attribute.String(attrs.GenAIResponseID, result.ResponseID),
		attribute.Int64(attrs.GenAIUsageInputTokens, result.InputTokens),
		attribute.Int64(attrs.GenAIUsageOutputTokens, result.OutputTokens),
	)

	if len(result.FinishReasons) > 0 {
		span.SetAttributes(attribute.StringSlice(attrs.GenAIResponseFinishReasons, result.FinishReasons))
	}

	// Calculate cost
	cost := CalculateCost(result.ResponseModel, result.InputTokens, result.OutputTokens)
	if cost > 0 {
		span.SetAttributes(attribute.Float64(attrs.GenAICostUSD, cost))
	}

	span.End()
}

// EndSpanError completes a GenAI span with an error.
func EndSpanError(span trace.Span, err error) {
	span.RecordError(err)
	span.End()
}

// SetTokenDetails sets extended token usage details.
func SetTokenDetails(span trace.Span, details TokenDetails) {
	if details.ReasoningTokens > 0 {
		span.SetAttributes(attribute.Int64(attrs.GenAIReasoningTokens, details.ReasoningTokens))
	}
	if details.CachedReadTokens > 0 {
		span.SetAttributes(attribute.Int64(attrs.GenAICachedReadTokens, details.CachedReadTokens))
	}
	if details.CachedWriteTokens > 0 {
		span.SetAttributes(attribute.Int64(attrs.GenAICachedWriteTokens, details.CachedWriteTokens))
	}
}

// SetTimeToFirstToken sets the time-to-first-token metric.
func SetTimeToFirstToken(span trace.Span, ttftMs int64) {
	span.SetAttributes(attribute.Int64(attrs.GenAITimeToFirstTokenMs, ttftMs))
}

// SetFramework sets the GenAI framework attribute.
func SetFramework(span trace.Span, framework string) {
	span.SetAttributes(attribute.String(attrs.GenAIFramework, framework))
}
