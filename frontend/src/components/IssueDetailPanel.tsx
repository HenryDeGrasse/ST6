import React, { useEffect, useState, useCallback, useMemo } from "react";
import type { Issue, IssueActivity, IssueDetailResponse, UpdateIssueRequest } from "@weekly-commitments/contracts";
import { IssueActivityType } from "@weekly-commitments/contracts";
import { RcdoPicker } from "./RcdoPicker.js";
import { useRcdo } from "../hooks/useRcdo.js";
import styles from "./IssueDetailPanel.module.css";

export interface PanelMember {
  userId: string;
  displayName: string;
}

export interface IssueDetailPanelProps {
  issueId: string | null;
  onClose: () => void;
  onFetchDetail: (issueId: string) => Promise<IssueDetailResponse | null>;
  onAddComment: (issueId: string, text: string) => Promise<void>;
  onLogTime: (issueId: string, hours: number) => Promise<void>;
  /** General-purpose update (assignee, outcome, effort type, etc.) */
  onUpdate?: (issueId: string, req: UpdateIssueRequest) => Promise<void>;
  /** @deprecated Use onUpdate instead. Kept for backwards compat. */
  onAssign?: (issueId: string, assigneeUserId: string | null) => Promise<void>;
  /** Team members available for assignment */
  teamMembers?: PanelMember[];
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
 * Slide-out panel showing full issue detail with all the same attributes
 * that weekly commitments have: status, effort type, chess priority,
 * estimated hours, assignee, outcome link, and activity log.
 */
export const IssueDetailPanel: React.FC<IssueDetailPanelProps> = ({
  issueId,
  onClose,
  onFetchDetail,
  onAddComment,
  onLogTime,
  onUpdate,
  onAssign,
  teamMembers = [],
}) => {
  const [detail, setDetail] = useState<{ issue: Issue; activities: IssueActivity[] } | null>(null);
  const [loading, setLoading] = useState(false);
  const [comment, setComment] = useState("");
  const [timeEntry, setTimeEntry] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editingOutcome, setEditingOutcome] = useState(false);

  // RCDO tree for outcome picker
  const { tree, searchResults, fetchTree, search, clearSearch } = useRcdo();

