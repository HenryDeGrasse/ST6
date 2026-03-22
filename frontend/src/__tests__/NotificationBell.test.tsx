import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  NotificationBell,
  formatDigestMessage,
  formatDraftReadyMessage,
  formatMisalignmentMessage,
} from "../components/NotificationBell.js";
import type { NotificationItem } from "@weekly-commitments/contracts";

describe("NotificationBell", () => {
  const mockNotification: NotificationItem = {
    id: "n1",
    type: "RECONCILIATION_SUBMITTED",
    payload: { name: "Alice" },
    read: false,
    createdAt: "2026-03-12T10:00:00Z",
  };

  const defaultProps = {
    notifications: [mockNotification],
    unreadCount: 1,
    onMarkRead: vi.fn().mockResolvedValue(undefined),
    onMarkAllRead: vi.fn().mockResolvedValue(undefined),
    onFetchUnread: vi.fn().mockResolvedValue(undefined),
  };

  it("renders the bell with unread count", () => {
    render(<NotificationBell {...defaultProps} />);
    expect(screen.getByTestId("notification-bell")).toBeInTheDocument();
    expect(screen.getByTestId("notification-count")).toHaveTextContent("1");
  });

  it("does not show count badge when no unread", () => {
    render(<NotificationBell {...defaultProps} notifications={[]} unreadCount={0} />);
    expect(screen.queryByTestId("notification-count")).not.toBeInTheDocument();
  });

  it("opens dropdown on click", async () => {
    render(<NotificationBell {...defaultProps} />);
    expect(screen.queryByTestId("notification-dropdown")).not.toBeInTheDocument();

    await userEvent.click(screen.getByTestId("notification-bell-btn"));

    expect(screen.getByTestId("notification-dropdown")).toBeInTheDocument();
    expect(screen.getByText("Reconciliation submitted for review")).toBeInTheDocument();
  });

  it("calls onFetchUnread on mount", () => {
    render(<NotificationBell {...defaultProps} />);
    expect(defaultProps.onFetchUnread).toHaveBeenCalled();
  });

  // --- Accessibility tests ---

  it("sets aria-expanded on the bell button", async () => {
    render(<NotificationBell {...defaultProps} />);
    const btn = screen.getByTestId("notification-bell-btn");
    expect(btn).toHaveAttribute("aria-expanded", "false");

    await userEvent.click(btn);
    expect(btn).toHaveAttribute("aria-expanded", "true");

    await userEvent.click(btn);
    expect(btn).toHaveAttribute("aria-expanded", "false");
  });

  it("closes dropdown on Escape and returns focus to bell button", async () => {
    render(<NotificationBell {...defaultProps} />);
    const btn = screen.getByTestId("notification-bell-btn");

    await userEvent.click(btn);
    expect(screen.getByTestId("notification-dropdown")).toBeInTheDocument();

    // Focus something inside the dropdown, then press Escape
    const markAllBtn = screen.getByTestId("mark-all-read-btn");
    markAllBtn.focus();
    await userEvent.keyboard("{Escape}");

    expect(screen.queryByTestId("notification-dropdown")).not.toBeInTheDocument();
    expect(document.activeElement).toBe(btn);
  });

  it("closes the open dropdown when Escape is pressed on the bell button", async () => {
    render(<NotificationBell {...defaultProps} />);
    const btn = screen.getByTestId("notification-bell-btn");

    await userEvent.click(btn);
    expect(screen.getByTestId("notification-dropdown")).toBeInTheDocument();

    btn.focus();
    await userEvent.keyboard("{Escape}");

    expect(screen.queryByTestId("notification-dropdown")).not.toBeInTheDocument();
    expect(document.activeElement).toBe(btn);
  });
});

// ─── WEEKLY_DIGEST notification rendering ──────────────────────────────────────

describe("WEEKLY_DIGEST notification rendering", () => {
  const digestNotification: NotificationItem = {
    id: "digest-1",
    type: "WEEKLY_DIGEST",
    payload: {
      weekStart: "2026-03-16",
      totalMemberCount: 5,
      reconciledCount: 4,
      reviewQueueSize: 2,
      staleCount: 0,
      rcdoAlignmentRate: 0.85,
      previousRcdoAlignmentRate: 0.78,
      doneEarlyCount: 1,
      message:
        "Weekly digest (w/c 2026-03-16): 4/5 reconciled, 2 pending review, 85% RCDO aligned (vs 78% last week), 1 done early.",
    },
    read: false,
    createdAt: "2026-03-20T17:00:00Z",
  };

  const digestProps = {
    notifications: [digestNotification],
    unreadCount: 1,
    onMarkRead: vi.fn().mockResolvedValue(undefined),
    onMarkAllRead: vi.fn().mockResolvedValue(undefined),
    onFetchUnread: vi.fn().mockResolvedValue(undefined),
  };

  it("renders WEEKLY_DIGEST label in the dropdown", async () => {
    render(<NotificationBell {...digestProps} />);
    await userEvent.click(screen.getByTestId("notification-bell-btn"));

    expect(screen.getByText("Weekly team digest")).toBeInTheDocument();
  });

  it("renders the digest summary message from the payload", async () => {
    render(<NotificationBell {...digestProps} />);
    await userEvent.click(screen.getByTestId("notification-bell-btn"));

    expect(screen.getByTestId("digest-summary-digest-1")).toBeInTheDocument();
    expect(screen.getByTestId("digest-summary-digest-1")).toHaveTextContent("4/5 reconciled");
  });

  it("does not render a digest-summary element for non-digest notifications", async () => {
    const regularNotification: NotificationItem = {
      id: "n-regular",
      type: "CHANGES_REQUESTED",
      payload: { message: "Changes needed" },
      read: false,
      createdAt: "2026-03-18T10:00:00Z",
    };
    render(
      <NotificationBell
        {...digestProps}
        notifications={[regularNotification]}
      />,
    );
    await userEvent.click(screen.getByTestId("notification-bell-btn"));

    expect(screen.queryByTestId("digest-summary-n-regular")).not.toBeInTheDocument();
  });
});

