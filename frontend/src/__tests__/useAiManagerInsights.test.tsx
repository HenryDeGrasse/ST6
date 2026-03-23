import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAiManagerInsights } from "../hooks/useAiManagerInsights.js";

const mockClient = {
  GET: vi.fn(),
  POST: vi.fn(),
  PATCH: vi.fn(),
  DELETE: vi.fn(),
  use: vi.fn(),
};

let mockManagerInsights = true;

vi.mock("../api/ApiContext.js", () => ({
  useApiClient: () => mockClient,
}));

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({
    managerInsights: mockManagerInsights,
  }),
}));

// ── helpers ────────────────────────────────────────────────────────────────

function mockOk(headline: string) {
  mockClient.POST.mockResolvedValueOnce({
    data: {
      status: "ok",
      headline,
      insights: [{ title: "T1", detail: "D1", severity: "INFO" }],
    },
    response: { status: 200 },
  });
}

function mockStatus(httpStatus: number, dataStatus: string) {
  mockClient.POST.mockResolvedValueOnce({
    data: { status: dataStatus },
    response: { status: httpStatus },
  });
}

function mockHttpStatus(httpStatus: number) {
  mockClient.POST.mockResolvedValueOnce({
    data: null,
    error: { error: { message: `Error ${httpStatus}` } },
    response: { status: httpStatus },
  });
}

// ══════════════════════════════════════════════════════════════════════════
// Basic happy / status paths
// ══════════════════════════════════════════════════════════════════════════

describe("useAiManagerInsights – response states", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockManagerInsights = true;
  });

  it("initial state is idle with no headline or insights", () => {
    const { result } = renderHook(() => useAiManagerInsights());
    expect(result.current.status).toBe("idle");
    expect(result.current.headline).toBeNull();
    expect(result.current.insights).toEqual([]);
  });

  it("sets status=ok and populates headline + insights on success", async () => {
    mockOk("Team is on track this week.");
    const { result } = renderHook(() => useAiManagerInsights());

    await act(async () => {
      await result.current.fetchInsights("2026-03-17");
    });

    expect(result.current.status).toBe("ok");
    expect(result.current.headline).toBe("Team is on track this week.");
    expect(result.current.insights).toHaveLength(1);
    expect(result.current.insights[0].title).toBe("T1");
  });

  it("posts to /ai/manager-insights with the given weekStart", async () => {
    mockOk("headline");
    const { result } = renderHook(() => useAiManagerInsights());

    await act(async () => {
      await result.current.fetchInsights("2026-03-17");
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/ai/manager-insights", {
      body: { weekStart: "2026-03-17" },
    });
  });

  it("sets status=unavailable when data.status is 'unavailable'", async () => {
    mockStatus(200, "unavailable");
    const { result } = renderHook(() => useAiManagerInsights());

    await act(async () => {
      await result.current.fetchInsights("2026-03-17");
    });

    expect(result.current.status).toBe("unavailable");
    expect(result.current.headline).toBeNull();
    expect(result.current.insights).toEqual([]);
  });

  it("sets status=rate_limited on HTTP 429", async () => {
    mockHttpStatus(429);
    const { result } = renderHook(() => useAiManagerInsights());

    await act(async () => {
      await result.current.fetchInsights("2026-03-17");
    });

    expect(result.current.status).toBe("rate_limited");
    expect(result.current.headline).toBeNull();
  });

  it("sets status=unavailable when resp.data is null", async () => {
    mockClient.POST.mockResolvedValueOnce({ data: null, response: { status: 200 } });
    const { result } = renderHook(() => useAiManagerInsights());

    await act(async () => {
      await result.current.fetchInsights("2026-03-17");
    });

    expect(result.current.status).toBe("unavailable");
  });

  it("sets status=unavailable on network error (caught exception)", async () => {
    mockClient.POST.mockRejectedValueOnce(new Error("Network failure"));
    const { result } = renderHook(() => useAiManagerInsights());

    await act(async () => {
      await result.current.fetchInsights("2026-03-17");
    });

    expect(result.current.status).toBe("unavailable");
    expect(result.current.headline).toBeNull();
  });

  it("clears loading after request completes", async () => {
    mockOk("headline");
    const { result } = renderHook(() => useAiManagerInsights());

    await act(async () => {
      await result.current.fetchInsights("2026-03-17");
    });

    // useAiManagerInsights does not expose a loading flag in its interface,
    // but the status transition from loading→ok is sufficient evidence.
    expect(result.current.status).toBe("ok");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Feature-flag gating
// ══════════════════════════════════════════════════════════════════════════

describe("useAiManagerInsights – feature flag gating", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("does not call the API when managerInsights flag is off", async () => {
    mockManagerInsights = false;
    const { result } = renderHook(() => useAiManagerInsights());

    await act(async () => {
      await result.current.fetchInsights("2026-03-17");
    });

    expect(mockClient.POST).not.toHaveBeenCalled();
    expect(result.current.status).toBe("idle");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// clearInsights + stale-response protection (from step-11)
// ══════════════════════════════════════════════════════════════════════════

describe("useAiManagerInsights – clearInsights and stale response", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockManagerInsights = true;
  });

  it("clearInsights resets status to idle and clears data", async () => {
    mockOk("headline");
    const { result } = renderHook(() => useAiManagerInsights());

    await act(async () => {
      await result.current.fetchInsights("2026-03-17");
    });
    expect(result.current.status).toBe("ok");

    act(() => {
      result.current.clearInsights();
    });

    expect(result.current.status).toBe("idle");
    expect(result.current.headline).toBeNull();
    expect(result.current.insights).toEqual([]);
  });

  it("ignores a stale response when a newer fetch starts before it resolves", async () => {
    let resolveFirst!: (value: unknown) => void;
    let resolveSecond!: (value: unknown) => void;

    mockClient.POST
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve; }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveSecond = resolve; }));

    const { result } = renderHook(() => useAiManagerInsights());

    let firstPromise!: Promise<void>;
    let secondPromise!: Promise<void>;

    act(() => { firstPromise = result.current.fetchInsights("2026-03-17"); });
    act(() => { secondPromise = result.current.fetchInsights("2026-03-24"); });

    await act(async () => {
      resolveFirst({
        data: { status: "ok", headline: "stale", insights: [] },
        response: { status: 200 },
      });
      await firstPromise;
    });

    expect(result.current.headline).toBeNull();
    expect(result.current.status).toBe("loading");

    await act(async () => {
      resolveSecond({
        data: { status: "ok", headline: "fresh", insights: [] },
        response: { status: 200 },
      });
      await secondPromise;
    });

    expect(result.current.status).toBe("ok");
    expect(result.current.headline).toBe("fresh");
  });

  it("clearInsights invalidates an in-flight response", async () => {
    let resolvePost!: (value: unknown) => void;
    mockClient.POST.mockImplementationOnce(
      () => new Promise((resolve) => { resolvePost = resolve; }),
    );

    const { result } = renderHook(() => useAiManagerInsights());

    let fetchPromise!: Promise<void>;
    act(() => { fetchPromise = result.current.fetchInsights("2026-03-17"); });
    act(() => { result.current.clearInsights(); });

    await act(async () => {
      resolvePost({ data: { status: "ok", headline: "late", insights: [] }, response: { status: 200 } });
      await fetchPromise;
    });

    expect(result.current.status).toBe("idle");
    expect(result.current.headline).toBeNull();
  });
});
