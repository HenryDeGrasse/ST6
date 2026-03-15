import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NotificationBell } from "../components/NotificationBell.js";
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
    render(
      <NotificationBell
        {...defaultProps}
        notifications={[]}
        unreadCount={0}
      />,
    );
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
