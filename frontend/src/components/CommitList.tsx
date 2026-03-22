import React, { useState } from "react";
import type {
  WeeklyCommit,
  WeeklyAssignmentWithActual,
  CreateCommitRequest,
  UpdateCommitRequest,
  RcdoCry,
  RcdoSearchResult,
  RcdoSuggestion,
  CheckInEntry,
  CheckInRequest,
} from "@weekly-commitments/contracts";
import { PlanState } from "@weekly-commitments/contracts";
import { CommitEditor } from "./CommitEditor.js";
import { QuickCheckIn } from "./QuickCheckIn.js";
import { StatusIcon } from "./icons/index.js";
import { COMMIT_DRAFT_SOURCE_LABELS, getCommitDraftSource } from "./commitDraftSource.js";
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";
import styles from "./CommitList.module.css";

/** Optional bundle of check-in affordance props (Wave 2 dailyCheckIn flag). */
export interface CommitListCheckInProps {
  /** Which commit's check-in panel is currently expanded, or null. */
  openForId: string | null;
  /** Pre-fetched check-in history for the currently open commit. */
  entries: CheckInEntry[];
  /** True while a check-in API call is in-flight. */
  loading: boolean;
  /** Latest error message, or null when idle. */
  error: string | null;
  /** Called when the user opens check-in for a commit. */
  onOpen: (commitId: string) => void;
  /** Called when the user closes the check-in panel. */
  onClose: () => void;
  /** Called when the user submits a new check-in entry. Returns true on success. */
  onSubmit: (commitId: string, req: CheckInRequest) => Promise<boolean>;
}

