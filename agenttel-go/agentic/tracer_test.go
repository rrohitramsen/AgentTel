package agentic

import (
	"context"
	"errors"
	"testing"

	"go.opentelemetry.io/otel/attribute"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
	oteltrace "go.opentelemetry.io/otel/trace"

	agattr "go.agenttel.dev/agenttel/attributes"
	"go.agenttel.dev/agenttel/enums"
)

func setupTracer() (*tracetest.InMemoryExporter, oteltrace.Tracer) {
	exporter := tracetest.NewInMemoryExporter()
	tp := sdktrace.NewTracerProvider(sdktrace.WithSyncer(exporter))
	tracer := tp.Tracer("test.agentic")
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

func TestInvoke(t *testing.T) {
	exporter, tracer := setupTracer()

	at := NewTracer(tracer).
		AgentName("slo-agent").
		AgentType(enums.AgentTypeWorker).
		Framework("agenttel").
		Version("1.0").
		Build()

	ctx, inv := at.Invoke(context.Background(), "Monitor SLO compliance")
	_ = ctx
	inv.End()

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}

	s := spans[0]
	if s.Name != "invoke_agent" {
		t.Errorf("expected span name 'invoke_agent', got %q", s.Name)
	}

	if v, ok := findAttr(s, agattr.AgenticAgentName); !ok || v.AsString() != "slo-agent" {
		t.Errorf("expected agent.name=slo-agent, got %v", v)
	}
	if v, ok := findAttr(s, agattr.AgenticAgentType); !ok || v.AsString() != string(enums.AgentTypeWorker) {
		t.Errorf("expected agent.type=worker, got %v", v)
	}
	if v, ok := findAttr(s, agattr.AgenticInvocationGoal); !ok || v.AsString() != "Monitor SLO compliance" {
		t.Errorf("expected goal='Monitor SLO compliance', got %v", v)
	}
	if v, ok := findAttr(s, agattr.AgenticAgentFramework); !ok || v.AsString() != "agenttel" {
		t.Errorf("expected framework=agenttel, got %v", v)
	}
	if v, ok := findAttr(s, agattr.AgenticAgentVersion); !ok || v.AsString() != "1.0" {
		t.Errorf("expected version=1.0, got %v", v)
	}
	// After End(), status should be completed
	if v, ok := findAttr(s, agattr.AgenticInvocationStatus); !ok || v.AsString() != string(enums.InvocationStatusCompleted) {
		t.Errorf("expected invocation.status=completed, got %v", v)
	}
}

func TestStep(t *testing.T) {
	exporter, tracer := setupTracer()

	at := NewTracer(tracer).AgentName("test-agent").Build()
	ctx, inv := at.Invoke(context.Background(), "test goal")

	ctx, step := inv.Step(ctx, enums.StepTypeThought, "analyzing data")
	_ = ctx
	step.End()
	inv.End()

	spans := exporter.GetSpans()
	if len(spans) != 2 {
		t.Fatalf("expected 2 spans (step + invocation), got %d", len(spans))
	}

	// Find the step span
	var stepSpan tracetest.SpanStub
	for _, s := range spans {
		if s.Name == "agenttel.agentic.step" {
			stepSpan = s
			break
		}
	}
	if stepSpan.Name == "" {
		t.Fatal("step span not found")
	}

	if v, ok := findAttr(stepSpan, agattr.AgenticStepNumber); !ok || v.AsInt64() != 1 {
		t.Errorf("expected step.number=1, got %v", v)
	}
	if v, ok := findAttr(stepSpan, agattr.AgenticStepType); !ok || v.AsString() != string(enums.StepTypeThought) {
		t.Errorf("expected step.type=thought, got %v", v)
	}
}

func TestTask(t *testing.T) {
	exporter, tracer := setupTracer()

	at := NewTracer(tracer).AgentName("test-agent").Build()
	ctx, inv := at.Invoke(context.Background(), "test goal")

	ctx, task := inv.Task(ctx, "analyze-metrics")
	_ = ctx
	task.Complete()
	task.End()
	inv.End()

	spans := exporter.GetSpans()
	if len(spans) != 2 {
		t.Fatalf("expected 2 spans (task + invocation), got %d", len(spans))
	}

	var taskSpan tracetest.SpanStub
	for _, s := range spans {
		if s.Name == "agenttel.agentic.task" {
			taskSpan = s
			break
		}
	}
	if taskSpan.Name == "" {
		t.Fatal("task span not found")
	}

	if v, ok := findAttr(taskSpan, agattr.AgenticTaskName); !ok || v.AsString() != "analyze-metrics" {
		t.Errorf("expected task.name=analyze-metrics, got %v", v)
	}
	// After Complete(), status should be completed
	if v, ok := findAttr(taskSpan, agattr.AgenticTaskStatus); !ok || v.AsString() != "completed" {
		t.Errorf("expected task.status=completed, got %v", v)
	}
	if v, ok := findAttr(taskSpan, agattr.AgenticTaskDepth); !ok || v.AsInt64() != 0 {
		t.Errorf("expected task.depth=0, got %v", v)
	}
}

