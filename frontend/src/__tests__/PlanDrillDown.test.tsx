import { describe, it, expect, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { PlanDrillDown } from "../components/PlanDrillDown.js";
import {
  PlanState,
  ReviewStatus,
  LockType,
  ChessPriority,
  CommitCategory,
  CompletionStatus,
} from "@weekly-commitments/contracts";
import type { WeeklyPlan, WeeklyCommit } from "@weekly-commitments/contracts";

describe("PlanDrillDown", () => {
  const plan: WeeklyPlan = {
    id: "plan-1",
    orgId: "org-1",
    ownerUserId: "user-1",
    weekStartDate: "2026-03-09",
    state: PlanState.LOCKED,
    reviewStatus: ReviewStatus.REVIEW_NOT_APPLICABLE,
    lockType: LockType.ON_TIME,
    lockedAt: "2026-03-09T10:00:00Z",
    carryForwardExecutedAt: null,
    version: 2,
    createdAt: "2026-03-09T08:00:00Z",
    updatedAt: "2026-03-09T09:00:00Z",
  };

  const commit: WeeklyCommit = {
    id: "commit-1",
    weeklyPlanId: "plan-1",
    title: "Ship feature",
    description: "Finish the rollout",
    chessPriority: ChessPriority.KING,
    category: CommitCategory.DELIVERY,
    outcomeId: "outcome-1",
    nonStrategicReason: null,
    expectedResult: "Feature is live",
    confidence: 80,
    tags: [],
    progressNotes: "",
    snapshotRallyCryId: null,
    snapshotRallyCryName: null,
    snapshotObjectiveId: null,
    snapshotObjectiveName: null,
    snapshotOutcomeId: "outcome-1",
    snapshotOutcomeName: "Increase adoption",
    carriedFromCommitId: null,
    version: 1,
    createdAt: "2026-03-09T08:00:00Z",
    updatedAt: "2026-03-09T09:00:00Z",
    validationErrors: [],
    actual: null,
  };

  const defaultProps = {
    loading: false,
    error: null,
    displayName: null as string | null,
    onSubmitReview: vi.fn().mockResolvedValue(true),
    onBack: vi.fn(),
  };

  it("renders a busy loading skeleton while drill-down data is loading", () => {
    render(<PlanDrillDown {...defaultProps} loading={true} plan={null} commits={[]} />);

    expect(screen.getByTestId("drilldown-loading")).toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("Loading plan…")).toBeInTheDocument();
  });

  it("shows the direct report display name in the header when provided", () => {
    render(<PlanDrillDown {...defaultProps} plan={plan} commits={[commit]} displayName="Alice Smith" />);

    expect(screen.getByRole("heading", { name: "Plan for Alice Smith" })).toBeInTheDocument();
    expect(screen.getByText("Increase adoption")).toBeInTheDocument();
  });

  it("falls back to ownerUserId when display name is unavailable", () => {
    render(<PlanDrillDown {...defaultProps} plan={plan} commits={[commit]} />);

    expect(screen.getByRole("heading", { name: "Plan for user-1" })).toBeInTheDocument();
  });

  // ─── Actuals columns tests ──────────────────────────────────────────────

  describe("actuals columns visibility", () => {
    it("does NOT show actuals columns for LOCKED plan", () => {
      render(<PlanDrillDown {...defaultProps} plan={{ ...plan, state: PlanState.LOCKED }} commits={[commit]} />);

      const table = screen.getByTestId("drilldown-commits");
      expect(within(table).queryByText("Completion Status")).not.toBeInTheDocument();
      expect(within(table).queryByText("Actual Result")).not.toBeInTheDocument();
      expect(within(table).queryByText("Delta Reason")).not.toBeInTheDocument();
    });

    it("does NOT show actuals columns for DRAFT plan", () => {
      render(<PlanDrillDown {...defaultProps} plan={{ ...plan, state: PlanState.DRAFT }} commits={[commit]} />);

      const table = screen.getByTestId("drilldown-commits");
      expect(within(table).queryByText("Completion Status")).not.toBeInTheDocument();
    });

    it.each([PlanState.RECONCILING, PlanState.RECONCILED, PlanState.CARRY_FORWARD])(
      "shows actuals columns for %s plan",
      (state) => {
        render(<PlanDrillDown {...defaultProps} plan={{ ...plan, state }} commits={[commit]} />);

        const table = screen.getByTestId("drilldown-commits");
        expect(within(table).getByText("Completion Status")).toBeInTheDocument();
        expect(within(table).getByText("Actual Result")).toBeInTheDocument();
        expect(within(table).getByText("Delta Reason")).toBeInTheDocument();
      },
    );
  });

  describe("actuals data rendering", () => {
    const reconciledPlan = { ...plan, state: PlanState.RECONCILED };

    it("shows '—' when commit has no actual", () => {
      render(<PlanDrillDown {...defaultProps} plan={reconciledPlan} commits={[{ ...commit, actual: null }]} />);

      expect(screen.getByTestId("actual-status-commit-1")).toHaveTextContent("—");
      expect(screen.getByTestId("actual-result-commit-1")).toHaveTextContent("—");
      expect(screen.getByTestId("actual-delta-commit-1")).toHaveTextContent("—");
    });

    it("renders actual data when present", () => {
      const commitWithActual: WeeklyCommit = {
        ...commit,
        actual: {
          commitId: "commit-1",
          completionStatus: CompletionStatus.DONE,
          actualResult: "Feature shipped successfully",
          deltaReason: null,
          timeSpent: 120,
        },
      };

      render(<PlanDrillDown {...defaultProps} plan={reconciledPlan} commits={[commitWithActual]} />);

      expect(screen.getByTestId("actual-status-commit-1")).toHaveTextContent("DONE");
      expect(screen.getByTestId("actual-result-commit-1")).toHaveTextContent("Feature shipped successfully");
      expect(screen.getByTestId("actual-delta-commit-1")).toHaveTextContent("—");
    });

    it("renders delta reason when present", () => {
      const commitWithDelta: WeeklyCommit = {
        ...commit,
        actual: {
          commitId: "commit-1",
          completionStatus: CompletionStatus.PARTIALLY,
          actualResult: "Only phase 1 shipped",
          deltaReason: "Blocked by API team",
          timeSpent: null,
        },
      };

      render(<PlanDrillDown {...defaultProps} plan={reconciledPlan} commits={[commitWithDelta]} />);

      expect(screen.getByTestId("actual-status-commit-1")).toHaveTextContent("PARTIALLY");
      expect(screen.getByTestId("actual-result-commit-1")).toHaveTextContent("Only phase 1 shipped");
      expect(screen.getByTestId("actual-delta-commit-1")).toHaveTextContent("Blocked by API team");
    });
  });

  describe("row color-coding by completion status", () => {
    const reconciledPlan = { ...plan, state: PlanState.RECONCILED };

    const makeCommitWithStatus = (status: CompletionStatus): WeeklyCommit => ({
      ...commit,
      actual: {
        commitId: "commit-1",
        completionStatus: status,
        actualResult: "result",
        deltaReason: null,
        timeSpent: null,
      },
    });

    it("applies green tint class for DONE", () => {
      render(
        <PlanDrillDown
          {...defaultProps}
          plan={reconciledPlan}
          commits={[makeCommitWithStatus(CompletionStatus.DONE)]}
        />,
      );

      const row = screen.getByTestId("drilldown-commit-commit-1");
      expect(row.className).toContain("rowDone");
    });

    it("applies amber tint class for PARTIALLY", () => {
      render(
        <PlanDrillDown
          {...defaultProps}
          plan={reconciledPlan}
          commits={[makeCommitWithStatus(CompletionStatus.PARTIALLY)]}
        />,
      );

      const row = screen.getByTestId("drilldown-commit-commit-1");
      expect(row.className).toContain("rowPartially");
    });

    it("applies red tint class for NOT_DONE", () => {
      render(
        <PlanDrillDown
          {...defaultProps}
          plan={reconciledPlan}
          commits={[makeCommitWithStatus(CompletionStatus.NOT_DONE)]}
        />,
      );

      const row = screen.getByTestId("drilldown-commit-commit-1");
      expect(row.className).toContain("rowNotDone");
    });

    it("applies red tint class for DROPPED", () => {
      render(
        <PlanDrillDown
          {...defaultProps}
          plan={reconciledPlan}
          commits={[makeCommitWithStatus(CompletionStatus.DROPPED)]}
        />,
      );

      const row = screen.getByTestId("drilldown-commit-commit-1");
      expect(row.className).toContain("rowDropped");
    });

    it("does not apply color tint class when plan is not in actuals-visible state", () => {
      render(
        <PlanDrillDown
          {...defaultProps}
          plan={{ ...plan, state: PlanState.LOCKED }}
          commits={[makeCommitWithStatus(CompletionStatus.DONE)]}
        />,
      );

      const row = screen.getByTestId("drilldown-commit-commit-1");
      expect(row.className).not.toContain("rowDone");
      expect(row.className).not.toContain("rowPartially");
      expect(row.className).not.toContain("rowNotDone");
      expect(row.className).not.toContain("rowDropped");
    });
  });
});
