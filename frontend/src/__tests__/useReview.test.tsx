import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useReview } from "../hooks/useReview.js";
import type { ManagerReview } from "@weekly-commitments/contracts";

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

// ── helpers ────────────────────────────────────────────────────────────────

function makeReview(id: string, decision: string): ManagerReview {
  return {
    id,
    weeklyPlanId: "plan-1",
    reviewerUserId: "manager-1",
    decision,
    comments: "Looks good.",
    createdAt: "2026-03-17T10:00:00Z",
  } as unknown as ManagerReview;
}

// ══════════════════════════════════════════════════════════════════════════
// submitReview
// ══════════════════════════════════════════════════════════════════════════

describe("useReview – submitReview", () => {
  beforeEach(() => vi.clearAllMocks());

  it("initial state is idle with empty reviews list", () => {
    const { result } = renderHook(() => useReview());
    expect(result.current.reviews).toEqual([]);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("returns the new review and appends it to the list on success", async () => {
    const review = makeReview("review-1", "APPROVED");
    mockClient.POST.mockResolvedValueOnce({
      data: review,
      response: { status: 201, ok: true },
    });

    const { result } = renderHook(() => useReview());

    let returned: ManagerReview | null = null;
    await act(async () => {
      returned = await result.current.submitReview("plan-1", "APPROVED", "Looks good.");
    });

    expect(returned).toEqual(review);
    expect(result.current.reviews).toHaveLength(1);
    expect(result.current.reviews[0]).toEqual(review);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("calls POST /plans/{planId}/review with correct body", async () => {
    mockClient.POST.mockResolvedValueOnce({
      data: makeReview("r-1", "APPROVED"),
      response: { status: 201, ok: true },
    });

    const { result } = renderHook(() => useReview());

    await act(async () => {
      await result.current.submitReview("plan-abc", "APPROVED", "All good");
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/plans/{planId}/review", {
      params: { path: { planId: "plan-abc" } },
      body: { decision: "APPROVED", comments: "All good" },
    });
  });

  it("accumulates multiple reviews across calls", async () => {
    mockClient.POST
      .mockResolvedValueOnce({ data: makeReview("r-1", "APPROVED"), response: { status: 201, ok: true } })
      .mockResolvedValueOnce({ data: makeReview("r-2", "CHANGES_REQUESTED"), response: { status: 201, ok: true } });

    const { result } = renderHook(() => useReview());

    await act(async () => {
      await result.current.submitReview("plan-1", "APPROVED", "");
      await result.current.submitReview("plan-2", "CHANGES_REQUESTED", "Revise scope");
    });

    expect(result.current.reviews).toHaveLength(2);
  });

  it("sets error state and returns null on API error", async () => {
    mockClient.POST.mockResolvedValueOnce({
      data: null,
      error: { error: { message: "Plan not found" } },
      response: { status: 404, ok: false },
    });

    const { result } = renderHook(() => useReview());

    let returned: ManagerReview | null = undefined as unknown as ManagerReview | null;
    await act(async () => {
      returned = await result.current.submitReview("plan-missing", "APPROVED", "");
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe("Plan not found");
    expect(result.current.reviews).toEqual([]);
    expect(result.current.loading).toBe(false);
  });

  it("falls back to generic error message when API provides none", async () => {
    mockClient.POST.mockResolvedValueOnce({
      data: null,
      error: null,
      response: { status: 500, ok: false },
    });

    const { result } = renderHook(() => useReview());

    await act(async () => {
      await result.current.submitReview("plan-1", "APPROVED", "");
    });

    expect(result.current.error).toContain("500");
  });

  it("sets error state on network exception", async () => {
    mockClient.POST.mockRejectedValueOnce(new Error("Connection refused"));
    const { result } = renderHook(() => useReview());

    let returned: ManagerReview | null = undefined as unknown as ManagerReview | null;
    await act(async () => {
      returned = await result.current.submitReview("plan-1", "APPROVED", "");
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe("Connection refused");
    expect(result.current.loading).toBe(false);
  });

  it("clears loading flag after error", async () => {
    mockClient.POST.mockRejectedValueOnce(new Error("fail"));
    const { result } = renderHook(() => useReview());

    await act(async () => {
      await result.current.submitReview("plan-1", "APPROVED", "");
    });

    expect(result.current.loading).toBe(false);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// clearError
// ══════════════════════════════════════════════════════════════════════════

describe("useReview – clearError", () => {
  beforeEach(() => vi.clearAllMocks());

  it("resets error to null", async () => {
    mockClient.POST.mockRejectedValueOnce(new Error("oops"));
    const { result } = renderHook(() => useReview());

    await act(async () => {
      await result.current.submitReview("plan-1", "APPROVED", "");
    });
    expect(result.current.error).toBe("oops");

    act(() => {
      result.current.clearError();
    });

    expect(result.current.error).toBeNull();
  });
});
