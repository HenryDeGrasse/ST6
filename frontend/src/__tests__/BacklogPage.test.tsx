import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BacklogPage } from "../pages/BacklogPage.js";
import type { Team, Issue } from "@weekly-commitments/contracts";
import { IssueStatus, EffortType } from "@weekly-commitments/contracts";

const mockTeamsHook = {
  teams: [] as Team[],
  loading: false,
  error: null as string | null,
  fetchTeams: vi.fn(),
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
  fetchIssues: vi.fn(),
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

vi.mock("../hooks/useRcdo.js", () => ({
  useRcdo: () => ({
    tree: [],
    searchResults: [],
    loading: false,
    error: null,
    fetchTree: vi.fn().mockResolvedValue(undefined),
    search: vi.fn().mockResolvedValue(undefined),
    clearSearch: vi.fn(),
    clearError: vi.fn(),
  }),
}));

const mockApiClient = {
  GET: vi.fn(),
  POST: vi.fn().mockResolvedValue({ data: null, response: { status: 503 } }),
  PATCH: vi.fn(),
  DELETE: vi.fn(),
  use: vi.fn(),
};
vi.mock("../api/ApiContext.js", () => ({
  useApiClient: () => mockApiClient,
}));

const mockTeam: Team = {
  id: "team-1",
  orgId: "org-1",
  name: "Engineering",
  keyPrefix: "ENG",
  description: null,
  ownerUserId: "user-1",
  issueSequence: 5,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const mockIssue: Issue = {
  id: "issue-1",
  orgId: "org-1",
  teamId: "team-1",
  issueKey: "ENG-1",
  sequenceNumber: 1,
  title: "Fix the login bug",
  description: "Users cannot log in on Safari",
  effortType: EffortType.BUILD,
  estimatedHours: 4,
  chessPriority: null,
  outcomeId: null,
  nonStrategicReason: null,
  creatorUserId: "user-1",
  assigneeUserId: "user-2",
  blockedByIssueId: null,
  status: IssueStatus.OPEN,
  aiRecommendedRank: 1,
  aiRankRationale: null,
  aiSuggestedEffortType: null,
  version: 1,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  archivedAt: null,
};

describe("BacklogPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockTeamsHook.teams = [];
    mockTeamsHook.error = null;
    mockTeamsHook.loading = false;
    mockTeamsHook.fetchTeamDetail.mockImplementation(() => new Promise(() => {}));
    mockIssuesHook.issues = [];
    mockIssuesHook.error = null;
    mockIssuesHook.loading = false;
    mockIssuesHook.totalElements = 0;
    mockIssuesHook.totalPages = 0;
    mockApiClient.GET.mockImplementation(() => new Promise(() => {}));
  });

  it("renders the backlog page with title", () => {
    render(<BacklogPage />);
    expect(screen.getByTestId("backlog-page")).toBeInTheDocument();
    expect(screen.getByText("Backlog")).toBeInTheDocument();
  });

  it("calls fetchTeams on mount", () => {
    render(<BacklogPage />);
    expect(mockTeamsHook.fetchTeams).toHaveBeenCalledTimes(1);
  });

  it("shows empty state when user has no teams", () => {
    render(<BacklogPage />);
    expect(screen.getByText(/You don't belong to any team yet/)).toBeInTheDocument();
  });

  it("shows issue table when team and issues are loaded", async () => {
    mockTeamsHook.teams = [mockTeam];
    mockTeamsHook.fetchTeamDetail.mockResolvedValue({
      team: mockTeam,
      members: [
        {
          teamId: "team-1",
          userId: "user-2",
          orgId: "org-1",
          role: "MEMBER",
          joinedAt: "2026-01-01T00:00:00Z",
        },
      ],
    });
    mockIssuesHook.issues = [mockIssue];
    mockIssuesHook.totalElements = 1;

    render(<BacklogPage />);
    expect(screen.getByTestId("issue-table")).toBeInTheDocument();
    expect(screen.getByText("ENG-1")).toBeInTheDocument();
    expect(screen.getByText("Fix the login bug")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getAllByText("user-2").length).toBeGreaterThan(0);
    });
  });

  it("renders status and effort type badges", () => {
    mockTeamsHook.teams = [mockTeam];
    mockIssuesHook.issues = [mockIssue];

    render(<BacklogPage />);
    const openElements = screen.getAllByText("OPEN");
    expect(openElements.length).toBeGreaterThan(0);
    const buildElements = screen.getAllByText("Build");
    expect(buildElements.length).toBeGreaterThan(0);
  });

  it("shows team selector when there are multiple teams", () => {
    const team2: Team = { ...mockTeam, id: "team-2", name: "Design", keyPrefix: "DSG" };
    mockTeamsHook.teams = [mockTeam, team2];

    render(<BacklogPage />);
    expect(screen.getByTestId("backlog-team-select")).toBeInTheDocument();
  });

  it("hides team selector when there is only one team", () => {
    mockTeamsHook.teams = [mockTeam];

    render(<BacklogPage />);
    expect(screen.queryByTestId("backlog-team-select")).toBeNull();
  });

  it("opens new issue modal when button is clicked", () => {
    mockTeamsHook.teams = [mockTeam];

    render(<BacklogPage />);
    fireEvent.click(screen.getByTestId("backlog-new-issue-btn"));
    expect(screen.getByTestId("new-issue-modal")).toBeInTheDocument();
    expect(screen.getByTestId("issue-create-form")).toBeInTheDocument();
  });

  it("closes new issue modal when cancel is clicked", async () => {
    mockTeamsHook.teams = [mockTeam];

    render(<BacklogPage />);
    fireEvent.click(screen.getByTestId("backlog-new-issue-btn"));
    expect(screen.getByTestId("new-issue-modal")).toBeInTheDocument();

    fireEvent.click(screen.getByTestId("issue-create-cancel"));
    await waitFor(() => {
      expect(screen.queryByTestId("new-issue-modal")).toBeNull();
    });
  });

  it("shows AI rank column when AI ranking toggle is on", () => {
    mockTeamsHook.teams = [mockTeam];
    mockIssuesHook.issues = [mockIssue];

    render(<BacklogPage />);
    fireEvent.click(screen.getByTestId("backlog-ai-rank-toggle"));

    expect(screen.getByText("AI Rank")).toBeInTheDocument();
    expect(screen.getByText("#1")).toBeInTheDocument();
  });

  it("shows error banner when teams hook has an error", () => {
    mockTeamsHook.error = "Failed to load teams";

    render(<BacklogPage />);
    expect(screen.getByText("Failed to load teams")).toBeInTheDocument();
  });

  it("shows error banner when issues hook has an error", () => {
    mockTeamsHook.teams = [mockTeam];
    mockIssuesHook.error = "Failed to load issues";

    render(<BacklogPage />);
    expect(screen.getByText("Failed to load issues")).toBeInTheDocument();
  });

  it("calls fetchIssues with ai_rank sort when AI ranking is toggled", () => {
    mockTeamsHook.teams = [mockTeam];

    render(<BacklogPage />);
    fireEvent.click(screen.getByTestId("backlog-ai-rank-toggle"));

    expect(mockIssuesHook.fetchIssues).toHaveBeenCalledWith("team-1", 0, 20, {}, "ai_rank");
  });

  it("passes assignee filter through to useIssues", async () => {
    mockTeamsHook.teams = [mockTeam];
    mockTeamsHook.fetchTeamDetail.mockResolvedValue({
      team: mockTeam,
      members: [
        {
          teamId: "team-1",
          userId: "user-2",
          orgId: "org-1",
          role: "MEMBER",
          joinedAt: "2026-01-01T00:00:00Z",
        },
      ],
    });

    render(<BacklogPage />);

    await waitFor(() => {
      expect(mockTeamsHook.fetchTeamDetail).toHaveBeenCalledWith("team-1");
    });

    fireEvent.change(screen.getByTestId("backlog-assignee-filter"), {
      target: { value: "user-2" },
    });

    expect(mockIssuesHook.fetchIssues).toHaveBeenLastCalledWith(
      "team-1",
      0,
      20,
      { assigneeUserId: "user-2" },
      "createdAt",
    );
  });
});
