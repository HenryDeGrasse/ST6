import { describe, it, expect } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { MyTrendsPanel } from "../components/MyTrendsPanel.js";
import { FeatureFlagProvider } from "../context/FeatureFlagContext.js";
import type { TrendsResponse } from "@weekly-commitments/contracts";

/* ── Helpers ──────────────────────────────────────────────────────────────── */

function renderWithFlags(
  ui: React.ReactElement,
  flags: { icTrends: boolean } = { icTrends: true },
) {
  return render(<FeatureFlagProvider flags={flags}>{ui}</FeatureFlagProvider>);
}

function makeTrends(overrides: Partial<TrendsResponse> = {}): TrendsResponse {
  return {
    weeksAnalyzed: 6,
    windowStart: "2026-02-02",
    windowEnd: "2026-03-09",
    strategicAlignmentRate: 0.8,
    teamStrategicAlignmentRate: 0.65,
    avgCarryForwardPerWeek: 0.5,
    carryForwardStreak: 1,
    avgConfidence: 0.75,
    completionAccuracy: 0.72,
    confidenceAccuracyGap: 0.03,
    avgEstimatedHoursPerWeek: 15,
    avgActualHoursPerWeek: 14,
    hoursAccuracyRatio: 0.93,
    priorityDistribution: {},
    categoryDistribution: {},
    weekPoints: [],
    insights: [],
    ...overrides,
  };
}

