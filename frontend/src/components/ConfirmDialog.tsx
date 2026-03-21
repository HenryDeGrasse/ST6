import React, { useEffect, useRef } from "react";
import styles from "./ConfirmDialog.module.css";

export interface ConfirmDialogProps {
  /** Dialog title */
  title: string;
  /** Dialog message body */
  message: string;
  /** Label for the confirm button (default: "Confirm") */
  confirmLabel?: string;
  /** Label for the cancel button (default: "Cancel") */
  cancelLabel?: string;
  /** Called when user confirms the action */
  onConfirm: () => void;
  /** Called when user cancels */
  onCancel: () => void;
  /** Whether the confirm action is in progress */
  loading?: boolean;
}

/**
 * Reusable confirmation dialog overlay.
 * Traps focus and uses aria-modal for accessibility (Gap 7).
 */
export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  title,
  message,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  onConfirm,
  onCancel,
  loading = false,
}) => {
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelBtnRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previousActiveElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;

    cancelBtnRef.current?.focus();

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (!loading) {
          onCancel();
        }
        return;
      }

      if (e.key === "Tab" && dialogRef.current) {
        const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
        );

        if (focusable.length === 0) {
          return;
        }

        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        const activeElement = document.activeElement;

        if (!dialogRef.current.contains(activeElement)) {
          e.preventDefault();
          first.focus();
          return;
        }

        if (e.shiftKey && activeElement === first) {
          e.preventDefault();
          last.focus();
          return;
        }

        if (!e.shiftKey && activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      previousActiveElement?.focus();
    };
  }, [loading, onCancel]);

  return (
    <div
      data-testid="confirm-dialog-overlay"
      className={styles.overlay}
      onClick={(e) => {
        if (!loading && e.target === e.currentTarget) {
          onCancel();
        }
      }}
    >
      <div
        ref={dialogRef}
        data-testid="confirm-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-message"
        aria-busy={loading}
        className={styles.dialog}
      >
        <h3 id="confirm-dialog-title" className={styles.title}>
          {title}
        </h3>
        <p id="confirm-dialog-message" className={styles.message}>
          {message}
        </p>
        <div className={styles.buttonRow}>
          <button
            ref={cancelBtnRef}
            data-testid="confirm-dialog-cancel"
            className={styles.cancelButton}
            onClick={onCancel}
            disabled={loading}
          >
            {cancelLabel}
          </button>
          <button
            data-testid="confirm-dialog-confirm"
            className={styles.confirmButton}
            onClick={onConfirm}
            disabled={loading}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
};
