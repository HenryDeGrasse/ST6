import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { WeeklyPlanPage } from "../pages/WeeklyPlanPage.js";
import { FeatureFlagProvider } from "../context/FeatureFlagContext.js";
import { PlanState, ReviewStatus, ChessPriority } from "@weekly-commitments/contracts";
import type { QualityNudge, WeeklyPlan, WeeklyCommit, NextWorkSuggestion } from "@weekly-commitments/contracts";

/* ---- Mock data ---- */

function makePlan(overrides: Partial<WeeklyPlan> = {}): WeeklyPlan {
  return {
    id: "plan-1",
    orgId: "org-1",
    ownerUserId: "user-1",
    weekStartDate: "2026-03-09",
    state: PlanState.DRAFT,
    reviewStatus: ReviewStatus.REVIEW_NOT_APPLICABLE,
    lockType: null,
    lockedAt: null,
    carryForwardExecutedAt: null,
    version: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides,
  };
}

function makeCommit(overrides: Partial<WeeklyCommit> = {}): WeeklyCommit {
  return {
    id: "commit-1",
    weeklyPlanId: "plan-1",
    title: "Test task",
    description: "A description",
    chessPriority: ChessPriority.ROOK,
    category: null,
    outcomeId: null,
    nonStrategicReason: null,
    expectedResult: "Expected",
    confidence: null,
    tags: [],
    progressNotes: "",
    snapshotRallyCryId: null,
    snapshotRallyCryName: null,
    snapshotObjectiveId: null,
    snapshotObjectiveName: null,
    snapshotOutcomeId: null,
    snapshotOutcomeName: null,
    carriedFromCommitId: null,
    version: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    validationErrors: [],
    actual: null,
    ...overrides,
  };
}

function makeNextWorkSuggestion(overrides: Partial<NextWorkSuggestion> = {}): NextWorkSuggestion {
  return {
    suggestionId: "next-work-1",
    title: "Follow up on activation fixes",
    suggestedOutcomeId: "outcome-1",
    suggestedChessPriority: ChessPriority.QUEEN,
    confidence: 0.82,
    source: "CARRY_FORWARD",
    sourceDetail: "Carried from the previous week",
    rationale: "This was not completed last week and remains strategically important.",
    ...overrides,
  };
}

/* ---- Mock hooks ---- */

const mockFetchPlan = vi.fn();
const mockCreatePlan = vi.fn();
const mockLockPlan = vi.fn();
const mockStartReconciliation = vi.fn();
const mockSubmitReconciliation = vi.fn();
const mockCarryForward = vi.fn();

const mockFetchCommits = vi.fn();
const mockCreateCommit = vi.fn();
const mockUpdateCommit = vi.fn();
const mockDeleteCommit = vi.fn();
const mockUpdateActual = vi.fn();
const mockResetCommits = vi.fn();

let mockPlan: WeeklyPlan | null = null;
let mockCommits: WeeklyCommit[] = [];
let mockPlanLoading = false;
let mockCommitsLoading = false;
let mockQualityNudges: QualityNudge[] = [];
let mockQualityStatus: "idle" | "loading" | "ok" | "unavailable" | "rate_limited" = "idle";
const mockCheckQuality = vi.fn();
const mockClearQualityNudges = vi.fn();
const mockFetchNextWorkSuggestions = vi.fn();
const mockSubmitNextWorkFeedback = vi.fn();
const mockDismissNextWorkSuggestion = vi.fn();
const mockClearNextWorkSuggestions = vi.fn();
const mockFetchCapacityProfile = vi.fn();
let mockNextWorkSuggestions: NextWorkSuggestion[] = [];
let mockNextWorkStatus: "idle" | "loading" | "ok" | "unavailable" | "rate_limited" = "idle";
let mockCapacityProfile: { realisticWeeklyCap: number | null } | null = null;

