import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BacklogPickerDialog } from "../components/BacklogPickerDialog.js";
import type { Team, Issue } from "@weekly-commitments/contracts";
import { IssueStatus, EffortType, ChessPriority } from "@weekly-commitments/contracts";

/* ── Mocks ── */

const mockFetchTeams = vi.fn();
const mockFetchIssues = vi.fn();

const mockTeamsHook = {
  teams: [] as Team[],
  loading: false,
  error: null as string | null,
  fetchTeams: mockFetchTeams,
  fetchTeamDetail: vi.fn(),
  createTeam: vi.fn(),
  updateTeam: vi.fn(),
  addMember: vi.fn(),
  removeMember: vi.fn(),
  clearError: vi.fn(),
};

const mockIssuesHook = {
  issues: [] as Issue[],
  totalElements: 0,
  totalPages: 0,
  loading: false,
  error: null as string | null,
  fetchIssues: mockFetchIssues,
  fetchIssueDetail: vi.fn(),
  createIssue: vi.fn(),
  updateIssue: vi.fn(),
  deleteIssue: vi.fn(),
  assignIssue: vi.fn(),
  addComment: vi.fn(),
  logTime: vi.fn(),
  clearError: vi.fn(),
};

vi.mock("../hooks/useTeams.js", () => ({
  useTeams: () => mockTeamsHook,
}));

vi.mock("../hooks/useIssues.js", () => ({
  useIssues: () => mockIssuesHook,
}));

/* ── Helpers ── */

