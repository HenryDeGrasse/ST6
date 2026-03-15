import React, { useState } from "react";
import type {
  WeeklyCommit,
  CreateCommitRequest,
  UpdateCommitRequest,
  RcdoCry,
  RcdoSearchResult,
  RcdoSuggestion,
} from "@weekly-commitments/contracts";
import { PlanState } from "@weekly-commitments/contracts";
import { CommitEditor } from "./CommitEditor.js";
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";

export interface CommitListProps {
  commits: WeeklyCommit[];
  planState: PlanState;
  rcdoTree: RcdoCry[];
  rcdoSearchResults: RcdoSearchResult[];
  onRcdoSearch: (query: string) => void;
  onRcdoClearSearch: () => void;
  onCreate: (req: CreateCommitRequest) => Promise<boolean>;
  onUpdate: (commitId: string, version: number, req: UpdateCommitRequest) => Promise<boolean>;
  onDelete: (commitId: string) => Promise<boolean>;
  /** AI RCDO suggestions (optional, non-blocking). */
  aiSuggestions?: RcdoSuggestion[];
  /** AI suggestion request status. */
  aiSuggestStatus?: AiRequestStatus;
  /** Trigger AI suggestion for a title/description. */
  onAiSuggestRequest?: (title: string, description?: string) => void;
  /** Clear AI suggestions (e.g. after accepting one). */
  onAiSuggestClear?: () => void;
}

/**
 * List of commits with inline editing, add-new form, and
 * read-only display for locked/reconciled states.
 */
export const CommitList: React.FC<CommitListProps> = ({
  commits,
  planState,
  rcdoTree,
  rcdoSearchResults,
  onRcdoSearch,
  onRcdoClearSearch,
  onCreate,
  onUpdate,
  onDelete,
  aiSuggestions = [],
  aiSuggestStatus = "idle",
  onAiSuggestRequest,
  onAiSuggestClear,
}) => {
  const [showNewForm, setShowNewForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const isDraft = planState === PlanState.DRAFT;

  return (
    <div data-testid="commit-list">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.75rem" }}>
        <h3 style={{ margin: 0 }}>Commitments ({String(commits.length)})</h3>
        {isDraft && !showNewForm && (
          <button data-testid="add-commit-btn" onClick={() => setShowNewForm(true)}>
            + Add Commitment
          </button>
        )}
      </div>

      {/* New commit form */}
      {showNewForm && (
        <CommitEditor
          planState={planState}
          rcdoTree={rcdoTree}
          rcdoSearchResults={rcdoSearchResults}
          onRcdoSearch={onRcdoSearch}
          onRcdoClearSearch={onRcdoClearSearch}
          onSave={(req) => {
            void onCreate(req as CreateCommitRequest).then((created) => {
              if (created) {
                setShowNewForm(false);
                onAiSuggestClear?.();
              }
            });
          }}
          onCancel={() => { setShowNewForm(false); onAiSuggestClear?.(); }}
          aiSuggestions={aiSuggestions}
          aiSuggestStatus={aiSuggestStatus}
          onAiSuggestRequest={onAiSuggestRequest}
          onAiSuggestClear={onAiSuggestClear}
        />
      )}

      {/* Existing commits */}
      {commits.map((commit) =>
        editingId === commit.id ? (
          <CommitEditor
            key={commit.id}
            commit={commit}
            planState={planState}
            rcdoTree={rcdoTree}
            rcdoSearchResults={rcdoSearchResults}
            onRcdoSearch={onRcdoSearch}
            onRcdoClearSearch={onRcdoClearSearch}
            onSave={(req) => {
              void onUpdate(commit.id, commit.version, req as UpdateCommitRequest).then((updated) => {
                if (updated) {
                  setEditingId(null);
                  onAiSuggestClear?.();
                }
              });
            }}
            onCancel={() => { setEditingId(null); onAiSuggestClear?.(); }}
            onDelete={() => {
              void onDelete(commit.id).then((deleted) => {
                if (deleted) {
                  setEditingId(null);
                }
              });
            }}
            aiSuggestions={aiSuggestions}
            aiSuggestStatus={aiSuggestStatus}
            onAiSuggestRequest={onAiSuggestRequest}
            onAiSuggestClear={onAiSuggestClear}
          />
        ) : (
          <div
            key={commit.id}
            data-testid={`commit-row-${commit.id}`}
            onClick={() => {
              // Editable in DRAFT; in LOCKED/RECONCILING only progressNotes editable
              if (isDraft || planState === PlanState.LOCKED || planState === PlanState.RECONCILING) {
                setEditingId(commit.id);
              }
            }}
            style={{
              padding: "0.5rem",
              border: "1px solid #ddd",
              borderRadius: "4px",
              marginBottom: "0.5rem",
              cursor: isDraft || planState === PlanState.LOCKED || planState === PlanState.RECONCILING ? "pointer" : "default",
            }}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                if (isDraft || planState === PlanState.LOCKED || planState === PlanState.RECONCILING) {
                  setEditingId(commit.id);
                }
              }
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <strong>{commit.title}</strong>
              <div style={{ display: "flex", gap: "0.5rem", fontSize: "0.8rem", color: "#666" }}>
                {commit.chessPriority && <span data-testid="commit-chess">{commit.chessPriority}</span>}
                {commit.category && <span data-testid="commit-category">{commit.category}</span>}
              </div>
            </div>
            {commit.description && (
              <p style={{ margin: "0.25rem 0", color: "#555", fontSize: "0.9rem" }}>{commit.description}</p>
            )}
            <div style={{ fontSize: "0.8rem", color: "#777" }}>
              {commit.outcomeId && commit.snapshotOutcomeName && (
                <span data-testid="commit-rcdo">
                  🎯 {commit.snapshotRallyCryName} → {commit.snapshotObjectiveName} → {commit.snapshotOutcomeName}
                </span>
              )}
              {commit.outcomeId && !commit.snapshotOutcomeName && (
                <span data-testid="commit-rcdo">🎯 Linked to outcome</span>
              )}
              {commit.nonStrategicReason && (
                <span data-testid="commit-non-strategic">📌 Non-strategic: {commit.nonStrategicReason}</span>
              )}
              {commit.carriedFromCommitId && (
                <span data-testid="commit-carried" style={{ marginLeft: "0.5rem", color: "#1565c0" }}>
                  ↩ Carried forward
                </span>
              )}
            </div>
            {commit.validationErrors.length > 0 && (
              <div style={{ color: "#c62828", fontSize: "0.8rem", marginTop: "0.25rem" }}>
                {commit.validationErrors.map((ve, i) => (
                  <span key={i}>⚠️ {ve.message} </span>
                ))}
              </div>
            )}
          </div>
        ),
      )}

      {commits.length === 0 && !showNewForm && (
        <p style={{ color: "#888", fontStyle: "italic" }}>No commitments yet.</p>
      )}
    </div>
  );
};
