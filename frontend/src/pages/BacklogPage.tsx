import React, { useCallback, useEffect, useMemo, useState } from "react";
import type {
  Issue,
  Team,
  TeamMember,
  IssueDetailResponse,
  CreateIssueRequest,
} from "@weekly-commitments/contracts";
import { EffortType, IssueStatus } from "@weekly-commitments/contracts";
import { useTeams } from "../hooks/useTeams.js";
import { useIssues, type IssueSortField } from "../hooks/useIssues.js";
import { IssueCreateForm } from "../components/IssueCreateForm.js";
import { IssueDetailPanel } from "../components/IssueDetailPanel.js";
import { ErrorBanner } from "../components/ErrorBanner.js";
import styles from "./BacklogPage.module.css";

function statusClass(status: IssueStatus): string {
  switch (status) {
    case IssueStatus.OPEN:
      return styles.statusOpen;
    case IssueStatus.IN_PROGRESS:
      return styles.statusInProgress;
    case IssueStatus.DONE:
      return styles.statusDone;
    case IssueStatus.ARCHIVED:
      return styles.statusArchived;
    default:
      return "";
  }
}

function effortClass(et: EffortType | null): string {
  switch (et) {
    case EffortType.BUILD:
      return styles.effortBuild;
    case EffortType.MAINTAIN:
      return styles.effortMaintain;
    case EffortType.COLLABORATE:
      return styles.effortCollaborate;
    case EffortType.LEARN:
      return styles.effortLearn;
    default:
      return "";
  }
}

function memberLabel(member: TeamMember): string {
  return `${member.userId}${member.role === "OWNER" ? " (Owner)" : ""}`;
}

export interface BacklogPageProps {
  /** Navigate to team management page (from App shell). */
  onManageTeam?: (teamId: string) => void;
}

