import type { SpanFactory } from '../core/span-factory';
import type { BatchProcessor } from '../transport/batch-processor';

/**
 * Tracks SPA navigation events by patching the History API
 * and listening for popstate events.
 */
export class NavigationTracker {
  private readonly spanFactory: SpanFactory;
  private readonly processor: BatchProcessor;
  private currentRoute: string;
  private navigationStart: number;
  private onNavigateCallback?: (route: string) => void;

  constructor(spanFactory: SpanFactory, processor: BatchProcessor) {
    this.spanFactory = spanFactory;
    this.processor = processor;
    this.currentRoute = window.location.pathname;
    this.navigationStart = performance.now();

    this.patchHistoryApi();
    this.listenPopState();
  }

  /**
   * Register a callback for navigation events (used by journey tracker).
   */
  onNavigate(callback: (route: string) => void): void {
    this.onNavigateCallback = callback;
  }

  getCurrentRoute(): string {
    return this.currentRoute;
  }

  private patchHistoryApi(): void {
    const originalPushState = history.pushState.bind(history);
    const originalReplaceState = history.replaceState.bind(history);

    history.pushState = (...args: Parameters<typeof history.pushState>) => {
      originalPushState(...args);
      this.handleNavigation();
    };

    history.replaceState = (...args: Parameters<typeof history.replaceState>) => {
      originalReplaceState(...args);
      this.handleNavigation();
    };
  }

  private listenPopState(): void {
    window.addEventListener('popstate', () => {
      this.handleNavigation();
    });
  }

  private handleNavigation(): void {
    const newRoute = window.location.pathname;
    if (newRoute === this.currentRoute) return;

    const now = performance.now();
    const durationMs = now - this.navigationStart;

    // Start a new page-level trace for the new route
    this.spanFactory.newPageTrace();

    const span = this.spanFactory.createNavigationSpan(
      this.currentRoute,
      newRoute,
      durationMs,
    );

    this.processor.addSpan(span);

    this.currentRoute = newRoute;
    this.navigationStart = now;

    // Notify listeners (journey tracker uses this)
    if (this.onNavigateCallback) {
      this.onNavigateCallback(newRoute);
    }
  }
}
