import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useIssues } from "../hooks/useIssues.js";
import type { Issue } from "@weekly-commitments/contracts";
import { IssueStatus, EffortType } from "@weekly-commitments/contracts";

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

const mockIssue: Issue = {
  id: "issue-1",
  orgId: "org-1",
  teamId: "team-1",
  issueKey: "ENG-1",
  sequenceNumber: 1,
  title: "Fix the login bug",
  description: null,
  effortType: EffortType.BUILD,
  estimatedHours: 4,
  chessPriority: null,
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
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  archivedAt: null,
};

describe("useIssues", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("starts with empty issues and no error", () => {
    const { result } = renderHook(() => useIssues());
    expect(result.current.issues).toEqual([]);
    expect(result.current.error).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it("fetches issues for a team", async () => {
    mockClient.GET.mockResolvedValue({
      data: {
        content: [mockIssue],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useIssues());

    await act(async () => {
      await result.current.fetchIssues("team-1");
    });

    expect(mockClient.GET).toHaveBeenCalledWith("/teams/{teamId}/issues", {
      params: expect.objectContaining({
        path: { teamId: "team-1" },
      }),
    });
    expect(result.current.issues).toHaveLength(1);
    expect(result.current.totalElements).toBe(1);
  });

  it("passes filters and sort to fetchIssues", async () => {
    mockClient.GET.mockResolvedValue({
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useIssues());

    await act(async () => {
      await result.current.fetchIssues(
        "team-1",
        0,
        20,
        { status: IssueStatus.OPEN, effortType: EffortType.BUILD },
        "ai_rank",
      );
    });

    expect(mockClient.GET).toHaveBeenCalledWith("/teams/{teamId}/issues", {
      params: expect.objectContaining({
        query: expect.objectContaining({
          status: "OPEN",
          effortType: "BUILD",
          sort: "ai_rank",
        }),
      }),
    });
  });

  it("creates an issue and prepends it to the list", async () => {
    mockClient.POST.mockResolvedValue({
      data: mockIssue,
      response: { status: 201 },
    });

    const { result } = renderHook(() => useIssues());

    await act(async () => {
      const created = await result.current.createIssue("team-1", {
        title: "Fix the login bug",
        effortType: EffortType.BUILD,
        estimatedHours: 4,
      });
      expect(created).toEqual(mockIssue);
    });

    expect(result.current.issues).toHaveLength(1);
    expect(result.current.issues[0].issueKey).toBe("ENG-1");
  });

  it("updates an issue in place", async () => {
    const updated = { ...mockIssue, title: "Fix the login bug (updated)" };
    mockClient.PATCH.mockResolvedValue({
      data: updated,
      response: { status: 200 },
    });

    const { result } = renderHook(() => useIssues());

    // Pre-populate issues list via mock
    await act(async () => {
      mockClient.GET.mockResolvedValue({
        data: { content: [mockIssue], page: 0, size: 20, totalElements: 1, totalPages: 1 },
        response: { status: 200 },
      });
      await result.current.fetchIssues("team-1");
    });

    await act(async () => {
      const r = await result.current.updateIssue("issue-1", {
        title: "Fix the login bug (updated)",
      });
      expect(r?.title).toBe("Fix the login bug (updated)");
    });

    expect(result.current.issues[0].title).toBe("Fix the login bug (updated)");
  });

  it("sets error when fetchIssues fails", async () => {
    mockClient.GET.mockResolvedValue({
      data: undefined,
      error: { error: { message: "Team not found" } },
      response: { status: 404 },
    });

    const { result } = renderHook(() => useIssues());

    await act(async () => {
      await result.current.fetchIssues("bad-team");
    });

    expect(result.current.error).toBe("Team not found");
  });

  it("clears error when clearError is called", async () => {
    mockClient.GET.mockResolvedValue({
      data: undefined,
      error: { error: { message: "Oops" } },
      response: { status: 500 },
    });

    const { result } = renderHook(() => useIssues());

    await act(async () => {
      await result.current.fetchIssues("team-1");
    });
    expect(result.current.error).toBeTruthy();

    act(() => {
      result.current.clearError();
    });
    expect(result.current.error).toBeNull();
  });

  it("deletes an issue via the DELETE endpoint", async () => {
    const { result } = renderHook(() => useIssues());

    await act(async () => {
      mockClient.GET.mockResolvedValue({
        data: { content: [mockIssue], page: 0, size: 20, totalElements: 1, totalPages: 1 },
        response: { status: 200 },
      });
      await result.current.fetchIssues("team-1");
    });

    mockClient.DELETE.mockResolvedValue({
      data: null,
      response: { status: 204, ok: true },
    });

    await act(async () => {
      const deleted = await result.current.deleteIssue("issue-1");
      expect(deleted).toBe(true);
    });

    expect(mockClient.DELETE).toHaveBeenCalledWith("/issues/{issueId}", {
      params: { path: { issueId: "issue-1" } },
    });
    expect(result.current.issues).toHaveLength(0);
    expect(result.current.totalElements).toBe(0);
  });

  it("posts a comment and returns activity", async () => {
    const activityResponse = {
      id: "act-1",
      orgId: "org-1",
      issueId: "issue-1",
      actorUserId: "user-1",
      activityType: "COMMENT",
      oldValue: null,
      newValue: null,
      commentText: "Hello world",
      hoursLogged: null,
      metadata: null,
      createdAt: "2026-01-01T00:00:00Z",
    };
    mockClient.POST.mockResolvedValue({
      data: activityResponse,
      response: { status: 201 },
    });

    const { result } = renderHook(() => useIssues());

    await act(async () => {
      const activity = await result.current.addComment("issue-1", { commentText: "Hello world" });
      expect(activity).toEqual(activityResponse);
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/issues/{issueId}/comment", {
      params: { path: { issueId: "issue-1" } },
      body: { commentText: "Hello world" },
    });
  });
});
