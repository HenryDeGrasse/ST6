import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { TeamManagementPage } from "../pages/TeamManagementPage.js";
import type {
  Team,
  TeamMember,
  TeamAccessRequest,
  TeamDetailResponse,
} from "@weekly-commitments/contracts";
import { AccessRequestStatus, TeamRole } from "@weekly-commitments/contracts";

// ── Auth mock ────────────────────────────────────────────────────────────────

const mockUser = {
  userId: "user-owner",
  orgId: "org-1",
  displayName: "Owner User",
  roles: ["IC", "MANAGER"],
  timezone: "UTC",
};

vi.mock("../context/AuthContext.js", () => ({
  useAuth: () => ({ user: mockUser }),
}));

// ── Hook mock ────────────────────────────────────────────────────────────────

const mockHook = {
  teams: [] as Team[],
  loading: false,
  error: null as string | null,
  teamDetail: null as TeamDetailResponse | null,
  accessRequests: [] as TeamAccessRequest[],
  loadingRequests: false,
  fetchTeams: vi.fn(),
  fetchTeamDetail: vi.fn(),
  createTeam: vi.fn(),
  updateTeam: vi.fn(),
  addMember: vi.fn(),
  removeMember: vi.fn(),
  fetchAccessRequests: vi.fn(),
  requestAccess: vi.fn(),
  decideAccessRequest: vi.fn(),
  clearError: vi.fn(),
};

vi.mock("../hooks/useTeamManagement.js", () => ({
  useTeamManagement: () => mockHook,
}));

// ── Fixtures ─────────────────────────────────────────────────────────────────

