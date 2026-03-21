import React from "react";
import type { SlackBand } from "../../hooks/useOutcomeMetadata.js";
import styles from "./StrategicSlackBanner.module.css";

// ─── Props ─────────────────────────────────────────────────────────────────────

export interface StrategicSlackBannerProps {
  /** Strategic slack band classification for the org. */
  slackBand: SlackBand | string;
  /**
   * Recommended strategic focus floor as a decimal in the range 0.50–0.95.
   * Displayed as a percentage, e.g. 0.75 → "75%".
   */
  strategicFocusFloor: number;
  /** Number of outcomes in the AT_RISK urgency band. */
  atRiskCount: number;
  /** Number of outcomes in the CRITICAL urgency band. */
  criticalCount: number;
}

// ─── Band config ────────────────────────────────────────────────────────────────

interface BandConfig {
  /** Short label used in the headline text, e.g. "HIGH", "LOW". */
  label: string;
  /** Icon rendered at the start of the banner. */
  icon: string;
  /** CSS modifier class applied to the root element. */
  className: string;
}

const BAND_CONFIG: Record<SlackBand, BandConfig> = {
  HIGH_SLACK: {
    label: "HIGH",
    icon: "✅",
    className: styles.highSlack,
  },
  MODERATE_SLACK: {
    label: "MODERATE",
    icon: "⚠️",
    className: styles.moderate,
  },
  LOW_SLACK: {
    label: "LOW",
    icon: "🟠",
    className: styles.low,
  },
  NO_SLACK: {
    label: "NO SLACK",
    icon: "🔴",
    className: styles.noSlack,
  },
};

const FALLBACK_CONFIG: BandConfig = {
  label: "UNKNOWN",
  icon: "ℹ️",
  className: styles.moderate,
};

function normalizeSlackBand(slackBand: SlackBand | string): SlackBand | null {
  if (typeof slackBand !== "string") {
    return slackBand;
  }

  const normalized = slackBand.trim().toUpperCase().replace(/[\s-]+/g, "_");

  switch (normalized) {
    case "HIGH":
    case "HIGH_SLACK":
      return "HIGH_SLACK";
    case "MODERATE":
    case "MODERATE_SLACK":
      return "MODERATE_SLACK";
    case "LOW":
    case "LOW_SLACK":
      return "LOW_SLACK";
    case "NONE":
    case "NO":
    case "NO_SLACK":
      return "NO_SLACK";
    default:
      return null;
  }
}

// ─── Component ─────────────────────────────────────────────────────────────────

/**
 * StrategicSlackBanner
 *
 * Banner component that communicates the current strategic slack level across
 * the org's RCDO outcome portfolio.
 *
 * Colour-coded by slack band:
 * - HIGH_SLACK   → green  ("capacity available for strategic work")
 * - MODERATE_SLACK → yellow ("monitor urgency proactively")
 * - LOW_SLACK    → orange ("attention required soon")
 * - NO_SLACK     → red   ("all outcomes need immediate action")
 *
 * Also shows:
 * - How many outcomes need attention (atRiskCount + criticalCount)
 * - The recommended strategic focus floor as a percentage
 *
 * Uses `data-testid="strategic-slack-banner"` for test queries.
 */
export const StrategicSlackBanner: React.FC<StrategicSlackBannerProps> = ({
  slackBand,
  strategicFocusFloor,
  atRiskCount,
  criticalCount,
}) => {
  const normalizedBand = normalizeSlackBand(slackBand);
  const config = normalizedBand ? BAND_CONFIG[normalizedBand] : FALLBACK_CONFIG;

  const attentionCount = atRiskCount + criticalCount;
  const floorPct = Math.round(strategicFocusFloor * 100);

  const attentionText =
    attentionCount === 1
      ? "1 outcome needs attention before its target date"
      : `${attentionCount} outcomes need attention before target dates`;

  return (
    <div
      data-testid="strategic-slack-banner"
      className={`${styles.banner} ${config.className}`}
      role="status"
      aria-live="polite"
      aria-label={`Strategic slack: ${config.label}`}
    >
      <span className={styles.icon} aria-hidden="true">
        {config.icon}
      </span>

      <div className={styles.content}>
        <span className={styles.headline}>
          Strategic slack:{" "}
          <strong data-testid="strategic-slack-band-label">{config.label}</strong>
          {" — "}
          <span data-testid="strategic-slack-attention-text">{attentionText}</span>
        </span>

        <span className={styles.floorHint} data-testid="strategic-slack-floor-hint">
          Recommended strategic focus: {floorPct}%
        </span>
      </div>
    </div>
  );
};
