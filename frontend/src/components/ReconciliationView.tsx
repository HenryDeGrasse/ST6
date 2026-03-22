import React, { useEffect, useRef, useState } from "react";
import type { WeeklyCommit, WeeklyAssignmentWithActual, UpdateActualRequest } from "@weekly-commitments/contracts";
import { CompletionStatus, PlanState } from "@weekly-commitments/contracts";
import { StatusIcon, type StatusIconName } from "./icons/index.js";
import styles from "./ReconciliationView.module.css";

export interface ActualEntry {
  commitId: string;
  actualResult: string;
  completionStatus: CompletionStatus | null;
  deltaReason: string;
  actualHours: number | null;
}

export interface ReconciliationViewProps {
  commits: WeeklyCommit[];
  /** Phase 6: assignment-based plan items (optional). */
  assignments?: WeeklyAssignmentWithActual[];
  planState: PlanState;
  onUpdateActual: (commitId: string, version: number, req: UpdateActualRequest) => Promise<void> | void;
  /** Phase 6: called when the user chooses to release an issue back to the backlog. */
  onReleaseToBacklog?: (issueId: string) => void | Promise<void>;
  onSubmit: () => void;
  loading?: boolean;
}

/** Plain text labels for <option> elements (SVG not supported inside <option>). */
const STATUS_LABELS: Record<CompletionStatus, string> = {
  [CompletionStatus.DONE]: "Done",
  [CompletionStatus.PARTIALLY]: "Partially",
  [CompletionStatus.NOT_DONE]: "Not Done",
  [CompletionStatus.DROPPED]: "Dropped",
};

/** Status-tinted card modifier classes keyed by CompletionStatus. */
const STATUS_CARD_CLASS: Record<CompletionStatus, string> = {
  [CompletionStatus.DONE]: styles.commitCardDone,
  [CompletionStatus.PARTIALLY]: styles.commitCardPartially,
  [CompletionStatus.NOT_DONE]: styles.commitCardNotDone,
  [CompletionStatus.DROPPED]: styles.commitCardDropped,
};

const STATUS_BADGE_CLASS: Record<CompletionStatus, string> = {
  [CompletionStatus.DONE]: styles.statusBadgeDone,
  [CompletionStatus.PARTIALLY]: styles.statusBadgePartially,
  [CompletionStatus.NOT_DONE]: styles.statusBadgeNotDone,
  [CompletionStatus.DROPPED]: styles.statusBadgeDropped,
};

const STATUS_ICONS: Record<CompletionStatus, StatusIconName> = {
  [CompletionStatus.DONE]: "check",
  [CompletionStatus.PARTIALLY]: "partial",
  [CompletionStatus.NOT_DONE]: "error-x",
  [CompletionStatus.DROPPED]: "trash",
};

function buildActualEntry(commit: WeeklyCommit): ActualEntry {
  return {
    commitId: commit.id,
    actualResult: commit.actual?.actualResult ?? "",
    completionStatus: commit.actual?.completionStatus ?? CompletionStatus.DONE,
    deltaReason: commit.actual?.deltaReason ?? "",
    actualHours: commit.actual?.actualHours ?? null,
  };
}

function buildActualSignature(commit: WeeklyCommit): string {
  return JSON.stringify({
    version: commit.version,
    actualResult: commit.actual?.actualResult ?? "",
    completionStatus: commit.actual?.completionStatus ?? CompletionStatus.DONE,
    deltaReason: commit.actual?.deltaReason ?? "",
    actualHours: commit.actual?.actualHours ?? null,
  });
}

/**
 * Reconciliation view: for each commit, user provides completion status,
 * actual result, and delta reason (required for non-DONE items).
 */