const mockTeam: Team = {
  id: "team-1",
  orgId: "org-1",
  name: "Engineering",
  keyPrefix: "ENG",
  description: "Build great things",
  ownerUserId: "user-owner",
  issueSequence: 5,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const ownerMember: TeamMember = {
  teamId: "team-1",
  userId: "user-owner",
  orgId: "org-1",
  role: TeamRole.OWNER,
  joinedAt: "2026-01-01T00:00:00Z",
};

const regularMember: TeamMember = {
  teamId: "team-1",
  userId: "user-2",
  orgId: "org-1",
  role: TeamRole.MEMBER,
  joinedAt: "2026-01-10T00:00:00Z",
};

const pendingRequest: TeamAccessRequest = {
  id: "req-1",
  teamId: "team-1",
  requesterUserId: "user-3",
  orgId: "org-1",
  status: AccessRequestStatus.PENDING,
  decidedByUserId: null,
  decidedAt: null,
  createdAt: "2026-02-01T00:00:00Z",
};

describe("TeamManagementPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockHook.teams = [];
    mockHook.teamDetail = null;
    mockHook.accessRequests = [];
    mockHook.loading = false;
    mockHook.loadingRequests = false;
    mockHook.error = null;
    mockHook.fetchTeams.mockResolvedValue(undefined);
    mockHook.fetchTeamDetail.mockResolvedValue(null);
    mockHook.fetchAccessRequests.mockResolvedValue(undefined);
  });

  it("renders the page heading", () => {
    render(<TeamManagementPage />);
    expect(screen.getByTestId("team-management-page")).toBeInTheDocument();
    expect(screen.getByText("Team Management")).toBeInTheDocument();
  });

  it("calls fetchTeams on mount", () => {
    render(<TeamManagementPage />);
    expect(mockHook.fetchTeams).toHaveBeenCalledTimes(1);
  });

  it("shows empty state when no teams", () => {
    render(<TeamManagementPage />);
    expect(screen.getByTestId("team-mgmt-empty")).toBeInTheDocument();
  });

  it("renders back button when onBack prop provided", () => {
    const onBack = vi.fn();
    render(<TeamManagementPage onBack={onBack} />);
    const backBtn = screen.getByTestId("team-mgmt-back-btn");
    expect(backBtn).toBeInTheDocument();
    fireEvent.click(backBtn);
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it("shows Create Team button for managers", () => {
    render(<TeamManagementPage />);
    expect(screen.getByTestId("create-team-btn")).toBeInTheDocument();
  });

  it("opens create team modal when Create Team is clicked", () => {
    render(<TeamManagementPage />);
    fireEvent.click(screen.getByTestId("create-team-btn"));
    expect(screen.getByTestId("create-team-modal")).toBeInTheDocument();
  });

  it("closes create team modal on cancel", () => {
    render(<TeamManagementPage />);
    fireEvent.click(screen.getByTestId("create-team-btn"));
    expect(screen.getByTestId("create-team-modal")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("create-team-cancel"));
    expect(screen.queryByTestId("create-team-modal")).toBeNull();
  });

  it("auto-derives prefix from team name in create modal", () => {
    render(<TeamManagementPage />);
    fireEvent.click(screen.getByTestId("create-team-btn"));
    const nameInput = screen.getByTestId("create-team-name-input");
    fireEvent.change(nameInput, { target: { value: "Platform Engineering" } });
    const prefixInput = screen.getByTestId("create-team-prefix-input") as HTMLInputElement;
    expect(prefixInput.value).toBe("PE");
  });

  it("allows overriding the prefix in create modal", () => {
    render(<TeamManagementPage />);
    fireEvent.click(screen.getByTestId("create-team-btn"));
    const nameInput = screen.getByTestId("create-team-name-input");
    fireEvent.change(nameInput, { target: { value: "Engineering" } });
    const prefixInput = screen.getByTestId("create-team-prefix-input");
    fireEvent.change(prefixInput, { target: { value: "ENG2" } });
    expect((prefixInput as HTMLInputElement).value).toBe("ENG2");
    // Changing name should not overwrite the manually set prefix
    fireEvent.change(nameInput, { target: { value: "Engineering Team" } });
    expect((prefixInput as HTMLInputElement).value).toBe("ENG2");
  });

  it("calls createTeam when form is submitted", async () => {
    mockHook.createTeam.mockResolvedValue(mockTeam);
    render(<TeamManagementPage />);
    fireEvent.click(screen.getByTestId("create-team-btn"));
    fireEvent.change(screen.getByTestId("create-team-name-input"), {
      target: { value: "Engineering" },
    });
    fireEvent.change(screen.getByTestId("create-team-prefix-input"), {
      target: { value: "ENG" },
    });
    fireEvent.click(screen.getByTestId("create-team-submit"));

    await waitFor(() => {
      expect(mockHook.createTeam).toHaveBeenCalledWith({
        name: "Engineering",
        keyPrefix: "ENG",
        description: null,
      });
    });
  });

  describe("with a selected team (owner)", () => {
    beforeEach(() => {
      mockHook.teams = [mockTeam];
      mockHook.teamDetail = {
        team: mockTeam,
        members: [ownerMember, regularMember],
      };
      mockHook.fetchTeamDetail.mockResolvedValue(mockHook.teamDetail);
    });

    it("renders team detail section", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(screen.getByTestId("team-info-section")).toBeInTheDocument();
      expect(screen.getByTestId("team-name-display")).toHaveTextContent("Engineering");
      expect(screen.getByTestId("team-prefix-display")).toHaveTextContent("ENG");
    });

    it("fetches access requests for owner views", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(mockHook.fetchAccessRequests).toHaveBeenCalledWith("team-1");
    });

    it("shows edit button for owner", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(screen.getByTestId("edit-team-btn")).toBeInTheDocument();
    });

    it("switches to edit mode when Edit clicked", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      fireEvent.click(screen.getByTestId("edit-team-btn"));
      expect(screen.getByTestId("edit-team-name-input")).toBeInTheDocument();
      expect(screen.getByTestId("save-team-btn")).toBeInTheDocument();
    });

    it("cancels editing when Cancel clicked", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      fireEvent.click(screen.getByTestId("edit-team-btn"));
      fireEvent.click(screen.getByTestId("cancel-edit-team-btn"));
      expect(screen.queryByTestId("edit-team-name-input")).toBeNull();
    });

    it("calls updateTeam with new name when saved", async () => {
      mockHook.updateTeam.mockResolvedValue({ ...mockTeam, name: "New Name" });
      render(<TeamManagementPage initialTeamId="team-1" />);
      fireEvent.click(screen.getByTestId("edit-team-btn"));
      const nameInput = screen.getByTestId("edit-team-name-input");
      fireEvent.change(nameInput, { target: { value: "New Name" } });
      fireEvent.click(screen.getByTestId("save-team-btn"));

      await waitFor(() => {
        expect(mockHook.updateTeam).toHaveBeenCalledWith("team-1", {
          name: "New Name",
          description: expect.anything() as unknown,
        });
      });
    });

    it("renders member table with members", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(screen.getByTestId("members-section")).toBeInTheDocument();
      expect(screen.getByTestId("member-table")).toBeInTheDocument();
      expect(screen.getByTestId("member-row-user-owner")).toBeInTheDocument();
      expect(screen.getByTestId("member-row-user-2")).toBeInTheDocument();
    });

    it("shows Remove button for non-owner members when user is owner", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(screen.getByTestId("remove-member-user-2")).toBeInTheDocument();
    });

    it("does not show Remove button for the owner themselves", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(screen.queryByTestId("remove-member-user-owner")).toBeNull();
    });

    it("calls removeMember when Remove is clicked", async () => {
      mockHook.removeMember.mockResolvedValue(true);
      render(<TeamManagementPage initialTeamId="team-1" />);
      fireEvent.click(screen.getByTestId("remove-member-user-2"));

      await waitFor(() => {
        expect(mockHook.removeMember).toHaveBeenCalledWith("team-1", "user-2");
      });
    });

    it("shows add member form for owner", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(screen.getByTestId("add-member-form")).toBeInTheDocument();
      expect(screen.getByTestId("add-member-input")).toBeInTheDocument();
    });

    it("calls addMember when Add Member button is clicked", async () => {
      mockHook.addMember.mockResolvedValue({
        teamId: "team-1",
        userId: "user-new",
        orgId: "org-1",
        role: TeamRole.MEMBER,
        joinedAt: "2026-03-01T00:00:00Z",
      });
      render(<TeamManagementPage initialTeamId="team-1" />);
      fireEvent.change(screen.getByTestId("add-member-input"), {
        target: { value: "user-new" },
      });
      fireEvent.click(screen.getByTestId("add-member-btn"));

      await waitFor(() => {
        expect(mockHook.addMember).toHaveBeenCalledWith("team-1", {
          userId: "user-new",
          role: TeamRole.MEMBER,
        });
      });
    });

    it("renders access requests section with pending requests", () => {
      mockHook.accessRequests = [pendingRequest];
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(screen.getByTestId("access-requests-section")).toBeInTheDocument();
      expect(screen.getByTestId("access-request-req-1")).toBeInTheDocument();
      expect(screen.getByTestId("approve-request-req-1")).toBeInTheDocument();
      expect(screen.getByTestId("deny-request-req-1")).toBeInTheDocument();
    });

    it("calls decideAccessRequest with APPROVED when Approve clicked", async () => {
      mockHook.accessRequests = [pendingRequest];
      mockHook.decideAccessRequest.mockResolvedValue(true);
      render(<TeamManagementPage initialTeamId="team-1" />);
      fireEvent.click(screen.getByTestId("approve-request-req-1"));

      await waitFor(() => {
        expect(mockHook.decideAccessRequest).toHaveBeenCalledWith(
          "team-1",
          "req-1",
          AccessRequestStatus.APPROVED,
        );
      });
    });

    it("calls decideAccessRequest with DENIED when Deny clicked", async () => {
      mockHook.accessRequests = [pendingRequest];
      mockHook.decideAccessRequest.mockResolvedValue(true);
      render(<TeamManagementPage initialTeamId="team-1" />);
      fireEvent.click(screen.getByTestId("deny-request-req-1"));

      await waitFor(() => {
        expect(mockHook.decideAccessRequest).toHaveBeenCalledWith(
          "team-1",
          "req-1",
          AccessRequestStatus.DENIED,
        );
      });
    });

    it("shows empty message when no access requests", () => {
      mockHook.accessRequests = [];
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(screen.getByTestId("no-access-requests")).toBeInTheDocument();
    });
  });

  describe("non-member view", () => {
    beforeEach(() => {
      // Team whose owner is someone else
      const otherTeam: Team = { ...mockTeam, ownerUserId: "user-other" };
      mockHook.teams = [otherTeam];
      mockHook.teamDetail = {
        team: otherTeam,
        members: [
          {
            teamId: "team-1",
            userId: "user-other",
            orgId: "org-1",
            role: TeamRole.OWNER,
            joinedAt: "2026-01-01T00:00:00Z",
          },
        ],
      };
      mockHook.fetchTeamDetail.mockResolvedValue(mockHook.teamDetail);
    });

    it("shows Request Access section for non-members", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(screen.getByTestId("request-access-section")).toBeInTheDocument();
      expect(screen.getByTestId("request-access-btn")).toBeInTheDocument();
    });

    it("does not fetch owner-only access requests for non-members", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(mockHook.fetchAccessRequests).not.toHaveBeenCalled();
    });

    it("calls requestAccess when button is clicked", async () => {
      mockHook.requestAccess.mockResolvedValue({
        id: "req-new",
        teamId: "team-1",
        requesterUserId: "user-owner",
        orgId: "org-1",
        status: AccessRequestStatus.PENDING,
        decidedByUserId: null,
        decidedAt: null,
        createdAt: "2026-03-01T00:00:00Z",
      });
      render(<TeamManagementPage initialTeamId="team-1" />);
      fireEvent.click(screen.getByTestId("request-access-btn"));

      await waitFor(() => {
        expect(mockHook.requestAccess).toHaveBeenCalledWith("team-1");
      });
    });

    it("shows confirmation text after request is sent", async () => {
      mockHook.requestAccess.mockResolvedValue({
        id: "req-new",
        teamId: "team-1",
        requesterUserId: "user-owner",
        orgId: "org-1",
        status: AccessRequestStatus.PENDING,
        decidedByUserId: null,
        decidedAt: null,
        createdAt: "2026-03-01T00:00:00Z",
      });
      render(<TeamManagementPage initialTeamId="team-1" />);
      fireEvent.click(screen.getByTestId("request-access-btn"));

      await waitFor(() => {
        expect(screen.getByTestId("access-request-sent")).toBeInTheDocument();
      });
    });

    it("does not show Edit button for non-owners", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(screen.queryByTestId("edit-team-btn")).toBeNull();
    });

    it("does not show access requests section for non-owners", () => {
      render(<TeamManagementPage initialTeamId="team-1" />);
      expect(screen.queryByTestId("access-requests-section")).toBeNull();
    });
  });

  describe("error handling", () => {
    it("shows error banner when hook has an error", () => {
      mockHook.error = "Failed to load team";
      render(<TeamManagementPage />);
      expect(screen.getByText("Failed to load team")).toBeInTheDocument();
    });
  });
});
