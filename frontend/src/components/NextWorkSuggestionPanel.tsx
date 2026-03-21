import React, { useState, useCallback, useMemo } from "react";
import type { NextWorkSuggestion, SuggestionFeedbackRequest } from "@weekly-commitments/contracts";
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";
import { StatusIcon } from "./icons/index.js";
import styles from "./NextWorkSuggestionPanel.module.css";

export interface NextWorkSuggestionPanelProps {
  /** Suggestions to display. */
  suggestions: NextWorkSuggestion[];
  /** Current fetch status. */
  status: AiRequestStatus;
  /**
   * Called when the user clicks Accept.
   * The suggestion is added to the DRAFT plan as a new commit.
   * Returns true if the commit was created successfully.
   */
  onAccept: (suggestion: NextWorkSuggestion) => Promise<boolean>;
  /**
   * Called when the user clicks Defer or Decline.
   * Returns true when the feedback was saved and the suggestion can be dismissed.
   */
  onFeedback: (req: SuggestionFeedbackRequest) => Promise<boolean>;
  /** Trigger a fresh fetch of suggestions. */
  onRefresh: () => void;
  /** Optional resolver for turning an outcome ID into a human-friendly RCDO label. */
  resolveOutcomeLabel?: (outcomeId: string) => string | null;
}

const SOURCE_LABEL: Record<string, string> = {
  CARRY_FORWARD: "Carry-forward",
  COVERAGE_GAP: "Coverage gap",
  EXTERNAL_TICKET: "External ticket",
};

const PRIORITY_LABEL: Record<string, string> = {
  KING: "♔ King",
  QUEEN: "♛ Queen",
  ROOK: "♜ Rook",
  BISHOP: "♝ Bishop",
  KNIGHT: "♞ Knight",
  PAWN: "♟ Pawn",
};

/**
 * Panel that surfaces AI-generated next-work suggestions on the DRAFT plan page.
 *
 * Each suggestion has three actions:
 * - Accept → creates a commit in the DRAFT plan (calls onAccept)
 * - Defer  → saves to the backlog (DEFER feedback)
 * - Decline → dismisses for 4 weeks (DECLINE feedback + optional reason)
 *
 * Phase 2 enhancements:
 * - Suggestions are sorted by confidence descending
 * - Each suggestion shows an "AI-generated" label with confidence bar + %
 * - A "Why this suggestion?" expandable section reveals sourceDetail & rationale
 *
 * Gated by the `suggestNextWork` feature flag — the parent is responsible
 * for rendering this panel only when the flag is enabled.
 */
