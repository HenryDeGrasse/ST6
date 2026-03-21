import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { OutcomeProgressCard } from "../components/UrgencyIndicator/OutcomeProgressCard.js";

// ─── Default props ─────────────────────────────────────────────────────────────

const defaultProps = {
  outcomeName: "Revenue Growth",
  targetDate: "2026-06-30",
  progressPct: 55,
  expectedProgressPct: 60,
  urgencyBand: "AT_RISK",
  daysRemaining: 42,
};

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("OutcomeProgressCard", () => {
  // ── Structural elements ─────────────────────────────────────────────────────

  it("renders with data-testid outcome-progress-card", () => {
    render(<OutcomeProgressCard {...defaultProps} />);
    expect(screen.getByTestId("outcome-progress-card")).toBeInTheDocument();
  });

  it("renders the outcome name as a heading", () => {
    render(<OutcomeProgressCard {...defaultProps} outcomeName="Market Penetration" />);
    expect(screen.getByText("Market Penetration")).toBeInTheDocument();
  });

  it("renders the urgency badge", () => {
    render(<OutcomeProgressCard {...defaultProps} urgencyBand="AT_RISK" />);
    const badge = screen.getByTestId("urgency-badge");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-urgency-band", "AT_RISK");
  });

  it("renders the progress bar with role=progressbar", () => {
    render(<OutcomeProgressCard {...defaultProps} />);
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  it("sets aria-valuenow on the progress bar to the rounded progress percentage", () => {
    render(<OutcomeProgressCard {...defaultProps} progressPct={55} />);
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "55");
  });

  // ── Progress percentage text ────────────────────────────────────────────────

  it("shows correct actual progress percentage text", () => {
    render(<OutcomeProgressCard {...defaultProps} progressPct={55} />);
    expect(screen.getByText("55% complete")).toBeInTheDocument();
  });

  it("shows correct expected progress percentage text", () => {
    render(<OutcomeProgressCard {...defaultProps} expectedProgressPct={60} />);
    expect(screen.getByText("(60% expected)")).toBeInTheDocument();
  });

  it("rounds progress percentage to nearest integer", () => {
    render(<OutcomeProgressCard {...defaultProps} progressPct={55.7} expectedProgressPct={60.3} />);
    expect(screen.getByText("56% complete")).toBeInTheDocument();
    expect(screen.getByText("(60% expected)")).toBeInTheDocument();
  });

  // ── Target date display ─────────────────────────────────────────────────────

  it("shows formatted target date when targetDate is provided", () => {
    render(<OutcomeProgressCard {...defaultProps} targetDate="2026-06-30" />);
    expect(screen.getByText(/June 30, 2026/)).toBeInTheDocument();
  });

  it("shows 'No target date' when targetDate is null", () => {
    render(
      <OutcomeProgressCard
        {...defaultProps}
        targetDate={null}
        daysRemaining={Number.MIN_SAFE_INTEGER}
      />,
    );
    expect(screen.getByText(/No target date/i)).toBeInTheDocument();
  });

  // ── Days remaining ──────────────────────────────────────────────────────────

  it("shows days remaining when daysRemaining is positive", () => {
    render(<OutcomeProgressCard {...defaultProps} daysRemaining={42} />);
    expect(screen.getByText(/42 days remaining/)).toBeInTheDocument();
  });

  it("shows singular 'day' for exactly 1 day remaining", () => {
    render(<OutcomeProgressCard {...defaultProps} daysRemaining={1} />);
    expect(screen.getByText("1 day remaining")).toBeInTheDocument();
  });

  it("shows 'Overdue' when daysRemaining is negative", () => {
    render(<OutcomeProgressCard {...defaultProps} daysRemaining={-5} />);
    expect(screen.getByText("Overdue")).toBeInTheDocument();
  });

  it("shows 'Overdue' when daysRemaining is -1", () => {
    render(<OutcomeProgressCard {...defaultProps} daysRemaining={-1} />);
    expect(screen.getByText("Overdue")).toBeInTheDocument();
  });

  it("does not show days remaining when targetDate is null", () => {
    render(
      <OutcomeProgressCard
        {...defaultProps}
        targetDate={null}
        daysRemaining={Number.MIN_SAFE_INTEGER}
      />,
    );
    expect(screen.queryByText(/days remaining/)).not.toBeInTheDocument();
    expect(screen.queryByText("Overdue")).not.toBeInTheDocument();
  });

  // ── Urgency badge variants ──────────────────────────────────────────────────

  it("renders ON_TRACK urgency badge when urgencyBand is ON_TRACK", () => {
    render(<OutcomeProgressCard {...defaultProps} urgencyBand="ON_TRACK" />);
    expect(screen.getByTestId("urgency-badge")).toHaveAttribute("data-urgency-band", "ON_TRACK");
  });

  it("renders CRITICAL urgency badge when urgencyBand is CRITICAL", () => {
    render(
      <OutcomeProgressCard {...defaultProps} urgencyBand="CRITICAL" daysRemaining={3} />,
    );
    expect(screen.getByTestId("urgency-badge")).toHaveAttribute("data-urgency-band", "CRITICAL");
  });

  it("renders NO_TARGET urgency badge when urgencyBand is NO_TARGET", () => {
    render(
      <OutcomeProgressCard
        {...defaultProps}
        urgencyBand="NO_TARGET"
        targetDate={null}
        daysRemaining={Number.MIN_SAFE_INTEGER}
      />,
    );
    expect(screen.getByTestId("urgency-badge")).toHaveAttribute("data-urgency-band", "NO_TARGET");
  });
});
