/**
 * QuickCheckIn – compact 5-second inline check-in form (Wave 2).
 *
 * Rendered as an expandable row inside CommitList when a LOCKED plan's
 * commit has the "Check in" affordance activated.
 *
 * The form has three parts:
 * 1. Status selector – four buttons (On Track / At Risk / Blocked / Done Early)
 * 2. Optional free-text note
 * 3. Submit button
 *
 * Below the form, if there is existing check-in history, it is rendered as
 * an append-only timeline (newest first) so the user can see their progress
 * arc without leaving the page.
 */
import React, { useState } from "react";
import type { CheckInEntry, CheckInRequest } from "@weekly-commitments/contracts";
import { StatusIcon } from "./icons/StatusIcon.js";
import type { StatusIconName } from "./icons/StatusIcon.js";
import styles from "./QuickCheckIn.module.css";

// ─── Types ───────────────────────────────────────────────────────────────────

/** Status values match the CheckInStatus union type from contracts/types.ts */
type CheckInStatus = "ON_TRACK" | "AT_RISK" | "BLOCKED" | "DONE_EARLY";

const STATUS_LABELS: Record<CheckInStatus, string> = {
  ON_TRACK: "On Track",
  AT_RISK: "At Risk",
  BLOCKED: "Blocked",
  DONE_EARLY: "Done Early",
};

const STATUS_ICONS: Record<CheckInStatus, StatusIconName> = {
  ON_TRACK: "check",
  AT_RISK: "warning",
  BLOCKED: "blocked",
  DONE_EARLY: "celebrate",
};

const ALL_STATUSES: CheckInStatus[] = ["ON_TRACK", "AT_RISK", "BLOCKED", "DONE_EARLY"];

// ─── Props ────────────────────────────────────────────────────────────────────

export interface QuickCheckInProps {
  /** The commit ID being checked in against. */
  commitId: string;
  /** Commit title shown above the form for context. */
  commitTitle: string;
  /**
   * Append-only check-in history for this commit (oldest first).
   * Rendered as a timeline below the form.
   */
  entries: CheckInEntry[];
  /** True while an API call is in progress (disables controls). */
  loading: boolean;
  /** Error message to display, or null when no error. */
  error: string | null;
  /**
   * Called when the user submits the form.
   * Returns true when the check-in was saved successfully.
   */
  onCheckIn: (req: CheckInRequest) => Promise<boolean>;
  /** Called when the user clicks the close / collapse button. */
  onClose: () => void;
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatEntryTime(isoString: string): string {
  try {
    return new Date(isoString).toLocaleString(undefined, {
      month: "short",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit",
    });
  } catch {
    return isoString;
  }
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * Compact inline check-in form for a single commit.
 *
 * Designed for a 5-second interaction: tap a status, optionally type a note,
 * hit "Check In".  Renders a history timeline beneath when entries exist.
 */
export const QuickCheckIn: React.FC<QuickCheckInProps> = ({
  commitId,
  commitTitle,
  entries,
  loading,
  error,
  onCheckIn,
  onClose,
}) => {
  const [selectedStatus, setSelectedStatus] = useState<CheckInStatus | null>(null);
  const [note, setNote] = useState("");
  const [lastSaved, setLastSaved] = useState(false);

  const handleSubmit = async () => {
    if (!selectedStatus) return;
    const req: CheckInRequest = {
      status: selectedStatus,
      ...(note.trim() ? { note: note.trim() } : {}),
    };
    const ok = await onCheckIn(req);
    if (ok) {
      setSelectedStatus(null);
      setNote("");
      setLastSaved(true);
    }
  };

  // commitId is part of the interface so the parent can identify which commit
  // this form is associated with; we expose it on the container for testing.
  return (
    <div
      data-testid="quick-check-in"
      data-commit-id={commitId}
      className={styles.container}
    >
      {/* ── Header ── */}
      <div className={styles.header}>
        <span className={styles.headerTitle}>Daily Check-In</span>
        <button
          type="button"
          data-testid="check-in-close"
          className={styles.closeButton}
          onClick={onClose}
          aria-label="Close check-in"
        >
          Close
        </button>
      </div>

      {/* ── Commit context ── */}
      <p data-testid="check-in-commit-title" className={styles.commitTitle}>
        {commitTitle}
      </p>

      {/* ── Status buttons ── */}
      <div
        className={styles.statusRow}
        role="group"
        aria-label="Select check-in status"
      >
        {ALL_STATUSES.map((status) => (
          <button
            key={status}
            type="button"
            data-testid={`check-in-status-${status.toLowerCase()}`}
            className={[
              styles.statusButton,
              selectedStatus === status ? styles.statusButtonSelected : "",
            ]
              .filter(Boolean)
              .join(" ")}
            onClick={() => {
              setSelectedStatus(status);
              setLastSaved(false);
            }}
            aria-pressed={selectedStatus === status}
            disabled={loading}
          >
            <span className={styles.statusEmoji} aria-hidden="true">
              <StatusIcon icon={STATUS_ICONS[status]} size={14} />
            </span>
            <span className={styles.statusLabel}>{STATUS_LABELS[status]}</span>
          </button>
        ))}
      </div>

      {/* ── Note (optional) ── */}
      <textarea
        data-testid="check-in-note"
        className={styles.noteInput}
        placeholder="Optional note (e.g. 'Waiting on code review')"
        value={note}
        onChange={(e) => setNote(e.target.value)}
        maxLength={500}
        rows={2}
        disabled={loading}
        aria-label="Check-in note"
      />

      {/* ── Error ── */}
      {error && (
        <p data-testid="check-in-error" className={styles.errorText} role="alert">
          {error}
        </p>
      )}

      {/* ── Actions ── */}
      <div className={styles.actionRow}>
        <button
          type="button"
          data-testid="check-in-submit"
          className={styles.submitButton}
          onClick={() => void handleSubmit()}
          disabled={!selectedStatus || loading}
          aria-busy={loading}
        >
          {loading ? "Saving…" : "Check In"}
        </button>
        {lastSaved && !error && (
          <span data-testid="check-in-success" className={styles.successText} aria-live="polite">
            Saved
          </span>
        )}
      </div>

      {/* ── History timeline ── */}
      {entries.length > 0 && (
        <div data-testid="check-in-history" className={styles.history}>
          <h4 className={styles.historyTitle}>History</h4>
          <ol className={styles.timeline}>
            {[...entries].reverse().map((entry) => (
              <li
                key={entry.id}
                data-testid={`check-in-entry-${entry.id}`}
                className={styles.timelineItem}
              >
                <span
                  className={[
                    styles.statusDot,
                    styles[`statusDot${entry.status}` as keyof typeof styles] ?? "",
                  ]
                    .filter(Boolean)
                    .join(" ")}
                  aria-hidden="true"
                />
                <div className={styles.timelineContent}>
                  <span
                    data-testid={`check-in-entry-status-${entry.id}`}
                    className={styles.entryStatus}
                  >
                    <StatusIcon icon={STATUS_ICONS[entry.status as CheckInStatus]} size={12} />{" "}
                    {STATUS_LABELS[entry.status as CheckInStatus] ?? entry.status}
                  </span>
                  <span className={styles.entryTime}>{formatEntryTime(entry.createdAt)}</span>
                  {entry.note && (
                    <p
                      data-testid={`check-in-entry-note-${entry.id}`}
                      className={styles.entryNote}
                    >
                      {entry.note}
                    </p>
                  )}
                </div>
              </li>
            ))}
          </ol>
        </div>
      )}
    </div>
  );
};
