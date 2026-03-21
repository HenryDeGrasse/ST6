import React from "react";
import type { Prediction } from "@weekly-commitments/contracts";
import { useFeatureFlags } from "../../context/FeatureFlagContext.js";
import styles from "./PredictionAlerts.module.css";

export interface PredictionAlertsProps {
  predictions: Prediction[];
  loading: boolean;
}

const CONFIDENCE_LABEL: Record<Prediction["confidence"], string> = {
  HIGH: "High",
  MEDIUM: "Medium",
  LOW: "Low",
};

const CONFIDENCE_STYLE_CLASS: Record<Prediction["confidence"], string> = {
  HIGH: styles.confidenceHigh,
  MEDIUM: styles.confidenceMedium,
  LOW: styles.confidenceLow,
};

/**
 * Formats a raw prediction type key into a human-readable label.
 * E.g. "CARRY_FORWARD_RISK" → "Carry Forward Risk"
 */
function formatTypeLabel(type: string): string {
  return type
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

/**
 * List of prediction alerts for likely (high-confidence) events.
 *
 * Filters predictions to `likely === true` and displays each as a card with:
 * - type label
 * - confidence badge (HIGH=red, MEDIUM=orange, LOW=yellow)
 * - reason text
 *
 * Shows an empty state when no likely predictions are available.
 * Gated by the `predictions` feature flag.
 */
export const PredictionAlerts: React.FC<PredictionAlertsProps> = ({ predictions, loading }) => {
  const flags = useFeatureFlags();

  if (!flags.predictions) {
    return null;
  }

  const likelyPredictions = predictions.filter((p) => p.likely);

  return (
    <div data-testid="prediction-alerts" className={styles.panel}>
      {/* ─── Header ─────────────────────────────────────────────────────────── */}
      <div className={styles.header}>
        <span className={styles.title}>Prediction Alerts</span>
        {likelyPredictions.length > 0 && !loading && (
          <span className={styles.count} aria-label={`${likelyPredictions.length} prediction alerts`}>
            {likelyPredictions.length}
          </span>
        )}
      </div>

      {/* ─── Loading ─────────────────────────────────────────────────────────── */}
      {loading && (
        <div data-testid="prediction-alerts-loading" className={styles.statusMsg}>
          Loading predictions…
        </div>
      )}

      {/* ─── Empty state ─────────────────────────────────────────────────────── */}
      {!loading && likelyPredictions.length === 0 && (
        <div data-testid="prediction-alerts-empty" className={styles.statusMsg}>
          No predictions for this period
        </div>
      )}

      {/* ─── Prediction list ─────────────────────────────────────────────────── */}
      {!loading && likelyPredictions.length > 0 && (
        <ul className={styles.list} role="list" aria-label="Prediction alerts">
          {likelyPredictions.map((prediction, index) => (
            <li
              key={`${prediction.type}-${prediction.subjectId}-${index}`}
              data-testid={`prediction-${index}`}
              className={styles.card}
            >
              {/* Type label */}
              <div className={styles.typeLabel}>{formatTypeLabel(prediction.type)}</div>

              {/* Confidence badge */}
              <span
                data-testid={`prediction-confidence-${index}`}
                className={`${styles.confidenceBadge} ${CONFIDENCE_STYLE_CLASS[prediction.confidence]}`}
                aria-label={`Confidence: ${CONFIDENCE_LABEL[prediction.confidence]}`}
              >
                {CONFIDENCE_LABEL[prediction.confidence]}
              </span>

              {/* Likelihood status */}
              <span className={styles.likelyBadge} aria-label="Likely prediction">
                Likely
              </span>

              {/* Reason text */}
              <p className={styles.reason}>{prediction.reason}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};
