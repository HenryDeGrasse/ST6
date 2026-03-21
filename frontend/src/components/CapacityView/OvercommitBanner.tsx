import React from "react";
import styles from "./OvercommitBanner.module.css";

// ─── Types ─────────────────────────────────────────────────────────────────────

export type OvercommitLevel = "NONE" | "MODERATE" | "HIGH";

// ─── Props ─────────────────────────────────────────────────────────────────────

export interface OvercommitBannerProps {
  /** Severity level of the overcommitment. Renders nothing when 'NONE'. */
  level: OvercommitLevel;
  /** Human-readable description of the overcommitment situation. */
  message: string;
  /** Total hours committed this week (estimated). */
  adjustedTotal: number;
  /** Realistic weekly capacity cap in hours. */
  realisticCap: number;
}

// ─── Component ─────────────────────────────────────────────────────────────────

/**
 * OvercommitBanner
 *
 * Displays a warning banner when the user is overcommitted for the week.
 *
 * - Renders nothing for level === 'NONE'.
 * - MODERATE: yellow/amber banner with ⚠️ icon.
 * - HIGH: red banner with ⛔ icon.
 *
 * Uses `data-testid="overcommit-banner"` for test queries.
 */
export const OvercommitBanner: React.FC<OvercommitBannerProps> = ({
  level,
  message,
  adjustedTotal,
  realisticCap,
}) => {
  if (level === "NONE") return null;

  const icon = level === "HIGH" ? "⛔" : "⚠️";
  const bannerClass = level === "HIGH" ? `${styles.banner} ${styles.high}` : `${styles.banner} ${styles.moderate}`;

  return (
    <div
      data-testid="overcommit-banner"
      className={bannerClass}
      role="alert"
      aria-live="polite"
    >
      <span className={styles.icon} aria-hidden="true">
        {icon}
      </span>
      <div className={styles.content}>
        <span className={styles.message}>{message}</span>
        <span className={styles.stats}>
          {adjustedTotal}h committed · {realisticCap}h cap
        </span>
      </div>
      {/* Extra testid element for level-specific queries */}
      <span data-testid={`overcommit-level-${level}`} className={styles.levelTag}>
        {level}
      </span>
    </div>
  );
};