function makeTeam(overrides: Partial<Team> = {}): Team {
  return {
    id: "team-1",
    orgId: "org-1",
    name: "Platform",
    keyPrefix: "PLAT",
    description: null,
    ownerUserId: "user-1",
    issueSequence: 10,
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
    issueKey: "PLAT-1",
    sequenceNumber: 1,
    title: "Add Redis caching layer",
    description: "Implement Redis for session caching.",
    effortType: EffortType.BUILD,
    estimatedHours: 8,
    chessPriority: ChessPriority.ROOK,
    outcomeId: null,
    nonStrategicReason: null,
    creatorUserId: "user-1",
    assigneeUserId: null,
    blockedByIssueId: null,
    status: IssueStatus.OPEN,
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

/* ── Tests ── */

describe("BacklogPickerDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockTeamsHook.teams = [];
    mockTeamsHook.loading = false;
    mockTeamsHook.error = null;
    mockIssuesHook.issues = [];
    mockIssuesHook.loading = false;
    mockIssuesHook.error = null;
  });

  it("renders with dialog role and accessible title", () => {
    render(
      <BacklogPickerDialog
        weekStart="2026-03-09"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    const dialog = screen.getByTestId("backlog-picker-dialog");
    expect(dialog).toHaveAttribute("role", "dialog");
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(dialog).toHaveAttribute("aria-labelledby", "backlog-picker-dialog-title");
    expect(screen.getByText("Add from Backlog")).toBeInTheDocument();
  });

  it("calls fetchTeams on mount", () => {
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    expect(mockFetchTeams).toHaveBeenCalledOnce();
  });

  it("shows team selector with available teams", () => {
    mockTeamsHook.teams = [
      makeTeam({ id: "t1", name: "Platform", keyPrefix: "PLAT" }),
      makeTeam({ id: "t2", name: "Security", keyPrefix: "SEC" }),
    ];
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    const select = screen.getByTestId("backlog-picker-team-select");
    expect(select).toBeInTheDocument();
    expect(screen.getByText("Platform (PLAT)")).toBeInTheDocument();
    expect(screen.getByText("Security (SEC)")).toBeInTheDocument();
  });

  it("shows issues in the list", () => {
    mockTeamsHook.teams = [makeTeam()];
    mockIssuesHook.issues = [
      makeIssue({ id: "i1", issueKey: "PLAT-1", title: "Add Redis caching layer" }),
      makeIssue({ id: "i2", issueKey: "PLAT-2", title: "Fix auth token expiry" }),
    ];
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    expect(screen.getByTestId("backlog-picker-issue-i1")).toBeInTheDocument();
    expect(screen.getByTestId("backlog-picker-issue-i2")).toBeInTheDocument();
    expect(screen.getByText("PLAT-1")).toBeInTheDocument();
    expect(screen.getByText("PLAT-2")).toBeInTheDocument();
    expect(screen.getByText("Add Redis caching layer")).toBeInTheDocument();
  });

  it("shows empty state when no issues match", () => {
    mockTeamsHook.teams = [makeTeam()];
    mockIssuesHook.issues = [];
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    expect(screen.getByText(/No open issues found/i)).toBeInTheDocument();
  });

  it("shows loading state while fetching issues", () => {
    mockTeamsHook.teams = [makeTeam()];
    mockIssuesHook.loading = true;
    mockIssuesHook.issues = [];
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    expect(screen.getByText(/Loading issues/i)).toBeInTheDocument();
  });

  it("shows error state when there is an API error", () => {
    mockTeamsHook.teams = [makeTeam()];
    mockIssuesHook.error = "Failed to fetch issues";
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Failed to fetch issues");
  });

  it("selecting an issue enables the confirm button", () => {
    mockTeamsHook.teams = [makeTeam()];
    mockIssuesHook.issues = [makeIssue({ id: "i1" })];
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    const confirmBtn = screen.getByTestId("backlog-picker-confirm");
    expect(confirmBtn).toBeDisabled();

    const issueRow = screen.getByTestId("backlog-picker-issue-i1");
    const checkbox = issueRow.querySelector("input[type=checkbox]") as HTMLInputElement;
    fireEvent.click(checkbox);

    expect(confirmBtn).not.toBeDisabled();
    expect(confirmBtn).toHaveTextContent("Add to Plan (1)");
  });

  it("selecting multiple issues shows correct count", () => {
    mockTeamsHook.teams = [makeTeam()];
    mockIssuesHook.issues = [
      makeIssue({ id: "i1" }),
      makeIssue({ id: "i2", issueKey: "PLAT-2", title: "Fix auth" }),
    ];
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    fireEvent.click(
      (screen.getByTestId("backlog-picker-issue-i1").querySelector("input[type=checkbox]") as HTMLInputElement),
    );
    fireEvent.click(
      (screen.getByTestId("backlog-picker-issue-i2").querySelector("input[type=checkbox]") as HTMLInputElement),
    );
    expect(screen.getByTestId("backlog-picker-confirm")).toHaveTextContent("Add to Plan (2)");
    expect(screen.getByText("2 issues selected")).toBeInTheDocument();
  });

  it("calls onConfirm with selected issues when confirmed", () => {
    const onConfirm = vi.fn();
    const issue = makeIssue({ id: "i1" });
    mockTeamsHook.teams = [makeTeam()];
    mockIssuesHook.issues = [issue];
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={onConfirm} onCancel={vi.fn()} />,
    );
    fireEvent.click(
      (screen.getByTestId("backlog-picker-issue-i1").querySelector("input[type=checkbox]") as HTMLInputElement),
    );
    fireEvent.click(screen.getByTestId("backlog-picker-confirm"));
    expect(onConfirm).toHaveBeenCalledWith([issue]);
  });

  it("calls onCancel when cancel button is clicked", () => {
    const onCancel = vi.fn();
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={onCancel} />,
    );
    fireEvent.click(screen.getByTestId("backlog-picker-cancel"));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("calls onCancel when Escape key is pressed", async () => {
    const onCancel = vi.fn();
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={onCancel} />,
    );
    fireEvent.keyDown(screen.getByTestId("backlog-picker-dialog"), { key: "Escape" });
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("shows effort type badge for issues", () => {
    mockTeamsHook.teams = [makeTeam()];
    mockIssuesHook.issues = [makeIssue({ id: "i1", effortType: EffortType.BUILD })];
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    expect(screen.getByTestId("issue-effort-i1")).toHaveTextContent("BUILD");
  });

  it("triggers a new issue search when search input changes", async () => {
    mockTeamsHook.teams = [makeTeam()];
    mockIssuesHook.issues = [];
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    const searchInput = screen.getByTestId("backlog-picker-search");
    fireEvent.change(searchInput, { target: { value: "redis" } });
    await waitFor(() => {
      // fetchIssues should have been called with the search term
      expect(mockFetchIssues).toHaveBeenCalledWith(
        expect.any(String),
        0,
        50,
        expect.objectContaining({ search: "redis" }),
      );
    });
  });

  it("is in loading state when loading prop is true", () => {
    render(
      <BacklogPickerDialog weekStart="2026-03-09" onConfirm={vi.fn()} onCancel={vi.fn()} loading />,
    );
    expect(screen.getByTestId("backlog-picker-cancel")).toBeDisabled();
    expect(screen.getByTestId("backlog-picker-confirm")).toBeDisabled();
  });
});
