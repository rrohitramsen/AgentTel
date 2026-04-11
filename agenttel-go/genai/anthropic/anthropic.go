// Package anthropic provides automatic OpenTelemetry tracing for the Anthropic Go SDK.
//
// Usage:
//
//	import (
//	    "github.com/anthropics/anthropic-sdk-go"
//	    agenttelAnthropic "go.agenttel.dev/agenttel-go/genai/anthropic"
//	)
//
//	client := anthropic.NewClient()
//	traced := agenttelAnthropic.Wrap(client.Messages)
//	msg, err := traced.CreateMessage(ctx, anthropic.MessageNewParams{...})
package anthropic

import (
	"context"

	"github.com/anthropics/anthropic-sdk-go"
	"github.com/anthropics/anthropic-sdk-go/packages/ssestream"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/trace"

	"go.agenttel.dev/agenttel-go/genai"
)

const (
	tracerName = "agenttel.genai.anthropic"
	systemName = "anthropic"
)

// Option configures the TracedClient.
type Option func(*TracedClient)

// WithTracer sets a custom tracer for the TracedClient.
func WithTracer(tracer trace.Tracer) Option {
	return func(tc *TracedClient) {
		tc.spanBuilder = genai.NewSpanBuilder(tracer)
	}
}

// TracedClient wraps an Anthropic MessageService to add automatic span
// creation for message requests.
type TracedClient struct {
	messages    *anthropic.MessageService
	spanBuilder *genai.SpanBuilder
}

// Wrap creates a TracedClient that instruments all message calls with
// OpenTelemetry spans enriched with GenAI semantic attributes.
//
// Pass client.Messages as the argument:
//
//	traced := agenttelAnthropic.Wrap(client.Messages)
func Wrap(messages *anthropic.MessageService, opts ...Option) *TracedClient {
	tc := &TracedClient{
		messages:    messages,
		spanBuilder: genai.NewSpanBuilder(otel.Tracer(tracerName)),
	}
	for _, opt := range opts {
		opt(tc)
	}
	return tc
}

// CreateMessage sends a message request and traces it with an OpenTelemetry
// span containing GenAI attributes (model, tokens, cost, cache usage).
func (tc *TracedClient) CreateMessage(
	ctx context.Context,
	params anthropic.MessageNewParams,
) (*anthropic.Message, error) {
	model := params.Model
	if model == "" {
		model = "unknown"
	}

	ctx, span := tc.spanBuilder.StartChatSpan(ctx, model, systemName)

	// Set optional request parameters.
	var temp float64
	if params.Temperature.Valid() {
		temp = params.Temperature.Value
	}
	genai.SetRequestParams(span, temp, int64(params.MaxTokens), 0)
	if params.TopP.Valid() {
		genai.SetRequestParams(span, 0, 0, params.TopP.Value)
	}

	msg, err := tc.messages.New(ctx, params)
	if err != nil {
		genai.EndSpanError(span, err)
		return nil, err
	}

	// Extract response data.
	result := genai.ChatResult{
		ResponseModel: msg.Model,
		ResponseID:    msg.ID,
		InputTokens:   msg.Usage.InputTokens,
		OutputTokens:  msg.Usage.OutputTokens,
	}

	stopReason := string(msg.StopReason)
	if stopReason != "" {
		result.FinishReasons = []string{stopReason}
	}

	// Set cache token details if present.
	if msg.Usage.CacheReadInputTokens > 0 || msg.Usage.CacheCreationInputTokens > 0 {
		genai.SetTokenDetails(span, genai.TokenDetails{
			CachedReadTokens:  msg.Usage.CacheReadInputTokens,
			CachedWriteTokens: msg.Usage.CacheCreationInputTokens,
		})
	}

	genai.EndSpanSuccess(span, result)
	return msg, nil
}

// CreateMessageStreaming sends a streaming message request and returns a
// TracedStream that accumulates token usage across SSE events and ends
// the span when the stream completes.
func (tc *TracedClient) CreateMessageStreaming(
	ctx context.Context,
	params anthropic.MessageNewParams,
) *TracedStream {
	model := params.Model
	if model == "" {
		model = "unknown"
	}

	ctx, span := tc.spanBuilder.StartChatSpan(ctx, model, systemName)

	var temp float64
	if params.Temperature.Valid() {
		temp = params.Temperature.Value
	}
	genai.SetRequestParams(span, temp, int64(params.MaxTokens), 0)
	if params.TopP.Valid() {
		genai.SetRequestParams(span, 0, 0, params.TopP.Value)
	}

	stream := tc.messages.NewStreaming(ctx, params)

	return &TracedStream{
		stream:        stream,
		span:          span,
		responseModel: model,
	}
}

// TracedStream wraps an Anthropic SSE stream to accumulate token
// usage from events and end the span when the stream completes.
type TracedStream struct {
	stream        *ssestream.Stream[anthropic.MessageStreamEventUnion]
	span          trace.Span
	responseModel string
	responseID    string
	inputTokens   int64
	outputTokens  int64
	finishReason  string
	cacheRead     int64
	cacheCreate   int64
	ended         bool
}

// Next advances the stream to the next event. Returns false when the
// stream is exhausted or an error occurs. The span is automatically
// ended when Next returns false.
func (ts *TracedStream) Next() bool {
	ok := ts.stream.Next()
	if !ok {
		ts.endSpan()
		return false
	}

	event := ts.stream.Current()
	ts.accumulateEvent(event)
	return true
}

// Current returns the current stream event.
func (ts *TracedStream) Current() anthropic.MessageStreamEventUnion {
	return ts.stream.Current()
}

// Err returns any error that occurred during streaming.
func (ts *TracedStream) Err() error {
	return ts.stream.Err()
}

// Close ends the span if the stream was abandoned before completion.
func (ts *TracedStream) Close() {
	ts.endSpan()
}

// accumulateEvent extracts token usage and metadata from SSE events.
func (ts *TracedStream) accumulateEvent(event anthropic.MessageStreamEventUnion) {
	switch event.Type {
	case "message_start":
		e := event.AsMessageStart()
		if e.Message.Model != "" {
			ts.responseModel = e.Message.Model
		}
		if e.Message.ID != "" {
			ts.responseID = e.Message.ID
		}
		ts.inputTokens = e.Message.Usage.InputTokens
		ts.cacheRead = e.Message.Usage.CacheReadInputTokens
		ts.cacheCreate = e.Message.Usage.CacheCreationInputTokens

	case "message_delta":
		e := event.AsMessageDelta()
		ts.outputTokens = e.Usage.OutputTokens
		stopReason := string(e.Delta.StopReason)
		if stopReason != "" {
			ts.finishReason = stopReason
		}
	}
}

// endSpan finalizes the span with accumulated data. Safe to call multiple times.
func (ts *TracedStream) endSpan() {
	if ts.ended {
		return
	}
	ts.ended = true

	if err := ts.stream.Err(); err != nil {
		genai.EndSpanError(ts.span, err)
		return
	}

	if ts.cacheRead > 0 || ts.cacheCreate > 0 {
		genai.SetTokenDetails(ts.span, genai.TokenDetails{
			CachedReadTokens:  ts.cacheRead,
			CachedWriteTokens: ts.cacheCreate,
		})
	}

	result := genai.ChatResult{
		ResponseModel: ts.responseModel,
		ResponseID:    ts.responseID,
		InputTokens:   ts.inputTokens,
		OutputTokens:  ts.outputTokens,
	}
	if ts.finishReason != "" {
		result.FinishReasons = []string{ts.finishReason}
	}

	genai.EndSpanSuccess(ts.span, result)
}
