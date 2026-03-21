import React, { useEffect, useState } from "react";
import { useFeatureFlags } from "../../context/FeatureFlagContext.js";
import { useAuth } from "../../context/AuthContext.js";
import {
  useCarryForwardHeatmap,
  useOutcomeCoverageTimeline,
  usePredictions,
} from "../../hooks/useAnalytics.js";
import { CarryForwardHeatmap } from "./CarryForwardHeatmap.js";
import { OutcomeCoverageTimeline } from "./OutcomeCoverageTimeline.js";
import { PredictionAlerts } from "./PredictionAlerts.js";
import styles from "./StrategicIntelligencePanel.module.css";
import type { OutcomeCoverageTimeline as OutcomeCoverageTimelineData } from "@weekly-commitments/contracts";

// ─── Per-outcome sub-component ────────────────────────────────────────────────

interface OutcomeTimelineItemProps {
  outcomeId: string;
  outcomeName: string;
  weekStart: string;
}

/**
 * Fetches and renders a single outcome's coverage timeline.
 * Each instance manages its own hook state so multiple outcomes
 * can be shown independently without requiring a parent array-hook.
 */
const OutcomeTimelineItem: React.FC<OutcomeTimelineItemProps> = ({
  outcomeId,
  outcomeName,
  weekStart,
}) => {
  const { data, loading, error, fetch } = useOutcomeCoverageTimeline();

  useEffect(() => {
    void fetch(outcomeId);
  }, [outcomeId, weekStart, fetch]);

  if (error) {
    return (
      <div className={styles.outcomeError} data-testid={`outcome-error-${outcomeId}`}>
        Failed to load coverage for {outcomeName}: {error}
      </div>
    );
  }

  return (
    <OutcomeCoverageTimeline
      outcomeId={outcomeId}
      outcomeName={outcomeName}
      data={data as OutcomeCoverageTimelineData | null}
      loading={loading}
    />
  );
};

// ─── Main panel ───────────────────────────────────────────────────────────────

export interface OutcomeInfo {
  id: string;
  name: string;
}

export interface StrategicIntelligencePanelProps {
  /** ISO date (YYYY-MM-DD) for the current week. Re-fetches data when this changes. */
  weekStart: string;
  /**
   * List of RCDO outcomes to render as coverage timelines.
   * Typically sourced from the RCDO rollup on the parent TeamDashboardPage.
   */
  outcomes?: OutcomeInfo[];
}

/**
 * Container panel orchestrating:
 *  - Per-outcome Coverage Timelines (OutcomeCoverageTimeline)
 *  - Carry-Forward Heatmap (CarryForwardHeatmap)
 *  - Prediction Alerts (PredictionAlerts)
 *
 * Each major section is independently collapsible.
 * The entire panel is gated on the `strategicIntelligence` feature flag.
 */
export const StrategicIntelligencePanel: React.FC<StrategicIntelligencePanelProps> = ({
  weekStart,
  outcomes = [],
}) => {
  const flags = useFeatureFlags();
  const { user } = useAuth();

  // ─── Per-section collapsed state ─────────────────────────────────────────
  const [heatmapExpanded, setHeatmapExpanded] = useState(true);
  const [predictionsExpanded, setPredictionsExpanded] = useState(true);
  const [timelinesExpanded, setTimelinesExpanded] = useState(true);

  // ─── Analytics hooks ──────────────────────────────────────────────────────
  const {
    data: heatmapData,
    loading: heatmapLoading,
    fetch: fetchHeatmap,
  } = useCarryForwardHeatmap();

  const {
    data: predictions,
    loading: predictionsLoading,
    fetch: fetchPredictions,
  } = usePredictions();

  // ─── Fetch on mount and weekStart change ─────────────────────────────────
  useEffect(() => {
    if (!flags.strategicIntelligence) {
      return;
    }

    void fetchHeatmap();
  }, [weekStart, fetchHeatmap, flags.strategicIntelligence]);

  useEffect(() => {
    if (!flags.strategicIntelligence || !flags.predictions) {
      return;
    }

    void fetchPredictions(user.userId);
  }, [weekStart, user.userId, fetchPredictions, flags.predictions, flags.strategicIntelligence]);

  // ─── Feature-flag gate ────────────────────────────────────────────────────
  if (!flags.strategicIntelligence) {
    return null;
  }

  return (
    <div data-testid="strategic-intelligence-panel" className={styles.panel}>
      {/* ─── Panel header ────────────────────────────────────────────── */}
      <div className={styles.panelHeader}>
        <span className={styles.panelTitle}>Strategic Intelligence</span>
      </div>

      {/* ─── Section: Outcome Coverage Timelines ─────────────────────── */}
      <div className={styles.section} data-testid="section-timelines">
        <div className={styles.sectionHeader}>
          <span className={styles.sectionTitle}>Outcome Coverage</span>
          <button
            type="button"
            className={styles.toggleButton}
            aria-expanded={timelinesExpanded}
            data-testid="toggle-timelines"
            onClick={() => setTimelinesExpanded((prev) => !prev)}
          >
            {timelinesExpanded ? "Hide" : "Show"}
          </button>
        </div>

        {timelinesExpanded && (
          <div className={styles.sectionContent} data-testid="timelines-content">
            {outcomes.length === 0 ? (
              <div className={styles.emptyOutcomes} data-testid="timelines-empty">
                No outcomes available for this week.
              </div>
            ) : (
              <div className={styles.outcomesGrid}>
                {outcomes.map((outcome) => (
                  <OutcomeTimelineItem
                    key={outcome.id}
                    outcomeId={outcome.id}
                    outcomeName={outcome.name}
                    weekStart={weekStart}
                  />
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* ─── Section: Carry-Forward Heatmap ──────────────────────────── */}
      <div className={styles.section} data-testid="section-heatmap">
        <div className={styles.sectionHeader}>
          <span className={styles.sectionTitle}>Carry-Forward</span>
          <button
            type="button"
            className={styles.toggleButton}
            aria-expanded={heatmapExpanded}
            data-testid="toggle-heatmap"
            onClick={() => setHeatmapExpanded((prev) => !prev)}
          >
            {heatmapExpanded ? "Hide" : "Show"}
          </button>
        </div>

        {heatmapExpanded && (
          <div className={styles.sectionContent} data-testid="heatmap-content">
            <CarryForwardHeatmap data={heatmapData} loading={heatmapLoading} />
          </div>
        )}
      </div>

      {/* ─── Section: Prediction Alerts ──────────────────────────────── */}
      {flags.predictions && (
        <div className={styles.section} data-testid="section-predictions">
          <div className={styles.sectionHeader}>
            <span className={styles.sectionTitle}>Predictions</span>
            <button
              type="button"
              className={styles.toggleButton}
              aria-expanded={predictionsExpanded}
              data-testid="toggle-predictions"
              onClick={() => setPredictionsExpanded((prev) => !prev)}
            >
              {predictionsExpanded ? "Hide" : "Show"}
            </button>
          </div>

          {predictionsExpanded && (
            <div className={styles.sectionContent} data-testid="predictions-content">
              <PredictionAlerts
                predictions={predictions ?? []}
                loading={predictionsLoading}
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
};
