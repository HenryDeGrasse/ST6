import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, act } from "@testing-library/react";
import { CommitEditor } from "../components/CommitEditor.js";
import { PlanState, ChessPriority } from "@weekly-commitments/contracts";
import type { WeeklyCommit } from "@weekly-commitments/contracts";

function makeCommit(overrides: Partial<WeeklyCommit> = {}): WeeklyCommit {
  return {
    id: "commit-1",
    weeklyPlanId: "plan-1",
    title: "Test task",
    description: "A test description",
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

describe("CommitEditor", () => {
  const defaultProps = {
    planState: PlanState.DRAFT,
    rcdoTree: [],
    rcdoSearchResults: [],
    onRcdoSearch: vi.fn(),
    onRcdoClearSearch: vi.fn(),
    onSave: vi.fn(),
  };

  it("renders new commit form with empty fields", () => {
    render(<CommitEditor {...defaultProps} />);
    expect(screen.getByTestId("commit-editor-new")).toBeInTheDocument();
    expect(screen.getByTestId("commit-title")).toHaveValue("");
    expect(screen.getByTestId("commit-save")).toBeDisabled();
  });

  it("enables save button when title is entered", () => {
    render(<CommitEditor {...defaultProps} />);
    fireEvent.change(screen.getByTestId("commit-title"), { target: { value: "New task" } });
    expect(screen.getByTestId("commit-save")).not.toBeDisabled();
  });

  it("calls onSave with create request for new commit", () => {
    const onSave = vi.fn();
    render(<CommitEditor {...defaultProps} onSave={onSave} />);
    fireEvent.change(screen.getByTestId("commit-title"), { target: { value: "New task" } });
    fireEvent.click(screen.getByTestId("commit-save"));
    expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({ title: "New task" }),
    );
  });

  it("renders existing commit with pre-filled fields", () => {
    const commit = makeCommit();
    render(<CommitEditor {...defaultProps} commit={commit} />);
    expect(screen.getByTestId("commit-editor-commit-1")).toBeInTheDocument();
    expect(screen.getByTestId("commit-title")).toHaveValue("Test task");
  });

  it("shows delete button for existing commits in draft", () => {
    const commit = makeCommit();
    const onDelete = vi.fn();
    render(<CommitEditor {...defaultProps} commit={commit} onDelete={onDelete} />);
    expect(screen.getByTestId("commit-delete")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("commit-delete"));
    expect(onDelete).toHaveBeenCalledOnce();
  });

  it("shows read-only view for RECONCILED state", () => {
    const commit = makeCommit();
    render(<CommitEditor {...defaultProps} commit={commit} planState={PlanState.RECONCILED} />);
    expect(screen.getByTestId("commit-readonly-commit-1")).toBeInTheDocument();
    expect(screen.queryByTestId("commit-title")).not.toBeInTheDocument();
  });

  it("shows only progressNotes input in LOCKED state", () => {
    const commit = makeCommit();
    render(<CommitEditor {...defaultProps} commit={commit} planState={PlanState.LOCKED} />);
    expect(screen.getByTestId("commit-progress-notes")).toBeInTheDocument();
    // Title should be disabled
    expect(screen.getByTestId("commit-title")).toBeDisabled();
  });

  it("shows validation errors from server", () => {
    const commit = makeCommit({
      validationErrors: [
        { code: "MISSING_RCDO_OR_REASON", message: "RCDO link or reason required" },
      ],
    });
    render(<CommitEditor {...defaultProps} commit={commit} />);
    expect(screen.getByTestId("commit-validation-errors")).toHaveTextContent("RCDO link or reason required");
  });

  it("toggles non-strategic mode", () => {
    render(<CommitEditor {...defaultProps} />);
    const toggle = screen.getByTestId("non-strategic-toggle");
    fireEvent.click(toggle);
    expect(screen.getByTestId("non-strategic-reason")).toBeInTheDocument();
    expect(screen.queryByTestId("rcdo-picker")).not.toBeInTheDocument();
  });

  it("does not re-trigger AI suggestion when only callback reference changes", () => {
    const suggest1 = vi.fn();
    const suggest2 = vi.fn();

    const { rerender } = render(
      <CommitEditor {...defaultProps} onAiSuggestRequest={suggest1} />,
    );

    // Type a title long enough to trigger the effect (>= 5 chars)
    act(() => {
      fireEvent.change(screen.getByTestId("commit-title"), {
        target: { value: "Hello world" },
      });
    });
    expect(suggest1).toHaveBeenCalledTimes(1);
    expect(suggest1).toHaveBeenCalledWith("Hello world", undefined);

    // Re-render with a new callback reference but the same title/description.
    // This should not itself retrigger the effect.
    suggest1.mockClear();
    rerender(
      <CommitEditor {...defaultProps} onAiSuggestRequest={suggest2} />,
    );

    expect(suggest1).not.toHaveBeenCalled();
    expect(suggest2).not.toHaveBeenCalled();

    // A subsequent content change should use the latest callback reference.
    fireEvent.change(screen.getByTestId("commit-description"), {
      target: { value: "Updated description" },
    });

    expect(suggest1).not.toHaveBeenCalled();
    expect(suggest2).toHaveBeenCalledTimes(1);
    expect(suggest2).toHaveBeenCalledWith("Hello world", "Updated description");
  });
});
