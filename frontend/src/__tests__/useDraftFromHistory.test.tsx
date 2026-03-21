import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useDraftFromHistory } from "../hooks/useDraftFromHistory.js";
import type { DraftFromHistoryResponse, SuggestedCommit } from "@weekly-commitments/contracts";

/* ── Mock API client ──────────────────────────────────────────────────────── */

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

/* ── Mock feature flags ───────────────────────────────────────────────────── */

let mockStartMyWeek = true;

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({
    suggestRcdo: true,
    draftReconciliation: false,
    managerInsights: false,
    icTrends: true,
    planQualityNudge: false,
    startMyWeek: mockStartMyWeek,
  }),
}));

/* ── Fixtures ─────────────────────────────────────────────────────────────── */

function makeSuggestedCommit(overrides: Partial<SuggestedCommit> = {}): SuggestedCommit {
  return {
    commitId: "commit-1",
    title: "Deploy API v2",
    source: "CARRIED_FORWARD",
    ...overrides,
  };
}

function makeResponse(overrides: Partial<DraftFromHistoryResponse> = {}): DraftFromHistoryResponse {
  return {
    planId: "plan-abc",
    suggestedCommits: [makeSuggestedCommit()],
    ...overrides,
  };
}

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("useDraftFromHistory", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockStartMyWeek = true;
  });

  it("initialises with idle status, null planId, and empty commits", () => {
    const { result } = renderHook(() => useDraftFromHistory());

    expect(result.current.status).toBe("idle");
    expect(result.current.planId).toBeNull();
    expect(result.current.suggestedCommits).toEqual([]);
    expect(result.current.error).toBeNull();
  });

  it("returns null and does not call the API when startMyWeek flag is disabled", async () => {
    mockStartMyWeek = false;
    const { result } = renderHook(() => useDraftFromHistory());

    let returnValue: DraftFromHistoryResponse | null = undefined as unknown as DraftFromHistoryResponse | null;
    await act(async () => {
      returnValue = await result.current.draftFromHistory("2026-03-16");
    });

    expect(mockClient.POST).not.toHaveBeenCalled();
    expect(returnValue).toBeNull();
    expect(result.current.status).toBe("idle");
  });

  it("calls POST /plans/draft-from-history with the weekStart", async () => {
    mockClient.POST.mockResolvedValue({
      data: makeResponse(),
      response: { status: 200 },
    });

    const { result } = renderHook(() => useDraftFromHistory());

    await act(async () => {
      await result.current.draftFromHistory("2026-03-16");
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/plans/draft-from-history", {
      body: { weekStart: "2026-03-16" },
    });
  });

  it("sets status to ok, planId, and suggestedCommits on success", async () => {
    const response = makeResponse({
      planId: "plan-xyz",
      suggestedCommits: [
        makeSuggestedCommit({ commitId: "c1", source: "CARRIED_FORWARD" }),
        makeSuggestedCommit({ commitId: "c2", source: "RECURRING" }),
      ],
    });
    mockClient.POST.mockResolvedValue({
      data: response,
      response: { status: 200 },
    });

    const { result } = renderHook(() => useDraftFromHistory());

    let returnValue: DraftFromHistoryResponse | null = null;
    await act(async () => {
      returnValue = await result.current.draftFromHistory("2026-03-16");
    });

    expect(result.current.status).toBe("ok");
    expect(result.current.planId).toBe("plan-xyz");
    expect(result.current.suggestedCommits).toHaveLength(2);
    expect(returnValue).toEqual(response);
  });

  it("returns the full DraftFromHistoryResponse on success", async () => {
    const response = makeResponse();
    mockClient.POST.mockResolvedValue({
      data: response,
      response: { status: 200 },
    });

    const { result } = renderHook(() => useDraftFromHistory());

    let returnValue: DraftFromHistoryResponse | null = null;
    await act(async () => {
      returnValue = await result.current.draftFromHistory("2026-03-16");
    });

    expect(returnValue).toEqual(response);
  });

  it("sets status to conflict on 409 response", async () => {
    mockClient.POST.mockResolvedValue({
      data: undefined,
      error: undefined,
      response: { status: 409 },
    });

    const { result } = renderHook(() => useDraftFromHistory());

    let returnValue: DraftFromHistoryResponse | null = undefined as unknown as DraftFromHistoryResponse | null;
    await act(async () => {
      returnValue = await result.current.draftFromHistory("2026-03-16");
    });

    expect(result.current.status).toBe("conflict");
    expect(result.current.error).toBeTruthy();
    expect(returnValue).toBeNull();
  });

  it("sets status to error with the API error message on non-409 failure", async () => {
    mockClient.POST.mockResolvedValue({
      data: undefined,
      error: { error: { message: "Server error occurred" } },
      response: { status: 500 },
    });

    const { result } = renderHook(() => useDraftFromHistory());

    await act(async () => {
      await result.current.draftFromHistory("2026-03-16");
    });

    expect(result.current.status).toBe("error");
    expect(result.current.error).toBe("Server error occurred");
    expect(result.current.planId).toBeNull();
  });

  it("sets status to error with a fallback message when no error body is present", async () => {
    mockClient.POST.mockResolvedValue({
      data: undefined,
      error: undefined,
      response: { status: 422 },
    });

    const { result } = renderHook(() => useDraftFromHistory());

    await act(async () => {
      await result.current.draftFromHistory("2026-03-16");
    });

    expect(result.current.status).toBe("error");
    expect(result.current.error).toContain("422");
  });

  it("sets status to error on network exception", async () => {
    mockClient.POST.mockRejectedValue(new Error("Network failure"));

    const { result } = renderHook(() => useDraftFromHistory());

    await act(async () => {
      await result.current.draftFromHistory("2026-03-16");
    });

    expect(result.current.status).toBe("error");
    expect(result.current.error).toBe("Network failure");
  });

  it("sets status to loading while the request is in-flight", async () => {
    let resolvePost!: (value: unknown) => void;
    mockClient.POST.mockReturnValue(
      new Promise((res) => {
        resolvePost = res;
      }),
    );

    const { result } = renderHook(() => useDraftFromHistory());

    let draftPromise: Promise<DraftFromHistoryResponse | null>;
    act(() => {
      draftPromise = result.current.draftFromHistory("2026-03-16");
    });

    expect(result.current.status).toBe("loading");

    await act(async () => {
      resolvePost({ data: makeResponse(), response: { status: 200 } });
      await draftPromise;
    });

    expect(result.current.status).toBe("ok");
  });

  it("reset() restores idle state", async () => {
    mockClient.POST.mockResolvedValue({
      data: makeResponse(),
      response: { status: 200 },
    });

    const { result } = renderHook(() => useDraftFromHistory());

    await act(async () => {
      await result.current.draftFromHistory("2026-03-16");
    });

    expect(result.current.status).toBe("ok");
    expect(result.current.planId).toBeTruthy();

    act(() => {
      result.current.reset();
    });

    expect(result.current.status).toBe("idle");
    expect(result.current.planId).toBeNull();
    expect(result.current.suggestedCommits).toEqual([]);
    expect(result.current.error).toBeNull();
  });

  it("clears previous results when a new draftFromHistory call starts", async () => {
    // First call succeeds
    mockClient.POST.mockResolvedValueOnce({
      data: makeResponse({ planId: "plan-first" }),
      response: { status: 200 },
    });

    const { result } = renderHook(() => useDraftFromHistory());

    await act(async () => {
      await result.current.draftFromHistory("2026-03-09");
    });

    expect(result.current.planId).toBe("plan-first");

    // Second call in-flight: state should reset
    let resolveSecond!: (value: unknown) => void;
    mockClient.POST.mockReturnValue(
      new Promise((res) => {
        resolveSecond = res;
      }),
    );

    let secondPromise: Promise<DraftFromHistoryResponse | null>;
    act(() => {
      secondPromise = result.current.draftFromHistory("2026-03-16");
    });

    // State reset at start of second call
    expect(result.current.planId).toBeNull();
    expect(result.current.suggestedCommits).toEqual([]);
    expect(result.current.status).toBe("loading");

    await act(async () => {
      resolveSecond({ data: makeResponse({ planId: "plan-second" }), response: { status: 200 } });
      await secondPromise;
    });

    expect(result.current.planId).toBe("plan-second");
  });
});
