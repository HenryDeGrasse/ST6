import React from "react";
import type { WeeklyPlan, WeeklyCommit, ReviewDecision } from "@weekly-commitments/contracts";
import { PlanState, CompletionStatus } from "@weekly-commitments/contracts";
import { ReviewPanel } from "./ReviewPanel.js";
import styles from "./PlanDrillDown.module.css";

/** Plan states where actuals columns should be visible */
const ACTUALS_VISIBLE_STATES = new Set<PlanState>([
  PlanState.RECONCILING,
  PlanState.RECONCILED,
  PlanState.CARRY_FORWARD,
]);

/** CSS module class by completion status (replaces completionRowStyle inline function) */
const COMPLETION_ROW_CLASS: Record<CompletionStatus, string> = {
  [CompletionStatus.DONE]: styles.rowDone,
  [CompletionStatus.PARTIALLY]: styles.rowPartially,
  [CompletionStatus.NOT_DONE]: styles.rowNotDone,
  [CompletionStatus.DROPPED]: styles.rowDropped,
};

export interface PlanDrillDownProps {
  plan: WeeklyPlan | null;
  commits: WeeklyCommit[];
  loading: boolean;
  error: string | null;
  displayName?: string | null;
  onSubmitReview: (decision: ReviewDecision, comments: string) => Promise<void>;
  onBack: () => void;
}

/**
 * Drill-down view showing a direct report's plan and commits.
 * Managers can review from here if the plan is RECONCILED.
 */
export const PlanDrillDown: React.FC<PlanDrillDownProps> = ({
  plan,
  commits,
  loading,
  error,
  displayName,
  onSubmitReview,
  onBack,
}) => {
  if (loading) {
    return (
      <div
        data-testid="drilldown-loading"
        aria-busy="true"
        className={styles.skeletonWrapper}
      >
        <div className={styles.skeletonOverlay} />
        <div className={styles.skeletonContent}>
          <div className={`${styles.skeletonBar} ${styles.skeletonBarSm}`} />
          <div className={`${styles.skeletonBar} ${styles.skeletonBarMd}`} />
          <div className={styles.skeletonBlock} />
          <div className={styles.skeletonBlock} />
          <div role="status" aria-live="polite" className={styles.skeletonLoadingText}>
            Loading plan…
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div data-testid="drilldown-error" className={styles.errorWrapper}>
        <p className={styles.errorText}>{error}</p>
        <button onClick={onBack} className={styles.errorBackBtn}>← Back</button>
      </div>
    );
  }

  if (!plan) {
    return (
      <div data-testid="drilldown-no-plan" className={styles.noPlanWrapper}>
        <p className={styles.noPlanText}>This user has no plan for the selected week.</p>
        <button data-testid="drilldown-back-btn" onClick={onBack} className={styles.noPlanBackBtn}>
          ← Back to Dashboard
        </button>
      </div>
    );
  }

  const canReview =
    plan.state === PlanState.RECONCILED || plan.state === PlanState.CARRY_FORWARD;

  const showActuals = ACTUALS_VISIBLE_STATES.has(plan.state);

  return (
    <div data-testid="plan-drilldown" className={styles.container}>
      <button data-testid="drilldown-back-btn" onClick={onBack} className={styles.backBtn}>
        ← Back to Dashboard
      </button>

      <div className={styles.planHeader}>
        <h3 className={styles.planHeading}>Plan for {displayName ?? plan.ownerUserId}</h3>
        <p className={styles.planMeta}>
          Week: {plan.weekStartDate} · State: <strong>{plan.state}</strong> ·
          Review: <strong>{plan.reviewStatus}</strong>
          {plan.lockType && <> · Lock: <strong>{plan.lockType}</strong></>}
        </p>
      </div>

      <h4 className={styles.sectionTitle}>Commits ({commits.length})</h4>
      {commits.length === 0 ? (
        <p className={styles.noCommits}>No commits in this plan.</p>
      ) : (
        <table data-testid="drilldown-commits" className={styles.table}>
          <thead className={styles.thead}>
            <tr>
              <th className={styles.th}>Title</th>
              <th className={styles.th}>Priority</th>
              <th className={styles.th}>Category</th>
              <th className={styles.th}>RCDO / Non-Strategic</th>
              {showActuals && (
                <>
                  <th className={styles.th}>Completion Status</th>
                  <th className={styles.th}>Actual Result</th>
                  <th className={styles.th}>Delta Reason</th>
                </>
              )}
            </tr>
          </thead>
          <tbody>
            {commits.map((c) => {
              const statusClass =
                showActuals && c.actual?.completionStatus
                  ? COMPLETION_ROW_CLASS[c.actual.completionStatus]
                  : "";
              return (
                <tr
                  key={c.id}
                  data-testid={`drilldown-commit-${c.id}`}
                  className={`${styles.tr} ${statusClass}`.trim()}
                >
                  <td className={styles.td}>
                    {c.title}
                    {c.carriedFromCommitId && (
                      <span className={styles.carriedBadge}>(carried)</span>
                    )}
                  </td>
                  <td className={styles.td}>{c.chessPriority ?? "—"}</td>
                  <td className={styles.td}>{c.category ?? "—"}</td>
                  <td className={styles.td}>
                    {c.snapshotOutcomeName ?? c.outcomeId ?? ""}
                    {c.nonStrategicReason && (
                      <span className={styles.nonStrategicText}>
                        Non-strategic: {c.nonStrategicReason}
                      </span>
                    )}
                  </td>
                  {showActuals && (
                    <>
                      <td className={styles.td} data-testid={`actual-status-${c.id}`}>
                        {c.actual?.completionStatus ?? "—"}
                      </td>
                      <td className={styles.td} data-testid={`actual-result-${c.id}`}>
                        {c.actual?.actualResult ?? "—"}
                      </td>
                      <td className={styles.td} data-testid={`actual-delta-${c.id}`}>
                        {c.actual?.deltaReason ?? "—"}
                      </td>
                    </>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {canReview && (
        <ReviewPanel
          plan={plan}
          onSubmitReview={onSubmitReview}
          loading={loading}
        />
      )}
    </div>
  );
};
