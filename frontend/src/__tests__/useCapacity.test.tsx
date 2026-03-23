/**
 * Unit tests for capacity hooks:
 *   - useCapacityProfile  (GET /users/me/capacity, gated by capacityTracking flag)
 */
import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useCapacityProfile } from "../hooks/useCapacity.js";
import type { CapacityProfile } from "../hooks/useCapacity.js";

const mockClient = {
  GET: vi.fn(),
  POST: vi.fn(),
  PATCH: vi.fn(),
  DELETE: vi.fn(),
  use: vi.fn(),
};

let mockCapacityTracking = true;

vi.mock("../api/ApiContext.js", () => ({
  useApiClient: () => mockClient,
}));

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({
    capacityTracking: mockCapacityTracking,
    estimationCoaching: false,
  }),
}));

// ── helpers ────────────────────────────────────────────────────────────────

function makeProfile(overrides: Partial<CapacityProfile> = {}): CapacityProfile {
  return {
    orgId: "org-1",
    userId: "user-1",
    weeksAnalyzed: 6,
    avgEstimatedHours: 32.0,
    avgActualHours: 28.5,
    estimationBias: 0.89,
    realisticWeeklyCap: 30.0,
    categoryBiasJson: null,
    priorityCompletionJson: null,
    confidenceLevel: "MEDIUM",
    computedAt: "2026-03-17T00:00:00Z",
    ...overrides,
  };
}

function mockGetOk(profile: CapacityProfile) {
  mockClient.GET.mockResolvedValueOnce({
    data: profile,
    response: { status: 200, ok: true },
  });
}

function mockGetError(status: number, message?: string) {
  mockClient.GET.mockResolvedValueOnce({
    data: null,
    error: message ? { error: { message } } : null,
    response: { status, ok: false },
  });
}

// ══════════════════════════════════════════════════════════════════════════
// useCapacityProfile
// ══════════════════════════════════════════════════════════════════════════

describe("useCapacityProfile", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCapacityTracking = true;
  });

  it("initial state has null profile, not loading, no error", () => {
    const { result } = renderHook(() => useCapacityProfile());
    expect(result.current.profile).toBeNull();
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("populates profile on success", async () => {
    const profile = makeProfile({ realisticWeeklyCap: 30 });
    mockGetOk(profile);
    const { result } = renderHook(() => useCapacityProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    expect(result.current.profile).toEqual(profile);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("calls GET /users/me/capacity", async () => {
    mockGetOk(makeProfile());
    const { result } = renderHook(() => useCapacityProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    expect(mockClient.GET).toHaveBeenCalledWith("/users/me/capacity");
  });

  it("sets error state on API error response", async () => {
    mockGetError(500, "Server error");
    const { result } = renderHook(() => useCapacityProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    expect(result.current.error).toBe("Server error");
    expect(result.current.profile).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it("falls back to generic error message when error body has no message", async () => {
    mockGetError(503);
    const { result } = renderHook(() => useCapacityProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    expect(result.current.error).toContain("503");
  });

  it("sets error state on network exception", async () => {
    mockClient.GET.mockRejectedValueOnce(new Error("Network timeout"));
    const { result } = renderHook(() => useCapacityProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    expect(result.current.error).toBe("Network timeout");
    expect(result.current.loading).toBe(false);
  });

  it("does not call the API when capacityTracking flag is off", async () => {
    mockCapacityTracking = false;
    const { result } = renderHook(() => useCapacityProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    expect(mockClient.GET).not.toHaveBeenCalled();
    expect(result.current.profile).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it("clearError resets error to null", async () => {
    mockGetError(500, "error");
    const { result } = renderHook(() => useCapacityProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });
    expect(result.current.error).not.toBeNull();

    act(() => {
      result.current.clearError();
    });

    expect(result.current.error).toBeNull();
  });

  it("re-fetches and updates profile on second call", async () => {
    const profile1 = makeProfile({ realisticWeeklyCap: 30 });
    const profile2 = makeProfile({ realisticWeeklyCap: 35 });
    mockGetOk(profile1);
    mockGetOk(profile2);

    const { result } = renderHook(() => useCapacityProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });
    expect(result.current.profile?.realisticWeeklyCap).toBe(30);

    await act(async () => {
      await result.current.fetchProfile();
    });
    expect(result.current.profile?.realisticWeeklyCap).toBe(35);
  });
});
