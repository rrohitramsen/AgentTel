import { ClientAnomalyDetector } from '../../src/enrichment/anomaly-detector';
import { AnomalyPattern } from '../../src/types/anomaly';

describe('ClientAnomalyDetector', () => {
  let detector: ClientAnomalyDetector;

  beforeEach(() => {
    detector = new ClientAnomalyDetector({
      apiFailureCascadeThreshold: 3,
      apiFailureCascadeWindowMs: 10000,
      slowLoadMultiplier: 3.0,
    });
  });

  describe('detectSlowPageLoad', () => {
    it('detects slow page load when ratio exceeds multiplier', () => {
      const result = detector.detectSlowPageLoad(3500, 1000);
      expect(result).not.toBeNull();
      expect(result!.detected).toBe(true);
      expect(result!.pattern).toBe(AnomalyPattern.SLOW_PAGE_LOAD);
    });

    it('returns null when load time is within threshold', () => {
      const result = detector.detectSlowPageLoad(2000, 1000);
      expect(result).toBeNull();
    });

    it('returns null when baseline is undefined', () => {
      const result = detector.detectSlowPageLoad(5000, undefined);
      expect(result).toBeNull();
    });

    it('returns null when baseline is zero', () => {
      const result = detector.detectSlowPageLoad(5000, 0);
      expect(result).toBeNull();
    });

    it('clamps score to 1.0', () => {
      const result = detector.detectSlowPageLoad(100000, 100);
      expect(result).not.toBeNull();
      expect(result!.score).toBeLessThanOrEqual(1.0);
    });
  });

  describe('recordApiError', () => {
    it('detects API failure cascade when threshold is reached', () => {
      const now = Date.now();
      detector.recordApiError(now);
      detector.recordApiError(now + 100);
      const result = detector.recordApiError(now + 200);

      expect(result).not.toBeNull();
      expect(result!.detected).toBe(true);
      expect(result!.pattern).toBe(AnomalyPattern.API_FAILURE_CASCADE);
    });

    it('does not trigger below threshold', () => {
      const now = Date.now();
      detector.recordApiError(now);
      const result = detector.recordApiError(now + 100);

      expect(result).toBeNull();
    });

    it('expires old errors outside the window', () => {
      const now = Date.now();
      detector.recordApiError(now);
      detector.recordApiError(now + 100);
      // Third error far outside the window
      const result = detector.recordApiError(now + 20000);

      expect(result).toBeNull();
    });

    it('clamps score to 1.0', () => {
      const now = Date.now();
      for (let i = 0; i < 10; i++) {
        detector.recordApiError(now + i * 100);
      }
      const result = detector.recordApiError(now + 1000);
      expect(result).not.toBeNull();
      expect(result!.score).toBeLessThanOrEqual(1.0);
    });
  });

  describe('detectFunnelDropOff', () => {
    it('detects funnel drop-off above multiplier', () => {
      const result = detector.detectFunnelDropOff(0.5, 0.1, 2.0);
      expect(result).not.toBeNull();
      expect(result!.detected).toBe(true);
      expect(result!.pattern).toBe(AnomalyPattern.FUNNEL_DROP_OFF);
    });

    it('returns null when within threshold', () => {
      const result = detector.detectFunnelDropOff(0.15, 0.1, 2.0);
      expect(result).toBeNull();
    });

    it('returns null when baseline is zero', () => {
      const result = detector.detectFunnelDropOff(0.5, 0, 2.0);
      expect(result).toBeNull();
    });

    it('clamps score to 1.0', () => {
      const result = detector.detectFunnelDropOff(0.9, 0.01, 2.0);
      expect(result).not.toBeNull();
      expect(result!.score).toBeLessThanOrEqual(1.0);
    });
  });
});
