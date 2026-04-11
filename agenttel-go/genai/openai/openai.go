// Package openai provides automatic OpenTelemetry tracing for the go-openai SDK.
//
// Usage:
//
//	import (
//	    openai "github.com/sashabaranov/go-openai"
//	    agenttelOpenAI "go.agenttel.dev/agenttel-go/genai/openai"
//	)
//
//	client := openai.NewClient("sk-...")
//	traced := agenttelOpenAI.Wrap(client)
//	resp, err := traced.CreateChatCompletion(ctx, openai.ChatCompletionRequest{...})
package openai

import (
	"context"
	"io"
	"sync"

	openai "github.com/sashabaranov/go-openai"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/trace"

	"go.agenttel.dev/agenttel-go/genai"
)

const (
	tracerName = "agenttel.genai.openai"
	systemName = "openai"
)

// Option configures the TracedClient.
type Option func(*TracedClient)

// WithTracer sets a custom tracer for the TracedClient.
func WithTracer(tracer trace.Tracer) Option {
	return func(tc *TracedClient) {
		tc.spanBuilder = genai.NewSpanBuilder(tracer)
	}
}

// TracedClient wraps an openai.Client to add automatic span creation
// for chat completion requests.
type TracedClient struct {
	client      *openai.Client
	spanBuilder *genai.SpanBuilder
}

// Wrap creates a TracedClient that instruments all chat completion calls
// with OpenTelemetry spans enriched with GenAI semantic attributes.
func Wrap(client *openai.Client, opts ...Option) *TracedClient {
	tc := &TracedClient{
		client:      client,
		spanBuilder: genai.NewSpanBuilder(otel.Tracer(tracerName)),
	}
	for _, opt := range opts {
		opt(tc)
	}
	return tc
}

// CreateChatCompletion sends a chat completion request and traces it with
// an OpenTelemetry span containing GenAI attributes (model, tokens, cost).
func (tc *TracedClient) CreateChatCompletion(
	ctx context.Context,
	req openai.ChatCompletionRequest,
) (openai.ChatCompletionResponse, error) {
	model := req.Model
	if model == "" {
		model = "unknown"
	}

	ctx, span := tc.spanBuilder.StartChatSpan(ctx, model, systemName)

	// Set request parameters.
	genai.SetRequestParams(span, float64(req.Temperature), int64(req.MaxTokens), float64(req.TopP))

	resp, err := tc.client.CreateChatCompletion(ctx, req)
	if err != nil {
		genai.EndSpanError(span, err)
		return resp, err
	}

	// Extract response data.
	result := genai.ChatResult{
		ResponseModel: resp.Model,
		ResponseID:    resp.ID,
		InputTokens:   int64(resp.Usage.PromptTokens),
		OutputTokens:  int64(resp.Usage.CompletionTokens),
	}

	if len(resp.Choices) > 0 {
		fr := string(resp.Choices[0].FinishReason)
		if fr != "" {
			result.FinishReasons = []string{fr}
		}
	}

	// Extended token details (reasoning tokens, cached tokens).
	if resp.Usage.CompletionTokensDetails != nil {
		genai.SetTokenDetails(span, genai.TokenDetails{
			ReasoningTokens: int64(resp.Usage.CompletionTokensDetails.ReasoningTokens),
		})
	}
	if resp.Usage.PromptTokensDetails != nil {
		genai.SetTokenDetails(span, genai.TokenDetails{
			CachedReadTokens: int64(resp.Usage.PromptTokensDetails.CachedTokens),
		})
	}

	genai.EndSpanSuccess(span, result)
	return resp, nil
}

// TracedStream wraps an openai.ChatCompletionStream to accumulate token
// usage across chunks and end the span when the stream completes.
type TracedStream struct {
	stream      *openai.ChatCompletionStream
	span        trace.Span
	model       string
	mu          sync.Mutex
	inputTokens int64
	outputTokens int64
	finishReason string
	responseModel string
	closed       bool
}

// CreateChatCompletionStream sends a streaming chat completion request and
// returns a TracedStream that tracks token usage and ends the span on EOF.
func (tc *TracedClient) CreateChatCompletionStream(
	ctx context.Context,
	req openai.ChatCompletionRequest,
) (*TracedStream, error) {
	model := req.Model
	if model == "" {
		model = "unknown"
	}

	ctx, span := tc.spanBuilder.StartChatSpan(ctx, model, systemName)

	genai.SetRequestParams(span, float64(req.Temperature), int64(req.MaxTokens), float64(req.TopP))

	stream, err := tc.client.CreateChatCompletionStream(ctx, req)
	if err != nil {
		genai.EndSpanError(span, err)
		return nil, err
	}

	return &TracedStream{
		stream:        stream,
		span:          span,
		model:         model,
		responseModel: model,
	}, nil
}

// Recv reads the next chunk from the stream. On io.EOF or error, the span
// is automatically ended with accumulated token usage.
func (ts *TracedStream) Recv() (openai.ChatCompletionStreamResponse, error) {
	resp, err := ts.stream.Recv()

	if err != nil {
		ts.endSpan(err)
		return resp, err
	}

	ts.mu.Lock()
	defer ts.mu.Unlock()

	// Accumulate response data from chunks.
	if resp.Model != "" {
		ts.responseModel = resp.Model
	}

	if len(resp.Choices) > 0 {
		fr := string(resp.Choices[0].FinishReason)
		if fr != "" {
			ts.finishReason = fr
		}
	}

	// Some providers send usage in stream chunks (with stream_options).
	if resp.Usage != nil {
		ts.inputTokens = int64(resp.Usage.PromptTokens)
		ts.outputTokens = int64(resp.Usage.CompletionTokens)
	}

	return resp, nil
}

// Close closes the underlying stream and ends the span if not already ended.
func (ts *TracedStream) Close() {
	ts.stream.Close()
	ts.endSpan(nil)
}

// endSpan finalizes the span with accumulated data. Safe to call multiple times.
func (ts *TracedStream) endSpan(err error) {
	ts.mu.Lock()
	defer ts.mu.Unlock()

	if ts.closed {
		return
	}
	ts.closed = true

	if err != nil && err != io.EOF {
		genai.EndSpanError(ts.span, err)
		return
	}

	result := genai.ChatResult{
		ResponseModel: ts.responseModel,
		InputTokens:   ts.inputTokens,
		OutputTokens:  ts.outputTokens,
	}
	if ts.finishReason != "" {
		result.FinishReasons = []string{ts.finishReason}
	}

	genai.EndSpanSuccess(ts.span, result)
}
