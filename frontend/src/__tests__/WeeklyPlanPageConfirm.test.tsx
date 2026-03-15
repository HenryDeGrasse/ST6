import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { WeeklyPlanPage } from "../pages/WeeklyPlanPage.js";
import { PlanState, ReviewStatus, ChessPriority } from "@weekly-commitments/contracts";
import type { WeeklyPlan, WeeklyCommit } from "@weekly-commitments/contracts";

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

describe("WeeklyPlanPage confirmation dialogs", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPlan = null;
    mockCommits = [];
    mockPlanLoading = false;
    mockCommitsLoading = false;
  });

  describe("Lock plan confirmation", () => {
    it("shows confirm dialog when Lock button is clicked", () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      render(<WeeklyPlanPage />);

      fireEvent.click(screen.getByTestId("lock-btn"));

      expect(screen.getByTestId("confirm-dialog")).toBeInTheDocument();
      expect(screen.getByText("Lock this plan? Once locked, you cannot add or remove commitments.")).toBeInTheDocument();
    });

    it("calls lockPlan when confirm is clicked", async () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      mockLockPlan.mockResolvedValue(makePlan({ state: PlanState.LOCKED }));
      render(<WeeklyPlanPage />);

      fireEvent.click(screen.getByTestId("lock-btn"));
      fireEvent.click(screen.getByTestId("confirm-dialog-confirm"));

      await waitFor(() => {
        expect(mockLockPlan).toHaveBeenCalledWith("plan-1", 1);
      });
    });

    it("dismisses dialog when cancel is clicked", () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      render(<WeeklyPlanPage />);

      fireEvent.click(screen.getByTestId("lock-btn"));
      expect(screen.getByTestId("confirm-dialog")).toBeInTheDocument();

      fireEvent.click(screen.getByTestId("confirm-dialog-cancel"));
      expect(screen.queryByTestId("confirm-dialog")).not.toBeInTheDocument();
      expect(mockLockPlan).not.toHaveBeenCalled();
    });

    it("keeps the normal lock label during commit loading", () => {
      mockPlan = makePlan({ state: PlanState.DRAFT });
      mockCommitsLoading = true;
      render(<WeeklyPlanPage />);

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
