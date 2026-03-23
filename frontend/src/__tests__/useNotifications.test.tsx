import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useNotifications } from "../hooks/useNotifications.js";
import type { NotificationItem } from "@weekly-commitments/contracts";

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

function makeNotification(id: string): NotificationItem {
  return {
    id,
    type: "PLAN_REVIEW_READY",
    message: `Notification ${id}`,
    read: false,
    createdAt: "2026-03-17T10:00:00Z",
  } as unknown as NotificationItem;
}

function mockGetOk(items: NotificationItem[]) {
  mockClient.GET.mockResolvedValueOnce({
    data: items,
    response: { status: 200, ok: true },
  });
}

function mockGetError(status: number, message: string) {
  mockClient.GET.mockResolvedValueOnce({
    data: null,
    error: { error: { message } },
    response: { status, ok: false },
  });
}

function mockPostOk() {
  mockClient.POST.mockResolvedValueOnce({ data: {}, response: { status: 200, ok: true } });
}

function mockPostError(status: number, message: string) {
  mockClient.POST.mockResolvedValueOnce({
    data: null,
    error: { error: { message } },
    response: { status, ok: false },
  });
}

// ══════════════════════════════════════════════════════════════════════════
// fetchUnread
// ══════════════════════════════════════════════════════════════════════════

describe("useNotifications – fetchUnread", () => {
  beforeEach(() => vi.clearAllMocks());

  it("starts with empty notifications and zero unread count", () => {
    const { result } = renderHook(() => useNotifications());
    expect(result.current.notifications).toEqual([]);
    expect(result.current.unreadCount).toBe(0);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("populates notifications on success", async () => {
    const items = [makeNotification("n-1"), makeNotification("n-2")];
    mockGetOk(items);
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });

    expect(result.current.notifications).toEqual(items);
    expect(result.current.unreadCount).toBe(2);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("calls GET /notifications/unread", async () => {
    mockGetOk([]);
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });

    expect(mockClient.GET).toHaveBeenCalledWith("/notifications/unread");
  });

  it("sets error state on API error response", async () => {
    mockGetError(500, "Internal error");
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });

    expect(result.current.error).toBe("Internal error");
    expect(result.current.notifications).toEqual([]);
    expect(result.current.loading).toBe(false);
  });

  it("falls back to generic message when error body has no message", async () => {
    mockClient.GET.mockResolvedValueOnce({
      data: null,
      error: null,
      response: { status: 503, ok: false },
    });
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });

    expect(result.current.error).toContain("503");
  });

  it("sets error state on network exception", async () => {
    mockClient.GET.mockRejectedValueOnce(new Error("Network failure"));
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });

    expect(result.current.error).toBe("Network failure");
    expect(result.current.loading).toBe(false);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// markRead
// ══════════════════════════════════════════════════════════════════════════

describe("useNotifications – markRead", () => {
  beforeEach(() => vi.clearAllMocks());

  it("removes the notification from the list on success", async () => {
    const items = [makeNotification("n-1"), makeNotification("n-2")];
    mockGetOk(items);
    mockPostOk();
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });
    await act(async () => {
      await result.current.markRead("n-1");
    });

    expect(result.current.notifications).toHaveLength(1);
    expect(result.current.notifications[0].id).toBe("n-2");
    expect(result.current.unreadCount).toBe(1);
  });

  it("calls POST /notifications/{notificationId}/read with correct path", async () => {
    mockGetOk([makeNotification("n-1")]);
    mockPostOk();
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });
    await act(async () => {
      await result.current.markRead("n-1");
    });

    expect(mockClient.POST).toHaveBeenCalledWith(
      "/notifications/{notificationId}/read",
      { params: { path: { notificationId: "n-1" } } },
    );
  });

  it("sets error state when markRead API call fails", async () => {
    mockGetOk([makeNotification("n-1")]);
    mockPostError(403, "Forbidden");
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });
    await act(async () => {
      await result.current.markRead("n-1");
    });

    expect(result.current.error).toBe("Forbidden");
    // Notification should still be in the list
    expect(result.current.notifications).toHaveLength(1);
  });

  it("sets error state on network exception in markRead", async () => {
    mockGetOk([makeNotification("n-1")]);
    mockClient.POST.mockRejectedValueOnce(new Error("Timeout"));
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });
    await act(async () => {
      await result.current.markRead("n-1");
    });

    expect(result.current.error).toBe("Timeout");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// markAllRead
// ══════════════════════════════════════════════════════════════════════════

describe("useNotifications – markAllRead", () => {
  beforeEach(() => vi.clearAllMocks());

  it("clears all notifications on success", async () => {
    const items = [makeNotification("n-1"), makeNotification("n-2")];
    mockGetOk(items);
    mockPostOk();
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });
    await act(async () => {
      await result.current.markAllRead();
    });

    expect(result.current.notifications).toEqual([]);
    expect(result.current.unreadCount).toBe(0);
  });

  it("calls POST /notifications/read-all", async () => {
    mockGetOk([]);
    mockPostOk();
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });
    await act(async () => {
      await result.current.markAllRead();
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/notifications/read-all");
  });

  it("sets error state when markAllRead fails", async () => {
    mockGetOk([makeNotification("n-1")]);
    mockPostError(500, "Server error");
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });
    await act(async () => {
      await result.current.markAllRead();
    });

    expect(result.current.error).toBe("Server error");
    // Notifications should not be cleared on failure
    expect(result.current.notifications).toHaveLength(1);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// clearError
// ══════════════════════════════════════════════════════════════════════════

describe("useNotifications – clearError", () => {
  beforeEach(() => vi.clearAllMocks());

  it("clears the error state", async () => {
    mockGetError(500, "Oops");
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await result.current.fetchUnread();
    });
    expect(result.current.error).toBe("Oops");

    act(() => {
      result.current.clearError();
    });

    expect(result.current.error).toBeNull();
  });
});
