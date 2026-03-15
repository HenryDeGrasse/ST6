import React from "react";
import type { WeeklyPlan } from "@weekly-commitments/contracts";
import { PlanState, ReviewStatus } from "@weekly-commitments/contracts";

export interface PlanHeaderProps {
  plan: WeeklyPlan;
  onLock: () => void;
  onStartReconciliation: () => void;
  onSubmitReconciliation: () => void;
  onCarryForward: () => void;
  loading?: boolean;
  canSubmitReconciliation?: boolean;
}

const STATE_LABELS: Record<PlanState, string> = {
  [PlanState.DRAFT]: "📝 Draft",
  [PlanState.LOCKED]: "🔒 Locked",
  [PlanState.RECONCILING]: "🔄 Reconciling",
  [PlanState.RECONCILED]: "✅ Reconciled",
  [PlanState.CARRY_FORWARD]: "↩ Carry Forward",
};

const REVIEW_LABELS: Record<ReviewStatus, string> = {
  [ReviewStatus.REVIEW_NOT_APPLICABLE]: "",
  [ReviewStatus.REVIEW_PENDING]: "⏳ Review pending",
  [ReviewStatus.CHANGES_REQUESTED]: "🔁 Changes requested",
  [ReviewStatus.APPROVED]: "✅ Approved",
};

/**
 * Plan status header with lifecycle action buttons.
 * Shows current state, review status, and the next available action.
 */
export const PlanHeader: React.FC<PlanHeaderProps> = ({
  plan,
  onLock,
  onStartReconciliation,
  onSubmitReconciliation,
  onCarryForward,
  loading = false,
  canSubmitReconciliation = false,
}) => {
  const reviewLabel = REVIEW_LABELS[plan.reviewStatus];

  return (
    <div
      data-testid="plan-header"
      style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        padding: "0.75rem",
        background: "#f8f9fa",
        borderRadius: "4px",
        marginBottom: "1rem",
      }}
    >
      <div>
        <span data-testid="plan-state" style={{ fontWeight: 600, fontSize: "1.1rem" }}>
          {STATE_LABELS[plan.state]}
        </span>
        {reviewLabel && (
          <span data-testid="plan-review-status" style={{ marginLeft: "1rem", color: "#555" }}>
            {reviewLabel}
          </span>
        )}
        {plan.lockType === "LATE_LOCK" && (
          <span data-testid="plan-late-lock" style={{ marginLeft: "0.5rem", color: "#e65100", fontSize: "0.85rem" }}>
            (Late lock)
          </span>
        )}
      </div>

      <div style={{ display: "flex", gap: "0.5rem" }}>
        {plan.state === PlanState.DRAFT && (
          <button data-testid="lock-btn" onClick={onLock} disabled={loading}>
            {loading ? "⏳ Locking…" : "🔒 Lock Plan"}
          </button>
        )}
        {plan.state === PlanState.LOCKED && (
          <button data-testid="start-reconciliation-btn" onClick={onStartReconciliation} disabled={loading}>
            {loading ? "⏳ Starting…" : "🔄 Start Reconciliation"}
          </button>
        )}
        {plan.state === PlanState.RECONCILING && (
          <button
            data-testid="submit-reconciliation-btn"
            onClick={onSubmitReconciliation}
            disabled={loading || !canSubmitReconciliation}
          >
            {loading ? "⏳ Submitting…" : "✅ Submit Reconciliation"}
          </button>
        )}
        {plan.state === PlanState.RECONCILED && (
          <button data-testid="carry-forward-btn" onClick={onCarryForward} disabled={loading}>
            {loading ? "⏳ Carrying…" : "↩ Carry Forward"}
          </button>
        )}
      </div>
    </div>
  );
};
