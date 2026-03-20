package agenttel

import (
	"os"
	"path/filepath"
	"testing"
)

func TestDefaultConfig(t *testing.T) {
	cfg := DefaultConfig()
	if !cfg.Enabled {
		t.Error("expected enabled=true")
	}
	if cfg.Baselines.RollingWindowSize != 1000 {
		t.Errorf("expected rollingWindowSize=1000, got %d", cfg.Baselines.RollingWindowSize)
	}
	if cfg.AnomalyDetection.ZScoreThreshold != 3.0 {
		t.Errorf("expected zScoreThreshold=3.0, got %f", cfg.AnomalyDetection.ZScoreThreshold)
	}
}

func TestLoadConfigFromFile(t *testing.T) {
	yaml := `
enabled: true
topology:
  team: payments
  tier: critical
  domain: fintech
dependencies:
  - name: postgres
    type: database
    criticality: required
operations:
  POST /transfer:
    expectedLatencyP50: 200ms
baselines:
  rollingWindowSize: 500
  rollingMinSamples: 20
anomalyDetection:
  enabled: true
  zScoreThreshold: 2.5
`
	dir := t.TempDir()
	path := filepath.Join(dir, "agenttel.yml")
	if err := os.WriteFile(path, []byte(yaml), 0644); err != nil {
		t.Fatal(err)
	}

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatal(err)
	}

	if cfg.Topology.Team != "payments" {
		t.Errorf("expected team=payments, got %s", cfg.Topology.Team)
	}
	if cfg.Topology.Tier != "critical" {
		t.Errorf("expected tier=critical, got %s", cfg.Topology.Tier)
	}
	if len(cfg.Dependencies) != 1 {
		t.Errorf("expected 1 dependency, got %d", len(cfg.Dependencies))
	}
	if cfg.Dependencies[0].Name != "postgres" {
		t.Errorf("expected dep name=postgres, got %s", cfg.Dependencies[0].Name)
	}
	if cfg.Baselines.RollingWindowSize != 500 {
		t.Errorf("expected rollingWindowSize=500, got %d", cfg.Baselines.RollingWindowSize)
	}
	if cfg.AnomalyDetection.ZScoreThreshold != 2.5 {
		t.Errorf("expected zScoreThreshold=2.5, got %f", cfg.AnomalyDetection.ZScoreThreshold)
	}
}

func TestEnvOverrides(t *testing.T) {
	t.Setenv("AGENTTEL_TOPOLOGY_TEAM", "platform")
	t.Setenv("AGENTTEL_TOPOLOGY_TIER", "internal")

	cfg := DefaultConfig()
	applyEnvOverrides(&cfg)

	if cfg.Topology.Team != "platform" {
		t.Errorf("expected team=platform, got %s", cfg.Topology.Team)
	}
	if cfg.Topology.Tier != "internal" {
		t.Errorf("expected tier=internal, got %s", cfg.Topology.Tier)
	}
}
