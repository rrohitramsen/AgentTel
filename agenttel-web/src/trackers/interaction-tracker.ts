import type { SpanFactory } from '../core/span-factory';
import type { BatchProcessor } from '../transport/batch-processor';

/**
 * Tracks user interactions (clicks, submits) and detects rage clicks.
 */
export class InteractionTracker {
  private readonly spanFactory: SpanFactory;
  private readonly processor: BatchProcessor;
  private readonly onRageClick: (target: string) => void;
  private clickBuffer: { target: string; timestamp: number }[] = [];
  private readonly rageClickThreshold: number;
  private readonly rageClickWindowMs: number;

  constructor(
    spanFactory: SpanFactory,
    processor: BatchProcessor,
    rageClickThreshold: number = 3,
    rageClickWindowMs: number = 2000,
    onRageClick: (target: string) => void,
  ) {
    this.spanFactory = spanFactory;
    this.processor = processor;
    this.rageClickThreshold = rageClickThreshold;
    this.rageClickWindowMs = rageClickWindowMs;
    this.onRageClick = onRageClick;

    this.setupListeners();
  }

  private setupListeners(): void {
    if (typeof document === 'undefined') return;

    document.addEventListener('click', (event) => this.handleClick(event), { capture: true });
    document.addEventListener('submit', (event) => this.handleSubmit(event), { capture: true });
  }

  private handleClick(event: MouseEvent): void {
    const target = this.getSemanticTarget(event.target as Element);
    const now = Date.now();

    // Track for rage click detection
    this.clickBuffer.push({ target, timestamp: now });
    this.clickBuffer = this.clickBuffer.filter((c) => now - c.timestamp < this.rageClickWindowMs);

    const sameTargetClicks = this.clickBuffer.filter((c) => c.target === target).length;
    const isRageClick = sameTargetClicks >= this.rageClickThreshold;

    const span = this.spanFactory.createInteractionSpan({
      type: 'click',
      target,
      outcome: 'success',
    });

    if (isRageClick) {
      this.spanFactory.addAnomalyAttributes(span, 'RAGE_CLICK', Math.min(sameTargetClicks / 5, 1.0));
      this.onRageClick(target);
    }

    this.processor.addSpan(span);
  }

  private handleSubmit(event: Event): void {
    const form = event.target as HTMLFormElement;
    const target = form.getAttribute('data-agenttel-target')
      ?? form.getAttribute('name')
      ?? form.id
      ?? 'form';

    const span = this.spanFactory.createInteractionSpan({
      type: 'submit',
      target,
      outcome: 'success',
    });

    this.processor.addSpan(span);
  }

  /**
   * Gets a semantic, non-PII target identifier from a DOM element.
   */
  private getSemanticTarget(element: Element | null): string {
    if (!element) return 'unknown';

    // Prefer data-agenttel-target attribute
    const agenttelTarget = element.getAttribute('data-agenttel-target');
    if (agenttelTarget) return agenttelTarget;

    // Try semantic identifiers
    if (element.id) return `#${element.id}`;
    if (element.getAttribute('name')) return `[name=${element.getAttribute('name')}]`;

    // Use tag + role as fallback
    const role = element.getAttribute('role');
    const tag = element.tagName.toLowerCase();
    if (role) return `${tag}[role=${role}]`;

    // For buttons/links, use text content (truncated)
    if (tag === 'button' || tag === 'a') {
      const text = element.textContent?.trim().substring(0, 30);
      if (text) return `${tag}:"${text}"`;
    }

    return tag;
  }
}
