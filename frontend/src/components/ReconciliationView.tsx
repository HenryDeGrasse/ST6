import React, { useEffect, useState } from "react";
import type {
  WeeklyCommit,
  UpdateActualRequest,
} from "@weekly-commitments/contracts";
import { CompletionStatus, PlanState } from "@weekly-commitments/contracts";

export interface ActualEntry {
  commitId: string;
  actualResult: string;
  completionStatus: CompletionStatus | null;
  deltaReason: string;
}

export interface ReconciliationViewProps {
  commits: WeeklyCommit[];
  planState: PlanState;
  onUpdateActual: (commitId: string, version: number, req: UpdateActualRequest) => Promise<void> | void;
  onSubmit: () => void;
  loading?: boolean;
}

const STATUS_LABELS: Record<CompletionStatus, string> = {
  [CompletionStatus.DONE]: "✅ Done",
  [CompletionStatus.PARTIALLY]: "🔶 Partially",
  [CompletionStatus.NOT_DONE]: "❌ Not Done",
  [CompletionStatus.DROPPED]: "🗑️ Dropped",
};

function buildActualEntry(commit: WeeklyCommit): ActualEntry {
  return {
    commitId: commit.id,
    actualResult: commit.actual?.actualResult ?? "",
    completionStatus: commit.actual?.completionStatus ?? null,
    deltaReason: commit.actual?.deltaReason ?? "",
  };
}

/**
 * Reconciliation view: for each commit, user provides completion status,
 * actual result, and delta reason (required for non-DONE items).
 */
export const ReconciliationView: React.FC<ReconciliationViewProps> = ({
  commits,
  planState,
  onUpdateActual,
  onSubmit,
  loading = false,
}) => {
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

  useEffect(() => {
    setActuals((prev) => {
      const next: Record<string, ActualEntry> = {};
      for (const commit of commits) {
        next[commit.id] = prev[commit.id] ?? buildActualEntry(commit);
      }
      return next;
    });
  }, [commits]);

  const updateActualField = (commitId: string, field: keyof ActualEntry, value: string | CompletionStatus | null) => {
    setActuals((prev) => ({
      ...prev,
      [commitId]: {
        commitId,
        actualResult: prev[commitId]?.actualResult ?? "",
        completionStatus: prev[commitId]?.completionStatus ?? null,
        deltaReason: prev[commitId]?.deltaReason ?? "",
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
    if (!entry.completionStatus) return false;
    if (entry.completionStatus !== CompletionStatus.DONE && !entry.deltaReason.trim()) return false;
    return true;
  });

  if (!isReconciling) {
    return null;
  }

  return (
    <div data-testid="reconciliation-view" style={{ marginTop: "1rem" }}>
      <h3>Reconciliation</h3>
      <p style={{ color: "#555", fontSize: "0.9rem" }}>
        Review each commitment and mark what actually happened this week.
      </p>

      {commits.map((commit) => {
        const entry = actuals[commit.id] ?? buildActualEntry(commit);
        const needsDelta = entry.completionStatus !== null && entry.completionStatus !== CompletionStatus.DONE;

        return (
          <div
            key={commit.id}
            data-testid={`reconcile-commit-${commit.id}`}
            style={{
              padding: "0.75rem",
              border: "1px solid #ccc",
              borderRadius: "4px",
              marginBottom: "0.75rem",
            }}
          >
            <div style={{ marginBottom: "0.5rem" }}>
              <strong>{commit.title}</strong>
              {commit.chessPriority && (
                <span style={{ marginLeft: "0.5rem", fontSize: "0.8rem", color: "#666" }}>
                  {commit.chessPriority}
                </span>
              )}
            </div>
            {commit.expectedResult && (
              <div style={{ fontSize: "0.85rem", color: "#555", marginBottom: "0.5rem" }}>
                Expected: {commit.expectedResult}
              </div>
            )}

            {/* Completion status */}
            <div style={{ marginBottom: "0.5rem" }}>
              <label htmlFor={`reconcile-status-select-${commit.id}`} style={{ fontSize: "0.85rem", fontWeight: 600 }}>Status:</label>
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
                style={{ marginLeft: "0.5rem" }}
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
            <div style={{ marginBottom: "0.5rem" }}>
              <textarea
                data-testid={`reconcile-actual-${commit.id}`}
                placeholder="What actually happened?"
                value={entry.actualResult}
                onChange={(e) => updateActualField(commit.id, "actualResult", e.target.value)}
                rows={2}
                style={{ width: "100%", padding: "0.25rem" }}
              />
            </div>

            {/* Delta reason (required if not DONE) */}
            {needsDelta && (
              <div style={{ marginBottom: "0.5rem" }}>
                <textarea
                  data-testid={`reconcile-delta-${commit.id}`}
                  placeholder="Why wasn't this completed? (required)"
                  value={entry.deltaReason}
                  onChange={(e) => updateActualField(commit.id, "deltaReason", e.target.value)}
                  rows={2}
                  style={{ width: "100%", padding: "0.25rem", borderColor: entry.deltaReason.trim() ? "#ccc" : "#c62828" }}
                />
              </div>
            )}

            <button
              data-testid={`reconcile-save-${commit.id}`}
              onClick={() => { void handleSaveActual(commit); }}
              disabled={!entry.completionStatus || savingCommits[commit.id]}
            >
              {savingCommits[commit.id] ? "⏳ Saving…" : "Save Actual"}
            </button>
          </div>
        );
      })}

      <div style={{ marginTop: "1rem", display: "flex", gap: "0.75rem", alignItems: "center" }}>
        <button
          data-testid="reconcile-submit"
          onClick={onSubmit}
          disabled={!allComplete || loading}
          style={{
            padding: "0.5rem 1.5rem",
            fontWeight: 600,
            background: allComplete ? "#1b5e20" : "#ccc",
            color: allComplete ? "#fff" : "#666",
            border: "none",
            borderRadius: "4px",
            cursor: allComplete ? "pointer" : "not-allowed",
          }}
        >
          Submit Reconciliation
        </button>
        {!allComplete && (
          <span style={{ color: "#888", fontSize: "0.85rem" }}>
            Complete all commitments to submit.
          </span>
        )}
      </div>
    </div>
  );
};
