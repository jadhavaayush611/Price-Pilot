/**
 * Core Web Vitals & Production Diagnostics Helper
 * Exposes readiness hooks for monitoring LCP, CLS, FCP, TTFB, and route timing metrics.
 */

export interface Metric {
  name: string;
  value: number;
  rating: 'good' | 'needs-improvement' | 'poor';
  delta: number;
  id: string;
}

export function initWebVitals(onReport?: (metric: Metric) => void) {
  if (typeof window === 'undefined' || !('performance' in window)) return;

  const report = (name: string, value: number, targetGood: number, targetPoor: number) => {
    const rating: Metric['rating'] = value <= targetGood ? 'good' : value <= targetPoor ? 'needs-improvement' : 'poor';
    const metric: Metric = {
      name,
      value: Math.round(value),
      rating,
      delta: value,
      id: `v1-${Date.now()}-${Math.floor(Math.random() * 1000)}`
    };

    if (onReport) {
      onReport(metric);
    } else if (import.meta.env.DEV) {
      console.debug(`[Web Vitals] ${metric.name}: ${metric.value}ms (${metric.rating})`);
    }
  };

  // Measure Navigation Timing (TTFB & FCP)
  window.addEventListener('load', () => {
    setTimeout(() => {
      const navEntries = performance.getEntriesByType('navigation') as PerformanceNavigationTiming[];
      if (navEntries.length > 0) {
        const nav = navEntries[0];
        report('TTFB', nav.responseStart - nav.requestStart, 800, 1800);
        report('DomInteractive', nav.domInteractive, 1500, 3000);
        report('DomComplete', nav.domComplete, 3000, 6000);
      }

      const paintEntries = performance.getEntriesByType('paint');
      paintEntries.forEach((entry) => {
        if (entry.name === 'first-contentful-paint') {
          report('FCP', entry.startTime, 1800, 3000);
        }
      });
    }, 0);
  });
}

/**
 * Utility to log route navigation timing in development
 */
export function trackRouteTiming(routeName: string, startTime: number) {
  const duration = performance.now() - startTime;
  if (import.meta.env.DEV) {
    console.debug(`[Route Timing] Navigated to ${routeName} in ${duration.toFixed(2)}ms`);
  }
}
