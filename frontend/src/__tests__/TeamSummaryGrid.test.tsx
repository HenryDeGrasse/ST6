import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TeamSummaryGrid } from "../components/TeamSummaryGrid.js";
import { PlanState, ReviewStatus } from "@weekly-commitments/contracts";
import type { TeamMemberSummary } from "@weekly-commitments/contracts";

describe("TeamSummaryGrid", () => {
  const mockUser: TeamMemberSummary = {
    userId: "user-1",
    displayName: "Alice Smith",
    planId: "plan-1",
    state: PlanState.LOCKED,
    reviewStatus: ReviewStatus.REVIEW_NOT_APPLICABLE,
    commitCount: 5,
    incompleteCount: 1,
    issueCount: 2,
    nonStrategicCount: 2,
    kingCount: 1,
    queenCount: 2,
    lastUpdated: "2026-03-09T12:00:00Z",
    isStale: false,
    isLateLock: false,
  };

  it("renders empty state when no users", () => {
    render(<TeamSummaryGrid users={[]} onDrillDown={vi.fn()} />);
    expect(screen.getByTestId("team-summary-empty")).toBeInTheDocument();
  });

  it("renders user row with correct data and display name", () => {
    render(<TeamSummaryGrid users={[mockUser]} onDrillDown={vi.fn()} />);
    expect(screen.getByTestId("team-summary-grid")).toBeInTheDocument();
    expect(screen.getByTestId("team-row-user-1")).toBeInTheDocument();
    expect(screen.getByText("Alice Smith")).toBeInTheDocument(); // display name
    expect(screen.getByText("5")).toBeInTheDocument(); // commit count
  });

  it("shows STALE badge for stale plans", () => {
    const staleUser: TeamMemberSummary = { ...mockUser, isStale: true };
    render(<TeamSummaryGrid users={[staleUser]} onDrillDown={vi.fn()} />);
    expect(screen.getByTestId("stale-badge-user-1")).toBeInTheDocument();
    expect(screen.getByText("STALE")).toBeInTheDocument();
  });

  it("shows LATE LOCK badge", () => {
    const lateUser: TeamMemberSummary = { ...mockUser, isLateLock: true };
    render(<TeamSummaryGrid users={[lateUser]} onDrillDown={vi.fn()} />);
    expect(screen.getByTestId("late-lock-badge-user-1")).toBeInTheDocument();
    expect(screen.getByText("LATE LOCK")).toBeInTheDocument();
  });

  it("calls onDrillDown when View button clicked", async () => {
    const onDrillDown = vi.fn();
    render(<TeamSummaryGrid users={[mockUser]} onDrillDown={onDrillDown} />);
    await userEvent.click(screen.getByTestId("drill-down-user-1"));
    expect(onDrillDown).toHaveBeenCalledWith("user-1", "plan-1");
  });

  it("renders No plan state badge for user without plan", () => {
    const noPlanUser: TeamMemberSummary = {
      ...mockUser,
      planId: null,
      state: null,
      reviewStatus: null,
      commitCount: 0,
    };
    render(<TeamSummaryGrid users={[noPlanUser]} onDrillDown={vi.fn()} />);
    expect(screen.getByText("No plan")).toBeInTheDocument();
  });

  it("falls back to userId when displayName is null", () => {
    const noNameUser: TeamMemberSummary = {
      ...mockUser,
      displayName: null,
    };
    render(<TeamSummaryGrid users={[noNameUser]} onDrillDown={vi.fn()} />);
    expect(screen.getByText("user-1")).toBeInTheDocument();
  });
});
