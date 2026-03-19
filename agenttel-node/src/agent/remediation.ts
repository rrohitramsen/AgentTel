export interface RemediationAction {
  name: string;
  description: string;
  riskLevel: 'low' | 'medium' | 'high';
  requiresApproval: boolean;
}

export interface RemediationResult {
  action: string;
  success: boolean;
  message: string;
}

export type RemediationHandler = (params: Record<string, unknown>) => Promise<RemediationResult>;

/** Manages available remediation actions. */
export class RemediationRegistry {
  private readonly actions = new Map<string, RemediationAction>();
  private readonly handlers = new Map<string, RemediationHandler>();

  register(action: RemediationAction, handler: RemediationHandler): void {
    this.actions.set(action.name, action);
    this.handlers.set(action.name, handler);
  }

  listActions(): RemediationAction[] {
    return [...this.actions.values()];
  }

  async execute(name: string, params: Record<string, unknown> = {}): Promise<RemediationResult> {
    const handler = this.handlers.get(name);
    if (!handler) throw new Error(`Unknown remediation action: ${name}`);
    return handler(params);
  }
}
