import React from "react";
import type { WeeklyPlan, WeeklyCommit, ReviewDecision } from "@weekly-commitments/contracts";
import { PlanState, CompletionStatus } from "@weekly-commitments/contracts";
import { ReviewPanel } from "./ReviewPanel.js";

/** Plan states where actuals columns should be visible */
const ACTUALS_VISIBLE_STATES = new Set<string>([
  PlanState.RECONCILING,
  PlanState.RECONCILED,
  PlanState.CARRY_FORWARD,
]);

/** Background tint by completion status */
function completionRowStyle(status: CompletionStatus | undefined): React.CSSProperties {
  switch (status) {
    case CompletionStatus.DONE:
      return { backgroundColor: "rgba(34, 197, 94, 0.1)" }; // green tint
    case CompletionStatus.PARTIALLY:
      return { backgroundColor: "rgba(245, 158, 11, 0.1)" }; // amber tint
    case CompletionStatus.NOT_DONE:
    case CompletionStatus.DROPPED:
      return { backgroundColor: "rgba(239, 68, 68, 0.1)" }; // red tint
    default:
      return {};
  }
}

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
        style={{
          position: "relative",
          padding: "1rem",
          borderRadius: "8px",
          minHeight: "18rem",
          overflow: "hidden",
          background: "#f8fafc",
          border: "1px solid #e5e7eb",
        }}
      >
        <div
          style={{
            position: "absolute",
            inset: 0,
            background: "rgba(255, 255, 255, 0.75)",
            backdropFilter: "blur(1px)",
          }}
        />
        <div style={{ position: "relative", display: "grid", gap: "0.75rem" }}>
          <div style={{ width: "10rem", height: "1rem", borderRadius: "999px", background: "#dbe3ea" }} />
          <div style={{ width: "16rem", height: "1.5rem", borderRadius: "999px", background: "#dbe3ea" }} />
          <div style={{ width: "100%", height: "7rem", borderRadius: "8px", background: "#e5e7eb" }} />
          <div style={{ width: "100%", height: "7rem", borderRadius: "8px", background: "#e5e7eb" }} />
          <div role="status" aria-live="polite" style={{ textAlign: "center", color: "#64748b", fontWeight: 500 }}>
            Loading plan…
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div data-testid="drilldown-error" style={{ padding: "1rem", color: "#dc2626" }}>
        {error}
        <button onClick={onBack} style={{ marginLeft: "1rem" }}>← Back</button>
      </div>
    );
  }

  if (!plan) {
    return (
      <div data-testid="drilldown-no-plan" style={{ padding: "1rem" }}>
        <p>This user has no plan for the selected week.</p>
        <button data-testid="drilldown-back-btn" onClick={onBack}>← Back to Dashboard</button>
      </div>
    );
  }

  const canReview =
    plan.state === PlanState.RECONCILED || plan.state === PlanState.CARRY_FORWARD;

  const showActuals = ACTUALS_VISIBLE_STATES.has(plan.state);

  return (
    <div data-testid="plan-drilldown" style={{ padding: "0.5rem" }}>
      <button data-testid="drilldown-back-btn" onClick={onBack} style={{ marginBottom: "1rem" }}>
        ← Back to Dashboard
      </button>

      <div style={{ marginBottom: "1rem" }}>
        <h3>Plan for {displayName ?? plan.ownerUserId}</h3>
        <p>
          Week: {plan.weekStartDate} · State: <strong>{plan.state}</strong> ·
          Review: <strong>{plan.reviewStatus}</strong>
          {plan.lockType && <> · Lock: <strong>{plan.lockType}</strong></>}
        </p>
      </div>

      <h4>Commits ({commits.length})</h4>
      {commits.length === 0 ? (
        <p style={{ color: "#888" }}>No commits in this plan.</p>
      ) : (
        <table data-testid="drilldown-commits" style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}>
          <thead>
            <tr style={{ borderBottom: "2px solid #ddd", textAlign: "left" }}>
              <th style={{ padding: "0.5rem" }}>Title</th>
              <th style={{ padding: "0.5rem" }}>Priority</th>
              <th style={{ padding: "0.5rem" }}>Category</th>
              <th style={{ padding: "0.5rem" }}>RCDO / Non-Strategic</th>
              {showActuals && (
                <>
                  <th style={{ padding: "0.5rem" }}>Completion Status</th>
                  <th style={{ padding: "0.5rem" }}>Actual Result</th>
                  <th style={{ padding: "0.5rem" }}>Delta Reason</th>
                </>
              )}
            </tr>
          </thead>
          <tbody>
            {commits.map((c) => (
              <tr
                key={c.id}
                data-testid={`drilldown-commit-${c.id}`}
                style={{
                  borderBottom: "1px solid #eee",
                  ...(showActuals ? completionRowStyle(c.actual?.completionStatus) : {}),
                }}
              >
                <td style={{ padding: "0.5rem" }}>
                  {c.title}
                  {c.carriedFromCommitId && (
                    <span style={{ marginLeft: "0.5rem", fontSize: "0.7rem", color: "#7c3aed" }}>
                      (carried)
                    </span>
                  )}
                </td>
                <td style={{ padding: "0.5rem" }}>{c.chessPriority ?? "—"}</td>
                <td style={{ padding: "0.5rem" }}>{c.category ?? "—"}</td>
                <td style={{ padding: "0.5rem" }}>
                  {c.snapshotOutcomeName ?? c.outcomeId ?? ""}
                  {c.nonStrategicReason && (
                    <span style={{ color: "#6b7280", fontStyle: "italic" }}>
                      Non-strategic: {c.nonStrategicReason}
                    </span>
                  )}
                </td>
                {showActuals && (
                  <>
                    <td style={{ padding: "0.5rem" }} data-testid={`actual-status-${c.id}`}>
                      {c.actual?.completionStatus ?? "—"}
                    </td>
                    <td style={{ padding: "0.5rem" }} data-testid={`actual-result-${c.id}`}>
                      {c.actual?.actualResult ?? "—"}
                    </td>
                    <td style={{ padding: "0.5rem" }} data-testid={`actual-delta-${c.id}`}>
                      {c.actual?.deltaReason ?? "—"}
                    </td>
                  </>
                )}
              </tr>
            ))}
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
