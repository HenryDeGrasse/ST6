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

  const error = dashError ?? reviewError ?? notificationError;
  const clearError = useCallback(() => {
    clearDashError();
    clearReviewError();
    clearNotificationError();
  }, [clearDashError, clearReviewError, clearNotificationError]);

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
            <span className="wc-volume-label" aria-hidden="true">
              Volume II
            </span>
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
