import React, { useCallback, useEffect, useRef, useState } from "react";
import type { WeeklyCommit } from "@weekly-commitments/contracts";
import { ChessIcon } from "./icons/ChessIcon.js";
import type { ChessPiece } from "./icons/ChessIcon.js";
import styles from "./CarryForwardDialog.module.css";

export interface CarryForwardDialogProps {
  commits: WeeklyCommit[];
  onCarryForward: (commitIds: string[]) => void;
  onCancel: () => void;
  loading?: boolean;
}

/** Maps a chessPriority string to a ChessPiece for the SVG icon. */
function priorityToPiece(priority: string | null | undefined): ChessPiece | null {
  if (!priority) return null;
  const valid: ChessPiece[] = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
  const upper = priority.toUpperCase() as ChessPiece;
  return valid.includes(upper) ? upper : null;
}

/**
 * Dialog for selecting incomplete commits to carry forward to the next week.
 * Only non-DONE commits are shown as candidates.
 */
export const CarryForwardDialog: React.FC<CarryForwardDialogProps> = ({
  commits,
  onCarryForward,
  onCancel,
  loading = false,
}) => {
  // Show all commits as carry-forward candidates; the user decides which to carry.
  // Actual completion status is tracked separately in the actual entity.
  const incompleteCommits = commits;

  // Pre-select commits that aren't DONE (heuristic for the dialog)
  const [selected, setSelected] = useState<Set<string>>(
    () => new Set(incompleteCommits.map((c) => c.id)),
  );

  const toggle = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const dialogRef = useRef<HTMLDivElement>(null);

  // Focus trap: on mount, focus the first focusable element
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const focusable = dialog.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
    );
    if (focusable.length > 0) {
      focusable[0].focus();
    }
  }, []);

  // Focus trap: wrap Tab at boundaries; Escape closes
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Escape") {
        onCancel();
        return;
      }
      if (e.key === "Tab") {
        const dialog = dialogRef.current;
        if (!dialog) return;
        const focusable = dialog.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
        );
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey) {
          if (document.activeElement === first) {
            e.preventDefault();
            last.focus();
          }
        } else {
          if (document.activeElement === last) {
            e.preventDefault();
            first.focus();
          }
        }
      }
    },
    [onCancel],
  );

  return (
    <div
      data-testid="carry-forward-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="carry-forward-dialog-title"
      onKeyDown={handleKeyDown}
      className={styles.overlay}
    >
      <div
        ref={dialogRef}
        className={styles.dialog}
      >
        <h3 id="carry-forward-dialog-title" className={styles.title}>
          Carry Forward to Next Week
        </h3>
        <p className={styles.description}>
          Select commitments to carry into next week&apos;s plan. They will appear as new
          draft items with a carry-forward reference.
        </p>

        {incompleteCommits.length === 0 ? (
          <p className={styles.emptyState}>No commits available to carry forward.</p>
        ) : (
          <div className={styles.commitList}>
            {incompleteCommits.map((commit) => {
              const piece = priorityToPiece(commit.chessPriority);
              return (
                <label
                  key={commit.id}
                  data-testid={`carry-option-${commit.id}`}
                  className={styles.commitRow}
                >
                  <input
                    type="checkbox"
                    className={styles.checkbox}
                    checked={selected.has(commit.id)}
                    onChange={() => toggle(commit.id)}
                  />
                  <div className={styles.commitMeta}>
                    <span className={styles.commitTitle}>{commit.title}</span>
                    <div className={styles.commitInfo}>
                      {piece && (
                        <span className={styles.chessBadge}>
                          <ChessIcon piece={piece} size={12} />
                          {commit.chessPriority}
                        </span>
                      )}
                      {commit.category && (
                        <span className={styles.categoryBadge}>{commit.category}</span>
                      )}
                    </div>
                  </div>
                </label>
              );
            })}
          </div>
        )}

        <div className={styles.buttonRow}>
          <button
            data-testid="carry-cancel"
            className={styles.cancelButton}
            onClick={onCancel}
            disabled={loading}
          >
            Cancel
          </button>
          <button
            data-testid="carry-confirm"
            className={styles.confirmButton}
            onClick={() => onCarryForward(Array.from(selected))}
            disabled={selected.size === 0 || loading}
          >
            {loading ? "Carrying…" : `Carry Forward (${String(selected.size)})`}
          </button>
        </div>
      </div>
    </div>
  );
};
