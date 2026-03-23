/**
 * QuickUpdateFlow – Rapid-fire batch check-in card flow (Phase 1).
 *
 * Presents a full-screen card-deck UX: one commitment per card, status
 * buttons, AI-generated option chips, a free-text note fallback, and
 * Previous/Next navigation (also keyboard-accessible via arrow keys).
 *
 * On the last card the "Next" button becomes "Submit All" which calls the
 * batch quick-update endpoint before invoking onComplete.
 *
 * Gated by the `quickUpdate` feature flag — returns null when the flag is
 * disabled so the parent never needs to check.
 */
import React, { useState, useEffect, useCallback, useRef } from "react";
import type { QuickUpdateItem, QuickUpdateNoteSource } from "@weekly-commitments/contracts";
import { useFeatureFlags } from "../../context/FeatureFlagContext.js";
import { useQuickUpdate } from "../../hooks/useQuickUpdate.js";
import type { CheckInOptionsResult } from "../../hooks/useQuickUpdate.js";
import { StatusIcon } from "../icons/StatusIcon.js";
import type { StatusIconName } from "../icons/StatusIcon.js";
import styles from "./QuickUpdateFlow.module.css";

// ─── Types ─────────────────────────────────────────────────────────────────

/** A single commitment shown in the rapid-fire update flow. */
export interface QuickUpdateCommitment {
  id: string;
  title: string;
  category: string | null;
  chessPriority: string | null;
  outcomeName: string | null;
  lastCheckInStatus: string | null;
  lastCheckInNote: string | null;
  lastCheckInDaysAgo: number;
}

export interface QuickUpdateFlowProps {
  /** Ordered list of commitments to update. */
  commitments: QuickUpdateCommitment[];
  /** The plan ID used when submitting the batch update. */
  planId: string;
  /** Called after the batch update has been successfully submitted. */
  onComplete: () => void;
  /** Called when the user dismisses the flow without submitting. */
  onClose: () => void;
}

// ─── Status map ────────────────────────────────────────────────────────────
// Mirrors QuickCheckIn.tsx's STATUS_LABELS / STATUS_EMOJI for consistency.
// The four values match the ProgressStatus enum: ON_TRACK | AT_RISK | BLOCKED | DONE_EARLY.

export const STATUS_MAP: Record<string, { label: string; icon: StatusIconName }> = {
  ON_TRACK: { label: "On Track", icon: "check" },
  AT_RISK: { label: "At Risk", icon: "warning" },
  BLOCKED: { label: "Blocked", icon: "blocked" },
  DONE_EARLY: { label: "Done Early", icon: "celebrate" },
};

const ALL_STATUS_KEYS = ["ON_TRACK", "AT_RISK", "BLOCKED", "DONE_EARLY"] as const satisfies readonly QuickUpdateItem["status"][];

// ─── Chess-priority labels ──────────────────────────────────────────────────

const CHESS_LABELS: Record<string, string> = {
  KING: "King",
  QUEEN: "Queen",
  ROOK: "Rook",
  BISHOP: "Bishop",
  KNIGHT: "Knight",
  PAWN: "Pawn",
};

type CommitUpdateDraft = {
  status: QuickUpdateItem["status"] | "";
  note: string;
  noteSource: QuickUpdateNoteSource;
  selectedSuggestionText?: string | null;
  selectedSuggestionSource?: string | null;
};

// ─── Component ─────────────────────────────────────────────────────────────

/**
 * Full-screen rapid-fire check-in flow.
 *
 * Navigate with Previous/Next buttons or the ← / → arrow keys.
 * On the final card, "Next" becomes "Submit All".
 */
