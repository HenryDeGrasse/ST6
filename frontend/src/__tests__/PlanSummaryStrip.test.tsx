import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PlanSummaryStrip, computeMetrics } from "../components/PlanSummaryStrip.js";
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

describe("computeMetrics", () => {
  it("returns zeros for empty commits", () => {
    const m = computeMetrics([]);
    expect(m.total).toBe(0);
    expect(m.strategicCount).toBe(0);
    expect(m.nonStrategicCount).toBe(0);
    expect(m.alignmentPct).toBe(0);
    expect(m.kingCount).toBe(0);
    expect(m.queenCount).toBe(0);
  });

  it("counts all non-strategic when no outcomeId set", () => {
    const commits = [makeCommit(), makeCommit(), makeCommit()];
    const m = computeMetrics(commits);
    expect(m.total).toBe(3);
    expect(m.strategicCount).toBe(0);
    expect(m.nonStrategicCount).toBe(3);
    expect(m.alignmentPct).toBe(0);
  });

  it("counts all strategic when all have outcomeId", () => {
    const commits = [
      makeCommit({ outcomeId: "o1" }),
      makeCommit({ outcomeId: "o2" }),
    ];
    const m = computeMetrics(commits);
    expect(m.total).toBe(2);
    expect(m.strategicCount).toBe(2);
    expect(m.nonStrategicCount).toBe(0);
    expect(m.alignmentPct).toBe(100);
  });

  it("computes mixed alignment correctly", () => {
    const commits = [
      makeCommit({ outcomeId: "o1" }),
      makeCommit({ outcomeId: "o2" }),
      makeCommit(),
    ];
    const m = computeMetrics(commits);
    expect(m.total).toBe(3);
    expect(m.strategicCount).toBe(2);
    expect(m.nonStrategicCount).toBe(1);
    expect(m.alignmentPct).toBe(67); // Math.round(2/3 * 100)
  });

  it("counts KING and QUEEN priorities", () => {
    const commits = [
      makeCommit({ chessPriority: ChessPriority.KING }),
      makeCommit({ chessPriority: ChessPriority.QUEEN }),
      makeCommit({ chessPriority: ChessPriority.QUEEN }),
      makeCommit({ chessPriority: ChessPriority.ROOK }),
      makeCommit({ chessPriority: ChessPriority.PAWN }),
    ];
    const m = computeMetrics(commits);
    expect(m.kingCount).toBe(1);
    expect(m.queenCount).toBe(2);
  });

  it("handles null chessPriority without counting as KING/QUEEN", () => {
    const commits = [makeCommit({ chessPriority: null })];
    const m = computeMetrics(commits);
    expect(m.kingCount).toBe(0);
    expect(m.queenCount).toBe(0);
  });
});

describe("PlanSummaryStrip", () => {
  it("renders the strip with correct test ids", () => {
    render(<PlanSummaryStrip commits={[]} />);
    expect(screen.getByTestId("plan-summary-strip")).toBeInTheDocument();
    expect(screen.getByTestId("metric-total")).toBeInTheDocument();
    expect(screen.getByTestId("metric-alignment")).toBeInTheDocument();
    expect(screen.getByTestId("metric-non-strategic")).toBeInTheDocument();
    expect(screen.getByTestId("metric-king")).toBeInTheDocument();
    expect(screen.getByTestId("metric-queen")).toBeInTheDocument();
  });

  it("displays zero metrics for empty commits", () => {
    render(<PlanSummaryStrip commits={[]} />);
    expect(screen.getByTestId("metric-total")).toHaveTextContent("0");
    expect(screen.getByTestId("metric-alignment")).toHaveTextContent("0%");
    expect(screen.getByTestId("metric-non-strategic")).toHaveTextContent("0");
  });

  it("displays correct metrics for a mix of commits", () => {
    const commits = [
      makeCommit({ outcomeId: "o1", chessPriority: ChessPriority.KING }),
      makeCommit({ outcomeId: "o2", chessPriority: ChessPriority.QUEEN }),
      makeCommit({ chessPriority: ChessPriority.ROOK }),
    ];
    render(<PlanSummaryStrip commits={commits} />);
    expect(screen.getByTestId("metric-total")).toHaveTextContent("3");
    expect(screen.getByTestId("metric-alignment")).toHaveTextContent("67%");
    expect(screen.getByTestId("metric-non-strategic")).toHaveTextContent("1");
    expect(screen.getByTestId("metric-king")).toHaveTextContent("1");
    expect(screen.getByTestId("metric-queen")).toHaveTextContent("1");
  });

  it("has region role and aria-label for accessibility", () => {
    render(<PlanSummaryStrip commits={[]} />);
    const strip = screen.getByTestId("plan-summary-strip");
    expect(strip).toHaveAttribute("role", "region");
    expect(strip).toHaveAttribute("aria-label", "Plan summary metrics");
  });
});
