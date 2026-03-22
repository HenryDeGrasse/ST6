import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { TeamDashboardPage } from "../pages/TeamDashboardPage.js";

const mockUseFeatureFlags = vi.fn();
const mockUseForecasts = vi.fn();
const mockFetchForecasts = vi.fn();

vi.mock("../api/ApiContext.js", () => ({
  useApiClient: () => ({
    GET: vi.fn(),
  }),
}));

vi.mock("../context/AuthContext.js", () => ({
  useAuth: () => ({ user: { roles: ["MANAGER"] } }),
}));

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => mockUseFeatureFlags(),
}));

vi.mock("../hooks/useTeamDashboard.js", () => ({
  useTeamDashboard: () => ({
    summary: {
      users: [],
      reviewStatusCounts: { pending: 0, approved: 0, changesRequested: 0 },
      totalPages: 1,
    },
    rollup: null,
    loading: false,
    error: null,
    fetchSummary: vi.fn(),
    fetchRollup: vi.fn(),
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
  useReview: () => ({ submitReview: vi.fn(), error: null, clearError: vi.fn() }),
}));

vi.mock("../hooks/useAiManagerInsights.js", () => ({
  useAiManagerInsights: () => ({
    headline: null,
    insights: [],
    status: "idle",
    fetchInsights: vi.fn(),
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
    tree: [
      {
        objectives: [
          {
            outcomes: [{ id: "outcome-1", name: "Reduce churn" }],
          },
        ],
      },
    ],
    fetchTree: vi.fn(),
  }),
}));

vi.mock("../hooks/useForecasts.js", () => ({
  useForecasts: () => mockUseForecasts(),
}));

vi.mock("../components/WeekSelector.js", () => ({
  WeekSelector: () => <div data-testid="week-selector" />,
}));
vi.mock("../components/TeamSummaryGrid.js", () => ({
  TeamSummaryGrid: () => <div data-testid="team-summary-grid" />,
}));
vi.mock("../components/TeamDashboardFilters.js", () => ({
  TeamDashboardFiltersPanel: () => <div data-testid="team-dashboard-filters" />,
}));
vi.mock("../components/RcdoRollupPanel.js", () => ({
  RcdoRollupPanel: () => <div data-testid="rcdo-rollup-panel" />,
}));
vi.mock("../components/PlanDrillDown.js", () => ({
  PlanDrillDown: () => <div data-testid="plan-drill-down" />,
}));
vi.mock("../components/NotificationBell.js", () => ({
  NotificationBell: () => <div data-testid="notification-bell" />,
}));
vi.mock("../components/ErrorBanner.js", () => ({
  ErrorBanner: ({ message }: { message?: string | null }) => (message ? <div>{message}</div> : null),
}));
vi.mock("../components/AiManagerInsightsPanel.js", () => ({
  AiManagerInsightsPanel: () => <div data-testid="ai-manager-insights" />,
}));
vi.mock("../components/GlassPanel.js", () => ({
  GlassPanel: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
vi.mock("../components/UrgencyIndicator/StrategicSlackBanner.js", () => ({
  StrategicSlackBanner: () => <div data-testid="strategic-slack-banner" />,
}));
vi.mock("../components/UrgencyIndicator/OutcomeMetadataEditor.js", () => ({
  OutcomeMetadataEditor: () => <div data-testid="outcome-metadata-editor" />,
}));
vi.mock("../components/StrategicIntelligence/index.js", () => ({
  StrategicIntelligencePanel: () => <div data-testid="strategic-intelligence-panel" />,
}));
vi.mock("../components/Phase5/index.js", () => ({
  PlanningCopilot: ({ weekStart }: { weekStart: string }) => <div data-testid="planning-copilot-stub">{weekStart}</div>,
  OutcomeRiskCard: ({ outcomeId }: { outcomeId: string }) => <div data-testid="outcome-risk-card-stub">{outcomeId}</div>,
}));

describe("TeamDashboardPage Phase 5 integrations", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseFeatureFlags.mockReturnValue({
      strategicSlack: false,
      outcomeUrgency: false,
      strategicIntelligence: false,
      targetDateForecasting: true,
      planningCopilot: true,
    });
    mockUseForecasts.mockReturnValue({
      forecasts: [
        { outcomeId: "outcome-risk", outcomeName: "Reduce churn", forecastStatus: "AT_RISK" },
        { outcomeId: "outcome-watch", outcomeName: "Improve onboarding", forecastStatus: "NEEDS_ATTENTION" },
      ],
      selectedForecast: null,
      loadingList: false,
      loadingForecast: false,
      errorList: null,
      errorForecast: null,
      fetchForecasts: mockFetchForecasts,
      fetchForecast: vi.fn(),
      clearErrors: vi.fn(),
    });
  });

  it("renders planning copilot and the selected forecast card when flags are enabled", () => {
    render(<TeamDashboardPage />);

    expect(screen.getByTestId("phase5-manager-panels")).toBeInTheDocument();
    expect(screen.getByTestId("planning-copilot-stub")).toBeInTheDocument();
    expect(screen.getByTestId("outcome-risk-card-stub")).toHaveTextContent("outcome-risk");
    expect(mockFetchForecasts).toHaveBeenCalled();
  });

  it("lets the manager switch the featured forecast", () => {
    render(<TeamDashboardPage />);

    fireEvent.click(screen.getByTestId("forecast-select-outcome-watch"));

    expect(screen.getByTestId("outcome-risk-card-stub")).toHaveTextContent("outcome-watch");
  });
});
