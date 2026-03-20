import { readFileSync } from 'node:fs';
import yaml from 'js-yaml';

export interface TopologyConfig {
  team?: string;
  tier?: string;
  domain?: string;
  onCallChannel?: string;
  repoUrl?: string;
}

export interface DependencyConfig {
  name: string;
  type?: string;
  criticality?: string;
  protocol?: string;
  timeoutMs?: number;
  circuitBreaker?: boolean;
  fallback?: string;
  healthEndpoint?: string;
}

export interface ConsumerConfig {
  name: string;
  pattern?: string;
  slaLatencyMs?: number;
}

export interface OperationConfig {
  name: string;
  baselineLatencyP50Ms?: number;
  baselineLatencyP99Ms?: number;
  baselineErrorRate?: number;
}

export interface SLOConfig {
  name: string;
  operationName?: string;
  type?: string;
  target: number;
  windowSeconds?: number;
}

export interface AgentTelConfig {
  topology?: TopologyConfig;
  dependencies?: DependencyConfig[];
  consumers?: ConsumerConfig[];
  operations?: OperationConfig[];
  slos?: SLOConfig[];
  rollingWindowSize?: number;
  rollingMinSamples?: number;
  anomalyZScoreThreshold?: number;
}

/** Load config from a YAML file. */
export function loadConfig(path: string): AgentTelConfig {
  const content = readFileSync(path, 'utf-8');
  const raw = yaml.load(content) as Record<string, unknown>;
  return parseConfig(raw);
}

/** Load config from environment variables (AGENTTEL_TOPOLOGY_TEAM, etc.). */
export function loadConfigFromEnv(): AgentTelConfig {
  const config = defaultConfig();

  if (!config.topology) config.topology = {};
  if (process.env.AGENTTEL_TOPOLOGY_TEAM) config.topology.team = process.env.AGENTTEL_TOPOLOGY_TEAM;
  if (process.env.AGENTTEL_TOPOLOGY_TIER) config.topology.tier = process.env.AGENTTEL_TOPOLOGY_TIER;
  if (process.env.AGENTTEL_TOPOLOGY_DOMAIN) config.topology.domain = process.env.AGENTTEL_TOPOLOGY_DOMAIN;
  if (process.env.AGENTTEL_TOPOLOGY_ON_CALL_CHANNEL) config.topology.onCallChannel = process.env.AGENTTEL_TOPOLOGY_ON_CALL_CHANNEL;
  if (process.env.AGENTTEL_TOPOLOGY_REPO_URL) config.topology.repoUrl = process.env.AGENTTEL_TOPOLOGY_REPO_URL;

  const thresh = process.env.AGENTTEL_ANOMALY_ZSCORE_THRESHOLD;
  if (thresh) config.anomalyZScoreThreshold = parseFloat(thresh);

  const winSize = process.env.AGENTTEL_ROLLING_WINDOW_SIZE;
  if (winSize) config.rollingWindowSize = parseInt(winSize, 10);

  const minSamp = process.env.AGENTTEL_ROLLING_MIN_SAMPLES;
  if (minSamp) config.rollingMinSamples = parseInt(minSamp, 10);

  return config;
}

/** Returns a config with sensible defaults. */
export function defaultConfig(): AgentTelConfig {
  return {
    rollingWindowSize: 1000,
    rollingMinSamples: 10,
    anomalyZScoreThreshold: 3.0,
  };
}

function parseConfig(raw: Record<string, unknown>): AgentTelConfig {
  const config: AgentTelConfig = defaultConfig();

  if (raw.topology && typeof raw.topology === 'object') {
    const t = raw.topology as Record<string, unknown>;
    config.topology = {
      team: t.team as string | undefined,
      tier: t.tier as string | undefined,
      domain: t.domain as string | undefined,
      onCallChannel: (t.on_call_channel ?? t.onCallChannel) as string | undefined,
      repoUrl: (t.repo_url ?? t.repoUrl) as string | undefined,
    };
  }

  if (Array.isArray(raw.dependencies)) {
    config.dependencies = raw.dependencies.map((d: Record<string, unknown>) => ({
      name: d.name as string,
      type: d.type as string | undefined,
      criticality: d.criticality as string | undefined,
      protocol: d.protocol as string | undefined,
      timeoutMs: (d.timeout_ms ?? d.timeoutMs) as number | undefined,
      circuitBreaker: (d.circuit_breaker ?? d.circuitBreaker) as boolean | undefined,
      fallback: d.fallback as string | undefined,
      healthEndpoint: (d.health_endpoint ?? d.healthEndpoint) as string | undefined,
    }));
  }

  if (Array.isArray(raw.consumers)) {
    config.consumers = raw.consumers.map((c: Record<string, unknown>) => ({
      name: c.name as string,
      pattern: c.pattern as string | undefined,
      slaLatencyMs: (c.sla_latency_ms ?? c.slaLatencyMs) as number | undefined,
    }));
  }

  if (Array.isArray(raw.operations)) {
    config.operations = raw.operations.map((o: Record<string, unknown>) => ({
      name: o.name as string,
      baselineLatencyP50Ms: (o.baseline_latency_p50_ms ?? o.baselineLatencyP50Ms) as number | undefined,
      baselineLatencyP99Ms: (o.baseline_latency_p99_ms ?? o.baselineLatencyP99Ms) as number | undefined,
      baselineErrorRate: (o.baseline_error_rate ?? o.baselineErrorRate) as number | undefined,
    }));
  }

  if (Array.isArray(raw.slos)) {
    config.slos = raw.slos.map((s: Record<string, unknown>) => ({
      name: s.name as string,
      operationName: (s.operation_name ?? s.operationName) as string | undefined,
      type: s.type as string | undefined,
      target: s.target as number,
      windowSeconds: (s.window_seconds ?? s.windowSeconds) as number | undefined,
    }));
  }

  if (typeof raw.rolling_window_size === 'number') config.rollingWindowSize = raw.rolling_window_size;
  if (typeof raw.rolling_min_samples === 'number') config.rollingMinSamples = raw.rolling_min_samples;
  if (typeof raw.anomaly_zscore_threshold === 'number') config.anomalyZScoreThreshold = raw.anomaly_zscore_threshold;

  return config;
}
