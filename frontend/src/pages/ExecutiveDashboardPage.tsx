import React, { useEffect, useState } from "react";
import { GlassPanel } from "../components/GlassPanel.js";
import { WeekSelector } from "../components/WeekSelector.js";
import { ExecutiveBriefing } from "../components/Phase5/index.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import { useExecutiveDashboard } from "../hooks/useExecutiveDashboard.js";
import { getWeekStart } from "../utils/week.js";
import {
  StackedBar,
  HBar,
  ProgressRing,
  fmtPct,
} from "../components/charts/index.js";
import styles from "./ExecutiveDashboardPage.module.css";

// ─── Helpers ───────────────────────────────────────────────────────────────

function fmtConfidence(value: number | null): string {
  return value === null ? "—" : `${Math.round(value * 100)}%`;
}

// ─── Stat Card ─────────────────────────────────────────────────────────────

interface StatCardProps {
  label: string;
  value: string | React.ReactNode;
  sub?: string;
  accent?: string;
  testId?: string;
}

const StatCard: React.FC<StatCardProps> = ({ label, value, sub, accent, testId }) => (
  <div className={styles.statCard} data-testid={testId} style={accent ? { borderTopColor: accent } : undefined}>
    <span className={styles.statLabel}>{label}</span>
    <span className={styles.statValue}>{value}</span>
    {sub && <span className={styles.statSub}>{sub}</span>}
  </div>
);

// ─── Page Component ────────────────────────────────────────────────────────

/**
 * Executive strategic-health page for Phase 5 rollup visibility.
 */
