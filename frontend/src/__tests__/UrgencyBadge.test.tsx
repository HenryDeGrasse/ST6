import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { UrgencyBadge } from "../components/UrgencyIndicator/UrgencyBadge.js";
import styles from "../components/UrgencyIndicator/UrgencyBadge.module.css";

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("UrgencyBadge", () => {
  // ── Band rendering ──────────────────────────────────────────────────────────

  it("renders ON_TRACK band with correct text and CSS class", () => {
    render(<UrgencyBadge urgencyBand="ON_TRACK" />);
    const badge = screen.getByTestId("urgency-badge");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-urgency-band", "ON_TRACK");
    expect(badge).toHaveTextContent("On Track");
    expect(badge).toHaveClass(styles.onTrack);
  });

  it("renders NEEDS_ATTENTION band with correct text and CSS class", () => {
    render(<UrgencyBadge urgencyBand="NEEDS_ATTENTION" />);
    const badge = screen.getByTestId("urgency-badge");
    expect(badge).toHaveAttribute("data-urgency-band", "NEEDS_ATTENTION");
    expect(badge).toHaveTextContent("Attention");
    expect(badge).toHaveClass(styles.needsAttention);
  });

  it("renders AT_RISK band with correct text and CSS class", () => {
    render(<UrgencyBadge urgencyBand="AT_RISK" />);
    const badge = screen.getByTestId("urgency-badge");
    expect(badge).toHaveAttribute("data-urgency-band", "AT_RISK");
    expect(badge).toHaveTextContent("At Risk");
    expect(badge).toHaveClass(styles.atRisk);
  });

  it("renders CRITICAL band with correct text and CSS class", () => {
    render(<UrgencyBadge urgencyBand="CRITICAL" />);
    const badge = screen.getByTestId("urgency-badge");
    expect(badge).toHaveAttribute("data-urgency-band", "CRITICAL");
    expect(badge).toHaveTextContent("Critical");
    expect(badge).toHaveClass(styles.critical);
  });

  it("renders NO_TARGET band with correct text and CSS class", () => {
    render(<UrgencyBadge urgencyBand="NO_TARGET" />);
    const badge = screen.getByTestId("urgency-badge");
    expect(badge).toHaveAttribute("data-urgency-band", "NO_TARGET");
    expect(badge).toHaveTextContent("No Target");
    expect(badge).toHaveClass(styles.noTarget);
  });

  // ── Size variants ───────────────────────────────────────────────────────────

  it("renders with sm size class when size='sm'", () => {
    render(<UrgencyBadge urgencyBand="ON_TRACK" size="sm" />);
    const badge = screen.getByTestId("urgency-badge");
    expect(badge).toHaveClass(styles.sm);
    expect(badge).not.toHaveClass(styles.md);
  });

  it("renders with md size class when size='md'", () => {
    render(<UrgencyBadge urgencyBand="ON_TRACK" size="md" />);
    const badge = screen.getByTestId("urgency-badge");
    expect(badge).toHaveClass(styles.md);
    expect(badge).not.toHaveClass(styles.sm);
  });

  it("defaults to md size class when size is omitted", () => {
    render(<UrgencyBadge urgencyBand="AT_RISK" />);
    const badge = screen.getByTestId("urgency-badge");
    expect(badge).toHaveClass(styles.md);
    expect(badge).not.toHaveClass(styles.sm);
  });

  // ── Aria-label ──────────────────────────────────────────────────────────────

  it("has aria-label 'Urgency: On Track' for ON_TRACK", () => {
    render(<UrgencyBadge urgencyBand="ON_TRACK" />);
    expect(screen.getByLabelText("Urgency: On Track")).toBeInTheDocument();
  });

  it("has aria-label 'Urgency: Needs Attention' for NEEDS_ATTENTION", () => {
    render(<UrgencyBadge urgencyBand="NEEDS_ATTENTION" />);
    expect(screen.getByLabelText("Urgency: Needs Attention")).toBeInTheDocument();
  });

  it("has aria-label 'Urgency: At Risk' for AT_RISK", () => {
    render(<UrgencyBadge urgencyBand="AT_RISK" />);
    expect(screen.getByLabelText("Urgency: At Risk")).toBeInTheDocument();
  });

  it("has aria-label 'Urgency: Critical' for CRITICAL", () => {
    render(<UrgencyBadge urgencyBand="CRITICAL" />);
    expect(screen.getByLabelText("Urgency: Critical")).toBeInTheDocument();
  });

  it("has aria-label 'Urgency: No Target' for NO_TARGET", () => {
    render(<UrgencyBadge urgencyBand="NO_TARGET" />);
    expect(screen.getByLabelText("Urgency: No Target")).toBeInTheDocument();
  });

  // ── data-testid and data-urgency-band attributes ────────────────────────────

  it("exposes data-urgency-band attribute for test queries", () => {
    render(<UrgencyBadge urgencyBand="CRITICAL" />);
    expect(screen.getByTestId("urgency-badge")).toHaveAttribute("data-urgency-band", "CRITICAL");
  });

  it("normalises lowercase urgency band string to uppercase for data attribute", () => {
    render(<UrgencyBadge urgencyBand="at_risk" />);
    const badge = screen.getByTestId("urgency-badge");
    expect(badge).toHaveAttribute("data-urgency-band", "AT_RISK");
  });
});
