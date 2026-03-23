import React, { useEffect, useState, useCallback } from "react";
import type {
  CreateCommitRequest,
  UpdateCommitRequest,
  UpdateActualRequest,
  ReconciliationDraftItem,
  NextWorkSuggestion,
  SuggestionFeedbackRequest,
  CheckInRequest,
  Issue,
} from "@weekly-commitments/contracts";
import { PlanState, CompletionStatus, ChessPriority } from "@weekly-commitments/contracts";
import { WeekSelector } from "../components/WeekSelector.js";
import { PlanHeader } from "../components/PlanHeader.js";
import { CommitList } from "../components/CommitList.js";
import { ValidationPanel } from "../components/ValidationPanel.js";
import { PlanSummaryStrip } from "../components/PlanSummaryStrip.js";
import { ReconciliationView } from "../components/ReconciliationView.js";
import { CarryForwardDialog } from "../components/CarryForwardDialog.js";
import { AiReconciliationDraft } from "../components/AiReconciliationDraft.js";
import { ConfirmDialog } from "../components/ConfirmDialog.js";
import { ErrorBanner } from "../components/ErrorBanner.js";
import { GlassPanel } from "../components/GlassPanel.js";
import { PlanQualityNudge } from "../components/PlanQualityNudge.js";
import { NextWorkSuggestionPanel } from "../components/NextWorkSuggestionPanel.js";
import { DigestPreferencesSection } from "../components/DigestPreferencesSection.js";
import { QuickUpdateFlow } from "../components/QuickUpdate/QuickUpdateFlow.js";
import type { QuickUpdateCommitment } from "../components/QuickUpdate/QuickUpdateFlow.js";
import { OvercommitBanner } from "../components/CapacityView/OvercommitBanner.js";
import type { OvercommitLevel } from "../components/CapacityView/OvercommitBanner.js";
import { BacklogPickerDialog } from "../components/BacklogPickerDialog.js";
import { usePlan } from "../hooks/usePlan.js";
import { useCommits } from "../hooks/useCommits.js";
import { useWeeklyAssignments } from "../hooks/useWeeklyAssignments.js";
import { useRcdo } from "../hooks/useRcdo.js";
import { useAiSuggestions } from "../hooks/useAiSuggestions.js";
import { usePlanQualityCheck } from "../hooks/usePlanQualityCheck.js";
import { useDraftFromHistory } from "../hooks/useDraftFromHistory.js";
import { useNextWorkSuggestions } from "../hooks/useNextWorkSuggestions.js";
import { useCheckIn } from "../hooks/useCheckIn.js";
import { useCapacityProfile } from "../hooks/useCapacity.js";
import { getWeekStart, getNextWeekStart, isPastWeek, isFutureWeek, isCreateAllowedForWeek } from "../utils/week.js";
import { useToast } from "../context/ToastContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import styles from "./WeeklyPlanPage.module.css";

/** Which confirmation dialog is currently shown */
type ConfirmAction =
  | { type: "delete-commit"; commitId: string }
  | { type: "lock-plan" }
  | { type: "submit-reconciliation" }
  | null;

type LifecycleAction = "lock-plan" | "start-reconciliation" | "submit-reconciliation" | "carry-forward" | null;

/**
 * Main IC workflow page for weekly planning.
 *
 * Orchestrates: week selection → plan creation/fetch → commit CRUD →
 * validation → lock → reconciliation → carry-forward.
 */
