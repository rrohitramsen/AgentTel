import type { AgentTelConfig } from './config.js';
import { defaultConfig } from './config.js';
import { AnomalyDetector } from './anomaly/detector.js';
import { StaticBaselineProvider, type BaselineProvider } from './baseline/provider.js';
import { RollingBaselineProvider } from './baseline/rolling.js';
import { CompositeBaselineProvider } from './baseline/composite.js';
import { ErrorClassifier } from './error/classifier.js';
import { SLOTracker } from './slo/tracker.js';
import { TopologyRegistry } from './topology/registry.js';
import { AgentTelEventEmitter } from './events/emitter.js';
import { AgentTelSpanProcessor } from './processor.js';
import type { OperationBaseline, SLODefinition } from './interfaces.js';

/** Top-level orchestrator for AgentTel SDK. */
export class AgentTelEngine {
  readonly topology: TopologyRegistry;
  readonly rollingProvider: RollingBaselineProvider;
  readonly baselineProvider: BaselineProvider;
  readonly anomalyDetector: AnomalyDetector;
  readonly sloTracker: SLOTracker;
  readonly errorClassifier: ErrorClassifier;
  readonly eventEmitter: AgentTelEventEmitter;
  readonly spanProcessor: AgentTelSpanProcessor;

  private constructor(config: AgentTelConfig) {
    // Topology
    this.topology = new TopologyRegistry();
    if (config.topology) {
      if (config.topology.team) this.topology.team = config.topology.team;
      if (config.topology.tier) this.topology.tier = config.topology.tier;
      if (config.topology.domain) this.topology.domain = config.topology.domain;
      if (config.topology.onCallChannel) this.topology.onCallChannel = config.topology.onCallChannel;
      if (config.topology.repoUrl) this.topology.repoUrl = config.topology.repoUrl;
    }
    if (config.dependencies) {
      for (const dep of config.dependencies) {
        this.topology.registerDependency({
          name: dep.name,
          type: dep.type ?? '',
          criticality: dep.criticality ?? '',
          protocol: dep.protocol,
          timeoutMs: dep.timeoutMs,
          circuitBreaker: dep.circuitBreaker,
          fallback: dep.fallback,
          healthEndpoint: dep.healthEndpoint,
        });
      }
    }
    if (config.consumers) {
      for (const c of config.consumers) {
        this.topology.registerConsumer({ name: c.name, pattern: c.pattern ?? 'sync', slaLatencyMs: c.slaLatencyMs ?? 0 });
      }
    }

    // Baselines
    this.rollingProvider = new RollingBaselineProvider(
      config.rollingWindowSize ?? 1000,
      config.rollingMinSamples ?? 10,
    );

    const staticBaselines: Record<string, OperationBaseline> = {};
    if (config.operations) {
      for (const op of config.operations) {
        staticBaselines[op.name] = {
          operationName: op.name,
          latencyP50Ms: op.baselineLatencyP50Ms ?? 0,
          latencyP99Ms: op.baselineLatencyP99Ms ?? 0,
          errorRate: op.baselineErrorRate ?? 0,
          source: 'static',
          updatedAt: new Date(),
        };
      }
    }
    const staticProvider = new StaticBaselineProvider(staticBaselines);
    this.baselineProvider = new CompositeBaselineProvider(staticProvider, this.rollingProvider);

    // Anomaly detection
    this.anomalyDetector = new AnomalyDetector(config.anomalyZScoreThreshold ?? 3.0);

    // SLO tracking
    this.sloTracker = new SLOTracker();
    if (config.slos) {
      for (const slo of config.slos) {
        this.sloTracker.register({
          name: slo.name,
          operationName: slo.operationName,
          type: slo.type ?? 'availability',
          target: slo.target,
          windowSeconds: slo.windowSeconds,
        });
      }
    }

    // Error classifier
    this.errorClassifier = new ErrorClassifier();

    // Events
    this.eventEmitter = new AgentTelEventEmitter();

    // Span processor
    this.spanProcessor = new AgentTelSpanProcessor({
      topology: this.topology,
      baselineProvider: this.baselineProvider,
      rollingProvider: this.rollingProvider,
      anomalyDetector: this.anomalyDetector,
      sloTracker: this.sloTracker,
      errorClassifier: this.errorClassifier,
      eventEmitter: this.eventEmitter,
    });
  }

  /** Create a new builder. */
  static builder(): AgentTelEngineBuilder {
    return new AgentTelEngineBuilder();
  }

  /** Create an engine with a config directly. */
  static fromConfig(config: AgentTelConfig): AgentTelEngine {
    return new AgentTelEngine(config);
  }
}

/** Builder for configuring and creating an AgentTelEngine. */
export class AgentTelEngineBuilder {
  private config: AgentTelConfig = defaultConfig();

  withConfig(config: AgentTelConfig): this {
    this.config = { ...this.config, ...config };
    return this;
  }

  withTeam(team: string): this {
    if (!this.config.topology) this.config.topology = {};
    this.config.topology.team = team;
    return this;
  }

  withTier(tier: string): this {
    if (!this.config.topology) this.config.topology = {};
    this.config.topology.tier = tier;
    return this;
  }

  withDomain(domain: string): this {
    if (!this.config.topology) this.config.topology = {};
    this.config.topology.domain = domain;
    return this;
  }

  withSLO(slo: SLODefinition): this {
    if (!this.config.slos) this.config.slos = [];
    this.config.slos.push(slo);
    return this;
  }

  withAnomalyThreshold(threshold: number): this {
    this.config.anomalyZScoreThreshold = threshold;
    return this;
  }

  build(): AgentTelEngine {
    return AgentTelEngine.fromConfig(this.config);
  }
}