export const BacklogPage: React.FC<BacklogPageProps> = ({ onManageTeam }) => {
  const teamsHook = useTeams();
  const issuesHook = useIssues();
  const fetchTeamDetail = teamsHook.fetchTeamDetail;
  const fetchIssues = issuesHook.fetchIssues;
  const fetchIssueDetail = issuesHook.fetchIssueDetail;

  const [selectedTeamId, setSelectedTeamId] = useState<string>("");
  const [teamMembers, setTeamMembers] = useState<TeamMember[]>([]);
  const [statusFilter, setStatusFilter] = useState<IssueStatus | "">("");
  const [effortFilter, setEffortFilter] = useState<EffortType | "">("");
  const [assigneeFilter, setAssigneeFilter] = useState<string>("");
  const [aiRankSort, setAiRankSort] = useState(false);
  const [page, setPage] = useState(0);

  const [showNewIssue, setShowNewIssue] = useState(false);
  const [submittingNew, setSubmittingNew] = useState(false);

  const [selectedIssueId, setSelectedIssueId] = useState<string | null>(null);

  useEffect(() => {
    void teamsHook.fetchTeams();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (teamsHook.teams.length > 0 && !selectedTeamId) {
      setSelectedTeamId(teamsHook.teams[0].id);
    }
  }, [teamsHook.teams, selectedTeamId]);

  useEffect(() => {
    if (!selectedTeamId) {
      setTeamMembers([]);
      return;
    }

    let cancelled = false;
    fetchTeamDetail(selectedTeamId).then((detail) => {
      if (!cancelled) {
        setTeamMembers(detail?.members ?? []);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [fetchTeamDetail, selectedTeamId]);

  const loadIssues = useCallback(() => {
    if (!selectedTeamId) return;
    const filters = {
      ...(statusFilter ? { status: statusFilter as IssueStatus } : {}),
      ...(effortFilter ? { effortType: effortFilter as EffortType } : {}),
      ...(assigneeFilter ? { assigneeUserId: assigneeFilter } : {}),
    };
    const sort: IssueSortField = aiRankSort ? "ai_rank" : "createdAt";
    void fetchIssues(selectedTeamId, page, 20, filters, sort);
  }, [aiRankSort, assigneeFilter, effortFilter, fetchIssues, page, selectedTeamId, statusFilter]);

  useEffect(() => {
    loadIssues();
  }, [loadIssues]);

  const handleCreateIssue = async (teamId: string, req: CreateIssueRequest) => {
    setSubmittingNew(true);
    const created = await issuesHook.createIssue(teamId, req);
    setSubmittingNew(false);
    if (created) {
      setShowNewIssue(false);
      loadIssues();
    }
  };

  const handleAddComment = async (issueId: string, text: string) => {
    await issuesHook.addComment(issueId, { commentText: text });
  };

  const handleLogTime = async (issueId: string, hours: number) => {
    await issuesHook.logTime(issueId, { hoursLogged: hours });
  };

  const fetchDetail = useCallback(
    (issueId: string): Promise<IssueDetailResponse | null> => fetchIssueDetail(issueId),
    [fetchIssueDetail],
  );

  const selectedTeam: Team | undefined = teamsHook.teams.find((t) => t.id === selectedTeamId);
  const teamMemberMap = useMemo(
    () => new Map(teamMembers.map((member) => [member.userId, memberLabel(member)])),
    [teamMembers],
  );

  return (
    <div className={styles.page} data-testid="backlog-page">
      <h1 className={styles.pageTitle}>Backlog</h1>

      {teamsHook.error && <ErrorBanner message={teamsHook.error} onDismiss={teamsHook.clearError} />}
      {issuesHook.error && <ErrorBanner message={issuesHook.error} onDismiss={issuesHook.clearError} />}

      <div className={styles.toolbar}>
        <div className={styles.toolbarLeft}>
          {teamsHook.teams.length > 1 && (
            <select
              className={`wc-select ${styles.teamSelect}`}
              value={selectedTeamId}
              onChange={(e) => {
                setSelectedTeamId(e.target.value);
                setAssigneeFilter("");
                setPage(0);
              }}
              data-testid="backlog-team-select"
              aria-label="Select team"
            >
              {teamsHook.teams.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          )}

          <div className={styles.filterGroup}>
            <select
              className="wc-select"
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as IssueStatus | "");
                setPage(0);
              }}
              aria-label="Filter by status"
              data-testid="backlog-status-filter"
            >
              <option value="">All statuses</option>
              {Object.values(IssueStatus).map((s) => (
                <option key={s} value={s}>
                  {s.replace("_", " ")}
                </option>
              ))}
            </select>

            <select
              className="wc-select"
              value={effortFilter}
              onChange={(e) => {
                setEffortFilter(e.target.value as EffortType | "");
                setPage(0);
              }}
              aria-label="Filter by effort type"
              data-testid="backlog-effort-filter"
            >
              <option value="">All effort types</option>
              {Object.values(EffortType).map((et) => (
                <option key={et} value={et}>
                  {et.charAt(0) + et.slice(1).toLowerCase()}
                </option>
              ))}
            </select>

            <select
              className="wc-select"
              value={assigneeFilter}
              onChange={(e) => {
                setAssigneeFilter(e.target.value);
                setPage(0);
              }}
              aria-label="Filter by assignee"
              data-testid="backlog-assignee-filter"
            >
              <option value="">All assignees</option>
              {teamMembers.map((member) => (
                <option key={member.userId} value={member.userId}>
                  {memberLabel(member)}
                </option>
              ))}
            </select>
          </div>

          <label className={styles.aiRankToggle}>
            <input
              type="checkbox"
              checked={aiRankSort}
              onChange={(e) => {
                setAiRankSort(e.target.checked);
                setPage(0);
              }}
              data-testid="backlog-ai-rank-toggle"
            />
            AI Ranking
          </label>
        </div>

        <div className={styles.toolbarRight}>
          {onManageTeam && selectedTeamId && (
            <button
              type="button"
              className="wc-button-secondary"
              onClick={() => onManageTeam(selectedTeamId)}
              data-testid="backlog-manage-team-btn"
            >
              Manage Team
            </button>
          )}
          <button
            type="button"
            className="wc-button"
            onClick={() => setShowNewIssue(true)}
            data-testid="backlog-new-issue-btn"
          >
            + New Issue
          </button>
        </div>
      </div>

      {teamsHook.loading && issuesHook.issues.length === 0 ? (
        <div className={styles.emptyState}>Loading teams…</div>
      ) : !selectedTeamId ? (
        <div className={styles.emptyState}>
          {teamsHook.teams.length === 0
            ? "You don't belong to any team yet. Create one to get started."
            : "Select a team to view its backlog."}
        </div>
      ) : (
        <>
          <div className={styles.tableWrapper}>
            <table className={styles.issueTable} data-testid="issue-table">
              <thead>
                <tr>
                  <th>Key</th>
                  <th>Title</th>
                  <th>Effort</th>
                  <th>Est. Hours</th>
                  <th>Priority</th>
                  <th>Assignee</th>
                  <th>Status</th>
                  {aiRankSort && <th>AI Rank</th>}
                </tr>
              </thead>
              <tbody>
                {issuesHook.loading ? (
                  <tr className={styles.loadingRow}>
                    <td colSpan={aiRankSort ? 8 : 7}>Loading issues…</td>
                  </tr>
                ) : issuesHook.issues.length === 0 ? (
                  <tr className={styles.loadingRow}>
                    <td colSpan={aiRankSort ? 8 : 7}>
                      No issues found.{" "}
                      <button
                        type="button"
                        className="wc-button-link"
                        onClick={() => setShowNewIssue(true)}
                      >
                        Create the first one.
                      </button>
                    </td>
                  </tr>
                ) : (
                  issuesHook.issues.map((issue: Issue) => (
                    <tr
                      key={issue.id}
                      onClick={() => setSelectedIssueId(issue.id)}
                      data-testid={`issue-row-${issue.id}`}
                    >
                      <td>
                        <span className={styles.issueKey}>{issue.issueKey}</span>
                      </td>
                      <td>
                        <span className={styles.issueTitle} title={issue.title}>
                          {issue.title}
                        </span>
                      </td>
                      <td>
                        {issue.effortType ? (
                          <span className={`${styles.effortBadge} ${effortClass(issue.effortType)}`}>
                            {issue.effortType.charAt(0) + issue.effortType.slice(1).toLowerCase()}
                          </span>
                        ) : (
                          "—"
                        )}
                      </td>
                      <td>{issue.estimatedHours != null ? `${issue.estimatedHours}h` : "—"}</td>
                      <td>{issue.chessPriority ?? "—"}</td>
                      <td>
                        {issue.assigneeUserId
                          ? (teamMemberMap.get(issue.assigneeUserId) ?? issue.assigneeUserId)
                          : "Unassigned"}
                      </td>
                      <td>
                        <span className={`${styles.statusBadge} ${statusClass(issue.status)}`}>
                          {issue.status.replace("_", " ")}
                        </span>
                      </td>
                      {aiRankSort && (
                        <td>
                          {issue.aiRecommendedRank != null ? (
                            <span className={styles.aiRankChip}>#{issue.aiRecommendedRank}</span>
                          ) : (
                            "—"
                          )}
                        </td>
                      )}
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {issuesHook.totalPages > 1 && (
            <div className={styles.pagination}>
              <span className={styles.paginationInfo}>
                Page {page + 1} of {issuesHook.totalPages} ({issuesHook.totalElements} issues)
              </span>
              <button
                type="button"
                className="wc-button-secondary"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                data-testid="backlog-prev-page"
              >
                ‹ Prev
              </button>
              <button
                type="button"
                className="wc-button-secondary"
                onClick={() => setPage((p) => Math.min(issuesHook.totalPages - 1, p + 1))}
                disabled={page >= issuesHook.totalPages - 1}
                data-testid="backlog-next-page"
              >
                Next ›
              </button>
            </div>
          )}
        </>
      )}

      {showNewIssue && (
        <div
          className={styles.modalOverlay}
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowNewIssue(false);
          }}
          data-testid="new-issue-modal"
        >
          <div className={styles.modal}>
            <div className={styles.modalHeader}>
              <span className={styles.modalTitle}>
                New Issue{selectedTeam ? ` — ${selectedTeam.name}` : ""}
              </span>
              <button
                type="button"
                style={{
                  background: "none",
                  border: "none",
                  cursor: "pointer",
                  color: "var(--wc-color-text-muted)",
                }}
                onClick={() => setShowNewIssue(false)}
                aria-label="Close"
              >
                ✕
              </button>
            </div>
            <IssueCreateForm
              teams={teamsHook.teams}
              defaultTeamId={selectedTeamId}
              onSubmit={handleCreateIssue}
              onCancel={() => setShowNewIssue(false)}
              submitting={submittingNew}
            />
          </div>
        </div>
      )}

      <IssueDetailPanel
        issueId={selectedIssueId}
        onClose={() => setSelectedIssueId(null)}
        onFetchDetail={fetchDetail}
        onAddComment={handleAddComment}
        onLogTime={handleLogTime}
      />
    </div>
  );
};
