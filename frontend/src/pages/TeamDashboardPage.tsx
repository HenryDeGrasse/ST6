import React, { useEffect, useState, useCallback, useMemo } from "react";
import { ReviewStatus } from "@weekly-commitments/contracts";
import type { WeeklyPlan, WeeklyCommit, ReviewDecision, ApiErrorResponse } from "@weekly-commitments/contracts";
import { WeekSelector } from "../components/WeekSelector.js";
import { TeamSummaryGrid } from "../components/TeamSummaryGrid.js";
import { TeamDashboardFiltersPanel } from "../components/TeamDashboardFilters.js";
import { RcdoRollupPanel } from "../components/RcdoRollupPanel.js";
import { PlanDrillDown } from "../components/PlanDrillDown.js";
import { NotificationBell } from "../components/NotificationBell.js";
import { ErrorBanner } from "../components/ErrorBanner.js";
import { AiManagerInsightsPanel } from "../components/AiManagerInsightsPanel.js";
import { GlassPanel } from "../components/GlassPanel.js";
import { StrategicSlackBanner } from "../components/UrgencyIndicator/StrategicSlackBanner.js";
import { OutcomeMetadataEditor } from "../components/UrgencyIndicator/OutcomeMetadataEditor.js";
import { OutcomeRiskCard, PlanningCopilot } from "../components/Phase5/index.js";
import {
  StrategicIntelligencePanel,
  type OutcomeInfo,
} from "../components/StrategicIntelligence/index.js";
import { useTeamDashboard, type TeamDashboardFilters } from "../hooks/useTeamDashboard.js";
import { useNotifications } from "../hooks/useNotifications.js";
import { useReview } from "../hooks/useReview.js";
import { useAiManagerInsights } from "../hooks/useAiManagerInsights.js";
import {
  useOutcomeMetadata,
  type OutcomeMetadataRequest,
  type ProgressUpdateRequest,
} from "../hooks/useOutcomeMetadata.js";
import { useRcdo } from "../hooks/useRcdo.js";
import { useForecasts } from "../hooks/useForecasts.js";
import { useOrgBacklogHealth } from "../hooks/useAnalytics.js";
import { EFFORT_TYPE_COLORS } from "../components/charts/index.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import { useAuth } from "../context/AuthContext.js";
import { useApiClient } from "../api/ApiContext.js";
import { getWeekStart } from "../utils/week.js";
import styles from "./TeamDashboardPage.module.css";

/**
 * Manager team dashboard page.
 *
 * Shows: team summary grid, RCDO roll-up, filters, drill-down, review panel,
 * and notification bell.
 */
