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
import { StatusIcon } from "./icons/index.js";
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";
import styles from "./CommitList.module.css";

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
 * Resolves the outcome name from the RCDO tree when snapshot fields aren't
 * populated yet (i.e. plan is still in DRAFT, before lock).
 */
function resolveOutcomeFromTree(
  outcomeId: string,
  tree: RcdoCry[],
): { rallyCryName: string; objectiveName: string; outcomeName: string } | null {
  for (const cry of tree) {
    for (const obj of cry.objectives) {
      for (const out of obj.outcomes) {
        if (out.id === outcomeId) {
          return {
            rallyCryName: cry.name,
            objectiveName: obj.name,
            outcomeName: out.name,
          };
        }
      }
    }
  }
  return null;
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
  const isEditable =
    isDraft || planState === PlanState.LOCKED || planState === PlanState.RECONCILING;

  return (
    <div data-testid="commit-list" className={styles.container}>
      {/* ── List header ── */}
      <div className={styles.listHeader}>
        <h3 className={styles.listTitle}>Commitments ({String(commits.length)})</h3>
        {isDraft && !showNewForm && (
          <button
            data-testid="add-commit-btn"
            type="button"
            className={styles.addButton}
            onClick={() => setShowNewForm(true)}
          >
            + Add Commitment
          </button>
        )}
      </div>

      {/* ── New commit form ── */}
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

      {/* ── Existing commits ── */}
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
            onClick={isEditable ? () => setEditingId(commit.id) : undefined}
            className={[
              styles.commitCard,
              !isEditable ? styles.commitCardReadOnly : "",
            ].join(" ")}
            role={isEditable ? "button" : undefined}
            tabIndex={isEditable ? 0 : undefined}
            aria-disabled={!isEditable}
            onKeyDown={
              isEditable
                ? (e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      setEditingId(commit.id);
                    }
                  }
                : undefined
            }
          >
            {/* ── Card header ── */}
            <div className={styles.cardHeader}>
              <span className={styles.cardTitle}>{commit.title}</span>
              <div className={styles.cardMeta}>
                {commit.chessPriority && (
                  <span data-testid="commit-chess" className={styles.metaBadge}>
                    {commit.chessPriority}
                  </span>
                )}
                {commit.category && (
                  <span data-testid="commit-category" className={styles.metaBadge}>
                    {commit.category}
                  </span>
                )}
              </div>
            </div>

            {/* ── Description ── */}
            {commit.description && (
              <p className={styles.cardDescription}>{commit.description}</p>
            )}

            {/* ── Footer: RCDO / non-strategic / carried ── */}
            <div className={styles.cardFooter}>
              {commit.outcomeId && (() => {
                // Use snapshot names if available (post-lock), otherwise look up from tree
                const resolved = commit.snapshotOutcomeName
                  ? {
                      rallyCryName: commit.snapshotRallyCryName ?? '',
                      objectiveName: commit.snapshotObjectiveName ?? '',
                      outcomeName: commit.snapshotOutcomeName,
                    }
                  : resolveOutcomeFromTree(commit.outcomeId, rcdoTree);

                if (resolved) {
                  return (
                    <span data-testid="commit-rcdo" className={styles.rcdoLink}>
                      <StatusIcon icon="target" size={13} />
                      <span className={styles.rcdoOutcome}>{resolved.outcomeName}</span>
                      <span className={styles.rcdoBreadcrumb}>
                        {resolved.rallyCryName} › {resolved.objectiveName}
                      </span>
                    </span>
                  );
                }

                return (
                  <span data-testid="commit-rcdo" className={styles.rcdoLink}>
                    <StatusIcon icon="target" size={13} />
                    Linked to outcome
                  </span>
                );
              })()}
              {commit.nonStrategicReason && (
                <span data-testid="commit-non-strategic" className={styles.nonStrategicLabel}>
                  <StatusIcon icon="pin" size={13} />
                  Non-strategic: {commit.nonStrategicReason}
                </span>
              )}
              {commit.carriedFromCommitId && (
                <span data-testid="commit-carried" className={styles.carriedBadge}>
                  <StatusIcon icon="return-arrow" size={12} />
                  Carried forward
                </span>
              )}
            </div>

            {/* ── Validation errors ── */}
            {commit.validationErrors.length > 0 && (
              <div className={styles.cardErrors}>
                {commit.validationErrors.map((ve, i) => (
                  <span key={i} className={styles.cardErrorItem}>
                    <StatusIcon icon="warning" size={13} />
                    {ve.message}
                  </span>
                ))}
              </div>
            )}
          </div>
        ),
      )}

      {/* ── Empty state ── */}
      {commits.length === 0 && !showNewForm && (
        <p className={styles.emptyState}>No commitments yet.</p>
      )}
    </div>
  );
};
