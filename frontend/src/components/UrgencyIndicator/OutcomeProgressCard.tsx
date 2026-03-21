import React from "react";
import { UrgencyBadge } from "./UrgencyBadge.js";
import styles from "./OutcomeProgressCard.module.css";

// ─── Props ─────────────────────────────────────────────────────────────────────

export interface OutcomeProgressCardProps {
  /** Display name of the RCDO outcome. */
  outcomeName: string;
  /** ISO-8601 date string, or null if no target date has been set. */
  targetDate: string | null;
  /** Current progress 0–100. */
  progressPct: number;
  /** Expected progress at today's date given linear progression. */
  expectedProgressPct: number;
  /** Urgency band classification for this outcome. */
  urgencyBand: string;
  /**
   * Calendar days remaining to the target date; negative if overdue.
   * Equal to Number.MIN_SAFE_INTEGER when no target date is set.
   */
  daysRemaining: number;
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Format an ISO-8601 date string (YYYY-MM-DD or full ISO datetime) into a
 * human-readable short date, e.g. "June 30, 2026".
 */
function formatTargetDate(iso: string): string {
  const [year, month, day] = iso.split("T")[0].split("-").map(Number);

  if (![year, month, day].every(Number.isFinite)) {
    return iso;
  }

  // Use a fixed UTC date to avoid off-by-one rendering in local timezones.
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.toLocaleDateString("en-US", {
    month: "long",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  });
}

/**
 * Clamp a number into [0, 100] for use as a CSS percentage width.
 */
function clamp(value: number): number {
  return Math.max(0, Math.min(100, value));
}

/**
 * Decide the CSS modifier class for the progress bar fill, based on the gap
 * between actual progress and expected progress.
 *
 * Gap thresholds:
 *  >= 0           → on-track (green)
 *  < 0 to >= -10  → slight lag (yellow)
 *  < -10 to >= -25 → at-risk (orange)
 *  < -25          → critical (red)
 */
function progressFillClass(progressPct: number, expectedProgressPct: number): string {
  const gap = progressPct - expectedProgressPct;
  if (gap >= 0) return styles.fillOnTrack;
  if (gap >= -10) return styles.fillNeedsAttention;
  if (gap >= -25) return styles.fillAtRisk;
  return styles.fillCritical;
}

// ─── Component ─────────────────────────────────────────────────────────────────

/**
 * OutcomeProgressCard
 *
 * Card component that displays progress details for a single RCDO outcome,
 * including:
 * - Outcome name as heading
 * - UrgencyBadge for the current urgency band
 * - CSS-based progress bar (coloured by gap between actual vs expected progress)
 * - Progress text: "X% complete (Y% expected)"
 * - Target date: "Target: June 30, 2026"
 * - Days remaining or "Overdue" if negative
 *
 * Follows the GlassPanel wood-panel styling approach.
 */
export const OutcomeProgressCard: React.FC<OutcomeProgressCardProps> = ({
  outcomeName,
  targetDate,
  progressPct,
  expectedProgressPct,
  urgencyBand,
  daysRemaining,
}) => {
  const hasTargetDate = targetDate !== null;
  const shouldShowDaysRemaining = hasTargetDate && daysRemaining !== Number.MIN_SAFE_INTEGER;

  const clampedProgressPct = clamp(progressPct);
  const clampedExpectedProgressPct = clamp(expectedProgressPct);
  const roundedProgressPct = Math.round(clampedProgressPct);
  const roundedExpectedProgressPct = Math.round(clampedExpectedProgressPct);

  const fillClass = progressFillClass(progressPct, expectedProgressPct);
  const fillWidth = `${clampedProgressPct}%`;
  const expectedLeft = `${clampedExpectedProgressPct}%`;
  const progressAriaText = `${roundedProgressPct}% complete (${roundedExpectedProgressPct}% expected)`;

  return (
    <div data-testid="outcome-progress-card" className={styles.card}>
      {/* ── Header row ── */}
      <div className={styles.header}>
        <h3 className={styles.heading}>{outcomeName}</h3>
        <UrgencyBadge urgencyBand={urgencyBand} size="sm" />
      </div>

      {/* ── Progress bar ── */}
      <div
        className={styles.progressBarWrap}
        role="progressbar"
        aria-valuenow={roundedProgressPct}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label="Outcome progress"
        aria-valuetext={progressAriaText}
      >
        <div className={`${styles.progressBarFill} ${fillClass}`} style={{ width: fillWidth }} />
        <div
          aria-hidden="true"
          className={styles.expectedMarker}
          style={{ left: expectedLeft }}
          title={`Expected: ${roundedExpectedProgressPct}%`}
        />
      </div>

      {/* ── Progress text ── */}
      <p className={styles.progressText}>
        <span className={styles.progressActual}>{roundedProgressPct}% complete</span>
        <span className={styles.progressExpected}> ({roundedExpectedProgressPct}% expected)</span>
      </p>

      {/* ── Meta row: target date + days remaining ── */}
      <div className={styles.metaRow}>
        {hasTargetDate ? (
          <span className={styles.targetDate}>Target: {formatTargetDate(targetDate)}</span>
        ) : (
          <span className={`${styles.targetDate} ${styles.noTarget}`}>No target date</span>
        )}
        {shouldShowDaysRemaining && (
          <span className={daysRemaining < 0 ? styles.overdue : styles.daysRemaining}>
            {daysRemaining < 0 ? "Overdue" : `${daysRemaining} day${daysRemaining === 1 ? "" : "s"} remaining`}
          </span>
        )}
      </div>
    </div>
  );
};
