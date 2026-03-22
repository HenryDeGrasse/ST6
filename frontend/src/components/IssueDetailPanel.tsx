import React, { useEffect, useState } from "react";
import type { Issue, IssueActivity, IssueDetailResponse } from "@weekly-commitments/contracts";
import { IssueActivityType } from "@weekly-commitments/contracts";
import styles from "./IssueDetailPanel.module.css";

export interface IssueDetailPanelProps {
  issueId: string | null;
  onClose: () => void;
  onFetchDetail: (issueId: string) => Promise<IssueDetailResponse | null>;
  onAddComment: (issueId: string, text: string) => Promise<void>;
  onLogTime: (issueId: string, hours: number) => Promise<void>;
}

function formatTime(dateStr: string): string {
  try {
    return new Date(dateStr).toLocaleString("en-US", {
      month: "short",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit",
    });
  } catch {
    return dateStr;
  }
}

function activityLabel(a: IssueActivity): string {
  switch (a.activityType) {
    case IssueActivityType.CREATED:
      return "Issue created";
    case IssueActivityType.STATUS_CHANGE:
      return `Status changed to ${a.newValue ?? ""}`;
    case IssueActivityType.COMMENT:
      return a.commentText ?? "(comment)";
    case IssueActivityType.TIME_ENTRY:
      return `Logged ${a.hoursLogged ?? 0}h`;
    case IssueActivityType.ASSIGNMENT_CHANGE:
      return `Assignee changed to ${a.newValue ?? "(unassigned)"}`;
    case IssueActivityType.PRIORITY_CHANGE:
      return `Priority changed to ${a.newValue ?? ""}`;
    case IssueActivityType.EFFORT_TYPE_CHANGE:
      return `Effort type changed to ${a.newValue ?? ""}`;
    case IssueActivityType.COMMITTED_TO_WEEK:
      return `Committed to week of ${a.newValue ?? ""}`;
    case IssueActivityType.RELEASED_TO_BACKLOG:
      return "Released back to backlog";
    case IssueActivityType.CARRIED_FORWARD:
      return "Carried forward";
    default:
      return a.activityType.replace(/_/g, " ").toLowerCase();
  }
}

function statusClass(status: string): string {
  switch (status) {
    case "OPEN":        return styles.statusOpen;
    case "IN_PROGRESS": return styles.statusInProgress;
    case "DONE":        return styles.statusDone;
    case "ARCHIVED":    return styles.statusArchived;
    default:            return "";
  }
}

/**
 * Slide-out panel showing full issue detail, activity log, comment input,
 * and time entry input.
 */
