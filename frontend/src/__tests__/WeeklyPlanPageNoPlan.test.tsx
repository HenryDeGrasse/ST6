import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act, fireEvent } from "@testing-library/react";
import { WeeklyPlanPage } from "../pages/WeeklyPlanPage.js";

/* ---- Mock hooks ---- */

const mockFetchPlan = vi.fn();
const mockCreatePlan = vi.fn();
const mockFetchCommits = vi.fn();
const mockResetCommits = vi.fn();
const mockFetchRcdoTree = vi.fn();

let mockPlan: null = null;
let mockPlanLoading = false;

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

vi.mock("../components/WeekSelector.js", () => ({
  WeekSelector: ({ onWeekChange }: { selectedWeek: string; onWeekChange: (weekStart: string) => void }) => (
    <div data-testid="week-selector">
      <button data-testid="goto-past" onClick={() => onWeekChange("2026-03-02")}>Past</button>
      <button data-testid="goto-next" onClick={() => onWeekChange("2026-03-16")}>Next</button>
      <button data-testid="goto-future" onClick={() => onWeekChange("2026-03-23")}>Future</button>
    </div>
  ),
}));

describe("WeeklyPlanPage no-plan UX states", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 11, 12, 0, 0));
    mockPlan = null;
    mockPlanLoading = false;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows Create button for the current week with no plan", () => {
    render(<WeeklyPlanPage />);

    expect(screen.getByTestId("no-plan")).toBeInTheDocument();
    expect(screen.getByTestId("create-plan-btn")).toBeInTheDocument();
    expect(screen.getByText("No plan for this week yet.")).toBeInTheDocument();
    expect(screen.queryByTestId("no-plan-past")).not.toBeInTheDocument();
    expect(screen.queryByTestId("no-plan-future")).not.toBeInTheDocument();
  });

  it("shows Create button for next week with no plan", async () => {
    render(<WeeklyPlanPage />);

    await act(async () => {
      fireEvent.click(screen.getByTestId("goto-next"));
    });

    expect(screen.getByTestId("create-plan-btn")).toBeInTheDocument();
    expect(screen.getByText("No plan for this week yet.")).toBeInTheDocument();
    expect(screen.queryByTestId("no-plan-past")).not.toBeInTheDocument();
    expect(screen.queryByTestId("no-plan-future")).not.toBeInTheDocument();
  });

  it("shows past week message when viewing a past week with no plan", async () => {
    render(<WeeklyPlanPage />);

    await act(async () => {
      fireEvent.click(screen.getByTestId("goto-past"));
    });

    expect(screen.getByTestId("no-plan-past")).toBeInTheDocument();
    expect(screen.getByText("No plan was created for this week.")).toBeInTheDocument();
    expect(screen.queryByTestId("create-plan-btn")).not.toBeInTheDocument();
    expect(screen.queryByTestId("no-plan-future")).not.toBeInTheDocument();
  });

  it("shows future restriction message for weeks beyond next week", async () => {
    render(<WeeklyPlanPage />);

    await act(async () => {
      fireEvent.click(screen.getByTestId("goto-future"));
    });

    expect(screen.getByTestId("no-plan-future")).toBeInTheDocument();
    expect(screen.getByText("Plans can only be created for the current or next week.")).toBeInTheDocument();
    expect(screen.queryByTestId("create-plan-btn")).not.toBeInTheDocument();
    expect(screen.queryByTestId("no-plan-past")).not.toBeInTheDocument();
  });
});
