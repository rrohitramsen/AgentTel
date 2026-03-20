import type { OperationBaseline } from '../interfaces.js';

/** Returns baseline data for a named operation. */
export interface BaselineProvider {
  getBaseline(operationName: string): OperationBaseline | undefined;
}

/** Returns baselines from a pre-configured map. */
export class StaticBaselineProvider implements BaselineProvider {
  private readonly baselines: Map<string, OperationBaseline>;

  constructor(baselines: Record<string, OperationBaseline>) {
    this.baselines = new Map(Object.entries(baselines));
  }

  getBaseline(operationName: string): OperationBaseline | undefined {
    return this.baselines.get(operationName);
  }
}
