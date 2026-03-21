import React, { useEffect, useRef } from "react";
import type { QualityNudge } from "@weekly-commitments/contracts";
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";
import { StatusIcon } from "./icons/index.js";
import styles from "./PlanQualityNudge.module.css";

export interface PlanQualityNudgeProps {
  /** Quality nudges returned from the check (may be empty). */
  nudges: QualityNudge[];
  /** Current check status — drives which body state is shown. */
  status: AiRequestStatus;
  /** Called when the user chooses to lock despite any nudges. */
  onLockAnyway: () => void;
  /** Called when the user wants to review their plan before locking. */
  onReview: () => void;
}

const SEVERITY_LABEL: Record<QualityNudge["severity"], string> = {
  INFO: "Info",
  WARNING: "Note",
  POSITIVE: "Great",
};

const SEVERITY_CLASS: Record<QualityNudge["severity"], string> = {
  INFO: styles.severityInfo,
  WARNING: styles.severityWarning,
  POSITIVE: styles.severityPositive,
};

/**
 * Advisory overlay shown before the lock confirmation when the
 * `planQualityNudge` feature flag is enabled.
 *
 * Non-blocking: the user can always click "Lock Anyway" to proceed
 * regardless of the nudges displayed.  "Review Plan" dismisses the
 * overlay so the user can address issues first.
 *
 * Traps focus for accessibility (same pattern as ConfirmDialog).
 */
export const PlanQualityNudge: React.FC<PlanQualityNudgeProps> = ({ nudges, status, onLockAnyway, onReview }) => {
  const dialogRef = useRef<HTMLDivElement>(null);
  const reviewBtnRef = useRef<HTMLButtonElement>(null);

  // Focus the "Review Plan" button on mount; restore focus on unmount
  useEffect(() => {
    const previousActiveElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;

    reviewBtnRef.current?.focus();

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onReview();
        return;
      }

      if (e.key === "Tab" && dialogRef.current) {
        const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
        );

        if (focusable.length === 0) return;

        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        const active = document.activeElement;

        if (!dialogRef.current.contains(active)) {
          e.preventDefault();
          first.focus();
          return;
        }

        if (e.shiftKey && active === first) {
          e.preventDefault();
          last.focus();
          return;
        }

        if (!e.shiftKey && active === last) {
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
  }, [onReview]);

  const isLoading = status === "loading";

  return (
    <div
      data-testid="plan-quality-nudge-overlay"
      className={styles.overlay}
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onReview();
        }
      }}
    >
      <div
        ref={dialogRef}
        data-testid="plan-quality-nudge-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="pqn-title"
        aria-describedby="pqn-body"
        aria-busy={isLoading}
        className={styles.dialog}
      >
        {/* ── Header ── */}
        <div className={styles.header}>
          <span className={styles.robotIcon}>
            <StatusIcon icon="robot" size={14} />
          </span>
          <h3 id="pqn-title" className={styles.title}>
            Plan Quality Check
          </h3>
          <span className={styles.advisoryBadge}>Advisory</span>
        </div>

        {/* ── Body ── */}
        <div id="pqn-body" className={styles.body}>
          {status === "loading" && (
            <div data-testid="plan-quality-nudge-loading" className={styles.loading}>
              Checking plan quality…
            </div>
          )}

          {status === "rate_limited" && (
            <div data-testid="plan-quality-nudge-rate-limited" className={styles.rateLimited}>
              Rate limit reached. You can still lock your plan.
            </div>
          )}

          {(status === "unavailable") && (
            <div data-testid="plan-quality-nudge-unavailable" className={styles.unavailable}>
              Quality check unavailable. You can still lock your plan.
            </div>
          )}

          {status === "ok" && nudges.length === 0 && (
            <div data-testid="plan-quality-nudge-all-clear" className={styles.allClear}>
              <StatusIcon icon="check" size={16} />
              No quality issues detected. Ready to lock.
            </div>
          )}

          {status === "ok" && nudges.length > 0 && (
            <>
              <ul data-testid="plan-quality-nudge-list" className={styles.nudgeList}>
                {nudges.map((nudge, index) => (
                  <li
                    key={`${nudge.type}-${index}`}
                    data-testid={`plan-quality-nudge-item-${index}`}
                    className={styles.nudgeItem}
                  >
                    <span
                      className={`${styles.severityBadge} ${SEVERITY_CLASS[nudge.severity]}`}
                      data-testid={`plan-quality-nudge-badge-${index}`}
                    >
                      {SEVERITY_LABEL[nudge.severity]}
                    </span>
                    <span className={styles.nudgeMessage}>{nudge.message}</span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>

        {/* ── Advisory hint (hidden while the check is still loading) ── */}
        {!isLoading && (
          <p className={styles.advisoryHint}>These suggestions are advisory — you can lock at any time.</p>
        )}

        {/* ── Actions (always available; the nudge is non-blocking) ── */}
        <div className={styles.buttonRow}>
          <button
            ref={reviewBtnRef}
            type="button"
            data-testid="plan-quality-nudge-review"
            className={styles.reviewButton}
            onClick={onReview}
          >
            Review Plan
          </button>
          <button
            type="button"
            data-testid="plan-quality-nudge-lock-anyway"
            className={styles.lockAnywayButton}
            onClick={onLockAnyway}
          >
            Lock Anyway
          </button>
        </div>
      </div>
    </div>
  );
};
