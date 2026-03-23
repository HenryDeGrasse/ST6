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

describe("useAiManagerInsights", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockManagerInsights = true;
  });

  it("ignores a stale response when a newer fetch starts before it resolves", async () => {
    let resolveFirst!: (value: unknown) => void;
    let resolveSecond!: (value: unknown) => void;

    mockClient.POST
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveFirst = resolve;
      }))
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveSecond = resolve;
      }));

    const { result } = renderHook(() => useAiManagerInsights());

    let firstPromise!: Promise<void>;
    let secondPromise!: Promise<void>;

    act(() => {
      firstPromise = result.current.fetchInsights("2026-03-17");
    });
    act(() => {
      secondPromise = result.current.fetchInsights("2026-03-24");
    });

    await act(async () => {
      resolveFirst({
        data: {
          status: "ok",
          headline: "stale headline",
          insights: [{ title: "stale", detail: "1", severity: "INFO" }],
        },
        response: { status: 200 },
      });
      await firstPromise;
    });

    expect(result.current.headline).toBeNull();
    expect(result.current.insights).toEqual([]);
    expect(result.current.status).toBe("loading");

    await act(async () => {
      resolveSecond({
        data: {
          status: "ok",
          headline: "fresh headline",
          insights: [{ title: "fresh", detail: "2", severity: "WARNING" }],
        },
        response: { status: 200 },
      });
      await secondPromise;
    });

    expect(result.current.status).toBe("ok");
    expect(result.current.headline).toBe("fresh headline");
    expect(result.current.insights).toEqual([{ title: "fresh", detail: "2", severity: "WARNING" }]);
  });

  it("clearInsights invalidates an in-flight response", async () => {
    let resolvePost!: (value: unknown) => void;
    mockClient.POST.mockImplementationOnce(() => new Promise((resolve) => {
      resolvePost = resolve;
    }));

    const { result } = renderHook(() => useAiManagerInsights());

    let fetchPromise!: Promise<void>;
    act(() => {
      fetchPromise = result.current.fetchInsights("2026-03-17");
    });

    act(() => {
      result.current.clearInsights();
    });

    await act(async () => {
      resolvePost({
        data: {
          status: "ok",
          headline: "late headline",
          insights: [{ title: "late", detail: "3", severity: "POSITIVE" }],
        },
        response: { status: 200 },
      });
      await fetchPromise;
    });

    expect(result.current.status).toBe("idle");
    expect(result.current.headline).toBeNull();
    expect(result.current.insights).toEqual([]);
  });
});
