/**
 * Sparkline — minimal inline SVG sparkline with gradient fill.
 *
 * A thin polyline rendered inside an SVG viewBox, with a subtle
 * gradient-fill area beneath the line and a terminal dot on the last
 * data point.
 */
import React, { useId } from "react";
import styles from "./charts.module.css";

export interface SparklineProps {
  /** Array of numeric values to plot (need at least 2 points). */
  data: number[];
  /** SVG intrinsic width in px. @default 140 */
  width?: number;
  /** SVG intrinsic height in px. @default 36 */
  height?: number;
  /** Stroke / fill accent color. @default var(--wc-color-accent, #C9A962) */
  color?: string;
  /** Accessible label for the SVG element. */
  label: string;
}

/**
 * Renders a minimal SVG sparkline with a gradient fill area.
 * Returns null when fewer than 2 data points are provided.
 */
export const Sparkline: React.FC<SparklineProps> = ({
  data,
  width = 140,
  height = 36,
  color = "var(--wc-color-accent, #C9A962)",
  label,
}) => {
  const gradientId = useId();

  if (data.length < 2) return null;

  const pad = 2;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const points = data.map((v, i) => {
    const x = pad + (i / (data.length - 1)) * (width - 2 * pad);
    const y = height - pad - ((v - min) / range) * (height - 2 * pad);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  const fillPoints = `${pad},${height - pad} ${points.join(" ")} ${width - pad},${height - pad}`;
  const gradId = `spark-grad-${gradientId.replace(/:/g, "-")}-${label.replace(/\s/g, "-")}`;

  return (
    <svg
      data-testid="sparkline"
      viewBox={`0 0 ${width} ${height}`}
      aria-label={`${label} sparkline`}
      role="img"
      className={styles.sparkline}
      preserveAspectRatio="none"
    >
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.25" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon points={fillPoints} fill={`url(#${gradId})`} />
      <polyline
        points={points.join(" ")}
        fill="none"
        stroke={color}
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* terminal dot */}
      {data.length > 0 && (() => {
        const lastPt = points[points.length - 1].split(",");
        return <circle cx={lastPt[0]} cy={lastPt[1]} r="2.5" fill={color} />;
      })()}
    </svg>
  );
};