export const NextWorkSuggestionPanel: React.FC<NextWorkSuggestionPanelProps> = ({
  suggestions,
  status,
  onAccept,
  onFeedback,
  onRefresh,
  resolveOutcomeLabel,
}) => {
  const [actioningId, setActioningId] = useState<string | null>(null);
  const [declineTargetId, setDeclineTargetId] = useState<string | null>(null);
  const [declineReason, setDeclineReason] = useState("");
  const [expandedWhyIds, setExpandedWhyIds] = useState<Set<string>>(new Set());

  // Sort suggestions by confidence descending (defensive — backend already sorts)
  const sortedSuggestions = useMemo(
    () => [...suggestions].sort((a, b) => b.confidence - a.confidence),
    [suggestions],
  );

  const toggleWhy = useCallback((id: string) => {
    setExpandedWhyIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);

  const handleAccept = useCallback(
    async (suggestion: NextWorkSuggestion) => {
      setActioningId(suggestion.suggestionId);
      try {
        const ok = await onAccept(suggestion);
        if (ok) {
          await onFeedback({
            suggestionId: suggestion.suggestionId,
            action: "ACCEPT",
            sourceType: suggestion.source,
            sourceDetail: suggestion.sourceDetail,
          });
        }
      } finally {
        setActioningId(null);
      }
    },
    [onAccept, onFeedback],
  );

  const handleDefer = useCallback(
    async (suggestion: NextWorkSuggestion) => {
      setActioningId(suggestion.suggestionId);
      try {
        await onFeedback({
          suggestionId: suggestion.suggestionId,
          action: "DEFER",
          sourceType: suggestion.source,
          sourceDetail: suggestion.sourceDetail,
        });
      } finally {
        setActioningId(null);
      }
    },
    [onFeedback],
  );

  const handleDeclineOpen = useCallback((suggestionId: string) => {
    setDeclineTargetId(suggestionId);
    setDeclineReason("");
  }, []);

  const handleDeclineCancel = useCallback(() => {
    setDeclineTargetId(null);
    setDeclineReason("");
  }, []);

  const handleDeclineConfirm = useCallback(
    async (suggestion: NextWorkSuggestion) => {
      setActioningId(suggestion.suggestionId);
      try {
        const ok = await onFeedback({
          suggestionId: suggestion.suggestionId,
          action: "DECLINE",
          reason: declineReason.trim() || null,
          sourceType: suggestion.source,
          sourceDetail: suggestion.sourceDetail,
        });

        if (ok) {
          setDeclineTargetId(null);
          setDeclineReason("");
        }
      } finally {
        setActioningId(null);
      }
    },
    [onFeedback, declineReason],
  );

  // ── Panel header is always shown when flag is enabled ──────────────────

  return (
    <div data-testid="next-work-suggestion-panel" className={styles.panel}>
      {/* ── Header ── */}
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <span className={styles.robotIcon} aria-hidden="true">
            <StatusIcon icon="robot" size={14} />
          </span>
          <span className={styles.title}>AI-Suggested Work</span>
          <span className={styles.advisoryBadge}>Advisory</span>
        </div>
        <button
          type="button"
          data-testid="next-work-refresh-btn"
          className={styles.refreshButton}
          onClick={onRefresh}
          disabled={status === "loading"}
          aria-label="Refresh suggestions"
        >
          ↻
        </button>
      </div>

      {/* ── Body states ── */}
      {status === "loading" && (
        <div data-testid="next-work-loading" className={styles.loading}>
          <StatusIcon icon="loading" size={14} />
          Analysing your history…
        </div>
      )}

      {status === "rate_limited" && (
        <div data-testid="next-work-rate-limited" className={styles.rateLimited}>
          Rate limit reached. Try again in a moment.
        </div>
      )}

      {status === "unavailable" && (
        <div data-testid="next-work-unavailable" className={styles.unavailable}>
          Suggestions unavailable right now.
        </div>
      )}

      {status === "idle" && (
        <div data-testid="next-work-idle" className={styles.idle}>
          <button
            type="button"
            data-testid="next-work-fetch-btn"
            className={styles.fetchButton}
            onClick={onRefresh}
          >
            ✨ Show AI suggestions
          </button>
        </div>
      )}

      {status === "ok" && suggestions.length === 0 && (
        <div data-testid="next-work-empty" className={styles.empty}>
          No suggestions this week — great job staying on top of things!
        </div>
      )}

      {status === "ok" && sortedSuggestions.length > 0 && (
        <ul data-testid="next-work-suggestion-list" className={styles.suggestionList}>
          {sortedSuggestions.map((suggestion) => {
            const isActioning = actioningId === suggestion.suggestionId;
            const isDeclining = declineTargetId === suggestion.suggestionId;
            const isWhyExpanded = expandedWhyIds.has(suggestion.suggestionId);
            const confidencePct = Math.round(suggestion.confidence * 100);

            return (
              <li
                key={suggestion.suggestionId}
                data-testid={`next-work-suggestion-${suggestion.suggestionId}`}
                className={styles.suggestionItem}
              >
                {/* ── Suggestion card ── */}
                <div className={styles.cardHeader}>
                  <span className={styles.suggestionTitle}>{suggestion.title}</span>
                  {suggestion.suggestedChessPriority && (
                    <span
                      data-testid={`next-work-priority-${suggestion.suggestionId}`}
                      className={styles.priorityBadge}
                    >
                      {PRIORITY_LABEL[suggestion.suggestedChessPriority] ?? suggestion.suggestedChessPriority}
                    </span>
                  )}
                </div>

                {/* ── AI-generated label with confidence bar ── */}
                <div
                  data-testid={`next-work-confidence-row-${suggestion.suggestionId}`}
                  className={styles.confidenceRow}
                >
                  <span
                    data-testid={`next-work-ai-label-${suggestion.suggestionId}`}
                    className={styles.aiGeneratedLabel}
                  >
                    AI-generated
                  </span>
                  <div
                    data-testid={`next-work-confidence-bar-${suggestion.suggestionId}`}
                    className={styles.confidenceBar}
                    role="progressbar"
                    aria-valuenow={confidencePct}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-label={`Confidence: ${confidencePct}%`}
                  >
                    <div
                      className={styles.confidenceFill}
                      style={{ width: `${confidencePct}%` }}
                    />
                  </div>
                  <span
                    data-testid={`next-work-confidence-pct-${suggestion.suggestionId}`}
                    className={styles.confidencePct}
                  >
                    {confidencePct}%
                  </span>
                </div>

                {/* Source badge (always visible) */}
                <div className={styles.sourceRow}>
                  <span className={styles.sourceBadge}>
                    {SOURCE_LABEL[suggestion.source] ?? suggestion.source}
                  </span>
                  {/* External ticket status badge */}
                  {suggestion.source === "EXTERNAL_TICKET" && suggestion.externalTicketStatus && (
                    <span
                      data-testid={`next-work-ticket-status-${suggestion.suggestionId}`}
                      className={styles.ticketStatusBadge}
                    >
                      {suggestion.externalTicketStatus}
                    </span>
                  )}
                </div>

                {/* External ticket link */}
                {suggestion.source === "EXTERNAL_TICKET" && suggestion.externalTicketUrl && (
                  <div
                    data-testid={`next-work-ticket-link-${suggestion.suggestionId}`}
                    className={styles.ticketLinkRow}
                  >
                    <a
                      href={suggestion.externalTicketUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className={styles.ticketLink}
                      aria-label={`Open ticket ${suggestion.sourceDetail} in external tracker`}
                    >
                      ↗ View ticket
                    </a>
                  </div>
                )}

                {/* Suggested RCDO link */}
                {suggestion.suggestedOutcomeId && (
                  <div
                    data-testid={`next-work-rcdo-${suggestion.suggestionId}`}
                    className={styles.rcdoRow}
                  >
                    <span className={styles.rcdoIcon} aria-hidden="true">
                      <StatusIcon icon="target" size={12} />
                    </span>
                    <span className={styles.rcdoLabel}>RCDO</span>
                    <span className={styles.rcdoValue}>
                      {resolveOutcomeLabel?.(suggestion.suggestedOutcomeId) ?? suggestion.suggestedOutcomeId}
                    </span>
                  </div>
                )}

                {/* ── Why this suggestion? expandable section ── */}
                <div className={styles.whySection}>
                  <button
                    type="button"
                    data-testid={`next-work-why-toggle-${suggestion.suggestionId}`}
                    className={styles.whyToggle}
                    aria-expanded={isWhyExpanded}
                    onClick={() => toggleWhy(suggestion.suggestionId)}
                  >
                    {isWhyExpanded ? "▾" : "▸"} Why this suggestion?
                  </button>
                  {isWhyExpanded && (
                    <div
                      data-testid={`next-work-why-content-${suggestion.suggestionId}`}
                      className={styles.whyContent}
                    >
                      <p className={styles.sourceDetail}>{suggestion.sourceDetail}</p>
                      {suggestion.rationale && (
                        <p
                          data-testid={`next-work-rationale-${suggestion.suggestionId}`}
                          className={styles.rationale}
                        >
                          {suggestion.rationale}
                        </p>
                      )}
                    </div>
                  )}
                </div>

                {/* Decline confirmation inline */}
                {isDeclining && (
                  <div data-testid={`next-work-decline-form-${suggestion.suggestionId}`} className={styles.declineForm}>
                    <label className={styles.declineLabel} htmlFor={`decline-reason-${suggestion.suggestionId}`}>
                      Reason (optional)
                    </label>
                    <input
                      id={`decline-reason-${suggestion.suggestionId}`}
                      data-testid={`next-work-decline-reason-${suggestion.suggestionId}`}
                      type="text"
                      className={styles.declineInput}
                      value={declineReason}
                      onChange={(e) => setDeclineReason(e.target.value)}
                      placeholder="Why are you dismissing this?"
                      maxLength={200}
                    />
                    <div className={styles.declineActions}>
                      <button
                        type="button"
                        data-testid={`next-work-decline-confirm-${suggestion.suggestionId}`}
                        className={styles.declineConfirmButton}
                        onClick={() => void handleDeclineConfirm(suggestion)}
                        disabled={isActioning}
                      >
                        {isActioning ? "…" : "Dismiss"}
                      </button>
                      <button
                        type="button"
                        data-testid={`next-work-decline-cancel-${suggestion.suggestionId}`}
                        className={styles.cancelButton}
                        onClick={handleDeclineCancel}
                        disabled={isActioning}
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}

                {/* Action buttons (hidden while decline form is open) */}
                {!isDeclining && (
                  <div className={styles.actionRow}>
                    <button
                      type="button"
                      data-testid={`next-work-accept-${suggestion.suggestionId}`}
                      className={styles.acceptButton}
                      onClick={() => void handleAccept(suggestion)}
                      disabled={isActioning || actioningId !== null}
                    >
                      {isActioning ? "…" : "Accept"}
                    </button>
                    <button
                      type="button"
                      data-testid={`next-work-defer-${suggestion.suggestionId}`}
                      className={styles.deferButton}
                      onClick={() => void handleDefer(suggestion)}
                      disabled={isActioning || actioningId !== null}
                    >
                      Defer
                    </button>
                    <button
                      type="button"
                      data-testid={`next-work-decline-${suggestion.suggestionId}`}
                      className={styles.declineButton}
                      onClick={() => handleDeclineOpen(suggestion.suggestionId)}
                      disabled={isActioning || actioningId !== null}
                    >
                      Decline
                    </button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}

      <p className={styles.advisoryHint}>These suggestions are advisory — you can plan manually at any time.</p>
    </div>
  );
};
