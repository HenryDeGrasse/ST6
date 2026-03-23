/**
 * Unit tests for the useQuickUpdate hook.
 *
 * The hook uses raw fetch() (not the typed API client) because the quick-update
 * and check-in-options endpoints are not yet in the generated OpenAPI types.
 * We mock globalThis.fetch and verify the hook's behaviour for:
 *   - submitBatchUpdate — happy path, error path, malformed JSON, unmount abort
 *   - fetchCheckInOptions — happy path, error path, abort on superseded call
 */
import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { QuickUpdateItem } from "@weekly-commitments/contracts";
import { useQuickUpdate } from "../hooks/useQuickUpdate.js";

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

// ── Helpers ────────────────────────────────────────────────────────────────

function mockFetchOk(body: unknown): void {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(body),
    }),
  );
}

function mockFetchError(status: number, errorBody: unknown): void {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      json: () => Promise.resolve(errorBody),
    }),
  );
}

function mockFetchNetworkError(message = "Network failure"): void {
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error(message)));
}

const SAMPLE_UPDATES: QuickUpdateItem[] = [
  {
    commitId: "commit-1",
    status: "ON_TRACK",
    note: "Going well",
    noteSource: "USER_TYPED",
    selectedSuggestionText: null,
    selectedSuggestionSource: null,
  },
];

const QUICK_UPDATE_RESPONSE = { updatedCount: 1, entries: [] };

const CHECK_IN_OPTIONS_RESPONSE = {
  status: "ok" as const,
  statusOptions: ["ON_TRACK", "AT_RISK"] as ("ON_TRACK" | "AT_RISK")[],
  progressOptions: [{ text: "Making good progress", source: "ai_generated" as const }],
};

// ══════════════════════════════════════════════════════════════════════════
// submitBatchUpdate
// ══════════════════════════════════════════════════════════════════════════

