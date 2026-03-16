import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReviewPanel } from "../components/ReviewPanel.js";
import { PlanState, ReviewStatus, LockType } from "@weekly-commitments/contracts";
import type { WeeklyPlan } from "@weekly-commitments/contracts";

describe("ReviewPanel", () => {
  const basePlan: WeeklyPlan = {
    id: "plan-1",
    orgId: "org-1",
    ownerUserId: "user-1",
    weekStartDate: "2026-03-09",
    state: PlanState.RECONCILED,
    reviewStatus: ReviewStatus.REVIEW_PENDING,
    lockType: LockType.ON_TIME,
    lockedAt: "2026-03-09T10:00:00Z",
    carryForwardExecutedAt: null,
    version: 3,
    createdAt: "2026-03-09T08:00:00Z",
    updatedAt: "2026-03-13T17:00:00Z",
  };

  it("renders review form for REVIEW_PENDING plan", async () => {
    render(
      <ReviewPanel
        plan={basePlan}
        onSubmitReview={vi.fn().mockResolvedValue(true)}
        loading={false}
      />,
    );
    expect(screen.getByTestId("review-panel")).toBeInTheDocument();
    expect(screen.getByTestId("approve-btn")).toBeInTheDocument();
    expect(screen.getByTestId("request-changes-btn")).toBeInTheDocument();
    // Both buttons are disabled until comments are filled in
    expect(screen.getByTestId("request-changes-btn")).toBeDisabled();
    // Fill in comments, then request-changes should be enabled
    await userEvent.type(screen.getByTestId("review-comments"), "Some feedback");
    expect(screen.getByTestId("request-changes-btn")).toBeEnabled();
  });

  it("disables Request Changes when carry-forward executed", () => {
    const cfPlan: WeeklyPlan = {
      ...basePlan,
      state: PlanState.CARRY_FORWARD,
      carryForwardExecutedAt: "2026-03-14T09:00:00Z",
    };
    render(
      <ReviewPanel plan={cfPlan} onSubmitReview={vi.fn().mockResolvedValue(true)} loading={false} />,
    );
    expect(screen.getByTestId("request-changes-btn")).toBeDisabled();
    expect(screen.getByTestId("carry-forward-warning")).toBeInTheDocument();
  });

  it("calls onSubmitReview with APPROVED when approved", async () => {
    const onSubmit = vi.fn().mockResolvedValue(true);
    render(
      <ReviewPanel plan={basePlan} onSubmitReview={onSubmit} loading={false} />,
    );

    await userEvent.type(screen.getByTestId("review-comments"), "Looks good");
    await userEvent.click(screen.getByTestId("approve-btn"));

    expect(onSubmit).toHaveBeenCalledWith("APPROVED", "Looks good");
    expect(screen.getByTestId("review-submitted")).toBeInTheDocument();
  });

  it("keeps the review form visible when submission fails", async () => {
    const onSubmit = vi.fn().mockResolvedValue(false);
    render(
      <ReviewPanel plan={basePlan} onSubmitReview={onSubmit} loading={false} />,
    );

    await userEvent.type(screen.getByTestId("review-comments"), "Needs more detail");
    await userEvent.click(screen.getByTestId("request-changes-btn"));

    expect(onSubmit).toHaveBeenCalledWith("CHANGES_REQUESTED", "Needs more detail");
    expect(screen.getByTestId("review-panel")).toBeInTheDocument();
    expect(screen.queryByTestId("review-submitted")).not.toBeInTheDocument();
  });

  it("does not render form for APPROVED plan", () => {
    const approvedPlan: WeeklyPlan = {
      ...basePlan,
      reviewStatus: ReviewStatus.APPROVED,
    };
    render(
      <ReviewPanel
        plan={approvedPlan}
        onSubmitReview={vi.fn().mockResolvedValue(true)}
        loading={false}
      />,
    );
    expect(screen.queryByTestId("approve-btn")).not.toBeInTheDocument();
    expect(screen.getByText(/APPROVED/)).toBeInTheDocument();
  });
});
