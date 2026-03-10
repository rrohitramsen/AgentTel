import { CorrelationEngine } from '../../src/enrichment/correlation-engine';

describe('CorrelationEngine', () => {
  let engine: CorrelationEngine;

  beforeEach(() => {
    engine = new CorrelationEngine();
  });

  describe('recordCorrelation / getBackendTrace', () => {
    it('records and retrieves a correlation', () => {
      engine.recordCorrelation('span-1', 'trace-abc', 'GET /api/users');

      const entry = engine.getBackendTrace('span-1');
      expect(entry).toBeDefined();
      expect(entry!.backendTraceId).toBe('trace-abc');
      expect(entry!.backendOperation).toBe('GET /api/users');
      expect(entry!.timestamp).toBeGreaterThan(0);
    });

    it('returns undefined for unknown spans', () => {
      expect(engine.getBackendTrace('nonexistent')).toBeUndefined();
    });
  });

  describe('LRU eviction', () => {
    it('evicts oldest entry when exceeding 100 entries', () => {
      // Fill with 100 entries
      for (let i = 0; i < 100; i++) {
        engine.recordCorrelation(`span-${i}`, `trace-${i}`, `op-${i}`);
      }

      // All 100 should still exist
      expect(engine.getBackendTrace('span-0')).toBeDefined();
      expect(engine.getBackendTrace('span-99')).toBeDefined();

      // Adding one more should evict the oldest
      engine.recordCorrelation('span-100', 'trace-100', 'op-100');

      expect(engine.getBackendTrace('span-0')).toBeUndefined();
      expect(engine.getBackendTrace('span-100')).toBeDefined();
    });
  });

  describe('getCorrelations', () => {
    it('returns a copy of all correlations', () => {
      engine.recordCorrelation('span-a', 'trace-a', 'GET /a');
      engine.recordCorrelation('span-b', 'trace-b', 'POST /b');

      const correlations = engine.getCorrelations();
      expect(correlations.size).toBe(2);

      // Verify it's a copy (mutating returned map doesn't affect engine)
      correlations.delete('span-a');
      expect(engine.getBackendTrace('span-a')).toBeDefined();
    });
  });
});
