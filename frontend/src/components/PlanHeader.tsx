import React from "react";
import type { WeeklyPlan } from "@weekly-commitments/contracts";
import { PlanState, ReviewStatus } from "@weekly-commitments/contracts";
import { StatusIcon } from "./icons/index.js";
import type { StatusIconName } from "./icons/index.js";
import styles from "./PlanHeader.module.css";

export interface PlanHeaderProps {
  plan: WeeklyPlan;
  onLock: () => void;
  onStartReconciliation: () => void;
  onSubmitReconciliation: () => void;
  onCarryForward: () => void;
  loading?: boolean;
  canSubmitReconciliation?: boolean;
}

// ─── State badge config ────────────────────────────────────────────────────

interface StateConfig {
  icon: StatusIconName;
  text: string;
  /** CSS module class for the badge colour variant */
  badgeClass: string;
}

const STATE_CONFIG: Record<PlanState, StateConfig> = {
  [PlanState.DRAFT]: {
    icon: "pencil",
    text: "Draft",
    badgeClass: styles.stateDraft,
  },
  [PlanState.LOCKED]: {
    icon: "lock",
    text: "Locked",
    badgeClass: styles.stateLocked,
  },
  [PlanState.RECONCILING]: {
    icon: "arrows",
    text: "Reconciling",
    badgeClass: styles.stateReconciling,
  },
  [PlanState.RECONCILED]: {
    icon: "check",
    text: "Reconciled",
    badgeClass: styles.stateReconciled,
  },
  [PlanState.CARRY_FORWARD]: {
    icon: "return-arrow",
    text: "Carry Forward",
    badgeClass: styles.stateCarryForward,
  },
};

// ─── Review status config ──────────────────────────────────────────────────

interface ReviewConfig {
  icon: StatusIconName;
  text: string;
}

const REVIEW_CONFIG: Partial<Record<ReviewStatus, ReviewConfig>> = {
  [ReviewStatus.REVIEW_PENDING]: { icon: "loading", text: "Review pending" },
  [ReviewStatus.CHANGES_REQUESTED]: { icon: "arrows", text: "Changes requested" },
  [ReviewStatus.APPROVED]: { icon: "check", text: "Approved" },
};

// ─── Component ─────────────────────────────────────────────────────────────

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
  const stateConfig = STATE_CONFIG[plan.state];
  const reviewConfig = REVIEW_CONFIG[plan.reviewStatus];

  return (
    <div data-testid="plan-header" className={styles.header}>
      {/* ── Left: status info ── */}
      <div className={styles.info}>
        <span data-testid="plan-state" className={[styles.stateBadge, stateConfig.badgeClass].join(" ")}>
          <StatusIcon icon={stateConfig.icon} size={14} />
          {stateConfig.text}
        </span>

        {reviewConfig && (
          <span data-testid="plan-review-status" className={styles.reviewStatus}>
            <StatusIcon icon={reviewConfig.icon} size={14} />
            {reviewConfig.text}
          </span>
        )}

        {plan.lockType === "LATE_LOCK" && (
          <span data-testid="plan-late-lock" className={styles.lateLock}>
            (Late lock)
          </span>
        )}
      </div>

      {/* ── Right: action buttons ── */}
      <div className={styles.actions}>
        {plan.state === PlanState.DRAFT && (
          <button data-testid="lock-btn" className={styles.ctaButton} onClick={onLock} disabled={loading}>
            {loading ? (
              <>
                <StatusIcon icon="loading" size={14} className={styles.loadingIcon} />
                Locking…
              </>
            ) : (
              <>
                <StatusIcon icon="lock" size={14} />
                Lock Plan
              </>
            )}
          </button>
        )}

        {plan.state === PlanState.LOCKED && (
          <button
            data-testid="start-reconciliation-btn"
            className={styles.ctaButton}
            onClick={onStartReconciliation}
            disabled={loading}
          >
            {loading ? (
              <>
                <StatusIcon icon="loading" size={14} className={styles.loadingIcon} />
                Starting…
              </>
            ) : (
              <>
                <StatusIcon icon="arrows" size={14} />
                Start Reconciliation
              </>
            )}
          </button>
        )}

        {plan.state === PlanState.RECONCILING && (
          <button
            data-testid="submit-reconciliation-btn"
            className={styles.ctaButton}
            onClick={onSubmitReconciliation}
            disabled={loading || !canSubmitReconciliation}
          >
            {loading ? (
              <>
                <StatusIcon icon="loading" size={14} className={styles.loadingIcon} />
                Submitting…
              </>
            ) : (
              <>
                <StatusIcon icon="check" size={14} />
                Submit Reconciliation
              </>
            )}
          </button>
        )}

        {plan.state === PlanState.RECONCILED && (
          <button
            data-testid="carry-forward-btn"
            className={styles.ctaButton}
            onClick={onCarryForward}
            disabled={loading}
          >
            {loading ? (
              <>
                <StatusIcon icon="loading" size={14} className={styles.loadingIcon} />
                Carrying…
              </>
            ) : (
              <>
                <StatusIcon icon="return-arrow" size={14} />
                Carry Forward
              </>
            )}
          </button>
        )}
      </div>
    </div>
  );
};
