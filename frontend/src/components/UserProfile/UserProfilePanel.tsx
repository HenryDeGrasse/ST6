/**
 * UserProfilePanel – Collapsible user model profile panel (Phase 1).
 *
 * Displays the current user's computed model snapshot: performance metrics,
 * behavioural preferences, and multi-dimension trend badges.
 *
 * Gated by the `userProfile` feature flag — returns null when the flag is
 * disabled so the parent never needs to check.
 *
 * Follows the MyTrendsPanel pattern exactly.
 */
import React, { useState } from "react";
import type { UserProfileData } from "../../hooks/useUserProfile.js";
import { useFeatureFlags } from "../../context/FeatureFlagContext.js";
import styles from "./UserProfilePanel.module.css";

// ─── Props ─────────────────────────────────────────────────────────────────

export interface UserProfilePanelProps {
  /** Fetched profile data, or null when not yet loaded. */
  profile: UserProfileData | null;
  /** True while the request is in-flight. */
  loading: boolean;
  /** Human-readable error message, or null. */
  error: string | null;
}

// ─── Helpers ───────────────────────────────────────────────────────────────

/** Format a fractional rate (0–1) as a percentage string. */
function fmtPct(value: number): string {
  return `${Math.round(value * 100)}%`;
}

// ─── Trend badge style map ──────────────────────────────────────────────────

const TREND_BADGE_CLASS: Record<string, string> = {
  IMPROVING: styles.badgeImproving,
  STABLE: styles.badgeStable,
  WORSENING: styles.badgeWorsening,
};

// ─── Component ─────────────────────────────────────────────────────────────

/**
 * Collapsible panel showing user model profile metrics and trends.
 *
 * Gated by the `userProfile` feature flag.  Appears alongside the trends
 * panel on the WeeklyPlanPage.
 */