  // Fetch RCDO tree on mount
  useEffect(() => {
    void fetchTree();
  }, [fetchTree]);

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
    return () => { cancelled = true; };
  }, [issueId, onFetchDetail]);

  useEffect(() => {
    setEditingOutcome(false);
  }, [issueId]);

  useEffect(() => {
    if (!issueId) return undefined;

    const scrollY = window.scrollY;
    const previousBodyOverflow = document.body.style.overflow;
    const previousBodyPosition = document.body.style.position;
    const previousBodyTop = document.body.style.top;
    const previousBodyWidth = document.body.style.width;
    const previousHtmlOverflow = document.documentElement.style.overflow;

    document.body.style.overflow = "hidden";
    document.body.style.position = "fixed";
    document.body.style.top = `-${scrollY}px`;
    document.body.style.width = "100%";
    document.documentElement.style.overflow = "hidden";

    return () => {
      document.body.style.overflow = previousBodyOverflow;
      document.body.style.position = previousBodyPosition;
      document.body.style.top = previousBodyTop;
      document.body.style.width = previousBodyWidth;
      document.documentElement.style.overflow = previousHtmlOverflow;
      window.scrollTo(0, scrollY);
    };
  }, [issueId]);

  const refreshDetail = useCallback(async () => {
    if (!issueId) return;
    const refreshed = await onFetchDetail(issueId);
    if (refreshed) setDetail(refreshed);
  }, [issueId, onFetchDetail]);

  // Build display name lookup from teamMembers prop
  const memberMap = useMemo(
    () => new Map(teamMembers.map((m) => [m.userId, m.displayName])),
    [teamMembers],
  );

  const issue = detail?.issue;

  // Resolve outcome name from RCDO tree
  const outcomeName = useMemo(() => {
    if (!issue?.outcomeId || !tree) return null;
    for (const rc of tree) {
      for (const obj of rc.objectives ?? []) {
        for (const out of obj.outcomes ?? []) {
          if (out.id === issue.outcomeId) return out.name;
        }
      }
    }
    return null;
  }, [issue?.outcomeId, tree]);

  const canEdit = Boolean(onUpdate);

  const assigneeName = issue?.assigneeUserId
    ? (memberMap.get(issue.assigneeUserId) ?? issue.assigneeUserId)
    : "Unassigned";

  // ── Early return AFTER all hooks ──────────────────────────────────────
  if (!issueId) return null;

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) onClose();
  };

  const handleComment = async () => {
    if (!comment.trim() || !issueId) return;
    setSubmitting(true);
    await onAddComment(issueId, comment.trim());
    setComment("");
    await refreshDetail();
    setSubmitting(false);
  };

  const handleLogTime = async () => {
    const hours = parseFloat(timeEntry);
    if (isNaN(hours) || hours <= 0 || !issueId) return;
    setSubmitting(true);
    await onLogTime(issueId, hours);
    setTimeEntry("");
    await refreshDetail();
    setSubmitting(false);
  };

  const handleFieldUpdate = async (req: UpdateIssueRequest) => {
    if (!issueId) return;
    setSaving(true);
    if (onUpdate) {
      await onUpdate(issueId, req);
    } else if (onAssign && req.assigneeUserId !== undefined) {
      await onAssign(issueId, req.assigneeUserId ?? null);
    }
    await refreshDetail();
    setSaving(false);
  };

  const handleAssigneeChange = async (newUserId: string) => {
    await handleFieldUpdate({ assigneeUserId: newUserId || null });
  };

  const handleOutcomeChange = async (outcomeId: string | null) => {
    await handleFieldUpdate({
      outcomeId: outcomeId,
      nonStrategicReason: outcomeId ? null : undefined,
    });
    setEditingOutcome(false);
  };

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
            {issue && <span className={styles.issueKey}>{issue.issueKey}</span>}
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
            ×
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
                  <span className={styles.metaLabel}>Effort type</span>
                  <span className={styles.metaValue}>
                    {issue.effortType
                      ? issue.effortType.charAt(0) + issue.effortType.slice(1).toLowerCase()
                      : "\u2014"}
                  </span>
                </div>
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Chess priority</span>
                  <span className={styles.metaValue}>{issue.chessPriority ?? "\u2014"}</span>
                </div>
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Estimated hours</span>
                  <span className={styles.metaValue}>
                    {issue.estimatedHours != null ? `${issue.estimatedHours}h` : "\u2014"}
                  </span>
                </div>

                {/* Assignee */}
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Assignee</span>
                  {(onUpdate || onAssign) && teamMembers.length > 0 ? (
                    <select
                      className={styles.assigneeSelect}
                      value={issue.assigneeUserId ?? ""}
                      onChange={(e) => { void handleAssigneeChange(e.target.value); }}
                      disabled={saving}
                      data-testid="issue-assignee-select"
                      aria-label="Assign to"
                    >
                      <option value="">Unassigned</option>
                      {teamMembers.map((m) => (
                        <option key={m.userId} value={m.userId}>
                          {m.displayName}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <span className={styles.metaValue}>{assigneeName}</span>
                  )}
                </div>

                {issue.aiRecommendedRank != null && (
                  <div className={styles.metaItem}>
                    <span className={styles.metaLabel}>AI rank</span>
                    <span className={styles.metaValue}>#{issue.aiRecommendedRank}</span>
                  </div>
                )}
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Created</span>
                  <span className={styles.metaValue}>{formatTime(issue.createdAt)}</span>
                </div>
              </div>

              {/* Outcome link — editable via RcdoPicker or read-only display */}
              <div className={styles.outcomeSection} data-testid="issue-outcome-section">
                <p className={styles.sectionTitle}>Outcome link</p>
                {canEdit ? (
                  <>
                    <div className={styles.outcomeSummaryRow}>
                      <span className={styles.outcomeSummaryValue}>
                        {outcomeName ?? (issue.outcomeId ? issue.outcomeId : "No linked outcome")}
                      </span>
                      <button
                        type="button"
                        className={styles.outcomeActionBtn}
                        onClick={() => setEditingOutcome((v) => !v)}
                        disabled={saving}
                        data-testid="issue-outcome-edit-toggle"
                      >
                        {editingOutcome ? "Cancel" : issue.outcomeId ? "Change" : "Link outcome"}
                      </button>
                    </div>

                    {editingOutcome && (
                      <div className={styles.outcomeEditor}>
                        <RcdoPicker
                          value={issue.outcomeId}
                          onChange={(sel) => { void handleOutcomeChange(sel?.outcomeId ?? null); }}
                          tree={tree}
                          searchResults={searchResults}
                          onSearch={search}
                          onClearSearch={clearSearch}
                          disabled={saving}
                        />
                      </div>
                    )}
                  </>
                ) : (
                  <span className={styles.metaValue}>
                    {outcomeName ?? (issue.outcomeId ? issue.outcomeId : "None")}
                  </span>
                )}
                {issue.nonStrategicReason && !issue.outcomeId && (
                  <p className={styles.nonStrategicNote}>
                    Non-strategic: {issue.nonStrategicReason}
                  </p>
                )}
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
                onClick={() => { void handleComment(); }}
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
                onClick={() => { void handleLogTime(); }}
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