// ─── formatDigestMessage unit tests ────────────────────────────────────────────

describe("Phase 5 notification rendering", () => {
  it("renders draft-ready summaries", async () => {
    const notification: NotificationItem = {
      id: "draft-1",
      type: "WEEKLY_PLAN_DRAFT_READY",
      payload: {
        suggestedCommitCount: 3,
        suggestedHours: "18",
        capacityHours: "24",
      },
      read: false,
      createdAt: "2026-03-20T17:00:00Z",
    };

    render(
      <NotificationBell
        notifications={[notification]}
        unreadCount={1}
        onMarkRead={vi.fn().mockResolvedValue(undefined)}
        onMarkAllRead={vi.fn().mockResolvedValue(undefined)}
        onFetchUnread={vi.fn().mockResolvedValue(undefined)}
      />,
    );

    await userEvent.click(screen.getByTestId("notification-bell-btn"));
    expect(screen.getByTestId("draft-ready-summary-draft-1")).toHaveTextContent("3 suggested commits");
  });

  it("renders misalignment summaries", async () => {
    const notification: NotificationItem = {
      id: "brief-1",
      type: "PLAN_MISALIGNMENT_BRIEFING",
      payload: {
        teamName: "Platform",
        concernCount: 2,
        overloadedMembers: ["Ava"],
        urgentOutcomesNeedingAttention: ["Outcome 1", "Outcome 2"],
      },
      read: false,
      createdAt: "2026-03-20T17:00:00Z",
    };

    render(
      <NotificationBell
        notifications={[notification]}
        unreadCount={1}
        onMarkRead={vi.fn().mockResolvedValue(undefined)}
        onMarkAllRead={vi.fn().mockResolvedValue(undefined)}
        onFetchUnread={vi.fn().mockResolvedValue(undefined)}
      />,
    );

    await userEvent.click(screen.getByTestId("notification-bell-btn"));
    expect(screen.getByTestId("misalignment-summary-brief-1")).toHaveTextContent("Platform has 2 planning concerns");
  });
});

describe("formatDigestMessage", () => {
  it("returns the message field when present and non-empty", () => {
    const result = formatDigestMessage({ message: "Team is 80% on track." });
    expect(result).toBe("Team is 80% on track.");
  });

  it("builds a digest summary from structured payload fields when message is absent", () => {
    const result = formatDigestMessage({
      weekStart: "2026-03-16",
      totalMemberCount: 5,
      reconciledCount: 4,
      reviewQueueSize: 2,
      staleCount: 1,
      rcdoAlignmentRate: 0.85,
      previousRcdoAlignmentRate: 0.78,
      doneEarlyCount: 1,
    });
    expect(result).toBe(
      "Weekly digest (w/c 2026-03-16): 4/5 reconciled, 2 pending review, 1 stale, 85% RCDO aligned (vs 78% last week), 1 done early.",
    );
  });

  it("returns fallback when message field is absent and payload lacks summary metrics", () => {
    const result = formatDigestMessage({ weekStart: "2026-03-16" });
    expect(result).toBe("Weekly team digest available");
  });

  it("returns fallback when message field is an empty string", () => {
    const result = formatDigestMessage({ message: "" });
    expect(result).toBe("Weekly team digest available");
  });

  it("returns fallback when payload is empty", () => {
    const result = formatDigestMessage({});
    expect(result).toBe("Weekly team digest available");
  });
});

describe("Phase 5 notification formatters", () => {
  it("builds draft-ready summaries", () => {
    expect(
      formatDraftReadyMessage({
        suggestedCommitCount: 2,
        suggestedHours: "12",
        capacityHours: "20",
        weekStartDate: "2026-03-16",
      }),
    ).toBe("Draft ready with 2 suggested commits covering 12h against 20h capacity for week of 2026-03-16.");
  });

  it("builds misalignment summaries", () => {
    expect(
      formatMisalignmentMessage({
        teamName: "Revenue",
        concernCount: 3,
        overloadedMembers: ["A", "B"],
        urgentOutcomesNeedingAttention: ["O1"],
      }),
    ).toBe("Revenue has 3 planning concerns, including 2 overloaded members and 1 urgent outcome needing attention.");
  });
});
