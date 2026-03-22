import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ExecutiveHealth } from "../components/Phase5/ExecutiveHealth.js";

const mockFetchDashboard = vi.fn();
const mockUseExecutiveDashboard = vi.fn();
let mockExecutiveDashboard = true;

vi.mock("../hooks/useExecutiveDashboard.js", () => ({ useExecutiveDashboard: () => mockUseExecutiveDashboard() }));
vi.mock("../context/FeatureFlagContext.js", () => ({ useFeatureFlags: () => ({ executiveDashboard: mockExecutiveDashboard }) }));

describe("ExecutiveHealth", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockExecutiveDashboard = true;
    mockUseExecutiveDashboard.mockReturnValue({ dashboard: null, dashboardStatus: "idle", errorDashboard: null, fetchDashboard: mockFetchDashboard });
  });

  it("renders summary metrics", () => {
    mockUseExecutiveDashboard.mockReturnValue({ dashboard: { weekStart: "2026-03-16", summary: { totalForecasts: 4, onTrackForecasts: 2, needsAttentionForecasts: 1, offTrackForecasts: 1, noDataForecasts: 0, averageForecastConfidence: 0.7, totalCapacityHours: 80, strategicHours: 40, nonStrategicHours: 40, strategicCapacityUtilizationPct: 50, nonStrategicCapacityUtilizationPct: 50, planningCoveragePct: 90 }, rallyCryRollups: [{ rallyCryId: null, rallyCryName: "Grow", forecastedOutcomeCount: 2, onTrackCount: 1, needsAttentionCount: 1, offTrackCount: 0, noDataCount: 0, averageForecastConfidence: 0.7, strategicHours: 20 }], teamBuckets: [{ bucketId: "North", memberCount: 5, planCoveragePct: 95, totalCapacityHours: 50, strategicHours: 25, nonStrategicHours: 25, strategicCapacityUtilizationPct: 50, averageForecastConfidence: 0.75 }], teamGroupingAvailable: true }, dashboardStatus: "ok", errorDashboard: null, fetchDashboard: mockFetchDashboard });
    render(<ExecutiveHealth weekStart="2026-03-16" />);
    expect(screen.getByTestId("executive-health-summary")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("executive-health-refresh"));
    expect(mockFetchDashboard).toHaveBeenCalledWith("2026-03-16");
  });

  it("shows dashboard errors without hiding the refresh control", () => {
    mockUseExecutiveDashboard.mockReturnValue({ dashboard: null, dashboardStatus: "unavailable", errorDashboard: "Request failed (500)", fetchDashboard: mockFetchDashboard });
    render(<ExecutiveHealth weekStart="2026-03-16" />);
    expect(screen.getByTestId("executive-health-error")).toHaveTextContent("Request failed (500)");
    fireEvent.click(screen.getByTestId("executive-health-refresh"));
    expect(mockFetchDashboard).toHaveBeenCalledWith("2026-03-16");
  });
});
