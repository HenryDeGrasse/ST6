import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { CommitList } from "../components/CommitList.js";
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

const defaultProps = {
  commits: [] as WeeklyCommit[],
  planState: PlanState.DRAFT,
  rcdoTree: [],
  rcdoSearchResults: [],
  onRcdoSearch: vi.fn(),
  onRcdoClearSearch: vi.fn(),
  onCreate: vi.fn<() => Promise<boolean>>(),
  onUpdate: vi.fn<() => Promise<boolean>>(),
  onDelete: vi.fn<() => Promise<boolean>>(),
};

describe("CommitList", () => {
  it("keeps the new-commit form open when create fails", async () => {
    const onCreate = vi.fn().mockResolvedValue(false);

    render(<CommitList {...defaultProps} onCreate={onCreate} />);

    fireEvent.click(screen.getByTestId("add-commit-btn"));
    fireEvent.change(screen.getByTestId("commit-title"), {
      target: { value: "New task" },
    });
    fireEvent.click(screen.getByTestId("commit-save"));

    await waitFor(() => expect(onCreate).toHaveBeenCalledOnce());
    expect(screen.getByTestId("commit-editor-new")).toBeInTheDocument();
  });

  it("closes the new-commit form when create succeeds", async () => {
    const onCreate = vi.fn().mockResolvedValue(true);

    render(<CommitList {...defaultProps} onCreate={onCreate} />);

    fireEvent.click(screen.getByTestId("add-commit-btn"));
    fireEvent.change(screen.getByTestId("commit-title"), {
      target: { value: "New task" },
    });
    fireEvent.click(screen.getByTestId("commit-save"));

    await waitFor(() => {
      expect(onCreate).toHaveBeenCalledOnce();
      expect(screen.queryByTestId("commit-editor-new")).not.toBeInTheDocument();
    });
  });

  it("keeps an existing commit open when update fails", async () => {
    const commit = makeCommit();
    const onUpdate = vi.fn().mockResolvedValue(false);

    render(
      <CommitList
        {...defaultProps}
        commits={[commit]}
        onUpdate={onUpdate}
      />,
    );

    fireEvent.click(screen.getByTestId(`commit-row-${commit.id}`));
    fireEvent.click(screen.getByTestId("commit-save"));

    await waitFor(() => expect(onUpdate).toHaveBeenCalledOnce());
    expect(screen.getByTestId(`commit-editor-${commit.id}`)).toBeInTheDocument();
  });

  it("prevents default scroll on Space key for commit rows", async () => {
    const commit = makeCommit();
    const onUpdate = vi.fn().mockResolvedValue(true);
    render(
      <CommitList
        {...defaultProps}
        commits={[commit]}
        onUpdate={onUpdate}
      />,
    );

    const row = screen.getByTestId(`commit-row-${commit.id}`);
    // Use fireEvent to check that Space opens editor (proving preventDefault + handler fire)
    const event = fireEvent.keyDown(row, { key: " " });
    // fireEvent returns false when preventDefault was called
    expect(event).toBe(false);
  });
});
