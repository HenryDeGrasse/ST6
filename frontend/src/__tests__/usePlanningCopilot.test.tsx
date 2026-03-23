import { act, renderHook } from "@testing-library/react";
import { ChessPriority } from "@weekly-commitments/contracts";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { usePlanningCopilot } from "../hooks/usePlanningCopilot.js";

const mockClient = { GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn(), DELETE: vi.fn(), use: vi.fn() };
let mockPlanningCopilot = true;

vi.mock("../api/ApiContext.js", () => ({ useApiClient: () => mockClient }));
vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({ planningCopilot: mockPlanningCopilot }),
}));

describe("usePlanningCopilot", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPlanningCopilot = true;
  });

  it("fetches team plan suggestions", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", weekStart: "2026-03-16", summary: { teamCapacityHours: 40, suggestedHours: 32, bufferHours: 8, atRiskOutcomeCount: 1, criticalOutcomeCount: 0, strategicFocusFloor: 0.5, headline: "Focus on top risks" }, members: [], outcomeAllocations: [], llmRefined: true },
      response: { status: 200 },
    });
    const { result } = renderHook(() => usePlanningCopilot());
    await act(async () => {
      await result.current.fetchSuggestion("2026-03-16");
    });
    expect(mockClient.POST).toHaveBeenCalledWith("/ai/team-plan-suggestion", { body: { weekStart: "2026-03-16", regenerate: false } });
    expect(result.current.suggestionStatus).toBe("ok");
  });

  it("applies selected suggestions", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", weekStart: "2026-03-16", members: [{ userId: "u1", displayName: "Alice", planId: "p1", createdPlan: true, appliedCommits: [] }] },
      response: { status: 200 },
    });
    const { result } = renderHook(() => usePlanningCopilot());
    await act(async () => {
      await result.current.applySuggestion({ weekStart: "2026-03-16", members: [{ userId: "u1", suggestedCommits: [{ title: "Ship", chessPriority: ChessPriority.KING }] }] });
    });
    expect(result.current.applyStatus).toBe("ok");
    expect(result.current.applyResult?.members).toHaveLength(1);
  });

  it("clears stale apply results when a later apply attempt fails", async () => {
    mockClient.POST
      .mockResolvedValueOnce({
        data: {
          status: "ok",
          weekStart: "2026-03-16",
          members: [{ userId: "u1", displayName: "Alice", planId: "p1", createdPlan: true, appliedCommits: [] }],
        },
        response: { status: 200 },
      })
      .mockResolvedValueOnce({
        error: { error: { message: "Rate limit reached" } },
        response: { status: 429 },
      });

    const { result } = renderHook(() => usePlanningCopilot());

    await act(async () => {
      await result.current.applySuggestion({ weekStart: "2026-03-16", members: [{ userId: "u1", suggestedCommits: [{ title: "Ship", chessPriority: ChessPriority.KING }] }] });
    });
    expect(result.current.applyResult?.members).toHaveLength(1);

    await act(async () => {
      await result.current.applySuggestion({ weekStart: "2026-03-16", members: [{ userId: "u1", suggestedCommits: [{ title: "Ship", chessPriority: ChessPriority.KING }] }] });
    });

    expect(result.current.applyStatus).toBe("rate_limited");
    expect(result.current.applyResult).toBeNull();
  });

  it("does not surface a duplicate generic error on rate limit", async () => {
    mockClient.POST.mockResolvedValue({
      error: { error: { message: "Rate limit reached" } },
      response: { status: 429 },
    });

    const { result } = renderHook(() => usePlanningCopilot());

    await act(async () => {
      await result.current.fetchSuggestion("2026-03-16");
    });

    expect(result.current.suggestionStatus).toBe("rate_limited");
    expect(result.current.error).toBeNull();
  });

  it("does nothing when feature flag is disabled", async () => {
    mockPlanningCopilot = false;
    const { result } = renderHook(() => usePlanningCopilot());
    await act(async () => {
      await result.current.fetchSuggestion("2026-03-16");
    });
    expect(mockClient.POST).not.toHaveBeenCalled();
  });
});