export const TeamDashboardPage: React.FC = () => {
  const [selectedWeek, setSelectedWeek] = useState(() => getWeekStart());
  const [activeTab, setActiveTab] = useState<"overview" | "strategic-intelligence">("overview");
  const [filters, setFilters] = useState<TeamDashboardFilters>({});
  const [page, setPage] = useState(0);
  const pageSize = 20;

  // Drill-down state
  const [drillDownUserId, setDrillDownUserId] = useState<string | null>(null);
  const [drillDownDisplayName, setDrillDownDisplayName] = useState<string | null>(null);
  const [drillDownPlan, setDrillDownPlan] = useState<WeeklyPlan | null>(null);
  const [drillDownCommits, setDrillDownCommits] = useState<WeeklyCommit[]>([]);
  const [drillDownLoading, setDrillDownLoading] = useState(false);
  const [drillDownError, setDrillDownError] = useState<string | null>(null);

  // Outcome Targets collapsible section state
  const [showOutcomeTargets, setShowOutcomeTargets] = useState(false);
  const [selectedForecastOutcomeId, setSelectedForecastOutcomeId] = useState<string | null>(null);

  const client = useApiClient();
  const flags = useFeatureFlags();
  const { user } = useAuth();
  const isManager = user.roles.includes("MANAGER");
  const strategicSlackEnabled = flags.strategicSlack;
  const outcomeUrgencyEnabled = flags.outcomeUrgency;
  const strategicIntelligenceEnabled = flags.strategicIntelligence;

  const {
    summary,
    rollup,
    loading: dashLoading,
    error: dashError,
    fetchSummary,
    fetchRollup,
    clearError: clearDashError,
  } = useTeamDashboard();

  const {
    notifications,
    unreadCount,
    error: notificationError,
    fetchUnread,
    markRead,
    markAllRead,
    clearError: clearNotificationError,
  } = useNotifications();

  const { submitReview, error: reviewError, clearError: clearReviewError } = useReview();

  const {
    headline: aiHeadline,
    insights: aiInsights,
    status: aiInsightsStatus,
    fetchInsights: fetchAiInsights,
  } = useAiManagerInsights();

  const {
    outcomeMetadata,
    strategicSlack,
    loading: metaLoading,
    fetchMetadata,
    fetchStrategicSlack,
    updateMetadata,
    updateProgress,
  } = useOutcomeMetadata();

  const { tree: rcdoTree, fetchTree } = useRcdo();
  const { forecasts, loadingList: forecastsLoading, errorList: forecastsError, fetchForecasts } = useForecasts();

  // Phase 6: backlog health (gated by useIssueBacklog flag)
  const {
    data: orgBacklogHealth,
    error: backlogHealthError,
    fetch: fetchOrgBacklogHealth,
    clearError: clearBacklogHealthError,
  } = useOrgBacklogHealth();

  // Flatten RCDO tree into { outcomeId, outcomeName } for the editor dropdown.
  const outcomes = useMemo(
    () =>
      rcdoTree.flatMap((cry) =>
        cry.objectives.flatMap((obj) =>
          obj.outcomes.map((o) => ({ outcomeId: o.id, outcomeName: o.name })),
        ),
      ),
    [rcdoTree],
  );

  // Flatten RCDO tree into OutcomeInfo[] for the StrategicIntelligencePanel.
  const strategicOutcomes = useMemo<OutcomeInfo[]>(
    () =>
      rcdoTree.flatMap((cry) =>
        cry.objectives.flatMap((obj) =>
          obj.outcomes.map((o) => ({ id: o.id, name: o.name })),
        ),
      ),
    [rcdoTree],
  );

  // Wrap updateMetadata/updateProgress to satisfy OutcomeMetadataEditor's Promise<void> signatures.
  const handleSaveMetadata = useCallback(
    async (outcomeId: string, data: OutcomeMetadataRequest): Promise<void> => {
      await updateMetadata(outcomeId, data);
    },
    [updateMetadata],
  );

  const handleUpdateProgress = useCallback(
    async (outcomeId: string, data: ProgressUpdateRequest): Promise<void> => {
      await updateProgress(outcomeId, data);
    },
    [updateProgress],
  );

  // Fetch team data when week or filters change
  useEffect(() => {
    void fetchSummary(selectedWeek, page, pageSize, filters);
    void fetchRollup(selectedWeek);
  }, [selectedWeek, page, filters, fetchSummary, fetchRollup]);

  useEffect(() => {
    void fetchAiInsights(selectedWeek);
  }, [selectedWeek, fetchAiInsights]);

  // Fetch strategic slack when the strategicSlack flag is enabled.
  useEffect(() => {
    if (strategicSlackEnabled) {
      void fetchStrategicSlack();
    }
  }, [strategicSlackEnabled, fetchStrategicSlack]);

  // Fetch outcome metadata and RCDO tree when the outcomeUrgency flag is enabled.
  // Also fetch RCDO tree when the strategicIntelligence flag is enabled (for outcome timelines).
  useEffect(() => {
    if (outcomeUrgencyEnabled) {
      void fetchMetadata();
      void fetchTree();
    } else if (strategicIntelligenceEnabled) {
      void fetchTree();
    }
  }, [outcomeUrgencyEnabled, strategicIntelligenceEnabled, fetchMetadata, fetchTree]);

  useEffect(() => {
    if (!strategicIntelligenceEnabled && activeTab !== "overview") {
      setActiveTab("overview");
    }
  }, [activeTab, strategicIntelligenceEnabled]);

  useEffect(() => {
    if (flags.targetDateForecasting) {
      void fetchForecasts();
    }
  }, [fetchForecasts, flags.targetDateForecasting]);

  useEffect(() => {
    if (!flags.useIssueBacklog) {
      clearBacklogHealthError();
      return;
    }
    void fetchOrgBacklogHealth();
  }, [clearBacklogHealthError, fetchOrgBacklogHealth, flags.useIssueBacklog]);

  const featuredForecasts = useMemo(() => {
    return [...forecasts]
      .sort((left, right) => {
        const statusWeight = (status: string | null | undefined): number => {
          switch (status) {
            case "AT_RISK":
              return 0;
            case "NEEDS_ATTENTION":
              return 1;
            case "ON_TRACK":
              return 2;
            default:
              return 3;
          }
        };

        return statusWeight(left.forecastStatus) - statusWeight(right.forecastStatus);
      })
      .slice(0, 3);
  }, [forecasts]);

  const backlogHealth = useMemo(() => {
    if (!orgBacklogHealth || orgBacklogHealth.length === 0) {
      return null;
    }

    const totalOpen = orgBacklogHealth.reduce((sum, team) => sum + team.openIssueCount, 0);
    const blockedCount = orgBacklogHealth.reduce((sum, team) => sum + team.blockedCount, 0);
    const avgAgeDays =
      totalOpen > 0
        ? Math.round(
            orgBacklogHealth.reduce(
              (sum, team) => sum + team.avgIssueAgeDays * team.openIssueCount,
              0,
            ) / totalOpen,
          )
        : 0;
    const avgCycleTimeDays =
      totalOpen > 0
        ? Math.round(
            orgBacklogHealth.reduce(
              (sum, team) => sum + team.avgCycleTimeDays * team.openIssueCount,
              0,
            ) / totalOpen,
          )
        : 0;

    return {
      openCount: totalOpen,
      blockedCount,
      avgAgeDays,
      avgCycleTimeDays,
      effortCounts: {
        BUILD: orgBacklogHealth.reduce((sum, team) => sum + team.buildCount, 0),
        MAINTAIN: orgBacklogHealth.reduce((sum, team) => sum + team.maintainCount, 0),
        COLLABORATE: orgBacklogHealth.reduce((sum, team) => sum + team.collaborateCount, 0),
        LEARN: orgBacklogHealth.reduce((sum, team) => sum + team.learnCount, 0),
      },
      teamCount: orgBacklogHealth.length,
    };
  }, [orgBacklogHealth]);

  useEffect(() => {
    if (!flags.targetDateForecasting || featuredForecasts.length === 0) {
      setSelectedForecastOutcomeId(null);
      return;
    }

    const selectionStillVisible = featuredForecasts.some((forecast) => forecast.outcomeId === selectedForecastOutcomeId);
    if (!selectionStillVisible) {
      setSelectedForecastOutcomeId(featuredForecasts[0]?.outcomeId ?? null);
    }
  }, [featuredForecasts, flags.targetDateForecasting, selectedForecastOutcomeId]);

  const handleWeekChange = useCallback((week: string) => {
    setSelectedWeek(week);
    setPage(0);
    setDrillDownUserId(null);
  }, []);

  const handleFiltersChange = useCallback((newFilters: TeamDashboardFilters) => {
    setFilters(newFilters);
    setPage(0);
  }, []);

  const handleDrillDown = useCallback(
    async (userId: string, _planId: string | null) => {
      // Resolve display name from current summary data
      const memberSummary = summary?.users.find((u) => u.userId === userId);
      setDrillDownDisplayName(memberSummary?.displayName ?? null);
      setDrillDownUserId(userId);
      setDrillDownLoading(true);
      setDrillDownError(null);
      setDrillDownPlan(null);
      setDrillDownCommits([]);

      try {
        // Fetch the user's plan
        const planResp = await client.GET("/weeks/{weekStart}/plans/{userId}", {
          params: { path: { weekStart: selectedWeek, userId } },
        });

        if (planResp.data) {
          const plan = planResp.data as WeeklyPlan;
          setDrillDownPlan(plan);

          // Fetch commits through the manager-authorized drill-down endpoint
          const commitsResp = await client.GET("/weeks/{weekStart}/plans/{userId}/commits", {
            params: { path: { weekStart: selectedWeek, userId } },
          });
          if (commitsResp.data) {
            setDrillDownCommits(commitsResp.data as WeeklyCommit[]);
          } else {
            const err = commitsResp.error as ApiErrorResponse | undefined;
            setDrillDownError(err?.error?.message ?? "Failed to load commits");
          }
        } else if (planResp.response.status === 404) {
          setDrillDownPlan(null);
        } else {
          const err = planResp.error as ApiErrorResponse | undefined;
          setDrillDownError(err?.error?.message ?? "Failed to load plan");
        }
      } catch (e) {
        setDrillDownError(e instanceof Error ? e.message : "Network error");
      } finally {
        setDrillDownLoading(false);
      }
    },
    [client, selectedWeek, summary],
  );

  const handleBack = useCallback(() => {
    setDrillDownUserId(null);
    setDrillDownDisplayName(null);
    setDrillDownPlan(null);
    setDrillDownCommits([]);
    setDrillDownError(null);
  }, []);

  const handleSubmitReview = useCallback(
    async (decision: ReviewDecision, comments: string): Promise<boolean> => {
      if (!drillDownPlan || !drillDownUserId) {
        return false;
      }
      const result = await submitReview(drillDownPlan.id, decision, comments);
      if (!result) {
        return false;
      }

      const nextReviewStatus = decision === "APPROVED" ? ReviewStatus.APPROVED : ReviewStatus.CHANGES_REQUESTED;
      setDrillDownPlan((current) => (current ? { ...current, reviewStatus: nextReviewStatus } : current));
      void fetchSummary(selectedWeek, page, pageSize, filters);
      return true;
    },
    [drillDownPlan, drillDownUserId, submitReview, fetchSummary, selectedWeek, page, filters],
  );

  const error = dashError ?? reviewError ?? notificationError ?? backlogHealthError;
  const clearError = useCallback(() => {
    clearDashError();
    clearReviewError();
    clearNotificationError();
    clearBacklogHealthError();
  }, [clearBacklogHealthError, clearDashError, clearReviewError, clearNotificationError]);

  // If in drill-down mode, show drill-down view
  if (drillDownUserId) {
    return (
      <div data-testid="team-dashboard-page" className={styles.page}>
        <GlassPanel className={styles.contentPanel}>
          <PlanDrillDown
            plan={drillDownPlan}
            commits={drillDownCommits}
            loading={drillDownLoading}
            error={drillDownError}
            displayName={drillDownDisplayName}
            onSubmitReview={handleSubmitReview}
            onBack={handleBack}
          />
        </GlassPanel>
      </div>
    );
  }

  return (
    <div data-testid="team-dashboard-page" className={styles.page}>
      <GlassPanel className={styles.contentPanel}>
        <div className={styles.header}>
          <div>
            <h2 className={styles.heading}>Team Dashboard</h2>
          </div>
          <NotificationBell
            notifications={notifications}
            unreadCount={unreadCount}
            onMarkRead={markRead}
            onMarkAllRead={markAllRead}
            onFetchUnread={fetchUnread}
          />
        </div>

        <WeekSelector selectedWeek={selectedWeek} onWeekChange={handleWeekChange} />

        <ErrorBanner message={error} onDismiss={clearError} />

        {/* ─── Tab bar (only rendered when the strategicIntelligence flag is on) ── */}
        {strategicIntelligenceEnabled && (
          <div className={styles.tabBar} role="tablist" data-testid="dashboard-tabs">
            <button
              role="tab"
              type="button"
              data-testid="tab-overview"
              aria-selected={activeTab === "overview"}
              className={`${styles.tab} ${activeTab === "overview" ? styles.tabActive : ""}`}
              onClick={() => setActiveTab("overview")}
            >
              Team Overview
            </button>
            <button
              role="tab"
              type="button"
              data-testid="tab-strategic-intelligence"
              aria-selected={activeTab === "strategic-intelligence"}
              className={`${styles.tab} ${activeTab === "strategic-intelligence" ? styles.tabActive : ""}`}
              onClick={() => setActiveTab("strategic-intelligence")}
            >
              Strategic Intelligence
            </button>
          </div>
        )}

        {/* ─── Team Overview tab content (always shown when flag is off, or tab active) ── */}
        {activeTab === "overview" && (
          <>
            <TeamDashboardFiltersPanel filters={filters} onFiltersChange={handleFiltersChange} />

            <AiManagerInsightsPanel
              status={aiInsightsStatus}
              headline={aiHeadline}
              insights={aiInsights}
              onRefresh={() => {
                void fetchAiInsights(selectedWeek);
              }}
            />

            {flags.strategicSlack && strategicSlack && (
              <StrategicSlackBanner
                slackBand={strategicSlack.slackBand}
                strategicFocusFloor={strategicSlack.strategicFocusFloor}
                atRiskCount={strategicSlack.atRiskCount}
                criticalCount={strategicSlack.criticalCount}
              />
            )}

            {(flags.targetDateForecasting || flags.planningCopilot) && (
              <section data-testid="phase5-manager-panels" className={styles.phase5Section}>
                {flags.targetDateForecasting && (
                  <div data-testid="forecasting-panel" className={styles.phase5Column}>
                    <div className={styles.phase5Header}>
                      <div>
                        <h3 className={styles.phase5Title}>Target-date Forecasts</h3>
                      </div>
                      <button
                        type="button"
                        data-testid="forecasting-refresh"
                        className={styles.outcomeTargetsToggle}
                        onClick={() => {
                          void fetchForecasts();
                        }}
                      >
                        Refresh Forecasts
                      </button>
                    </div>

                    {forecastsError && <ErrorBanner message={forecastsError} onDismiss={() => undefined} />}
                    {forecastsLoading && featuredForecasts.length === 0 && (
                      <div data-testid="forecasting-loading" className={styles.loading}>
                        Loading forecasts…
                      </div>
                    )}
                    {!forecastsLoading && featuredForecasts.length === 0 && !forecastsError && (
                      <div data-testid="forecasting-empty" className={styles.phase5Empty}>
                        No persisted forecasts are available yet.
                      </div>
                    )}

                    {featuredForecasts.length > 0 && (
                      <>
                        <div data-testid="forecasting-picker" className={styles.forecastPicker}>
                          {featuredForecasts.map((forecast) => {
                            const isActive = selectedForecastOutcomeId === forecast.outcomeId;
                            return (
                              <button
                                key={forecast.outcomeId}
                                type="button"
                                data-testid={`forecast-select-${forecast.outcomeId}`}
                                className={`${styles.forecastButton} ${isActive ? styles.forecastButtonActive : ""}`}
                                onClick={() => {
                                  setSelectedForecastOutcomeId(forecast.outcomeId);
                                }}
                              >
                                <span>{forecast.outcomeName}</span>
                                <span className={styles.forecastStatus}>{forecast.forecastStatus ?? "UNKNOWN"}</span>
                              </button>
                            );
                          })}
                        </div>
                        {selectedForecastOutcomeId && <OutcomeRiskCard outcomeId={selectedForecastOutcomeId} />}
                      </>
                    )}
                  </div>
                )}

                {flags.planningCopilot && (
                  <div data-testid="planning-copilot-panel" className={styles.phase5Column}>
                    <PlanningCopilot weekStart={selectedWeek} />
                  </div>
                )}
              </section>
            )}

            {dashLoading && !summary && (
              <div data-testid="dashboard-loading" className={styles.loading}>
                Loading…
              </div>
            )}

            {summary && (
              <>
                <div data-testid="review-status-counts" className={styles.reviewStatusCounts}>
                  <span>
                    Pending: <strong>{summary.reviewStatusCounts.pending}</strong>
                  </span>
                  <span>
                    Approved: <strong>{summary.reviewStatusCounts.approved}</strong>
                  </span>
                  <span>
                    Changes Requested: <strong>{summary.reviewStatusCounts.changesRequested}</strong>
                  </span>
                </div>

                <TeamSummaryGrid
                  users={summary.users}
                  onDrillDown={(userId, planId) => {
                    void handleDrillDown(userId, planId);
                  }}
                />

                {/* Pagination controls */}
                {summary.totalPages > 1 && (
                  <div data-testid="pagination" className={styles.pagination}>
                    <button
                      data-testid="prev-page"
                      disabled={page === 0}
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                    >
                      ← Previous
                    </button>
                    <span className={styles.paginationLabel}>
                      Page {page + 1} of {summary.totalPages}
                    </span>
                    <button
                      data-testid="next-page"
                      disabled={page >= summary.totalPages - 1}
                      onClick={() => setPage((p) => p + 1)}
                    >
                      Next →
                    </button>
                  </div>
                )}
              </>
            )}

            <RcdoRollupPanel rollup={rollup} loading={dashLoading} />

            {/* ─── Phase 6: Backlog Health ────────────────────────────── */}
            {flags.useIssueBacklog && backlogHealth && (
              <section data-testid="backlog-health-section" className={styles.backlogHealthSection}>
                <div className={styles.backlogHealthHeader}>
                  <h3 className={styles.backlogHealthTitle}>Backlog Health</h3>
                </div>
                <div className={styles.backlogHealthGrid}>
                  <div className={styles.backlogHealthCard} data-testid="backlog-open-count">
                    <span className={styles.backlogMetricLabel}>Open Issues</span>
                    <span className={styles.backlogMetricValue}>{backlogHealth.openCount}</span>
                  </div>
                  <div className={styles.backlogHealthCard} data-testid="backlog-blocked-count">
                    <span className={styles.backlogMetricLabel}>Blocked</span>
                    <span
                      className={styles.backlogMetricValue}
                      style={backlogHealth.blockedCount > 0 ? { color: "#C47A84" } : undefined}
                    >
                      {backlogHealth.blockedCount}
                    </span>
                  </div>
                  <div className={styles.backlogHealthCard} data-testid="backlog-avg-age">
                    <span className={styles.backlogMetricLabel}>Avg Age</span>
                    <span className={styles.backlogMetricValue}>{backlogHealth.avgAgeDays}d</span>
                  </div>
                  <div className={styles.backlogHealthCard} data-testid="backlog-avg-cycle-time">
                    <span className={styles.backlogMetricLabel}>Avg Cycle Time</span>
                    <span className={styles.backlogMetricValue}>
                      {backlogHealth.avgCycleTimeDays > 0 ? `${backlogHealth.avgCycleTimeDays}d` : "—"}
                    </span>
                  </div>
                  {backlogHealth.teamCount > 0 && (
                    <div className={styles.backlogHealthCard} data-testid="backlog-team-count">
                      <span className={styles.backlogMetricLabel}>Teams</span>
                      <span className={styles.backlogMetricValue}>{backlogHealth.teamCount}</span>
                    </div>
                  )}
                </div>
                {/* Effort type breakdown */}
                {Object.keys(backlogHealth.effortCounts).length > 0 && (
                  <div className={styles.backlogEffortBreakdown} data-testid="backlog-effort-breakdown">
                    <span className={styles.backlogEffortLabel}>By Effort Type</span>
                    <div className={styles.backlogEffortBars}>
                      {Object.entries(backlogHealth.effortCounts).map(([type, count]) => (
                        <div key={type} className={styles.backlogEffortItem}>
                          <span
                            className={styles.backlogEffortSwatch}
                            style={{ backgroundColor: EFFORT_TYPE_COLORS[type] ?? "#9C8B7A" }}
                          />
                          <span className={styles.backlogEffortType}>
                            {type.charAt(0) + type.slice(1).toLowerCase()}
                          </span>
                          <span className={styles.backlogEffortCount}>{count}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                <p className={styles.backlogHealthLink}>
                  <a
                    href="#backlog"
                    onClick={(e) => {
                      e.preventDefault();
                      window.dispatchEvent(
                        new CustomEvent("wc:navigate", {
                          detail: { route: "weekly/backlog" },
                        }),
                      );
                    }}
                    data-testid="backlog-health-link"
                    className={styles.backlogLink}
                  >
                    View full backlog →
                  </a>
                </p>
              </section>
            )}

            {flags.outcomeUrgency && isManager && (
              <div data-testid="outcome-targets-section">
                <button
                  type="button"
                  data-testid="outcome-targets-toggle"
                  className={styles.outcomeTargetsToggle}
                  onClick={() => {
                    setShowOutcomeTargets((v) => !v);
                  }}
                >
                  {showOutcomeTargets ? "Manage Outcome Targets ▾" : "Manage Outcome Targets ▸"}
                </button>
                {showOutcomeTargets && (
                  <OutcomeMetadataEditor
                    outcomes={outcomes}
                    metadata={outcomeMetadata}
                    onSave={handleSaveMetadata}
                    onUpdateProgress={handleUpdateProgress}
                    loading={metaLoading}
                  />
                )}
              </div>
            )}
          </>
        )}

        {/* ─── Strategic Intelligence tab content ────────────────────────────── */}
        {strategicIntelligenceEnabled && activeTab === "strategic-intelligence" && (
          <StrategicIntelligencePanel weekStart={selectedWeek} outcomes={strategicOutcomes} />
        )}
      </GlassPanel>
    </div>
  );
};
