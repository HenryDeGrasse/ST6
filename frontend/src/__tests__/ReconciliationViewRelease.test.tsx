/**
 * Tests for ReconciliationView Phase 6 additions:
 *   - Renders assignment-based items in reconciliation
 *   - 'Release to Backlog' button for assignments
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ReconciliationView } from "../components/ReconciliationView.js";
import { PlanState, ChessPriority, IssueStatus, EffortType } from "@weekly-commitments/contracts";
import type { WeeklyCommit, WeeklyAssignmentWithActual, Issue } from "@weekly-commitments/contracts";

function makeCommit(overrides: Partial<WeeklyCommit> = {}): WeeklyCommit {
  return {
    id: "commit-1",
    weeklyPlanId: "plan-1",
    title: "Test task",
    description: "",
    chessPriority: ChessPriority.ROOK,
    category: null,
    outcomeId: null,
    nonStrategicReason: null,
    expectedResult: "Expected output",
    confidence: null,
    tags: [],
    progressNotes: "",
    snapshotRallyCryId: null,
    snapshotRallyCryName: null,
    snapshotObjectiveId: null,
    snapshotObjectiveName: null,
    snapshotOutcomeId: null,
    snapshotOutcomeName: null,
    carriedFromCommitId: null,
    version: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    validationErrors: [],
    actual: null,
    ...overrides,
  };
}

function makeIssue(overrides: Partial<Issue> = {}): Issue {
  return {
    id: "issue-1",
    orgId: "org-1",
    teamId: "team-1",
    issueKey: "PLAT-42",
    sequenceNumber: 42,
    title: "Add Redis caching layer",
    description: null,
    effortType: EffortType.BUILD,
    estimatedHours: 8,
    chessPriority: ChessPriority.ROOK,
    outcomeId: null,
    nonStrategicReason: null,
    creatorUserId: "user-1",
    assigneeUserId: null,
    blockedByIssueId: null,
    status: IssueStatus.IN_PROGRESS,
    aiRecommendedRank: null,
    aiRankRationale: null,
    aiSuggestedEffortType: null,
    version: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    archivedAt: null,
    ...overrides,
  };
}

function makeAssignment(overrides: Partial<WeeklyAssignmentWithActual> = {}): WeeklyAssignmentWithActual {
  return {
    id: "assignment-1",
    orgId: "org-1",
    weeklyPlanId: "plan-1",
    issueId: "issue-1",
    chessPriorityOverride: null,
    expectedResult: null,
    confidence: null,
    snapshotRallyCryId: null,
    snapshotRallyCryName: null,
    snapshotObjectiveId: null,
    snapshotObjectiveName: null,
    snapshotOutcomeId: null,
    snapshotOutcomeName: null,
    tags: [],
    version: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    actual: null,
    issue: makeIssue(),
    ...overrides,
  };
}

describe("ReconciliationView — Phase 6 assignment support", () => {
  it("renders assignment section when assignments are provided in RECONCILING state", () => {
    render(
      <ReconciliationView
        commits={[makeCommit()]}
        assignments={[makeAssignment()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    expect(screen.getByTestId("reconcile-assignments-section")).toBeInTheDocument();
    expect(screen.getByTestId("reconcile-assignment-assignment-1")).toBeInTheDocument();
    expect(screen.getByText("PLAT-42: Add Redis caching layer")).toBeInTheDocument();
  });

  it("does not render assignment section when assignments array is empty", () => {
    render(
      <ReconciliationView
        commits={[makeCommit()]}
        assignments={[]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    expect(screen.queryByTestId("reconcile-assignments-section")).not.toBeInTheDocument();
  });

  it("shows 'Release to Backlog' button when onReleaseToBacklog prop is provided", () => {
    render(
      <ReconciliationView
        commits={[]}
        assignments={[makeAssignment()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onReleaseToBacklog={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    expect(screen.getByTestId("release-to-backlog-assignment-1")).toBeInTheDocument();
    expect(screen.getByTestId("release-to-backlog-assignment-1")).toHaveTextContent("Release to Backlog");
  });

  it("does not show 'Release to Backlog' button when onReleaseToBacklog prop is absent", () => {
    render(
      <ReconciliationView
        commits={[]}
        assignments={[makeAssignment()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    expect(screen.queryByTestId("release-to-backlog-assignment-1")).not.toBeInTheDocument();
  });

  it("calls onReleaseToBacklog with the issue id when the button is clicked", async () => {
    const onRelease = vi.fn();
    render(
      <ReconciliationView
        commits={[]}
        assignments={[makeAssignment()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onReleaseToBacklog={onRelease}
        onSubmit={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByTestId("release-to-backlog-assignment-1"));
    await waitFor(() => {
      expect(onRelease).toHaveBeenCalledWith("issue-1");
    });
  });

  it("shows assignment issue key and title in reconciliation card", () => {
    const assignment = makeAssignment({
      issue: makeIssue({ issueKey: "ENG-99", title: "Migrate to Postgres 16" }),
    });
    render(
      <ReconciliationView
        commits={[]}
        assignments={[assignment]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    expect(screen.getByText("ENG-99: Migrate to Postgres 16")).toBeInTheDocument();
  });

  it("returns null when plan is not in RECONCILING state", () => {
    const { container } = render(
      <ReconciliationView
        commits={[makeCommit()]}
        assignments={[makeAssignment()]}
        planState={PlanState.DRAFT}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    expect(container.firstChild).toBeNull();
  });
});
