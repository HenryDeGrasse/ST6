import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act, fireEvent } from "@testing-library/react";
import { WeeklyPlanPage } from "../pages/WeeklyPlanPage.js";
import { FeatureFlagProvider } from "../context/FeatureFlagContext.js";
import type { DraftFromHistoryResponse } from "@weekly-commitments/contracts";

/* ---- Mock hooks ---- */

const mockFetchPlan = vi.fn();
const mockCreatePlan = vi.fn();
const mockFetchCommits = vi.fn();
const mockResetCommits = vi.fn();
const mockFetchRcdoTree = vi.fn();
const mockDraftFromHistory = vi.fn();
const mockResetDraftFromHistory = vi.fn();

let mockPlan: null = null;
let mockPlanLoading = false;
let mockDraftFromHistoryStatus: "idle" | "loading" | "ok" | "error" | "conflict" = "idle";
let mockDraftFromHistoryError: string | null = null;

vi.mock("../hooks/usePlan.js", () => ({
  usePlan: () => ({
    plan: mockPlan,
    loading: mockPlanLoading,
    error: null,
    conflictVersion: null,
    fetchPlan: mockFetchPlan,
    createPlan: mockCreatePlan,
    lockPlan: vi.fn(),
    startReconciliation: vi.fn(),
    submitReconciliation: vi.fn(),
    carryForward: vi.fn(),
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useCommits.js", () => ({
  useCommits: () => ({
    commits: [],
    loading: false,
    error: null,
    conflictVersion: null,
    fetchCommits: mockFetchCommits,
    createCommit: vi.fn(),
    updateCommit: vi.fn(),
    deleteCommit: vi.fn(),
    updateActual: vi.fn(),
    resetCommits: mockResetCommits,
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useWeeklyAssignments.js", () => ({
  useWeeklyAssignments: () => ({
    assignments: [],
    loading: false,
    error: null,
    fetchAssignments: vi.fn().mockResolvedValue(undefined),
    createAssignment: vi.fn().mockResolvedValue(null),
    removeAssignment: vi.fn().mockResolvedValue(true),
    releaseToBacklog: vi.fn().mockResolvedValue(true),
    resetAssignments: vi.fn(),
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useRcdo.js", () => ({
  useRcdo: () => ({
    tree: [],
    searchResults: [],
    loading: false,
    error: null,
    fetchTree: mockFetchRcdoTree,
    search: vi.fn(),
    clearSearch: vi.fn(),
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useAiSuggestions.js", () => ({
  useAiSuggestions: () => ({
    suggestions: [],
    suggestStatus: "idle",
    fetchSuggestions: vi.fn(),
    clearSuggestions: vi.fn(),
    draftItems: [],
    draftStatus: "idle",
    fetchDraft: vi.fn(),
  }),
}));

vi.mock("../hooks/useTrends.js", () => ({
  useTrends: () => ({
    trends: null,
    loading: false,
    error: null,
    fetchTrends: vi.fn(),
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/usePlanQualityCheck.js", () => ({
  usePlanQualityCheck: () => ({
    nudges: [],
    status: "idle",
    checkQuality: vi.fn(),
    clearNudges: vi.fn(),
  }),
}));

vi.mock("../hooks/useDraftFromHistory.js", () => ({
  useDraftFromHistory: () => ({
    status: mockDraftFromHistoryStatus,
    planId: null,
    suggestedCommits: [],
    error: mockDraftFromHistoryError,
    draftFromHistory: mockDraftFromHistory,
    reset: mockResetDraftFromHistory,
  }),
}));

vi.mock("../hooks/useNextWorkSuggestions.js", () => ({
  useNextWorkSuggestions: () => ({
    suggestions: [],
    status: "idle",
    fetchSuggestions: vi.fn(),
    submitFeedback: vi.fn().mockResolvedValue(true),
    dismissSuggestion: vi.fn(),
    clearSuggestions: vi.fn(),
  }),
}));

vi.mock("../hooks/useCheckIn.js", () => ({
  useCheckIn: () => ({
    entries: [],
    loading: false,
    error: null,
    addCheckIn: vi.fn().mockResolvedValue(null),
    fetchCheckIns: vi.fn().mockResolvedValue(undefined),
    clearEntries: vi.fn(),
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useUserProfile.js", () => ({
  useUserProfile: () => ({
    profile: null,
    loading: false,
    error: null,
    fetchProfile: vi.fn().mockResolvedValue(undefined),
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useCapacity.js", () => ({
  useCapacityProfile: () => ({
    profile: null,
    loading: false,
    error: null,
    fetchProfile: vi.fn().mockResolvedValue(undefined),
    clearError: vi.fn(),
  }),
}));

vi.mock("../components/WeekSelector.js", () => ({
  WeekSelector: ({ onWeekChange }: { selectedWeek: string; onWeekChange: (weekStart: string) => void }) => (
    <div data-testid="week-selector">
      <button data-testid="goto-past" onClick={() => onWeekChange("2026-03-02")}>
        Past
      </button>
      <button data-testid="goto-next" onClick={() => onWeekChange("2026-03-16")}>
        Next
      </button>
      <button data-testid="goto-future" onClick={() => onWeekChange("2026-03-23")}>
        Future
      </button>
    </div>
  ),
}));

/* ---- Helpers ---- */

function renderPage(flags?: { startMyWeek?: boolean }) {
  return render(
    <FeatureFlagProvider flags={flags}>
      <WeeklyPlanPage />
    </FeatureFlagProvider>,
  );
}

describe("WeeklyPlanPage no-plan UX states", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 11, 12, 0, 0));
    mockPlan = null;
    mockPlanLoading = false;
    mockDraftFromHistoryStatus = "idle";
    mockDraftFromHistoryError = null;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows Create button for the current week with no plan", () => {
    renderPage();

    expect(screen.getByTestId("no-plan")).toBeInTheDocument();
    expect(screen.getByTestId("create-plan-btn")).toBeInTheDocument();
    expect(screen.getByText("No plan for this week yet.")).toBeInTheDocument();
    expect(screen.queryByTestId("no-plan-past")).not.toBeInTheDocument();
    expect(screen.queryByTestId("no-plan-future")).not.toBeInTheDocument();
  });

  it("shows Create button for next week with no plan", async () => {
    renderPage();

    await act(async () => {
      fireEvent.click(screen.getByTestId("goto-next"));
    });

    expect(screen.getByTestId("create-plan-btn")).toBeInTheDocument();
    expect(screen.getByText("No plan for this week yet.")).toBeInTheDocument();
    expect(screen.queryByTestId("no-plan-past")).not.toBeInTheDocument();
    expect(screen.queryByTestId("no-plan-future")).not.toBeInTheDocument();
  });

  it("shows past week message when viewing a past week with no plan", async () => {
    renderPage();

    await act(async () => {
      fireEvent.click(screen.getByTestId("goto-past"));
    });

    expect(screen.getByTestId("no-plan-past")).toBeInTheDocument();
    expect(screen.getByText("No plan was created for this week.")).toBeInTheDocument();
    expect(screen.queryByTestId("create-plan-btn")).not.toBeInTheDocument();
    expect(screen.queryByTestId("no-plan-future")).not.toBeInTheDocument();
  });

  it("shows future restriction message for weeks beyond next week", async () => {
    renderPage();

    await act(async () => {
      fireEvent.click(screen.getByTestId("goto-future"));
    });

    expect(screen.getByTestId("no-plan-future")).toBeInTheDocument();
    expect(screen.getByText("Plans can only be created for the current or next week.")).toBeInTheDocument();
    expect(screen.queryByTestId("create-plan-btn")).not.toBeInTheDocument();
    expect(screen.queryByTestId("no-plan-past")).not.toBeInTheDocument();
  });

  // ── startMyWeek flag: button visibility ─────────────────────────────────

  it("does NOT show 'Start from Last Week' button when startMyWeek flag is disabled", () => {
    renderPage({ startMyWeek: false });

    expect(screen.getByTestId("create-plan-btn")).toBeInTheDocument();
    expect(screen.queryByTestId("start-from-last-week-btn")).not.toBeInTheDocument();
  });

  it("shows BOTH buttons when startMyWeek flag is enabled and no plan exists", () => {
    renderPage({ startMyWeek: true });

    expect(screen.getByTestId("create-plan-btn")).toBeInTheDocument();
    expect(screen.getByTestId("start-from-last-week-btn")).toBeInTheDocument();
    expect(screen.getByTestId("start-from-last-week-btn")).toHaveTextContent("Start from Last Week");
  });

  it("'Start from Last Week' button is not shown for past weeks even with flag enabled", async () => {
    renderPage({ startMyWeek: true });

    await act(async () => {
      fireEvent.click(screen.getByTestId("goto-past"));
    });

    expect(screen.queryByTestId("start-from-last-week-btn")).not.toBeInTheDocument();
    expect(screen.queryByTestId("create-plan-btn")).not.toBeInTheDocument();
    expect(screen.getByTestId("no-plan-past")).toBeInTheDocument();
  });

  it("'Start from Last Week' button is not shown for future weeks even with flag enabled", async () => {
    renderPage({ startMyWeek: true });

    await act(async () => {
      fireEvent.click(screen.getByTestId("goto-future"));
    });

    expect(screen.queryByTestId("start-from-last-week-btn")).not.toBeInTheDocument();
    expect(screen.queryByTestId("create-plan-btn")).not.toBeInTheDocument();
    expect(screen.getByTestId("no-plan-future")).toBeInTheDocument();
  });

  // ── startMyWeek flag: interaction ───────────────────────────────────────

  it("calls draftFromHistory with the current weekStart when 'Start from Last Week' is clicked", async () => {
    mockDraftFromHistory.mockResolvedValue(null);
    renderPage({ startMyWeek: true });

    await act(async () => {
      fireEvent.click(screen.getByTestId("start-from-last-week-btn"));
    });

    expect(mockDraftFromHistory).toHaveBeenCalledTimes(1);
    // The current week (system time set to 2026-03-11) → Monday 2026-03-09
    expect(mockDraftFromHistory).toHaveBeenCalledWith(expect.stringMatching(/^2026-03-0[0-9]/));
  });

  it("calls fetchPlan after draftFromHistory succeeds to navigate into the new plan", async () => {
    const response: DraftFromHistoryResponse = {
      planId: "plan-new",
      suggestedCommits: [],
    };
    mockDraftFromHistory.mockResolvedValue(response);

    renderPage({ startMyWeek: true });

    // Initial mount triggers one fetchPlan call
    const initialCallCount = mockFetchPlan.mock.calls.length;

    await act(async () => {
      fireEvent.click(screen.getByTestId("start-from-last-week-btn"));
    });

    // fetchPlan called a second time after draftFromHistory succeeds
    expect(mockFetchPlan.mock.calls.length).toBeGreaterThan(initialCallCount);
  });

  it("does NOT call fetchPlan when draftFromHistory returns null (error/flag-off)", async () => {
    mockDraftFromHistory.mockResolvedValue(null);
    renderPage({ startMyWeek: true });

    await act(async () => {
      fireEvent.click(screen.getByTestId("start-from-last-week-btn"));
    });

    // fetchPlan is called once on mount (to check for existing plan), not on failure
    expect(mockFetchPlan).toHaveBeenCalledTimes(1);
  });

  it("shows 'Building your plan…' loading text while draftFromHistory is in-flight", () => {
    mockDraftFromHistoryStatus = "loading";
    renderPage({ startMyWeek: true });

    expect(screen.getByTestId("start-from-last-week-btn")).toHaveTextContent("Building your plan…");
  });

  it("disables both buttons while draftFromHistory is loading", () => {
    mockDraftFromHistoryStatus = "loading";
    renderPage({ startMyWeek: true });

    expect(screen.getByTestId("create-plan-btn")).toBeDisabled();
    expect(screen.getByTestId("start-from-last-week-btn")).toBeDisabled();
  });

  it("the button text reverts to 'Start from Last Week' when not loading", () => {
    mockDraftFromHistoryStatus = "idle";
    renderPage({ startMyWeek: true });

    expect(screen.getByTestId("start-from-last-week-btn")).toHaveTextContent("Start from Last Week");
    expect(screen.getByTestId("start-from-last-week-btn")).not.toBeDisabled();
  });
});
