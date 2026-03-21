import React from "react";
import type { UrgencyBand } from "../../hooks/useOutcomeMetadata.js";
import styles from "./UrgencyBadge.module.css";

// ─── Props ─────────────────────────────────────────────────────────────────────

export interface UrgencyBadgeProps {
  /** The urgency band classification returned by the backend. */
  urgencyBand: UrgencyBand | string;
  /** Visual size of the badge. Defaults to 'md'. */
  size?: "sm" | "md";
}

// ─── Band config map ────────────────────────────────────────────────────────────

interface BandConfig {
  label: string;
  ariaLabel: string;
  className: string;
}

const BAND_CONFIG: Record<UrgencyBand, BandConfig> = {
  ON_TRACK: {
    label: "✓ On Track",
    ariaLabel: "On Track",
    className: styles.onTrack,
  },
  NEEDS_ATTENTION: {
    label: "⚠ Attention",
    ariaLabel: "Needs Attention",
    className: styles.needsAttention,
  },
  AT_RISK: {
    label: "⚠ At Risk",
    ariaLabel: "At Risk",
    className: styles.atRisk,
  },
  CRITICAL: {
    label: "🔴 Critical",
    ariaLabel: "Critical",
    className: styles.critical,
  },
  NO_TARGET: {
    label: "No Target",
    ariaLabel: "No Target",
    className: styles.noTarget,
  },
};

const FALLBACK_CONFIG: BandConfig = {
  label: "Unknown",
  ariaLabel: "Unknown urgency",
  className: styles.noTarget,
};

// ─── Component ─────────────────────────────────────────────────────────────────

/**
 * UrgencyBadge
 *
 * Small inline badge that visually communicates the urgency band of an RCDO
 * outcome.  Colour-coded with accessible aria-label and data attributes for
 * test queries.
 *
 * - ON_TRACK       → green background, '✓ On Track'
 * - NEEDS_ATTENTION → yellow background, '⚠ Attention'
 * - AT_RISK         → orange background, '⚠ At Risk'
 * - CRITICAL        → red background, '🔴 Critical'
 * - NO_TARGET       → gray background, 'No Target'
 */
export const UrgencyBadge: React.FC<UrgencyBadgeProps> = ({ urgencyBand, size = "md" }) => {
  const normalizedBand = typeof urgencyBand === "string" ? urgencyBand.trim().toUpperCase() : urgencyBand;
  const config = BAND_CONFIG[normalizedBand as UrgencyBand] ?? FALLBACK_CONFIG;

  const sizeClass = size === "sm" ? styles.sm : styles.md;

  return (
    <span
      data-testid="urgency-badge"
      data-urgency-band={normalizedBand}
      aria-label={`Urgency: ${config.ariaLabel}`}
      className={`${styles.badge} ${config.className} ${sizeClass}`}
      title={config.ariaLabel}
    >
      {config.label}
    </span>
  );
};
