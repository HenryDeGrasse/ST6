import React, { useEffect } from "react";
import { useEstimationCoaching } from "../../hooks/useCapacity.js";
import { useFeatureFlags } from "../../context/FeatureFlagContext.js";
import type { CategoryInsight, PriorityInsight } from "../../hooks/useCapacity.js";
import styles from "./EstimationCoaching.module.css";

// ─── Props ─────────────────────────────────────────────────────────────────────

export interface EstimationCoachingProps {
  /** The plan ID to fetch coaching data for. */
  planId: string;
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

/** Format a bias ratio (e.g. 1.25) as a numeric multiplier. */
function fmtBiasRatio(bias: number | null): string {
  if (bias === null) return "–";
  return `${bias.toFixed(2)}×`;
}

/** Format a bias ratio (e.g. 1.25) as a human-readable delta label. */
function fmtBiasDelta(bias: number | null): string {
  if (bias === null) return "No historical baseline yet";
  const pct = Math.round((bias - 1) * 100);
  if (pct === 0) return "On target";
  return pct > 0 ? `+${pct}% over` : `${Math.abs(pct)}% under`;
}

/** Format an accuracy ratio (0–∞) as a percentage label. */
function fmtRatio(ratio: number | null): string {
  if (ratio === null) return "–";
  return `${Math.round(ratio * 100)}%`;
}

/** Format a completion rate (0–1) as a percentage. */
function fmtPct(value: number): string {
  return `${Math.round(value * 100)}%`;
}

/** Determine the CSS class for a bias ratio (>1 = over, <1 = under, null = neutral). */
function biasClass(bias: number | null): string {
  if (bias === null) return "";
  if (bias > 1.1) return styles.biasOver;
  if (bias < 0.9) return styles.biasUnder;
  return styles.biasOnTarget;
}

// ─── Component ─────────────────────────────────────────────────────────────────

/**
 * EstimationCoaching
 *
 * Displays post-reconciliation estimation coaching for a given plan.
 * Fetches data internally via the `useEstimationCoaching` hook.
 *
 * - Renders nothing when the `estimationCoaching` feature flag is off.
 * - Renders nothing when no coaching payload is available after loading completes.
 * - Shows loading/error states following existing component patterns.
 *
 * Uses `data-testid="estimation-coaching"` for test queries.
 */
export const EstimationCoaching: React.FC<EstimationCoachingProps> = ({ planId }) => {
  const flags = useFeatureFlags();
  const { coaching, loading, error, fetchCoaching } = useEstimationCoaching();

  // Fetch coaching data on mount (or when planId changes) if the flag is enabled.
  useEffect(() => {
    if (flags.estimationCoaching && planId) {
      void fetchCoaching(planId);
    }
  }, [flags.estimationCoaching, planId, fetchCoaching]);

  // Gate on the feature flag first.
  if (!flags.estimationCoaching) {
    return null;
  }

  // Loading state.
  if (loading) {
    return (
      <div data-testid="estimation-coaching" className={styles.card}>
        <div className={styles.header}>
          <span className={styles.icon} aria-hidden="true"></span>
          <h3 className={styles.title}>Estimation Coaching</h3>
        </div>
        <div data-testid="estimation-coaching-loading" className={styles.loading}>
          Loading coaching insights…
        </div>
      </div>
    );
  }

  // Error state.
  if (error) {
    return (
      <div data-testid="estimation-coaching" className={styles.card}>
        <div className={styles.header}>
          <span className={styles.icon} aria-hidden="true"></span>
          <h3 className={styles.title}>Estimation Coaching</h3>
        </div>
        <div data-testid="estimation-coaching-error" className={styles.errorMsg}>
          {error}
        </div>
      </div>
    );
  }

  // Render nothing when there is no data yet.
  if (!coaching) {
    return null;
  }

  const categoryInsights: CategoryInsight[] = coaching.categoryInsights ?? [];
  const priorityInsights: PriorityInsight[] = coaching.priorityInsights ?? [];

  return (
    <div data-testid="estimation-coaching" className={styles.card}>
      {/* ── Header ── */}
      <div className={styles.header}>
        <span className={styles.icon} aria-hidden="true"></span>
        <h3 className={styles.title}>Estimation Coaching</h3>
        <span
          data-testid="estimation-coaching-confidence"
          className={`${styles.confidenceBadge} ${styles[`confidence${coaching.confidenceLevel}`]}`}
        >
          {coaching.confidenceLevel}
        </span>
      </div>

      {/* ── This week: estimated vs actual ── */}
      <section
        data-testid="estimation-coaching-this-week"
        className={styles.section}
        aria-labelledby="ec-this-week-label"
      >
        <h4 id="ec-this-week-label" className={styles.sectionTitle}>
          This Week
        </h4>
        <div className={styles.metricsRow}>
          <div className={styles.metricCard} data-testid="ec-metric-estimated">
            <div className={styles.metricLabel}>Estimated</div>
            <div className={styles.metricValue}>{coaching.thisWeekEstimated}h</div>
          </div>
          <div className={styles.metricCard} data-testid="ec-metric-actual">
            <div className={styles.metricLabel}>Actual</div>
            <div className={styles.metricValue}>{coaching.thisWeekActual}h</div>
          </div>
          <div className={styles.metricCard} data-testid="ec-metric-accuracy">
            <div className={styles.metricLabel}>Accuracy</div>
            <div className={styles.metricValue}>{fmtRatio(coaching.accuracyRatio)}</div>
          </div>
          <div
            className={`${styles.metricCard} ${biasClass(coaching.overallBias)}`}
            data-testid="ec-metric-overall-bias"
          >
            <div className={styles.metricLabel}>Overall Bias</div>
            <div className={styles.metricValue}>{fmtBiasRatio(coaching.overallBias)}</div>
            <div className={styles.metricSub}>{fmtBiasDelta(coaching.overallBias)}</div>
          </div>
        </div>
      </section>

      {/* ── Per-category insights ── */}
      {categoryInsights.length > 0 && (
        <section
          data-testid="estimation-coaching-categories"
          className={styles.section}
          aria-labelledby="ec-category-label"
        >
          <h4 id="ec-category-label" className={styles.sectionTitle}>
            By Category
          </h4>
          <ul className={styles.insightList}>
            {categoryInsights.map((insight, idx) => (
              <li
                key={`${insight.category}-${idx}`}
                data-testid={`ec-category-insight-${idx}`}
                className={styles.insightItem}
              >
                <div className={styles.insightRow}>
                  <span className={styles.insightCategory}>{insight.category}</span>
                  <span
                    className={`${styles.insightBias} ${biasClass(insight.bias)}`}
                    data-testid={`ec-category-bias-${idx}`}
                  >
                    {`${fmtBiasRatio(insight.bias)} · ${fmtBiasDelta(insight.bias)}`}
                  </span>
                </div>
                {insight.tip && (
                  <p className={styles.insightTip} data-testid={`ec-category-tip-${idx}`}>
                    {insight.tip}
                  </p>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* ── Per-priority completion rates ── */}
      {priorityInsights.length > 0 && (
        <section
          data-testid="estimation-coaching-priorities"
          className={styles.section}
          aria-labelledby="ec-priority-label"
        >
          <h4 id="ec-priority-label" className={styles.sectionTitle}>
            Completion by Priority
          </h4>
          <ul className={styles.priorityList}>
            {priorityInsights.map((insight, idx) => (
              <li
                key={`${insight.priority}-${idx}`}
                data-testid={`ec-priority-insight-${idx}`}
                className={styles.priorityItem}
              >
                <span className={styles.priorityLabel}>{insight.priority}</span>
                <div
                  className={styles.priorityBar}
                  role="progressbar"
                  aria-label={`${insight.priority} completion rate`}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={Math.min(100, Math.round(insight.completionRate * 100))}
                >
                  <div
                    className={styles.priorityBarFill}
                    style={{ width: `${Math.min(100, Math.round(insight.completionRate * 100))}%` }}
                  />
                </div>
                <span
                  className={styles.priorityRate}
                  data-testid={`ec-priority-rate-${idx}`}
                >
                  {fmtPct(insight.completionRate)}
                </span>
                <span className={styles.prioritySample}>
                  ({insight.sampleSize} {insight.sampleSize === 1 ? "task" : "tasks"})
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
};
