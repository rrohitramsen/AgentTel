import type { SpanFactory } from '../core/span-factory';
import type { BatchProcessor } from '../transport/batch-processor';

/**
 * Tracks page load performance using the Navigation Timing API
 * and PerformanceObserver. Emits enriched page_load spans.
 */
export class PageTracker {
  private readonly spanFactory: SpanFactory;
  private readonly processor: BatchProcessor;

  constructor(spanFactory: SpanFactory, processor: BatchProcessor) {
    this.spanFactory = spanFactory;
    this.processor = processor;
    this.trackInitialPageLoad();
  }

  private trackInitialPageLoad(): void {
    if (typeof window === 'undefined') return;

    // Wait for the page to fully load
    if (document.readyState === 'complete') {
      this.emitPageLoadSpan();
    } else {
      window.addEventListener('load', () => {
        // Slight delay to ensure navigation timing is populated
        setTimeout(() => this.emitPageLoadSpan(), 0);
      });
    }
  }

  private emitPageLoadSpan(): void {
    const timing = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined;
    if (!timing) return;

    const loadTimeMs = timing.loadEventEnd - timing.startTime;
    if (loadTimeMs <= 0) return;

    const span = this.spanFactory.createPageLoadSpan(
      window.location.pathname,
      document.title,
      loadTimeMs,
    );

    // Add Web Vitals-like metrics as attributes
    span.attributes['page.dom_content_loaded_ms'] = timing.domContentLoadedEventEnd - timing.startTime;
    span.attributes['page.dom_interactive_ms'] = timing.domInteractive - timing.startTime;
    span.attributes['page.ttfb_ms'] = timing.responseStart - timing.startTime;

    if (timing.transferSize !== undefined) {
      span.attributes['page.transfer_size_bytes'] = timing.transferSize;
    }

    this.processor.addSpan(span);
  }
}
