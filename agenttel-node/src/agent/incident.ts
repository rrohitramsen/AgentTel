import type { IncidentPattern } from '../enums-impl.js';
import type { AnomalyResult, SLOStatus, SLOAlert, DependencyDescriptor } from '../interfaces.js';
import type { ServiceHealth } from './health.js';

export interface IncidentContext {
  serviceName: string;
  patterns: IncidentPattern[];
  anomalies: AnomalyResult[];
  sloStatuses: SLOStatus[];
  sloAlerts: SLOAlert[];
  health: ServiceHealth;
  startedAt: Date;
  dependencies: DependencyDescriptor[];
}

/** Builds incident context from multiple sources. */
export class IncidentContextBuilder {
  private ctx: IncidentContext;

  constructor(serviceName: string) {
    this.ctx = {
      serviceName,
      patterns: [],
      anomalies: [],
      sloStatuses: [],
      sloAlerts: [],
      health: { overall: 'unknown', components: {}, updatedAt: new Date() },
      startedAt: new Date(),
      dependencies: [],
    };
  }

  withPatterns(patterns: IncidentPattern[]): this { this.ctx.patterns = patterns; return this; }
  withAnomalies(anomalies: AnomalyResult[]): this { this.ctx.anomalies = anomalies; return this; }
  withSLOStatuses(statuses: SLOStatus[]): this { this.ctx.sloStatuses = statuses; return this; }
  withSLOAlerts(alerts: SLOAlert[]): this { this.ctx.sloAlerts = alerts; return this; }
  withHealth(health: ServiceHealth): this { this.ctx.health = health; return this; }
  withDependencies(deps: DependencyDescriptor[]): this { this.ctx.dependencies = deps; return this; }

  build(): IncidentContext { return { ...this.ctx }; }
}
