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
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";

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

  if (isReadOnly && !isNew) {
    return (
      <div data-testid={`commit-readonly-${commit.id}`} style={{ padding: "0.5rem", border: "1px solid #ddd", borderRadius: "4px", marginBottom: "0.5rem" }}>
        <strong>{commit.title}</strong>
        {commit.description && <p style={{ margin: "0.25rem 0", color: "#555" }}>{commit.description}</p>}
        <div style={{ fontSize: "0.85rem", color: "#777" }}>
          {commit.chessPriority && <span style={{ marginRight: "0.5rem" }}>🏅 {commit.chessPriority}</span>}
          {commit.category && <span style={{ marginRight: "0.5rem" }}>📁 {commit.category}</span>}
          {commit.snapshotOutcomeName && (
            <span>🎯 {commit.snapshotRallyCryName} → {commit.snapshotObjectiveName} → {commit.snapshotOutcomeName}</span>
          )}
          {commit.nonStrategicReason && <span>📌 Non-strategic: {commit.nonStrategicReason}</span>}
        </div>
      </div>
    );
  }

  return (
    <div data-testid={isNew ? "commit-editor-new" : `commit-editor-${commit.id}`} style={{ padding: "0.75rem", border: "1px solid #ccc", borderRadius: "4px", marginBottom: "0.75rem" }}>
      {/* Title */}
      <div style={{ marginBottom: "0.5rem" }}>
        <input
          data-testid="commit-title"
          type="text"
          placeholder="Commitment title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          disabled={!isDraft && !isNew}
          style={{ width: "100%", padding: "0.35rem", fontWeight: 600 }}
        />
      </div>

      {/* Description */}
      {(isDraft || isNew) && (
        <div style={{ marginBottom: "0.5rem" }}>
          <textarea
            data-testid="commit-description"
            placeholder="Description (optional)"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={2}
            style={{ width: "100%", padding: "0.35rem" }}
          />
        </div>
      )}

      {/* Chess priority + Category row */}
      {(isDraft || isNew) && (
        <div style={{ display: "flex", gap: "0.5rem", marginBottom: "0.5rem" }}>
          <ChessPicker value={chessPriority} onChange={setChessPriority} />
          <CategoryPicker value={category} onChange={setCategory} />
        </div>
      )}

      {/* AI RCDO Suggestions (non-blocking, clearly labeled) */}
      {(isDraft || isNew) && !isNonStrategic && (
        <AiSuggestionPanel
          suggestions={aiSuggestions}
          status={aiSuggestStatus}
          onAccept={handleAcceptAiSuggestion}
        />
      )}

      {/* RCDO picker or non-strategic toggle */}
      {(isDraft || isNew) && (
        <div style={{ marginBottom: "0.5rem" }}>
          <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.5rem" }}>
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
              style={{ width: "100%", padding: "0.25rem" }}
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

      {/* Expected result */}
      {(isDraft || isNew) && (
        <div style={{ marginBottom: "0.5rem" }}>
          <input
            data-testid="commit-expected-result"
            type="text"
            placeholder="Expected result"
            value={expectedResult}
            onChange={(e) => setExpectedResult(e.target.value)}
            style={{ width: "100%", padding: "0.25rem" }}
          />
        </div>
      )}

      {/* Progress notes (editable in LOCKED and RECONCILING too) */}
      {!isNew && isLockedOrReconciling && (
        <div style={{ marginBottom: "0.5rem" }}>
          <label style={{ fontSize: "0.85rem", fontWeight: 600 }}>Progress Notes</label>
          <textarea
            data-testid="commit-progress-notes"
            value={progressNotes}
            onChange={(e) => setProgressNotes(e.target.value)}
            rows={2}
            style={{ width: "100%", padding: "0.25rem" }}
          />
        </div>
      )}

      {/* Validation errors from server */}
      {commit && commit.validationErrors.length > 0 && (
        <div data-testid="commit-validation-errors" style={{ color: "#c62828", fontSize: "0.85rem", marginBottom: "0.5rem" }}>
          {commit.validationErrors.map((ve, i) => (
            <div key={i}>⚠️ {ve.message}</div>
          ))}
        </div>
      )}

      {/* Actions */}
      <div style={{ display: "flex", gap: "0.5rem" }}>
        <button
          data-testid="commit-save"
          onClick={handleSave}
          disabled={isNew ? !title.trim() : false}
        >
          {isNew ? "Add Commitment" : "Save"}
        </button>
        {onCancel && (
          <button data-testid="commit-cancel" onClick={onCancel}>
            Cancel
          </button>
        )}
        {onDelete && isDraft && !isNew && (
          <button data-testid="commit-delete" onClick={onDelete} style={{ color: "#c62828" }}>
            Delete
          </button>
        )}
      </div>
    </div>
  );
};
