import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ExecutiveDashboardPage } from "../pages/ExecutiveDashboardPage.js";

const mockUseFeatureFlags = vi.fn();
const mockUseExecutiveDashboard = vi.fn();
const mockExecutiveBriefing = vi.fn();
const mockFetchDashboard = vi.fn().mockResolvedValue(undefined);
const mockFetchBacklogHealth = vi.fn().mockResolvedValue(undefined);

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

let mockBacklogHealth = null as null | Array<{
  teamId: string;
  openIssueCount: number;
  avgIssueAgeDays: number;
  blockedCount: number;
  buildCount: number;
  maintainCount: number;
  collaborateCount: number;
  learnCount: number;
  avgCycleTimeDays: number;
}>;

vi.mock("../hooks/useAnalytics.js", () => ({
  useOrgBacklogHealth: () => ({
    data: mockBacklogHealth,
    loading: false,
    error: null,
    fetch: mockFetchBacklogHealth,
    clearError: vi.fn(),
  }),
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
    mockFetchDashboard.mockResolvedValue(undefined);
    mockFetchBacklogHealth.mockResolvedValue(undefined);
    mockBacklogHealth = null;
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
    mockUseFeatureFlags.mockReturnValue({ executiveDashboard: true, useIssueBacklog: false });
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

  it("renders org backlog overview when issue backlog metrics are available", () => {
    mockUseFeatureFlags.mockReturnValue({ executiveDashboard: true, useIssueBacklog: true });
    mockBacklogHealth = [
      {
        teamId: "team-1",
        openIssueCount: 12,
        avgIssueAgeDays: 8,
        blockedCount: 2,
        buildCount: 5,
        maintainCount: 4,
        collaborateCount: 2,
        learnCount: 1,
        avgCycleTimeDays: 11,
      },
      {
        teamId: "team-2",
        openIssueCount: 8,
        avgIssueAgeDays: 5,
        blockedCount: 1,
        buildCount: 3,
        maintainCount: 2,
        collaborateCount: 2,
        learnCount: 1,
        avgCycleTimeDays: 9,
      },
    ];
    mockUseExecutiveDashboard.mockReturnValue({
      dashboard: {
        weekStart: "2026-03-16",
        summary: {
          totalForecasts: 10,
          onTrackForecasts: 5,
          needsAttentionForecasts: 3,
          offTrackForecasts: 2,
          noDataForecasts: 0,
          averageForecastConfidence: 0.74,
          totalCapacityHours: 120,
          strategicHours: 72,
          nonStrategicHours: 48,
          strategicCapacityUtilizationPct: 60,
          nonStrategicCapacityUtilizationPct: 40,
          planningCoveragePct: 92,
        },
        rallyCryRollups: [],
        teamBuckets: [],
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

    expect(screen.getByTestId("exec-backlog-metrics")).toBeInTheDocument();
    expect(screen.getByTestId("exec-backlog-open")).toHaveTextContent("20");
    expect(screen.getByTestId("exec-backlog-cycle-time")).toHaveTextContent("10d");
    expect(screen.getByTestId("exec-backlog-ratio")).toHaveTextContent("2.0x");
    expect(screen.getByTestId("exec-backlog-teams")).toHaveTextContent("2");
  });

  it("shows a flag-disabled state without rendering executive panels", () => {
    mockUseFeatureFlags.mockReturnValue({ executiveDashboard: false, useIssueBacklog: false });

    render(<ExecutiveDashboardPage />);

    expect(screen.getByTestId("executive-dashboard-flag-disabled")).toBeInTheDocument();
    expect(screen.queryByTestId("executive-briefing-stub")).not.toBeInTheDocument();
  });
});
