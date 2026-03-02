import type { RouteConfig } from '../config/types';

/**
 * Matches URL paths to configured route patterns.
 * Supports Express-style parameter patterns like "/checkout/:step".
 */
export class RouteMatcher {
  private readonly patterns: Array<{ pattern: string; regex: RegExp; config: RouteConfig }>;

  constructor(routes: Record<string, RouteConfig>) {
    this.patterns = Object.entries(routes).map(([pattern, config]) => ({
      pattern,
      regex: this.toRegex(pattern),
      config,
    }));
  }

  /**
   * Finds the matching route config for a given pathname.
   */
  match(pathname: string): { pattern: string; config: RouteConfig } | undefined {
    for (const entry of this.patterns) {
      if (entry.regex.test(pathname)) {
        return { pattern: entry.pattern, config: entry.config };
      }
    }
    return undefined;
  }

  /**
   * Returns the route pattern for a pathname (e.g., "/checkout/payment" → "/checkout/:step").
   */
  getRoutePattern(pathname: string): string {
    const match = this.match(pathname);
    return match?.pattern ?? pathname;
  }

  private toRegex(pattern: string): RegExp {
    const escaped = pattern
      .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      .replace(/:\\w+/g, '[^/]+');
    return new RegExp(`^${escaped}$`);
  }
}