describe("useQuickUpdate – submitBatchUpdate", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("returns updated count on success and clears loading", async () => {
    mockFetchOk(QUICK_UPDATE_RESPONSE);
    const { result } = renderHook(() => useQuickUpdate());

    let response: unknown = null;
    await act(async () => {
      response = await result.current.submitBatchUpdate("plan-1", SAMPLE_UPDATES);
    });

    expect(response).toEqual(QUICK_UPDATE_RESPONSE);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("posts to the correct URL with Authorization header", async () => {
    mockFetchOk(QUICK_UPDATE_RESPONSE);
    const { result } = renderHook(() => useQuickUpdate());

    await act(async () => {
      await result.current.submitBatchUpdate("plan-abc", SAMPLE_UPDATES);
    });

    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect(call[0]).toBe("/api/v1/plans/plan-abc/quick-update");
    expect((call[1].headers as Record<string, string>)["Authorization"]).toContain("Bearer");
    expect(call[1].method).toBe("POST");
  });

  it("URL-encodes planId in path", async () => {
    mockFetchOk(QUICK_UPDATE_RESPONSE);
    const { result } = renderHook(() => useQuickUpdate());

    await act(async () => {
      await result.current.submitBatchUpdate("plan/with/slashes", SAMPLE_UPDATES);
    });

    const url = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
    expect(url).toBe("/api/v1/plans/plan%2Fwith%2Fslashes/quick-update");
  });

  it("sets error state and returns null on HTTP error", async () => {
    mockFetchError(422, { error: { message: "Validation failed" } });
    const { result } = renderHook(() => useQuickUpdate());

    let response: unknown = undefined;
    await act(async () => {
      response = await result.current.submitBatchUpdate("plan-1", SAMPLE_UPDATES);
    });

    expect(response).toBeNull();
    expect(result.current.error).toBe("Validation failed");
    expect(result.current.loading).toBe(false);
  });

  it("falls back to generic message when error body has no message", async () => {
    mockFetchError(500, null);
    const { result } = renderHook(() => useQuickUpdate());

    await act(async () => {
      await result.current.submitBatchUpdate("plan-1", SAMPLE_UPDATES);
    });

    expect(result.current.error).toContain("500");
  });

  it("sets error state on network failure", async () => {
    mockFetchNetworkError("Failed to fetch");
    const { result } = renderHook(() => useQuickUpdate());

    await act(async () => {
      await result.current.submitBatchUpdate("plan-1", SAMPLE_UPDATES);
    });

    expect(result.current.error).toBe("Failed to fetch");
    expect(result.current.loading).toBe(false);
  });

  it("sets error state when success response has malformed JSON body", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.reject(new SyntaxError("Unexpected token")),
      }),
    );
    const { result } = renderHook(() => useQuickUpdate());

    await act(async () => {
      await result.current.submitBatchUpdate("plan-1", SAMPLE_UPDATES);
    });

    expect(result.current.error).toContain("invalid response");
    expect(result.current.loading).toBe(false);
  });

  it("passes AbortSignal to fetch", async () => {
    mockFetchOk(QUICK_UPDATE_RESPONSE);
    const { result } = renderHook(() => useQuickUpdate());

    await act(async () => {
      await result.current.submitBatchUpdate("plan-1", SAMPLE_UPDATES);
    });

    const fetchInit = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1] as RequestInit;
    expect(fetchInit.signal).toBeInstanceOf(AbortSignal);
  });

  it("does not set error state when fetch is aborted (unmount)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(Object.assign(new Error("Aborted"), { name: "AbortError" })),
    );
    const { result } = renderHook(() => useQuickUpdate());

    await act(async () => {
      await result.current.submitBatchUpdate("plan-1", SAMPLE_UPDATES);
    });

    expect(result.current.error).toBeNull();
  });

  it("clearError resets the error state", async () => {
    mockFetchNetworkError();
    const { result } = renderHook(() => useQuickUpdate());

    await act(async () => {
      await result.current.submitBatchUpdate("plan-1", SAMPLE_UPDATES);
    });
    expect(result.current.error).not.toBeNull();

    act(() => {
      result.current.clearError();
    });
    expect(result.current.error).toBeNull();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// fetchCheckInOptions
// ══════════════════════════════════════════════════════════════════════════

describe("useQuickUpdate – fetchCheckInOptions", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("returns check-in options on success", async () => {
    mockFetchOk(CHECK_IN_OPTIONS_RESPONSE);
    const { result } = renderHook(() => useQuickUpdate());

    let options: unknown = null;
    await act(async () => {
      options = await result.current.fetchCheckInOptions("commit-1", "ON_TRACK", "note", 1);
    });

    expect(options).toEqual(CHECK_IN_OPTIONS_RESPONSE);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("posts to /ai/check-in-options with correct body", async () => {
    mockFetchOk(CHECK_IN_OPTIONS_RESPONSE);
    const { result } = renderHook(() => useQuickUpdate());

    await act(async () => {
      await result.current.fetchCheckInOptions("commit-abc", "AT_RISK", "blocked", 3);
    });

    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect(call[0]).toBe("/api/v1/ai/check-in-options");
    const body = JSON.parse(call[1].body as string) as Record<string, unknown>;
    expect(body).toEqual({
      commitId: "commit-abc",
      currentStatus: "AT_RISK",
      lastNote: "blocked",
      daysSinceLastCheckIn: 3,
    });
  });

  it("sets error state on HTTP error", async () => {
    mockFetchError(503, { error: { message: "Service unavailable" } });
    const { result } = renderHook(() => useQuickUpdate());

    let options: unknown;
    await act(async () => {
      options = await result.current.fetchCheckInOptions("commit-1", "ON_TRACK", "", 0);
    });

    expect(options).toBeNull();
    expect(result.current.error).toBe("Service unavailable");
  });

  it("aborts a superseded request — second call wins", async () => {
    let resolveSecond!: (value: unknown) => void;

    vi.stubGlobal(
      "fetch",
      vi.fn()
        .mockImplementationOnce((_url: string, init: RequestInit) =>
          new Promise((_resolve, reject) => {
            init.signal?.addEventListener("abort", () =>
              reject(Object.assign(new Error("Aborted"), { name: "AbortError" })),
            );
          }),
        )
        .mockImplementationOnce((_url: string, _init: RequestInit) =>
          new Promise((resolve) => {
            resolveSecond = resolve;
          }),
        ),
    );

    const { result } = renderHook(() => useQuickUpdate());

    let firstPromise: Promise<unknown>;
    let secondPromise: Promise<unknown>;

    act(() => {
      firstPromise = result.current.fetchCheckInOptions("commit-1", "ON_TRACK", "", 0);
    });
    act(() => {
      // Starting second call should abort the first
      secondPromise = result.current.fetchCheckInOptions("commit-1", "AT_RISK", "", 1);
    });

    await act(async () => {
      await firstPromise;
    });

    // No error should be set — AbortError is swallowed
    expect(result.current.error).toBeNull();
    // The second request is still in flight, so loading must remain true.
    expect(result.current.loading).toBe(true);

    await act(async () => {
      resolveSecond({
        ok: true,
        status: 200,
        json: () => Promise.resolve(CHECK_IN_OPTIONS_RESPONSE),
      });
      await secondPromise;
    });

    // Second call's result should be returned (loading cleared)
    expect(result.current.loading).toBe(false);
  });

  it("ignores a stale response even if the aborted fetch still resolves", async () => {
    let resolveFirst!: (value: unknown) => void;
    let resolveSecond!: (value: unknown) => void;

    vi.stubGlobal(
      "fetch",
      vi.fn()
        .mockImplementationOnce(() =>
          new Promise((resolve) => {
            resolveFirst = resolve;
          }),
        )
        .mockImplementationOnce(() =>
          new Promise((resolve) => {
            resolveSecond = resolve;
          }),
        ),
    );

    const { result } = renderHook(() => useQuickUpdate());

    let firstPromise!: Promise<unknown>;
    let secondPromise!: Promise<unknown>;

    act(() => {
      firstPromise = result.current.fetchCheckInOptions("commit-1", "ON_TRACK", "", 0);
    });
    act(() => {
      secondPromise = result.current.fetchCheckInOptions("commit-1", "AT_RISK", "latest", 2);
    });

    await act(async () => {
      resolveFirst({
        ok: false,
        status: 503,
        json: () => Promise.resolve({ error: { message: "stale failure" } }),
      });
      await firstPromise;
    });

    // Stale response must not clear loading or surface an error.
    expect(result.current.loading).toBe(true);
    expect(result.current.error).toBeNull();

    let secondResult: unknown = null;
    await act(async () => {
      resolveSecond({
        ok: true,
        status: 200,
        json: () => Promise.resolve(CHECK_IN_OPTIONS_RESPONSE),
      });
      secondResult = await secondPromise;
    });

    expect(secondResult).toEqual(CHECK_IN_OPTIONS_RESPONSE);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });
});
