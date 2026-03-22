/**
 * EffortTypeChart — SVG donut chart with legend for effort type distributions.
 *
 * Mirrors the CategoryDonut pattern but uses the Phase 6 EffortType palette
 * (BUILD / MAINTAIN / COLLABORATE / LEARN). Accepts any Record<string, number>
 * so it stays compatible with both the trends `effortTypeDistribution` shape
 * and ad-hoc count maps derived from issue lists.
 */
import React from "react";
import { fmtPct } from "./utils.js";
import styles from "./charts.module.css";

/** Default palette keyed by EffortType name. */
export const EFFORT_TYPE_COLORS: Record<string, string> = {
  BUILD: "#C9A962",       // gold   — building new features
  MAINTAIN: "#7A8C6E",    // green  — maintenance & operational
  COLLABORATE: "#8B6E8C", // purple — cross-team collaboration
  LEARN: "#6E7A8C",       // slate  — learning & upskilling
};

const EFFORT_TYPE_LABELS: Record<string, string> = {
  BUILD: "Build",
  MAINTAIN: "Maintain",
  COLLABORATE: "Collaborate",
  LEARN: "Learn",
};

export interface EffortTypeChartProps {
  /** Map of EffortType name → numeric value (ratios computed internally). */
  data: Record<string, number>;
  /** Diameter of the SVG in px. @default 100 */
  size?: number;
}

/**
 * Renders a stroke-based donut chart for effort type distribution data.
 * Returns null when there are no positive-value entries.
 */
export const EffortTypeChart: React.FC<EffortTypeChartProps> = ({ data, size = 100 }) => {
  const entries = Object.entries(data).filter(([, v]) => v > 0);
  if (entries.length === 0) return null;

  const total = entries.reduce((s, [, v]) => s + v, 0);
  const r = 36;
  const circumference = 2 * Math.PI * r;
  let cumulative = 0;

  return (
    <div className={styles.donutWrap}>
      <svg
        data-testid="effort-type-chart"
        width={size}
        height={size}
        viewBox="0 0 100 100"
        className={styles.donutSvg}
        aria-label="Effort type distribution donut chart"
        role="img"
      >
        {entries.map(([type, val]) => {
          const fraction = val / total;
          const dash = fraction * circumference;
          const offset = -cumulative * circumference;
          cumulative += fraction;
          return (
            <circle
              key={type}
              cx="50"
              cy="50"
              r={r}
              fill="none"
              stroke={EFFORT_TYPE_COLORS[type] ?? "#9C8B7A"}
              strokeWidth="12"
              strokeDasharray={`${dash} ${circumference - dash}`}
              strokeDashoffset={offset}
              transform="rotate(-90 50 50)"
            />
          );
        })}
      </svg>
      <div className={styles.donutLegend}>
        {entries.map(([type, val]) => (
          <div key={type} className={styles.legendItem}>
            <span
              className={styles.legendSwatch}
              style={{ backgroundColor: EFFORT_TYPE_COLORS[type] ?? "#9C8B7A" }}
            />
            <span className={styles.legendLabel}>
              {EFFORT_TYPE_LABELS[type] ?? (type.charAt(0) + type.slice(1).toLowerCase())}
            </span>
            <span className={styles.legendValue}>{fmtPct(val / total)}</span>
          </div>
        ))}
      </div>
    </div>
  );
};
