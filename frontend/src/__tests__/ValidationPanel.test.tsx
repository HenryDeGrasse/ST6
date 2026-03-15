import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ValidationPanel } from "../components/ValidationPanel.js";
import type { WeeklyCommit } from "@weekly-commitments/contracts";
import { ChessPriority } from "@weekly-commitments/contracts";

function makeCommit(overrides: Partial<WeeklyCommit> = {}): WeeklyCommit {
  return {
    id: crypto.randomUUID(),
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

describe("ValidationPanel", () => {
  it("shows all-pass message when valid", () => {
    const commits = [
      makeCommit({ chessPriority: ChessPriority.KING }),
      makeCommit({ chessPriority: ChessPriority.ROOK }),
    ];
    render(<ValidationPanel commits={commits} />);
    expect(screen.getByTestId("validation-panel")).toHaveTextContent("Ready to lock");
  });

  it("warns when no KING is present", () => {
    const commits = [
      makeCommit({ chessPriority: ChessPriority.ROOK }),
      makeCommit({ chessPriority: ChessPriority.PAWN }),
    ];
    render(<ValidationPanel commits={commits} />);
    expect(screen.getByTestId("validation-panel")).toHaveTextContent("No KING commitment");
  });

  it("errors when multiple KINGs are present", () => {
    const commits = [
      makeCommit({ chessPriority: ChessPriority.KING }),
      makeCommit({ chessPriority: ChessPriority.KING }),
    ];
    render(<ValidationPanel commits={commits} />);
    expect(screen.getByTestId("validation-panel")).toHaveTextContent("2 KING commits");
  });

  it("errors when too many QUEENs are present", () => {
    const commits = [
      makeCommit({ chessPriority: ChessPriority.KING }),
      makeCommit({ chessPriority: ChessPriority.QUEEN }),
      makeCommit({ chessPriority: ChessPriority.QUEEN }),
      makeCommit({ chessPriority: ChessPriority.QUEEN }),
    ];
    render(<ValidationPanel commits={commits} />);
    expect(screen.getByTestId("validation-panel")).toHaveTextContent("3 QUEEN commits");
  });

  it("warns when no commits exist", () => {
    render(<ValidationPanel commits={[]} />);
    expect(screen.getByTestId("validation-panel")).toHaveTextContent("No commitments yet");
  });

  it("shows commit-level validation errors", () => {
    const commits = [
      makeCommit({
        title: "Bad commit",
        validationErrors: [
          { code: "MISSING_CHESS_PRIORITY", message: "Chess priority is required" },
        ],
      }),
    ];
    render(<ValidationPanel commits={commits} />);
    expect(screen.getByTestId("validation-panel")).toHaveTextContent("1 commit(s) have validation errors");
    expect(screen.getByTestId("validation-panel")).toHaveTextContent("Chess priority is required");
  });
});
