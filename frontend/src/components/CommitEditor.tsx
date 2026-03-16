import React, { useState, useEffect, useRef } from "react";
import type {
  WeeklyCommit,
  CreateCommitRequest,
  UpdateCommitRequest,
  RcdoCry,
  RcdoSearchResult,
  RcdoSuggestion,
} from "@weekly-commitments/contracts";
import { PlanState } from "@weekly-commitments/contracts";
import { ChessPicker } from "./ChessPicker.js";
import { CategoryPicker } from "./CategoryPicker.js";
import { RcdoPicker, type RcdoSelection } from "./RcdoPicker.js";
import { AiSuggestionPanel } from "./AiSuggestionPanel.js";
import { ChessIcon, StatusIcon } from "./icons/index.js";
import type { ChessPiece } from "./icons/index.js";
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";
import styles from "./CommitEditor.module.css";

export interface CommitEditorProps {
  commit?: WeeklyCommit;
  planState: PlanState;
  rcdoTree: RcdoCry[];
  rcdoSearchResults: RcdoSearchResult[];
  onRcdoSearch: (query: string) => void;
  onRcdoClearSearch: () => void;
  onSave: (req: CreateCommitRequest | UpdateCommitRequest) => void;
  onDelete?: () => void;
  onCancel?: () => void;
  /** AI suggestions for this commit (optional, non-blocking). */
  aiSuggestions?: RcdoSuggestion[];
  /** AI suggestion request status. */
  aiSuggestStatus?: AiRequestStatus;
  /** Called when title/description changes to trigger AI suggestions. */
  onAiSuggestRequest?: (title: string, description?: string) => void;
  /** Called when user accepts an AI suggestion. */
  onAiSuggestClear?: () => void;
}

/** Maps a chess-priority string to a ChessPiece for the icon. */
function chessStringToPiece(priority: string | null): ChessPiece | null {
  if (!priority) return null;
  const upper = priority.toUpperCase() as ChessPiece;
  const valid: ChessPiece[] = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
  return valid.includes(upper) ? upper : null;
}

/**
 * Inline editor for a single commitment. Supports both create and edit modes.
 * Respects plan state for field editability (frozen after lock except progressNotes).
 */
