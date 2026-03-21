import { describe, it, expect, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { PlanQualityNudge } from "../components/PlanQualityNudge.js";
import type { QualityNudge } from "@weekly-commitments/contracts";

/* ── Helpers ──────────────────────────────────────────────────────────────── */

function makeNudge(overrides: Partial<QualityNudge> = {}): QualityNudge {
  return {
    type: "COVERAGE_GAP",
    message: "3 commits have no RCDO outcome linked.",
    severity: "WARNING",
    ...overrides,
  };
}

const defaultProps = {
  nudges: [] as QualityNudge[],
  status: "idle" as const,
  onLockAnyway: vi.fn(),
  onReview: vi.fn(),
};

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("PlanQualityNudge", () => {
  it("renders the overlay and dialog", () => {
    render(<PlanQualityNudge {...defaultProps} />);

    expect(screen.getByTestId("plan-quality-nudge-overlay")).toBeInTheDocument();
    expect(screen.getByTestId("plan-quality-nudge-dialog")).toBeInTheDocument();
  });

  it("shows Plan Quality Check title and Advisory badge", () => {
    render(<PlanQualityNudge {...defaultProps} />);

    expect(screen.getByText("Plan Quality Check")).toBeInTheDocument();
    expect(screen.getByText("Advisory")).toBeInTheDocument();
  });

  it("renders Review Plan and Lock Anyway buttons", () => {
    render(<PlanQualityNudge {...defaultProps} />);

    expect(screen.getByTestId("plan-quality-nudge-review")).toBeInTheDocument();
    expect(screen.getByTestId("plan-quality-nudge-lock-anyway")).toBeInTheDocument();
  });

  it("calls onReview when Review Plan button is clicked", () => {
    const onReview = vi.fn();
    render(<PlanQualityNudge {...defaultProps} onReview={onReview} />);

    fireEvent.click(screen.getByTestId("plan-quality-nudge-review"));

    expect(onReview).toHaveBeenCalledTimes(1);
  });

  it("calls onLockAnyway when Lock Anyway button is clicked", () => {
    const onLockAnyway = vi.fn();
    render(<PlanQualityNudge {...defaultProps} status="ok" onLockAnyway={onLockAnyway} />);

    fireEvent.click(screen.getByTestId("plan-quality-nudge-lock-anyway"));

    expect(onLockAnyway).toHaveBeenCalledTimes(1);
  });

  it("calls onReview when clicking the backdrop overlay", () => {
    const onReview = vi.fn();
    render(<PlanQualityNudge {...defaultProps} onReview={onReview} />);

    fireEvent.click(screen.getByTestId("plan-quality-nudge-overlay"));

    expect(onReview).toHaveBeenCalledTimes(1);
  });

  it("shows loading state when status is loading", () => {
    render(<PlanQualityNudge {...defaultProps} status="loading" />);

    expect(screen.getByTestId("plan-quality-nudge-loading")).toBeInTheDocument();
    expect(screen.getByText(/Checking plan quality/)).toBeInTheDocument();
  });

  it("keeps Lock Anyway available while loading", () => {
    const onLockAnyway = vi.fn();
    render(<PlanQualityNudge {...defaultProps} status="loading" onLockAnyway={onLockAnyway} />);

    const lockButton = screen.getByTestId("plan-quality-nudge-lock-anyway");
    expect(lockButton).toBeEnabled();

    fireEvent.click(lockButton);
    expect(onLockAnyway).toHaveBeenCalledTimes(1);
  });

  it("shows unavailable state when status is unavailable", () => {
    render(<PlanQualityNudge {...defaultProps} status="unavailable" />);

    expect(screen.getByTestId("plan-quality-nudge-unavailable")).toBeInTheDocument();
  });

  it("shows rate_limited state when status is rate_limited", () => {
    render(<PlanQualityNudge {...defaultProps} status="rate_limited" />);

    expect(screen.getByTestId("plan-quality-nudge-rate-limited")).toBeInTheDocument();
  });

  it("shows all-clear message when status is ok and nudges are empty", () => {
    render(<PlanQualityNudge {...defaultProps} status="ok" nudges={[]} />);

    expect(screen.getByTestId("plan-quality-nudge-all-clear")).toBeInTheDocument();
    expect(screen.getByText(/No quality issues detected/)).toBeInTheDocument();
  });

  it("renders nudge list when status is ok and nudges exist", () => {
    const nudges = [
      makeNudge({ type: "COVERAGE_GAP", message: "3 commits missing RCDO.", severity: "WARNING" }),
      makeNudge({ type: "HIGH_RCDO_ALIGNMENT", message: "Strong alignment.", severity: "POSITIVE" }),
    ];

    render(<PlanQualityNudge {...defaultProps} status="ok" nudges={nudges} />);

    expect(screen.getByTestId("plan-quality-nudge-list")).toBeInTheDocument();
    expect(screen.getByTestId("plan-quality-nudge-item-0")).toBeInTheDocument();
    expect(screen.getByTestId("plan-quality-nudge-item-1")).toBeInTheDocument();
    expect(screen.getByText("3 commits missing RCDO.")).toBeInTheDocument();
    expect(screen.getByText("Strong alignment.")).toBeInTheDocument();
  });

  it("renders severity badges with correct labels", () => {
    const nudges: QualityNudge[] = [
      makeNudge({ severity: "WARNING" }),
      makeNudge({ type: "POSITIVE_TYPE", severity: "POSITIVE", message: "Great job." }),
      makeNudge({ type: "INFO_TYPE", severity: "INFO", message: "FYI." }),
    ];

    render(<PlanQualityNudge {...defaultProps} status="ok" nudges={nudges} />);

    expect(screen.getByTestId("plan-quality-nudge-badge-0")).toHaveTextContent("Note");
    expect(screen.getByTestId("plan-quality-nudge-badge-1")).toHaveTextContent("Great");
    expect(screen.getByTestId("plan-quality-nudge-badge-2")).toHaveTextContent("Info");
  });

  it("shows the advisory hint text when status is not loading", () => {
    render(<PlanQualityNudge {...defaultProps} status="ok" />);

    expect(screen.getByText(/These suggestions are advisory/)).toBeInTheDocument();
  });

  it("does not show advisory hint while loading", () => {
    render(<PlanQualityNudge {...defaultProps} status="loading" />);

    expect(screen.queryByText(/These suggestions are advisory/)).not.toBeInTheDocument();
  });

  it("has correct aria attributes on the dialog", () => {
    render(<PlanQualityNudge {...defaultProps} status="ok" />);

    const dialog = screen.getByTestId("plan-quality-nudge-dialog");
    expect(dialog).toHaveAttribute("role", "dialog");
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(dialog).toHaveAttribute("aria-labelledby", "pqn-title");
  });

  it("marks dialog as busy while loading", () => {
    render(<PlanQualityNudge {...defaultProps} status="loading" />);

    const dialog = screen.getByTestId("plan-quality-nudge-dialog");
    expect(dialog).toHaveAttribute("aria-busy", "true");
  });
});
