/**
 * StackedBar — horizontal stacked bar for multi-segment data.
 *
 * Each segment is rendered as a proportional slice of the total,
 * with an optional in-bar percentage label and a legend row below.
 */
import React from "react";
import { fmtPct } from "./utils.js";
import styles from "./charts.module.css";

export interface StackedBarSegment {
  /** Display label for the segment. */
  label: string;
  /** Numeric value (any unit; ratios computed from total). */
  value: number;
  /** Fill color for this segment. */
  color: string;
}

export interface StackedBarProps {
  /** Array of segments to render from left to right. */
  segments: StackedBarSegment[];
  /** Height of the bar track in px. @default 20 */
  height?: number;
}

/**
 * Renders a horizontal stacked bar with a legend.
 * Segments with value ≤ 0 are omitted.
 * Returns null when there are no valid segments.
 */
export const StackedBar: React.FC<StackedBarProps> = ({ segments, height = 20 }) => {
  const valid = segments.filter((s) => s.value > 0);
  if (valid.length === 0) return null;

  const total = valid.reduce((sum, s) => sum + s.value, 0);

  return (
    <div className={styles.stackedBar} data-testid="stacked-bar">
      <div
        className={styles.stackedBarTrack}
        style={{ height: `${height}px` }}
        aria-label="Stacked bar chart"
        role="img"
      >
        {valid.map((seg) => {
          const pct = (seg.value / total) * 100;
          return (
            <div
              key={seg.label}
              className={styles.stackedBarSegment}
              style={{ width: `${pct}%`, backgroundColor: seg.color }}
              title={`${seg.label}: ${fmtPct(seg.value / total)}`}
            >
              {pct >= 12 && (
                <span className={styles.stackedBarSegmentLabel}>
                  {fmtPct(seg.value / total)}
                </span>
              )}
            </div>
          );
        })}
      </div>
      <div className={styles.stackedBarLegend}>
        {valid.map((seg) => (
          <span key={seg.label} className={styles.stackedBarLegendItem}>
            <span
              className={styles.stackedBarLegendSwatch}
              style={{ backgroundColor: seg.color }}
            />
            {seg.label}
          </span>
        ))}
      </div>
    </div>
  );
};
