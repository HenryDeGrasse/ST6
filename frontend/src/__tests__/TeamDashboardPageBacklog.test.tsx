/**
 * Tests for the Phase 6 backlog health section in TeamDashboardPage.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { TeamDashboardPage } from "../pages/TeamDashboardPage.js";
import type { TeamBacklogHealthSnapshot } from "../hooks/useAnalytics.js";

const mockFetchSummary = vi.fn().mockResolvedValue(undefined);
const mockFetchRollup = vi.fn().mockResolvedValue(undefined);
const mockFetchAiInsights = vi.fn().mockResolvedValue(undefined);
const mockFetchBacklogHealth = vi.fn().mockResolvedValue(undefined);

vi.mock("../utils/week.js", () => ({ getWeekStart: () => "2026-03-16" }));

vi.mock("../components/GlassPanel.js", () => ({
  GlassPanel: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock("../components/WeekSelector.js", () => ({
  WeekSelector: ({ selectedWeek }: { selectedWeek: string }) => (
    <div data-testid="week-selector">{selectedWeek}</div>
  ),
}));

vi.mock("../components/ErrorBanner.js", () => ({
  ErrorBanner: () => null,
}));

vi.mock("../components/NotificationBell.js", () => ({
  NotificationBell: () => null,
}));

vi.mock("../components/TeamDashboardFilters.js", () => ({
  TeamDashboardFiltersPanel: () => null,
}));

vi.mock("../components/AiManagerInsightsPanel.js", () => ({
  AiManagerInsightsPanel: () => null,
}));

vi.mock("../components/TeamSummaryGrid.js", () => ({
  TeamSummaryGrid: () => null,
}));

vi.mock("../components/RcdoRollupPanel.js", () => ({
  RcdoRollupPanel: () => null,
}));

vi.mock("../components/PlanDrillDown.js", () => ({
  PlanDrillDown: () => null,
}));

vi.mock("../components/UrgencyIndicator/StrategicSlackBanner.js", () => ({
  StrategicSlackBanner: () => null,
}));

vi.mock("../components/UrgencyIndicator/OutcomeMetadataEditor.js", () => ({
  OutcomeMetadataEditor: () => null,
}));

vi.mock("../components/Phase5/index.js", () => ({
  OutcomeRiskCard: () => null,
  PlanningCopilot: () => null,
}));

vi.mock("../components/StrategicIntelligence/index.js", () => ({
  StrategicIntelligencePanel: () => null,
}));

vi.mock("../hooks/useTeamDashboard.js", () => ({
  useTeamDashboard: () => ({
    summary: null,
    rollup: null,
    loading: false,
    error: null,
    fetchSummary: mockFetchSummary,
    fetchRollup: mockFetchRollup,
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useNotifications.js", () => ({
  useNotifications: () => ({
    notifications: [],
    unreadCount: 0,
    error: null,
    fetchUnread: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useReview.js", () => ({
  useReview: () => ({
    submitReview: vi.fn(),
    error: null,
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useAiManagerInsights.js", () => ({
  useAiManagerInsights: () => ({
    headline: null,
    insights: [],
    status: "idle",
    fetchInsights: mockFetchAiInsights,
  }),
}));

vi.mock("../hooks/useOutcomeMetadata.js", () => ({
  useOutcomeMetadata: () => ({
    outcomeMetadata: [],
    strategicSlack: null,
    loading: false,
    fetchMetadata: vi.fn(),
    fetchStrategicSlack: vi.fn(),
    updateMetadata: vi.fn(),
    updateProgress: vi.fn(),
  }),
}));

vi.mock("../hooks/useRcdo.js", () => ({
  useRcdo: () => ({
    tree: [],
    fetchTree: vi.fn(),
  }),
}));

vi.mock("../hooks/useForecasts.js", () => ({
  useForecasts: () => ({
    forecasts: [],
    loadingList: false,
    errorList: null,
    fetchForecasts: vi.fn(),
  }),
}));

vi.mock("../context/AuthContext.js", () => ({
  useAuth: () => ({ user: { roles: ["MANAGER"] } }),
}));

vi.mock("../api/ApiContext.js", () => ({
  useApiClient: () => ({}),
}));

let mockBacklogHealth: TeamBacklogHealthSnapshot[] | null = null;

vi.mock("../hooks/useAnalytics.js", () => ({
  useOrgBacklogHealth: () => ({
    data: mockBacklogHealth,
    loading: false,
    error: null,
    fetch: mockFetchBacklogHealth,
    clearError: vi.fn(),
  }),
}));

let mockFlags: Record<string, boolean> = {};

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => mockFlags,
}));

function makeBacklogHealth(overrides: Partial<TeamBacklogHealthSnapshot> = {}): TeamBacklogHealthSnapshot {
  return {
    teamId: "team-1",
    openIssueCount: 6,
    avgIssueAgeDays: 14,
    blockedCount: 2,
    buildCount: 3,
    maintainCount: 2,
    collaborateCount: 1,
    learnCount: 0,
    avgCycleTimeDays: 9,
    ...overrides,
  };
}

describe("TeamDashboardPage — backlog health section", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockBacklogHealth = null;
    mockFlags = {
      useIssueBacklog: false,
      strategicSlack: false,
      outcomeUrgency: false,
      strategicIntelligence: false,
      targetDateForecasting: false,
      planningCopilot: false,
      managerInsights: true,
    };
  });

  it("does not render backlog health section when useIssueBacklog flag is off", () => {
    mockFlags.useIssueBacklog = false;
    mockBacklogHealth = [makeBacklogHealth()];

    render(<TeamDashboardPage />);

    expect(screen.queryByTestId("backlog-health-section")).not.toBeInTheDocument();
  });

  it("renders backlog health section when flag is on and health data is loaded", () => {
    mockFlags.useIssueBacklog = true;
    mockBacklogHealth = [makeBacklogHealth()];

    render(<TeamDashboardPage />);

    expect(screen.getByTestId("backlog-health-section")).toBeInTheDocument();
    expect(screen.getByText("Backlog Health")).toBeInTheDocument();
    expect(screen.getByTestId("backlog-open-count")).toBeInTheDocument();
    expect(screen.getByTestId("backlog-blocked-count")).toBeInTheDocument();
    expect(screen.getByTestId("backlog-avg-age")).toBeInTheDocument();
    expect(screen.getByTestId("backlog-avg-cycle-time")).toBeInTheDocument();
  });

  it("aggregates effort type breakdown across teams", () => {
    mockFlags.useIssueBacklog = true;
    mockBacklogHealth = [
      makeBacklogHealth({ buildCount: 2, maintainCount: 1, collaborateCount: 0, learnCount: 1 }),
      makeBacklogHealth({ teamId: "team-2", buildCount: 1, maintainCount: 2, collaborateCount: 3, learnCount: 0 }),
    ];

    render(<TeamDashboardPage />);

    expect(screen.getByTestId("backlog-effort-breakdown")).toBeInTheDocument();
    expect(screen.getByText("Build")).toBeInTheDocument();
    expect(screen.getByText("Maintain")).toBeInTheDocument();
    expect(screen.getByText("Collaborate")).toBeInTheDocument();
  });

  it("shows team count from backlog health rows", () => {
    mockFlags.useIssueBacklog = true;
    mockBacklogHealth = [makeBacklogHealth(), makeBacklogHealth({ teamId: "team-2" })];

    render(<TeamDashboardPage />);

    const teamCountCard = screen.getByTestId("backlog-team-count");
    expect(teamCountCard).toBeInTheDocument();
    expect(teamCountCard).toHaveTextContent("2");
  });

  it("shows a link to the full backlog and dispatches navigation", () => {
    mockFlags.useIssueBacklog = true;
    mockBacklogHealth = [makeBacklogHealth()];
    const dispatchSpy = vi.spyOn(window, "dispatchEvent");

    render(<TeamDashboardPage />);

    fireEvent.click(screen.getByTestId("backlog-health-link"));

    expect(screen.getByText("View full backlog →")).toBeInTheDocument();
    expect(dispatchSpy).toHaveBeenCalled();
  });

  it("does not show backlog section when flag is on but no data exists", () => {
    mockFlags.useIssueBacklog = true;
    mockBacklogHealth = [];

    render(<TeamDashboardPage />);

    expect(screen.queryByTestId("backlog-health-section")).not.toBeInTheDocument();
  });
});
