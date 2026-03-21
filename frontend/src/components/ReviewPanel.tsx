import React, { useState } from "react";
import { ReviewStatus } from "@weekly-commitments/contracts";
import type { WeeklyPlan, ReviewDecision } from "@weekly-commitments/contracts";
import styles from "./ReviewPanel.module.css";

export interface ReviewPanelProps {
  plan: WeeklyPlan;
  onSubmitReview: (decision: ReviewDecision, comments: string) => Promise<boolean>;
  loading: boolean;
}

/**
 * Manager review panel for approving or requesting changes on a plan.
 * Disables "Request Changes" if carry-forward has been executed (PRD §6).
 */
export const ReviewPanel: React.FC<ReviewPanelProps> = ({ plan, onSubmitReview, loading }) => {
  const [comments, setComments] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const canReview =
    plan.reviewStatus === ReviewStatus.REVIEW_PENDING || plan.reviewStatus === ReviewStatus.CHANGES_REQUESTED;
  const canRequestChanges = plan.carryForwardExecutedAt === null;
  const hasComments = comments.trim().length > 0;

  const handleSubmit = async (decision: ReviewDecision) => {
    if (!hasComments) {
      return;
    }
    const submittedSuccessfully = await onSubmitReview(decision, comments);
    if (submittedSuccessfully) {
      setSubmitted(true);
      setComments("");
    }
  };

  if (submitted) {
    return (
      <div data-testid="review-submitted" className={styles.submitted}>
        Review submitted successfully.
      </div>
    );
  }

  if (!canReview) {
    return (
      <div data-testid="review-panel" className={styles.panel}>
        <p className={styles.statusText}>
          Review status: <strong>{plan.reviewStatus}</strong>
        </p>
      </div>
    );
  }

  return (
    <div data-testid="review-panel" className={styles.panel}>
      <h4 className={styles.heading}>Manager Review</h4>
      <div className={styles.fieldRow}>
        <label htmlFor="review-comments" className={styles.label}>
          Comments:
        </label>
        <textarea
          id="review-comments"
          data-testid="review-comments"
          value={comments}
          onChange={(e) => setComments(e.target.value)}
          rows={3}
          className={styles.textarea}
          placeholder="Add your review comments..."
        />
      </div>
      <div className={styles.actionRow}>
        <button
          type="button"
          data-testid="approve-btn"
          onClick={() => {
            void handleSubmit("APPROVED");
          }}
          disabled={loading || !hasComments}
          className={`${styles.btn} ${styles.approveBtn}`}
        >
          Approve
        </button>
        <button
          type="button"
          data-testid="request-changes-btn"
          onClick={() => {
            void handleSubmit("CHANGES_REQUESTED");
          }}
          disabled={loading || !hasComments || !canRequestChanges}
          title={
            canRequestChanges
              ? "Request changes to reconciliation"
              : "Carry-forward already executed. Add comments for next week's planning."
          }
          className={`${styles.btn} ${canRequestChanges ? styles.requestChangesBtn : styles.requestChangesBtnMuted}`}
        >
          Request Changes
        </button>
      </div>
      {!canRequestChanges && (
        <p data-testid="carry-forward-warning" className={styles.warning}>
          Carry-forward already executed. Add comments for next week&apos;s planning.
        </p>
      )}
    </div>
  );
};
