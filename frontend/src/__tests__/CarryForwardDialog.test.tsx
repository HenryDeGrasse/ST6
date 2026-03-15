import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CarryForwardDialog } from "../components/CarryForwardDialog.js";
import { ChessPriority } from "@weekly-commitments/contracts";
import type { WeeklyCommit } from "@weekly-commitments/contracts";

function makeCommit(overrides: Partial<WeeklyCommit> = {}): WeeklyCommit {
  return {
    id: overrides.id ?? "commit-1",
    weeklyPlanId: "plan-1",
    title: overrides.title ?? "Test task",
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

describe("CarryForwardDialog", () => {
  it("renders commits with checkboxes", () => {
    const commits = [
      makeCommit({ id: "c1", title: "Task A" }),
      makeCommit({ id: "c2", title: "Task B" }),
    ];
    render(
      <CarryForwardDialog
        commits={commits}
        onCarryForward={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("carry-forward-dialog")).toBeInTheDocument();
    expect(screen.getByTestId("carry-option-c1")).toBeInTheDocument();
    expect(screen.getByTestId("carry-option-c2")).toBeInTheDocument();
  });

  it("pre-selects all commits", () => {
    const commits = [makeCommit({ id: "c1" })];
    render(
      <CarryForwardDialog
        commits={commits}
        onCarryForward={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    const checkbox = screen.getByTestId("carry-option-c1").querySelector("input");
    expect(checkbox).toBeChecked();
  });

  it("calls onCarryForward with selected commit IDs", () => {
    const onCarryForward = vi.fn();
    const commits = [
      makeCommit({ id: "c1" }),
      makeCommit({ id: "c2" }),
    ];
    render(
      <CarryForwardDialog
        commits={commits}
        onCarryForward={onCarryForward}
        onCancel={vi.fn()}
      />,
    );
    // Uncheck c2
    const c2Checkbox = screen.getByTestId("carry-option-c2").querySelector("input");
    expect(c2Checkbox).not.toBeNull();
    fireEvent.click(c2Checkbox as HTMLInputElement);

    fireEvent.click(screen.getByTestId("carry-confirm"));
    expect(onCarryForward).toHaveBeenCalledWith(["c1"]);
  });

  it("calls onCancel when cancel button is clicked", () => {
    const onCancel = vi.fn();
    render(
      <CarryForwardDialog
        commits={[makeCommit()]}
        onCarryForward={vi.fn()}
        onCancel={onCancel}
      />,
    );
    fireEvent.click(screen.getByTestId("carry-cancel"));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("disables confirm when no commits are selected", () => {
    const commits = [makeCommit({ id: "c1" })];
    render(
      <CarryForwardDialog
        commits={commits}
        onCarryForward={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    // Uncheck the only commit
    const checkbox = screen.getByTestId("carry-option-c1").querySelector("input");
    expect(checkbox).not.toBeNull();
    fireEvent.click(checkbox as HTMLInputElement);

    expect(screen.getByTestId("carry-confirm")).toBeDisabled();
  });

  // --- Accessibility tests ---

  it("has role=dialog, aria-modal, and aria-labelledby", () => {
    render(
      <CarryForwardDialog
        commits={[makeCommit()]}
        onCarryForward={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    const dialog = screen.getByTestId("carry-forward-dialog");
    expect(dialog).toHaveAttribute("role", "dialog");
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(dialog).toHaveAttribute("aria-labelledby", "carry-forward-dialog-title");
    expect(document.getElementById("carry-forward-dialog-title")).toHaveTextContent(
      "Carry Forward to Next Week",
    );
  });

  it("calls onCancel when Escape is pressed", async () => {
    const onCancel = vi.fn();
    render(
      <CarryForwardDialog
        commits={[makeCommit()]}
        onCarryForward={vi.fn()}
        onCancel={onCancel}
      />,
    );
    await userEvent.keyboard("{Escape}");
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("traps focus within the dialog", () => {
    render(
      <CarryForwardDialog
        commits={[makeCommit({ id: "c1" })]}
        onCarryForward={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    // The first focusable element should receive focus on mount
    const dialog = screen.getByTestId("carry-forward-dialog");
    const focusable = dialog.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
    );
    expect(focusable.length).toBeGreaterThan(0);
    expect(document.activeElement).toBe(focusable[0]);
  });
});
