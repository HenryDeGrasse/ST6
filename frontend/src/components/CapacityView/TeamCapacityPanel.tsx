import React, { useEffect } from "react";
import { useTeamCapacity } from "../../hooks/useCapacity.js";
import { useFeatureFlags } from "../../context/FeatureFlagContext.js";
import type { TeamMemberCapacity, OvercommitLevel } from "../../hooks/useCapacity.js";
import styles from "./TeamCapacityPanel.module.css";

// ─── Props ─────────────────────────────────────────────────────────────────────

export interface TeamCapacityPanelProps {
  /** ISO date string for the week start (e.g. "2025-01-06"). */
  weekStart: string;
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

/** Map overcommit level to emoji + label. */
function statusLabel(level: OvercommitLevel): string {
  switch (level) {
    case "NONE":
      return "✅ OK";
    case "MODERATE":
      return "⚠️ MODERATE";
    case "HIGH":
      return "⛔ HIGH";
  }
}

/** Map overcommit level to CSS modifier class. */
function statusRowClass(level: OvercommitLevel, styles: Record<string, string>): string {
  switch (level) {
    case "MODERATE":
      return styles.rowModerate ?? "";
    case "HIGH":
      return styles.rowHigh ?? "";
    default:
      return "";
  }
}

/** Determine the worst overcommit level from an array of members. */
function worstOvercommitLevel(members: TeamMemberCapacity[]): OvercommitLevel {
  if (members.some((m) => m.overcommitLevel === "HIGH")) return "HIGH";
  if (members.some((m) => m.overcommitLevel === "MODERATE")) return "MODERATE";
  return "NONE";
}

/** Format a nullable number as "Xh" or "–". */
function fmtHours(value: number | null): string {
  if (value === null) return "–";
  return `${value}h`;
}

// ─── Component ─────────────────────────────────────────────────────────────────

/**
 * TeamCapacityPanel
 *
 * Displays a table of team members' capacity data for a given week.
 * Fetches data internally via the `useTeamCapacity` hook.
 *
 * - Renders nothing when the `capacityTracking` feature flag is off.
 * - Shows loading/error states following existing component patterns.
 * - Includes a team totals row at the bottom.
 *
 * Uses `data-testid="team-capacity-panel"` for the container,
 * `data-testid="team-capacity-row-{index}"` for individual rows, and
 * `data-testid="team-capacity-totals"` for the totals row.
 */
export const TeamCapacityPanel: React.FC<TeamCapacityPanelProps> = ({ weekStart }) => {
  const flags = useFeatureFlags();
  const { teamCapacity, loading, error, fetchTeamCapacity } = useTeamCapacity();

  // Fetch team capacity on mount (or when weekStart changes) if the flag is enabled.
  useEffect(() => {
    if (flags.capacityTracking && weekStart) {
      void fetchTeamCapacity(weekStart);
    }
  }, [flags.capacityTracking, weekStart, fetchTeamCapacity]);

  // Gate on the feature flag first.
  if (!flags.capacityTracking) {
    return null;
  }

  // Loading state.
  if (loading) {
    return (
      <div data-testid="team-capacity-panel" className={styles.panel}>
        <div data-testid="team-capacity-loading" className={styles.loading}>
          Loading team capacity…
        </div>
      </div>
    );
  }

  // Error state.
  if (error) {
    return (
      <div data-testid="team-capacity-panel" className={styles.panel}>
        <div data-testid="team-capacity-error" className={styles.errorMsg}>
          {error}
        </div>
      </div>
    );
  }

  // Render nothing when there is no data yet.
  if (!teamCapacity) {
    return null;
  }

  const members = teamCapacity.members ?? [];

  // Compute team totals.
  const totalEstimated = members.reduce((sum, m) => sum + m.estimatedHours, 0);
  const totalAdjusted = members.reduce((sum, m) => sum + m.adjustedEstimate, 0);
  const totalRealisticCap = members.some((m) => m.realisticCap !== null)
    ? members.reduce((sum, m) => sum + (m.realisticCap ?? 0), 0)
    : null;
  const teamOvercommitLevel = worstOvercommitLevel(members);

  return (
    <div data-testid="team-capacity-panel" className={styles.panel}>
      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead className={styles.thead}>
            <tr>
              <th className={styles.th} scope="col">
                Name
              </th>
              <th className={`${styles.th} ${styles.tdRight}`} scope="col">
                Estimated (h)
              </th>
              <th className={`${styles.th} ${styles.tdRight}`} scope="col">
                Adjusted Est. (h)
              </th>
              <th className={`${styles.th} ${styles.tdRight}`} scope="col">
                Realistic Cap (h)
              </th>
              <th className={styles.th} scope="col">
                Status
              </th>
            </tr>
          </thead>
          <tbody>
            {members.map((member, index) => (
              <tr
                key={member.userId}
                data-testid={`team-capacity-row-${index}`}
                className={`${styles.tr} ${statusRowClass(member.overcommitLevel, styles)}`}
              >
                <td className={styles.td}>{member.name ?? member.userId}</td>
                <td className={`${styles.td} ${styles.tdRight}`}>{fmtHours(member.estimatedHours)}</td>
                <td className={`${styles.td} ${styles.tdRight}`}>{fmtHours(member.adjustedEstimate)}</td>
                <td className={`${styles.td} ${styles.tdRight}`}>{fmtHours(member.realisticCap)}</td>
                <td className={styles.td}>
                  <span
                    className={`${styles.statusBadge} ${styles[`status${member.overcommitLevel}`] ?? ""}`}
                    data-testid={`team-capacity-status-${index}`}
                  >
                    {statusLabel(member.overcommitLevel)}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
          {members.length > 0 && (
            <tfoot>
              <tr
                data-testid="team-capacity-totals"
                className={`${styles.tr} ${styles.totalsRow} ${statusRowClass(teamOvercommitLevel, styles)}`}
              >
                <td className={`${styles.td} ${styles.totalsLabel}`}>Team Total</td>
                <td className={`${styles.td} ${styles.tdRight} ${styles.totalsValue}`}>
                  {fmtHours(totalEstimated)}
                </td>
                <td className={`${styles.td} ${styles.tdRight} ${styles.totalsValue}`}>
                  {fmtHours(totalAdjusted)}
                </td>
                <td className={`${styles.td} ${styles.tdRight} ${styles.totalsValue}`}>
                  {fmtHours(totalRealisticCap)}
                </td>
                <td className={styles.td}>
                  <span
                    className={`${styles.statusBadge} ${styles[`status${teamOvercommitLevel}`] ?? ""}`}
                    data-testid="team-capacity-totals-status"
                  >
                    {statusLabel(teamOvercommitLevel)}
                  </span>
                </td>
              </tr>
            </tfoot>
          )}
        </table>
      </div>
    </div>
  );
};
