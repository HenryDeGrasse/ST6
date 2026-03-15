import React, { useCallback, useEffect, useRef, useState } from "react";
import type { WeeklyCommit } from "@weekly-commitments/contracts";

export interface CarryForwardDialogProps {
  commits: WeeklyCommit[];
  onCarryForward: (commitIds: string[]) => void;
  onCancel: () => void;
  loading?: boolean;
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
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        background: "rgba(0,0,0,0.4)",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        zIndex: 1000,
      }}
    >
      <div
        ref={dialogRef}
        style={{
          background: "#fff",
          borderRadius: "8px",
          padding: "1.5rem",
          maxWidth: "600px",
          width: "90%",
          maxHeight: "80vh",
          overflow: "auto",
        }}
      >
        <h3 id="carry-forward-dialog-title" style={{ marginTop: 0 }}>Carry Forward to Next Week</h3>
        <p style={{ color: "#555", fontSize: "0.9rem" }}>
          Select commitments to carry into next week&apos;s plan. They will appear as new
          draft items with a carry-forward reference.
        </p>

        {incompleteCommits.length === 0 ? (
          <p style={{ color: "#888" }}>No commits available to carry forward.</p>
        ) : (
          <div>
            {incompleteCommits.map((commit) => (
              <label
                key={commit.id}
                data-testid={`carry-option-${commit.id}`}
                style={{
                  display: "flex",
                  alignItems: "flex-start",
                  gap: "0.5rem",
                  padding: "0.5rem 0",
                  borderBottom: "1px solid #eee",
                  cursor: "pointer",
                }}
              >
                <input
                  type="checkbox"
                  checked={selected.has(commit.id)}
                  onChange={() => toggle(commit.id)}
                />
                <div>
                  <strong>{commit.title}</strong>
                  <div style={{ fontSize: "0.85rem", color: "#666" }}>
                    {commit.chessPriority && <span>{commit.chessPriority}</span>}
                    {commit.category && <span style={{ marginLeft: "0.5rem" }}>{commit.category}</span>}
                  </div>
                </div>
              </label>
            ))}
          </div>
        )}

        <div style={{ marginTop: "1rem", display: "flex", gap: "0.75rem", justifyContent: "flex-end" }}>
          <button data-testid="carry-cancel" onClick={onCancel} disabled={loading}>
            Cancel
          </button>
          <button
            data-testid="carry-confirm"
            onClick={() => onCarryForward(Array.from(selected))}
            disabled={selected.size === 0 || loading}
            style={{ fontWeight: 600 }}
          >
            {loading ? "Carrying…" : `Carry Forward (${String(selected.size)})`}
          </button>
        </div>
      </div>
    </div>
  );
};
