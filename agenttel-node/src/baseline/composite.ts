import type { OperationBaseline } from '../interfaces.js';
import type { BaselineProvider } from './provider.js';

/** Composite provider that tries providers in priority order. */
export class CompositeBaselineProvider implements BaselineProvider {
  private readonly providers: BaselineProvider[];

  constructor(...providers: BaselineProvider[]) {
    this.providers = providers;
  }

  getBaseline(operationName: string): OperationBaseline | undefined {
    for (const provider of this.providers) {
      const baseline = provider.getBaseline(operationName);
      if (baseline) return baseline;
    }
    return undefined;
  }
}
