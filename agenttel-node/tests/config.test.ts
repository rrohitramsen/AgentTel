import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { defaultConfig, loadConfigFromEnv } from '../src/config.js';

describe('Config', () => {
  it('default config has sensible defaults', () => {
    const config = defaultConfig();
    expect(config.rollingWindowSize).toBe(1000);
    expect(config.rollingMinSamples).toBe(10);
    expect(config.anomalyZScoreThreshold).toBe(3.0);
  });

  describe('env overrides', () => {
    const envBackup: Record<string, string | undefined> = {};

    beforeEach(() => {
      envBackup.AGENTTEL_TOPOLOGY_TEAM = process.env.AGENTTEL_TOPOLOGY_TEAM;
      envBackup.AGENTTEL_TOPOLOGY_TIER = process.env.AGENTTEL_TOPOLOGY_TIER;
      envBackup.AGENTTEL_TOPOLOGY_DOMAIN = process.env.AGENTTEL_TOPOLOGY_DOMAIN;
      envBackup.AGENTTEL_ANOMALY_ZSCORE_THRESHOLD = process.env.AGENTTEL_ANOMALY_ZSCORE_THRESHOLD;
    });

    afterEach(() => {
      for (const [key, value] of Object.entries(envBackup)) {
        if (value === undefined) delete process.env[key];
        else process.env[key] = value;
      }
    });

    it('loads topology from env vars', () => {
      process.env.AGENTTEL_TOPOLOGY_TEAM = 'payments';
      process.env.AGENTTEL_TOPOLOGY_TIER = 'critical';
      process.env.AGENTTEL_TOPOLOGY_DOMAIN = 'fintech';

      const config = loadConfigFromEnv();
      expect(config.topology?.team).toBe('payments');
      expect(config.topology?.tier).toBe('critical');
      expect(config.topology?.domain).toBe('fintech');
    });

    it('loads anomaly threshold from env', () => {
      process.env.AGENTTEL_ANOMALY_ZSCORE_THRESHOLD = '4.5';
      const config = loadConfigFromEnv();
      expect(config.anomalyZScoreThreshold).toBe(4.5);
    });
  });
});
