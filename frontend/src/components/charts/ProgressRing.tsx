/**
 * ProgressRing — small circular SVG for percentage display.
 *
 * Renders a stroke-based ring (annulus) with the percentage value in
 * the centre. Useful for compact KPI indicators.
 */
import React from "react";
import { clamp, fmtPct } from "./utils.js";
import styles from "./charts.module.css";

export interface ProgressRingProps {
  /** Completion fraction in the range 0–1. */
  value: number;
  /** Diameter of the SVG in px. @default 64 */
  size?: number;
  /** Ring stroke color. @default var(--wc-color-accent, #C9A962) */
  color?: string;
  /** Optional label rendered below the ring. */
  label?: string;
}

/**
 * Renders a circular progress ring with a centered percentage label.
 */
export const ProgressRing: React.FC<ProgressRingProps> = ({
  value,
  size = 64,
  color = "var(--wc-color-accent, #C9A962)",
  label,
}) => {
  const strokeWidth = size * 0.1;
  const r = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * r;
  const clamped = clamp(value, 0, 1);
  const dash = clamped * circumference;
  const cx = size / 2;
  const cy = size / 2;

  return (
    <div className={styles.progressRingWrap}>
      <svg
        data-testid="progress-ring"
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        className={styles.progressRingSvg}
        aria-label={label ? `${label}: ${fmtPct(clamped)}` : `${fmtPct(clamped)}`}
        role="img"
      >
        {/* Track */}
        <circle
          cx={cx}
          cy={cy}
          r={r}
          fill="none"
          stroke="rgba(60, 50, 40, 0.5)"
          strokeWidth={strokeWidth}
        />
        {/* Progress arc */}
        <circle
          cx={cx}
          cy={cy}
          r={r}
          fill="none"
          stroke={color}
          strokeWidth={strokeWidth}
          strokeDasharray={`${dash} ${circumference - dash}`}
          strokeDashoffset={circumference / 4} /* start at 12 o'clock */
          strokeLinecap="round"
          transform={`rotate(-90 ${cx} ${cy})`}
        />
        {/* Center text */}
        <text
          x={cx}
          y={cy}
          textAnchor="middle"
          dominantBaseline="central"
          fill="var(--wc-color-text, #E8DFD4)"
          fontFamily="'Cinzel', serif"
          fontSize={size * 0.2}
          fontWeight="600"
        >
          {fmtPct(clamped)}
        </text>
      </svg>
      {label && <span className={styles.progressRingLabel}>{label}</span>}
    </div>
  );
};
