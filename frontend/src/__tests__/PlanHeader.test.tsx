import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PlanHeader } from "../components/PlanHeader.js";
import type { WeeklyPlan } from "@weekly-commitments/contracts";
import { PlanState, ReviewStatus, LockType } from "@weekly-commitments/contracts";

function makePlan(overrides: Partial<WeeklyPlan> = {}): WeeklyPlan {
  return {
    id: "plan-1",
    orgId: "org-1",
    ownerUserId: "user-1",
    weekStartDate: "2026-03-09",
    state: PlanState.DRAFT,
    reviewStatus: ReviewStatus.REVIEW_NOT_APPLICABLE,
    lockType: null,
    lockedAt: null,
    carryForwardExecutedAt: null,
    version: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides,
  };
}

const noop = vi.fn();

describe("PlanHeader", () => {
  it("shows Lock button in DRAFT state", () => {
    render(
      <PlanHeader
        plan={makePlan()}
        onLock={noop}
        onStartReconciliation={noop}
        onSubmitReconciliation={noop}
        onCarryForward={noop}
      />,
    );
    expect(screen.getByTestId("plan-state")).toHaveTextContent("Draft");
    expect(screen.getByTestId("lock-btn")).toBeInTheDocument();
  });

  it("shows Start Reconciliation button in LOCKED state", () => {
    render(
      <PlanHeader
        plan={makePlan({ state: PlanState.LOCKED })}
        onLock={noop}
        onStartReconciliation={noop}
        onSubmitReconciliation={noop}
        onCarryForward={noop}
      />,
    );
    expect(screen.getByTestId("plan-state")).toHaveTextContent("Locked");
    expect(screen.getByTestId("start-reconciliation-btn")).toBeInTheDocument();
  });

  it("shows Submit Reconciliation button in RECONCILING state", () => {
    render(
      <PlanHeader
        plan={makePlan({ state: PlanState.RECONCILING })}
        onLock={noop}
        onStartReconciliation={noop}
        onSubmitReconciliation={noop}
        onCarryForward={noop}
        canSubmitReconciliation={true}
      />,
    );
    expect(screen.getByTestId("submit-reconciliation-btn")).toBeInTheDocument();
  });

  it("shows Carry Forward button in RECONCILED state", () => {
    render(
      <PlanHeader
        plan={makePlan({ state: PlanState.RECONCILED })}
        onLock={noop}
        onStartReconciliation={noop}
        onSubmitReconciliation={noop}
        onCarryForward={noop}
      />,
    );
    expect(screen.getByTestId("carry-forward-btn")).toBeInTheDocument();
  });

  it("shows review status when applicable", () => {
    render(
      <PlanHeader
        plan={makePlan({ state: PlanState.RECONCILED, reviewStatus: ReviewStatus.REVIEW_PENDING })}
        onLock={noop}
        onStartReconciliation={noop}
        onSubmitReconciliation={noop}
        onCarryForward={noop}
      />,
    );
    expect(screen.getByTestId("plan-review-status")).toHaveTextContent("Review pending");
  });

  it("shows late lock badge", () => {
    render(
      <PlanHeader
        plan={makePlan({ state: PlanState.RECONCILING, lockType: LockType.LATE_LOCK })}
        onLock={noop}
        onStartReconciliation={noop}
        onSubmitReconciliation={noop}
        onCarryForward={noop}
      />,
    );
    expect(screen.getByTestId("plan-late-lock")).toHaveTextContent("Late lock");
  });

  it("calls onLock when Lock button is clicked", () => {
    const onLock = vi.fn();
    render(
      <PlanHeader
        plan={makePlan()}
        onLock={onLock}
        onStartReconciliation={noop}
        onSubmitReconciliation={noop}
        onCarryForward={noop}
      />,
    );
    fireEvent.click(screen.getByTestId("lock-btn"));
    expect(onLock).toHaveBeenCalledOnce();
  });

  it("shows loading text on Lock button when loading=true", () => {
    render(
      <PlanHeader
        plan={makePlan()}
        onLock={noop}
        onStartReconciliation={noop}
        onSubmitReconciliation={noop}
        onCarryForward={noop}
        loading={true}
      />,
    );
    const btn = screen.getByTestId("lock-btn");
    expect(btn).toBeDisabled();
    expect(btn).toHaveTextContent("Locking");
  });

  it("shows loading text on Start Reconciliation button when loading=true", () => {
    render(
      <PlanHeader
        plan={makePlan({ state: PlanState.LOCKED })}
        onLock={noop}
        onStartReconciliation={noop}
        onSubmitReconciliation={noop}
        onCarryForward={noop}
        loading={true}
      />,
    );
    const btn = screen.getByTestId("start-reconciliation-btn");
    expect(btn).toBeDisabled();
    expect(btn).toHaveTextContent("Starting");
  });

  it("shows loading text on Submit Reconciliation button when loading=true", () => {
    render(
      <PlanHeader
        plan={makePlan({ state: PlanState.RECONCILING })}
        onLock={noop}
        onStartReconciliation={noop}
        onSubmitReconciliation={noop}
        onCarryForward={noop}
        loading={true}
        canSubmitReconciliation={true}
      />,
    );
    const btn = screen.getByTestId("submit-reconciliation-btn");
    expect(btn).toBeDisabled();
    expect(btn).toHaveTextContent("Submitting");
  });

  it("shows loading text on Carry Forward button when loading=true", () => {
    render(
      <PlanHeader
        plan={makePlan({ state: PlanState.RECONCILED })}
        onLock={noop}
        onStartReconciliation={noop}
        onSubmitReconciliation={noop}
        onCarryForward={noop}
        loading={true}
      />,
    );
    const btn = screen.getByTestId("carry-forward-btn");
    expect(btn).toBeDisabled();
    expect(btn).toHaveTextContent("Carrying");
  });
});