export interface CommitListProps {
  commits: WeeklyCommit[];
  /**
   * Phase 6: weekly assignments from the backlog (optional).
   * When provided alongside the useIssueBacklog flag, assignment rows are
   * rendered with issue key and effort type.
   */
  assignments?: WeeklyAssignmentWithActual[];
  /** Called to remove an assignment from the plan (Phase 6). */
  onRemoveAssignment?: (assignmentId: string) => void | Promise<void>;
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
  /**
   * Check-in affordance (Wave 2).
   * When provided, a "Check in" button is shown on each card in LOCKED state.
   * When omitted (or feature flag off), check-in UI is not rendered.
   */
  checkIn?: CommitListCheckInProps;
  /** Phase 6: Callback to open the BacklogPickerDialog (shown only in DRAFT state). */
  onAddFromBacklog?: () => void;
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
  assignments = [],
  onRemoveAssignment,
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
  checkIn,
  onAddFromBacklog,
}) => {
  const [showNewForm, setShowNewForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const isDraft = planState === PlanState.DRAFT;
  const isEditable = isDraft || planState === PlanState.LOCKED || planState === PlanState.RECONCILING;
  const totalItems = commits.length + assignments.length;


  return (
    <div data-testid="commit-list" className={styles.container}>
      {/* ── List header ── */}
      <div className={styles.listHeader}>
        <h3 className={styles.listTitle}>Commitments ({String(totalItems)})</h3>
        {isDraft && !showNewForm && (
          <>
            <button
              data-testid="add-commit-btn"
              type="button"
              className={styles.addButton}
              onClick={() => setShowNewForm(true)}
            >
              + Add Commitment
            </button>
            {onAddFromBacklog && (
              <button
                data-testid="add-from-backlog-btn"
                type="button"
                className={styles.addButton}
                onClick={onAddFromBacklog}
                style={{ marginLeft: "0.5rem" }}
              >
                + Add from Backlog
              </button>
            )}
          </>
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
          onCancel={() => {
            setShowNewForm(false);
            onAiSuggestClear?.();
          }}
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
            onCancel={() => {
              setEditingId(null);
              onAiSuggestClear?.();
            }}
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
          <React.Fragment key={commit.id}>
            <div
              data-testid={`commit-row-${commit.id}`}
              onClick={isEditable ? () => setEditingId(commit.id) : undefined}
              className={[styles.commitCard, !isEditable ? styles.commitCardReadOnly : ""].join(" ")}
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
                  {/* ── Check-in button (LOCKED plans only) ── */}
                  {checkIn && planState === PlanState.LOCKED && (
                    <button
                      type="button"
                      data-testid={`check-in-btn-${commit.id}`}
                      className={styles.checkInButton}
                      onClick={(e) => {
                        e.stopPropagation();
                        if (checkIn.openForId === commit.id) {
                          checkIn.onClose();
                        } else {
                          checkIn.onOpen(commit.id);
                        }
                      }}
                      aria-expanded={checkIn.openForId === commit.id}
                      aria-controls={`check-in-panel-${commit.id}`}
                    >
                      {checkIn.openForId === commit.id ? "Close" : "Check in"}
                    </button>
                  )}
                </div>
              </div>

              {/* ── Description ── */}
              {commit.description && <p className={styles.cardDescription}>{commit.description}</p>}

              {/* ── Footer: RCDO / non-strategic / carried ── */}
              <div className={styles.cardFooter}>
                {commit.outcomeId &&
                  (() => {
                    // Use snapshot names if available (post-lock), otherwise look up from tree
                    const resolved = commit.snapshotOutcomeName
                      ? {
                          rallyCryName: commit.snapshotRallyCryName ?? "",
                          objectiveName: commit.snapshotObjectiveName ?? "",
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
                {(() => {
                  const src = getCommitDraftSource(commit.tags, commit.carriedFromCommitId);
                  if (!src) return null;
                  return (
                    <span
                      data-testid={`commit-draft-source-${src.toLowerCase()}`}
                      className={styles.draftSourceBadge}
                      data-source={src}
                    >
                      {COMMIT_DRAFT_SOURCE_LABELS[src]}
                    </span>
                  );
                })()}
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

            {/* ── Inline check-in panel (expandable) ── */}
            {checkIn && planState === PlanState.LOCKED && checkIn.openForId === commit.id && (
              <div id={`check-in-panel-${commit.id}`}>
                <QuickCheckIn
                  commitId={commit.id}
                  commitTitle={commit.title}
                  entries={checkIn.entries}
                  loading={checkIn.loading}
                  error={checkIn.error}
                  onCheckIn={(req) => checkIn.onSubmit(commit.id, req)}
                  onClose={checkIn.onClose}
                />
              </div>
            )}
          </React.Fragment>
        ),
      )}

      {/* ── Assignment rows (Phase 6 backlog items) ── */}
      {assignments.map((assignment) => {
        const issue = assignment.issue;
        return (
          <div
            key={assignment.id}
            data-testid={`assignment-row-${assignment.id}`}
            className={styles.commitCard}
          >
            <div className={styles.cardHeader}>
              <span className={styles.cardTitle}>
                {issue ? (
                  <>
                    <span className={styles.issueKeyBadge} data-testid={`assignment-key-${assignment.id}`}>
                      {issue.issueKey}
                    </span>{" "}
                    {issue.title}
                  </>
                ) : (
                  `Assignment ${assignment.id}`
                )}
              </span>
              <div className={styles.cardMeta}>
                {(assignment.chessPriorityOverride ?? issue?.chessPriority) && (
                  <span data-testid="assignment-priority" className={styles.metaBadge}>
                    {assignment.chessPriorityOverride ?? issue?.chessPriority}
                  </span>
                )}
                {issue?.effortType && (
                  <span data-testid={`assignment-effort-${assignment.id}`} className={styles.metaBadge}>
                    {issue.effortType}
                  </span>
                )}
                {isDraft && onRemoveAssignment && (
                  <button
                    type="button"
                    data-testid={`assignment-remove-${assignment.id}`}
                    className={styles.checkInButton}
                    onClick={() => void onRemoveAssignment(assignment.id)}
                    aria-label={`Remove assignment ${issue?.issueKey ?? assignment.id} from plan`}
                  >
                    Remove
                  </button>
                )}
              </div>
            </div>
            {issue?.description && <p className={styles.cardDescription}>{issue.description}</p>}
            <div className={styles.cardFooter}>
              <span className={styles.draftSourceBadge} data-source="BACKLOG">
                From backlog
              </span>
            </div>
          </div>
        );
      })}

      {/* ── Empty state ── */}
      {totalItems === 0 && !showNewForm && <p className={styles.emptyState}>No commitments yet.</p>}
    </div>
  );
};
