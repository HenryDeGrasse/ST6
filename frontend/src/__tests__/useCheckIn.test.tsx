import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useCheckIn } from "../hooks/useCheckIn.js";
import type { CheckInEntry } from "@weekly-commitments/contracts";

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

/* ── Fixtures ─────────────────────────────────────────────────────────────── */

function makeEntry(overrides: Partial<CheckInEntry> = {}): CheckInEntry {
  return {
    id: "entry-1",
    commitId: "commit-1",
    status: "ON_TRACK",
    note: "Making good progress",
    createdAt: "2026-03-11T10:00:00.000Z",
    ...overrides,
  };
}

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("useCheckIn", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── Initial state ──────────────────────────────────────────────────────────

  it("initialises with empty entries, not loading, no error", () => {
    const { result } = renderHook(() => useCheckIn());

    expect(result.current.entries).toEqual([]);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  // ── fetchCheckIns ──────────────────────────────────────────────────────────

  it("calls GET /commits/{commitId}/check-ins with the correct commit ID", async () => {
    mockClient.GET.mockResolvedValue({
      data: { commitId: "commit-1", entries: [] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useCheckIn());

    await act(async () => {
      await result.current.fetchCheckIns("commit-1");
    });

    expect(mockClient.GET).toHaveBeenCalledWith("/commits/{commitId}/check-ins", {
      params: { path: { commitId: "commit-1" } },
    });
  });

  it("populates entries on successful fetch", async () => {
    const entries = [makeEntry(), makeEntry({ id: "entry-2", status: "AT_RISK" })];
    mockClient.GET.mockResolvedValue({
      data: { commitId: "commit-1", entries },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useCheckIn());

    await act(async () => {
      await result.current.fetchCheckIns("commit-1");
    });

    expect(result.current.entries).toEqual(entries);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("sets loading to true while fetch is in-flight", async () => {
    let resolve!: (value: unknown) => void;
    mockClient.GET.mockReturnValue(
      new Promise((res) => {
        resolve = res;
      }),
    );

    const { result } = renderHook(() => useCheckIn());

    let fetchPromise: Promise<void>;
    act(() => {
      fetchPromise = result.current.fetchCheckIns("commit-1");
    });

    expect(result.current.loading).toBe(true);

    await act(async () => {
      resolve({ data: { commitId: "commit-1", entries: [] }, response: { status: 200 } });
      await fetchPromise;
    });

    expect(result.current.loading).toBe(false);
  });

  it("sets error on failed fetch (no data)", async () => {
    mockClient.GET.mockResolvedValue({
      data: undefined,
      error: { error: { message: "Not found" } },
      response: { status: 404 },
    });

    const { result } = renderHook(() => useCheckIn());

    await act(async () => {
      await result.current.fetchCheckIns("commit-unknown");
    });

    expect(result.current.error).toBe("Not found");
    expect(result.current.entries).toEqual([]);
  });

  it("sets error on fetch with no error body (uses status code)", async () => {
    mockClient.GET.mockResolvedValue({
      data: undefined,
      error: undefined,
      response: { status: 500 },
    });

    const { result } = renderHook(() => useCheckIn());

    await act(async () => {
      await result.current.fetchCheckIns("commit-1");
    });

    expect(result.current.error).toContain("500");
  });

  it("sets error on network exception during fetch", async () => {
    mockClient.GET.mockRejectedValue(new Error("Network failure"));

    const { result } = renderHook(() => useCheckIn());

    await act(async () => {
      await result.current.fetchCheckIns("commit-1");
    });

    expect(result.current.error).toBe("Network failure");
    expect(result.current.entries).toEqual([]);
  });

  it("ignores stale fetch results when a newer commit is opened", async () => {
    let resolveFirst!: (value: unknown) => void;
    let resolveSecond!: (value: unknown) => void;

    mockClient.GET
      .mockImplementationOnce(
        () =>
          new Promise((res) => {
            resolveFirst = res;
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise((res) => {
            resolveSecond = res;
          }),
      );

    const { result } = renderHook(() => useCheckIn());

    act(() => {
      void result.current.fetchCheckIns("commit-1");
      void result.current.fetchCheckIns("commit-2");
    });

    await act(async () => {
      resolveSecond({
        data: { commitId: "commit-2", entries: [makeEntry({ id: "entry-2", commitId: "commit-2" })] },
        response: { status: 200 },
      });
      await Promise.resolve();
    });

    await act(async () => {
      resolveFirst({
        data: { commitId: "commit-1", entries: [makeEntry({ id: "entry-1", commitId: "commit-1" })] },
        response: { status: 200 },
      });
      await Promise.resolve();
    });

    expect(result.current.entries).toEqual([makeEntry({ id: "entry-2", commitId: "commit-2" })]);
    expect(result.current.loading).toBe(false);
  });

  // ── addCheckIn ─────────────────────────────────────────────────────────────

  it("calls POST /commits/{commitId}/check-in with the correct body", async () => {
    const newEntry = makeEntry();
    mockClient.POST.mockResolvedValue({
      data: newEntry,
      response: { status: 201 },
    });

    const { result } = renderHook(() => useCheckIn());

    await act(async () => {
      await result.current.addCheckIn("commit-1", { status: "ON_TRACK", note: "Making progress" });
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/commits/{commitId}/check-in", {
      params: { path: { commitId: "commit-1" } },
      body: { status: "ON_TRACK", note: "Making progress" },
    });
  });

  it("returns the created entry and appends it to entries on success", async () => {
    const existingEntry = makeEntry({ id: "entry-1" });
    const newEntry = makeEntry({ id: "entry-2", status: "AT_RISK" });
    mockClient.POST.mockResolvedValue({
      data: newEntry,
      response: { status: 201 },
    });

    const { result } = renderHook(() => useCheckIn());

    // Seed with an existing entry
    mockClient.GET.mockResolvedValue({
      data: { commitId: "commit-1", entries: [existingEntry] },
      response: { status: 200 },
    });
    await act(async () => {
      await result.current.fetchCheckIns("commit-1");
    });

    let addedEntry: CheckInEntry | null = null;
    await act(async () => {
      addedEntry = await result.current.addCheckIn("commit-1", { status: "AT_RISK" });
    });

    expect(addedEntry).toEqual(newEntry);
    expect(result.current.entries).toHaveLength(2);
    expect(result.current.entries[1]).toEqual(newEntry);
  });

  it("returns null and sets error when add fails", async () => {
    mockClient.POST.mockResolvedValue({
      data: undefined,
      error: { error: { message: "Forbidden" } },
      response: { status: 403 },
    });

    const { result } = renderHook(() => useCheckIn());

    let returnValue: CheckInEntry | null = undefined as unknown as CheckInEntry | null;
    await act(async () => {
      returnValue = await result.current.addCheckIn("commit-1", { status: "BLOCKED" });
    });

    expect(returnValue).toBeNull();
    expect(result.current.error).toBe("Forbidden");
  });

  it("returns null and sets error on network exception during add", async () => {
    mockClient.POST.mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() => useCheckIn());

    let returnValue: CheckInEntry | null = undefined as unknown as CheckInEntry | null;
    await act(async () => {
      returnValue = await result.current.addCheckIn("commit-1", { status: "ON_TRACK" });
    });

    expect(returnValue).toBeNull();
    expect(result.current.error).toBe("Network error");
  });

  it("sets loading to true while add is in-flight", async () => {
    let resolve!: (value: unknown) => void;
    mockClient.POST.mockReturnValue(
      new Promise((res) => {
        resolve = res;
      }),
    );

    const { result } = renderHook(() => useCheckIn());

    let addPromise: Promise<CheckInEntry | null>;
    act(() => {
      addPromise = result.current.addCheckIn("commit-1", { status: "ON_TRACK" });
    });

    expect(result.current.loading).toBe(true);

    await act(async () => {
      resolve({ data: makeEntry(), response: { status: 201 } });
      await addPromise;
    });

    expect(result.current.loading).toBe(false);
  });

  it("ignores stale add results after switching to a different commit", async () => {
    let resolveAdd!: (value: unknown) => void;

    mockClient.POST.mockImplementationOnce(
      () =>
        new Promise((res) => {
          resolveAdd = res;
        }),
    );
    mockClient.GET.mockResolvedValueOnce({
      data: { commitId: "commit-2", entries: [makeEntry({ id: "entry-2", commitId: "commit-2" })] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useCheckIn());

    act(() => {
      void result.current.addCheckIn("commit-1", { status: "AT_RISK" });
    });

    await act(async () => {
      await result.current.fetchCheckIns("commit-2");
    });

    await act(async () => {
      resolveAdd({
        data: makeEntry({ id: "entry-1", commitId: "commit-1", status: "AT_RISK" }),
        response: { status: 201 },
      });
      await Promise.resolve();
    });

    expect(result.current.entries).toEqual([makeEntry({ id: "entry-2", commitId: "commit-2" })]);
    expect(result.current.loading).toBe(false);
  });

  it("can send a check-in without a note", async () => {
    mockClient.POST.mockResolvedValue({
      data: makeEntry({ note: "" }),
      response: { status: 201 },
    });

    const { result } = renderHook(() => useCheckIn());

    await act(async () => {
      await result.current.addCheckIn("commit-1", { status: "DONE_EARLY" });
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/commits/{commitId}/check-in", {
      params: { path: { commitId: "commit-1" } },
      body: { status: "DONE_EARLY" },
    });
  });

  // ── clearEntries ───────────────────────────────────────────────────────────

  it("clearEntries resets entries to empty", async () => {
    mockClient.GET.mockResolvedValue({
      data: { commitId: "commit-1", entries: [makeEntry()] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useCheckIn());

    await act(async () => {
      await result.current.fetchCheckIns("commit-1");
    });

    expect(result.current.entries).toHaveLength(1);

    act(() => {
      result.current.clearEntries();
    });

    expect(result.current.entries).toEqual([]);
  });

  it("clearEntries cancels an in-flight request and clears loading", async () => {
    let resolve!: (value: unknown) => void;
    mockClient.GET.mockReturnValue(
      new Promise((res) => {
        resolve = res;
      }),
    );

    const { result } = renderHook(() => useCheckIn());

    act(() => {
      void result.current.fetchCheckIns("commit-1");
    });

    expect(result.current.loading).toBe(true);

    act(() => {
      result.current.clearEntries();
    });

    expect(result.current.entries).toEqual([]);
    expect(result.current.loading).toBe(false);

    await act(async () => {
      resolve({ data: { commitId: "commit-1", entries: [makeEntry()] }, response: { status: 200 } });
      await Promise.resolve();
    });

    expect(result.current.entries).toEqual([]);
    expect(result.current.loading).toBe(false);
  });

  // ── clearError ─────────────────────────────────────────────────────────────

  it("clearError resets error to null", async () => {
    mockClient.GET.mockResolvedValue({
      data: undefined,
      error: { error: { message: "Something went wrong" } },
      response: { status: 500 },
    });

    const { result } = renderHook(() => useCheckIn());

    await act(async () => {
      await result.current.fetchCheckIns("commit-1");
    });

    expect(result.current.error).not.toBeNull();

    act(() => {
      result.current.clearError();
    });

    expect(result.current.error).toBeNull();
  });

  // ── Multiple status values ─────────────────────────────────────────────────

  it("supports all CheckInStatus values", async () => {
    const statuses = ["ON_TRACK", "AT_RISK", "BLOCKED", "DONE_EARLY"] as const;

    for (const status of statuses) {
      mockClient.POST.mockResolvedValue({
        data: makeEntry({ status }),
        response: { status: 201 },
      });

      const { result } = renderHook(() => useCheckIn());

      const entries: Array<CheckInEntry | null> = [];
      await act(async () => {
        entries.push(await result.current.addCheckIn("commit-1", { status }));
      });

      expect(entries[0]?.status).toBe(status);
    }
  });
});
