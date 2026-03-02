import type { SpanFactory } from '../core/span-factory';
import type { BatchProcessor } from '../transport/batch-processor';

/**
 * Captures JavaScript errors via window.onerror and unhandledrejection.
 * Detects error loops (same error repeated rapidly).
 */
export class ErrorTracker {
  private readonly spanFactory: SpanFactory;
  private readonly processor: BatchProcessor;
  private readonly onErrorLoop: (message: string) => void;
  private errorBuffer: { message: string; timestamp: number }[] = [];
  private readonly errorLoopThreshold: number;
  private readonly errorLoopWindowMs: number;
  private currentRoute: () => string;

  constructor(
    spanFactory: SpanFactory,
    processor: BatchProcessor,
    errorLoopThreshold: number = 5,
    errorLoopWindowMs: number = 30000,
    currentRoute: () => string,
    onErrorLoop: (message: string) => void,
  ) {
    this.spanFactory = spanFactory;
    this.processor = processor;
    this.errorLoopThreshold = errorLoopThreshold;
    this.errorLoopWindowMs = errorLoopWindowMs;
    this.currentRoute = currentRoute;
    this.onErrorLoop = onErrorLoop;

    this.setupListeners();
  }

  private setupListeners(): void {
    if (typeof window === 'undefined') return;

    window.addEventListener('error', (event) => {
      this.handleError(event.message ?? 'Unknown error', 'onerror');
    });

    window.addEventListener('unhandledrejection', (event) => {
      const message = event.reason instanceof Error
        ? event.reason.message
        : String(event.reason);
      this.handleError(message, 'unhandledrejection');
    });
  }

  private handleError(message: string, source: string): void {
    const now = Date.now();
    const route = this.currentRoute();

    // Track for error loop detection
    this.errorBuffer.push({ message, timestamp: now });
    this.errorBuffer = this.errorBuffer.filter((e) => now - e.timestamp < this.errorLoopWindowMs);

    const sameErrorCount = this.errorBuffer.filter((e) => e.message === message).length;
    const isErrorLoop = sameErrorCount >= this.errorLoopThreshold;

    const span = this.spanFactory.createErrorSpan(message, source, route);

    if (isErrorLoop) {
      this.spanFactory.addAnomalyAttributes(span, 'ERROR_LOOP', Math.min(sameErrorCount / 10, 1.0));
      this.onErrorLoop(message);
    }

    this.processor.addSpan(span);
  }
}
