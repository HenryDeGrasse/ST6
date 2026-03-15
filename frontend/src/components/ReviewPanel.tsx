import React, { useState } from "react";
import type { WeeklyPlan, ReviewDecision } from "@weekly-commitments/contracts";

export interface ReviewPanelProps {
  plan: WeeklyPlan;
  onSubmitReview: (decision: ReviewDecision, comments: string) => Promise<void>;
  loading: boolean;
}

/**
 * Manager review panel for approving or requesting changes on a plan.
 * Disables "Request Changes" if carry-forward has been executed (PRD §6).
 */
export const ReviewPanel: React.FC<ReviewPanelProps> = ({
  plan,
  onSubmitReview,
  loading,
}) => {
  const [comments, setComments] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const canReview =
    plan.reviewStatus === "REVIEW_PENDING" ||
    plan.reviewStatus === "CHANGES_REQUESTED";

  const canRequestChanges = plan.carryForwardExecutedAt === null;

  const handleSubmit = async (decision: ReviewDecision) => {
    if (!comments.trim()) {
      return;
    }
    await onSubmitReview(decision, comments);
    setSubmitted(true);
    setComments("");
  };

  if (!canReview) {
    return (
      <div data-testid="review-panel" style={{ marginTop: "1rem", padding: "1rem", background: "#f9fafb", borderRadius: "8px" }}>
        <p>Review status: <strong>{plan.reviewStatus}</strong></p>
      </div>
    );
  }

  if (submitted) {
    return (
      <div data-testid="review-submitted" style={{ marginTop: "1rem", padding: "1rem", background: "#d1fae5", borderRadius: "8px" }}>
        Review submitted successfully.
      </div>
    );
  }

  return (
    <div data-testid="review-panel" style={{ marginTop: "1rem", padding: "1rem", background: "#f9fafb", borderRadius: "8px" }}>
      <h4>Manager Review</h4>
      <div style={{ marginBottom: "0.5rem" }}>
        <label htmlFor="review-comments">Comments:</label>
        <textarea
          id="review-comments"
          data-testid="review-comments"
          value={comments}
          onChange={(e) => setComments(e.target.value)}
          rows={3}
          style={{ width: "100%", marginTop: "0.25rem" }}
          placeholder="Add your review comments..."
        />
      </div>
      <div style={{ display: "flex", gap: "0.5rem" }}>
        <button
          data-testid="approve-btn"
          onClick={() => handleSubmit("APPROVED")}
          disabled={loading || !comments.trim()}
          style={{
            background: "#059669",
            color: "white",
            padding: "0.5rem 1rem",
            border: "none",
            borderRadius: "4px",
            cursor: loading ? "wait" : "pointer",
          }}
        >
          Approve
        </button>
        <button
          data-testid="request-changes-btn"
          onClick={() => handleSubmit("CHANGES_REQUESTED")}
          disabled={loading || !comments.trim() || !canRequestChanges}
          title={
            canRequestChanges
              ? "Request changes to reconciliation"
              : "Carry-forward already executed. Add comments for next week's planning."
          }
          style={{
            background: canRequestChanges ? "#dc2626" : "#9ca3af",
            color: "white",
            padding: "0.5rem 1rem",
            border: "none",
            borderRadius: "4px",
            cursor: !canRequestChanges || loading ? "not-allowed" : "pointer",
          }}
        >
          Request Changes
        </button>
      </div>
      {!canRequestChanges && (
        <p data-testid="carry-forward-warning" style={{ color: "#92400e", fontSize: "0.875rem", marginTop: "0.5rem" }}>
          Carry-forward already executed. Add comments for next week&apos;s planning.
        </p>
      )}
    </div>
  );
};
