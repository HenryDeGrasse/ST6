/**
 * Client-side observability utilities for the Weekly Commitments micro-frontend.
 *
 * Per PRD §9.7: Client-side error reporting (Sentry or PA's existing tool).
 * Performance marks for LCP, TTI. Custom spans for AI suggestion latency.
 */

/**
 * Records a performance mark for measuring user-perceived latency.
 */
export function markPerformance(name: string): void {
  if (typeof performance !== "undefined") {
    performance.mark(name);
  }
}

/**
 * Measures the duration between two performance marks.
 */
export function measurePerformance(name: string, startMark: string, endMark: string): number | null {
  if (typeof performance === "undefined") {
    return null;
  }
  try {
    const measure = performance.measure(name, startMark, endMark);
    return measure.duration;
  } catch {
    return null;
  }
}

/**
 * Reports a client-side error to the error reporting service.
 * In production this would integrate with Sentry or PA's error tracker.
 */
export function reportError(error: Error, context?: Record<string, string>): void {
  // In production: Sentry.captureException(error, { extra: context })
  console.error("[WC Error]", error.message, context);
}

/**
 * Tracks a custom metric for analytics.
 */
export function trackMetric(name: string, value: number, tags?: Record<string, string>): void {
  // In production: integrate with the PA host's analytics system
  console.debug("[WC Metric]", name, value, tags);
}

/**
 * Timing helper for measuring async operation duration.
 */
export async function withTiming<T>(
  operationName: string,
  fn: () => Promise<T>,
): Promise<{ result: T; durationMs: number }> {
  const start = performance.now();
  const result = await fn();
  const durationMs = performance.now() - start;
  trackMetric(`${operationName}.duration_ms`, durationMs);
  return { result, durationMs };
}

/**
 * Web Vitals observer for LCP and CLS (PRD §14.3).
 */
export function observeWebVitals(): void {
  if (typeof PerformanceObserver === "undefined") {
    return;
  }

  // Largest Contentful Paint
  try {
    const lcpObserver = new PerformanceObserver((list) => {
      const entries = list.getEntries();
      const lastEntry = entries[entries.length - 1];
      if (lastEntry) {
        trackMetric("web_vitals.lcp_ms", lastEntry.startTime);
      }
    });
    lcpObserver.observe({ type: "largest-contentful-paint", buffered: true });
  } catch {
    // Not supported in all browsers
  }

  // Cumulative Layout Shift
  try {
    let clsValue = 0;
    const clsObserver = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const layoutShiftEntry = entry as any;
        if (!layoutShiftEntry.hadRecentInput) {
          clsValue += layoutShiftEntry.value;
        }
      }
      trackMetric("web_vitals.cls", clsValue);
    });
    clsObserver.observe({ type: "layout-shift", buffered: true });
  } catch {
    // Not supported in all browsers
  }
}
