import React, { useState } from "react";
import type { TrendsResponse, TrendInsight } from "@weekly-commitments/contracts";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import styles from "./MyTrendsPanel.module.css";

export interface MyTrendsPanelProps {
  /** Trend data to display, or null when not yet fetched. */
  trends: TrendsResponse | null;
  /** True while the request is in-flight. */
  loading: boolean;
  /** Human-readable error message, or null. */
  error: string | null;
}

const SEVERITY_BADGE_CLASS: Record<TrendInsight["severity"], string> = {
  INFO: styles.badgeInfo,
  WARNING: styles.badgeWarning,
  POSITIVE: styles.badgePositive,
};

const SEVERITY_LABEL: Record<TrendInsight["severity"], string> = {
  INFO: "Info",
  WARNING: "Note",
  POSITIVE: "Great",
};

/** Format a fractional rate (0–1) as a percentage string. */
function fmtPct(value: number): string {
  return `${Math.round(value * 100)}%`;
}

/**
 * Collapsible, non-intrusive panel showing cross-week IC trend metrics
 * and structured insight badges.
 *
 * Gated by the `icTrends` feature flag.  Appears above the commit list
 * on the WeeklyPlanPage so ICs can review their rolling-window patterns
 * without it dominating the page.
 */
export const MyTrendsPanel: React.FC<MyTrendsPanelProps> = ({ trends, loading, error }) => {
  const flags = useFeatureFlags();
  const [expanded, setExpanded] = useState(false);

  if (!flags.icTrends) {
    return null;
  }

  const hasData = trends !== null;

  return (
    <div data-testid="my-trends-panel" className={styles.panel}>
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          {/* Simple bar-chart icon inline SVG — no StatusIcon equivalent */}
          <span className={styles.chartIcon} aria-hidden="true">
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
              <line x1="18" y1="20" x2="18" y2="10" />
              <line x1="12" y1="20" x2="12" y2="4" />
              <line x1="6" y1="20" x2="6" y2="14" />
              <line x1="2" y1="20" x2="22" y2="20" />
            </svg>
          </span>
          <span className={styles.title}>My Trends</span>
        </div>
        <button
          type="button"
          data-testid="my-trends-toggle"
          onClick={() => setExpanded((prev) => !prev)}
          className={styles.toggleButton}
          aria-expanded={expanded}
        >
          {expanded ? "Hide" : "Show"}
        </button>
      </div>

      {expanded && (
        <div data-testid="my-trends-content" className={styles.content}>
          {loading && (
            <div data-testid="my-trends-loading" className={styles.loading}>
              Loading trend data…
            </div>
          )}

          {!loading && error && (
            <div data-testid="my-trends-error" className={styles.errorMsg}>
              {error}
            </div>
          )}

          {!loading && !error && !hasData && (
            <div data-testid="my-trends-empty" className={styles.empty}>
              No trend data available yet. Trends appear once you have two or more completed weeks.
            </div>
          )}

          {!loading && !error && hasData && (
            <>
              <div data-testid="my-trends-metrics" className={styles.metricsGrid}>
                <div className={styles.metricCard} data-testid="metric-strategic-alignment">
                  <div className={styles.metricLabel}>Strategic Alignment</div>
                  <div className={styles.metricValue}>{fmtPct(trends.strategicAlignmentRate)}</div>
                  <div className={styles.metricSub}>vs {fmtPct(trends.teamStrategicAlignmentRate)} team avg</div>
                </div>

                <div className={styles.metricCard} data-testid="metric-completion-accuracy">
                  <div className={styles.metricLabel}>Completion Accuracy</div>
                  <div className={styles.metricValue}>{fmtPct(trends.completionAccuracy)}</div>
                  <div className={styles.metricSub}>{trends.weeksAnalyzed} weeks analyzed</div>
                </div>

                <div className={styles.metricCard} data-testid="metric-avg-confidence">
                  <div className={styles.metricLabel}>Avg Confidence</div>
                  <div className={styles.metricValue}>{fmtPct(trends.avgConfidence)}</div>
                  {trends.confidenceAccuracyGap > 0.05 && (
                    <div className={styles.metricSub} data-testid="metric-gap-hint">
                      +{fmtPct(trends.confidenceAccuracyGap)} gap
                    </div>
                  )}
                </div>

                <div className={styles.metricCard} data-testid="metric-carry-forward">
                  <div className={styles.metricLabel}>Carry-Forward</div>
                  <div className={styles.metricValue}>{trends.avgCarryForwardPerWeek.toFixed(1)}/wk</div>
                  {trends.carryForwardStreak > 0 && (
                    <div className={styles.metricSub} data-testid="metric-streak">
                      {trends.carryForwardStreak}-week streak
                    </div>
                  )}
                </div>
              </div>

              {trends.insights.length > 0 && (
                <ul data-testid="my-trends-insights" className={styles.insightList}>
                  {trends.insights.map((insight, idx) => (
                    <li
                      key={`${insight.type}-${idx}`}
                      data-testid={`trend-insight-${idx}`}
                      className={styles.insightItem}
                    >
                      <span
                        className={`${styles.insightBadge} ${SEVERITY_BADGE_CLASS[insight.severity]}`}
                        data-testid={`trend-insight-badge-${idx}`}
                      >
                        {SEVERITY_LABEL[insight.severity]}
                      </span>
                      <span className={styles.insightMessage}>{insight.message}</span>
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
};
