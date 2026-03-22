import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { OutcomeRiskCard } from "../components/Phase5/OutcomeRiskCard.js";

const mockFetchForecast = vi.fn();
const mockUseForecasts = vi.fn();
let mockTargetDateForecasting = true;

vi.mock("../hooks/useForecasts.js", () => ({ useForecasts: () => mockUseForecasts() }));
vi.mock("../context/FeatureFlagContext.js", () => ({ useFeatureFlags: () => ({ targetDateForecasting: mockTargetDateForecasting }) }));

describe("OutcomeRiskCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockTargetDateForecasting = true;
    mockUseForecasts.mockReturnValue({ selectedForecast: null, loadingForecast: false, errorForecast: null, fetchForecast: mockFetchForecast });
  });

  it("renders nothing when feature flag is disabled", () => {
    mockTargetDateForecasting = false;
    const { container } = render(<OutcomeRiskCard outcomeId="o1" />);
    expect(container.firstChild).toBeNull();
  });

  it("renders forecast details", () => {
    mockUseForecasts.mockReturnValue({ selectedForecast: { outcomeId: "o1", outcomeName: "Improve Margin", targetDate: "2026-06-01", projectedTargetDate: "2026-06-15", projectedProgressPct: 67, projectedVelocity: null, confidenceScore: 0.82, confidenceBand: "HIGH", forecastStatus: "NEEDS_ATTENTION", modelVersion: null, contributingFactors: [{ type: "velocity", label: "Velocity", score: 0.5, detail: "Slipping" }], recommendations: ["Add a delivery checkpoint"], computedAt: null }, loadingForecast: false, errorForecast: null, fetchForecast: mockFetchForecast });
    render(<OutcomeRiskCard outcomeId="o1" />);
    expect(screen.getByTestId("outcome-risk-card")).toBeInTheDocument();
    expect(screen.getByText("Improve Margin")).toBeInTheDocument();
    expect(screen.getByTestId("outcome-risk-status")).toHaveTextContent("NEEDS_ATTENTION");
    expect(screen.getByTestId("outcome-risk-recommendation-0")).toHaveTextContent("Add a delivery checkpoint");
  });
});
