package topology

import (
	"strings"
	"testing"

	"go.agenttel.dev/agenttel-go/models"
)

func TestRegistry_BasicMetadata(t *testing.T) {
	r := NewRegistry()
	r.SetTeam("payments")
	r.SetTier("critical")
	r.SetDomain("fintech")
	r.SetOnCallChannel("#payments-oncall")
	r.SetRepoURL("https://github.com/example/payments")

	if r.Team() != "payments" {
		t.Errorf("expected team=payments, got %s", r.Team())
	}
	if r.Tier() != "critical" {
		t.Errorf("expected tier=critical, got %s", r.Tier())
	}
	if r.Domain() != "fintech" {
		t.Errorf("expected domain=fintech, got %s", r.Domain())
	}
}

func TestRegistry_Dependencies(t *testing.T) {
	r := NewRegistry()
	r.RegisterDependency(models.DependencyDescriptor{
		Name:        "postgres",
		Type:        "database",
		Criticality: "required",
	})
	r.RegisterDependency(models.DependencyDescriptor{
		Name:        "redis",
		Type:        "cache",
		Criticality: "optional",
	})

	deps := r.Dependencies()
	if len(deps) != 2 {
		t.Errorf("expected 2 dependencies, got %d", len(deps))
	}

	dep, ok := r.GetDependency("postgres")
	if !ok {
		t.Fatal("expected to find postgres")
	}
	if dep.Type != "database" {
		t.Errorf("expected type=database, got %s", dep.Type)
	}

	_, ok = r.GetDependency("nonexistent")
	if ok {
		t.Error("should not find nonexistent dependency")
	}
}

func TestRegistry_Consumers(t *testing.T) {
	r := NewRegistry()
	r.RegisterConsumer(models.ConsumerDescriptor{
		Name:         "checkout-service",
		Pattern:      "sync",
		SLALatencyMs: 200,
	})

	consumers := r.Consumers()
	if len(consumers) != 1 {
		t.Errorf("expected 1 consumer, got %d", len(consumers))
	}
	if consumers[0].Name != "checkout-service" {
		t.Errorf("expected consumer=checkout-service, got %s", consumers[0].Name)
	}
}

func TestRegistry_SerializeJSON(t *testing.T) {
	r := NewRegistry()
	r.RegisterDependency(models.DependencyDescriptor{
		Name: "postgres",
		Type: "database",
	})

	json := r.SerializeDependenciesJSON()
	if !strings.Contains(json, "postgres") {
		t.Errorf("expected JSON to contain 'postgres', got %s", json)
	}
}
