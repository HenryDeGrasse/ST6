import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ExecutiveDashboardPage } from "../pages/ExecutiveDashboardPage.js";

const mockUseFeatureFlags = vi.fn();
const mockUseExecutiveDashboard = vi.fn();
const mockExecutiveBriefing = vi.fn();
const mockFetchDashboard = vi.fn().mockResolvedValue(undefined);

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => mockUseFeatureFlags(),
}));

vi.mock("../utils/week.js", () => ({
  getWeekStart: () => "2026-03-16",
}));

vi.mock("../components/GlassPanel.js", () => ({
  GlassPanel: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock("../components/WeekSelector.js", () => ({
  WeekSelector: ({ selectedWeek, onWeekChange }: { selectedWeek: string; onWeekChange: (week: string) => void }) => (
    <button data-testid="week-selector" onClick={() => onWeekChange("2026-03-23")}>
      {selectedWeek}
    </button>
  ),
}));

vi.mock("../hooks/useExecutiveDashboard.js", () => ({
  useExecutiveDashboard: () => mockUseExecutiveDashboard(),
}));

vi.mock("../components/Phase5/index.js", () => ({
  ExecutiveBriefing: (props: { weekStart: string }) => {
    mockExecutiveBriefing(props);
    return <div data-testid="executive-briefing-stub">briefing:{props.weekStart}</div>;
  },
}));

describe("ExecutiveDashboardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseExecutiveDashboard.mockReturnValue({
      dashboard: null,
      briefing: null,
      dashboardStatus: "idle" as const,
      briefingStatus: "idle" as const,
      errorDashboard: null,
      errorBriefing: null,
      fetchDashboard: mockFetchDashboard,
      fetchBriefing: vi.fn().mockResolvedValue(undefined),
      clearErrors: vi.fn(),
    });
  });

  it("renders rich executive sections and refetches when the week changes", () => {
    mockUseFeatureFlags.mockReturnValue({ executiveDashboard: true });
    mockUseExecutiveDashboard.mockReturnValue({
      dashboard: {
        weekStart: "2026-03-16",
        summary: {
          totalForecasts: 8,
          onTrackForecasts: 5,
          needsAttentionForecasts: 2,
          offTrackForecasts: 1,
          noDataForecasts: 0,
          averageForecastConfidence: 0.74,
          totalCapacityHours: 120,
          strategicHours: 72,
          nonStrategicHours: 48,
          strategicCapacityUtilizationPct: 60,
          nonStrategicCapacityUtilizationPct: 40,
          planningCoveragePct: 92,
        },
        rallyCryRollups: [
          {
            rallyCryId: "rc-1",
            rallyCryName: "Ship enterprise controls",
            forecastedOutcomeCount: 4,
            onTrackCount: 3,
            needsAttentionCount: 1,
            offTrackCount: 0,
            noDataCount: 0,
            averageForecastConfidence: 0.82,
            strategicHours: 30,
          },
        ],
        teamBuckets: [
          {
            bucketId: "Platform",
            memberCount: 6,
            planCoveragePct: 96,
            totalCapacityHours: 240,
            strategicHours: 156,
            strategicCapacityUtilizationPct: 65,
            averageForecastConfidence: 0.78,
          },
        ],
        teamGroupingAvailable: true,
      },
      briefing: null,
      dashboardStatus: "ok" as const,
      briefingStatus: "idle" as const,
      errorDashboard: null,
      errorBriefing: null,
      fetchDashboard: mockFetchDashboard,
      fetchBriefing: vi.fn().mockResolvedValue(undefined),
      clearErrors: vi.fn(),
    });

    render(<ExecutiveDashboardPage />);

    expect(screen.getByTestId("executive-dashboard-page")).toBeInTheDocument();
    expect(screen.getByRole("separator", { hidden: true })).toBeInTheDocument();
    expect(screen.getByTestId("executive-summary-stats")).toBeInTheDocument();
    expect(screen.getByTestId("executive-capacity-bar")).toBeInTheDocument();
    expect(screen.getByTestId("executive-rally-cries")).toBeInTheDocument();
    expect(screen.getByTestId("executive-team-buckets")).toBeInTheDocument();
    expect(screen.getByTestId("executive-briefing-stub")).toHaveTextContent("briefing:2026-03-16");
    expect(screen.getByText("Ship enterprise controls")).toBeInTheDocument();
    expect(screen.getByText("Platform")).toBeInTheDocument();
    expect(mockFetchDashboard).toHaveBeenCalledWith("2026-03-16");

    fireEvent.click(screen.getByTestId("week-selector"));

    expect(mockExecutiveBriefing).toHaveBeenLastCalledWith({ weekStart: "2026-03-23" });
    expect(mockFetchDashboard).toHaveBeenLastCalledWith("2026-03-23");
  });

  it("shows a flag-disabled state without rendering executive panels", () => {
    mockUseFeatureFlags.mockReturnValue({ executiveDashboard: false });

    render(<ExecutiveDashboardPage />);

    expect(screen.getByTestId("executive-dashboard-flag-disabled")).toBeInTheDocument();
    expect(screen.queryByTestId("executive-briefing-stub")).not.toBeInTheDocument();
  });
});
