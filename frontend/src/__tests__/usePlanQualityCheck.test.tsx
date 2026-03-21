import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { usePlanQualityCheck } from "../hooks/usePlanQualityCheck.js";
import type { QualityNudge } from "@weekly-commitments/contracts";

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

let mockPlanQualityNudge = true;

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({
    suggestRcdo: true,
    draftReconciliation: false,
    managerInsights: false,
    icTrends: true,
    planQualityNudge: mockPlanQualityNudge,
  }),
}));

/* ── Fixtures ─────────────────────────────────────────────────────────────── */

function makeNudge(overrides: Partial<QualityNudge> = {}): QualityNudge {
  return {
    type: "COVERAGE_GAP",
    message: "3 commits have no RCDO outcome linked.",
    severity: "WARNING",
    ...overrides,
  };
}

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("usePlanQualityCheck", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPlanQualityNudge = true;
  });

  it("initialises with empty nudges, idle status", () => {
    const { result } = renderHook(() => usePlanQualityCheck());

    expect(result.current.nudges).toEqual([]);
    expect(result.current.status).toBe("idle");
  });

  it("does not call the API when planQualityNudge flag is disabled", async () => {
    mockPlanQualityNudge = false;
    const { result } = renderHook(() => usePlanQualityCheck());

    await act(async () => {
      await result.current.checkQuality("plan-1");
    });

    expect(mockClient.POST).not.toHaveBeenCalled();
    expect(result.current.nudges).toEqual([]);
    expect(result.current.status).toBe("idle");
  });

  it("sets status to ok and populates nudges on success", async () => {
    const nudges = [makeNudge()];
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", nudges },
      response: { status: 200 },
    });

    const { result } = renderHook(() => usePlanQualityCheck());

    await act(async () => {
      await result.current.checkQuality("plan-1");
    });

    expect(result.current.status).toBe("ok");
    expect(result.current.nudges).toEqual(nudges);
  });

  it("calls POST /ai/plan-quality-check with the plan ID", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", nudges: [] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => usePlanQualityCheck());

    await act(async () => {
      await result.current.checkQuality("plan-abc");
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/ai/plan-quality-check", {
      body: { planId: "plan-abc" },
    });
  });

  it("sets status to ok with empty nudges when check passes cleanly", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", nudges: [] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => usePlanQualityCheck());

    await act(async () => {
      await result.current.checkQuality("plan-1");
    });

    expect(result.current.status).toBe("ok");
    expect(result.current.nudges).toEqual([]);
  });

  it("sets status to unavailable when backend returns unavailable", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "unavailable", nudges: [] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => usePlanQualityCheck());

    await act(async () => {
      await result.current.checkQuality("plan-1");
    });

    expect(result.current.status).toBe("unavailable");
    expect(result.current.nudges).toEqual([]);
  });

  it("sets status to rate_limited on 429 response", async () => {
    mockClient.POST.mockResolvedValue({
      data: undefined,
      response: { status: 429 },
    });

    const { result } = renderHook(() => usePlanQualityCheck());

    await act(async () => {
      await result.current.checkQuality("plan-1");
    });

    expect(result.current.status).toBe("rate_limited");
    expect(result.current.nudges).toEqual([]);
  });

  it("sets status to unavailable when response has no data", async () => {
    mockClient.POST.mockResolvedValue({
      data: undefined,
      response: { status: 500 },
    });

    const { result } = renderHook(() => usePlanQualityCheck());

    await act(async () => {
      await result.current.checkQuality("plan-1");
    });

    expect(result.current.status).toBe("unavailable");
    expect(result.current.nudges).toEqual([]);
  });

  it("sets status to unavailable on network exception", async () => {
    mockClient.POST.mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() => usePlanQualityCheck());

    await act(async () => {
      await result.current.checkQuality("plan-1");
    });

    expect(result.current.status).toBe("unavailable");
    expect(result.current.nudges).toEqual([]);
  });

  it("clearNudges resets nudges and status to idle", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", nudges: [makeNudge()] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => usePlanQualityCheck());

    await act(async () => {
      await result.current.checkQuality("plan-1");
    });

    expect(result.current.nudges).toHaveLength(1);

    act(() => {
      result.current.clearNudges();
    });

    expect(result.current.nudges).toEqual([]);
    expect(result.current.status).toBe("idle");
  });

  it("sets status to loading while the request is in-flight", async () => {
    let resolvePost!: (value: unknown) => void;
    mockClient.POST.mockReturnValue(
      new Promise((res) => {
        resolvePost = res;
      }),
    );

    const { result } = renderHook(() => usePlanQualityCheck());

    // Start the check but don't await it yet
    let checkPromise: Promise<void>;
    act(() => {
      checkPromise = result.current.checkQuality("plan-1");
    });

    expect(result.current.status).toBe("loading");

    // Resolve the request
    await act(async () => {
      resolvePost({ data: { status: "ok", nudges: [] }, response: { status: 200 } });
      await checkPromise;
    });

    expect(result.current.status).toBe("ok");
  });

  it("ignores stale responses after clearNudges is called", async () => {
    let resolvePost!: (value: unknown) => void;
    mockClient.POST.mockReturnValue(
      new Promise((res) => {
        resolvePost = res;
      }),
    );

    const { result } = renderHook(() => usePlanQualityCheck());

    let checkPromise: Promise<void>;
    act(() => {
      checkPromise = result.current.checkQuality("plan-1");
    });

    expect(result.current.status).toBe("loading");

    act(() => {
      result.current.clearNudges();
    });

    expect(result.current.status).toBe("idle");
    expect(result.current.nudges).toEqual([]);

    await act(async () => {
      resolvePost({ data: { status: "ok", nudges: [makeNudge()] }, response: { status: 200 } });
      await checkPromise;
    });

    expect(result.current.status).toBe("idle");
    expect(result.current.nudges).toEqual([]);
  });

  it("returns multiple nudges with different severities", async () => {
    const nudges: QualityNudge[] = [
      makeNudge({ type: "COVERAGE_GAP", severity: "WARNING" }),
      makeNudge({ type: "HIGH_RCDO_ALIGNMENT", severity: "POSITIVE", message: "Strong RCDO coverage." }),
      makeNudge({ type: "CHESS_NO_KING", severity: "INFO", message: "No KING priority set." }),
    ];
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", nudges },
      response: { status: 200 },
    });

    const { result } = renderHook(() => usePlanQualityCheck());

    await act(async () => {
      await result.current.checkQuality("plan-1");
    });

    expect(result.current.nudges).toHaveLength(3);
    expect(result.current.nudges[0].severity).toBe("WARNING");
    expect(result.current.nudges[1].severity).toBe("POSITIVE");
    expect(result.current.nudges[2].severity).toBe("INFO");
  });
});
