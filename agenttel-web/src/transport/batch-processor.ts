import type { InternalSpan } from '../types/span';
import { OtlpHttpExporter } from './otlp-exporter';

/**
 * Batches spans and flushes them periodically or when the buffer is full.
 * Uses navigator.sendBeacon on page unload for reliable delivery.
 */
export class BatchProcessor {
  private buffer: InternalSpan[] = [];
  private readonly exporter: OtlpHttpExporter;
  private readonly maxSize: number;
  private readonly flushIntervalMs: number;
  private flushTimer: ReturnType<typeof setInterval> | null = null;

  constructor(exporter: OtlpHttpExporter, maxSize: number = 50, flushIntervalMs: number = 5000) {
    this.exporter = exporter;
    this.maxSize = maxSize;
    this.flushIntervalMs = flushIntervalMs;

    this.startTimer();
    this.setupUnloadHandler();
  }

  /**
   * Adds a span to the batch. Flushes immediately if buffer is full.
   */
  addSpan(span: InternalSpan): void {
    this.buffer.push(span);
    if (this.buffer.length >= this.maxSize) {
      this.flush();
    }
  }

  /**
   * Flushes all buffered spans to the collector.
   */
  async flush(): Promise<void> {
    if (this.buffer.length === 0) return;

    const spans = this.buffer;
    this.buffer = [];

    await this.exporter.export(spans);
  }

  /**
   * Shuts down the processor, flushing remaining spans.
   */
  shutdown(): void {
    if (this.flushTimer) {
      clearInterval(this.flushTimer);
      this.flushTimer = null;
    }
    this.flushSync();
  }

  private startTimer(): void {
    this.flushTimer = setInterval(() => {
      this.flush();
    }, this.flushIntervalMs);
  }

  private setupUnloadHandler(): void {
    if (typeof window !== 'undefined') {
      window.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'hidden') {
          this.flushSync();
        }
      });

      window.addEventListener('pagehide', () => {
        this.flushSync();
      });
    }
  }

  /**
   * Synchronous flush using sendBeacon (works on page unload).
   */
  private flushSync(): void {
    if (this.buffer.length === 0) return;

    const spans = this.buffer;
    this.buffer = [];

    this.exporter.exportBeacon(spans);
  }
}
