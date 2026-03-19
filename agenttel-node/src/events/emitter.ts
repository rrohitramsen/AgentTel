/** Structured event emitter for observability events. */
export type EventHandler = (eventName: string, attributes: Record<string, unknown>) => void;

export class AgentTelEventEmitter {
  private readonly handlers: EventHandler[] = [];

  onEvent(handler: EventHandler): void {
    this.handlers.push(handler);
  }

  emit(eventName: string, attributes: Record<string, unknown> = {}): void {
    for (const handler of this.handlers) {
      try {
        handler(eventName, attributes);
      } catch {
        // handlers should not throw
      }
    }
  }

  emitJSON(eventName: string, data: unknown): void {
    this.emit(eventName, { payload: JSON.stringify(data) });
  }
}