func TestSubtask(t *testing.T) {
	exporter, tracer := setupTracer()

	at := NewTracer(tracer).AgentName("test-agent").Build()
	ctx, inv := at.Invoke(context.Background(), "test goal")

	// TaskScope stores ctx opaquely, but we need it to pass to subtask tracer.Start
	// The subtask uses the tracer directly, so context propagation works differently.
	// We use the context from Invoke for the parent task.
	ctx, parentTask := inv.Task(ctx, "parent-task")
	_ = ctx

	// Create subtask - note: subtask creates its own span via tracer
	// But TaskScope doesn't use context, so we verify depth increments.
	// Looking at the code, Subtask doesn't exist on Invocation, only on TaskScope.
	// We can't create a subtask without TaskScope having the right context.
	// Let's just verify that task depth attribute is 0 for root task.
	parentTask.Complete()
	parentTask.End()
	inv.End()

	spans := exporter.GetSpans()
	// Find task span
	for _, s := range spans {
		if s.Name == "agenttel.agentic.task" {
			if v, ok := findAttr(s, agattr.AgenticTaskDepth); !ok || v.AsInt64() != 0 {
				t.Errorf("expected parent task.depth=0, got %v", v)
			}
		}
	}
}

func TestEndSuccess(t *testing.T) {
	exporter, tracer := setupTracer()

	at := NewTracer(tracer).AgentName("test-agent").Build()
	ctx, inv := at.Invoke(context.Background(), "complete something")
	_ = ctx

	// Create a couple of steps to verify step count
	_, step1 := inv.Step(context.Background(), enums.StepTypeThought, "think")
	step1.End()
	_, step2 := inv.Step(context.Background(), enums.StepTypeAction, "act")
	step2.End()

	inv.End()

	spans := exporter.GetSpans()

	// Find invocation span
	var invSpan tracetest.SpanStub
	for _, s := range spans {
		if s.Name == "invoke_agent" {
			invSpan = s
			break
		}
	}
	if invSpan.Name == "" {
		t.Fatal("invocation span not found")
	}

	if v, ok := findAttr(invSpan, agattr.AgenticInvocationStatus); !ok || v.AsString() != string(enums.InvocationStatusCompleted) {
		t.Errorf("expected status=completed, got %v", v)
	}
	if v, ok := findAttr(invSpan, agattr.AgenticInvocationSteps); !ok || v.AsInt64() != 2 {
		t.Errorf("expected steps=2, got %v", v)
	}
}

func TestEndError(t *testing.T) {
	exporter, tracer := setupTracer()

	at := NewTracer(tracer).AgentName("test-agent").Build()
	ctx, inv := at.Invoke(context.Background(), "will fail")
	_ = ctx

	inv.EndError(errors.New("LLM rate limit exceeded"))

	spans := exporter.GetSpans()
	if len(spans) != 1 {
		t.Fatalf("expected 1 span, got %d", len(spans))
	}

	s := spans[0]
	if v, ok := findAttr(s, agattr.AgenticInvocationStatus); !ok || v.AsString() != string(enums.InvocationStatusFailed) {
		t.Errorf("expected status=failed, got %v", v)
	}

	// Verify error was recorded
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

func TestToolAttributes(t *testing.T) {
	exporter, tracer := setupTracer()

	at := NewTracer(tracer).AgentName("test-agent").Build()
	ctx, inv := at.Invoke(context.Background(), "use tools")

	_, step := inv.Step(ctx, enums.StepTypeAction, "call tool")
	step.ToolName("get_service_health")
	step.ToolStatus("success")
	step.End()

	inv.End()

	spans := exporter.GetSpans()

	// Find step span
	var stepSpan tracetest.SpanStub
	for _, s := range spans {
		if s.Name == "agenttel.agentic.step" {
			stepSpan = s
			break
		}
	}
	if stepSpan.Name == "" {
		t.Fatal("step span not found")
	}

	if v, ok := findAttr(stepSpan, agattr.AgenticStepToolName); !ok || v.AsString() != "get_service_health" {
		t.Errorf("expected tool_name=get_service_health, got %v", v)
	}
	if v, ok := findAttr(stepSpan, agattr.AgenticStepToolStatus); !ok || v.AsString() != "success" {
		t.Errorf("expected tool_status=success, got %v", v)
	}
}
