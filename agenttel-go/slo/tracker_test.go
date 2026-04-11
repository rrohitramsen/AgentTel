package slo

import (
	"math"
	"testing"

	"go.agenttel.dev/agenttel-go/models"
)

func TestTracker_BasicAvailability(t *testing.T) {
	tracker := NewTracker()
	tracker.Register(models.SLODefinition{
		Name:          "payment-availability",
		OperationName: "POST /pay",
		Type:          "availability",
		Target:        0.999,
	})

	// 999 successes, 1 failure
	for i := 0; i < 999; i++ {
		tracker.RecordSuccess("POST /pay")
	}
	tracker.RecordFailure("POST /pay")

	status, ok := tracker.GetStatus("payment-availability")
	if !ok {
		t.Fatal("expected status")
	}

	if status.TotalRequests != 1000 {
		t.Errorf("expected 1000 requests, got %d", status.TotalRequests)
	}
	if status.FailedRequests != 1 {
		t.Errorf("expected 1 failed request, got %d", status.FailedRequests)
	}
	if math.Abs(status.Actual-0.999) > 0.0001 {
		t.Errorf("expected actual=0.999, got %f", status.Actual)
	}
}

func TestTracker_BudgetExhaustion(t *testing.T) {
	tracker := NewTracker()
	tracker.Register(models.SLODefinition{
		Name:          "api-avail",
		OperationName: "op",
		Type:          "availability",
		Target:        0.99, // 1% error budget
	})

	// 90 successes, 10 failures (10% error rate, budget=1%)
	for i := 0; i < 90; i++ {
		tracker.RecordSuccess("op")
	}
	for i := 0; i < 10; i++ {
		tracker.RecordFailure("op")
	}

	alerts := tracker.CheckAlerts()
	if len(alerts) == 0 {
		t.Fatal("expected at least one alert")
	}
	if alerts[0].Severity != "critical" {
		t.Errorf("expected critical alert, got %s", alerts[0].Severity)
	}
}

func TestTracker_NoAlertWhenHealthy(t *testing.T) {
	tracker := NewTracker()
	tracker.Register(models.SLODefinition{
		Name:          "healthy-slo",
		OperationName: "op",
		Type:          "availability",
		Target:        0.99,
	})

	for i := 0; i < 1000; i++ {
		tracker.RecordSuccess("op")
	}

	alerts := tracker.CheckAlerts()
	if len(alerts) != 0 {
		t.Errorf("expected no alerts, got %d", len(alerts))
	}
}

func TestTracker_EmptySLO(t *testing.T) {
	tracker := NewTracker()
	tracker.Register(models.SLODefinition{
		Name:   "empty-slo",
		Target: 0.99,
	})

	status, ok := tracker.GetStatus("empty-slo")
	if !ok {
		t.Fatal("expected status")
	}
	if status.Actual != 1.0 {
		t.Errorf("expected actual=1.0 for empty SLO, got %f", status.Actual)
	}
	if !status.IsWithinBudget() {
		t.Error("expected within budget for empty SLO")
	}
}

func TestTracker_GetStatuses(t *testing.T) {
	tracker := NewTracker()
	tracker.Register(models.SLODefinition{Name: "slo-1", OperationName: "op1", Target: 0.99})
	tracker.Register(models.SLODefinition{Name: "slo-2", OperationName: "op2", Target: 0.999})

	statuses := tracker.GetStatuses()
	if len(statuses) != 2 {
		t.Errorf("expected 2 statuses, got %d", len(statuses))
	}
}
