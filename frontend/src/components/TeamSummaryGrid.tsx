import React from "react";
import type { TeamMemberSummary } from "@weekly-commitments/contracts";
import { ChessIcon } from "./icons/ChessIcon.js";
import styles from "./TeamSummaryGrid.module.css";

export interface TeamSummaryGridProps {
  users: TeamMemberSummary[];
  onDrillDown: (userId: string, planId: string | null) => void;
}

/**
 * Renders the team status grid for the manager dashboard.
 * Shows user, state, review status, commit counts, and badges.
 */
export const TeamSummaryGrid: React.FC<TeamSummaryGridProps> = ({ users, onDrillDown }) => {
  if (users.length === 0) {
    return (
      <div data-testid="team-summary-empty" className={styles.empty}>
        No direct reports found for this week.
      </div>
    );
  }

  return (
    <div className={styles.tableWrap}>
      <table data-testid="team-summary-grid" className={styles.table}>
        <thead className={styles.thead}>
          <tr>
            <th className={styles.th} scope="col">
              User
            </th>
            <th className={styles.th} scope="col">
              State
            </th>
            <th className={styles.th} scope="col">
              Review
            </th>
            <th className={`${styles.th} ${styles.tdCenter}`} scope="col">
              Commits
            </th>
            <th className={`${styles.th} ${styles.thPiece}`} scope="col" aria-label="King / Queen counts">
              <span className={styles.piecePairHeader}>
                <ChessIcon piece="KING" size={16} />
                {" / "}
                <ChessIcon piece="QUEEN" size={16} />
              </span>
            </th>
            <th className={`${styles.th} ${styles.tdCenter}`} scope="col">
              Issues
            </th>
            <th className={`${styles.th} ${styles.tdCenter}`} scope="col">
              Incomplete
            </th>
            <th className={`${styles.th} ${styles.tdCenter}`} scope="col">
              Non-Strategic
            </th>
            <th className={styles.th} scope="col">
              Badges
            </th>
            <th className={styles.th} scope="col" aria-label="Actions"></th>
          </tr>
        </thead>
        <tbody>
          {users.map((user) => (
            <tr key={user.userId} data-testid={`team-row-${user.userId}`} className={styles.tr}>
              <td className={styles.td}>{user.displayName ?? user.userId}</td>
              <td className={styles.td}>
                <StateBadge state={user.state} />
              </td>
              <td className={styles.td}>
                <ReviewBadge status={user.reviewStatus} />
              </td>
              <td className={`${styles.td} ${styles.tdCenter}`}>{user.commitCount}</td>
              <td className={`${styles.td} ${styles.tdCenter}`}>
                <span className={styles.piecePair}>
                  {user.kingCount}
                  {" / "}
                  {user.queenCount}
                </span>
              </td>
              <td className={`${styles.td} ${styles.tdCenter}`}>
                {user.issueCount > 0 && <span className={styles.issueCount}>{user.issueCount}</span>}
              </td>
              <td className={`${styles.td} ${styles.tdCenter}`}>
                {user.incompleteCount > 0 && <span className={styles.incompleteCount}>{user.incompleteCount}</span>}
              </td>
              <td className={`${styles.td} ${styles.tdCenter}`}>
                {user.nonStrategicCount > 0 && (
                  <span className={styles.nonStrategicCount}>{user.nonStrategicCount}</span>
                )}
              </td>
              <td className={styles.td}>
                <span className={styles.badgesCell}>
                  {user.isStale && (
                    <span data-testid={`stale-badge-${user.userId}`} className={styles.staleBadge}>
                      STALE
                    </span>
                  )}
                  {user.isLateLock && (
                    <span data-testid={`late-lock-badge-${user.userId}`} className={styles.lateLockedBadge}>
                      LATE LOCK
                    </span>
                  )}
                </span>
              </td>
              <td className={styles.td}>
                <button
                  data-testid={`drill-down-${user.userId}`}
                  className={styles.drillButton}
                  onClick={() => onDrillDown(user.userId, user.planId)}
                >
                  View
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

/* ─── StateBadge ─────────────────────────────────────────────────────────── */

const STATE_CLASS: Record<string, string> = {
  DRAFT: styles.stateDraft,
  LOCKED: styles.stateLocked,
  RECONCILING: styles.stateReconciling,
  RECONCILED: styles.stateReconciled,
  CARRY_FORWARD: styles.stateCarryForward,
};

const StateBadge: React.FC<{ state: string | null }> = ({ state }) => {
  if (!state) {
    return <span className={styles.badgeNoPlan}>No plan</span>;
  }

  const stateClass = STATE_CLASS[state] ?? styles.stateDefault;

  return (
    <span data-testid="state-badge" className={`${styles.badge} ${stateClass}`}>
      {state}
    </span>
  );
};

/* ─── ReviewBadge ────────────────────────────────────────────────────────── */

const ReviewBadge: React.FC<{ status: string | null }> = ({ status }) => {
  if (!status || status === "REVIEW_NOT_APPLICABLE") {
    return <span className={styles.reviewNa}>—</span>;
  }

  let reviewClass: string;
  switch (status) {
    case "REVIEW_PENDING":
      reviewClass = styles.reviewPending;
      break;
    case "APPROVED":
      reviewClass = styles.reviewApproved;
      break;
    case "CHANGES_REQUESTED":
      reviewClass = styles.reviewChangesRequested;
      break;
    default:
      reviewClass = styles.reviewDefault;
  }

  return (
    <span data-testid="review-badge" className={`${styles.badge} ${reviewClass}`}>
      {status.replace("REVIEW_", "").replace("_", " ")}
    </span>
  );
};
