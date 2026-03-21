import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { OvercommitBanner } from "../components/CapacityView/OvercommitBanner.js";
import type { OvercommitLevel } from "../components/CapacityView/OvercommitBanner.js";
import styles from "../components/CapacityView/OvercommitBanner.module.css";

// ─── Helpers ─────────────────────────────────────────────────────────────────

const defaultProps = {
  level: "MODERATE" as OvercommitLevel,
  message: "You have committed more than your realistic capacity allows.",
  adjustedTotal: 42,
  realisticCap: 35,
};

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("OvercommitBanner", () => {
  it("renders nothing when level is NONE", () => {
    const { container } = render(<OvercommitBanner {...defaultProps} level="NONE" />);
    expect(container.firstChild).toBeNull();
  });

  it("renders the yellow/amber banner variant when level is MODERATE", () => {
    render(<OvercommitBanner {...defaultProps} level="MODERATE" />);
    expect(screen.getByTestId("overcommit-banner")).toHaveClass(styles.moderate);
  });

  it("renders the red banner variant when level is HIGH", () => {
    render(<OvercommitBanner {...defaultProps} level="HIGH" />);
    expect(screen.getByTestId("overcommit-banner")).toHaveClass(styles.high);
  });

  it("displays the message for MODERATE level", () => {
    render(
      <OvercommitBanner
        {...defaultProps}
        level="MODERATE"
        message="You are moderately overcommitted this week."
      />,
    );
    expect(screen.getByTestId("overcommit-banner")).toHaveTextContent(
      "You are moderately overcommitted this week.",
    );
  });

  it("displays the message for HIGH level", () => {
    render(
      <OvercommitBanner
        {...defaultProps}
        level="HIGH"
        message="Your commitment load is critically high."
      />,
    );
    expect(screen.getByTestId("overcommit-banner")).toHaveTextContent(
      "Your commitment load is critically high.",
    );
  });

  it("shows adjusted total hours in the banner", () => {
    render(<OvercommitBanner {...defaultProps} level="MODERATE" adjustedTotal={42} />);
    expect(screen.getByTestId("overcommit-banner")).toHaveTextContent("42h");
  });

  it("shows realistic cap hours in the banner", () => {
    render(<OvercommitBanner {...defaultProps} level="MODERATE" realisticCap={35} />);
    expect(screen.getByTestId("overcommit-banner")).toHaveTextContent("35h");
  });

  it("shows both adjusted total and realistic cap together", () => {
    render(
      <OvercommitBanner {...defaultProps} level="MODERATE" adjustedTotal={50} realisticCap={40} />,
    );
    const banner = screen.getByTestId("overcommit-banner");
    expect(banner).toHaveTextContent("50h");
    expect(banner).toHaveTextContent("40h");
  });

  it("renders the MODERATE level tag for MODERATE severity", () => {
    render(<OvercommitBanner {...defaultProps} level="MODERATE" />);
    expect(screen.getByTestId("overcommit-level-MODERATE")).toBeInTheDocument();
  });

  it("renders the HIGH level tag for HIGH severity", () => {
    render(<OvercommitBanner {...defaultProps} level="HIGH" />);
    expect(screen.getByTestId("overcommit-level-HIGH")).toBeInTheDocument();
  });

  it("does not render the MODERATE level tag when level is HIGH", () => {
    render(<OvercommitBanner {...defaultProps} level="HIGH" />);
    expect(screen.queryByTestId("overcommit-level-MODERATE")).not.toBeInTheDocument();
  });

  it("uses the ⚠️ icon for MODERATE", () => {
    render(<OvercommitBanner {...defaultProps} level="MODERATE" />);
    expect(screen.getByTestId("overcommit-banner")).toHaveTextContent("⚠️");
  });

  it("uses the ⛔ icon for HIGH", () => {
    render(<OvercommitBanner {...defaultProps} level="HIGH" />);
    expect(screen.getByTestId("overcommit-banner")).toHaveTextContent("⛔");
  });

  it("has role=alert for screen reader accessibility", () => {
    render(<OvercommitBanner {...defaultProps} level="MODERATE" />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});
