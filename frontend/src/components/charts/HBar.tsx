/**
 * HBar — horizontal progress bar with label and percentage value.
 *
 * Used in priority distribution and category completion-rate lists.
 */
import React from "react";
import { clamp, fmtPct } from "./utils.js";
import styles from "./charts.module.css";

export interface HBarProps {
  /** Text label displayed to the left of the bar. */
  label: string;
  /** Value in the range 0–1 (clamped). */
  value: number;
  /** Fill color. @default var(--wc-color-accent, #2563eb) */
  color?: string;
}

/**
 * Renders a labeled horizontal bar where `value` is a 0–1 fraction.
 */
export const HBar: React.FC<HBarProps> = ({
  label,
  value,
  color = "var(--wc-color-accent, #2563eb)",
}) => (
  <div className={styles.hbar} data-testid="hbar">
    <span className={styles.hbarLabel}>{label}</span>
    <div className={styles.hbarTrack}>
      <div
        className={styles.hbarFill}
        style={{ width: `${clamp(value * 100, 0, 100)}%`, backgroundColor: color }}
      />
    </div>
    <span className={styles.hbarValue}>{fmtPct(value)}</span>
  </div>
);
