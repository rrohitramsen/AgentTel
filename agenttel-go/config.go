package agenttel

import (
	"os"
	"strings"

	"gopkg.in/yaml.v3"
)

// AgentTelConfig is the top-level configuration matching agenttel.yml.
type AgentTelConfig struct {
	Enabled          bool                       `yaml:"enabled"`
	Topology         TopologyConfig             `yaml:"topology"`
	Dependencies     []DependencyConfig         `yaml:"dependencies"`
	Consumers        []ConsumerConfig           `yaml:"consumers"`
	Operations       map[string]OperationConfig `yaml:"operations"`
	Baselines        BaselineConfig             `yaml:"baselines"`
	AnomalyDetection AnomalyDetectionConfig     `yaml:"anomalyDetection"`
}

// TopologyConfig describes service ownership metadata.
type TopologyConfig struct {
	Team          string `yaml:"team"`
	Tier          string `yaml:"tier"`
	Domain        string `yaml:"domain"`
	OnCallChannel string `yaml:"onCallChannel"`
	RepoURL       string `yaml:"repoUrl"`
}

// DependencyConfig declares a service dependency.
type DependencyConfig struct {
	Name           string `yaml:"name"`
	Type           string `yaml:"type"`
	Criticality    string `yaml:"criticality"`
	Protocol       string `yaml:"protocol"`
	TimeoutMs      int    `yaml:"timeoutMs"`
	CircuitBreaker bool   `yaml:"circuitBreaker"`
	Fallback       string `yaml:"fallback"`
	HealthEndpoint string `yaml:"healthEndpoint"`
}

// ConsumerConfig declares a downstream consumer.
type ConsumerConfig struct {
	Name         string `yaml:"name"`
	Pattern      string `yaml:"pattern"`
	SLALatencyMs int    `yaml:"slaLatencyMs"`
}

// OperationConfig provides per-operation configuration.
type OperationConfig struct {
	Profile            string  `yaml:"profile"`
	ExpectedLatencyP50 string  `yaml:"expectedLatencyP50"`
	ExpectedLatencyP99 string  `yaml:"expectedLatencyP99"`
	ExpectedErrorRate  float64 `yaml:"expectedErrorRate"`
	Retryable          *bool   `yaml:"retryable"`
	Idempotent         *bool   `yaml:"idempotent"`
	RunbookURL         string  `yaml:"runbookUrl"`
	EscalationLevel    string  `yaml:"escalationLevel"`
	SafeToRestart      *bool   `yaml:"safeToRestart"`
}

// BaselineConfig configures the rolling baseline provider.
type BaselineConfig struct {
	RollingWindowSize int `yaml:"rollingWindowSize"`
	RollingMinSamples int `yaml:"rollingMinSamples"`
}

// AnomalyDetectionConfig configures anomaly detection.
type AnomalyDetectionConfig struct {
	Enabled         bool    `yaml:"enabled"`
	ZScoreThreshold float64 `yaml:"zScoreThreshold"`
}

// DefaultConfig returns a configuration with sensible defaults.
func DefaultConfig() AgentTelConfig {
	return AgentTelConfig{
		Enabled: true,
		Baselines: BaselineConfig{
			RollingWindowSize: 1000,
			RollingMinSamples: 10,
		},
		AnomalyDetection: AnomalyDetectionConfig{
			Enabled:         true,
			ZScoreThreshold: 3.0,
		},
	}
}

// LoadConfig reads an AgentTelConfig from a YAML file, then applies env overrides.
func LoadConfig(path string) (AgentTelConfig, error) {
	cfg := DefaultConfig()

	data, err := os.ReadFile(path)
	if err != nil {
		return cfg, err
	}

	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return cfg, err
	}

	applyEnvOverrides(&cfg)
	return cfg, nil
}

// LoadConfigFromEnv attempts to load config from the AGENTTEL_CONFIG_FILE env var
// or falls back to "agenttel.yml" in the current directory.
func LoadConfigFromEnv() (AgentTelConfig, error) {
	path := os.Getenv("AGENTTEL_CONFIG_FILE")
	if path == "" {
		path = "agenttel.yml"
	}

	cfg, err := LoadConfig(path)
	if err != nil && !os.IsNotExist(err) {
		return cfg, err
	}
	if os.IsNotExist(err) {
		cfg = DefaultConfig()
	}

	applyEnvOverrides(&cfg)
	return cfg, nil
}

func applyEnvOverrides(cfg *AgentTelConfig) {
	envMap := map[string]*string{
		"AGENTTEL_TOPOLOGY_TEAM":           &cfg.Topology.Team,
		"AGENTTEL_TOPOLOGY_TIER":           &cfg.Topology.Tier,
		"AGENTTEL_TOPOLOGY_DOMAIN":         &cfg.Topology.Domain,
		"AGENTTEL_TOPOLOGY_ON_CALL_CHANNEL": &cfg.Topology.OnCallChannel,
		"AGENTTEL_TOPOLOGY_REPO_URL":       &cfg.Topology.RepoURL,
	}

	for envKey, field := range envMap {
		if val := os.Getenv(envKey); val != "" {
			*field = val
		}
	}

	if val := os.Getenv("AGENTTEL_ENABLED"); val != "" {
		cfg.Enabled = strings.EqualFold(val, "true")
	}
}
