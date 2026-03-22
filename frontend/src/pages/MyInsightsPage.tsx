import React, { useEffect, useMemo } from "react";
import type { WeekTrendPoint } from "@weekly-commitments/contracts";
import { useUserProfile } from "../hooks/useUserProfile.js";
import { useTrends } from "../hooks/useTrends.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import {
  Sparkline,
  HBar,
  CategoryDonut,
  DONUT_COLORS,
  fmtPct,
  clamp,
} from "../components/charts/index.js";
import styles from "./MyInsightsPage.module.css";

// ─── Hours Comparison ──────────────────────────────────────────────────────

interface HoursBarProps {
  weekPoints: WeekTrendPoint[];
}

const HoursComparison: React.FC<HoursBarProps> = ({ weekPoints }) => {
  const weeks = weekPoints.filter(w => w.estimatedHours != null && w.actualHours != null);
  if (weeks.length === 0) return null;

  const maxH = Math.max(...weeks.flatMap(w => [w.estimatedHours ?? 0, w.actualHours ?? 0]), 1);

  return (
    <div className={styles.hoursChart}>
      <div className={styles.hoursLegendRow}>
        <span className={styles.hoursLegendItem}><span className={styles.hoursLegendBar} style={{ backgroundColor: "var(--wc-color-accent, #C9A962)" }} />Estimated</span>
        <span className={styles.hoursLegendItem}><span className={styles.hoursLegendBar} style={{ backgroundColor: "#7A8C6E" }} />Actual</span>
      </div>
      <div className={styles.hoursBarGroup}>
        {weeks.map((w, i) => {
          const estPct = clamp(((w.estimatedHours ?? 0) / maxH) * 100, 0, 100);
          const actPct = clamp(((w.actualHours ?? 0) / maxH) * 100, 0, 100);
          const weekLabel = new Date(w.weekStart + "T00:00:00").toLocaleDateString("en-US", { month: "short", day: "numeric" });
          return (
            <div key={i} className={styles.hoursPair}>
              <div className={styles.hoursBarWrap}>
                <div className={styles.hoursBarEst} style={{ height: `${estPct}%` }} title={`Est: ${w.estimatedHours}h`} />
                <div className={styles.hoursBarAct} style={{ height: `${actPct}%` }} title={`Act: ${w.actualHours}h`} />
              </div>
              <span className={styles.hoursWeekLabel}>{weekLabel}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
};

// ─── Stat Card ─────────────────────────────────────────────────────────────

interface StatCardProps {
  label: string;
  value: string;
  sub?: string;
  sparkData?: number[];
  sparkColor?: string;
  testId?: string;
}

const StatCard: React.FC<StatCardProps> = ({ label, value, sub, sparkData, sparkColor, testId }) => (
  <div className={styles.statCard} data-testid={testId}>
    <div className={styles.statTop}>
      <span className={styles.statLabel}>{label}</span>
      <span className={styles.statValue}>{value}</span>
      {sub && <span className={styles.statSub}>{sub}</span>}
    </div>
    {sparkData && sparkData.length >= 2 && (
      <Sparkline data={sparkData} label={label} color={sparkColor} />
    )}
  </div>
);

// ─── Trend Badge ───────────────────────────────────────────────────────────

const TREND_CLASS: Record<string, string> = {
  IMPROVING: styles.trendImproving,
  STABLE: styles.trendStable,
  WORSENING: styles.trendWorsening,
};

const SEVERITY_CLASS: Record<string, string> = {
  POSITIVE: styles.insightPositive,
  INFO: styles.insightInfo,
  WARNING: styles.insightWarning,
};

const DAY_LABELS: Record<"MON" | "TUE" | "WED" | "THU" | "FRI", string> = {
  MON: "MONDAY",
  TUE: "TUESDAY",
  WED: "WEDNESDAY",
  THU: "THURSDAY",
  FRI: "FRIDAY",
};

// ─── Page Component ────────────────────────────────────────────────────────

export const MyInsightsPage: React.FC = () => {
  const flags = useFeatureFlags();
  const { trends, loading: trendsLoading, error: trendsError, fetchTrends } = useTrends();
  const { profile, loading: profileLoading, error: profileError, fetchProfile } = useUserProfile();

  useEffect(() => {
    if (flags.icTrends) void fetchTrends();
  }, [flags.icTrends, fetchTrends]);

  useEffect(() => {
    if (flags.userProfile) void fetchProfile();
  }, [flags.userProfile, fetchProfile]);

  const loading = trendsLoading || profileLoading;
  const error = trendsError || profileError;

  // Memoize sparkline data from weekPoints
  const sparks = useMemo(() => {
    if (!trends?.weekPoints?.length) return null;
    const pts = trends.weekPoints;
    return {
      completion: pts.map(p => p.completionRate),
      strategic: pts.map(p => p.strategicCommits / Math.max(p.totalCommits, 1)),
      commits: pts.map(p => p.totalCommits),
      carryFwd: pts.map(p => p.carryForwardCommits),
      confidence: pts.map(p => p.avgConfidence),
    };
  }, [trends]);

  const hasProfile = profile !== null && profile.weeksAnalyzed > 0;
  const perf = profile?.performanceProfile ?? null;
  const prefs = profile?.preferences ?? null;
  const userTrends = profile?.trends ?? null;

  return (
    <div className={styles.page} data-testid="my-insights-page">
      <span aria-hidden="true" className="wc-volume-label">Volume II</span>
      <h2 className={styles.heading}>My Insights</h2>
      <div aria-hidden="true" className="wc-ornate-divider" role="separator" />

      {loading && (
        <div className={styles.loadingState}>Loading your insights...</div>
      )}

      {!loading && error && (
        <div className={styles.errorState}>{error}</div>
      )}

      {!loading && !error && !trends && !hasProfile && (
        <div className={styles.emptyState}>
          Not enough data yet. Complete a few weeks and your insights will appear here.
        </div>
      )}

      {!loading && !error && (trends || hasProfile) && (
        <>
          {/* ── Section 1: Key Metrics with Sparklines ── */}
          {trends && (
            <section className={styles.section} data-testid="key-metrics-section">
              <h3 className={styles.sectionTitle}>Performance at a Glance</h3>
              <p className={styles.sectionSub}>
                {trends.weeksAnalyzed}-week rolling window
                {trends.windowStart && trends.windowEnd && (
                  <> &middot; {trends.windowStart} to {trends.windowEnd}</>
                )}
              </p>

              <div className={styles.statGrid}>
                <StatCard
                  label="Completion Rate"
                  value={fmtPct(trends.completionAccuracy)}
                  sub={`${trends.weeksAnalyzed} weeks`}
                  sparkData={sparks?.completion}
                  sparkColor="#7A8C6E"
                  testId="stat-completion"
                />
                <StatCard
                  label="Strategic Alignment"
                  value={fmtPct(trends.strategicAlignmentRate)}
                  sub={`vs ${fmtPct(trends.teamStrategicAlignmentRate)} team`}
                  sparkData={sparks?.strategic}
                  sparkColor="#C9A962"
                  testId="stat-alignment"
                />
                <StatCard
                  label="Avg Confidence"
                  value={fmtPct(trends.avgConfidence)}
                  sub={trends.confidenceAccuracyGap > 0.05 ? `+${fmtPct(trends.confidenceAccuracyGap)} gap` : undefined}
                  sparkData={sparks?.confidence}
                  sparkColor="#8B6E8C"
                  testId="stat-confidence"
                />
                <StatCard
                  label="Commits / Week"
                  value={sparks?.commits.length ? (sparks.commits.reduce((a, b) => a + b, 0) / sparks.commits.length).toFixed(1) : "—"}
                  sparkData={sparks?.commits}
                  sparkColor="#6E7A8C"
                  testId="stat-commits"
                />
                <StatCard
                  label="Carry-Forward"
                  value={`${trends.avgCarryForwardPerWeek.toFixed(1)}/wk`}
                  sub={trends.carryForwardStreak > 0 ? `${trends.carryForwardStreak}-week streak` : undefined}
                  sparkData={sparks?.carryFwd}
                  sparkColor="#8C6E6E"
                  testId="stat-carry-forward"
                />
                {trends.avgEstimatedHoursPerWeek != null && trends.avgActualHoursPerWeek != null && (
                  <StatCard
                    label="Avg Hours / Week"
                    value={`${trends.avgActualHoursPerWeek.toFixed(1)}h`}
                    sub={`of ${trends.avgEstimatedHoursPerWeek.toFixed(1)}h estimated`}
                    testId="stat-hours"
                  />
                )}
              </div>
            </section>
          )}

          {/* ── Section 2: Weekly Hours Comparison ── */}
          {trends?.weekPoints && trends.weekPoints.some(w => w.estimatedHours != null) && (
            <section className={styles.section} data-testid="hours-section">
              <h3 className={styles.sectionTitle}>Estimated vs Actual Hours</h3>
              <HoursComparison weekPoints={trends.weekPoints} />
            </section>
          )}

          {/* ── Section 3: Distribution Charts ── */}
          {trends && (
            <section className={styles.section} data-testid="distribution-section">
              <div className={styles.distGrid}>
                {/* Category distribution */}
                <div className={styles.distCard}>
                  <h3 className={styles.sectionTitle}>Category Mix</h3>
                  <CategoryDonut data={trends.categoryDistribution} />
                </div>

                {/* Priority distribution */}
                <div className={styles.distCard}>
                  <h3 className={styles.sectionTitle}>Priority Distribution</h3>
                  <div className={styles.priorityBars}>
                    {Object.entries(trends.priorityDistribution)
                      .sort(([a], [b]) => {
                        const order = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
                        return order.indexOf(a) - order.indexOf(b);
                      })
                      .map(([p, v]) => (
                        <HBar
                          key={p}
                          label={p.charAt(0) + p.slice(1).toLowerCase()}
                          value={v}
                          color={
                            p === "KING" ? "#C9A962" :
                            p === "QUEEN" ? "#B8A050" :
                            p === "ROOK" ? "#7A8C6E" :
                            p === "BISHOP" ? "#6E7A8C" :
                            p === "KNIGHT" ? "#8B6E8C" :
                            "#9C8B7A"
                          }
                        />
                      ))}
                  </div>
                </div>
              </div>
            </section>
          )}

          {/* ── Section 4: Profile Intelligence ── */}
          {hasProfile && perf && (
            <section className={styles.section} data-testid="profile-section">
              <h3 className={styles.sectionTitle}>Performance Profile</h3>
              <p className={styles.sectionSub}>
                Based on {profile?.weeksAnalyzed ?? 0} weeks of history
              </p>

              <div className={styles.profileGrid}>
                {/* Completion by priority */}
                <div className={styles.profileCard}>
                  <h4 className={styles.cardTitle}>Completion by Priority</h4>
                  {Object.entries(perf.priorityCompletionRates)
                    .sort(([a], [b]) => {
                      const order = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
                      return order.indexOf(a) - order.indexOf(b);
                    })
                    .map(([p, rate]) => (
                      <HBar key={p} label={p.charAt(0) + p.slice(1).toLowerCase()} value={rate} />
                    ))}
                </div>

                {/* Completion by category */}
                <div className={styles.profileCard}>
                  <h4 className={styles.cardTitle}>Completion by Category</h4>
                  {Object.entries(perf.categoryCompletionRates)
                    .sort(([, a], [, b]) => b - a)
                    .map(([cat, rate]) => (
                      <HBar
                        key={cat}
                        label={cat.charAt(0) + cat.slice(1).toLowerCase()}
                        value={rate}
                        color={DONUT_COLORS[cat] ?? "#9C8B7A"}
                      />
                    ))}
                </div>

                {/* Core metrics */}
                <div className={styles.profileCard}>
                  <h4 className={styles.cardTitle}>Key Metrics</h4>
                  <div className={styles.profileMetricRow}>
                    <span>Estimation Accuracy</span>
                    <strong>{fmtPct(perf.estimationAccuracy)}</strong>
                  </div>
                  <div className={styles.profileMetricRow}>
                    <span>Completion Reliability</span>
                    <strong>{fmtPct(perf.completionReliability)}</strong>
                  </div>
                  <div className={styles.profileMetricRow}>
                    <span>Avg Commits / Week</span>
                    <strong>{perf.avgCommitsPerWeek.toFixed(1)}</strong>
                  </div>
                  <div className={styles.profileMetricRow}>
                    <span>Carry-Forward / Week</span>
                    <strong>{perf.avgCarryForwardPerWeek.toFixed(1)}</strong>
                  </div>
                </div>
              </div>
            </section>
          )}

          {/* ── Section 5: Preferences & Habits ── */}
          {hasProfile && prefs && (
            <section className={styles.section} data-testid="habits-section">
              <h3 className={styles.sectionTitle}>Work Habits</h3>
              <div className={styles.habitsGrid}>
                {prefs.typicalPriorityPattern && (
                  <div className={styles.habitCard}>
                    <span className={styles.habitLabel}>Typical Pattern</span>
                    <span className={styles.habitValue}>{prefs.typicalPriorityPattern}</span>
                  </div>
                )}
                <div className={styles.habitCard}>
                  <span className={styles.habitLabel}>Check-ins / Week</span>
                  <span className={styles.habitValue}>{prefs.avgCheckInsPerWeek.toFixed(1)}</span>
                </div>
                {prefs.preferredUpdateDays.length > 0 && (
                  <div className={styles.habitCard}>
                    <span className={styles.habitLabel}>Update Days</span>
                    <div className={styles.dayChips}>
                      {(["MON", "TUE", "WED", "THU", "FRI"] as const).map(d => {
                        const fullDay = DAY_LABELS[d];
                        const active = prefs.preferredUpdateDays.includes(fullDay);
                        return (
                          <span
                            key={d}
                            className={`${styles.dayChip} ${active ? styles.dayActive : ""}`}
                          >
                            {d}
                          </span>
                        );
                      })}
                    </div>
                  </div>
                )}
                {prefs.recurringCommitTitles.length > 0 && (
                  <div className={styles.habitCard}>
                    <span className={styles.habitLabel}>Recurring Work</span>
                    <ul className={styles.recurringList}>
                      {prefs.recurringCommitTitles.map((t, i) => (
                        <li key={i}>{t}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            </section>
          )}

          {/* ── Section 6: Trend Badges + Insights ── */}
          <section className={styles.section} data-testid="signals-section">
            <h3 className={styles.sectionTitle}>Signals</h3>
            <div className={styles.signalsRow}>
              {userTrends && (
                <div className={styles.trendBadges}>
                  <span className={`${styles.trendBadge} ${TREND_CLASS[userTrends.strategicAlignmentTrend] ?? ""}`}>
                    Alignment &middot; {userTrends.strategicAlignmentTrend.toLowerCase()}
                  </span>
                  <span className={`${styles.trendBadge} ${TREND_CLASS[userTrends.completionTrend] ?? ""}`}>
                    Completion &middot; {userTrends.completionTrend.toLowerCase()}
                  </span>
                  <span className={`${styles.trendBadge} ${TREND_CLASS[userTrends.carryForwardTrend] ?? ""}`}>
                    Carry-forward &middot; {userTrends.carryForwardTrend.toLowerCase()}
                  </span>
                </div>
              )}

              {trends?.insights && trends.insights.length > 0 && (
                <ul className={styles.insightList}>
                  {trends.insights.map((ins, i) => (
                    <li key={i} className={`${styles.insightItem} ${SEVERITY_CLASS[ins.severity] ?? ""}`}>
                      <span className={styles.insightSeverity}>
                        {ins.severity === "POSITIVE" ? "Great" : ins.severity === "WARNING" ? "Note" : "Info"}
                      </span>
                      {ins.message}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </section>
        </>
      )}
    </div>
  );
};
