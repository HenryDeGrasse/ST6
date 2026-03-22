import React, { useEffect } from "react";
import { useFeatureFlags } from "../../context/FeatureFlagContext.js";
import { useForecasts } from "../../hooks/useForecasts.js";
import styles from "./Phase5Panels.module.css";

export interface OutcomeRiskCardProps {
  outcomeId: string;
}

function fmtPct(value: number | null): string {
  return value === null ? "—" : `${Math.round(value)}%`;
}

function fmtDate(value: string | null): string {
  return value ?? "—";
}

export const OutcomeRiskCard: React.FC<OutcomeRiskCardProps> = ({ outcomeId }) => {
  const flags = useFeatureFlags();
  const { selectedForecast, loadingForecast, errorForecast, fetchForecast } = useForecasts();

  useEffect(() => {
    if (flags.targetDateForecasting && outcomeId) {
      void fetchForecast(outcomeId);
    }
  }, [fetchForecast, flags.targetDateForecasting, outcomeId]);

  if (!flags.targetDateForecasting) {
    return null;
  }

  if (loadingForecast) {
    return (
      <section data-testid="outcome-risk-card" className={styles.panel}>
        <div data-testid="outcome-risk-loading" className={styles.loading}>Loading forecast…</div>
      </section>
    );
  }

  if (errorForecast) {
    return (
      <section data-testid="outcome-risk-card" className={styles.panel}>
        <div data-testid="outcome-risk-error" className={styles.error}>{errorForecast}</div>
      </section>
    );
  }

  if (!selectedForecast) {
    return null;
  }

  return (
    <section data-testid="outcome-risk-card" className={styles.panel}>
      <div className={styles.headerRow}>
        <div>
          <div className={styles.eyebrow}>Outcome Forecast</div>
          <h3 className={styles.title}>{selectedForecast.outcomeName}</h3>
        </div>
        <span data-testid="outcome-risk-status" className={styles.statusBadge}>
          {selectedForecast.forecastStatus ?? "UNKNOWN"}
        </span>
      </div>

      <div className={styles.metricGrid}>
        <div className={styles.metricCard} data-testid="outcome-risk-target-date">
          <span className={styles.metricLabel}>Target Date</span>
          <span className={styles.metricValueSmall}>{fmtDate(selectedForecast.targetDate)}</span>
        </div>
        <div className={styles.metricCard} data-testid="outcome-risk-projected-date">
          <span className={styles.metricLabel}>Projected Date</span>
          <span className={styles.metricValueSmall}>{fmtDate(selectedForecast.projectedTargetDate)}</span>
        </div>
        <div className={styles.metricCard} data-testid="outcome-risk-progress">
          <span className={styles.metricLabel}>Projected Progress</span>
          <span className={styles.metricValueSmall}>{fmtPct(selectedForecast.projectedProgressPct)}</span>
        </div>
        <div className={styles.metricCard} data-testid="outcome-risk-confidence">
          <span className={styles.metricLabel}>Confidence</span>
          <span className={styles.metricValueSmall}>
            {selectedForecast.confidenceBand ?? "—"}
            {selectedForecast.confidenceScore !== null ? ` · ${Math.round(selectedForecast.confidenceScore * 100)}%` : ""}
          </span>
        </div>
      </div>

      {selectedForecast.contributingFactors.length > 0 && (
        <div data-testid="outcome-risk-factors" className={styles.section}>
          <h4 className={styles.sectionTitle}>Contributing Factors</h4>
          <ul className={styles.list}>
            {selectedForecast.contributingFactors.map((factor, index) => (
              <li key={`${factor.type}-${index}`} data-testid={`outcome-risk-factor-${index}`} className={styles.listItem}>
                <strong>{factor.label}</strong> — {factor.detail}
              </li>
            ))}
          </ul>
        </div>
      )}

      {selectedForecast.recommendations.length > 0 && (
        <div data-testid="outcome-risk-recommendations" className={styles.section}>
          <h4 className={styles.sectionTitle}>Recommended Actions</h4>
          <ul className={styles.list}>
            {selectedForecast.recommendations.map((item, index) => (
              <li key={`${item}-${index}`} data-testid={`outcome-risk-recommendation-${index}`} className={styles.listItem}>
                {item}
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
};
