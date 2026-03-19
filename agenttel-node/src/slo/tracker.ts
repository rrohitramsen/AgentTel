import { AlertSeverity, SLOType } from '../enums-impl.js';
import type { SLODefinition, SLOStatus, SLOAlert } from '../interfaces.js';

interface SLOState {
  definition: SLODefinition;
  totalRequests: number;
  failedRequests: number;
}

/** Tracks SLO compliance, budget, and burn rate. */
export class SLOTracker {
  private readonly slos = new Map<string, SLOState>();
  private readonly order: string[] = [];

  register(slo: SLODefinition): void {
    if (!this.slos.has(slo.name)) {
      this.order.push(slo.name);
    }
    this.slos.set(slo.name, { definition: slo, totalRequests: 0, failedRequests: 0 });
  }

  recordSuccess(operationName: string): void {
    for (const state of this.slos.values()) {
      if (state.definition.operationName === operationName || !state.definition.operationName) {
        state.totalRequests++;
      }
    }
  }

  recordFailure(operationName: string): void {
    for (const state of this.slos.values()) {
      if (state.definition.operationName === operationName || !state.definition.operationName) {
        state.totalRequests++;
        state.failedRequests++;
      }
    }
  }

  recordLatency(operationName: string, latencyMs: number, thresholdMs: number): void {
    for (const state of this.slos.values()) {
      if (state.definition.operationName !== operationName && state.definition.operationName) continue;
      const sloType = state.definition.type as SLOType;
      if (sloType === SLOType.LATENCY_P99 || sloType === SLOType.LATENCY_P50) {
        state.totalRequests++;
        if (latencyMs > thresholdMs) state.failedRequests++;
      }
    }
  }

  getStatuses(): SLOStatus[] {
    return this.order.map((name) => {
      const state = this.slos.get(name)!;
      return this.computeStatus(state);
    });
  }

  getStatus(sloName: string): SLOStatus | undefined {
    const state = this.slos.get(sloName);
    if (!state) return undefined;
    return this.computeStatus(state);
  }

  checkAlerts(): SLOAlert[] {
    const alerts: SLOAlert[] = [];

    for (const name of this.order) {
      const state = this.slos.get(name)!;
      const status = this.computeStatus(state);

      let severity: AlertSeverity | undefined;
      if (status.budgetRemaining <= 0.10) severity = AlertSeverity.CRITICAL;
      else if (status.budgetRemaining <= 0.25) severity = AlertSeverity.WARNING;
      else if (status.budgetRemaining <= 0.50) severity = AlertSeverity.INFO;

      if (severity) {
        alerts.push({
          sloName: name,
          severity,
          budgetRemaining: status.budgetRemaining,
          burnRate: status.burnRate,
        });
      }
    }

    return alerts;
  }

  private computeStatus(state: SLOState): SLOStatus {
    if (state.totalRequests === 0) {
      return {
        sloName: state.definition.name,
        target: state.definition.target,
        actual: 1.0,
        budgetRemaining: 1.0,
        burnRate: 0,
        totalRequests: 0,
        failedRequests: 0,
      };
    }

    const actual = 1.0 - state.failedRequests / state.totalRequests;
    const errorBudgetTotal = 1.0 - state.definition.target;
    const errorBudgetUsed = state.failedRequests / state.totalRequests;

    let budgetRemaining = 1.0;
    let burnRate = 0;
    if (errorBudgetTotal > 0) {
      budgetRemaining = Math.max(0, 1.0 - errorBudgetUsed / errorBudgetTotal);
      burnRate = errorBudgetUsed / errorBudgetTotal;
    }

    return {
      sloName: state.definition.name,
      target: state.definition.target,
      actual,
      budgetRemaining,
      burnRate,
      totalRequests: state.totalRequests,
      failedRequests: state.failedRequests,
    };
  }
}