export const ReconciliationView: React.FC<ReconciliationViewProps> = ({
  commits,
  assignments = [],
  planState,
  onUpdateActual,
  onReleaseToBacklog,
  onSubmit,
  loading = false,
}) => {
  const [releasingIds, setReleasingIds] = useState<Set<string>>(new Set());
  const isReconciling = planState === PlanState.RECONCILING;

  // Per-commit saving state
  const [savingCommits, setSavingCommits] = useState<Record<string, boolean>>({});

  // Local state for each commit's actual data, pre-populated from saved actuals
  const [actuals, setActuals] = useState<Record<string, ActualEntry>>(() => {
    const init: Record<string, ActualEntry> = {};
    for (const commit of commits) {
      init[commit.id] = buildActualEntry(commit);
    }
    return init;
  });
  const serverActualSignaturesRef = useRef<Record<string, string>>(
    Object.fromEntries(commits.map((commit) => [commit.id, buildActualSignature(commit)])),
  );

  useEffect(() => {
    const nextServerSignatures: Record<string, string> = {};

    setActuals((prev) => {
      const next: Record<string, ActualEntry> = {};
      for (const commit of commits) {
        const serverSignature = buildActualSignature(commit);
        nextServerSignatures[commit.id] = serverSignature;
        next[commit.id] =
          !prev[commit.id] || serverActualSignaturesRef.current[commit.id] !== serverSignature
            ? buildActualEntry(commit)
            : prev[commit.id];
      }
      return next;
    });

    serverActualSignaturesRef.current = nextServerSignatures;
  }, [commits]);

  const updateActualField = (
    commitId: string,
    field: keyof ActualEntry,
    value: string | CompletionStatus | number | null,
  ) => {
    setActuals((prev) => ({
      ...prev,
      [commitId]: {
        commitId,
        actualResult: prev[commitId]?.actualResult ?? "",
        completionStatus: prev[commitId]?.completionStatus ?? CompletionStatus.DONE,
        deltaReason: prev[commitId]?.deltaReason ?? "",
        actualHours: prev[commitId]?.actualHours ?? null,
        [field]: value,
      },
    }));
  };

  const handleSaveActual = async (commit: WeeklyCommit) => {
    const entry = actuals[commit.id];
    if (!entry.completionStatus) return;

    const req: UpdateActualRequest = {
      actualResult: entry.actualResult,
      completionStatus: entry.completionStatus,
      ...(entry.completionStatus !== CompletionStatus.DONE && entry.deltaReason
        ? { deltaReason: entry.deltaReason }
        : {}),
      ...(entry.actualHours !== null ? { actualHours: entry.actualHours } : {}),
    };

    setSavingCommits((prev) => ({ ...prev, [commit.id]: true }));
    try {
      await onUpdateActual(commit.id, commit.version, req);
    } finally {
      setSavingCommits((prev) => ({ ...prev, [commit.id]: false }));
    }
  };

  // Validate all commits have status (and delta reason if non-DONE) for submit
  const allComplete = commits.every((commit) => {
    const entry = actuals[commit.id] ?? buildActualEntry(commit);
    if (entry.completionStatus !== CompletionStatus.DONE && !entry.deltaReason.trim()) return false;
    return true;
  });
  const isSubmitDisabled = !allComplete || loading;

  if (!isReconciling) {
    return null;
  }

  return (
    <div data-testid="reconciliation-view" className={styles.container}>
      <h3 className={styles.heading}>Reconciliation</h3>
      <p className={styles.description}>Review each commitment and mark what actually happened this week.</p>

      {commits.map((commit) => {
        const entry = actuals[commit.id] ?? buildActualEntry(commit);
        const needsDelta = entry.completionStatus !== null && entry.completionStatus !== CompletionStatus.DONE;
        const statusClass = entry.completionStatus ? STATUS_CARD_CLASS[entry.completionStatus] : "";
        const statusBadgeClass = entry.completionStatus ? STATUS_BADGE_CLASS[entry.completionStatus] : "";
        const statusIcon = entry.completionStatus ? STATUS_ICONS[entry.completionStatus] : null;

        return (
          <div
            key={commit.id}
            data-testid={`reconcile-commit-${commit.id}`}
            className={`${styles.commitCard} ${statusClass}`}
          >
            <div className={styles.cardHeader}>
              <strong className={styles.cardTitle}>{commit.title}</strong>
              {commit.chessPriority && <span className={styles.chessBadge}>{commit.chessPriority}</span>}
              {entry.completionStatus && statusIcon && (
                <span className={`${styles.statusBadge} ${statusBadgeClass}`}>
                  <StatusIcon icon={statusIcon} size={14} />
                  {STATUS_LABELS[entry.completionStatus]}
                </span>
              )}
            </div>
            {commit.expectedResult && <div className={styles.expectedResult}>Expected: {commit.expectedResult}</div>}

            {/* Completion status */}
            <div className={styles.statusRow}>
              <label htmlFor={`reconcile-status-select-${commit.id}`} className={styles.statusLabel}>
                Status:
              </label>
              <select
                id={`reconcile-status-select-${commit.id}`}
                data-testid={`reconcile-status-${commit.id}`}
                value={entry.completionStatus ?? ""}
                onChange={(e) =>
                  updateActualField(
                    commit.id,
                    "completionStatus",
                    e.target.value ? (e.target.value as CompletionStatus) : null,
                  )
                }
                className={styles.statusSelect}
              >
                <option value="">Select…</option>
                {Object.values(CompletionStatus).map((s) => (
                  <option key={s} value={s}>
                    {STATUS_LABELS[s]}
                  </option>
                ))}
              </select>
            </div>

            {/* Actual result */}
            <div className={styles.fieldRow}>
              <textarea
                data-testid={`reconcile-actual-${commit.id}`}
                placeholder="What actually happened?"
                aria-label={`Actual result for ${commit.title}`}
                value={entry.actualResult}
                onChange={(e) => updateActualField(commit.id, "actualResult", e.target.value)}
                rows={2}
                className={styles.textarea}
              />
            </div>

            {/* Delta reason (required if not DONE) */}
            {needsDelta && (
              <div className={styles.fieldRow}>
                <textarea
                  data-testid={`reconcile-delta-${commit.id}`}
                  placeholder="Why wasn't this completed? (required)"
                  aria-label={`Delta reason for ${commit.title}`}
                  value={entry.deltaReason}
                  onChange={(e) => updateActualField(commit.id, "deltaReason", e.target.value)}
                  rows={2}
                  className={`${styles.textarea} ${entry.deltaReason.trim() ? "" : styles.textareaError}`}
                />
              </div>
            )}

            {/* Actual hours spent */}
            <div className={styles.fieldRow}>
              <input
                data-testid={`reconcile-actual-hours-${commit.id}`}
                type="number"
                step="0.5"
                min="0"
                max="100"
                placeholder="Actual hours (optional)"
                aria-label={`Actual hours for ${commit.title}`}
                value={entry.actualHours ?? ""}
                onChange={(e) =>
                  updateActualField(commit.id, "actualHours", e.target.value ? Number(e.target.value) : null)
                }
                className={styles.input}
              />
            </div>

            <button
              data-testid={`reconcile-save-${commit.id}`}
              onClick={() => {
                void handleSaveActual(commit);
              }}
              disabled={savingCommits[commit.id]}
              className={styles.saveButton}
            >
              {savingCommits[commit.id] ? (
                <>
                  <StatusIcon icon="loading" size={14} />
                  Saving…
                </>
              ) : (
                "Save Actual"
              )}
            </button>
          </div>
        );
      })}

      {/* ── Phase 6: Assignment-based items with Release to Backlog option ── */}
      {assignments.length > 0 && (
        <div data-testid="reconcile-assignments-section">
          <h4 className={styles.heading} style={{ marginTop: "1.25rem", fontSize: "0.95rem" }}>
            Backlog Issues
          </h4>
          {assignments.map((assignment) => {
            const issue = assignment.issue;
            const isReleasing = releasingIds.has(assignment.id);
            return (
              <div
                key={assignment.id}
                data-testid={`reconcile-assignment-${assignment.id}`}
                className={styles.commitCard}
              >
                <div className={styles.cardHeader}>
                  <strong className={styles.cardTitle}>
                    {issue ? `${issue.issueKey}: ${issue.title}` : `Assignment ${assignment.id}`}
                  </strong>
                  {(assignment.chessPriorityOverride ?? issue?.chessPriority) && (
                    <span className={styles.chessBadge}>
                      {assignment.chessPriorityOverride ?? issue?.chessPriority}
                    </span>
                  )}
                </div>
                {onReleaseToBacklog && issue && (
                  <div className={styles.statusRow} style={{ marginTop: "0.5rem" }}>
                    <button
                      data-testid={`release-to-backlog-${assignment.id}`}
                      className={styles.saveButton}
                      onClick={() => {
                        setReleasingIds((prev) => new Set([...prev, assignment.id]));
                        void Promise.resolve(onReleaseToBacklog(issue.id)).finally(() => {
                          setReleasingIds((prev) => {
                            const next = new Set(prev);
                            next.delete(assignment.id);
                            return next;
                          });
                        });
                      }}
                      disabled={isReleasing}
                      style={{ background: "rgba(248,113,113,0.15)", borderColor: "rgba(248,113,113,0.3)" }}
                    >
                      {isReleasing ? "Releasing…" : "Release to Backlog"}
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      <div className={styles.submitRow}>
        <button
          data-testid="reconcile-submit"
          onClick={onSubmit}
          disabled={isSubmitDisabled}
          className={`${styles.submitButton} ${
            isSubmitDisabled ? styles.submitButtonDisabled : styles.submitButtonEnabled
          }`}
        >
          Submit Reconciliation
        </button>
        {!allComplete && <span className={styles.incompleteHint}>Complete all commitments to submit.</span>}
      </div>
    </div>
  );
};
