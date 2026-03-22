/**
 * Tests for WeeklyPlanPage Phase 6 additions:
 *   - Shows 'Add from Backlog' button when useIssueBacklog flag is on
 *   - Opens BacklogPickerDialog when button is clicked
 *   - Renders assignment rows in CommitList
 *   - Feature flag off: hides backlog-related UI
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { WeeklyPlanPage } from "../pages/WeeklyPlanPage.js";
import { FeatureFlagProvider } from "../context/FeatureFlagContext.js";
import { getWeekStart } from "../utils/week.js";
import { PlanState, ReviewStatus, ChessPriority, IssueStatus, EffortType } from "@weekly-commitments/contracts";
import type { WeeklyPlan, WeeklyAssignmentWithActual, Issue } from "@weekly-commitments/contracts";

/* ── Mock hooks ── */

const mockFetchPlan = vi.fn();
const mockFetchCommits = vi.fn();
const mockResetCommits = vi.fn();
const mockFetchAssignments = vi.fn();
const mockResetAssignments = vi.fn();
const mockCreateAssignment = vi.fn();
const mockRemoveAssignment = vi.fn();
const mockReleaseToBacklog = vi.fn();

let mockPlan: WeeklyPlan | null = null;
let mockAssignments: WeeklyAssignmentWithActual[] = [];

