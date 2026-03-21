import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, act, within } from "@testing-library/react";
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

  it("defaults untouched commits to DONE so submit is enabled", () => {
    render(
      <ReconciliationView
        commits={[makeCommit()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    expect(screen.getByTestId("reconcile-status-commit-1")).toHaveValue(CompletionStatus.DONE);
    expect(screen.getByTestId("reconcile-submit")).toBeEnabled();
  });

  it("shows delta reason field and status icon when status is not DONE", () => {
    render(
      <ReconciliationView
        commits={[makeCommit()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByTestId("reconcile-status-commit-1"), {
      target: { value: CompletionStatus.NOT_DONE },
    });

    const card = screen.getByTestId("reconcile-commit-commit-1");
    expect(screen.getByTestId("reconcile-delta-commit-1")).toBeInTheDocument();
    expect(within(card).getByTestId("status-icon-error-x")).toBeInTheDocument();
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
              actualHours: 5.5,
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
    expect(screen.getByTestId("reconcile-actual-hours-commit-1")).toHaveValue(5.5);
  });

  it("includes actualHours in save requests when provided", async () => {
    const onUpdateActual = vi.fn();
    render(
      <ReconciliationView
        commits={[makeCommit()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={onUpdateActual}
        onSubmit={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByTestId("reconcile-actual-hours-commit-1"), {
      target: { value: "6.5" },
    });

    await act(async () => {
      fireEvent.click(screen.getByTestId("reconcile-save-commit-1"));
    });

    expect(onUpdateActual).toHaveBeenCalledWith(
      "commit-1",
      1,
      expect.objectContaining({
        actualResult: "",
        completionStatus: CompletionStatus.DONE,
        actualHours: 6.5,
      }),
    );
  });

  it("rehydrates actual hours when server payload changes", () => {
    const { rerender } = render(
      <ReconciliationView
        commits={[
          makeCommit({
            actual: {
              commitId: "commit-1",
              actualResult: "Saved actual",
              completionStatus: CompletionStatus.DONE,
              deltaReason: null,
              timeSpent: null,
              actualHours: 3,
            },
          }),
        ]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.getByTestId("reconcile-actual-hours-commit-1")).toHaveValue(3);

    fireEvent.change(screen.getByTestId("reconcile-actual-hours-commit-1"), {
      target: { value: "4" },
    });
    expect(screen.getByTestId("reconcile-actual-hours-commit-1")).toHaveValue(4);

    rerender(
      <ReconciliationView
        commits={[
          makeCommit({
            actual: {
              commitId: "commit-1",
              actualResult: "Saved actual",
              completionStatus: CompletionStatus.DONE,
              deltaReason: null,
              timeSpent: null,
              actualHours: 7.5,
            },
          }),
        ]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.getByTestId("reconcile-actual-hours-commit-1")).toHaveValue(7.5);
  });

  it("shows Saving text and disables save button while saving", async () => {
    let resolveUpdate: () => void = () => {};
    const onUpdateActual = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveUpdate = resolve;
        }),
    );

    render(
      <ReconciliationView
        commits={[makeCommit()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={onUpdateActual}
        onSubmit={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByTestId("reconcile-status-commit-1"), {
      target: { value: CompletionStatus.DONE },
    });

    const saveBtn = screen.getByTestId("reconcile-save-commit-1");
    expect(saveBtn).toHaveTextContent("Save Actual");
    expect(saveBtn).not.toBeDisabled();

    fireEvent.click(saveBtn);
    expect(saveBtn).toHaveTextContent("Saving");
    expect(saveBtn).toBeDisabled();

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
      <ReconciliationView commits={[]} planState={PlanState.RECONCILING} onUpdateActual={vi.fn()} onSubmit={vi.fn()} />,
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

  it("preserves local edits until the server payload changes, then rehydrates", () => {
    const { rerender } = render(
      <ReconciliationView
        commits={[
          makeCommit({
            actual: {
              commitId: "commit-1",
              actualResult: "Initial server value",
              completionStatus: CompletionStatus.PARTIALLY,
              deltaReason: "Initial delta",
              timeSpent: null,
            },
          }),
        ]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByTestId("reconcile-actual-commit-1"), {
      target: { value: "Unsaved local edit" },
    });

    rerender(
      <ReconciliationView
        commits={[
          makeCommit({
            actual: {
              commitId: "commit-1",
              actualResult: "Initial server value",
              completionStatus: CompletionStatus.PARTIALLY,
              deltaReason: "Initial delta",
              timeSpent: null,
            },
          }),
        ]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.getByTestId("reconcile-actual-commit-1")).toHaveValue("Unsaved local edit");

    rerender(
      <ReconciliationView
        commits={[
          makeCommit({
            version: 2,
            actual: {
              commitId: "commit-1",
              actualResult: "Fresh server value",
              completionStatus: CompletionStatus.DROPPED,
              deltaReason: "Descoped",
              timeSpent: null,
            },
          }),
        ]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    const card = screen.getByTestId("reconcile-commit-commit-1");
    expect(screen.getByTestId("reconcile-status-commit-1")).toHaveValue(CompletionStatus.DROPPED);
    expect(screen.getByTestId("reconcile-actual-commit-1")).toHaveValue("Fresh server value");
    expect(screen.getByTestId("reconcile-delta-commit-1")).toHaveValue("Descoped");
    expect(within(card).getByTestId("status-icon-trash")).toBeInTheDocument();
  });
});
