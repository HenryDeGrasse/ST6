import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useNextWorkSuggestions } from "../hooks/useNextWorkSuggestions.js";
import { ChessPriority } from "@weekly-commitments/contracts";
import type { NextWorkSuggestion } from "@weekly-commitments/contracts";

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

let mockSuggestNextWork = true;

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({
    suggestRcdo: true,
    draftReconciliation: false,
    managerInsights: false,
    icTrends: true,
    planQualityNudge: false,
    startMyWeek: false,
    suggestNextWork: mockSuggestNextWork,
  }),
}));

/* ── Fixtures ─────────────────────────────────────────────────────────────── */

function makeSuggestion(overrides: Partial<NextWorkSuggestion> = {}): NextWorkSuggestion {
  return {
    suggestionId: "sugg-1",
    title: "Complete Q2 planning document",
    suggestedOutcomeId: "outcome-1",
    suggestedChessPriority: ChessPriority.QUEEN,
    confidence: 0.85,
    source: "CARRY_FORWARD",
    sourceDetail: "Not completed in week of 2026-03-09",
    rationale: "This item was not done last week.",
    ...overrides,
  };
}

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("useNextWorkSuggestions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSuggestNextWork = true;
  });

  it("initialises with empty suggestions, idle status", () => {
    const { result } = renderHook(() => useNextWorkSuggestions());

    expect(result.current.suggestions).toEqual([]);
    expect(result.current.status).toBe("idle");
  });

  it("does not call the API when suggestNextWork flag is disabled", async () => {
    mockSuggestNextWork = false;
    const { result } = renderHook(() => useNextWorkSuggestions());

    await act(async () => {
      await result.current.fetchSuggestions();
    });

    expect(mockClient.POST).not.toHaveBeenCalled();
    expect(result.current.suggestions).toEqual([]);
    expect(result.current.status).toBe("idle");
  });

  it("sets status to ok and populates suggestions on success", async () => {
    const suggestions = [makeSuggestion()];
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", suggestions },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useNextWorkSuggestions());

    await act(async () => {
      await result.current.fetchSuggestions();
    });

    expect(result.current.status).toBe("ok");
    expect(result.current.suggestions).toEqual(suggestions);
  });

  it("calls POST /ai/suggest-next-work with empty body when no weekStart", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", suggestions: [] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useNextWorkSuggestions());

    await act(async () => {
      await result.current.fetchSuggestions();
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/ai/suggest-next-work", {
      body: {},
    });
  });

  it("calls POST /ai/suggest-next-work with weekStart when provided", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", suggestions: [] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useNextWorkSuggestions());

    await act(async () => {
      await result.current.fetchSuggestions("2026-03-16");
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/ai/suggest-next-work", {
      body: { weekStart: "2026-03-16" },
    });
  });

  it("sets status to unavailable when backend returns unavailable", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "unavailable", suggestions: [] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useNextWorkSuggestions());

    await act(async () => {
      await result.current.fetchSuggestions();
    });

    expect(result.current.status).toBe("unavailable");
    expect(result.current.suggestions).toEqual([]);
  });

  it("sets status to rate_limited on 429 response", async () => {
    mockClient.POST.mockResolvedValue({
      data: undefined,
      response: { status: 429 },
    });

    const { result } = renderHook(() => useNextWorkSuggestions());

    await act(async () => {
      await result.current.fetchSuggestions();
    });

    expect(result.current.status).toBe("rate_limited");
    expect(result.current.suggestions).toEqual([]);
  });

  it("sets status to unavailable when response has no data", async () => {
    mockClient.POST.mockResolvedValue({
      data: undefined,
      response: { status: 500 },
    });

    const { result } = renderHook(() => useNextWorkSuggestions());

    await act(async () => {
      await result.current.fetchSuggestions();
    });

    expect(result.current.status).toBe("unavailable");
    expect(result.current.suggestions).toEqual([]);
  });

  it("sets status to unavailable on network exception", async () => {
    mockClient.POST.mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() => useNextWorkSuggestions());

    await act(async () => {
      await result.current.fetchSuggestions();
    });

    expect(result.current.status).toBe("unavailable");
    expect(result.current.suggestions).toEqual([]);
  });

  it("sets status to loading while the request is in-flight", async () => {
    let resolvePost!: (value: unknown) => void;
    mockClient.POST.mockReturnValue(
      new Promise((res) => {
        resolvePost = res;
      }),
    );

    const { result } = renderHook(() => useNextWorkSuggestions());

    let fetchPromise: Promise<void>;
    act(() => {
      fetchPromise = result.current.fetchSuggestions();
    });

    expect(result.current.status).toBe("loading");

    await act(async () => {
      resolvePost({ data: { status: "ok", suggestions: [] }, response: { status: 200 } });
      await fetchPromise;
    });

    expect(result.current.status).toBe("ok");
  });

  it("clearSuggestions resets suggestions and status to idle", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", suggestions: [makeSuggestion()] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useNextWorkSuggestions());

    await act(async () => {
      await result.current.fetchSuggestions();
    });

    expect(result.current.suggestions).toHaveLength(1);

    act(() => {
      result.current.clearSuggestions();
    });

    expect(result.current.suggestions).toEqual([]);
    expect(result.current.status).toBe("idle");
  });

  it("dismissSuggestion removes the suggestion from the list", async () => {
    const s1 = makeSuggestion({ suggestionId: "sugg-1" });
    const s2 = makeSuggestion({ suggestionId: "sugg-2", title: "Another item" });
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", suggestions: [s1, s2] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useNextWorkSuggestions());

    await act(async () => {
      await result.current.fetchSuggestions();
    });

    expect(result.current.suggestions).toHaveLength(2);

    act(() => {
      result.current.dismissSuggestion("sugg-1");
    });

    expect(result.current.suggestions).toHaveLength(1);
    expect(result.current.suggestions[0].suggestionId).toBe("sugg-2");
  });

  it("submitFeedback calls POST /ai/suggestion-feedback", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "ok" },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useNextWorkSuggestions());

    let feedbackResult: boolean = false;
    await act(async () => {
      feedbackResult = await result.current.submitFeedback({
        suggestionId: "sugg-1",
        action: "ACCEPT",
        sourceType: "CARRY_FORWARD",
        sourceDetail: "Not done last week",
      });
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/ai/suggestion-feedback", {
      body: {
        suggestionId: "sugg-1",
        action: "ACCEPT",
        sourceType: "CARRY_FORWARD",
        sourceDetail: "Not done last week",
      },
    });
    expect(feedbackResult).toBe(true);
  });

  it("submitFeedback returns false when flag is disabled", async () => {
    mockSuggestNextWork = false;
    const { result } = renderHook(() => useNextWorkSuggestions());

    let feedbackResult: boolean = true;
    await act(async () => {
      feedbackResult = await result.current.submitFeedback({
        suggestionId: "sugg-1",
        action: "DECLINE",
      });
    });

    expect(mockClient.POST).not.toHaveBeenCalled();
    expect(feedbackResult).toBe(false);
  });

  it("submitFeedback returns false on network error", async () => {
    mockClient.POST.mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() => useNextWorkSuggestions());

    let feedbackResult: boolean = true;
    await act(async () => {
      feedbackResult = await result.current.submitFeedback({
        suggestionId: "sugg-1",
        action: "DEFER",
      });
    });

    expect(feedbackResult).toBe(false);
  });

  it("submitFeedback returns false when response status is unavailable", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "unavailable" },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useNextWorkSuggestions());

    let feedbackResult: boolean = true;
    await act(async () => {
      feedbackResult = await result.current.submitFeedback({
        suggestionId: "sugg-1",
        action: "ACCEPT",
      });
    });

    expect(feedbackResult).toBe(false);
  });

  it("ignores stale responses after clearSuggestions is called", async () => {
    let resolvePost!: (value: unknown) => void;
    mockClient.POST.mockReturnValue(
      new Promise((res) => {
        resolvePost = res;
      }),
    );

    const { result } = renderHook(() => useNextWorkSuggestions());

    let fetchPromise: Promise<void>;
    act(() => {
      fetchPromise = result.current.fetchSuggestions();
    });

    expect(result.current.status).toBe("loading");

    act(() => {
      result.current.clearSuggestions();
    });

    expect(result.current.status).toBe("idle");

    await act(async () => {
      resolvePost({ data: { status: "ok", suggestions: [makeSuggestion()] }, response: { status: 200 } });
      await fetchPromise;
    });

    // Stale response should be ignored
    expect(result.current.status).toBe("idle");
    expect(result.current.suggestions).toEqual([]);
  });
});
