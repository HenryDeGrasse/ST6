import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, act } from "@testing-library/react";
import { ReconciliationView } from "../components/ReconciliationView.js";
import { PlanState, ChessPriority, CompletionStatus } from "@weekly-commitments/contracts";
import type { WeeklyCommit } from "@weekly-commitments/contracts";

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

describe("ReconciliationView", () => {
  it("renders nothing when plan is not RECONCILING", () => {
    const { container } = render(
      <ReconciliationView
        commits={[makeCommit()]}
        planState={PlanState.DRAFT}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("renders commit reconciliation cards in RECONCILING state", () => {
    render(
      <ReconciliationView
        commits={[makeCommit()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    expect(screen.getByTestId("reconciliation-view")).toBeInTheDocument();
    expect(screen.getByTestId("reconcile-commit-commit-1")).toBeInTheDocument();
  });

  it("disables submit when not all commits have status", () => {
    render(
      <ReconciliationView
        commits={[makeCommit()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    expect(screen.getByTestId("reconcile-submit")).toBeDisabled();
  });

  it("shows delta reason field when status is not DONE", () => {
    render(
      <ReconciliationView
        commits={[makeCommit()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    // Select NOT_DONE
    fireEvent.change(screen.getByTestId("reconcile-status-commit-1"), {
      target: { value: CompletionStatus.NOT_DONE },
    });
    expect(screen.getByTestId("reconcile-delta-commit-1")).toBeInTheDocument();
  });

  it("does not show delta reason field when status is DONE", () => {
    render(
      <ReconciliationView
        commits={[makeCommit()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByTestId("reconcile-status-commit-1"), {
      target: { value: CompletionStatus.DONE },
    });
    expect(screen.queryByTestId("reconcile-delta-commit-1")).not.toBeInTheDocument();
  });

  it("pre-populates saved actuals from the commit payload", () => {
    render(
      <ReconciliationView
        commits={[
          makeCommit({
            actual: {
              commitId: "commit-1",
              actualResult: "Shipped to production",
              completionStatus: CompletionStatus.PARTIALLY,
              deltaReason: "Blocked on dependency",
              timeSpent: null,
            },
          }),
        ]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.getByTestId("reconcile-status-commit-1")).toHaveValue(CompletionStatus.PARTIALLY);
    expect(screen.getByTestId("reconcile-actual-commit-1")).toHaveValue("Shipped to production");
    expect(screen.getByTestId("reconcile-delta-commit-1")).toHaveValue("Blocked on dependency");
  });

  it("shows Saving text and disables save button while saving", async () => {
    let resolveUpdate: () => void = () => {};
    const onUpdateActual = vi.fn(
      () => new Promise<void>((resolve) => { resolveUpdate = resolve; }),
    );

    render(
      <ReconciliationView
        commits={[makeCommit()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={onUpdateActual}
        onSubmit={vi.fn()}
      />,
    );

    // Select a status so Save button is enabled
    fireEvent.change(screen.getByTestId("reconcile-status-commit-1"), {
      target: { value: CompletionStatus.DONE },
    });

    const saveBtn = screen.getByTestId("reconcile-save-commit-1");
    expect(saveBtn).toHaveTextContent("Save Actual");
    expect(saveBtn).not.toBeDisabled();

    // Click save — should show "Saving…" and be disabled
    fireEvent.click(saveBtn);
    expect(saveBtn).toHaveTextContent("Saving");
    expect(saveBtn).toBeDisabled();

    // Resolve the promise — button should revert
    await act(async () => {
      resolveUpdate();
    });
    await vi.waitFor(() => {
      expect(screen.getByTestId("reconcile-save-commit-1")).toHaveTextContent("Save Actual");
    });
  });

  it("associates status label with select via htmlFor/id", () => {
    render(
      <ReconciliationView
        commits={[makeCommit()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    const select = screen.getByTestId("reconcile-status-commit-1");
    expect(select).toHaveAttribute("id", "reconcile-status-select-commit-1");
    const label = select.closest("div")?.querySelector("label");
    expect(label).toHaveAttribute("for", "reconcile-status-select-commit-1");
  });

  it("hydrates actuals when commits arrive after initial render", () => {
    const { rerender } = render(
      <ReconciliationView
        commits={[]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    rerender(
      <ReconciliationView
        commits={[
          makeCommit({
            actual: {
              commitId: "commit-1",
              actualResult: "Recovered after reload",
              completionStatus: CompletionStatus.DONE,
              deltaReason: null,
              timeSpent: null,
            },
          }),
        ]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.getByTestId("reconcile-status-commit-1")).toHaveValue(CompletionStatus.DONE);
    expect(screen.getByTestId("reconcile-actual-commit-1")).toHaveValue("Recovered after reload");
  });
});