export const ExecutiveDashboardPage: React.FC = () => {
  const flags = useFeatureFlags();
  const [selectedWeek, setSelectedWeek] = useState(() => getWeekStart());
  const { dashboard, dashboardStatus, errorDashboard, fetchDashboard } = useExecutiveDashboard();

  useEffect(() => {
    if (flags.executiveDashboard) {
      void fetchDashboard(selectedWeek);
    }
  }, [flags.executiveDashboard, fetchDashboard, selectedWeek]);

  return (
    <div data-testid="executive-dashboard-page" className={styles.page}>
      <GlassPanel className={styles.contentPanel}>
        <div className={styles.header}>
          <div>
            <span className="wc-volume-label" aria-hidden="true">
              Volume V
            </span>
            <h2 className={styles.heading}>Executive Dashboard</h2>
            <p className={styles.description}>
              Strategic-health forecasting, planning coverage, and the executive AI briefing for the selected week.
            </p>
          </div>
          <div aria-hidden="true" className="wc-ornate-divider" role="separator" />
        </div>

        <WeekSelector selectedWeek={selectedWeek} onWeekChange={setSelectedWeek} />

        {!flags.executiveDashboard ? (
          <div data-testid="executive-dashboard-flag-disabled" className={styles.empty}>
            Executive dashboard is disabled for this workspace.
          </div>
        ) : (
          <div className={styles.sections}>

            {/* ── Loading / Error states ── */}
            {dashboardStatus === "loading" && (
              <div className={styles.loadingState}>Loading executive dashboard…</div>
            )}

            {dashboardStatus === "unavailable" && !dashboard && !errorDashboard && (
              <div className={styles.empty}>Executive dashboard data is unavailable for the selected week.</div>
            )}

            {errorDashboard && !dashboard && (
              <div className={styles.errorState}>{errorDashboard}</div>
            )}

            {/* ── A. Summary Stat Cards ── */}
            {dashboard && dashboardStatus === "ok" && (
              <section className={styles.section} data-testid="executive-summary-stats">
                <h3 className={styles.sectionTitle}>Strategic Health at a Glance</h3>
                <div className={styles.statGrid}>
                  <StatCard
                    label="Total Forecasts"
                    value={String(dashboard.summary.totalForecasts)}
                    testId="exec-stat-total"
                  />
                  <StatCard
                    label="On Track"
                    value={String(dashboard.summary.onTrackForecasts)}
                    accent="#9AB88C"
                    testId="exec-stat-on-track"
                  />
                  <StatCard
                    label="Needs Attention"
                    value={String(dashboard.summary.needsAttentionForecasts)}
                    accent="#C9A962"
                    testId="exec-stat-attention"
                  />
                  <StatCard
                    label="At Risk"
                    value={String(dashboard.summary.offTrackForecasts)}
                    accent="#C47A84"
                    testId="exec-stat-at-risk"
                  />
                  <div className={styles.statCard} data-testid="exec-stat-coverage">
                    <span className={styles.statLabel}>Planning Coverage</span>
                    <div className={styles.statRingWrap}>
                      <ProgressRing
                        value={(dashboard.summary.planningCoveragePct ?? 0) / 100}
                        size={56}
                        label="Planning Coverage"
                      />
                    </div>
                  </div>
                  <StatCard
                    label="Avg Confidence"
                    value={fmtConfidence(dashboard.summary.averageForecastConfidence)}
                    testId="exec-stat-confidence"
                  />
                </div>
              </section>
            )}

            {/* ── B. Strategic Capacity Utilization ── */}
            {dashboard && dashboardStatus === "ok" &&
              (dashboard.summary.strategicCapacityUtilizationPct != null ||
                dashboard.summary.nonStrategicCapacityUtilizationPct != null) && (
              <section className={styles.section} data-testid="executive-capacity-bar">
                <h3 className={styles.sectionTitle}>Strategic Capacity Utilization</h3>
                <p className={styles.sectionSub}>
                  Proportion of total capacity hours allocated to strategic vs. non-strategic work
                </p>
                <StackedBar
                  segments={[
                    {
                      label: "Strategic",
                      value: dashboard.summary.strategicHours ?? 0,
                      color: "#7A8C6E",
                    },
                    {
                      label: "Non-Strategic",
                      value: dashboard.summary.nonStrategicHours ?? 0,
                      color: "#6E7A8C",
                    },
                  ]}
                  height={24}
                />
                <div className={styles.capacityStats}>
                  <span className={styles.capacityStat}>
                    <span className={styles.capacityStatValue} style={{ color: "#9AB88C" }}>
                      {fmtPct((dashboard.summary.strategicCapacityUtilizationPct ?? 0) / 100)}
                    </span>
                    <span className={styles.capacityStatLabel}>Strategic Utilization</span>
                  </span>
                  <span className={styles.capacityStat}>
                    <span className={styles.capacityStatValue} style={{ color: "#8C9AAB" }}>
                      {fmtPct((dashboard.summary.nonStrategicCapacityUtilizationPct ?? 0) / 100)}
                    </span>
                    <span className={styles.capacityStatLabel}>Non-Strategic Utilization</span>
                  </span>
                </div>
              </section>
            )}

            {/* ── C. Rally Cry Health ── */}
            {dashboard && dashboardStatus === "ok" && dashboard.rallyCryRollups.length > 0 && (
              <section className={styles.section} data-testid="executive-rally-cries">
                <h3 className={styles.sectionTitle}>Rally Cry Health</h3>
                <div className={styles.rallyCryGrid}>
                  {dashboard.rallyCryRollups.map((rollup) => {
                    const total =
                      rollup.onTrackCount +
                      rollup.needsAttentionCount +
                      rollup.offTrackCount +
                      rollup.noDataCount;
                    return (
                      <div key={rollup.rallyCryName} className={styles.rallyCryCard}>
                        <div className={styles.rallyCryHeader}>
                          <span className={styles.rallyCryName}>{rollup.rallyCryName}</span>
                          {rollup.averageForecastConfidence != null && (
                            <span className={styles.rallyCryConfidence}>
                              {fmtConfidence(rollup.averageForecastConfidence)} confidence
                            </span>
                          )}
                        </div>
                        {total > 0 && (
                          <StackedBar
                            height={12}
                            segments={[
                              { label: "On Track", value: rollup.onTrackCount, color: "#9AB88C" },
                              { label: "Needs Attention", value: rollup.needsAttentionCount, color: "#C9A962" },
                              { label: "At Risk", value: rollup.offTrackCount, color: "#C47A84" },
                            ]}
                          />
                        )}
                        {rollup.strategicHours != null && (
                          <div className={styles.rallyCryMeta}>
                            {rollup.strategicHours.toFixed(1)}h strategic
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </section>
            )}

            {/* ── D. Team Comparison ── */}
            {dashboard && dashboardStatus === "ok" && dashboard.teamBuckets.length > 0 && (
              <section className={styles.section} data-testid="executive-team-buckets">
                <h3 className={styles.sectionTitle}>Team Comparison</h3>
                <div className={styles.teamGrid}>
                  {dashboard.teamBuckets.map((bucket) => (
                    <div key={bucket.bucketId} className={styles.teamCard}>
                      <div className={styles.teamCardHeader}>
                        <span className={styles.teamBucketName}>{bucket.bucketId}</span>
                        <span className={styles.teamMemberBadge}>
                          {bucket.memberCount} {bucket.memberCount === 1 ? "member" : "members"}
                        </span>
                      </div>
                      <div className={styles.teamBars}>
                        <HBar
                          label="Plan Coverage"
                          value={(bucket.planCoveragePct ?? 0) / 100}
                          color="#C9A962"
                        />
                        <HBar
                          label="Strategic Utilization"
                          value={(bucket.strategicCapacityUtilizationPct ?? 0) / 100}
                          color="#7A8C6E"
                        />
                      </div>
                      {bucket.averageForecastConfidence != null && (
                        <div className={styles.teamConfidence}>
                          Avg confidence: {fmtConfidence(bucket.averageForecastConfidence)}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </section>
            )}

            {/* ── E. Executive Briefing ── */}
            <div className={styles.section}>
              <ExecutiveBriefing weekStart={selectedWeek} />
            </div>

          </div>
        )}
      </GlassPanel>
    </div>
  );
};
