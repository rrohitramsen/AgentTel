import { generateTraceId, generateSpanId, nowNano } from '../../src/types/span';

describe('span utilities', () => {
  describe('generateTraceId', () => {
    it('returns a 32-character hex string', () => {
      const traceId = generateTraceId();
      expect(traceId).toHaveLength(32);
      expect(traceId).toMatch(/^[0-9a-f]{32}$/);
    });

    it('generates unique trace IDs', () => {
      const ids = new Set(Array.from({ length: 100 }, () => generateTraceId()));
      expect(ids.size).toBe(100);
    });
  });

  describe('generateSpanId', () => {
    it('returns a 16-character hex string', () => {
      const spanId = generateSpanId();
      expect(spanId).toHaveLength(16);
      expect(spanId).toMatch(/^[0-9a-f]{16}$/);
    });

    it('generates unique span IDs', () => {
      const ids = new Set(Array.from({ length: 100 }, () => generateSpanId()));
      expect(ids.size).toBe(100);
    });
  });

  describe('nowNano', () => {
    it('returns a positive bigint', () => {
      const ts = nowNano();
      expect(typeof ts).toBe('bigint');
      expect(ts > 0n).toBe(true);
    });

    it('returns increasing values', () => {
      const ts1 = nowNano();
      const ts2 = nowNano();
      expect(ts2 >= ts1).toBe(true);
    });
  });
});
