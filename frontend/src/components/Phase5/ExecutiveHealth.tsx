import React, { useEffect } from "react";
import { useFeatureFlags } from "../../context/FeatureFlagContext.js";
import { useExecutiveDashboard } from "../../hooks/useExecutiveDashboard.js";
import styles from "./Phase5Panels.module.css";

export interface ExecutiveHealthProps {
  weekStart?: string;
}

function fmtPct(value: number | null): string {
  return value === null ? "—" : `${Math.round(value)}%`;
}

function fmtConfidence(value: number | null): string {
  return value === null ? "—" : `${Math.round(value * 100)}%`;
}

export const ExecutiveHealth: React.FC<ExecutiveHealthProps> = ({ weekStart }) => {
  const flags = useFeatureFlags();
  const { dashboard, dashboardStatus, errorDashboard, fetchDashboard } = useExecutiveDashboard();

  useEffect(() => {
    if (flags.executiveDashboard) {
      void fetchDashboard(weekStart);
    }
  }, [fetchDashboard, flags.executiveDashboard, weekStart]);

  if (!flags.executiveDashboard) {
    return null;
  }

  return (
    <section data-testid="executive-health" className={styles.panel}>
      <div className={styles.headerRow}>
        <div>
          <div className={styles.eyebrow}>Executive View</div>
          <h3 className={styles.title}>Strategic Health</h3>
        </div>
        <button
          type="button"
          data-testid="executive-health-refresh"
          className={styles.secondaryButton}
          onClick={() => {
            void fetchDashboard(weekStart);
          }}
        >
          Refresh
        </button>
      </div>

      {dashboardStatus === "loading" && (
        <div data-testid="executive-health-loading" className={styles.loading}>
          Loading executive dashboard…
        </div>
      )}
      {errorDashboard && (
        <div data-testid="executive-health-error" className={styles.error}>
          {errorDashboard}
        </div>
      )}
      {!errorDashboard && dashboardStatus === "unavailable" && (
        <div data-testid="executive-health-unavailable" className={styles.empty}>
          Executive dashboard is unavailable.
        </div>
      )}

      {dashboard && dashboardStatus === "ok" && (
        <>
          <div data-testid="executive-health-summary" className={styles.metricGrid}>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>Forecasts</span>
              <span className={styles.metricValueSmall}>{dashboard.summary.totalForecasts}</span>
            </div>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>On Track</span>
              <span className={styles.metricValueSmall}>{dashboard.summary.onTrackForecasts}</span>
            </div>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>Needs Attention</span>
              <span className={styles.metricValueSmall}>{dashboard.summary.needsAttentionForecasts}</span>
            </div>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>At Risk</span>
              <span className={styles.metricValueSmall}>{dashboard.summary.offTrackForecasts}</span>
            </div>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>Planning Coverage</span>
              <span className={styles.metricValueSmall}>{fmtPct(dashboard.summary.planningCoveragePct)}</span>
            </div>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>Avg Confidence</span>
              <span className={styles.metricValueSmall}>
                {fmtConfidence(dashboard.summary.averageForecastConfidence)}
              </span>
            </div>
          </div>

          <div className={styles.section} data-testid="executive-health-rally-cries">
            <h4 className={styles.sectionTitle}>Rally Cry Rollups</h4>
            <ul className={styles.list}>
              {dashboard.rallyCryRollups.map((rollup) => (
                <li key={rollup.rallyCryName} className={styles.listItem}>
                  <strong>{rollup.rallyCryName}</strong> — {rollup.onTrackCount} on track, {rollup.needsAttentionCount} needs attention, {rollup.offTrackCount} at risk
                </li>
              ))}
            </ul>
          </div>

          <div className={styles.section} data-testid="executive-health-team-buckets">
            <h4 className={styles.sectionTitle}>Team Buckets</h4>
            <ul className={styles.list}>
              {dashboard.teamBuckets.map((bucket) => (
                <li key={bucket.bucketId} className={styles.listItem}>
                  <strong>{bucket.bucketId}</strong> — {bucket.memberCount} members, {fmtPct(bucket.planCoveragePct)} plan coverage, {fmtConfidence(bucket.averageForecastConfidence)} avg confidence
                </li>
              ))}
            </ul>
          </div>
        </>
      )}
    </section>
  );
};
