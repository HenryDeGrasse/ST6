/**
 * CategoryDonut — SVG donut chart with legend for category distributions.
 *
 * Renders a stroke-based donut chart where each category occupies an arc
 * proportional to its value. A colour legend is shown to the right.
 */
import React from "react";
import { fmtPct } from "./utils.js";
import styles from "./charts.module.css";

/** Default palette keyed by category name. */
export const DONUT_COLORS: Record<string, string> = {
  DELIVERY: "#C9A962",
  OPERATIONS: "#7A8C6E",
  CUSTOMER: "#8B6E8C",
  PEOPLE: "#6E7A8C",
  LEARNING: "#8C7A6E",
  GTM: "#6E8C8B",
  TECH_DEBT: "#8C6E6E",
};

export interface CategoryDonutProps {
  /** Map of category name → numeric value (any unit; ratios are computed internally). */
  data: Record<string, number>;
  /** Diameter of the SVG in px. @default 100 */
  size?: number;
}

/**
 * Renders a stroke-based donut chart for category distribution data.
 * Returns null when there are no positive-value entries.
 */
export const CategoryDonut: React.FC<CategoryDonutProps> = ({ data, size = 100 }) => {
  const entries = Object.entries(data).filter(([, v]) => v > 0);
  if (entries.length === 0) return null;

  const total = entries.reduce((s, [, v]) => s + v, 0);
  const r = 36;
  const circumference = 2 * Math.PI * r;
  let cumulative = 0;

  return (
    <div className={styles.donutWrap}>
      <svg
        data-testid="category-donut"
        width={size}
        height={size}
        viewBox="0 0 100 100"
        className={styles.donutSvg}
        aria-label="Category distribution donut chart"
        role="img"
      >
        {entries.map(([cat, val]) => {
          const fraction = val / total;
          const dash = fraction * circumference;
          const offset = -cumulative * circumference;
          cumulative += fraction;
          return (
            <circle
              key={cat}
              cx="50"
              cy="50"
              r={r}
              fill="none"
              stroke={DONUT_COLORS[cat] ?? "#9C8B7A"}
              strokeWidth="12"
              strokeDasharray={`${dash} ${circumference - dash}`}
              strokeDashoffset={offset}
              transform="rotate(-90 50 50)"
            />
          );
        })}
      </svg>
      <div className={styles.donutLegend}>
        {entries.map(([cat, val]) => (
          <div key={cat} className={styles.legendItem}>
            <span
              className={styles.legendSwatch}
              style={{ backgroundColor: DONUT_COLORS[cat] ?? "#9C8B7A" }}
            />
            <span className={styles.legendLabel}>
              {cat.charAt(0) + cat.slice(1).toLowerCase()}
            </span>
            <span className={styles.legendValue}>{fmtPct(val / total)}</span>
          </div>
        ))}
      </div>
    </div>
  );
};
