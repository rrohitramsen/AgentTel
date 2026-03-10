import { RouteMatcher } from '../../src/enrichment/route-matcher';

describe('RouteMatcher', () => {
  const matcher = new RouteMatcher({
    '/': { businessCriticality: 'engagement' },
    '/checkout/:step': { businessCriticality: 'revenue' },
    '/products/:id': { businessCriticality: 'engagement' },
    '/api/health': { businessCriticality: 'internal' },
  });

  describe('match', () => {
    it('matches exact paths', () => {
      const result = matcher.match('/');
      expect(result).toBeDefined();
      expect(result!.pattern).toBe('/');
      expect(result!.config.businessCriticality).toBe('engagement');
    });

    it('matches parameterized routes', () => {
      const result = matcher.match('/checkout/payment');
      expect(result).toBeDefined();
      expect(result!.pattern).toBe('/checkout/:step');
      expect(result!.config.businessCriticality).toBe('revenue');
    });

    it('matches parameterized routes with different params', () => {
      const result = matcher.match('/products/123');
      expect(result).toBeDefined();
      expect(result!.pattern).toBe('/products/:id');
    });

    it('returns undefined for no match', () => {
      const result = matcher.match('/unknown/path');
      expect(result).toBeUndefined();
    });

    it('does not match partial paths', () => {
      const result = matcher.match('/checkout/payment/extra');
      expect(result).toBeUndefined();
    });
  });

  describe('getRoutePattern', () => {
    it('returns the pattern for a matched path', () => {
      expect(matcher.getRoutePattern('/checkout/shipping')).toBe('/checkout/:step');
    });

    it('returns the original path when no match', () => {
      expect(matcher.getRoutePattern('/nowhere')).toBe('/nowhere');
    });
  });
});