const defaultProps = {
  trends: null,
  loading: false,
  error: null,
};

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("MyTrendsPanel", () => {
  it("renders nothing when the icTrends flag is disabled", () => {
    const { container } = renderWithFlags(<MyTrendsPanel {...defaultProps} />, { icTrends: false });
    expect(container.innerHTML).toBe("");
  });

  it("renders the panel header when the flag is enabled", () => {
    renderWithFlags(<MyTrendsPanel {...defaultProps} />);
    expect(screen.getByTestId("my-trends-panel")).toBeInTheDocument();
    expect(screen.getByText("My Trends")).toBeInTheDocument();
  });

  it("renders a Show/Hide toggle button", () => {
    renderWithFlags(<MyTrendsPanel {...defaultProps} />);
    const toggle = screen.getByTestId("my-trends-toggle");
    expect(toggle).toBeInTheDocument();
    expect(toggle).toHaveTextContent("Show");
    expect(toggle).toHaveAttribute("aria-expanded", "false");
  });

  it("does not show content area when collapsed", () => {
    renderWithFlags(<MyTrendsPanel {...defaultProps} />);
    expect(screen.queryByTestId("my-trends-content")).not.toBeInTheDocument();
  });

  it("expands and collapses when the toggle is clicked", () => {
    renderWithFlags(<MyTrendsPanel {...defaultProps} />);
    const toggle = screen.getByTestId("my-trends-toggle");

    fireEvent.click(toggle);

    expect(screen.getByTestId("my-trends-content")).toBeInTheDocument();
    expect(toggle).toHaveTextContent("Hide");
    expect(toggle).toHaveAttribute("aria-expanded", "true");

    fireEvent.click(toggle);

    expect(screen.queryByTestId("my-trends-content")).not.toBeInTheDocument();
    expect(toggle).toHaveTextContent("Show");
    expect(toggle).toHaveAttribute("aria-expanded", "false");
  });

  it("shows a loading indicator when loading=true", () => {
    renderWithFlags(<MyTrendsPanel {...defaultProps} loading={true} />);
    fireEvent.click(screen.getByTestId("my-trends-toggle"));

    expect(screen.getByTestId("my-trends-loading")).toBeInTheDocument();
  });

  it("shows an error message when error is set", () => {
    renderWithFlags(<MyTrendsPanel {...defaultProps} error="Network error" />);
    fireEvent.click(screen.getByTestId("my-trends-toggle"));

    expect(screen.getByTestId("my-trends-error")).toBeInTheDocument();
    expect(screen.getByText("Network error")).toBeInTheDocument();
  });

  it("shows the empty state when there is no data", () => {
    renderWithFlags(<MyTrendsPanel {...defaultProps} trends={null} />);
    fireEvent.click(screen.getByTestId("my-trends-toggle"));

    expect(screen.getByTestId("my-trends-empty")).toBeInTheDocument();
  });

  it("renders metric cards when data is available", () => {
    renderWithFlags(<MyTrendsPanel {...defaultProps} trends={makeTrends()} />);
    fireEvent.click(screen.getByTestId("my-trends-toggle"));

    expect(screen.getByTestId("my-trends-metrics")).toBeInTheDocument();
    expect(screen.getByTestId("metric-strategic-alignment")).toBeInTheDocument();
    expect(screen.getByTestId("metric-completion-accuracy")).toBeInTheDocument();
    expect(screen.getByTestId("metric-avg-confidence")).toBeInTheDocument();
    expect(screen.getByTestId("metric-carry-forward")).toBeInTheDocument();
  });

  it("formats rates as percentages in metric cards", () => {
    renderWithFlags(
      <MyTrendsPanel
        {...defaultProps}
        trends={makeTrends({
          strategicAlignmentRate: 0.8,
          teamStrategicAlignmentRate: 0.65,
          completionAccuracy: 0.72,
          avgConfidence: 0.75,
        })}
      />,
    );
    fireEvent.click(screen.getByTestId("my-trends-toggle"));

    expect(screen.getByTestId("metric-strategic-alignment")).toHaveTextContent("80%");
    expect(screen.getByTestId("metric-strategic-alignment")).toHaveTextContent("65%");
    expect(screen.getByTestId("metric-completion-accuracy")).toHaveTextContent("72%");
    expect(screen.getByTestId("metric-avg-confidence")).toHaveTextContent("75%");
  });

  it("shows carry-forward streak hint when streak > 0", () => {
    renderWithFlags(<MyTrendsPanel {...defaultProps} trends={makeTrends({ carryForwardStreak: 3 })} />);
    fireEvent.click(screen.getByTestId("my-trends-toggle"));

    expect(screen.getByTestId("metric-streak")).toBeInTheDocument();
    expect(screen.getByTestId("metric-streak")).toHaveTextContent("3-week streak");
  });

  it("hides carry-forward streak hint when streak = 0", () => {
    renderWithFlags(<MyTrendsPanel {...defaultProps} trends={makeTrends({ carryForwardStreak: 0 })} />);
    fireEvent.click(screen.getByTestId("my-trends-toggle"));

    expect(screen.queryByTestId("metric-streak")).not.toBeInTheDocument();
  });

  it("shows confidence-accuracy gap hint when gap > 0.05", () => {
    renderWithFlags(
      <MyTrendsPanel {...defaultProps} trends={makeTrends({ confidenceAccuracyGap: 0.15 })} />,
    );
    fireEvent.click(screen.getByTestId("my-trends-toggle"));

    expect(screen.getByTestId("metric-gap-hint")).toBeInTheDocument();
  });

  it("hides confidence-accuracy gap hint when gap ≤ 0.05", () => {
    renderWithFlags(
      <MyTrendsPanel {...defaultProps} trends={makeTrends({ confidenceAccuracyGap: 0.04 })} />,
    );
    fireEvent.click(screen.getByTestId("my-trends-toggle"));

    expect(screen.queryByTestId("metric-gap-hint")).not.toBeInTheDocument();
  });

  it("renders insight badges with correct severity labels", () => {
    renderWithFlags(
      <MyTrendsPanel
        {...defaultProps}
        trends={makeTrends({
          insights: [
            { type: "CARRY_FORWARD_STREAK", message: "You have a long carry-forward streak.", severity: "WARNING" },
            { type: "HIGH_ALIGNMENT", message: "Strategic alignment is above average.", severity: "POSITIVE" },
            { type: "LOW_CONFIDENCE", message: "Confidence data is sparse.", severity: "INFO" },
          ],
        })}
      />,
    );
    fireEvent.click(screen.getByTestId("my-trends-toggle"));

    expect(screen.getByTestId("my-trends-insights")).toBeInTheDocument();
    expect(screen.getByTestId("trend-insight-0")).toBeInTheDocument();
    expect(screen.getByTestId("trend-insight-badge-0")).toHaveTextContent("Note");
    expect(screen.getByTestId("trend-insight-badge-1")).toHaveTextContent("Great");
    expect(screen.getByTestId("trend-insight-badge-2")).toHaveTextContent("Info");
    expect(screen.getByText("You have a long carry-forward streak.")).toBeInTheDocument();
  });

  it("hides the insight list when there are no insights", () => {
    renderWithFlags(<MyTrendsPanel {...defaultProps} trends={makeTrends({ insights: [] })} />);
    fireEvent.click(screen.getByTestId("my-trends-toggle"));

    expect(screen.queryByTestId("my-trends-insights")).not.toBeInTheDocument();
  });

  it("shows weeks-analyzed in completion accuracy card", () => {
    renderWithFlags(<MyTrendsPanel {...defaultProps} trends={makeTrends({ weeksAnalyzed: 8 })} />);
    fireEvent.click(screen.getByTestId("my-trends-toggle"));

    expect(screen.getByTestId("metric-completion-accuracy")).toHaveTextContent("8 weeks analyzed");
  });
});