export const IssueDetailPanel: React.FC<IssueDetailPanelProps> = ({
  issueId,
  onClose,
  onFetchDetail,
  onAddComment,
  onLogTime,
}) => {
  const [detail, setDetail] = useState<{ issue: Issue; activities: IssueActivity[] } | null>(null);
  const [loading, setLoading] = useState(false);
  const [comment, setComment] = useState("");
  const [timeEntry, setTimeEntry] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!issueId) {
      setDetail(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    onFetchDetail(issueId).then((d) => {
      if (!cancelled && d) setDetail(d);
      if (!cancelled) setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [issueId, onFetchDetail]);

  if (!issueId) return null;

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) onClose();
  };

  const handleComment = async () => {
    if (!comment.trim() || !issueId) return;
    setSubmitting(true);
    await onAddComment(issueId, comment.trim());
    setComment("");
    // Refresh
    const refreshed = await onFetchDetail(issueId);
    if (refreshed) setDetail(refreshed);
    setSubmitting(false);
  };

  const handleLogTime = async () => {
    const hours = parseFloat(timeEntry);
    if (isNaN(hours) || hours <= 0 || !issueId) return;
    setSubmitting(true);
    await onLogTime(issueId, hours);
    setTimeEntry("");
    const refreshed = await onFetchDetail(issueId);
    if (refreshed) setDetail(refreshed);
    setSubmitting(false);
  };

  const issue = detail?.issue;

  return (
    <div
      className={styles.overlay}
      onClick={handleOverlayClick}
      data-testid="issue-detail-overlay"
    >
      <div className={styles.panel} data-testid="issue-detail-panel" role="dialog" aria-modal="true">
        {/* Header */}
        <div className={styles.header}>
          <div className={styles.headerLeft}>
            {issue && (
              <span className={styles.issueKey}>{issue.issueKey}</span>
            )}
            <span className={styles.issueTitle}>
              {loading ? "Loading…" : issue?.title ?? "Issue Detail"}
            </span>
          </div>
          <button
            type="button"
            className={styles.closeBtn}
            onClick={onClose}
            aria-label="Close issue detail"
            data-testid="issue-detail-close"
          >
            ✕
          </button>
        </div>

        {/* Body */}
        <div className={styles.body}>
          {loading && <p>Loading issue details…</p>}

          {!loading && issue && (
            <>
              {/* Metadata grid */}
              <div className={styles.metaGrid}>
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Status</span>
                  <span className={`${styles.statusBadge} ${statusClass(issue.status)}`}>
                    {issue.status.replace("_", " ")}
                  </span>
                </div>
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Effort Type</span>
                  <span className={styles.metaValue}>
                    {issue.effortType
                      ? issue.effortType.charAt(0) + issue.effortType.slice(1).toLowerCase()
                      : "—"}
                  </span>
                </div>
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Chess Priority</span>
                  <span className={styles.metaValue}>{issue.chessPriority ?? "—"}</span>
                </div>
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Estimated Hours</span>
                  <span className={styles.metaValue}>
                    {issue.estimatedHours != null ? `${issue.estimatedHours}h` : "—"}
                  </span>
                </div>
                {issue.aiRecommendedRank != null && (
                  <div className={styles.metaItem}>
                    <span className={styles.metaLabel}>AI Rank</span>
                    <span className={styles.metaValue}>#{issue.aiRecommendedRank}</span>
                  </div>
                )}
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Created</span>
                  <span className={styles.metaValue}>{formatTime(issue.createdAt)}</span>
                </div>
              </div>

              {/* Description */}
              {issue.description && (
                <div>
                  <p className={styles.sectionTitle}>Description</p>
                  <p className={styles.description}>{issue.description}</p>
                </div>
              )}

              {/* Activity log */}
              <div>
                <p className={styles.sectionTitle}>Activity</p>
                <div className={styles.activityList}>
                  {(detail?.activities ?? []).length === 0 && (
                    <p style={{ fontSize: "0.8rem", color: "var(--wc-color-text-muted)" }}>
                      No activity yet.
                    </p>
                  )}
                  {(detail?.activities ?? []).map((a) => (
                    <div key={a.id} className={styles.activityItem}>
                      <span className={styles.activityTime}>{formatTime(a.createdAt)}</span>
                      <span
                        className={
                          a.activityType === IssueActivityType.COMMENT
                            ? styles.commentText
                            : styles.activityText
                        }
                      >
                        {activityLabel(a)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}
        </div>

        {/* Footer — comment + time entry */}
        {issue && (
          <div className={styles.footer}>
            <div className={styles.inputRow}>
              <input
                type="text"
                className="wc-input"
                placeholder="Add a comment…"
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                disabled={submitting}
                data-testid="issue-comment-input"
              />
              <button
                type="button"
                className="wc-button"
                onClick={handleComment}
                disabled={submitting || !comment.trim()}
                data-testid="issue-comment-submit"
              >
                Post
              </button>
            </div>

            <div className={styles.inputRow}>
              <input
                type="number"
                className="wc-input"
                placeholder="Log time (hours)…"
                value={timeEntry}
                onChange={(e) => setTimeEntry(e.target.value)}
                min="0"
                step="0.25"
                disabled={submitting}
                data-testid="issue-time-input"
              />
              <button
                type="button"
                className="wc-button-secondary"
                onClick={handleLogTime}
                disabled={submitting || !timeEntry}
                data-testid="issue-time-submit"
              >
                Log
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
