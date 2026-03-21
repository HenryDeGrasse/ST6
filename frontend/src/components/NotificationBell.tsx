import React, { useCallback, useEffect, useRef, useState } from "react";
import type { NotificationItem } from "@weekly-commitments/contracts";
import { StatusIcon } from "./icons/StatusIcon.js";
import styles from "./NotificationBell.module.css";

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

  const handleEscapeToClose = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Escape" && open) {
        e.preventDefault();
        closeDropdown();
      }
    },
    [closeDropdown, open],
  );

  return (
    <div data-testid="notification-bell" className={styles.wrapper}>
      <button
        ref={bellButtonRef}
        data-testid="notification-bell-btn"
        onClick={() => setOpen(!open)}
        onKeyDown={handleEscapeToClose}
        aria-expanded={open}
        className={styles.bellButton}
        aria-label={`Notifications (${unreadCount} unread)`}
      >
        <StatusIcon icon="bell" size={18} />
        {unreadCount > 0 && (
          <span data-testid="notification-count" className={styles.badge}>
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div data-testid="notification-dropdown" onKeyDown={handleEscapeToClose} className={styles.dropdown}>
          <div className={styles.dropdownHeader}>
            <span className={styles.dropdownTitle}>Notifications</span>
            {unreadCount > 0 && (
              <button
                data-testid="mark-all-read-btn"
                onClick={() => {
                  void onMarkAllRead();
                }}
                className={styles.markAllBtn}
              >
                Mark all read
              </button>
            )}
          </div>

          {notifications.length === 0 && <div className={styles.emptyState}>No new notifications</div>}

          {notifications.map((n) => (
            <div key={n.id} data-testid={`notification-item-${n.id}`} className={styles.notificationItem}>
              <div className={styles.notificationContent}>
                <div className={styles.notificationType}>{formatNotificationType(n.type)}</div>
                {n.type === "WEEKLY_DIGEST" && (
                  <div
                    data-testid={`digest-summary-${n.id}`}
                    className={styles.notificationDigestSummary}
                  >
                    {formatDigestMessage(n.payload)}
                  </div>
                )}
                <div className={styles.notificationTime}>{new Date(n.createdAt).toLocaleString()}</div>
              </div>
              <button
                onClick={() => {
                  void onMarkRead(n.id);
                }}
                className={styles.dismissBtn}
                title="Dismiss"
              >
                <StatusIcon icon="error-x" size={14} />
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
    WEEKLY_DIGEST: "Weekly team digest",
  };
  return labels[type] ?? type.replace(/_/g, " ").toLowerCase();
}

/**
 * Extracts the human-readable summary message from a WEEKLY_DIGEST notification payload.
 * Returns the pre-built `message` field when present, otherwise reconstructs a digest
 * summary from the structured payload fields before falling back to a generic label.
 */
export function formatDigestMessage(payload: Record<string, unknown>): string {
  if (typeof payload.message === "string" && payload.message.trim().length > 0) {
    return payload.message.trim();
  }

  const weekStart = typeof payload.weekStart === "string" ? payload.weekStart : null;
  const totalMembers = readNumericField(payload.totalMemberCount);
  const reconciledCount = readNumericField(payload.reconciledCount);
  const reviewQueueSize = readNumericField(payload.reviewQueueSize);
  const staleCount = readNumericField(payload.staleCount);
  const rcdoAlignmentRate = readNumericField(payload.rcdoAlignmentRate);
  const previousRcdoAlignmentRate = readNumericField(payload.previousRcdoAlignmentRate);
  const doneEarlyCount = readNumericField(payload.doneEarlyCount);

  if (totalMembers === null || reconciledCount === null || rcdoAlignmentRate === null) {
    return "Weekly team digest available";
  }

  const parts = ["Weekly digest"];
  if (weekStart) {
    parts.push(`(w/c ${weekStart})`);
  }

  let summary = `${parts.join(" ")}: ${reconciledCount}/${totalMembers} reconciled`;

  if (reviewQueueSize !== null && reviewQueueSize > 0) {
    summary += `, ${reviewQueueSize} pending review`;
  }
  if (staleCount !== null && staleCount > 0) {
    summary += `, ${staleCount} stale`;
  }

  summary += `, ${Math.round(rcdoAlignmentRate * 100)}% RCDO aligned`;

  if (previousRcdoAlignmentRate !== null) {
    summary += ` (vs ${Math.round(previousRcdoAlignmentRate * 100)}% last week)`;
  }
  if (doneEarlyCount !== null && doneEarlyCount > 0) {
    summary += `, ${doneEarlyCount} done early`;
  }

  return `${summary}.`;
}

function readNumericField(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return null;
}
