/**
 * Tests for CommitList Phase 6 additions:
 *   - Renders assignment rows with issue key and effort type
 *   - 'Add from Backlog' button alongside 'Add Commitment'
 *   - Remove assignment button in DRAFT state
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CommitList } from "../components/CommitList.js";
import { PlanState, ChessPriority, IssueStatus, EffortType } from "@weekly-commitments/contracts";
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
    status: IssueStatus.OPEN,
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

const defaultProps = {
  commits: [] as WeeklyCommit[],
  planState: PlanState.DRAFT,
  rcdoTree: [],
  rcdoSearchResults: [],
  onRcdoSearch: vi.fn(),
  onRcdoClearSearch: vi.fn(),
  onCreate: vi.fn().mockResolvedValue(true),
  onUpdate: vi.fn().mockResolvedValue(true),
  onDelete: vi.fn().mockResolvedValue(true),
};

describe("CommitList — Phase 6 assignment support", () => {
  it("renders assignment rows with issue key and title", () => {
    const assignment = makeAssignment({
      issue: makeIssue({ issueKey: "PLAT-42", title: "Add Redis caching layer" }),
    });
    render(
      <CommitList
        {...defaultProps}
        assignments={[assignment]}
      />,
    );
    expect(screen.getByTestId("assignment-row-assignment-1")).toBeInTheDocument();
    expect(screen.getByTestId("assignment-key-assignment-1")).toHaveTextContent("PLAT-42");
    expect(screen.getByText("Add Redis caching layer")).toBeInTheDocument();
  });

  it("shows effort type badge for assignment items", () => {
    const assignment = makeAssignment({
      issue: makeIssue({ effortType: EffortType.BUILD }),
    });
    render(<CommitList {...defaultProps} assignments={[assignment]} />);
    expect(screen.getByTestId("assignment-effort-assignment-1")).toHaveTextContent("BUILD");
  });

  it("shows priority badge for assignment items", () => {
    const assignment = makeAssignment({
      issue: makeIssue({ chessPriority: ChessPriority.QUEEN }),
    });
    render(<CommitList {...defaultProps} assignments={[assignment]} />);
    expect(screen.getByTestId("assignment-priority")).toHaveTextContent("QUEEN");
  });

  it("shows 'From backlog' badge for assignment items", () => {
    render(<CommitList {...defaultProps} assignments={[makeAssignment()]} />);
    expect(screen.getByText("From backlog")).toBeInTheDocument();
  });

  it("shows total item count including assignments in header", () => {
    const commit = makeCommit();
    const assignment = makeAssignment();
    render(<CommitList {...defaultProps} commits={[commit]} assignments={[assignment]} />);
    expect(screen.getByText("Commitments (2)")).toBeInTheDocument();
  });

  it("shows 'Add from Backlog' button when onAddFromBacklog is provided in DRAFT state", () => {
    const onAddFromBacklog = vi.fn();
    render(<CommitList {...defaultProps} onAddFromBacklog={onAddFromBacklog} />);
    expect(screen.getByTestId("add-from-backlog-btn")).toBeInTheDocument();
  });

  it("does not show 'Add from Backlog' button when onAddFromBacklog is not provided", () => {
    render(<CommitList {...defaultProps} />);
    expect(screen.queryByTestId("add-from-backlog-btn")).not.toBeInTheDocument();
  });

  it("calls onAddFromBacklog when 'Add from Backlog' button is clicked", () => {
    const onAddFromBacklog = vi.fn();
    render(<CommitList {...defaultProps} onAddFromBacklog={onAddFromBacklog} />);
    fireEvent.click(screen.getByTestId("add-from-backlog-btn"));
    expect(onAddFromBacklog).toHaveBeenCalledOnce();
  });

  it("does not show 'Add from Backlog' button when plan is not in DRAFT state", () => {
    const onAddFromBacklog = vi.fn();
    render(
      <CommitList {...defaultProps} planState={PlanState.LOCKED} onAddFromBacklog={onAddFromBacklog} />,
    );
    expect(screen.queryByTestId("add-from-backlog-btn")).not.toBeInTheDocument();
  });

  it("shows remove button for assignments in DRAFT state when onRemoveAssignment is provided", () => {
    render(
      <CommitList
        {...defaultProps}
        assignments={[makeAssignment()]}
        onRemoveAssignment={vi.fn()}
      />,
    );
    expect(screen.getByTestId("assignment-remove-assignment-1")).toBeInTheDocument();
  });

  it("calls onRemoveAssignment with assignment id when remove button is clicked", () => {
    const onRemoveAssignment = vi.fn();
    render(
      <CommitList
        {...defaultProps}
        assignments={[makeAssignment({ id: "a1" })]}
        onRemoveAssignment={onRemoveAssignment}
      />,
    );
    fireEvent.click(screen.getByTestId("assignment-remove-a1"));
    expect(onRemoveAssignment).toHaveBeenCalledWith("a1");
  });

  it("does not show remove button in non-DRAFT plan state", () => {
    render(
      <CommitList
        {...defaultProps}
        planState={PlanState.LOCKED}
        assignments={[makeAssignment()]}
        onRemoveAssignment={vi.fn()}
      />,
    );
    expect(screen.queryByTestId("assignment-remove-assignment-1")).not.toBeInTheDocument();
  });

  it("shows empty state only when both commits and assignments are empty", () => {
    render(<CommitList {...defaultProps} commits={[]} assignments={[]} />);
    expect(screen.getByText("No commitments yet.")).toBeInTheDocument();
  });

  it("does not show empty state when only assignments exist", () => {
    render(<CommitList {...defaultProps} commits={[]} assignments={[makeAssignment()]} />);
    expect(screen.queryByText("No commitments yet.")).not.toBeInTheDocument();
  });
});
