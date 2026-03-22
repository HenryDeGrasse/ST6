import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useTeams } from "../hooks/useTeams.js";
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

const mockTeam: Team = {
  id: "team-1",
  orgId: "org-1",
  name: "Engineering",
  keyPrefix: "ENG",
  description: null,
  ownerUserId: "user-1",
  issueSequence: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("useTeams", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("starts with empty teams and no error", () => {
    const { result } = renderHook(() => useTeams());
    expect(result.current.teams).toEqual([]);
    expect(result.current.error).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it("fetches and populates teams", async () => {
    mockClient.GET.mockResolvedValue({
      data: { teams: [mockTeam] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useTeams());

    await act(async () => {
      await result.current.fetchTeams();
    });

    expect(mockClient.GET).toHaveBeenCalledWith("/teams");
    expect(result.current.teams).toHaveLength(1);
    expect(result.current.teams[0].name).toBe("Engineering");
  });

  it("sets error when fetchTeams fails", async () => {
    mockClient.GET.mockResolvedValue({
      data: undefined,
      error: { error: { message: "Unauthorized" } },
      response: { status: 401 },
    });

    const { result } = renderHook(() => useTeams());

    await act(async () => {
      await result.current.fetchTeams();
    });

    expect(result.current.error).toBe("Unauthorized");
  });

  it("creates a team and adds it to the list", async () => {
    mockClient.POST.mockResolvedValue({
      data: mockTeam,
      response: { status: 201 },
    });

    const { result } = renderHook(() => useTeams());

    await act(async () => {
      const created = await result.current.createTeam({
        name: "Engineering",
        keyPrefix: "ENG",
      });
      expect(created).toEqual(mockTeam);
    });

    expect(result.current.teams).toHaveLength(1);
  });

  it("updates a team in place", async () => {
    const updated = { ...mockTeam, name: "Platform Engineering" };
    mockClient.PATCH.mockResolvedValue({
      data: updated,
      response: { status: 200 },
    });

    const { result } = renderHook(() => useTeams());

    // Pre-populate
    await act(async () => {
      result.current.teams.push(mockTeam);
    });

    await act(async () => {
      const r = await result.current.updateTeam("team-1", { name: "Platform Engineering" });
      expect(r?.name).toBe("Platform Engineering");
    });
  });

  it("clears error when clearError is called", async () => {
    mockClient.GET.mockResolvedValue({
      data: undefined,
      error: { error: { message: "Oops" } },
      response: { status: 500 },
    });

    const { result } = renderHook(() => useTeams());

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
