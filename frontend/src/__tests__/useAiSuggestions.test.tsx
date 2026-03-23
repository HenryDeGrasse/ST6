import { act, renderHook } from "@testing-library/react";
import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { useAiSuggestions } from "../hooks/useAiSuggestions.js";
import type { RcdoSuggestion } from "@weekly-commitments/contracts";

const mockClient = {
  GET: vi.fn(),
  POST: vi.fn(),
  PATCH: vi.fn(),
  DELETE: vi.fn(),
  use: vi.fn(),
};

let mockSuggestRcdo = true;
let mockDraftReconciliation = false;

vi.mock("../api/ApiContext.js", () => ({
  useApiClient: () => mockClient,
}));

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({
    suggestRcdo: mockSuggestRcdo,
    draftReconciliation: mockDraftReconciliation,
  }),
}));

function makeSuggestion(overrides: Partial<RcdoSuggestion> = {}): RcdoSuggestion {
  return {
    outcomeId: "outcome-1",
    rallyCryName: "Scale Revenue",
    objectiveName: "Improve Conversion",
    outcomeName: "Increase trial-to-paid",
    rationale: "Best strategic fit",
    confidence: 0.91,
    ...overrides,
  };
}

describe("useAiSuggestions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    mockSuggestRcdo = true;
    mockDraftReconciliation = false;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("cancels a pending debounce when the title becomes too short", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", suggestions: [makeSuggestion()] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useAiSuggestions());

    act(() => {
      void result.current.fetchSuggestions("Long enough title");
    });

    act(() => {
      void result.current.fetchSuggestions("abc");
    });

    await act(async () => {
      vi.advanceTimersByTime(600);
      await Promise.resolve();
    });

    expect(mockClient.POST).not.toHaveBeenCalled();
    expect(result.current.suggestions).toEqual([]);
    expect(result.current.suggestStatus).toBe("idle");
  });

  it("aborts a superseded in-flight request and keeps the newer response", async () => {
    let firstResolve!: (value: unknown) => void;
    let secondResolve!: (value: unknown) => void;

    mockClient.POST
      .mockImplementationOnce((_path: string, init?: { signal?: AbortSignal }) => new Promise((resolve, reject) => {
        firstResolve = resolve;
        init?.signal?.addEventListener("abort", () => {
          const err = Object.assign(new Error("Aborted"), { name: "AbortError" });
          reject(err);
        });
      }))
      .mockImplementationOnce((_path: string, _init?: { signal?: AbortSignal }) => new Promise((resolve) => {
        secondResolve = resolve;
      }));

    const { result } = renderHook(() => useAiSuggestions());

    act(() => {
      void result.current.fetchSuggestions("First strategic title");
    });

    await act(async () => {
      vi.advanceTimersByTime(500);
      await Promise.resolve();
    });

    expect(result.current.suggestStatus).toBe("loading");

    act(() => {
      void result.current.fetchSuggestions("Second strategic title");
    });

    await act(async () => {
      vi.advanceTimersByTime(500);
      await Promise.resolve();
    });

    await act(async () => {
      firstResolve({
        data: { status: "ok", suggestions: [makeSuggestion({ outcomeId: "stale-outcome" })] },
        response: { status: 200 },
      });
      await Promise.resolve();
    });

    expect(result.current.suggestions).toEqual([]);
    expect(result.current.suggestStatus).toBe("loading");

    await act(async () => {
      secondResolve({
        data: { status: "ok", suggestions: [makeSuggestion({ outcomeId: "fresh-outcome" })] },
        response: { status: 200 },
      });
      await Promise.resolve();
    });

    expect(result.current.suggestStatus).toBe("ok");
    expect(result.current.suggestions).toEqual([makeSuggestion({ outcomeId: "fresh-outcome" })]);
  });
});