export const QuickUpdateFlow: React.FC<QuickUpdateFlowProps> = ({
  commitments,
  planId,
  onComplete,
  onClose,
}) => {
  const flags = useFeatureFlags();
  const isEnabled = flags.quickUpdate;

  // ── State ──────────────────────────────────────────────────────────────
  const [currentIndex, setCurrentIndex] = useState(0);

  /** Map<commitId, draft> — accumulated user selections plus note provenance. */
  const [updates, setUpdates] = useState<Map<string, CommitUpdateDraft>>(new Map());

  /** Map<commitId, CheckInOptionsResult> — AI options cache. */
  const [aiOptions, setAiOptions] = useState<Map<string, CheckInOptionsResult>>(new Map());

  const [submitting, setSubmitting] = useState(false);

  const { submitBatchUpdate, fetchCheckInOptions } = useQuickUpdate();

  // Track which commitIds we've already fetched AI options for (avoids
  // double-fetching in strict mode or rapid navigation).
  const fetchingRef = useRef<Set<string>>(new Set());

  // ── Derived helpers ────────────────────────────────────────────────────

  const total = commitments.length;
  const isLast = currentIndex === total - 1;

  const currentCommitment = commitments[currentIndex];
  const currentUpdate = currentCommitment ? updates.get(currentCommitment.id) : undefined;

  useEffect(() => {
    setCurrentIndex((index) => {
      if (total === 0) return 0;
      return Math.min(index, total - 1);
    });
  }, [total]);

  // ── Fetch AI options on card change ────────────────────────────────────

  useEffect(() => {
    if (!isEnabled || !currentCommitment) return;

    const commitId = currentCommitment.id;

    // Skip if already fetched or in-flight
    if (aiOptions.has(commitId) || fetchingRef.current.has(commitId)) return;

    fetchingRef.current.add(commitId);

    void fetchCheckInOptions(
      commitId,
      currentCommitment.lastCheckInStatus ?? "",
      currentCommitment.lastCheckInNote ?? "",
      currentCommitment.lastCheckInDaysAgo,
    ).then((result) => {
      fetchingRef.current.delete(commitId);
      if (result) {
        setAiOptions((prev) => new Map(prev).set(commitId, result));
      }
    });
  }, [currentCommitment, aiOptions, fetchCheckInOptions, isEnabled]);

  // ── Keyboard navigation ────────────────────────────────────────────────

  useEffect(() => {
    if (!isEnabled || total === 0) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      // Don't override when focus is inside an editable field
      const target = e.target;
      if (
        target instanceof HTMLElement &&
        (target.tagName === "TEXTAREA" ||
          target.tagName === "INPUT" ||
          target.isContentEditable)
      ) {
        return;
      }

      if (e.key === "ArrowLeft") {
        setCurrentIndex((i) => Math.max(0, i - 1));
      } else if (e.key === "ArrowRight") {
        setCurrentIndex((i) => Math.min(total - 1, i + 1));
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isEnabled, total]);

  // ── Update helpers ─────────────────────────────────────────────────────

  const buildTypedNoteUpdate = useCallback(
    (existingStatus: CommitUpdateDraft["status"] | undefined, note: string): CommitUpdateDraft => ({
      status: existingStatus ?? "",
      note,
      noteSource: note.trim() ? "USER_TYPED" : "UNKNOWN",
      selectedSuggestionText: null,
      selectedSuggestionSource: null,
    }),
    [],
  );

  const setStatus = useCallback(
    (commitId: string, status: QuickUpdateItem["status"]) => {
      setUpdates((prev) => {
        const next = new Map(prev);
        const existing = prev.get(commitId);
        next.set(commitId, {
          status,
          note: existing?.note ?? "",
          noteSource: existing?.noteSource ?? "UNKNOWN",
          selectedSuggestionText: existing?.selectedSuggestionText ?? null,
          selectedSuggestionSource: existing?.selectedSuggestionSource ?? null,
        });
        return next;
      });
    },
    [],
  );

  const handleStatusSelect = useCallback(
    (commitId: string, status: QuickUpdateItem["status"]) => {
      setStatus(commitId, status);
      if (currentIndex < total - 1) {
        setCurrentIndex((i) => Math.min(total - 1, i + 1));
      }
    },
    [currentIndex, setStatus, total],
  );

  const setTypedNote = useCallback(
    (commitId: string, note: string) => {
      setUpdates((prev) => {
        const next = new Map(prev);
        const existing = prev.get(commitId);
        next.set(commitId, buildTypedNoteUpdate(existing?.status, note));
        return next;
      });
    },
    [buildTypedNoteUpdate],
  );

  const setSuggestedNote = useCallback(
    (commitId: string, note: string, source: string) => {
      setUpdates((prev) => {
        const next = new Map(prev);
        const existing = prev.get(commitId);
        const isClearing = note.trim() === "";
        next.set(commitId, {
          status: existing?.status ?? "",
          note,
          noteSource: isClearing ? "UNKNOWN" : "SUGGESTION_ACCEPTED",
          selectedSuggestionText: isClearing ? null : note,
          selectedSuggestionSource: isClearing ? null : source,
        });
        return next;
      });
    },
    [],
  );

  // ── Navigation handlers ────────────────────────────────────────────────

  const handlePrevious = useCallback(() => {
    setCurrentIndex((i) => Math.max(0, i - 1));
  }, []);

  const handleNext = useCallback(() => {
    if (total === 0) return;
    setCurrentIndex((i) => Math.min(total - 1, i + 1));
  }, [total]);

  // ── Submit ─────────────────────────────────────────────────────────────

  const handleSubmit = useCallback(async () => {
    setSubmitting(true);
    try {
      const items: QuickUpdateItem[] = commitments.flatMap((c) => {
        const u = updates.get(c.id);
        if (!u?.status) {
          return [];
        }

        return [{
          commitId: c.id,
          status: u.status,
          note: u.note ?? "",
          noteSource: u.noteSource,
          selectedSuggestionText: u.selectedSuggestionText ?? null,
          selectedSuggestionSource: u.selectedSuggestionSource ?? null,
        } satisfies QuickUpdateItem];
      });

      const result = await submitBatchUpdate(planId, items);
      if (result) {
        onComplete();
      }
    } finally {
      setSubmitting(false);
    }
  }, [commitments, updates, planId, submitBatchUpdate, onComplete]);

  // ── Feature-flag gate ──────────────────────────────────────────────────

  if (!isEnabled) return null;

  // ── Empty state guard ──────────────────────────────────────────────────

  if (total === 0) {
    return (
      <div data-testid="quick-update-flow" className={styles.overlay}>
        <div data-testid="quick-update-card" className={styles.card}>
          <div className={styles.emptyState}>No commitments to update.</div>
          <div className={styles.navBar}>
            <button type="button" className={styles.closeButton} onClick={onClose}>
              ×
            </button>
          </div>
        </div>
      </div>
    );
  }

  // ── Render ──────────────────────────────────────────────────────────────

  const commitment = currentCommitment;
  const update = currentUpdate;
  const options = commitment ? aiOptions.get(commitment.id) : undefined;

  return (
    <div data-testid="quick-update-flow" className={styles.overlay} role="dialog" aria-modal="true">
      {/* Close button */}
      <button
        type="button"
        className={styles.overlayClose}
        onClick={onClose}
        aria-label="Close quick update"
      >
        ×
      </button>

      <div data-testid="quick-update-card" className={styles.card}>
        {/* ── Progress indicator ── */}
        <div
          data-testid="quick-update-progress"
          className={styles.progressBar}
          aria-label={`Commitment ${currentIndex + 1} of ${total}`}
        >
          <span className={styles.progressText}>
            {currentIndex + 1} of {total} commitments
          </span>
          <div className={styles.progressTrack}>
            <div
              className={styles.progressFill}
              style={{ width: `${((currentIndex + 1) / total) * 100}%` }}
            />
          </div>
        </div>

        {/* ── Commitment header ── */}
        <div className={styles.commitmentHeader}>
          <h2 className={styles.commitmentTitle}>{commitment.title}</h2>
          <div className={styles.badgeRow}>
            {commitment.chessPriority && (
              <span className={styles.badge}>
                {CHESS_LABELS[commitment.chessPriority] ?? commitment.chessPriority}
              </span>
            )}
            {commitment.category && (
              <span className={styles.badgeCategory}>{commitment.category}</span>
            )}
            {commitment.outcomeName && (
              <span className={styles.badgeOutcome}>{commitment.outcomeName}</span>
            )}
          </div>
        </div>

        {/* ── Last check-in context ── */}
        {commitment.lastCheckInStatus && (
          <div className={styles.lastCheckIn}>
            <span className={styles.lastCheckInLabel}>Last check-in</span>
            <span className={styles.lastCheckInStatus}>
              {STATUS_MAP[commitment.lastCheckInStatus]?.label ?? commitment.lastCheckInStatus}
            </span>
            {commitment.lastCheckInDaysAgo > 0 && (
              <span className={styles.lastCheckInDays}>
                {commitment.lastCheckInDaysAgo}d ago
              </span>
            )}
          </div>
        )}

        {/* ── Status buttons ── */}
        <div className={styles.statusSection}>
          <span className={styles.sectionLabel}>How is this going?</span>
          <div className={styles.statusRow} role="group" aria-label="Select status">
            {ALL_STATUS_KEYS.map((statusKey) => {
              const { label, icon } = STATUS_MAP[statusKey];
              const isSelected = update?.status === statusKey;
              return (
                <button
                  key={statusKey}
                  type="button"
                  data-testid={`quick-update-status-${statusKey}`}
                  className={[
                    styles.statusButton,
                    isSelected ? styles.statusButtonSelected : "",
                  ]
                    .filter(Boolean)
                    .join(" ")}
                  onClick={() => handleStatusSelect(commitment.id, statusKey)}
                  aria-pressed={isSelected}
                  disabled={submitting}
                >
                  <StatusIcon icon={icon} size={16} />
                  <span className={styles.statusLabel}>{label}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* ── AI option chips ── */}
        {options && options.progressOptions.length > 0 && (
          <div className={styles.optionsSection}>
            <span className={styles.sectionLabel}>AI progress notes</span>
            <div className={styles.chipRow}>
              {options.progressOptions.map((opt, idx) => {
                const isChipSelected =
                  update?.noteSource === "SUGGESTION_ACCEPTED" &&
                  update?.selectedSuggestionText === opt.text;
                return (
                  <button
                    key={idx}
                    type="button"
                    className={[
                      styles.chip,
                      isChipSelected ? styles.chipSelected : "",
                    ]
                      .filter(Boolean)
                      .join(" ")}
                    onClick={() =>
                      setSuggestedNote(commitment.id, isChipSelected ? "" : opt.text, opt.source)
                    }
                    disabled={submitting}
                  >
                    {opt.text}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* ── Free-text note ── */}
        <div className={styles.noteSection}>
          <textarea
            data-testid="quick-update-note-input"
            className={styles.noteInput}
            placeholder="Add a note (optional)"
            value={update?.note ?? ""}
            onChange={(e) => setTypedNote(commitment.id, e.target.value)}
            rows={2}
            maxLength={500}
            disabled={submitting}
            aria-label="Update note"
          />
        </div>

        {/* ── Navigation bar ── */}
        <div className={styles.navBar}>
          <button
            type="button"
            data-testid="quick-update-prev"
            className={styles.navButton}
            onClick={handlePrevious}
            disabled={currentIndex === 0 || submitting}
            aria-label="Previous commitment"
          >
            ← Previous
          </button>

          <span className={styles.navSpacer} />

          {isLast ? (
            <button
              type="button"
              data-testid="quick-update-submit"
              className={styles.submitButton}
              onClick={() => void handleSubmit()}
              disabled={submitting}
              aria-busy={submitting}
            >
              {submitting ? "Submitting…" : "Submit All"}
            </button>
          ) : (
            <button
              type="button"
              data-testid="quick-update-next"
              className={styles.navButton}
              onClick={handleNext}
              disabled={submitting}
              aria-label="Next commitment"
            >
              Next →
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
