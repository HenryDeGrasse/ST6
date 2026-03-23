import React from "react";
import type { OutcomeCoverageTimeline as OutcomeCoverageTimelineData } from "@weekly-commitments/contracts";
import { useFeatureFlags } from "../../context/FeatureFlagContext.js";
import styles from "./OutcomeCoverageTimeline.module.css";

export interface OutcomeCoverageTimelineProps {
  outcomeId: string;
  outcomeName: string;
  data: OutcomeCoverageTimelineData | null;
  loading: boolean;
}

const TREND_LABEL: Record<OutcomeCoverageTimelineData["trendDirection"], string> = {
  RISING: "Rising",
  FALLING: "Falling",
  STABLE: "Stable",
};

const TREND_STYLE_CLASS: Record<OutcomeCoverageTimelineData["trendDirection"], string> = {
  RISING: styles.trendRising,
  FALLING: styles.trendFalling,
  STABLE: styles.trendStable,
};

/** Maximum bar height in pixels for the proportional bar chart. */
const BAR_MAX_HEIGHT_PX = 72;

/** Minimum visible bar height so even zero-ish weeks render a sliver. */
const BAR_MIN_HEIGHT_PX = 2;

/**
 * Week-by-week bar chart showing commit coverage for a single RCDO outcome.
 *
 * Uses CSS div bars — no external chart library.
 * Gated by the `strategicIntelligence` feature flag.
 */
export const OutcomeCoverageTimeline: React.FC<OutcomeCoverageTimelineProps> = ({
  outcomeId,
  outcomeName,
  data,
  loading,
}) => {
  const flags = useFeatureFlags();

  if (!flags.strategicIntelligence) {
    return null;
  }

  const weeks = data?.weeks ?? [];
  const hasMeaningfulSignal = weeks.some((w) => w.commitCount > 0 || w.contributorCount > 0);
  const maxCommitCount = Math.max(...weeks.map((w) => w.commitCount), 1);

  return (
    <div
      data-testid="outcome-coverage-timeline"
      data-outcome-id={outcomeId}
      className={styles.panel}
    >
      {/* ─── Header ─────────────────────────────────────────────────────── */}
      <div className={styles.header}>
        <span className={styles.title}>{outcomeName}</span>

        {data && hasMeaningfulSignal && (
          <span
            data-testid="coverage-trend-indicator"
            className={`${styles.trendIndicator} ${TREND_STYLE_CLASS[data.trendDirection]}`}
            aria-label={`Coverage trend: ${TREND_LABEL[data.trendDirection]}`}
            title={TREND_LABEL[data.trendDirection]}
          >
            {TREND_LABEL[data.trendDirection]}
          </span>
        )}
      </div>

      {/* ─── Loading ─────────────────────────────────────────────────────── */}
      {loading && (
        <div data-testid="outcome-coverage-loading" className={styles.statusMsg}>
          Loading coverage data…
        </div>
      )}

      {/* ─── Empty states ────────────────────────────────────────────────── */}
      {!loading && !data && (
        <div data-testid="outcome-coverage-empty" className={styles.statusMsg}>
          No coverage data available.
        </div>
      )}

      {!loading && data && weeks.length === 0 && (
        <div data-testid="outcome-coverage-no-weeks" className={styles.statusMsg}>
          No weekly data recorded yet.
        </div>
      )}

      {!loading && data && weeks.length > 0 && !hasMeaningfulSignal && (
        <div data-testid="outcome-coverage-no-signal" className={styles.statusMsg}>
          No strategic intelligence signals yet.
        </div>
      )}

      {/* ─── Bar chart ───────────────────────────────────────────────────── */}
      {!loading && data && weeks.length > 0 && hasMeaningfulSignal && (
        <div className={styles.chart} role="list" aria-label="Weekly commit coverage">
          {weeks.map((week) => {
            const barHeightPx = Math.max(
              Math.round((week.commitCount / maxCommitCount) * BAR_MAX_HEIGHT_PX),
              BAR_MIN_HEIGHT_PX,
            );

            return (
              <div
                key={week.weekStart}
                data-testid={`coverage-week-${week.weekStart}`}
                className={styles.weekColumn}
                role="listitem"
                aria-label={`Week of ${week.weekStart}: ${week.commitCount} commits, ${week.contributorCount} contributors`}
              >
                {/* Bar area */}
                <div className={styles.barWrapper}>
                  <div
                    className={styles.bar}
                    style={{ height: `${barHeightPx}px` }}
                  />
                </div>

                {/* Counts below bar */}
                <div className={styles.weekMeta}>
                  <span className={styles.commitCount} title="Commits">
                    {week.commitCount}
                  </span>
                  <span className={styles.separator} aria-hidden="true">·</span>
                  <span className={styles.contributorCount} title="Contributors">
                    {week.contributorCount}p
                  </span>
                </div>

                {/* Week label (MM-DD from ISO date) */}
                <div className={styles.weekLabel}>
                  {week.weekStart.length >= 10 ? week.weekStart.slice(5, 10) : week.weekStart}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