export const WeeklyPlanPage: React.FC = () => {
  const [selectedWeek, setSelectedWeek] = useState(() => getWeekStart());
  const [showCarryForward, setShowCarryForward] = useState(false);
  const [showBacklogPicker, setShowBacklogPicker] = useState(false);
  const [addingFromBacklog, setAddingFromBacklog] = useState(false);
  const [pendingConfirm, setPendingConfirm] = useState<ConfirmAction>(null);
  const [lifecycleAction, setLifecycleAction] = useState<LifecycleAction>(null);
  const [showQualityNudge, setShowQualityNudge] = useState(false);
  const [showQuickUpdate, setShowQuickUpdate] = useState(false);

  const { showToast } = useToast();

  const {
    plan,
    loading: planLoading,
    error: planError,
    fetchPlan,
    createPlan,
    lockPlan,
    startReconciliation,
    submitReconciliation,
    carryForward,
    clearError: clearPlanError,
  } = usePlan();

  const {
    commits,
    loading: commitsLoading,
    error: commitsError,
    fetchCommits,
    createCommit,
    updateCommit,
    deleteCommit,
    updateActual,
    resetCommits,
    clearError: clearCommitsError,
  } = useCommits();

  const {
    assignments,
    loading: assignmentsLoading,
    error: assignmentsError,
    fetchAssignments,
    createAssignment,
    removeAssignment,
    releaseToBacklog,
    resetAssignments,
    clearError: clearAssignmentsError,
  } = useWeeklyAssignments();

  const {
    tree: rcdoTree,
    searchResults: rcdoSearchResults,
    error: rcdoError,
    fetchTree: fetchRcdoTree,
    search: searchRcdo,
    clearSearch: clearRcdoSearch,
    clearError: clearRcdoError,
  } = useRcdo();

  const {
    suggestions: aiSuggestions,
    suggestStatus: aiSuggestStatus,
    fetchSuggestions: fetchAiSuggestions,
    clearSuggestions: clearAiSuggestions,
    draftItems: aiDraftItems,
    draftStatus: aiDraftStatus,
    fetchDraft: fetchAiDraft,
  } = useAiSuggestions();

  const {
    nudges: qualityNudges,
    status: qualityStatus,
    checkQuality,
    clearNudges: clearQualityNudges,
  } = usePlanQualityCheck();

  const {
    status: draftFromHistoryStatus,
    error: draftFromHistoryError,
    draftFromHistory,
    reset: resetDraftFromHistory,
  } = useDraftFromHistory();

  const {
    suggestions: nextWorkSuggestions,
    status: nextWorkStatus,
    fetchSuggestions: fetchNextWorkSuggestions,
    submitFeedback: submitNextWorkFeedback,
    dismissSuggestion: dismissNextWorkSuggestion,
    clearSuggestions: clearNextWorkSuggestions,
  } = useNextWorkSuggestions();

  const { profile: capacityProfile, fetchProfile: fetchCapacityProfile } = useCapacityProfile();

  const flags = useFeatureFlags();

  const [checkInOpenForId, setCheckInOpenForId] = useState<string | null>(null);

  const {
    entries: checkInEntries,
    loading: checkInLoading,
    error: checkInError,
    addCheckIn,
    fetchCheckIns,
    clearEntries: clearCheckInEntries,
  } = useCheckIn();

  // Load plan when week changes
  useEffect(() => {
    void fetchPlan(selectedWeek);
  }, [selectedWeek, fetchPlan]);

  // Load commits when plan is available
  useEffect(() => {
    if (plan) {
      resetCommits();
      void fetchCommits(plan.id);
      return;
    }
    resetCommits();
  }, [plan, fetchCommits, resetCommits]);

  // Load assignments when plan is available (only when useIssueBacklog flag is on)
  useEffect(() => {
    if (plan && flags.useIssueBacklog) {
      resetAssignments();
      void fetchAssignments(plan.id);
      return;
    }
    resetAssignments();
  }, [plan, flags.useIssueBacklog, fetchAssignments, resetAssignments]);

  // Load RCDO tree on mount
  useEffect(() => {
    void fetchRcdoTree();
  }, [fetchRcdoTree]);

  // Load next-work suggestions when the panel is available on a DRAFT plan.
  useEffect(() => {
    if (flags.suggestNextWork && plan?.state === PlanState.DRAFT) {
      void fetchNextWorkSuggestions(selectedWeek);
      return;
    }

    clearNextWorkSuggestions();
  }, [
    clearNextWorkSuggestions,
    fetchNextWorkSuggestions,
    flags.suggestNextWork,
    plan?.id,
    plan?.state,
    selectedWeek,
  ]);

  // Fetch capacity profile when plan is in DRAFT state
  useEffect(() => {
    if (plan?.state === PlanState.DRAFT) {
      void fetchCapacityProfile();
    }
  }, [plan?.state, fetchCapacityProfile]);

  // ── Overcommit computation ────────────────────────────────────────────────

  const adjustedTotal = commits.reduce((sum, c) => sum + (c.estimatedHours ?? 0), 0);
  const realisticCap = capacityProfile?.realisticWeeklyCap ?? null;

  let overcommitLevel: OvercommitLevel = "NONE";
  if (realisticCap !== null) {
    if (adjustedTotal > realisticCap * 1.2) {
      overcommitLevel = "HIGH";
    } else if (adjustedTotal > realisticCap) {
      overcommitLevel = "MODERATE";
    }
  }

  let overcommitMessage = "";
  if (overcommitLevel === "HIGH") {
    overcommitMessage = `You're significantly over your realistic weekly capacity. Consider deferring some commitments to avoid burnout.`;
  } else if (overcommitLevel === "MODERATE") {
    overcommitMessage = `You're slightly over your realistic weekly capacity. Review your commitments before locking.`;
  }

  // ── Event handlers ────────────────────────────────────────────────────────

  const handleWeekChange = useCallback((week: string) => {
    setSelectedWeek(week);
  }, []);

  const handleCreatePlan = useCallback(async () => {
    await createPlan(selectedWeek);
  }, [createPlan, selectedWeek]);

  const handleCreateCommit = useCallback(
    async (req: CreateCommitRequest): Promise<boolean> => {
      if (!plan) {
        return false;
      }
      const created = await createCommit(plan.id, req);
      return created !== null;
    },
    [plan, createCommit],
  );

  const handleUpdateCommit = useCallback(
    async (commitId: string, version: number, req: UpdateCommitRequest): Promise<boolean> => {
      const updated = await updateCommit(commitId, version, req);
      return updated !== null;
    },
    [updateCommit],
  );

  const handleRequestDeleteCommit = useCallback(async (commitId: string): Promise<boolean> => {
    setPendingConfirm({ type: "delete-commit", commitId });
    // Actual deletion happens via confirmation dialog; return false to keep editor open
    return false;
  }, []);

  const handleConfirmDeleteCommit = useCallback(
    async (commitId: string): Promise<boolean> => {
      const result = await deleteCommit(commitId);
      setPendingConfirm(null);
      return result;
    },
    [deleteCommit],
  );

  const handleRequestLock = useCallback(() => {
    if (flags.planQualityNudge && plan) {
      setShowQualityNudge(true);
      void checkQuality(plan.id);
    } else {
      setPendingConfirm({ type: "lock-plan" });
    }
  }, [flags.planQualityNudge, plan, checkQuality]);

  const handleConfirmLock = useCallback(async () => {
    setLifecycleAction("lock-plan");
    try {
      if (plan) {
        const result = await lockPlan(plan.id, plan.version);
        if (result) showToast("Plan locked successfully");
      }
      setPendingConfirm(null);
    } finally {
      setLifecycleAction(null);
    }
  }, [plan, lockPlan, showToast]);

  const handleStartReconciliation = useCallback(async () => {
    setLifecycleAction("start-reconciliation");
    try {
      if (plan) {
        const result = await startReconciliation(plan.id, plan.version);
        if (result) showToast("Reconciliation started");
      }
    } finally {
      setLifecycleAction(null);
    }
  }, [plan, startReconciliation, showToast]);

  const handleUpdateActual = useCallback(
    async (commitId: string, version: number, req: UpdateActualRequest) => {
      await updateActual(commitId, version, req);
      // Refresh commits after actual update for version bump
      if (plan) {
        await fetchCommits(plan.id);
      }
    },
    [updateActual, plan, fetchCommits],
  );

  const handleRequestSubmitReconciliation = useCallback(() => {
    setPendingConfirm({ type: "submit-reconciliation" });
  }, []);

  const handleConfirmSubmitReconciliation = useCallback(async () => {
    setLifecycleAction("submit-reconciliation");
    try {
      if (plan) {
        const result = await submitReconciliation(plan.id, plan.version);
        if (result) showToast("Reconciliation submitted");
      }
      setPendingConfirm(null);
    } finally {
      setLifecycleAction(null);
    }
  }, [plan, submitReconciliation, showToast]);

  const handleFetchAiDraft = useCallback(async () => {
    if (plan) {
      await fetchAiDraft(plan.id);
    }
  }, [plan, fetchAiDraft]);

  const handleApplyDraftItem = useCallback(
    async (item: ReconciliationDraftItem) => {
      const commit = commits.find((c) => c.id === item.commitId);
      if (commit) {
        await updateActual(commit.id, commit.version, {
          actualResult: item.suggestedActualResult,
          completionStatus: item.suggestedStatus as CompletionStatus,
          ...(item.suggestedDeltaReason ? { deltaReason: item.suggestedDeltaReason } : {}),
        });
        if (plan) {
          await fetchCommits(plan.id);
        }
      }
    },
    [commits, updateActual, plan, fetchCommits],
  );

  const handleCarryForward = useCallback(
    async (commitIds: string[]) => {
      setLifecycleAction("carry-forward");
      try {
        if (plan) {
          const result = await carryForward(plan.id, plan.version, commitIds);
          if (result) {
            showToast("Carry-forward complete");
            setShowCarryForward(false);
          }
        }
      } finally {
        setLifecycleAction(null);
      }
    },
    [plan, carryForward, showToast],
  );

  // ── Phase 6: Assignment / Backlog handlers ────────────────────────────────

  const handleAddFromBacklog = useCallback(
    async (issues: Issue[]) => {
      if (!plan) return;
      setAddingFromBacklog(true);
      try {
        await Promise.all(
          issues.map((issue) => createAssignment(selectedWeek, { issueId: issue.id })),
        );
        await Promise.all([
          fetchAssignments(plan.id),
          fetchCommits(plan.id),
        ]);
        setShowBacklogPicker(false);
      } finally {
        setAddingFromBacklog(false);
      }
    },
    [plan, selectedWeek, createAssignment, fetchAssignments, fetchCommits],
  );

  const handleRemoveAssignment = useCallback(
    async (assignmentId: string) => {
      if (!plan) return;
      const ok = await removeAssignment(selectedWeek, assignmentId);
      if (ok) {
        await fetchCommits(plan.id);
      }
    },
    [fetchCommits, plan, removeAssignment, selectedWeek],
  );

  const handleReleaseToBacklog = useCallback(
    async (issueId: string) => {
      if (!plan) return;
      const ok = await releaseToBacklog(issueId, plan.id);
      if (ok) {
        await Promise.all([
          fetchAssignments(plan.id),
          fetchCommits(plan.id),
        ]);
      }
    },
    [plan, releaseToBacklog, fetchAssignments, fetchCommits],
  );

  const handleCarryForwardAssignments = useCallback(
    async (assignmentIds: string[]) => {
      const nextWeekStart = getNextWeekStart(selectedWeek);

      await Promise.all(
        assignmentIds.map(async (assignmentId) => {
          const assignment = assignments.find((a) => a.id === assignmentId);
          if (!assignment) {
            return;
          }
          await createAssignment(nextWeekStart, {
            issueId: assignment.issueId,
            chessPriorityOverride: assignment.chessPriorityOverride ?? undefined,
          });
        }),
      );
    },
    [selectedWeek, assignments, createAssignment],
  );

  // ── End Phase 6 handlers ─────────────────────────────────────────────────

  const handleStartFromLastWeek = useCallback(async () => {
    const result = await draftFromHistory(selectedWeek);
    if (result) {
      // Refresh plan data now that the draft plan exists
      await fetchPlan(selectedWeek);
    }
  }, [draftFromHistory, fetchPlan, selectedWeek]);

  const resolveNextWorkOutcomeLabel = useCallback(
    (outcomeId: string): string | null => {
      for (const cry of rcdoTree) {
        for (const objective of cry.objectives) {
          for (const outcome of objective.outcomes) {
            if (outcome.id === outcomeId) {
              return `${cry.name} / ${objective.name} / ${outcome.name}`;
            }
          }
        }
      }
      return null;
    },
    [rcdoTree],
  );

  const getNextWorkDraftSourceTag = useCallback((suggestion: NextWorkSuggestion) => {
    switch (suggestion.source) {
      case "CARRY_FORWARD":
        return "draft_source:CARRIED_FORWARD";
      case "COVERAGE_GAP":
      default:
        return "draft_source:COVERAGE_GAP";
    }
  }, []);

  const handleNextWorkAccept = useCallback(
    async (suggestion: NextWorkSuggestion): Promise<boolean> => {
      if (!plan) {
        return false;
      }

      // Chess-aware safety net: downgrade priority if it would violate chess rules.
      // Backend should already handle this, but guard against stale suggestions.
      let chessPriority = suggestion.suggestedChessPriority;
      if (chessPriority) {
        const kingCount = commits.filter((c) => c.chessPriority === ChessPriority.KING).length;
        const queenCount = commits.filter((c) => c.chessPriority === ChessPriority.QUEEN).length;
        if (chessPriority === ChessPriority.KING && kingCount >= 1) {
          chessPriority = ChessPriority.ROOK;
        } else if (chessPriority === ChessPriority.QUEEN && queenCount >= 2) {
          chessPriority = ChessPriority.ROOK;
        }
      }

      const req: CreateCommitRequest = {
        title: suggestion.title,
        chessPriority,
        outcomeId: suggestion.suggestedOutcomeId ?? null,
        tags: [getNextWorkDraftSourceTag(suggestion)],
      };
      const created = await createCommit(plan.id, req);
      if (created) {
        dismissNextWorkSuggestion(suggestion.suggestionId);
      }
      return created !== null;
    },
    [plan, commits, createCommit, dismissNextWorkSuggestion, getNextWorkDraftSourceTag],
  );

  const handleNextWorkFeedback = useCallback(
    async (req: SuggestionFeedbackRequest): Promise<boolean> => {
      const ok = await submitNextWorkFeedback(req);
      if (ok) {
        dismissNextWorkSuggestion(req.suggestionId);
        return true;
      }

      showToast("Couldn't save suggestion feedback. Please try again.", "error");
      return false;
    },
    [submitNextWorkFeedback, dismissNextWorkSuggestion, showToast],
  );

  const handleNextWorkRefresh = useCallback(() => {
    void fetchNextWorkSuggestions(selectedWeek);
  }, [fetchNextWorkSuggestions, selectedWeek]);

  const buildQuickUpdateCommitments = useCallback(
    (): QuickUpdateCommitment[] =>
      commits.map((commit) => ({
        id: commit.id,
        title: commit.title,
        category: commit.category,
        chessPriority: commit.chessPriority,
        outcomeName: commit.snapshotOutcomeName,
        lastCheckInStatus: null,
        lastCheckInNote: null,
        lastCheckInDaysAgo: 0,
      })),
    [commits],
  );

  const handleOpenCheckIn = useCallback(
    (commitId: string) => {
      setCheckInOpenForId(commitId);
      clearCheckInEntries();
      void fetchCheckIns(commitId);
    },
    [fetchCheckIns, clearCheckInEntries],
  );

  const handleCloseCheckIn = useCallback(() => {
    setCheckInOpenForId(null);
    clearCheckInEntries();
  }, [clearCheckInEntries]);

  const handleSubmitCheckIn = useCallback(
    async (commitId: string, req: CheckInRequest): Promise<boolean> => {
      const entry = await addCheckIn(commitId, req);
      return entry !== null;
    },
    [addCheckIn],
  );

  const loading = planLoading || commitsLoading || assignmentsLoading;
  const lifecycleLoading = lifecycleAction !== null;
  const draftLoading = draftFromHistoryStatus === "loading";
  const error = planError ?? commitsError ?? rcdoError ?? draftFromHistoryError ?? assignmentsError;

  const clearError = useCallback(() => {
    clearPlanError();
    clearCommitsError();
    clearRcdoError();
    resetDraftFromHistory();
    clearAssignmentsError();
  }, [clearPlanError, clearCommitsError, clearRcdoError, resetDraftFromHistory, clearAssignmentsError]);

  return (
    <div data-testid="weekly-plan-page" className={styles.page}>

      {/* ── Page top bar: week selector (left) + plan state + actions (right) ── */}
      <div className={styles.pageTopBar}>
        <WeekSelector selectedWeek={selectedWeek} onWeekChange={handleWeekChange} />
        {plan && (
          <PlanHeader
            plan={plan}
            onLock={handleRequestLock}
            onStartReconciliation={handleStartReconciliation}
            onSubmitReconciliation={handleRequestSubmitReconciliation}
            onCarryForward={() => setShowCarryForward(true)}
            onQuickUpdate={flags.quickUpdate ? () => setShowQuickUpdate(true) : undefined}
            loading={lifecycleLoading}
            canSubmitReconciliation={commits.length > 0}
          />
        )}
      </div>

      <ErrorBanner message={error} onDismiss={clearError} />

      {loading && !plan && (
        <div data-testid="loading-indicator" className={styles.loading}>
          Loading…
        </div>
      )}

      {!loading && !plan && (
        <div data-testid="no-plan" className={styles.noPlan}>
          {isCreateAllowedForWeek(selectedWeek) && (
            <div className={styles.noPlanCard}>
              <p className={styles.noPlanHeading}>No plan for this week yet.</p>
              <p className={styles.noPlanDescription}>
                Create a fresh plan or start from your previous week’s commitments.
              </p>
              <div className={styles.noPlanActions}>
                <button
                  data-testid="create-plan-btn"
                  className={styles.noPlanButton}
                  onClick={handleCreatePlan}
                  disabled={draftLoading}
                >
                  Create Empty Plan
                </button>
                {flags.startMyWeek && (
                  <button
                    data-testid="start-from-last-week-btn"
                    className={styles.noPlanButtonPrimary}
                    onClick={() => void handleStartFromLastWeek()}
                    disabled={draftLoading}
                  >
                    {draftLoading ? "Building your plan…" : "Start from Last Week"}
                  </button>
                )}
              </div>
            </div>
          )}
          {isPastWeek(selectedWeek) && (
            <p data-testid="no-plan-past" className={styles.noPlanMuted}>
              No plan was created for this week.
            </p>
          )}
          {isFutureWeek(selectedWeek) && (
            <p data-testid="no-plan-future" className={styles.noPlanMuted}>
              Plans can only be created for the current or next week.
            </p>
          )}
        </div>
      )}

      {plan && (
        <GlassPanel className={styles.contentPanel}>
          <PlanSummaryStrip commits={commits} />

          {plan.state === PlanState.DRAFT && <ValidationPanel commits={commits} />}

          {flags.suggestNextWork && plan.state === PlanState.DRAFT && (
            <NextWorkSuggestionPanel
              suggestions={nextWorkSuggestions}
              status={nextWorkStatus}
              onAccept={handleNextWorkAccept}
              onFeedback={handleNextWorkFeedback}
              onRefresh={handleNextWorkRefresh}
              resolveOutcomeLabel={resolveNextWorkOutcomeLabel}
            />
          )}

          {plan.state === PlanState.DRAFT && (
            <OvercommitBanner
              level={overcommitLevel}
              message={overcommitMessage}
              adjustedTotal={adjustedTotal}
              realisticCap={realisticCap ?? 0}
            />
          )}

          <CommitList
            commits={commits}
            assignments={flags.useIssueBacklog ? assignments : []}
            onRemoveAssignment={flags.useIssueBacklog ? handleRemoveAssignment : undefined}
            onAddFromBacklog={flags.useIssueBacklog ? () => setShowBacklogPicker(true) : undefined}
            planState={plan.state}
            rcdoTree={rcdoTree}
            rcdoSearchResults={rcdoSearchResults}
            onRcdoSearch={searchRcdo}
            onRcdoClearSearch={clearRcdoSearch}
            onCreate={handleCreateCommit}
            onUpdate={handleUpdateCommit}
            onDelete={handleRequestDeleteCommit}
            aiSuggestions={aiSuggestions}
            aiSuggestStatus={aiSuggestStatus}
            onAiSuggestRequest={fetchAiSuggestions}
            onAiSuggestClear={clearAiSuggestions}
            checkIn={
              flags.dailyCheckIn
                ? {
                    openForId: checkInOpenForId,
                    entries: checkInEntries,
                    loading: checkInLoading,
                    error: checkInError,
                    onOpen: handleOpenCheckIn,
                    onClose: handleCloseCheckIn,
                    onSubmit: handleSubmitCheckIn,
                  }
                : undefined
            }
          />

          {plan.state === PlanState.RECONCILING && (
            <>
              <AiReconciliationDraft
                draftItems={aiDraftItems}
                draftStatus={aiDraftStatus}
                onFetchDraft={handleFetchAiDraft}
                onApplyDraft={handleApplyDraftItem}
              />
              <ReconciliationView
                commits={commits}
                assignments={flags.useIssueBacklog ? assignments : []}
                planState={plan.state}
                onUpdateActual={handleUpdateActual}
                onReleaseToBacklog={flags.useIssueBacklog ? handleReleaseToBacklog : undefined}
                onSubmit={handleRequestSubmitReconciliation}
                loading={loading}
              />
            </>
          )}

          {showCarryForward && plan.state === PlanState.RECONCILED && (
            <CarryForwardDialog
              commits={commits}
              assignments={flags.useIssueBacklog ? assignments : []}
              onCarryForward={handleCarryForward}
              onCarryForwardAssignments={flags.useIssueBacklog ? handleCarryForwardAssignments : undefined}
              onCancel={() => setShowCarryForward(false)}
              loading={lifecycleAction === "carry-forward"}
            />
          )}

          {showQuickUpdate && (
            <QuickUpdateFlow
              commitments={buildQuickUpdateCommitments()}
              planId={plan.id}
              onComplete={() => {
                setShowQuickUpdate(false);
                if (plan) void fetchCommits(plan.id);
              }}
              onClose={() => setShowQuickUpdate(false)}
            />
          )}

          <DigestPreferencesSection />
        </GlassPanel>
      )}
      {/* Phase 6: Backlog picker dialog */}
      {flags.useIssueBacklog && showBacklogPicker && (
        <BacklogPickerDialog
          weekStart={selectedWeek}
          onConfirm={handleAddFromBacklog}
          onCancel={() => setShowBacklogPicker(false)}
          loading={addingFromBacklog}
        />
      )}

      {/* Plan quality nudge — shown before lock confirm dialog when flag is enabled */}
      {showQualityNudge && (
        <PlanQualityNudge
          nudges={qualityNudges}
          status={qualityStatus}
          onLockAnyway={() => {
            setShowQualityNudge(false);
            clearQualityNudges();
            setPendingConfirm({ type: "lock-plan" });
          }}
          onReview={() => {
            setShowQualityNudge(false);
            clearQualityNudges();
          }}
        />
      )}

      {/* Confirmation dialogs */}
      {pendingConfirm?.type === "delete-commit" && (
        <ConfirmDialog
          title="Delete Commitment"
          message="Delete this commitment? This cannot be undone."
          confirmLabel="Delete"
          onConfirm={() => {
            void handleConfirmDeleteCommit(pendingConfirm.commitId);
          }}
          onCancel={() => setPendingConfirm(null)}
          loading={commitsLoading}
        />
      )}
      {pendingConfirm?.type === "lock-plan" && (
        <ConfirmDialog
          title="Lock Plan"
          message="Lock this plan? Once locked, you cannot add or remove commitments."
          confirmLabel="Lock"
          onConfirm={() => {
            void handleConfirmLock();
          }}
          onCancel={() => setPendingConfirm(null)}
          loading={lifecycleAction === "lock-plan"}
        />
      )}
      {pendingConfirm?.type === "submit-reconciliation" && (
        <ConfirmDialog
          title="Submit Reconciliation"
          message="Submit reconciliation? This will finalize your weekly report."
          confirmLabel="Submit"
          onConfirm={() => {
            void handleConfirmSubmitReconciliation();
          }}
          onCancel={() => setPendingConfirm(null)}
          loading={lifecycleAction === "submit-reconciliation"}
        />
      )}
    </div>
  );
};
