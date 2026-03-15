import React from "react";
import type { TeamMemberSummary } from "@weekly-commitments/contracts";

export interface TeamSummaryGridProps {
  users: TeamMemberSummary[];
  onDrillDown: (userId: string, planId: string | null) => void;
}

/**
 * Renders the team status grid for the manager dashboard.
 * Shows user, state, review status, commit counts, and badges.
 */
export const TeamSummaryGrid: React.FC<TeamSummaryGridProps> = ({
  users,
  onDrillDown,
}) => {
  if (users.length === 0) {
    return (
      <div data-testid="team-summary-empty" style={{ padding: "1rem", textAlign: "center", color: "#888" }}>
        No direct reports found for this week.
      </div>
    );
  }

  return (
    <table data-testid="team-summary-grid" style={{ width: "100%", borderCollapse: "collapse" }}>
      <thead>
        <tr style={{ borderBottom: "2px solid #ddd", textAlign: "left" }}>
          <th style={{ padding: "0.5rem" }}>User</th>
          <th style={{ padding: "0.5rem" }}>State</th>
          <th style={{ padding: "0.5rem" }}>Review</th>
          <th style={{ padding: "0.5rem" }}>Commits</th>
          <th style={{ padding: "0.5rem" }}>👑 / ♛</th>
          <th style={{ padding: "0.5rem" }}>Issues</th>
          <th style={{ padding: "0.5rem" }}>Incomplete</th>
          <th style={{ padding: "0.5rem" }}>Non-Strategic</th>
          <th style={{ padding: "0.5rem" }}>Badges</th>
          <th style={{ padding: "0.5rem" }}></th>
        </tr>
      </thead>
      <tbody>
        {users.map((user) => (
          <tr
            key={user.userId}
            data-testid={`team-row-${user.userId}`}
            style={{ borderBottom: "1px solid #eee" }}
          >
            <td style={{ padding: "0.5rem" }}>{user.displayName ?? user.userId}</td>
            <td style={{ padding: "0.5rem" }}>
              <StateBadge state={user.state} />
            </td>
            <td style={{ padding: "0.5rem" }}>
              <ReviewBadge status={user.reviewStatus} />
            </td>
            <td style={{ padding: "0.5rem" }}>{user.commitCount}</td>
            <td style={{ padding: "0.5rem" }}>
              {user.kingCount} / {user.queenCount}
            </td>
            <td style={{ padding: "0.5rem" }}>
              {user.issueCount > 0 && (
                <span style={{ color: "#dc2626" }}>{user.issueCount}</span>
              )}
            </td>
            <td style={{ padding: "0.5rem" }}>
              {user.incompleteCount > 0 && (
                <span style={{ color: "#d97706" }}>{user.incompleteCount}</span>
              )}
            </td>
            <td style={{ padding: "0.5rem" }}>
              {user.nonStrategicCount > 0 && (
                <span style={{ color: "#6b7280" }}>{user.nonStrategicCount}</span>
              )}
            </td>
            <td style={{ padding: "0.5rem" }}>
              {user.isStale && (
                <span
                  data-testid={`stale-badge-${user.userId}`}
                  style={{
                    background: "#fef3c7",
                    color: "#92400e",
                    padding: "0.125rem 0.375rem",
                    borderRadius: "4px",
                    fontSize: "0.75rem",
                    marginRight: "0.25rem",
                  }}
                >
                  STALE
                </span>
              )}
              {user.isLateLock && (
                <span
                  data-testid={`late-lock-badge-${user.userId}`}
                  style={{
                    background: "#fee2e2",
                    color: "#991b1b",
                    padding: "0.125rem 0.375rem",
                    borderRadius: "4px",
                    fontSize: "0.75rem",
                  }}
                >
                  LATE LOCK
                </span>
              )}
            </td>
            <td style={{ padding: "0.5rem" }}>
              <button
                data-testid={`drill-down-${user.userId}`}
                onClick={() => onDrillDown(user.userId, user.planId)}
              >
                View
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
};

const StateBadge: React.FC<{ state: string | null }> = ({ state }) => {
  if (!state) {
    return <span style={{ color: "#9ca3af" }}>No plan</span>;
  }

  const colors: Record<string, { bg: string; fg: string }> = {
    DRAFT: { bg: "#dbeafe", fg: "#1e40af" },
    LOCKED: { bg: "#e0e7ff", fg: "#3730a3" },
    RECONCILING: { bg: "#fef9c3", fg: "#854d0e" },
    RECONCILED: { bg: "#d1fae5", fg: "#065f46" },
    CARRY_FORWARD: { bg: "#f3e8ff", fg: "#6b21a8" },
  };

  const c = colors[state] ?? { bg: "#f3f4f6", fg: "#374151" };

  return (
    <span
      data-testid="state-badge"
      style={{
        background: c.bg,
        color: c.fg,
        padding: "0.125rem 0.375rem",
        borderRadius: "4px",
        fontSize: "0.75rem",
      }}
    >
      {state}
    </span>
  );
};

const ReviewBadge: React.FC<{ status: string | null }> = ({ status }) => {
  if (!status || status === "REVIEW_NOT_APPLICABLE") {
    return <span style={{ color: "#9ca3af" }}>—</span>;
  }

  const colors: Record<string, { bg: string; fg: string }> = {
    REVIEW_PENDING: { bg: "#fef3c7", fg: "#92400e" },
    APPROVED: { bg: "#d1fae5", fg: "#065f46" },
    CHANGES_REQUESTED: { bg: "#fee2e2", fg: "#991b1b" },
  };

  const c = colors[status] ?? { bg: "#f3f4f6", fg: "#374151" };

  return (
    <span
      data-testid="review-badge"
      style={{
        background: c.bg,
        color: c.fg,
        padding: "0.125rem 0.375rem",
        borderRadius: "4px",
        fontSize: "0.75rem",
      }}
    >
      {status.replace("REVIEW_", "").replace("_", " ")}
    </span>
  );
};
