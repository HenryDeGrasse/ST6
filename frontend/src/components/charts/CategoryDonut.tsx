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
  DELIVERY:   "#2563eb", // blue   — primary delivery
  OPERATIONS: "#0f766e", // teal   — operational work
  CUSTOMER:   "#7c3aed", // purple — customer-facing
  PEOPLE:     "#059669", // green  — people & org
  LEARNING:   "#d97706", // amber  — learning & growth
  GTM:        "#dc2626", // red    — go-to-market
  TECH_DEBT:  "#94a3b8", // gray   — tech debt
};

const CATEGORY_LABELS: Record<string, string> = {
  DELIVERY: "Delivery",
  OPERATIONS: "Operations",
  CUSTOMER: "Customer",
  PEOPLE: "People",
  LEARNING: "Learning",
  GTM: "GTM",
  TECH_DEBT: "Tech Debt",
};

function formatCategoryLabel(category: string): string {
  return category
    .toLowerCase()
    .split("_")
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(" ");
}

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
              stroke={DONUT_COLORS[cat] ?? "#94a3b8"}
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
              style={{ backgroundColor: DONUT_COLORS[cat] ?? "#94a3b8" }}
            />
            <span className={styles.legendLabel}>
              {CATEGORY_LABELS[cat] ?? formatCategoryLabel(cat)}
            </span>
            <span className={styles.legendValue}>{fmtPct(val / total)}</span>
          </div>
        ))}
      </div>
    </div>
  );
};
