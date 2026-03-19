package baseline

import (
	"testing"
	"time"

	"go.agenttel.dev/agenttel/models"
)

func TestCompositeProvider_PriorityOrder(t *testing.T) {
	static := NewStaticProvider(map[string]models.OperationBaseline{
		"op1": {OperationName: "op1", LatencyP50Ms: 10, Source: "static"},
	})

	rolling := NewRollingProvider(100, 1)
	rolling.RecordLatency("op1", 50)
	rolling.RecordLatency("op2", 100)

	composite := NewCompositeProvider(static, rolling)

	// op1 should come from static (first provider)
	b, ok := composite.GetBaseline("op1")
	if !ok {
		t.Fatal("expected baseline for op1")
	}
	if b.Source != "static" {
		t.Errorf("expected source=static, got %s", b.Source)
	}

	// op2 should come from rolling (second provider)
	b, ok = composite.GetBaseline("op2")
	if !ok {
		t.Fatal("expected baseline for op2")
	}
	if b.Source != "rolling_7d" {
		t.Errorf("expected source=rolling_7d, got %s", b.Source)
	}

	// op3 should not exist
	_, ok = composite.GetBaseline("op3")
	if ok {
		t.Error("should not find baseline for op3")
	}
}

func TestStaticProvider(t *testing.T) {
	now := time.Now()
	static := NewStaticProvider(map[string]models.OperationBaseline{
		"payment": {OperationName: "payment", LatencyP50Ms: 100, LatencyP99Ms: 500, ErrorRate: 0.01, Source: "static", UpdatedAt: now},
	})

	b, ok := static.GetBaseline("payment")
	if !ok {
		t.Fatal("expected baseline for payment")
	}
	if b.LatencyP50Ms != 100 {
		t.Errorf("expected P50=100, got %f", b.LatencyP50Ms)
	}
	if b.ErrorRate != 0.01 {
		t.Errorf("expected error rate=0.01, got %f", b.ErrorRate)
	}
}
