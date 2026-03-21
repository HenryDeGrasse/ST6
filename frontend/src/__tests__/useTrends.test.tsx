import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useTrends } from "../hooks/useTrends.js";
import type { TrendsResponse } from "@weekly-commitments/contracts";

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

let mockIcTrends = true;

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({
    suggestRcdo: true,
    draftReconciliation: false,
    managerInsights: false,
    icTrends: mockIcTrends,
  }),
}));

/* ── Fixture ──────────────────────────────────────────────────────────────── */

function makeTrends(overrides: Partial<TrendsResponse> = {}): TrendsResponse {
  return {
    weeksAnalyzed: 4,
    windowStart: "2026-02-16",
    windowEnd: "2026-03-09",
    strategicAlignmentRate: 0.75,
    teamStrategicAlignmentRate: 0.6,
    avgCarryForwardPerWeek: 1.5,
    carryForwardStreak: 2,
    avgConfidence: 0.8,
    completionAccuracy: 0.7,
    confidenceAccuracyGap: 0.1,
    priorityDistribution: { KING: 0.3, QUEEN: 0.4, ROOK: 0.3 },
    categoryDistribution: { DELIVERY: 0.8, OPERATIONS: 0.2 },
    weekPoints: [],
    insights: [],
    ...overrides,
  };
}

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("useTrends", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIcTrends = true;
  });

  it("initialises with null trends, not loading, no error", () => {
    const { result } = renderHook(() => useTrends());

    expect(result.current.trends).toBeNull();
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("does not call the API when icTrends flag is disabled", async () => {
    mockIcTrends = false;
    const { result } = renderHook(() => useTrends());

    await act(async () => {
      await result.current.fetchTrends();
    });

    expect(mockClient.GET).not.toHaveBeenCalled();
    expect(result.current.trends).toBeNull();
  });

  it("sets trends data on a successful response", async () => {
    const data = makeTrends();
    mockClient.GET.mockResolvedValue({
      data,
      response: { status: 200 },
    });

    const { result } = renderHook(() => useTrends());

    await act(async () => {
      await result.current.fetchTrends();
    });

    expect(result.current.trends).toEqual(data);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("calls GET /users/me/trends without query param when weeks is omitted", async () => {
    mockClient.GET.mockResolvedValue({ data: makeTrends(), response: { status: 200 } });

    const { result } = renderHook(() => useTrends());

    await act(async () => {
      await result.current.fetchTrends();
    });

    expect(mockClient.GET).toHaveBeenCalledWith("/users/me/trends", {
      params: { query: {} },
    });
  });

  it("passes weeks query param when specified", async () => {
    mockClient.GET.mockResolvedValue({ data: makeTrends(), response: { status: 200 } });

    const { result } = renderHook(() => useTrends());

    await act(async () => {
      await result.current.fetchTrends(12);
    });

    expect(mockClient.GET).toHaveBeenCalledWith("/users/me/trends", {
      params: { query: { weeks: 12 } },
    });
  });

  it("sets an error message when the response has no data", async () => {
    mockClient.GET.mockResolvedValue({
      data: undefined,
      error: { error: { message: "Validation failed" } },
      response: { status: 422 },
    });

    const { result } = renderHook(() => useTrends());

    await act(async () => {
      await result.current.fetchTrends();
    });

    expect(result.current.trends).toBeNull();
    expect(result.current.error).toBe("Validation failed");
  });

  it("falls back to status-code message when error body has no message", async () => {
    mockClient.GET.mockResolvedValue({
      data: undefined,
      error: {},
      response: { status: 503 },
    });

    const { result } = renderHook(() => useTrends());

    await act(async () => {
      await result.current.fetchTrends();
    });

    expect(result.current.error).toBe("Request failed (503)");
  });

  it("captures network exceptions as error string", async () => {
    mockClient.GET.mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() => useTrends());

    await act(async () => {
      await result.current.fetchTrends();
    });

    expect(result.current.error).toBe("Network error");
    expect(result.current.loading).toBe(false);
  });

  it("clears the error via clearError without re-fetching", async () => {
    mockClient.GET.mockRejectedValue(new Error("fail"));

    const { result } = renderHook(() => useTrends());

    await act(async () => {
      await result.current.fetchTrends();
    });

    expect(result.current.error).not.toBeNull();

    act(() => {
      result.current.clearError();
    });

    expect(result.current.error).toBeNull();
    // Only one GET call (the initial fetch, not a re-fetch)
    expect(mockClient.GET).toHaveBeenCalledTimes(1);
  });

  it("returns to loading=false after the request completes", async () => {
    mockClient.GET.mockResolvedValue({ data: makeTrends(), response: { status: 200 } });

    const { result } = renderHook(() => useTrends());

    await act(async () => {
      await result.current.fetchTrends();
    });

    expect(result.current.loading).toBe(false);
    expect(result.current.trends).not.toBeNull();
  });
});
