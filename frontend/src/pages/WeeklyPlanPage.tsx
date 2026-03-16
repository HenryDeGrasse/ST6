import React, { useEffect, useState, useCallback } from "react";
import type {
  CreateCommitRequest,
  UpdateCommitRequest,
  UpdateActualRequest,
  ReconciliationDraftItem,
} from "@weekly-commitments/contracts";
import { PlanState, CompletionStatus } from "@weekly-commitments/contracts";
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
import { usePlan } from "../hooks/usePlan.js";
import { useCommits } from "../hooks/useCommits.js";
import { useRcdo } from "../hooks/useRcdo.js";
import { useAiSuggestions } from "../hooks/useAiSuggestions.js";
import { getWeekStart, isPastWeek, isFutureWeek, isCreateAllowedForWeek } from "../utils/week.js";
import { useToast } from "../context/ToastContext.js";
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
  const [pendingConfirm, setPendingConfirm] = useState<ConfirmAction>(null);
  const [lifecycleAction, setLifecycleAction] = useState<LifecycleAction>(null);

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

  // Load RCDO tree on mount
  useEffect(() => {
    void fetchRcdoTree();
  }, [fetchRcdoTree]);

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

  const handleRequestDeleteCommit = useCallback(
    async (commitId: string): Promise<boolean> => {
      setPendingConfirm({ type: "delete-commit", commitId });
      // Actual deletion happens via confirmation dialog; return false to keep editor open
      return false;
    },
    [],
  );

  const handleConfirmDeleteCommit = useCallback(
    async (commitId: string): Promise<boolean> => {
      const result = await deleteCommit(commitId);
      setPendingConfirm(null);
      return result;
    },
    [deleteCommit],
  );

  const handleRequestLock = useCallback(() => {
    setPendingConfirm({ type: "lock-plan" });
  }, []);

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

  const loading = planLoading || commitsLoading;
  const lifecycleLoading = lifecycleAction !== null;
  const error = planError ?? commitsError ?? rcdoError;

  const clearError = useCallback(() => {
    clearPlanError();
    clearCommitsError();
    clearRcdoError();
  }, [clearPlanError, clearCommitsError, clearRcdoError]);

  return (
    <div data-testid="weekly-plan-page" className={styles.page}>
      <span className="wc-volume-label" aria-hidden="true">Volume I</span>
      <h2 className={styles.heading}>Weekly Commitments</h2>
      <div className="wc-ornate-divider" role="separator" aria-hidden="true" />

      <WeekSelector selectedWeek={selectedWeek} onWeekChange={handleWeekChange} />

      <ErrorBanner message={error} onDismiss={clearError} />

      {loading && !plan && (
        <div data-testid="loading-indicator" className={styles.loading}>
          Loading…
        </div>
      )}

      {!loading && !plan && (
        <div data-testid="no-plan" className={styles.noPlan}>
          {isCreateAllowedForWeek(selectedWeek) && (
            <>
              <p>No plan for this week yet.</p>
              <button data-testid="create-plan-btn" onClick={handleCreatePlan}>
                Create Weekly Plan
              </button>
            </>
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
          <PlanHeader
            plan={plan}
            onLock={handleRequestLock}
            onStartReconciliation={handleStartReconciliation}
            onSubmitReconciliation={handleRequestSubmitReconciliation}
            onCarryForward={() => setShowCarryForward(true)}
            loading={lifecycleLoading}
            canSubmitReconciliation={commits.length > 0}
          />

          <PlanSummaryStrip commits={commits} />

          {plan.state === PlanState.DRAFT && <ValidationPanel commits={commits} />}

          <CommitList
            commits={commits}
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
                planState={plan.state}
                onUpdateActual={handleUpdateActual}
                onSubmit={handleRequestSubmitReconciliation}
                loading={loading}
              />
            </>
          )}

          {showCarryForward && plan.state === PlanState.RECONCILED && (
            <CarryForwardDialog
              commits={commits}
              onCarryForward={handleCarryForward}
              onCancel={() => setShowCarryForward(false)}
              loading={lifecycleAction === "carry-forward"}
            />
          )}
        </GlassPanel>
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
