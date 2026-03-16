import React from "react";
import type { WeeklyCommit } from "@weekly-commitments/contracts";
import { ChessPriority } from "@weekly-commitments/contracts";
import { StatusIcon } from "./icons/index.js";
import styles from "./ValidationPanel.module.css";

export interface ValidationPanelProps {
  commits: WeeklyCommit[];
}

interface PlanValidationIssue {
  level: "error" | "warning";
  message: string;
}

/**
 * Displays commit-level validation errors (from server) and plan-level
 * chess constraint warnings. Shown prominently before the Lock action.
 */
export const ValidationPanel: React.FC<ValidationPanelProps> = ({ commits }) => {
  const issues: PlanValidationIssue[] = [];

  // Aggregate commit-level validation errors
  const commitsWithErrors = commits.filter((c) => c.validationErrors.length > 0);
  if (commitsWithErrors.length > 0) {
    issues.push({
      level: "error",
      message: `${String(commitsWithErrors.length)} commit(s) have validation errors`,
    });
  }

  // Chess rule checks (client-side preview of server rules)
  const kingCount = commits.filter((c) => c.chessPriority === ChessPriority.KING).length;
  const queenCount = commits.filter((c) => c.chessPriority === ChessPriority.QUEEN).length;

  if (commits.length > 0 && kingCount === 0) {
    issues.push({ level: "warning", message: "No KING commitment. One KING per week is required to lock." });
  }
  if (kingCount > 1) {
    issues.push({ level: "error", message: `${String(kingCount)} KING commits — exactly 1 is required.` });
  }
  if (queenCount > 2) {
    issues.push({ level: "error", message: `${String(queenCount)} QUEEN commits — max 2 allowed.` });
  }

  // No-commits warning
  if (commits.length === 0) {
    issues.push({ level: "warning", message: "No commitments yet. Add at least one to start planning." });
  }

  if (issues.length === 0) {
    return (
      <div
        data-testid="validation-panel"
        className={`${styles.panel} ${styles.panelSuccess}`}
      >
        <span className={styles.successMessage}>
          <StatusIcon icon="check" size={16} />
          All validations pass. Ready to lock.
        </span>
      </div>
    );
  }

  return (
    <div
      data-testid="validation-panel"
      className={`${styles.panel} ${styles.panelIssues}`}
    >
      <strong className={styles.heading}>Validation Issues</strong>
      <ul className={styles.issueList}>
        {issues.map((issue, i) => (
          <li
            key={i}
            data-testid={`validation-issue-${String(i)}`}
            className={`${styles.issueItem} ${
              issue.level === "error" ? styles.issueError : styles.issueWarning
            }`}
          >
            {issue.level === "error" ? (
              <StatusIcon icon="error-x" size={14} />
            ) : (
              <StatusIcon icon="warning" size={14} />
            )}
            {issue.message}
          </li>
        ))}
      </ul>
      {commitsWithErrors.length > 0 && (
        <div className={styles.perCommitErrors}>
          <strong className={styles.perCommitHeading}>Per-commit errors:</strong>
          {commitsWithErrors.map((c) => (
            <div key={c.id} className={styles.commitErrorGroup}>
              <em className={styles.commitErrorTitle}>{c.title || "(untitled)"}</em>:
              {c.validationErrors.map((ve, j) => (
                <span key={j} className={styles.commitErrorMessage}>
                  {ve.message}
                </span>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
