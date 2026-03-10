/**
 * Jest setup file for jsdom environment compatibility.
 * Polyfills missing browser APIs that jsdom doesn't provide.
 */

// Ensure performance.timeOrigin is a valid number (jsdom may set it to NaN)
if (typeof performance !== 'undefined' && (isNaN(performance.timeOrigin) || performance.timeOrigin === undefined)) {
  Object.defineProperty(performance, 'timeOrigin', {
    value: Date.now(),
    writable: false,
    configurable: true,
  });
}

// Polyfill performance.getEntriesByType (jsdom doesn't provide Performance Observer APIs)
if (typeof performance !== 'undefined' && typeof performance.getEntriesByType !== 'function') {
  (performance as any).getEntriesByType = () => [];
}

// Polyfill navigator.sendBeacon (jsdom doesn't provide it)
if (typeof navigator !== 'undefined' && typeof navigator.sendBeacon !== 'function') {
  (navigator as any).sendBeacon = () => true;
}
