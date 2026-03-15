import { describe, it, expect, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { AiManagerInsightsPanel } from "../components/AiManagerInsightsPanel.js";
import { FeatureFlagProvider } from "../context/FeatureFlagContext.js";
import type { ManagerInsightItem } from "@weekly-commitments/contracts";

function renderWithFlags(
  ui: React.ReactElement,
  flags: { managerInsights: boolean } = { managerInsights: true },
) {
  return render(
    <FeatureFlagProvider flags={flags}>{ui}</FeatureFlagProvider>,
  );
}

function makeInsight(overrides: Partial<ManagerInsightItem> = {}): ManagerInsightItem {
  return {
    title: "Review hotspot",
    detail: "One report still needs follow-up review.",
    severity: "WARNING",
    ...overrides,
  };
}

describe("AiManagerInsightsPanel", () => {
  const defaultProps = {
    status: "idle" as const,
    headline: null,
    insights: [] as ManagerInsightItem[],
    onRefresh: vi.fn(),
  };

  it("renders nothing when the feature flag is disabled", () => {
    const { container } = render(
      <FeatureFlagProvider flags={{ managerInsights: false }}>
        <AiManagerInsightsPanel {...defaultProps} />
      </FeatureFlagProvider>,
    );

    expect(container.innerHTML).toBe("");
  });

  it("shows beta labeling and refresh control", () => {
    renderWithFlags(<AiManagerInsightsPanel {...defaultProps} />);

    expect(screen.getByText(/AI Manager Insights/)).toBeInTheDocument();
    expect(screen.getByText(/Beta — summary only/)).toBeInTheDocument();
    expect(screen.getByTestId("ai-manager-insights-refresh")).toBeInTheDocument();
  });

  it("calls onRefresh when refresh is clicked", () => {
    const onRefresh = vi.fn();
    renderWithFlags(
      <AiManagerInsightsPanel {...defaultProps} onRefresh={onRefresh} />,
    );

    fireEvent.click(screen.getByTestId("ai-manager-insights-refresh"));
    expect(onRefresh).toHaveBeenCalled();
  });

  it("renders loading state", () => {
    renderWithFlags(
      <AiManagerInsightsPanel {...defaultProps} status="loading" />,
    );

    expect(screen.getByTestId("ai-manager-insights-loading")).toBeInTheDocument();
  });

  it("renders unavailable state", () => {
    renderWithFlags(
      <AiManagerInsightsPanel {...defaultProps} status="unavailable" />,
    );

    expect(screen.getByTestId("ai-manager-insights-unavailable")).toBeInTheDocument();
  });

  it("renders headline and insight bullets when available", () => {
    renderWithFlags(
      <AiManagerInsightsPanel
        {...defaultProps}
        status="ok"
        headline="Team focus is healthy overall."
        insights={[makeInsight()]}
      />,
    );

    expect(screen.getByTestId("ai-manager-insights-content")).toBeInTheDocument();
    expect(screen.getByText("Team focus is healthy overall.")).toBeInTheDocument();
    expect(screen.getByTestId("ai-manager-insight-0")).toBeInTheDocument();
    expect(screen.getByText(/Review hotspot/)).toBeInTheDocument();
  });
});