export const UserProfilePanel: React.FC<UserProfilePanelProps> = ({ profile, loading, error }) => {
  const flags = useFeatureFlags();
  const [expanded, setExpanded] = useState(false);

  if (!flags.userProfile) {
    return null;
  }

  const hasData = profile !== null && profile.weeksAnalyzed > 0;

  const perf = profile?.performanceProfile ?? null;
  const prefs = profile?.preferences ?? null;
  const trends = profile?.trends ?? null;

  return (
    <div data-testid="user-profile-panel" className={styles.panel}>
      {/* ── Header row ── */}
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <span className={styles.profileIcon} aria-hidden="true">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              width={14}
              height={14}
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <circle cx="12" cy="8" r="4" />
              <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" />
            </svg>
          </span>
          <span className={styles.title}>My Profile</span>
        </div>
        <button
          type="button"
          data-testid="user-profile-toggle"
          onClick={() => setExpanded((prev) => !prev)}
          className={styles.toggleButton}
          aria-expanded={expanded}
        >
          {expanded ? "Hide" : "Show"}
        </button>
      </div>

      {/* ── Collapsed summary line ── */}
      {!expanded && hasData && perf && (
        <div className={styles.summary}>
          {profile?.weeksAnalyzed} weeks analyzed · {fmtPct(perf.completionReliability)} completion
          reliability
        </div>
      )}

      {/* ── Expanded content ── */}
      {expanded && (
        <div data-testid="user-profile-content" className={styles.content}>
          {loading && (
            <div data-testid="user-profile-loading" className={styles.loading}>
              Loading profile…
            </div>
          )}

          {!loading && error && (
            <div data-testid="user-profile-error" className={styles.errorMsg}>
              {error}
            </div>
          )}

          {!loading && !error && !hasData && (
            <div data-testid="user-profile-empty" className={styles.empty}>
              Not enough data yet
            </div>
          )}

          {!loading && !error && hasData && perf && (
            <>
              {/* ── Core metric grid ── */}
              <div data-testid="user-profile-metrics" className={styles.metricsGrid}>
                <div className={styles.metricCard} data-testid="metric-estimation-accuracy">
                  <div className={styles.metricLabel}>Estimation Accuracy</div>
                  <div className={styles.metricValue}>{fmtPct(perf.estimationAccuracy)}</div>
                </div>

                <div className={styles.metricCard} data-testid="metric-completion-reliability">
                  <div className={styles.metricLabel}>Completion Reliability</div>
                  <div className={styles.metricValue}>{fmtPct(perf.completionReliability)}</div>
                  <div className={styles.metricSub}>{profile?.weeksAnalyzed} weeks analyzed</div>
                </div>

                <div className={styles.metricCard} data-testid="metric-avg-commits">
                  <div className={styles.metricLabel}>Avg Commits / Week</div>
                  <div className={styles.metricValue}>{perf.avgCommitsPerWeek.toFixed(1)}</div>
                </div>
              </div>

              {/* ── Category completion rates ── */}
              {Object.keys(perf.categoryCompletionRates).length > 0 && (
                <div
                  data-testid="user-profile-category-rates"
                  className={styles.ratesSection}
                >
                  <div className={styles.sectionLabel}>Category Completion</div>
                  <div className={styles.barList}>
                    {Object.entries(perf.categoryCompletionRates).map(([cat, rate]) => (
                      <div key={cat} className={styles.barItem}>
                        <span className={styles.barLabel}>{cat}</span>
                        <div className={styles.barTrack}>
                          <div
                            className={styles.barFill}
                            style={{ width: `${Math.round(rate * 100)}%` }}
                          />
                        </div>
                        <span className={styles.barValue}>{fmtPct(rate)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* ── Priority completion rates ── */}
              {Object.keys(perf.priorityCompletionRates).length > 0 && (
                <div
                  data-testid="user-profile-priority-rates"
                  className={styles.ratesSection}
                >
                  <div className={styles.sectionLabel}>Priority Completion</div>
                  <div className={styles.barList}>
                    {Object.entries(perf.priorityCompletionRates).map(([priority, rate]) => (
                      <div key={priority} className={styles.barItem}>
                        <span className={styles.barLabel}>{priority}</span>
                        <div className={styles.barTrack}>
                          <div
                            className={styles.barFill}
                            style={{ width: `${Math.round(rate * 100)}%` }}
                          />
                        </div>
                        <span className={styles.barValue}>{fmtPct(rate)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* ── Preferred update days ── */}
              {prefs && prefs.preferredUpdateDays.length > 0 && (
                <div
                  data-testid="user-profile-update-days"
                  className={styles.daysSection}
                >
                  <div className={styles.sectionLabel}>Preferred Update Days</div>
                  <div className={styles.chipRow}>
                    {prefs.preferredUpdateDays.map((day) => (
                      <span key={day} className={styles.dayChip}>
                        {day}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* ── Trend badges ── */}
              {trends && (
                <div
                  data-testid="user-profile-trends"
                  className={styles.trendsSection}
                >
                  <div className={styles.sectionLabel}>Trends</div>
                  <div className={styles.trendBadgeRow}>
                    <span
                      className={`${styles.trendBadge} ${TREND_BADGE_CLASS[trends.strategicAlignmentTrend] ?? ""}`}
                      data-testid="trend-strategic-alignment"
                    >
                      Alignment · {trends.strategicAlignmentTrend}
                    </span>
                    <span
                      className={`${styles.trendBadge} ${TREND_BADGE_CLASS[trends.completionTrend] ?? ""}`}
                      data-testid="trend-completion"
                    >
                      Completion · {trends.completionTrend}
                    </span>
                    <span
                      className={`${styles.trendBadge} ${TREND_BADGE_CLASS[trends.carryForwardTrend] ?? ""}`}
                      data-testid="trend-carry-forward"
                    >
                      Carry-Forward · {trends.carryForwardTrend}
                    </span>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
};
