import { NavigationTracker } from '../../src/trackers/navigation-tracker';
import { SpanFactory } from '../../src/core/span-factory';
import { BatchProcessor } from '../../src/transport/batch-processor';
import { OtlpHttpExporter } from '../../src/transport/otlp-exporter';
import type { InternalSpan } from '../../src/types/span';

describe('NavigationTracker', () => {
  let spanFactory: SpanFactory;
  let processor: BatchProcessor;
  let addedSpans: InternalSpan[];
  let originalPushState: typeof history.pushState;
  let originalReplaceState: typeof history.replaceState;

  beforeEach(() => {
    jest.useFakeTimers({ doNotFake: ['performance'] });

    // Save originals before NavigationTracker patches them
    originalPushState = history.pushState.bind(history);
    originalReplaceState = history.replaceState.bind(history);

    spanFactory = new SpanFactory({
      appName: 'test',
      collectorEndpoint: '/otlp',
    });

    const exporter = new OtlpHttpExporter('/otlp');
    jest.spyOn(exporter, 'export').mockResolvedValue(true);
    jest.spyOn(exporter, 'exportBeacon').mockReturnValue(true);
    processor = new BatchProcessor(exporter, 100, 60000);

    addedSpans = [];
    const originalAddSpan = processor.addSpan.bind(processor);
    processor.addSpan = (span: InternalSpan) => {
      addedSpans.push(span);
      originalAddSpan(span);
    };
  });

  afterEach(() => {
    jest.useRealTimers();
    // Restore original history methods
    history.pushState = originalPushState;
    history.replaceState = originalReplaceState;
  });

  it('tracks the current route', () => {
    const tracker = new NavigationTracker(spanFactory, processor);
    expect(tracker.getCurrentRoute()).toBe(window.location.pathname);
  });

  it('creates navigation span on pushState to a different route', () => {
    const tracker = new NavigationTracker(spanFactory, processor);
    const initialRoute = tracker.getCurrentRoute();

    history.pushState({}, '', '/new-page');

    expect(addedSpans).toHaveLength(1);
    expect(addedSpans[0].name).toContain('navigate /new-page');
    expect(addedSpans[0].attributes['navigation.from']).toBe(initialRoute);
    expect(addedSpans[0].attributes['navigation.to']).toBe('/new-page');
    expect(tracker.getCurrentRoute()).toBe('/new-page');

    // Restore URL
    history.pushState({}, '', initialRoute);
  });

  it('does not create span for same-route pushState', () => {
    new NavigationTracker(spanFactory, processor);
    const currentRoute = window.location.pathname;

    history.pushState({}, '', currentRoute);

    expect(addedSpans).toHaveLength(0);
  });

  it('generates a new page trace on navigation', () => {
    const newPageTraceSpy = jest.spyOn(spanFactory, 'newPageTrace');
    new NavigationTracker(spanFactory, processor);

    history.pushState({}, '', '/page-2');

    expect(newPageTraceSpy).toHaveBeenCalledTimes(1);

    // Restore
    history.pushState({}, '', '/');
  });

  it('calls onNavigate callback', () => {
    const callback = jest.fn();
    const tracker = new NavigationTracker(spanFactory, processor);
    tracker.onNavigate(callback);

    history.pushState({}, '', '/callback-test');

    expect(callback).toHaveBeenCalledWith('/callback-test');

    // Restore
    history.pushState({}, '', '/');
  });
});
