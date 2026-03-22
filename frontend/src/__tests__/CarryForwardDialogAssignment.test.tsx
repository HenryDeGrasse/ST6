/**
 * Tests for CarryForwardDialog Phase 6 additions:
 *   - Shows assignment-based items with issue keys
 *   - onCarryForwardAssignments is called (not a commit clone)
 *   - Assignment pre-selection
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CarryForwardDialog } from "../components/CarryForwardDialog.js";
import { ChessPriority, IssueStatus, EffortType } from "@weekly-commitments/contracts";
import type { WeeklyCommit, WeeklyAssignmentWithActual, Issue } from "@weekly-commitments/contracts";

function makeCommit(overrides: Partial<WeeklyCommit> = {}): WeeklyCommit {
  return {
    id: "commit-1",
    weeklyPlanId: "plan-1",
    title: "Test commit",
    description: "",
    chessPriority: ChessPriority.ROOK,
    category: null,
    outcomeId: null,
    nonStrategicReason: null,
    expectedResult: "",
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

describe("CarryForwardDialog — Phase 6 assignment support", () => {
  it("renders assignment-based items in the carry forward list with issue key", () => {
    const assignment = makeAssignment({
      issue: makeIssue({ issueKey: "PLAT-42", title: "Add Redis caching layer" }),
    });
    render(
      <CarryForwardDialog
        commits={[]}
        assignments={[assignment]}
        onCarryForward={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("carry-assignment-option-assignment-1")).toBeInTheDocument();
    expect(screen.getByText("PLAT-42: Add Redis caching layer")).toBeInTheDocument();
  });

  it("pre-selects all assignments", () => {
    const assignment = makeAssignment();
    render(
      <CarryForwardDialog
        commits={[]}
        assignments={[assignment]}
        onCarryForward={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    const row = screen.getByTestId("carry-assignment-option-assignment-1");
    const checkbox = row.querySelector("input[type=checkbox]") as HTMLInputElement;
    expect(checkbox).toBeChecked();
  });

  it("confirm button shows total count including assignments", () => {
    const commit = makeCommit({ id: "c1" });
    const assignment = makeAssignment({ id: "a1" });
    render(
      <CarryForwardDialog
        commits={[commit]}
        assignments={[assignment]}
        onCarryForward={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("carry-confirm")).toHaveTextContent("Carry Forward (2)");
  });

  it("calls onCarryForwardAssignments with selected assignment ids", () => {
    const onCarryForward = vi.fn();
    const onCarryForwardAssignments = vi.fn();
    const assignment = makeAssignment({ id: "a1" });
    render(
      <CarryForwardDialog
        commits={[]}
        assignments={[assignment]}
        onCarryForward={onCarryForward}
        onCarryForwardAssignments={onCarryForwardAssignments}
        onCancel={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByTestId("carry-confirm"));
    expect(onCarryForward).toHaveBeenCalledWith([]);
    expect(onCarryForwardAssignments).toHaveBeenCalledWith(["a1"]);
  });

  it("does not call onCarryForwardAssignments if no assignments selected", () => {
    const onCarryForwardAssignments = vi.fn();
    const assignment = makeAssignment({ id: "a1" });
    render(
      <CarryForwardDialog
        commits={[makeCommit({ id: "c1" })]}
        assignments={[assignment]}
        onCarryForward={vi.fn()}
        onCarryForwardAssignments={onCarryForwardAssignments}
        onCancel={vi.fn()}
      />,
    );
    // Uncheck the assignment
    const row = screen.getByTestId("carry-assignment-option-a1");
    const checkbox = row.querySelector("input[type=checkbox]") as HTMLInputElement;
    fireEvent.click(checkbox);

    fireEvent.click(screen.getByTestId("carry-confirm"));
    expect(onCarryForwardAssignments).not.toHaveBeenCalled();
  });

  it("shows empty state only when both commits and assignments are empty", () => {
    render(
      <CarryForwardDialog
        commits={[]}
        assignments={[]}
        onCarryForward={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByText("No commits available to carry forward.")).toBeInTheDocument();
  });

  it("does not show empty state when only assignments exist", () => {
    render(
      <CarryForwardDialog
        commits={[]}
        assignments={[makeAssignment()]}
        onCarryForward={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.queryByText("No commits available to carry forward.")).not.toBeInTheDocument();
  });

  it("shows 'Backlog' badge for assignment items", () => {
    const assignment = makeAssignment();
    render(
      <CarryForwardDialog
        commits={[]}
        assignments={[assignment]}
        onCarryForward={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByText("Backlog")).toBeInTheDocument();
  });
});
