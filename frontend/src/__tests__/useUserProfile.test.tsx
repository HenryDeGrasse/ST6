/**
 * Unit tests for useUserProfile hook.
 *
 * The hook uses raw fetch() (not the typed client) because the
 * /users/me/profile endpoint is not in the generated OpenAPI types.
 * We mock globalThis.fetch and verify behaviour.
 */
import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useUserProfile } from "../hooks/useUserProfile.js";
import type { UserProfileData } from "../hooks/useUserProfile.js";

// ── Mocks ──────────────────────────────────────────────────────────────────

vi.mock("../api/ApiContext.js", () => ({
  useApiBaseUrl: () => "/api/v1",
}));

vi.mock("../api/client.js", () => ({
  buildDevToken: () => "dev:user-1:org-1:IC",
}));

vi.mock("../context/AuthContext.js", () => ({
  useAuth: () => ({
    getToken: () => "dev-token-carol",
    user: { userId: "user-1", orgId: "org-1", roles: ["IC"] },
  }),
}));

let mockUserProfile = true;

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({
    userProfile: mockUserProfile,
  }),
}));

// ── helpers ────────────────────────────────────────────────────────────────

function makeProfile(overrides: Partial<UserProfileData> = {}): UserProfileData {
  return {
    userId: "user-1",
    weeksAnalyzed: 6,
    performanceProfile: {
      estimationAccuracy: 0.87,
      completionReliability: 0.91,
      avgCommitsPerWeek: 5.2,
      avgCarryForwardPerWeek: 0.4,
      topCategories: ["DELIVERY"],
      categoryCompletionRates: { DELIVERY: 0.91 },
      priorityCompletionRates: { KING: 0.97 },
    },
    preferences: {
      typicalPriorityPattern: "1 KING, 2 QUEENs",
      recurringCommitTitles: ["Weekly sync"],
      avgCheckInsPerWeek: 2.1,
      preferredUpdateDays: ["Tuesday"],
    },
    trends: {
      strategicAlignmentTrend: "IMPROVING",
      completionTrend: "STABLE",
      carryForwardTrend: "IMPROVING",
    },
    ...overrides,
  };
}

function mockFetchOk(body: UserProfileData) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(body),
    }),
  );
}

function mockFetchError(status: number, message?: string) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      json: () => Promise.resolve(message ? { error: { message } } : null),
    }),
  );
}

function mockFetchNetwork(message = "Network error") {
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error(message)));
}

// ══════════════════════════════════════════════════════════════════════════
// fetchProfile
// ══════════════════════════════════════════════════════════════════════════

describe("useUserProfile – fetchProfile", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockUserProfile = true;
  });

  it("initial state has null profile, not loading, no error", () => {
    const { result } = renderHook(() => useUserProfile());
    expect(result.current.profile).toBeNull();
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("populates profile on success", async () => {
    const profile = makeProfile();
    mockFetchOk(profile);
    const { result } = renderHook(() => useUserProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    expect(result.current.profile).toEqual(profile);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("fetches from /api/v1/users/me/profile with Authorization header", async () => {
    mockFetchOk(makeProfile());
    const { result } = renderHook(() => useUserProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect(call[0]).toBe("/api/v1/users/me/profile");
    expect(call[1].method).toBe("GET");
    expect((call[1].headers as Record<string, string>)["Authorization"]).toContain("Bearer");
  });

  it("sets error on non-ok response with message", async () => {
    mockFetchError(403, "Forbidden access");
    const { result } = renderHook(() => useUserProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    expect(result.current.error).toBe("Forbidden access");
    expect(result.current.profile).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it("sets generic error when response has no message", async () => {
    mockFetchError(500);
    const { result } = renderHook(() => useUserProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    expect(result.current.error).toContain("500");
  });

  it("sets error on network failure", async () => {
    mockFetchNetwork("Connection refused");
    const { result } = renderHook(() => useUserProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    expect(result.current.error).toBe("Connection refused");
    expect(result.current.profile).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it("does not call fetch when userProfile flag is off", async () => {
    mockUserProfile = false;
    vi.stubGlobal("fetch", vi.fn());
    const { result } = renderHook(() => useUserProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    expect(fetch).not.toHaveBeenCalled();
    expect(result.current.profile).toBeNull();
  });

  it("clears loading after successful fetch", async () => {
    mockFetchOk(makeProfile());
    const { result } = renderHook(() => useUserProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });

    expect(result.current.loading).toBe(false);
  });

  it("re-fetches and updates profile on second call", async () => {
    const p1 = makeProfile({ weeksAnalyzed: 4 });
    const p2 = makeProfile({ weeksAnalyzed: 8 });
    vi.stubGlobal(
      "fetch",
      vi.fn()
        .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve(p1) })
        .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve(p2) }),
    );

    const { result } = renderHook(() => useUserProfile());

    await act(async () => { await result.current.fetchProfile(); });
    expect(result.current.profile?.weeksAnalyzed).toBe(4);

    await act(async () => { await result.current.fetchProfile(); });
    expect(result.current.profile?.weeksAnalyzed).toBe(8);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// clearError
// ══════════════════════════════════════════════════════════════════════════

describe("useUserProfile – clearError", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockUserProfile = true;
  });

  it("clears the error state", async () => {
    mockFetchError(500, "Internal error");
    const { result } = renderHook(() => useUserProfile());

    await act(async () => {
      await result.current.fetchProfile();
    });
    expect(result.current.error).not.toBeNull();

    act(() => {
      result.current.clearError();
    });

    expect(result.current.error).toBeNull();
  });
});
