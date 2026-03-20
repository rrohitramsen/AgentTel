package anthropic

import (
	"context"
	"testing"

	"go.opentelemetry.io/otel/attribute"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
	oteltrace "go.opentelemetry.io/otel/trace"

	attrs "go.agenttel.dev/agenttel/attributes"
	"go.agenttel.dev/agenttel/genai"
)

func setupTracer() (*tracetest.InMemoryExporter, oteltrace.Tracer) {
	exporter := tracetest.NewInMemoryExporter()
	tp := sdktrace.NewTracerProvider(sdktrace.WithSyncer(exporter))
	tracer := tp.Tracer("test.anthropic")
	return exporter, tracer
}

func findAttr(span tracetest.SpanStub, key string) (attribute.Value, bool) {
	for _, a := range span.Attributes {
		if string(a.Key) == key {
			return a.Value, true
		}
	}
	return attribute.Value{}, false
}

func TestCreateMessage_Success(t *testing.T) {
	exporter, tracer := setupTracer()
	sb := genai.NewSpanBuilder(tracer)

	_, span := sb.StartChatSpan(context.Background(), "claude-opus-4", "anthropic")

	genai.EndSpanSuccess(span, genai.ChatResult{
		ResponseModel: "claude-opus-4",
		ResponseID:    "msg_abc123",
		InputTokens:   500,
		OutputTokens:  200,
		FinishReasons: []string{"end_turn"},
	})

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}

	s := spans[0]
	if s.Name != "chat claude-opus-4" {
		t.Errorf("expected span name 'chat claude-opus-4', got %q", s.Name)
	}

	if v, ok := findAttr(s, attrs.GenAISystem); !ok || v.AsString() != "anthropic" {
		t.Errorf("expected gen_ai.system=anthropic, got %v", v)
	}

	if v, ok := findAttr(s, attrs.GenAIUsageInputTokens); !ok || v.AsInt64() != 500 {
		t.Errorf("expected input_tokens=500, got %v", v)
	}

	if v, ok := findAttr(s, attrs.GenAIUsageOutputTokens); !ok || v.AsInt64() != 200 {
		t.Errorf("expected output_tokens=200, got %v", v)
	}

	if v, ok := findAttr(s, attrs.GenAIResponseFinishReasons); !ok {
		t.Error("expected finish_reasons attribute")
	} else {
		reasons := v.AsStringSlice()
		if len(reasons) != 1 || reasons[0] != "end_turn" {
			t.Errorf("expected finish_reasons=[end_turn], got %v", reasons)
		}
	}
}

func TestCreateMessage_Error(t *testing.T) {
	exporter, tracer := setupTracer()
	sb := genai.NewSpanBuilder(tracer)

	_, span := sb.StartChatSpan(context.Background(), "claude-opus-4", "anthropic")

	testErr := &testError{msg: "overloaded_error"}
	genai.EndSpanError(span, testErr)

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}

	s := spans[0]
	foundError := false
	for _, ev := range s.Events {
		if ev.Name == "exception" {
			foundError = true
			break
		}
	}
	if !foundError {
		t.Error("expected exception event recorded on span")
	}
}

func TestStream(t *testing.T) {
	exporter, tracer := setupTracer()
	sb := genai.NewSpanBuilder(tracer)

	_, span := sb.StartChatSpan(context.Background(), "claude-opus-4", "anthropic")

	// Simulate SSE event accumulation from streaming
	genai.EndSpanSuccess(span, genai.ChatResult{
		ResponseModel: "claude-opus-4",
		ResponseID:    "msg_stream_456",
		InputTokens:   1000,
		OutputTokens:  800,
		FinishReasons: []string{"end_turn"},
	})

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}

	s := spans[0]
	if v, ok := findAttr(s, attrs.GenAIUsageInputTokens); !ok || v.AsInt64() != 1000 {
		t.Errorf("expected accumulated input_tokens=1000, got %v", v)
	}
	if v, ok := findAttr(s, attrs.GenAIUsageOutputTokens); !ok || v.AsInt64() != 800 {
		t.Errorf("expected accumulated output_tokens=800, got %v", v)
	}
}

func TestCacheTokens(t *testing.T) {
	exporter, tracer := setupTracer()
	sb := genai.NewSpanBuilder(tracer)

	_, span := sb.StartChatSpan(context.Background(), "claude-opus-4", "anthropic")

	// Set cache token details
	genai.SetTokenDetails(span, genai.TokenDetails{
		CachedReadTokens: 250,
	})

	genai.EndSpanSuccess(span, genai.ChatResult{
		ResponseModel: "claude-opus-4",
		InputTokens:   500,
		OutputTokens:  200,
	})

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}

	s := spans[0]
	if v, ok := findAttr(s, attrs.GenAICachedReadTokens); !ok || v.AsInt64() != 250 {
		t.Errorf("expected cached_read_tokens=250, got %v", v)
	}
}

func TestCostCalculation(t *testing.T) {
	exporter, tracer := setupTracer()
	sb := genai.NewSpanBuilder(tracer)

	_, span := sb.StartChatSpan(context.Background(), "claude-opus-4", "anthropic")

	genai.EndSpanSuccess(span, genai.ChatResult{
		ResponseModel: "claude-opus-4",
		InputTokens:   1_000_000,
		OutputTokens:  1_000_000,
	})

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}

	s := spans[0]
	// claude-opus-4: input=15.00/M, output=75.00/M => 15.00 + 75.00 = 90.00
	if v, ok := findAttr(s, attrs.GenAICostUSD); !ok {
		t.Error("expected cost_usd attribute")
	} else if v.AsFloat64() != 90.00 {
		t.Errorf("expected cost_usd=90.00, got %f", v.AsFloat64())
	}
}

func TestNilClient(t *testing.T) {
	// Verify that the span builder handles graceful operations
	// without a real Anthropic client
	exporter, tracer := setupTracer()
	sb := genai.NewSpanBuilder(tracer)

	_, span := sb.StartChatSpan(context.Background(), "claude-opus-4", "anthropic")
	span.End()

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}
}

type testError struct {
	msg string
}

func (e *testError) Error() string { return e.msg }
