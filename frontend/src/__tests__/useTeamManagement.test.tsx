import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useTeamManagement } from "../hooks/useTeamManagement.js";
import type { Team, TeamMember, TeamAccessRequest } from "@weekly-commitments/contracts";
import { AccessRequestStatus, TeamRole } from "@weekly-commitments/contracts";

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

const mockTeam: Team = {
  id: "team-1",
  orgId: "org-1",
  name: "Engineering",
  keyPrefix: "ENG",
  description: "Build great things",
  ownerUserId: "user-owner",
  issueSequence: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const mockMember: TeamMember = {
  teamId: "team-1",
  userId: "user-2",
  orgId: "org-1",
  role: TeamRole.MEMBER,
  joinedAt: "2026-01-15T00:00:00Z",
};

const mockRequest: TeamAccessRequest = {
  id: "req-1",
  teamId: "team-1",
  requesterUserId: "user-3",
  orgId: "org-1",
  status: AccessRequestStatus.PENDING,
  decidedByUserId: null,
  decidedAt: null,
  createdAt: "2026-02-01T00:00:00Z",
};

describe("useTeamManagement", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("starts with empty state", () => {
    const { result } = renderHook(() => useTeamManagement());
    expect(result.current.teams).toEqual([]);
    expect(result.current.teamDetail).toBeNull();
    expect(result.current.accessRequests).toEqual([]);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  describe("fetchTeams", () => {
    it("populates teams on success", async () => {
      mockClient.GET.mockResolvedValue({
        data: { teams: [mockTeam] },
        response: { status: 200 },
      });

      const { result } = renderHook(() => useTeamManagement());

      await act(async () => {
        await result.current.fetchTeams();
      });

      expect(mockClient.GET).toHaveBeenCalledWith("/teams");
      expect(result.current.teams).toHaveLength(1);
      expect(result.current.teams[0].name).toBe("Engineering");
    });

    it("sets error on failure", async () => {
      mockClient.GET.mockResolvedValue({
        data: undefined,
        error: { error: { message: "Unauthorized" } },
        response: { status: 401 },
      });

      const { result } = renderHook(() => useTeamManagement());

      await act(async () => {
        await result.current.fetchTeams();
      });

      expect(result.current.error).toBe("Unauthorized");
    });
  });

  describe("fetchTeamDetail", () => {
    it("stores detail on success", async () => {
      mockClient.GET.mockResolvedValue({
        data: { team: mockTeam, members: [mockMember] },
        response: { status: 200 },
      });

      const { result } = renderHook(() => useTeamManagement());

      await act(async () => {
        await result.current.fetchTeamDetail("team-1");
      });

      expect(mockClient.GET).toHaveBeenCalledWith("/teams/{teamId}", {
        params: { path: { teamId: "team-1" } },
      });
      expect(result.current.teamDetail?.team.name).toBe("Engineering");
      expect(result.current.teamDetail?.members).toHaveLength(1);
    });
  });

  describe("createTeam", () => {
    it("adds created team to list", async () => {
      mockClient.POST.mockResolvedValue({
        data: mockTeam,
        response: { status: 201 },
      });

      const { result } = renderHook(() => useTeamManagement());

      let created: Team | null = null;
      await act(async () => {
        created = await result.current.createTeam({
          name: "Engineering",
          keyPrefix: "ENG",
          description: "Build great things",
        });
      });

      expect(created).toEqual(mockTeam);
      expect(result.current.teams).toContain(mockTeam);
    });
  });

  describe("updateTeam", () => {
    it("updates team in list and detail", async () => {
      const updated = { ...mockTeam, name: "Platform Engineering" };
      // fetchTeams returns teams list
      mockClient.GET.mockResolvedValue({
        data: { teams: [mockTeam] },
        response: { status: 200 },
      });
      mockClient.PATCH.mockResolvedValue({
        data: updated,
        response: { status: 200 },
      });

      const { result } = renderHook(() => useTeamManagement());

      // Seed teams
      await act(async () => {
        await result.current.fetchTeams();
      });

      expect(result.current.teams).toHaveLength(1);

      await act(async () => {
        const r = await result.current.updateTeam("team-1", { name: "Platform Engineering" });
        expect(r?.name).toBe("Platform Engineering");
      });

      expect(result.current.teams[0].name).toBe("Platform Engineering");
    });
  });

  describe("addMember", () => {
    it("appends member to teamDetail", async () => {
      mockClient.GET.mockResolvedValue({
        data: { team: mockTeam, members: [] },
        response: { status: 200 },
      });
      mockClient.POST.mockResolvedValue({
        data: mockMember,
        response: { status: 201 },
      });

      const { result } = renderHook(() => useTeamManagement());

      await act(async () => {
        await result.current.fetchTeamDetail("team-1");
      });

      await act(async () => {
        await result.current.addMember("team-1", {
          userId: "user-2",
          role: TeamRole.MEMBER,
        });
      });

      expect(result.current.teamDetail?.members).toHaveLength(1);
      expect(result.current.teamDetail?.members[0].userId).toBe("user-2");
    });
  });

  describe("removeMember", () => {
    it("removes member from teamDetail on success", async () => {
      mockClient.GET.mockResolvedValue({
        data: { team: mockTeam, members: [mockMember] },
        response: { status: 200 },
      });
      mockClient.DELETE.mockResolvedValue({
        response: { ok: true, status: 204 },
      });

      const { result } = renderHook(() => useTeamManagement());

      await act(async () => {
        await result.current.fetchTeamDetail("team-1");
      });

      expect(result.current.teamDetail?.members).toHaveLength(1);

      await act(async () => {
        const ok = await result.current.removeMember("team-1", "user-2");
        expect(ok).toBe(true);
      });

      expect(result.current.teamDetail?.members).toHaveLength(0);
    });
  });

  describe("fetchAccessRequests", () => {
    it("populates access requests", async () => {
      mockClient.GET.mockResolvedValue({
        data: { requests: [mockRequest] },
        response: { status: 200 },
      });

      const { result } = renderHook(() => useTeamManagement());

      await act(async () => {
        await result.current.fetchAccessRequests("team-1");
      });

      expect(mockClient.GET).toHaveBeenCalledWith("/teams/{teamId}/access-requests", {
        params: { path: { teamId: "team-1" } },
      });
      expect(result.current.accessRequests).toHaveLength(1);
      expect(result.current.accessRequests[0].status).toBe(AccessRequestStatus.PENDING);
    });
  });

  describe("requestAccess", () => {
    it("returns the created access request", async () => {
      mockClient.POST.mockResolvedValue({
        data: mockRequest,
        response: { status: 201 },
      });

      const { result } = renderHook(() => useTeamManagement());

      let req: TeamAccessRequest | null = null;
      await act(async () => {
        req = await result.current.requestAccess("team-1");
      });

      expect(req).toEqual(mockRequest);
    });
  });

  describe("decideAccessRequest", () => {
    it("updates request status to APPROVED in state and adds the member locally", async () => {
      mockClient.GET
        .mockResolvedValueOnce({
          data: { team: mockTeam, members: [] },
          response: { status: 200 },
        })
        .mockResolvedValueOnce({
          data: { requests: [mockRequest] },
          response: { status: 200 },
        });
      mockClient.PATCH.mockResolvedValue({
        response: { ok: true, status: 200 },
      });

      const { result } = renderHook(() => useTeamManagement());

      await act(async () => {
        await result.current.fetchTeamDetail("team-1");
        await result.current.fetchAccessRequests("team-1");
      });

      await act(async () => {
        const ok = await result.current.decideAccessRequest(
          "team-1",
          "req-1",
          AccessRequestStatus.APPROVED,
        );
        expect(ok).toBe(true);
      });

      expect(result.current.accessRequests[0].status).toBe(AccessRequestStatus.APPROVED);
      expect(result.current.teamDetail?.members).toHaveLength(1);
      expect(result.current.teamDetail?.members[0].userId).toBe("user-3");
    });

    it("updates request status to DENIED in state", async () => {
      mockClient.GET.mockResolvedValue({
        data: { requests: [mockRequest] },
        response: { status: 200 },
      });
      mockClient.PATCH.mockResolvedValue({
        response: { ok: true, status: 200 },
      });

      const { result } = renderHook(() => useTeamManagement());

      await act(async () => {
        await result.current.fetchAccessRequests("team-1");
      });

      await act(async () => {
        await result.current.decideAccessRequest(
          "team-1",
          "req-1",
          AccessRequestStatus.DENIED,
        );
      });

      expect(result.current.accessRequests[0].status).toBe(AccessRequestStatus.DENIED);
    });
  });

  describe("clearError", () => {
    it("clears error state", async () => {
      mockClient.GET.mockResolvedValue({
        data: undefined,
        error: { error: { message: "Oops" } },
        response: { status: 500 },
      });

      const { result } = renderHook(() => useTeamManagement());

      await act(async () => {
        await result.current.fetchTeams();
      });

      expect(result.current.error).toBeTruthy();

      act(() => {
        result.current.clearError();
      });

      expect(result.current.error).toBeNull();
    });
  });
});
