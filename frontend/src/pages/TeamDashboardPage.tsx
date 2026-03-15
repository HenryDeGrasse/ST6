import React, { useEffect, useState, useCallback } from "react";
import type {
  WeeklyPlan,
  WeeklyCommit,
  ReviewDecision,
  ApiErrorResponse,
} from "@weekly-commitments/contracts";
import { WeekSelector } from "../components/WeekSelector.js";
import { TeamSummaryGrid } from "../components/TeamSummaryGrid.js";
import { TeamDashboardFiltersPanel } from "../components/TeamDashboardFilters.js";
import { RcdoRollupPanel } from "../components/RcdoRollupPanel.js";
import { PlanDrillDown } from "../components/PlanDrillDown.js";
import { NotificationBell } from "../components/NotificationBell.js";
import { ErrorBanner } from "../components/ErrorBanner.js";
import { AiManagerInsightsPanel } from "../components/AiManagerInsightsPanel.js";
import { useTeamDashboard, type TeamDashboardFilters } from "../hooks/useTeamDashboard.js";
import { useNotifications } from "../hooks/useNotifications.js";
import { useReview } from "../hooks/useReview.js";
import { useAiManagerInsights } from "../hooks/useAiManagerInsights.js";
import { useApiClient } from "../api/ApiContext.js";
import { getWeekStart } from "../utils/week.js";

/**
 * Manager team dashboard page.
 *
 * Shows: team summary grid, RCDO roll-up, filters, drill-down, review panel,
 * and notification bell.
 */
export const TeamDashboardPage: React.FC = () => {
  const [selectedWeek, setSelectedWeek] = useState(() => getWeekStart());
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

  const client = useApiClient();

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

  const {
    submitReview,
    error: reviewError,
    clearError: clearReviewError,
  } = useReview();

  const {
    headline: aiHeadline,
    insights: aiInsights,
    status: aiInsightsStatus,
    fetchInsights: fetchAiInsights,
  } = useAiManagerInsights();

  // Fetch team data when week or filters change
  useEffect(() => {
    void fetchSummary(selectedWeek, page, pageSize, filters);
    void fetchRollup(selectedWeek);
  }, [selectedWeek, page, filters, fetchSummary, fetchRollup]);

  useEffect(() => {
    void fetchAiInsights(selectedWeek);
  }, [selectedWeek, fetchAiInsights]);

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
    async (decision: ReviewDecision, comments: string) => {
      if (!drillDownPlan || !drillDownUserId) return;
      const result = await submitReview(drillDownPlan.id, decision, comments);
      if (result) {
        // Refresh drill-down and summary
        await handleDrillDown(drillDownUserId, drillDownPlan.id);
        void fetchSummary(selectedWeek, page, pageSize, filters);
      }
    },
    [drillDownPlan, drillDownUserId, submitReview, handleDrillDown, fetchSummary, selectedWeek, page, filters],
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
      <div data-testid="team-dashboard-page" style={{ maxWidth: "900px", margin: "0 auto", padding: "1rem" }}>
        <PlanDrillDown
          plan={drillDownPlan}
          commits={drillDownCommits}
          loading={drillDownLoading}
          error={drillDownError}
          displayName={drillDownDisplayName}
          onSubmitReview={handleSubmitReview}
          onBack={handleBack}
        />
      </div>
    );
  }

  return (
    <div data-testid="team-dashboard-page" style={{ maxWidth: "900px", margin: "0 auto", padding: "1rem" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h2>Team Dashboard</h2>
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

      <TeamDashboardFiltersPanel filters={filters} onFiltersChange={handleFiltersChange} />

      <AiManagerInsightsPanel
        status={aiInsightsStatus}
        headline={aiHeadline}
        insights={aiInsights}
        onRefresh={() => {
          void fetchAiInsights(selectedWeek);
        }}
      />

      {dashLoading && !summary && (
        <div data-testid="dashboard-loading" style={{ padding: "2rem", textAlign: "center", color: "#888" }}>
          Loading…
        </div>
      )}

      {summary && (
        <>
          <div data-testid="review-status-counts" style={{ marginBottom: "1rem", display: "flex", gap: "1rem" }}>
            <span>Pending: <strong>{summary.reviewStatusCounts.pending}</strong></span>
            <span>Approved: <strong>{summary.reviewStatusCounts.approved}</strong></span>
            <span>Changes Requested: <strong>{summary.reviewStatusCounts.changesRequested}</strong></span>
          </div>

          <TeamSummaryGrid
            users={summary.users}
            onDrillDown={(userId, planId) => { void handleDrillDown(userId, planId); }}
          />

          {/* Pagination controls */}
          {summary.totalPages > 1 && (
            <div data-testid="pagination" style={{ marginTop: "1rem", display: "flex", gap: "0.5rem", justifyContent: "center" }}>
              <button
                data-testid="prev-page"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                ← Previous
              </button>
              <span>Page {page + 1} of {summary.totalPages}</span>
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
    </div>
  );
};
