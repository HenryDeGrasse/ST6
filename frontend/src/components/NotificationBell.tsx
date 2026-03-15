import React, { useCallback, useEffect, useRef, useState } from "react";
import type { NotificationItem } from "@weekly-commitments/contracts";

export interface NotificationBellProps {
  notifications: NotificationItem[];
  unreadCount: number;
  onMarkRead: (id: string) => Promise<void>;
  onMarkAllRead: () => Promise<void>;
  onFetchUnread: () => Promise<void>;
}

/**
 * In-app notification bell with dropdown for unread notifications.
 * MVP renders on page load by querying the notifications table (PRD §4).
 */
export const NotificationBell: React.FC<NotificationBellProps> = ({
  notifications,
  unreadCount,
  onMarkRead,
  onMarkAllRead,
  onFetchUnread,
}) => {
  const [open, setOpen] = useState(false);
  const bellButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    void onFetchUnread();
  }, [onFetchUnread]);

  const closeDropdown = useCallback(() => {
    setOpen(false);
    bellButtonRef.current?.focus();
  }, []);

  const handleEscapeToClose = useCallback((e: React.KeyboardEvent) => {
    if (e.key === "Escape" && open) {
      e.preventDefault();
      closeDropdown();
    }
  }, [closeDropdown, open]);

  return (
    <div data-testid="notification-bell" style={{ position: "relative", display: "inline-block" }}>
      <button
        ref={bellButtonRef}
        data-testid="notification-bell-btn"
        onClick={() => setOpen(!open)}
        onKeyDown={handleEscapeToClose}
        aria-expanded={open}
        style={{
          background: "none",
          border: "1px solid #ddd",
          borderRadius: "50%",
          width: "36px",
          height: "36px",
          cursor: "pointer",
          position: "relative",
          fontSize: "1.2rem",
        }}
        aria-label={`Notifications (${unreadCount} unread)`}
      >
        🔔
        {unreadCount > 0 && (
          <span
            data-testid="notification-count"
            style={{
              position: "absolute",
              top: "-4px",
              right: "-4px",
              background: "#dc2626",
              color: "white",
              borderRadius: "50%",
              width: "18px",
              height: "18px",
              fontSize: "0.7rem",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div
          data-testid="notification-dropdown"
          onKeyDown={handleEscapeToClose}
          style={{
            position: "absolute",
            top: "40px",
            right: 0,
            width: "320px",
            maxHeight: "400px",
            overflowY: "auto",
            background: "white",
            border: "1px solid #ddd",
            borderRadius: "8px",
            boxShadow: "0 4px 12px rgba(0,0,0,0.1)",
            zIndex: 100,
          }}
        >
          <div style={{ padding: "0.75rem", borderBottom: "1px solid #eee", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <strong>Notifications</strong>
            {unreadCount > 0 && (
              <button
                data-testid="mark-all-read-btn"
                onClick={() => { void onMarkAllRead(); }}
                style={{ fontSize: "0.75rem", color: "#2563eb", background: "none", border: "none", cursor: "pointer" }}
              >
                Mark all read
              </button>
            )}
          </div>

          {notifications.length === 0 && (
            <div style={{ padding: "1rem", textAlign: "center", color: "#888" }}>
              No new notifications
            </div>
          )}

          {notifications.map((n) => (
            <div
              key={n.id}
              data-testid={`notification-item-${n.id}`}
              style={{
                padding: "0.75rem",
                borderBottom: "1px solid #f3f4f6",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "flex-start",
              }}
            >
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: "bold", fontSize: "0.875rem" }}>
                  {formatNotificationType(n.type)}
                </div>
                <div style={{ fontSize: "0.75rem", color: "#6b7280", marginTop: "0.25rem" }}>
                  {new Date(n.createdAt).toLocaleString()}
                </div>
              </div>
              <button
                onClick={() => { void onMarkRead(n.id); }}
                style={{
                  fontSize: "0.7rem",
                  color: "#6b7280",
                  background: "none",
                  border: "none",
                  cursor: "pointer",
                  marginLeft: "0.5rem",
                }}
                title="Dismiss"
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

function formatNotificationType(type: string): string {
  const labels: Record<string, string> = {
    PLAN_STILL_DRAFT: "Your plan is still in draft",
    PLAN_STILL_LOCKED: "Time to reconcile your week",
    RECONCILIATION_OVERDUE: "Reconciliation is overdue",
    RECONCILIATION_SUBMITTED: "Reconciliation submitted for review",
    CHANGES_REQUESTED: "Manager requested changes",
  };
  return labels[type] ?? type.replace(/_/g, " ").toLowerCase();
}
