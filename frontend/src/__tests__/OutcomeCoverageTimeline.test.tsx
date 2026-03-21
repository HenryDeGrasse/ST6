import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { OutcomeCoverageTimeline } from "../components/StrategicIntelligence/OutcomeCoverageTimeline.js";
import { FeatureFlagProvider } from "../context/FeatureFlagContext.js";
import type { OutcomeCoverageTimeline as OutcomeCoverageTimelineData } from "@weekly-commitments/contracts";

/* ── Helpers ──────────────────────────────────────────────────────────────── */

function renderWithFlags(
  ui: React.ReactElement,
  flags: { strategicIntelligence: boolean } = { strategicIntelligence: true },
) {
  return render(<FeatureFlagProvider flags={flags}>{ui}</FeatureFlagProvider>);
}

function makeData(overrides: Partial<OutcomeCoverageTimelineData> = {}): OutcomeCoverageTimelineData {
  return {
    outcomeId: "oc-1",
    weeks: [
      { weekStart: "2026-01-05", commitCount: 4, contributorCount: 2, highPriorityCount: 1 },
      { weekStart: "2026-01-12", commitCount: 7, contributorCount: 3, highPriorityCount: 2 },
      { weekStart: "2026-01-19", commitCount: 2, contributorCount: 1, highPriorityCount: 0 },
    ],
    trendDirection: "RISING",
    ...overrides,
  };
}

const defaultProps = {
  outcomeId: "oc-1",
  outcomeName: "Revenue Growth",
  data: null as OutcomeCoverageTimelineData | null,
  loading: false,
};

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("OutcomeCoverageTimeline", () => {
  it("renders nothing when the strategicIntelligence flag is disabled", () => {
    const { container } = renderWithFlags(
      <OutcomeCoverageTimeline {...defaultProps} />,
      { strategicIntelligence: false },
    );

    expect(container.innerHTML).toBe("");
  });

  it("renders the timeline panel when the flag is enabled", () => {
    renderWithFlags(<OutcomeCoverageTimeline {...defaultProps} />);

    expect(screen.getByTestId("outcome-coverage-timeline")).toBeInTheDocument();
    expect(screen.getByText("Revenue Growth")).toBeInTheDocument();
  });

  it("shows loading state when loading=true", () => {
    renderWithFlags(<OutcomeCoverageTimeline {...defaultProps} loading={true} />);

    expect(screen.getByTestId("outcome-coverage-loading")).toBeInTheDocument();
    expect(screen.getByText("Loading coverage data…")).toBeInTheDocument();
  });

  it("shows empty state when data is null and not loading", () => {
    renderWithFlags(<OutcomeCoverageTimeline {...defaultProps} data={null} loading={false} />);

    expect(screen.getByTestId("outcome-coverage-empty")).toBeInTheDocument();
    expect(screen.getByText("No coverage data available.")).toBeInTheDocument();
  });

  it("shows no-weeks state when data has an empty weeks array", () => {
    const emptyData = makeData({ weeks: [] });
    renderWithFlags(<OutcomeCoverageTimeline {...defaultProps} data={emptyData} />);

    expect(screen.getByTestId("outcome-coverage-no-weeks")).toBeInTheDocument();
    expect(screen.getByText("No weekly data recorded yet.")).toBeInTheDocument();
  });

  it("renders week bars with correct data-testid values", () => {
    const data = makeData();
    renderWithFlags(<OutcomeCoverageTimeline {...defaultProps} data={data} />);

    expect(screen.getByTestId("coverage-week-2026-01-05")).toBeInTheDocument();
    expect(screen.getByTestId("coverage-week-2026-01-12")).toBeInTheDocument();
    expect(screen.getByTestId("coverage-week-2026-01-19")).toBeInTheDocument();
  });

  it("renders all week bars with correct commit counts", () => {
    const data = makeData();
    renderWithFlags(<OutcomeCoverageTimeline {...defaultProps} data={data} />);

    // Each week column shows commit count and contributor count
    const week1 = screen.getByTestId("coverage-week-2026-01-05");
    const week2 = screen.getByTestId("coverage-week-2026-01-12");
    expect(week1).toHaveTextContent("4");
    expect(week2).toHaveTextContent("7");
  });

  it("renders contributor count alongside commit count", () => {
    const data = makeData();
    renderWithFlags(<OutcomeCoverageTimeline {...defaultProps} data={data} />);

    // "2p" means 2 contributors
    expect(screen.getByTestId("coverage-week-2026-01-05")).toHaveTextContent("2p");
    expect(screen.getByTestId("coverage-week-2026-01-12")).toHaveTextContent("3p");
  });

  it("shows the trend indicator when data is provided", () => {
    const data = makeData({ trendDirection: "RISING" });
    renderWithFlags(<OutcomeCoverageTimeline {...defaultProps} data={data} />);

    const indicator = screen.getByTestId("coverage-trend-indicator");
    expect(indicator).toBeInTheDocument();
    expect(indicator).toHaveTextContent("↑");
    expect(indicator).toHaveAttribute("aria-label", "Coverage trend: Rising");
  });

  it("shows FALLING trend arrow for FALLING trendDirection", () => {
    const data = makeData({ trendDirection: "FALLING" });
    renderWithFlags(<OutcomeCoverageTimeline {...defaultProps} data={data} />);

    const indicator = screen.getByTestId("coverage-trend-indicator");
    expect(indicator).toHaveTextContent("↓");
    expect(indicator).toHaveAttribute("aria-label", "Coverage trend: Falling");
  });

  it("shows STABLE trend arrow for STABLE trendDirection", () => {
    const data = makeData({ trendDirection: "STABLE" });
    renderWithFlags(<OutcomeCoverageTimeline {...defaultProps} data={data} />);

    const indicator = screen.getByTestId("coverage-trend-indicator");
    expect(indicator).toHaveTextContent("→");
    expect(indicator).toHaveAttribute("aria-label", "Coverage trend: Stable");
  });

  it("does not render trend indicator when data is null", () => {
    renderWithFlags(<OutcomeCoverageTimeline {...defaultProps} data={null} />);

    expect(screen.queryByTestId("coverage-trend-indicator")).not.toBeInTheDocument();
  });

  it("shows MM-DD week label from ISO date in week columns", () => {
    const data = makeData();
    renderWithFlags(<OutcomeCoverageTimeline {...defaultProps} data={data} />);

    // ISO "2026-01-05" → should display "01-05"
    const week = screen.getByTestId("coverage-week-2026-01-05");
    expect(week).toHaveTextContent("01-05");
  });

  it("displays the outcome name in the header", () => {
    const data = makeData();
    renderWithFlags(
      <OutcomeCoverageTimeline
        {...defaultProps}
        outcomeName="Market Expansion"
        data={data}
      />,
    );

    expect(screen.getByText("Market Expansion")).toBeInTheDocument();
  });

  it("sets the correct data-outcome-id attribute", () => {
    renderWithFlags(
      <OutcomeCoverageTimeline {...defaultProps} outcomeId="oc-42" />,
    );

    const panel = screen.getByTestId("outcome-coverage-timeline");
    expect(panel).toHaveAttribute("data-outcome-id", "oc-42");
  });
});
