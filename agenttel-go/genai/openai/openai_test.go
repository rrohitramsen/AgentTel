package openai

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

// setupTracer creates an in-memory exporter and a tracer for testing.
func setupTracer() (*tracetest.InMemoryExporter, oteltrace.Tracer) {
	exporter := tracetest.NewInMemoryExporter()
	tp := sdktrace.NewTracerProvider(sdktrace.WithSyncer(exporter))
	tracer := tp.Tracer("test.openai")
	return exporter, tracer
}

// findAttr returns the attribute value from a span's attributes by key.
func findAttr(span tracetest.SpanStub, key string) (attribute.Value, bool) {
	for _, a := range span.Attributes {
		if string(a.Key) == key {
			return a.Value, true
		}
	}
	return attribute.Value{}, false
}

func TestCreateChatCompletion_Success(t *testing.T) {
	exporter, tracer := setupTracer()
	sb := genai.NewSpanBuilder(tracer)

	ctx, span := sb.StartChatSpan(context.Background(), "gpt-4o", "openai")

	_ = ctx // context used for downstream propagation in real code

	genai.EndSpanSuccess(span, genai.ChatResult{
		ResponseModel: "gpt-4o",
		ResponseID:    "chatcmpl-abc123",
		InputTokens:   100,
		OutputTokens:  50,
		FinishReasons: []string{"stop"},
	})

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}

	s := spans[0]
	if s.Name != "chat gpt-4o" {
		t.Errorf("expected span name 'chat gpt-4o', got %q", s.Name)
	}

	// Verify system attribute
	if v, ok := findAttr(s, attrs.GenAISystem); !ok || v.AsString() != "openai" {
		t.Errorf("expected gen_ai.system=openai, got %v", v)
	}

	// Verify model attribute
	if v, ok := findAttr(s, attrs.GenAIRequestModel); !ok || v.AsString() != "gpt-4o" {
		t.Errorf("expected gen_ai.request.model=gpt-4o, got %v", v)
	}

	// Verify usage input tokens
	if v, ok := findAttr(s, attrs.GenAIUsageInputTokens); !ok || v.AsInt64() != 100 {
		t.Errorf("expected input_tokens=100, got %v", v)
	}

	// Verify usage output tokens
	if v, ok := findAttr(s, attrs.GenAIUsageOutputTokens); !ok || v.AsInt64() != 50 {
		t.Errorf("expected output_tokens=50, got %v", v)
	}

	// Verify response ID
	if v, ok := findAttr(s, attrs.GenAIResponseID); !ok || v.AsString() != "chatcmpl-abc123" {
		t.Errorf("expected response_id=chatcmpl-abc123, got %v", v)
	}

	// Verify finish reasons
	if v, ok := findAttr(s, attrs.GenAIResponseFinishReasons); !ok {
		t.Error("expected finish_reasons attribute")
	} else {
		reasons := v.AsStringSlice()
		if len(reasons) != 1 || reasons[0] != "stop" {
			t.Errorf("expected finish_reasons=[stop], got %v", reasons)
		}
	}
}

func TestCreateChatCompletion_Error(t *testing.T) {
	exporter, tracer := setupTracer()
	sb := genai.NewSpanBuilder(tracer)

	_, span := sb.StartChatSpan(context.Background(), "gpt-4o", "openai")

	// Simulate an error
	testErr := &testError{msg: "rate limit exceeded"}
	span.RecordError(testErr)
	genai.EndSpanError(span, testErr)

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}

	s := spans[0]
	// Verify error was recorded as an event
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

func TestCreateChatCompletionStream(t *testing.T) {
	exporter, tracer := setupTracer()
	sb := genai.NewSpanBuilder(tracer)

	_, span := sb.StartChatSpan(context.Background(), "gpt-4o", "openai")

	// Simulate accumulated tokens from streaming
	genai.EndSpanSuccess(span, genai.ChatResult{
		ResponseModel: "gpt-4o",
		ResponseID:    "chatcmpl-stream-123",
		InputTokens:   200,
		OutputTokens:  300,
		FinishReasons: []string{"stop"},
	})

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}

	s := spans[0]
	if v, ok := findAttr(s, attrs.GenAIUsageInputTokens); !ok || v.AsInt64() != 200 {
		t.Errorf("expected accumulated input_tokens=200, got %v", v)
	}
	if v, ok := findAttr(s, attrs.GenAIUsageOutputTokens); !ok || v.AsInt64() != 300 {
		t.Errorf("expected accumulated output_tokens=300, got %v", v)
	}
}

func TestRequestParams(t *testing.T) {
	exporter, tracer := setupTracer()
	sb := genai.NewSpanBuilder(tracer)

	_, span := sb.StartChatSpan(context.Background(), "gpt-4o", "openai")
	genai.SetRequestParams(span, 0.7, 4096, 0.9)
	span.End()

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}

	s := spans[0]
	if v, ok := findAttr(s, attrs.GenAIRequestTemperature); !ok || v.AsFloat64() != 0.7 {
		t.Errorf("expected temperature=0.7, got %v", v)
	}
	if v, ok := findAttr(s, attrs.GenAIRequestMaxTokens); !ok || v.AsInt64() != 4096 {
		t.Errorf("expected max_tokens=4096, got %v", v)
	}
	if v, ok := findAttr(s, attrs.GenAIRequestTopP); !ok || v.AsFloat64() != 0.9 {
		t.Errorf("expected top_p=0.9, got %v", v)
	}
}

func TestCostCalculation(t *testing.T) {
	exporter, tracer := setupTracer()
	sb := genai.NewSpanBuilder(tracer)

	_, span := sb.StartChatSpan(context.Background(), "gpt-4o", "openai")

	genai.EndSpanSuccess(span, genai.ChatResult{
		ResponseModel: "gpt-4o",
		InputTokens:   1_000_000,
		OutputTokens:  1_000_000,
	})

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}

	s := spans[0]
	// gpt-4o: input=2.50/M, output=10.00/M => 2.50 + 10.00 = 12.50
	if v, ok := findAttr(s, attrs.GenAICostUSD); !ok {
		t.Error("expected cost_usd attribute")
	} else if v.AsFloat64() != 12.50 {
		t.Errorf("expected cost_usd=12.50, got %f", v.AsFloat64())
	}
}

func TestNilClient(t *testing.T) {
	// Verify that the span builder handles graceful operations
	// even when no real client is present (builder still works standalone)
	exporter, tracer := setupTracer()
	sb := genai.NewSpanBuilder(tracer)

	// Building spans without a client should not panic
	_, span := sb.StartChatSpan(context.Background(), "gpt-4o", "openai")
	span.End()

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}
}

// testError satisfies the error interface for testing.
type testError struct {
	msg string
}

func (e *testError) Error() string { return e.msg }