export const CommitEditor: React.FC<CommitEditorProps> = ({
  commit,
  planState,
  rcdoTree,
  rcdoSearchResults,
  onRcdoSearch,
  onRcdoClearSearch,
  onSave,
  onDelete,
  onCancel,
  aiSuggestions = [],
  aiSuggestStatus = "idle",
  onAiSuggestRequest,
  onAiSuggestClear,
}) => {
  const isNew = !commit;
  const isDraft = planState === PlanState.DRAFT;
  const isLockedOrReconciling =
    planState === PlanState.LOCKED || planState === PlanState.RECONCILING;
  const isReadOnly =
    planState === PlanState.RECONCILED || planState === PlanState.CARRY_FORWARD;

  const [title, setTitle] = useState(commit?.title ?? "");
  const [description, setDescription] = useState(commit?.description ?? "");
  const [chessPriority, setChessPriority] = useState(commit?.chessPriority ?? null);
  const [category, setCategory] = useState(commit?.category ?? null);
  const [outcomeId, setOutcomeId] = useState(commit?.outcomeId ?? null);
  const [nonStrategicReason, setNonStrategicReason] = useState(commit?.nonStrategicReason ?? "");
  const [expectedResult, setExpectedResult] = useState(commit?.expectedResult ?? "");
  const [progressNotes, setProgressNotes] = useState(commit?.progressNotes ?? "");
  const [isNonStrategic, setIsNonStrategic] = useState(
    !!commit?.nonStrategicReason && !commit?.outcomeId,
  );

  // Keep a stable ref to the AI suggest callback so it doesn't re-trigger the effect
  const suggestRef = useRef(onAiSuggestRequest);
  suggestRef.current = onAiSuggestRequest;

  // Trigger AI suggestions when title or description changes
  useEffect(() => {
    if ((isDraft || isNew) && !isNonStrategic && suggestRef.current && title.length >= 5) {
      suggestRef.current(title, description || undefined);
    }
  }, [title, description, isDraft, isNew, isNonStrategic]);

  const handleAcceptAiSuggestion = (suggestion: RcdoSuggestion) => {
    setOutcomeId(suggestion.outcomeId);
    setIsNonStrategic(false);
    setNonStrategicReason("");
    onAiSuggestClear?.();
  };

  const handleRcdoChange = (selection: RcdoSelection | null) => {
    setOutcomeId(selection?.outcomeId ?? null);
    if (selection) {
      setIsNonStrategic(false);
      setNonStrategicReason("");
    }
  };

  const handleNonStrategicToggle = (checked: boolean) => {
    setIsNonStrategic(checked);
    if (checked) {
      setOutcomeId(null);
    } else {
      setNonStrategicReason("");
    }
  };

  const handleSave = () => {
    if (isNew) {
      const req: CreateCommitRequest = {
        title,
        description,
        expectedResult,
        ...(chessPriority && { chessPriority }),
        ...(category && { category }),
        ...(outcomeId && { outcomeId }),
        ...(isNonStrategic && nonStrategicReason ? { nonStrategicReason } : {}),
      };
      onSave(req);
    } else if (isDraft) {
      const req: UpdateCommitRequest = {
        title,
        description,
        chessPriority,
        category,
        outcomeId: isNonStrategic ? null : outcomeId,
        nonStrategicReason: isNonStrategic ? nonStrategicReason : null,
        expectedResult,
        progressNotes,
      };
      onSave(req);
    } else if (isLockedOrReconciling) {
      // Only progressNotes is mutable after lock
      const req: UpdateCommitRequest = { progressNotes };
      onSave(req);
    }
  };

  // ── Read-only view ────────────────────────────────────────────────────────
  if (isReadOnly && !isNew) {
    const chessPiece = chessStringToPiece(commit.chessPriority);
    return (
      <div
        data-testid={`commit-readonly-${commit.id}`}
        className={styles.readonlyCard}
      >
        <span className={styles.readonlyTitle}>{commit.title}</span>
        {commit.description && (
          <p className={styles.readonlyDescription}>{commit.description}</p>
        )}
        <div className={styles.readonlyMeta}>
          {chessPiece && (
            <span className={styles.readonlyMetaItem}>
              <ChessIcon piece={chessPiece} size={14} />
              {commit.chessPriority}
            </span>
          )}
          {!chessPiece && commit.chessPriority && (
            <span className={styles.readonlyMetaItem}>{commit.chessPriority}</span>
          )}
          {commit.category && (
            <span className={styles.readonlyMetaItem}>
              <StatusIcon icon="pin" size={14} />
              {commit.category}
            </span>
          )}
          {commit.snapshotOutcomeName && (
            <span className={styles.readonlyMetaItem}>
              <StatusIcon icon="target" size={14} />
              {commit.snapshotRallyCryName} → {commit.snapshotObjectiveName} → {commit.snapshotOutcomeName}
            </span>
          )}
          {commit.nonStrategicReason && (
            <span className={styles.readonlyMetaItem}>
              <StatusIcon icon="pin" size={14} />
              Non-strategic: {commit.nonStrategicReason}
            </span>
          )}
        </div>
      </div>
    );
  }

  // ── Edit / create form ────────────────────────────────────────────────────
  return (
    <div
      data-testid={isNew ? "commit-editor-new" : `commit-editor-${commit.id}`}
      className={styles.editorCard}
    >
      {/* ── Title ── */}
      <div className={styles.fieldGroup}>
        <input
          data-testid="commit-title"
          type="text"
          placeholder="Commitment title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          disabled={!isDraft && !isNew}
          className={[styles.input, styles.titleInput].join(" ")}
        />
      </div>

      {/* ── Description ── */}
      {(isDraft || isNew) && (
        <div className={styles.fieldGroup}>
          <textarea
            data-testid="commit-description"
            placeholder="Description (optional)"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={2}
            className={styles.textarea}
          />
        </div>
      )}

      {/* ── Chess priority + Category row ── */}
      {(isDraft || isNew) && (
        <div className={[styles.fieldGroup, styles.pickerRow].join(" ")}>
          <ChessPicker value={chessPriority} onChange={setChessPriority} />
          <CategoryPicker value={category} onChange={setCategory} />
        </div>
      )}

      {/* ── AI RCDO Suggestions (non-blocking) ── */}
      {(isDraft || isNew) && !isNonStrategic && (
        <AiSuggestionPanel
          suggestions={aiSuggestions}
          status={aiSuggestStatus}
          onAccept={handleAcceptAiSuggestion}
        />
      )}

      {/* ── RCDO picker or non-strategic toggle ── */}
      {(isDraft || isNew) && (
        <div className={styles.fieldGroup}>
          <label className={styles.checkboxLabel}>
            <input
              data-testid="non-strategic-toggle"
              type="checkbox"
              checked={isNonStrategic}
              onChange={(e) => handleNonStrategicToggle(e.target.checked)}
            />
            Non-strategic work
          </label>
          {isNonStrategic ? (
            <input
              data-testid="non-strategic-reason"
              type="text"
              placeholder="Reason this work is non-strategic"
              value={nonStrategicReason}
              onChange={(e) => setNonStrategicReason(e.target.value)}
              className={styles.input}
            />
          ) : (
            <RcdoPicker
              value={outcomeId}
              onChange={handleRcdoChange}
              tree={rcdoTree}
              searchResults={rcdoSearchResults}
              onSearch={onRcdoSearch}
              onClearSearch={onRcdoClearSearch}
            />
          )}
        </div>
      )}

      {/* ── Expected result ── */}
      {(isDraft || isNew) && (
        <div className={styles.fieldGroup}>
          <input
            data-testid="commit-expected-result"
            type="text"
            placeholder="Expected result"
            value={expectedResult}
            onChange={(e) => setExpectedResult(e.target.value)}
            className={styles.input}
          />
        </div>
      )}

      {/* ── Progress notes (editable in LOCKED and RECONCILING) ── */}
      {!isNew && isLockedOrReconciling && (
        <div className={styles.fieldGroup}>
          <label className={styles.fieldLabel}>Progress Notes</label>
          <textarea
            data-testid="commit-progress-notes"
            value={progressNotes}
            onChange={(e) => setProgressNotes(e.target.value)}
            rows={2}
            className={styles.textarea}
          />
        </div>
      )}

      {/* ── Validation errors from server ── */}
      {commit && commit.validationErrors.length > 0 && (
        <div
          data-testid="commit-validation-errors"
          className={styles.validationErrors}
        >
          {commit.validationErrors.map((ve, i) => (
            <div key={i} className={styles.validationError}>
              <StatusIcon icon="warning" size={14} />
              {ve.message}
            </div>
          ))}
        </div>
      )}

      {/* ── Actions ── */}
      <div className={styles.actions}>
        <button
          data-testid="commit-save"
          type="button"
          className={styles.saveButton}
          onClick={handleSave}
          disabled={isNew ? !title.trim() : false}
        >
          {isNew ? "Add Commitment" : "Save"}
        </button>
        {onCancel && (
          <button
            data-testid="commit-cancel"
            type="button"
            className={styles.cancelButton}
            onClick={onCancel}
          >
            Cancel
          </button>
        )}
        {onDelete && isDraft && !isNew && (
          <button
            data-testid="commit-delete"
            type="button"
            className={styles.deleteButton}
            onClick={onDelete}
          >
            Delete
          </button>
        )}
      </div>
    </div>
  );
};
