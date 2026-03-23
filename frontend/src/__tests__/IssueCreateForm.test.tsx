import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { IssueCreateForm } from "../components/IssueCreateForm.js";
import type { Team } from "@weekly-commitments/contracts";

const mockClient = {
  GET: vi.fn(),
  POST: vi.fn(),
  PATCH: vi.fn(),
  DELETE: vi.fn(),
  use: vi.fn(),
};

vi.mock("../api/ApiContext.js", () => ({
  useApiClient: () => mockClient,
}));

vi.mock("../hooks/useRcdo.js", () => ({
  useRcdo: () => ({
    tree: [
      {
        id: "cry-1",
        name: "Grow Product",
        objectives: [
          {
            id: "obj-1",
            name: "Ship core flows",
            outcomes: [{ id: "outcome-1", name: "Improve login success" }],
          },
        ],
      },
    ],
    searchResults: [
      {
        id: "outcome-1",
        name: "Improve login success",
        objectiveId: "obj-1",
        objectiveName: "Ship core flows",
        rallyCryId: "cry-1",
        rallyCryName: "Grow Product",
      },
    ],
    loading: false,
    error: null,
    fetchTree: vi.fn().mockResolvedValue(undefined),
    search: vi.fn().mockResolvedValue(undefined),
    clearSearch: vi.fn(),
    clearError: vi.fn(),
  }),
}));

const mockTeams: Team[] = [
  {
    id: "team-1",
    orgId: "org-1",
    name: "Engineering",
    keyPrefix: "ENG",
    description: null,
    ownerUserId: "user-1",
    issueSequence: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
  {
    id: "team-2",
    orgId: "org-1",
    name: "Design",
    keyPrefix: "DSG",
    description: null,
    ownerUserId: "user-1",
    issueSequence: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

describe("IssueCreateForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockClient.POST.mockResolvedValue({ data: null, response: { status: 503 } });
    mockClient.GET.mockImplementation(() => new Promise(() => {}));
  });

  it("renders form fields", () => {
    render(
      <IssueCreateForm
        teams={mockTeams}
        defaultTeamId="team-1"
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("issue-create-form")).toBeInTheDocument();
    expect(screen.getByTestId("issue-title-input")).toBeInTheDocument();
    expect(screen.getByTestId("issue-description-input")).toBeInTheDocument();
    expect(screen.getByTestId("effort-type-picker")).toBeInTheDocument();
    expect(screen.getByTestId("rcdo-picker")).toBeInTheDocument();
    expect(screen.getByTestId("issue-assignee-select")).toBeInTheDocument();
  });

  it("shows team selector when multiple teams are provided", () => {
    render(
      <IssueCreateForm
        teams={mockTeams}
        defaultTeamId="team-1"
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("issue-team-select")).toBeInTheDocument();
  });

  it("hides team selector when only one team is provided", () => {
    render(
      <IssueCreateForm
        teams={[mockTeams[0]]}
        defaultTeamId="team-1"
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.queryByTestId("issue-team-select")).toBeNull();
  });

  it("calls onCancel when cancel button is clicked", () => {
    const onCancel = vi.fn();
    render(
      <IssueCreateForm
        teams={mockTeams}
        defaultTeamId="team-1"
        onSubmit={vi.fn()}
        onCancel={onCancel}
      />,
    );
    fireEvent.click(screen.getByTestId("issue-create-cancel"));
    expect(onCancel).toHaveBeenCalled();
  });

  it("shows validation error when title is empty on submit", async () => {
    const onSubmit = vi.fn();
    render(
      <IssueCreateForm
        teams={mockTeams}
        defaultTeamId="team-1"
        onSubmit={onSubmit}
        onCancel={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByTestId("issue-create-submit"));
    await waitFor(() => {
      expect(screen.getByText("Title is required")).toBeInTheDocument();
    });
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("calls onSubmit with selected outcome, assignee, and blocker", async () => {
    mockClient.GET.mockImplementation(async (path: string) => {
      if (path === "/teams/{teamId}") {
        return {
          data: {
            team: mockTeams[0],
            members: [
              {
                teamId: "team-1",
                userId: "user-2",
                orgId: "org-1",
                role: "MEMBER",
                joinedAt: "2026-01-01T00:00:00Z",
              },
            ],
          },
          response: { status: 200 },
        };
      }

      if (path === "/teams/{teamId}/issues") {
        return {
          data: {
            content: [
              {
                id: "issue-2",
                orgId: "org-1",
                teamId: "team-1",
                issueKey: "ENG-2",
                sequenceNumber: 2,
                title: "Investigate Safari auth",
                description: null,
                effortType: null,
                estimatedHours: null,
                chessPriority: null,
                outcomeId: null,
                nonStrategicReason: null,
                creatorUserId: "user-1",
                assigneeUserId: null,
                blockedByIssueId: null,
                status: "OPEN",
                aiRecommendedRank: null,
                aiRankRationale: null,
                aiSuggestedEffortType: null,
                version: 1,
                createdAt: "2026-01-01T00:00:00Z",
                updatedAt: "2026-01-01T00:00:00Z",
                archivedAt: null,
              },
            ],
            page: 0,
            size: 100,
            totalElements: 1,
            totalPages: 1,
          },
          response: { status: 200 },
        };
      }

      return { data: null, response: { status: 404 } };
    });

    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <IssueCreateForm
        teams={mockTeams}
        defaultTeamId="team-1"
        onSubmit={onSubmit}
        onCancel={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByTestId("issue-title-input"), {
      target: { value: "Fix the login bug" },
    });
    fireEvent.change(screen.getByTestId("issue-hours-input"), {
      target: { value: "3" },
    });

    // Open search panel first (browse is now the default mode)
    fireEvent.click(screen.getByTestId("rcdo-search-toggle"));
    fireEvent.click(screen.getByTestId("rcdo-result-outcome-1"));

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "user-2" })).toBeInTheDocument();
    });
    fireEvent.change(screen.getByTestId("issue-assignee-select"), {
      target: { value: "user-2" },
    });
    fireEvent.change(screen.getByTestId("issue-blocked-by-input"), {
      target: { value: "ENG-2 — Investigate Safari auth" },
    });

    fireEvent.click(screen.getByTestId("issue-create-submit"));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith(
        "team-1",
        expect.objectContaining({
          title: "Fix the login bug",
          estimatedHours: 3,
          outcomeId: "outcome-1",
          assigneeUserId: "user-2",
          blockedByIssueId: "issue-2",
        }),
      );
    });
  });

  it("disables submit button and shows 'Creating…' when submitting", () => {
    render(
      <IssueCreateForm
        teams={mockTeams}
        defaultTeamId="team-1"
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        submitting
      />,
    );
    const submitBtn = screen.getByTestId("issue-create-submit");
    expect(submitBtn).toBeDisabled();
    expect(submitBtn).toHaveTextContent("Creating…");
  });
});
