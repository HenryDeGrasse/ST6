import React from "react";
import type { WeeklyCommit } from "@weekly-commitments/contracts";
import { ChessPriority } from "@weekly-commitments/contracts";

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
      <div data-testid="validation-panel" style={{ padding: "0.5rem", background: "#e8f5e9", borderRadius: "4px" }}>
        ✅ All validations pass. Ready to lock.
      </div>
    );
  }

  return (
    <div data-testid="validation-panel" style={{ padding: "0.5rem", background: "#fff3e0", borderRadius: "4px" }}>
      <strong>Validation Issues</strong>
      <ul style={{ margin: "0.25rem 0", paddingLeft: "1.25rem" }}>
        {issues.map((issue, i) => (
          <li
            key={i}
            data-testid={`validation-issue-${String(i)}`}
            style={{ color: issue.level === "error" ? "#c62828" : "#e65100" }}
          >
            {issue.level === "error" ? "❌" : "⚠️"} {issue.message}
          </li>
        ))}
      </ul>
      {commitsWithErrors.length > 0 && (
        <div style={{ marginTop: "0.5rem", fontSize: "0.85rem" }}>
          <strong>Per-commit errors:</strong>
          {commitsWithErrors.map((c) => (
            <div key={c.id} style={{ marginLeft: "0.5rem" }}>
              <em>{c.title || "(untitled)"}</em>:
              {c.validationErrors.map((ve, j) => (
                <span key={j} style={{ color: "#c62828", marginLeft: "0.25rem" }}>
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
