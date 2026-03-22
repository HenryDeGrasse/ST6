import React, { useCallback, useEffect, useRef, useState } from "react";
import type { Issue, Team } from "@weekly-commitments/contracts";
import { IssueStatus } from "@weekly-commitments/contracts";
import { useTeams } from "../hooks/useTeams.js";
import { useIssues } from "../hooks/useIssues.js";
import styles from "./BacklogPickerDialog.module.css";

export interface BacklogPickerDialogProps {
  /** The week start date (ISO string, e.g. "2026-03-09") for creating assignments. */
  weekStart: string;
  /**
   * Called when the user confirms their selection.
   * Receives the array of selected issues; the caller is responsible for
   * creating the assignments via useWeeklyAssignments.createAssignment.
   */
  onConfirm: (issues: Issue[]) => void | Promise<void>;
  onCancel: () => void;
  loading?: boolean;
}

/**
 * Modal dialog that lets the user browse/search team backlog issues and
 * select one or more to add to the current week's plan.
 */
export const BacklogPickerDialog: React.FC<BacklogPickerDialogProps> = ({
  onConfirm,
  onCancel,
  loading = false,
}) => {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState("");
  const [selectedTeamId, setSelectedTeamId] = useState<string>("");
  const dialogRef = useRef<HTMLDivElement>(null);

  const { teams, loading: teamsLoading, error: teamsError, fetchTeams } = useTeams();
  const { issues, loading: issuesLoading, error: issuesError, fetchIssues } = useIssues();

  // Load teams on mount
  useEffect(() => {
    void fetchTeams();
  }, [fetchTeams]);

  // Select first team once teams load
  useEffect(() => {
    if (teams.length > 0 && !selectedTeamId) {
      setSelectedTeamId(teams[0].id);
    }
  }, [teams, selectedTeamId]);

  // Load issues when team changes or search changes
  useEffect(() => {
    if (!selectedTeamId) return;
    void fetchIssues(selectedTeamId, 0, 50, {
      status: IssueStatus.OPEN,
      ...(search.trim() ? { search: search.trim() } : {}),
    });
  }, [selectedTeamId, search, fetchIssues]);

  // Focus trap
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const focusable = dialog.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
    );
    if (focusable.length > 0) focusable[0].focus();
  }, []);

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

  const toggleIssue = (issueId: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(issueId)) next.delete(issueId);
      else next.add(issueId);
      return next;
    });
  };

  const handleConfirm = useCallback(() => {
    const selected = issues.filter((i) => selectedIds.has(i.id));
    void onConfirm(selected);
  }, [issues, selectedIds, onConfirm]);

  const selectedTeam: Team | undefined = teams.find((t) => t.id === selectedTeamId);
  const isDataLoading = teamsLoading || issuesLoading;
  const dataError = teamsError ?? issuesError;

  return (
    <div
      data-testid="backlog-picker-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="backlog-picker-dialog-title"
      onKeyDown={handleKeyDown}
      className={styles.overlay}
    >
      <div ref={dialogRef} className={styles.dialog}>
        <h3 id="backlog-picker-dialog-title" className={styles.title}>
          Add from Backlog
        </h3>
        <p className={styles.description}>Search and select issues to add to this week&apos;s plan.</p>

        {dataError && (
          <div className={styles.errorState} role="alert">
            {dataError}
          </div>
        )}

        {/* ── Controls ── */}
        <div className={styles.controls}>
          {/* Team selector */}
          <select
            data-testid="backlog-picker-team-select"
            aria-label="Select team"
            value={selectedTeamId}
            onChange={(e) => {
              setSelectedTeamId(e.target.value);
              setSelectedIds(new Set());
            }}
            className={styles.teamSelect}
            disabled={teamsLoading}
          >
            {teams.length === 0 && <option value="">No teams available</option>}
            {teams.map((team) => (
              <option key={team.id} value={team.id}>
                {team.name} ({team.keyPrefix})
              </option>
            ))}
          </select>

          {/* Search input */}
          <input
            data-testid="backlog-picker-search"
            type="text"
            placeholder={`Search ${selectedTeam ? selectedTeam.name : "backlog"} issues…`}
            aria-label="Search issues"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className={styles.searchInput}
          />
        </div>

        {/* ── Issue list ── */}
        <div
          data-testid="backlog-picker-issue-list"
          className={styles.issueList}
          role="listbox"
          aria-multiselectable="true"
          aria-label="Backlog issues"
        >
          {isDataLoading && !issues.length && (
            <p className={styles.loadingState} aria-live="polite">
              Loading issues…
            </p>
          )}
          {!isDataLoading && issues.length === 0 && (
            <p className={styles.emptyState}>No open issues found{search ? ` for "${search}"` : ""}.</p>
          )}
          {issues.map((issue) => {
            const isSelected = selectedIds.has(issue.id);
            return (
              <label
                key={issue.id}
                data-testid={`backlog-picker-issue-${issue.id}`}
                className={[styles.issueRow, isSelected ? styles.issueRowSelected : ""].join(" ")}
                role="option"
                aria-selected={isSelected}
              >
                <input
                  type="checkbox"
                  className={styles.checkbox}
                  checked={isSelected}
                  onChange={() => toggleIssue(issue.id)}
                  aria-label={`Select ${issue.issueKey}: ${issue.title}`}
                />
                <div className={styles.issueMeta}>
                  <div className={styles.issueKey}>{issue.issueKey}</div>
                  <div className={styles.issueTitle}>{issue.title}</div>
                  <div className={styles.issueBadges}>
                    {issue.effortType && (
                      <span className={styles.badge} data-testid={`issue-effort-${issue.id}`}>
                        {issue.effortType}
                      </span>
                    )}
                    {issue.chessPriority && (
                      <span className={styles.badge} data-testid={`issue-priority-${issue.id}`}>
                        {issue.chessPriority}
                      </span>
                    )}
                  </div>
                </div>
              </label>
            );
          })}
        </div>

        {/* ── Footer ── */}
        <div className={styles.buttonRow}>
          {selectedIds.size > 0 && (
            <span className={styles.selectionCount}>{selectedIds.size} issue{selectedIds.size !== 1 ? "s" : ""} selected</span>
          )}
          <button
            data-testid="backlog-picker-cancel"
            className={styles.cancelButton}
            onClick={onCancel}
            disabled={loading}
          >
            Cancel
          </button>
          <button
            data-testid="backlog-picker-confirm"
            className={styles.confirmButton}
            onClick={handleConfirm}
            disabled={selectedIds.size === 0 || loading}
          >
            {loading ? "Adding…" : `Add to Plan (${String(selectedIds.size)})`}
          </button>
        </div>
      </div>
    </div>
  );
};
