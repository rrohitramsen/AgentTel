/**
 * Manages frontend ↔ backend trace correlation.
 * Maps frontend interaction spans to backend trace IDs
 * extracted from API response traceparent headers.
 */
export class CorrelationEngine {
  private correlationMap: Map<string, CorrelationEntry> = new Map();
  private readonly maxEntries = 100;

  /**
   * Records a correlation between a frontend span and a backend trace.
   */
  recordCorrelation(frontendSpanId: string, backendTraceId: string, backendOperation: string): void {
    this.correlationMap.set(frontendSpanId, {
      backendTraceId,
      backendOperation,
      timestamp: Date.now(),
    });

    // Keep bounded
    if (this.correlationMap.size > this.maxEntries) {
      const oldest = this.correlationMap.keys().next().value;
      if (oldest) this.correlationMap.delete(oldest);
    }
  }

  /**
   * Gets the backend trace ID for a frontend span.
   */
  getBackendTrace(frontendSpanId: string): CorrelationEntry | undefined {
    return this.correlationMap.get(frontendSpanId);
  }

  /**
   * Returns all active correlations (for debugging/monitoring).
   */
  getCorrelations(): Map<string, CorrelationEntry> {
    return new Map(this.correlationMap);
  }
}

export interface CorrelationEntry {
  backendTraceId: string;
  backendOperation: string;
  timestamp: number;
}
