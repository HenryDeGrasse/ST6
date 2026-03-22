/**
 * Shared utility helpers for chart components.
 */

/** Format a 0–1 fraction as a percentage string, e.g. 0.75 → "75%" */
export function fmtPct(value: number): string {
  return `${Math.round(value * 100)}%`;
}

/** Clamp v between lo and hi (inclusive). */
export function clamp(v: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, v));
}
