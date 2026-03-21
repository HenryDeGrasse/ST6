import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StrategicSlackBanner } from "../components/UrgencyIndicator/StrategicSlackBanner.js";
import type { SlackInfo } from "../hooks/useOutcomeMetadata.js";
import styles from "../components/UrgencyIndicator/StrategicSlackBanner.module.css";

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Conditionally renders StrategicSlackBanner only when slackData is non-null,
 * mirroring the pattern used in TeamDashboardPage:
 *   {flags.strategicSlack && strategicSlack && <StrategicSlackBanner ... />}
 */
function ConditionalBanner({ slackData }: { slackData: SlackInfo | null }) {
  if (!slackData) return null;
  return (
    <StrategicSlackBanner
      slackBand={slackData.slackBand}
      strategicFocusFloor={slackData.strategicFocusFloor}
      atRiskCount={slackData.atRiskCount}
      criticalCount={slackData.criticalCount}
    />
  );
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("StrategicSlackBanner", () => {
  // ── Null / no-data handling ─────────────────────────────────────────────────

  it("renders nothing when slackData is null (no-data guard)", () => {
    const { container } = render(<ConditionalBanner slackData={null} />);
    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId("strategic-slack-banner")).not.toBeInTheDocument();
  });

  // ── Slack band colour / CSS class ───────────────────────────────────────────

  it("renders the HIGH_SLACK banner with correct CSS class", () => {
    render(
      <StrategicSlackBanner
        slackBand="HIGH_SLACK"
        strategicFocusFloor={0.8}
        atRiskCount={0}
        criticalCount={0}
      />,
    );
    const banner = screen.getByTestId("strategic-slack-banner");
    expect(banner).toHaveClass(styles.highSlack);
  });

  it("renders the MODERATE_SLACK banner with correct CSS class", () => {
    render(
      <StrategicSlackBanner
        slackBand="MODERATE_SLACK"
        strategicFocusFloor={0.75}
        atRiskCount={1}
        criticalCount={0}
      />,
    );
    const banner = screen.getByTestId("strategic-slack-banner");
    expect(banner).toHaveClass(styles.moderate);
  });

  it("renders the LOW_SLACK banner with correct CSS class", () => {
    render(
      <StrategicSlackBanner
        slackBand="LOW_SLACK"
        strategicFocusFloor={0.65}
        atRiskCount={2}
        criticalCount={1}
      />,
    );
    const banner = screen.getByTestId("strategic-slack-banner");
    expect(banner).toHaveClass(styles.low);
  });

  it("renders the NO_SLACK banner with correct CSS class", () => {
    render(
      <StrategicSlackBanner
        slackBand="NO_SLACK"
        strategicFocusFloor={0.5}
        atRiskCount={3}
        criticalCount={2}
      />,
    );
    const banner = screen.getByTestId("strategic-slack-banner");
    expect(banner).toHaveClass(styles.noSlack);
  });

  // ── Band label text ─────────────────────────────────────────────────────────

  it("shows the HIGH label text for HIGH_SLACK band", () => {
    render(
      <StrategicSlackBanner
        slackBand="HIGH_SLACK"
        strategicFocusFloor={0.8}
        atRiskCount={0}
        criticalCount={0}
      />,
    );
    expect(screen.getByTestId("strategic-slack-band-label")).toHaveTextContent("HIGH");
  });

  it("shows the MODERATE label text for MODERATE_SLACK band", () => {
    render(
      <StrategicSlackBanner
        slackBand="MODERATE_SLACK"
        strategicFocusFloor={0.75}
        atRiskCount={1}
        criticalCount={0}
      />,
    );
    expect(screen.getByTestId("strategic-slack-band-label")).toHaveTextContent("MODERATE");
  });

  it("shows the LOW label text for LOW_SLACK band", () => {
    render(
      <StrategicSlackBanner
        slackBand="LOW_SLACK"
        strategicFocusFloor={0.65}
        atRiskCount={1}
        criticalCount={0}
      />,
    );
    expect(screen.getByTestId("strategic-slack-band-label")).toHaveTextContent("LOW");
  });

  it("shows the NO SLACK label text for NO_SLACK band", () => {
    render(
      <StrategicSlackBanner
        slackBand="NO_SLACK"
        strategicFocusFloor={0.5}
        atRiskCount={2}
        criticalCount={1}
      />,
    );
    expect(screen.getByTestId("strategic-slack-band-label")).toHaveTextContent("NO SLACK");
  });

  // ── Floor percentage ────────────────────────────────────────────────────────

  it("shows the strategic focus floor as a percentage", () => {
    render(
      <StrategicSlackBanner
        slackBand="HIGH_SLACK"
        strategicFocusFloor={0.8}
        atRiskCount={0}
        criticalCount={0}
      />,
    );
    expect(screen.getByTestId("strategic-slack-floor-hint")).toHaveTextContent("80%");
  });

  it("shows 75% floor for strategicFocusFloor=0.75", () => {
    render(
      <StrategicSlackBanner
        slackBand="MODERATE_SLACK"
        strategicFocusFloor={0.75}
        atRiskCount={1}
        criticalCount={0}
      />,
    );
    expect(screen.getByTestId("strategic-slack-floor-hint")).toHaveTextContent("75%");
  });

  it("shows 50% floor for strategicFocusFloor=0.5", () => {
    render(
      <StrategicSlackBanner
        slackBand="NO_SLACK"
        strategicFocusFloor={0.5}
        atRiskCount={3}
        criticalCount={2}
      />,
    );
    expect(screen.getByTestId("strategic-slack-floor-hint")).toHaveTextContent("50%");
  });

  // ── At-risk and critical count text ────────────────────────────────────────

  it("shows combined attention count in the attention text", () => {
    render(
      <StrategicSlackBanner
        slackBand="LOW_SLACK"
        strategicFocusFloor={0.65}
        atRiskCount={2}
        criticalCount={1}
      />,
    );
    // 2 at-risk + 1 critical = 3 outcomes need attention
    expect(screen.getByTestId("strategic-slack-attention-text")).toHaveTextContent(
      "3 outcomes need attention",
    );
  });

  it("uses singular 'outcome' when total attention count is 1", () => {
    render(
      <StrategicSlackBanner
        slackBand="MODERATE_SLACK"
        strategicFocusFloor={0.75}
        atRiskCount={1}
        criticalCount={0}
      />,
    );
    expect(screen.getByTestId("strategic-slack-attention-text")).toHaveTextContent(
      "1 outcome needs attention",
    );
  });

  it("shows zero outcomes need attention when counts are both 0", () => {
    render(
      <StrategicSlackBanner
        slackBand="HIGH_SLACK"
        strategicFocusFloor={0.9}
        atRiskCount={0}
        criticalCount={0}
      />,
    );
    expect(screen.getByTestId("strategic-slack-attention-text")).toHaveTextContent(
      "0 outcomes need attention",
    );
  });

  // ── Accessibility ───────────────────────────────────────────────────────────

  it("has role=status for live region accessibility", () => {
    render(
      <StrategicSlackBanner
        slackBand="HIGH_SLACK"
        strategicFocusFloor={0.8}
        atRiskCount={0}
        criticalCount={0}
      />,
    );
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("has aria-label describing the slack level", () => {
    render(
      <StrategicSlackBanner
        slackBand="NO_SLACK"
        strategicFocusFloor={0.5}
        atRiskCount={3}
        criticalCount={2}
      />,
    );
    expect(screen.getByRole("status")).toHaveAttribute(
      "aria-label",
      "Strategic slack: NO SLACK",
    );
  });
});