vi.mock("../hooks/usePlan.js", () => ({
  usePlan: () => ({
    plan: mockPlan,
    loading: mockPlanLoading,
    error: null,
    conflictVersion: null,
    fetchPlan: mockFetchPlan,
    createPlan: mockCreatePlan,
    lockPlan: mockLockPlan,
    startReconciliation: mockStartReconciliation,
    submitReconciliation: mockSubmitReconciliation,
    carryForward: mockCarryForward,
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useCommits.js", () => ({
  useCommits: () => ({
    commits: mockCommits,
    loading: mockCommitsLoading,
    error: null,
    conflictVersion: null,
    fetchCommits: mockFetchCommits,
    createCommit: mockCreateCommit,
    updateCommit: mockUpdateCommit,
    deleteCommit: mockDeleteCommit,
    updateActual: mockUpdateActual,
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
    fetchTree: vi.fn(),
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
    nudges: mockQualityNudges,
    status: mockQualityStatus,
    checkQuality: mockCheckQuality,
    clearNudges: mockClearQualityNudges,
  }),
}));

vi.mock("../hooks/useDraftFromHistory.js", () => ({
  useDraftFromHistory: () => ({
    status: "idle",
    planId: null,
    suggestedCommits: [],
    error: null,
    draftFromHistory: vi.fn().mockResolvedValue(null),
    reset: vi.fn(),
  }),
}));

vi.mock("../hooks/useNextWorkSuggestions.js", () => ({
  useNextWorkSuggestions: () => ({
    suggestions: mockNextWorkSuggestions,
    status: mockNextWorkStatus,
    fetchSuggestions: mockFetchNextWorkSuggestions,
    submitFeedback: mockSubmitNextWorkFeedback,
    dismissSuggestion: mockDismissNextWorkSuggestion,
    clearSuggestions: mockClearNextWorkSuggestions,
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
    profile: mockCapacityProfile,
    loading: false,
    error: null,
    fetchProfile: mockFetchCapacityProfile,
    clearError: vi.fn(),
  }),
}));

function renderWeeklyPlanPage(
  flags?: Partial<{
    planQualityNudge: boolean;
    suggestNextWork: boolean;
  }>,
) {
  return render(
    <FeatureFlagProvider flags={{ planQualityNudge: false, suggestNextWork: false, ...flags }}>
      <WeeklyPlanPage />
    </FeatureFlagProvider>,
  );
}

describe("WeeklyPlanPage confirmation dialogs", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPlan = null;
    mockCommits = [];
    mockPlanLoading = false;
    mockCommitsLoading = false;
    mockQualityNudges = [];
    mockQualityStatus = "idle";
    mockNextWorkSuggestions = [];
    mockNextWorkStatus = "idle";
    mockCapacityProfile = null;
    mockFetchCapacityProfile.mockResolvedValue(undefined);
    mockSubmitNextWorkFeedback.mockResolvedValue(true);
  });

  describe("Lock plan confirmation", () => {
    it("shows confirm dialog when Lock button is clicked", () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      renderWeeklyPlanPage();

      fireEvent.click(screen.getByTestId("lock-btn"));

      expect(screen.getByTestId("confirm-dialog")).toBeInTheDocument();
      expect(
        screen.getByText("Lock this plan? Once locked, you cannot add or remove commitments."),
      ).toBeInTheDocument();
    });

    it("shows the quality nudge before the confirm dialog when the flag is enabled", () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      renderWeeklyPlanPage({ planQualityNudge: true });

      fireEvent.click(screen.getByTestId("lock-btn"));

      expect(mockCheckQuality).toHaveBeenCalledWith("plan-1");
      expect(screen.getByTestId("plan-quality-nudge-dialog")).toBeInTheDocument();
      expect(screen.queryByTestId("confirm-dialog")).not.toBeInTheDocument();
    });

    it("allows proceeding from the quality nudge to the confirm dialog", () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      mockQualityStatus = "loading";
      renderWeeklyPlanPage({ planQualityNudge: true });

      fireEvent.click(screen.getByTestId("lock-btn"));
      fireEvent.click(screen.getByTestId("plan-quality-nudge-lock-anyway"));

      expect(mockClearQualityNudges).toHaveBeenCalledTimes(1);
      expect(screen.queryByTestId("plan-quality-nudge-dialog")).not.toBeInTheDocument();
      expect(screen.getByTestId("confirm-dialog")).toBeInTheDocument();
    });

    it("dismisses the quality nudge when Review Plan is clicked", () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      renderWeeklyPlanPage({ planQualityNudge: true });

      fireEvent.click(screen.getByTestId("lock-btn"));
      fireEvent.click(screen.getByTestId("plan-quality-nudge-review"));

      expect(mockClearQualityNudges).toHaveBeenCalledTimes(1);
      expect(screen.queryByTestId("plan-quality-nudge-dialog")).not.toBeInTheDocument();
      expect(screen.queryByTestId("confirm-dialog")).not.toBeInTheDocument();
    });

    it("calls lockPlan when confirm is clicked", async () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      mockLockPlan.mockResolvedValue(makePlan({ state: PlanState.LOCKED }));
      renderWeeklyPlanPage();

      fireEvent.click(screen.getByTestId("lock-btn"));
      fireEvent.click(screen.getByTestId("confirm-dialog-confirm"));

      await waitFor(() => {
        expect(mockLockPlan).toHaveBeenCalledWith("plan-1", 1);
      });
    });

    it("dismisses dialog when cancel is clicked", () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      renderWeeklyPlanPage();

      fireEvent.click(screen.getByTestId("lock-btn"));
      expect(screen.getByTestId("confirm-dialog")).toBeInTheDocument();

      fireEvent.click(screen.getByTestId("confirm-dialog-cancel"));
      expect(screen.queryByTestId("confirm-dialog")).not.toBeInTheDocument();
      expect(mockLockPlan).not.toHaveBeenCalled();
    });

    it("keeps the normal lock label during commit loading", () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      mockCommitsLoading = true;
      renderWeeklyPlanPage();

      expect(screen.getByTestId("lock-btn")).toHaveTextContent("Lock Plan");
      expect(screen.getByTestId("lock-btn")).not.toHaveTextContent("Locking");
    });
  });

  describe("Submit reconciliation confirmation", () => {
    it("shows confirm dialog when Submit Reconciliation button is clicked", () => {
      mockPlan = makePlan({ state: PlanState.RECONCILING });
      mockCommits = [makeCommit()];
      render(<WeeklyPlanPage />);

      fireEvent.click(screen.getByTestId("submit-reconciliation-btn"));

      expect(screen.getByTestId("confirm-dialog")).toBeInTheDocument();
      expect(screen.getByText("Submit reconciliation? This will finalize your weekly report.")).toBeInTheDocument();
    });

    it("calls submitReconciliation when confirm is clicked", async () => {
      mockPlan = makePlan({ state: PlanState.RECONCILING });
      mockCommits = [makeCommit()];
      mockSubmitReconciliation.mockResolvedValue(makePlan({ state: PlanState.RECONCILED }));
      render(<WeeklyPlanPage />);

      fireEvent.click(screen.getByTestId("submit-reconciliation-btn"));
      fireEvent.click(screen.getByTestId("confirm-dialog-confirm"));

      await waitFor(() => {
        expect(mockSubmitReconciliation).toHaveBeenCalledWith("plan-1", 1);
      });
    });

    it("dismisses dialog when cancel is clicked", () => {
      mockPlan = makePlan({ state: PlanState.RECONCILING });
      mockCommits = [makeCommit()];
      render(<WeeklyPlanPage />);

      fireEvent.click(screen.getByTestId("submit-reconciliation-btn"));
      expect(screen.getByTestId("confirm-dialog")).toBeInTheDocument();

      fireEvent.click(screen.getByTestId("confirm-dialog-cancel"));
      expect(screen.queryByTestId("confirm-dialog")).not.toBeInTheDocument();
      expect(mockSubmitReconciliation).not.toHaveBeenCalled();
    });
  });

  describe("Draft plan capacity warnings", () => {
    it("fetches the capacity profile and renders an overcommit banner for draft plans", async () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      mockCommits = [
        makeCommit({ id: "c-1", estimatedHours: 12 }),
        makeCommit({ id: "c-2", estimatedHours: 10 }),
      ];
      mockCapacityProfile = { realisticWeeklyCap: 18 };

      renderWeeklyPlanPage();

      await waitFor(() => {
        expect(mockFetchCapacityProfile).toHaveBeenCalledTimes(1);
      });

      expect(screen.getByTestId("overcommit-banner")).toBeInTheDocument();
      expect(screen.getByTestId("overcommit-level-HIGH")).toBeInTheDocument();
      expect(screen.getByText("22h committed · 18h cap")).toBeInTheDocument();
    });
  });

  describe("Next-work suggestions", () => {
    it("renders the AI-suggested work panel and fetches suggestions when enabled", async () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      mockNextWorkSuggestions = [makeNextWorkSuggestion()];
      mockNextWorkStatus = "ok";

      renderWeeklyPlanPage({ suggestNextWork: true });

      expect(screen.getByText("AI-Suggested Work")).toBeInTheDocument();
      expect(screen.getByText("Follow up on activation fixes")).toBeInTheDocument();

      await waitFor(() => {
        expect(mockFetchNextWorkSuggestions).toHaveBeenCalledWith(expect.any(String));
      });
    });

    it("accepts a next-work suggestion into the draft plan and records feedback", async () => {
      const suggestion = makeNextWorkSuggestion();
      mockPlan = makePlan({ state: PlanState.DRAFT });
      mockNextWorkSuggestions = [suggestion];
      mockNextWorkStatus = "ok";
      mockCreateCommit.mockResolvedValue(makeCommit({ id: "new-commit" }));
      mockSubmitNextWorkFeedback.mockResolvedValue(true);

      renderWeeklyPlanPage({ suggestNextWork: true });

      fireEvent.click(screen.getByTestId(`next-work-accept-${suggestion.suggestionId}`));

      await waitFor(() => {
        expect(mockCreateCommit).toHaveBeenCalledWith(
          "plan-1",
          expect.objectContaining({
            title: suggestion.title,
            chessPriority: suggestion.suggestedChessPriority,
            outcomeId: suggestion.suggestedOutcomeId,
            tags: ["draft_source:CARRIED_FORWARD"],
          }),
        );
      });

      await waitFor(() => {
        expect(mockSubmitNextWorkFeedback).toHaveBeenCalledWith({
          suggestionId: suggestion.suggestionId,
          action: "ACCEPT",
          sourceType: suggestion.source,
          sourceDetail: suggestion.sourceDetail,
        });
      });
    });
  });

  describe("Delete commit confirmation", () => {
    it("shows confirm dialog when delete is clicked in commit editor", async () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      mockCommits = [makeCommit({ id: "c-1" })];
      render(<WeeklyPlanPage />);

      // Click commit row to open editor
      fireEvent.click(screen.getByTestId("commit-row-c-1"));

      // Click delete
      fireEvent.click(screen.getByTestId("commit-delete"));

      expect(screen.getByTestId("confirm-dialog")).toBeInTheDocument();
      expect(screen.getByText("Delete this commitment? This cannot be undone.")).toBeInTheDocument();
    });

    it("calls deleteCommit when confirm is clicked", async () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      mockCommits = [makeCommit({ id: "c-1" })];
      mockDeleteCommit.mockResolvedValue(true);
      render(<WeeklyPlanPage />);

      fireEvent.click(screen.getByTestId("commit-row-c-1"));
      fireEvent.click(screen.getByTestId("commit-delete"));
      fireEvent.click(screen.getByTestId("confirm-dialog-confirm"));

      await waitFor(() => {
        expect(mockDeleteCommit).toHaveBeenCalledWith("c-1");
      });
    });

    it("dismisses dialog when cancel is clicked without deleting", () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      mockCommits = [makeCommit({ id: "c-1" })];
      render(<WeeklyPlanPage />);

      fireEvent.click(screen.getByTestId("commit-row-c-1"));
      fireEvent.click(screen.getByTestId("commit-delete"));

      expect(screen.getByTestId("confirm-dialog")).toBeInTheDocument();
      fireEvent.click(screen.getByTestId("confirm-dialog-cancel"));

      expect(screen.queryByTestId("confirm-dialog")).not.toBeInTheDocument();
      expect(mockDeleteCommit).not.toHaveBeenCalled();
    });
  });
});