vi.mock("../hooks/usePlan.js", () => ({
  usePlan: () => ({
    plan: mockPlan,
    loading: false,
    error: null,
    fetchPlan: mockFetchPlan,
    createPlan: vi.fn(),
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
    assignments: mockAssignments,
    loading: false,
    error: null,
    fetchAssignments: mockFetchAssignments,
    createAssignment: mockCreateAssignment,
    removeAssignment: mockRemoveAssignment,
    releaseToBacklog: mockReleaseToBacklog,
    resetAssignments: mockResetAssignments,
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useRcdo.js", () => ({
  useRcdo: () => ({
    tree: [],
    searchResults: [],
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
    nudges: [],
    status: "idle",
    checkQuality: vi.fn(),
    clearNudges: vi.fn(),
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
    fetchCheckIns: vi.fn(),
    clearEntries: vi.fn(),
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useUserProfile.js", () => ({
  useUserProfile: () => ({
    profile: null,
    loading: false,
    error: null,
    fetchProfile: vi.fn(),
    clearError: vi.fn(),
  }),
}));

vi.mock("../hooks/useCapacity.js", () => ({
  useCapacityProfile: () => ({
    profile: null,
    loading: false,
    error: null,
    fetchProfile: vi.fn(),
    clearError: vi.fn(),
  }),
}));

vi.mock("../components/WeekSelector.js", () => ({
  WeekSelector: () => <div data-testid="week-selector" />,
}));

vi.mock("../components/BacklogPickerDialog.js", () => ({
  BacklogPickerDialog: ({ onCancel, onConfirm }: { onCancel: () => void; onConfirm: (issues: Issue[]) => void }) => (
    <div data-testid="backlog-picker-dialog">
      <button data-testid="backlog-picker-cancel" onClick={onCancel}>
        Cancel
      </button>
      <button
        data-testid="backlog-picker-confirm"
        onClick={() =>
          onConfirm([
            makeIssue({ id: "issue-99", issueKey: "PLAT-99", title: "Ship assignment" }),
          ])
        }
      >
        Confirm
      </button>
    </div>
  ),
}));

/* ── Helpers ── */

function makePlan(overrides: Partial<WeeklyPlan> = {}): WeeklyPlan {
  return {
    id: "plan-1",
    orgId: "org-1",
    ownerUserId: "user-1",
    weekStartDate: getWeekStart(),
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

function makeIssue(overrides: Partial<Issue> = {}): Issue {
  return {
    id: "issue-1",
    orgId: "org-1",
    teamId: "team-1",
    issueKey: "PLAT-42",
    sequenceNumber: 42,
    title: "Add Redis caching layer",
    description: null,
    effortType: EffortType.BUILD,
    estimatedHours: 8,
    chessPriority: ChessPriority.ROOK,
    outcomeId: null,
    nonStrategicReason: null,
    creatorUserId: "user-1",
    assigneeUserId: null,
    blockedByIssueId: null,
    status: IssueStatus.IN_PROGRESS,
    aiRecommendedRank: null,
    aiRankRationale: null,
    aiSuggestedEffortType: null,
    version: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    archivedAt: null,
    ...overrides,
  };
}

function makeAssignment(overrides: Partial<WeeklyAssignmentWithActual> = {}): WeeklyAssignmentWithActual {
  return {
    id: "assignment-1",
    orgId: "org-1",
    weeklyPlanId: "plan-1",
    issueId: "issue-1",
    chessPriorityOverride: null,
    expectedResult: null,
    confidence: null,
    snapshotRallyCryId: null,
    snapshotRallyCryName: null,
    snapshotObjectiveId: null,
    snapshotObjectiveName: null,
    snapshotOutcomeId: null,
    snapshotOutcomeName: null,
    tags: [],
    version: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    actual: null,
    issue: makeIssue(),
    ...overrides,
  };
}

function renderWithFlags(flags: Record<string, boolean> = {}) {
  return render(
    <FeatureFlagProvider flags={flags}>
      <WeeklyPlanPage />
    </FeatureFlagProvider>,
  );
}

/* ── Tests ── */

describe("WeeklyPlanPage — Phase 6 backlog/assignment workflow", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPlan = null;
    mockAssignments = [];
    mockCreateAssignment.mockResolvedValue({ id: "new-assignment" });
    mockRemoveAssignment.mockResolvedValue(true);
    mockReleaseToBacklog.mockResolvedValue(true);
  });

  it("does NOT show 'Add from Backlog' button when useIssueBacklog flag is off", () => {
    mockPlan = makePlan();
    renderWithFlags({ useIssueBacklog: false });
    expect(screen.queryByTestId("add-from-backlog-btn")).not.toBeInTheDocument();
  });

  it("shows 'Add from Backlog' button when useIssueBacklog flag is on in DRAFT state", () => {
    mockPlan = makePlan({ state: PlanState.DRAFT });
    renderWithFlags({ useIssueBacklog: true });
    expect(screen.getByTestId("add-from-backlog-btn")).toBeInTheDocument();
  });

  it("opens BacklogPickerDialog when 'Add from Backlog' is clicked", async () => {
    mockPlan = makePlan({ state: PlanState.DRAFT });
    renderWithFlags({ useIssueBacklog: true });
    fireEvent.click(screen.getByTestId("add-from-backlog-btn"));
    await waitFor(() => {
      expect(screen.getByTestId("backlog-picker-dialog")).toBeInTheDocument();
    });
  });

  it("closes BacklogPickerDialog when cancel is clicked", async () => {
    mockPlan = makePlan({ state: PlanState.DRAFT });
    renderWithFlags({ useIssueBacklog: true });
    fireEvent.click(screen.getByTestId("add-from-backlog-btn"));
    await waitFor(() => {
      expect(screen.getByTestId("backlog-picker-dialog")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByTestId("backlog-picker-cancel"));
    await waitFor(() => {
      expect(screen.queryByTestId("backlog-picker-dialog")).not.toBeInTheDocument();
    });
  });

  it("creates backlog assignments and refreshes both assignments and commits when confirmed", async () => {
    mockPlan = makePlan({ state: PlanState.DRAFT });
    renderWithFlags({ useIssueBacklog: true });

    fireEvent.click(screen.getByTestId("add-from-backlog-btn"));
    await waitFor(() => {
      expect(screen.getByTestId("backlog-picker-dialog")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId("backlog-picker-confirm"));

    await waitFor(() => {
      expect(mockCreateAssignment).toHaveBeenCalledWith(mockPlan?.weekStartDate, { issueId: "issue-99" });
    });
    await waitFor(() => {
      expect(mockFetchAssignments).toHaveBeenCalledWith("plan-1");
      expect(mockFetchCommits).toHaveBeenCalledWith("plan-1");
    });
  });

  it("renders assignment rows when useIssueBacklog is enabled and assignments exist", () => {
    mockPlan = makePlan({ state: PlanState.DRAFT });
    mockAssignments = [makeAssignment()];
    renderWithFlags({ useIssueBacklog: true });
    expect(screen.getByTestId("assignment-row-assignment-1")).toBeInTheDocument();
  });

  it("removes an assignment and refreshes commits in draft mode", async () => {
    mockPlan = makePlan({ state: PlanState.DRAFT });
    mockAssignments = [makeAssignment()];
    renderWithFlags({ useIssueBacklog: true });

    fireEvent.click(screen.getByTestId("assignment-remove-assignment-1"));

    await waitFor(() => {
      expect(mockRemoveAssignment).toHaveBeenCalledWith(mockPlan?.weekStartDate, "assignment-1");
      expect(mockFetchCommits).toHaveBeenCalledWith("plan-1");
    });
  });

  it("does NOT render assignment rows when useIssueBacklog is disabled", () => {
    mockPlan = makePlan({ state: PlanState.DRAFT });
    mockAssignments = [makeAssignment()];
    renderWithFlags({ useIssueBacklog: false });
    expect(screen.queryByTestId("assignment-row-assignment-1")).not.toBeInTheDocument();
  });

  it("releases assignment-based work back to backlog and refreshes data in reconciliation", async () => {
    mockPlan = makePlan({ state: PlanState.RECONCILING });
    mockAssignments = [makeAssignment()];
    renderWithFlags({ useIssueBacklog: true });

    fireEvent.click(screen.getByTestId("release-to-backlog-assignment-1"));

    await waitFor(() => {
      expect(mockReleaseToBacklog).toHaveBeenCalledWith("issue-1", "plan-1");
    });
    await waitFor(() => {
      expect(mockFetchAssignments).toHaveBeenCalledWith("plan-1");
      expect(mockFetchCommits).toHaveBeenCalledWith("plan-1");
    });
  });

  it("calls fetchAssignments when plan loads and useIssueBacklog is enabled", async () => {
    mockPlan = makePlan({ state: PlanState.DRAFT });
    renderWithFlags({ useIssueBacklog: true });
    await waitFor(() => {
      expect(mockFetchAssignments).toHaveBeenCalledWith("plan-1");
    });
  });

  it("does NOT call fetchAssignments when useIssueBacklog is disabled", async () => {
    mockPlan = makePlan({ state: PlanState.DRAFT });
    renderWithFlags({ useIssueBacklog: false });
    // Give effects a chance to run
    await waitFor(() => {
      expect(mockResetAssignments).toHaveBeenCalled();
    });
    expect(mockFetchAssignments).not.toHaveBeenCalled();
  });
});
