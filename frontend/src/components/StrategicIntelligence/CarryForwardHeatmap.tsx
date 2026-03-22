import React from "react";
import type { CarryForwardHeatmap as CarryForwardHeatmapData } from "@weekly-commitments/contracts";
import { useFeatureFlags } from "../../context/FeatureFlagContext.js";
import styles from "./CarryForwardHeatmap.module.css";

export interface CarryForwardHeatmapProps {
  data: CarryForwardHeatmapData | null;
  loading: boolean;
}

/**
 * Returns the CSS class for a given carried-forward count.
 *
 * 0        → neutral / green   (no carry-forward)
 * 1–2      → light purple      (mild carry-forward)
 * 3+       → stronger purple   (persistent carry-forward pattern)
 */
function cellStyleClass(carriedCount: number): string {
  if (carriedCount === 0) return styles.cellNeutral;
  if (carriedCount <= 2) return styles.cellWarn;
  return styles.cellHigh;
}

/**
 * Heatmap grid showing per-user, per-week carry-forward counts.
 *
 * Rows    → team members (displayName)
 * Columns → weeks (weekStart ISO date)
 * Cell    → colored by carriedCount with the numeric value displayed.
 *
 * Gated by the `strategicIntelligence` feature flag.
 */
export const CarryForwardHeatmap: React.FC<CarryForwardHeatmapProps> = ({ data, loading }) => {
  const flags = useFeatureFlags();

  if (!flags.strategicIntelligence) {
    return null;
  }

  const weekStarts = data?.weekStarts ?? [];
  const users = data?.users ?? [];

  return (
    <div data-testid="carry-forward-heatmap" className={styles.panel}>
      {/* ─── Header ────────────────────────────────────────────────── */}
      <div className={styles.header}>
        <span className={styles.title}>Carry-Forward Heatmap</span>
        <span className={styles.legend}>
          <span className={`${styles.legendDot} ${styles.cellNeutral}`} aria-hidden="true" />
          <span className={styles.legendLabel}>0</span>
          <span className={`${styles.legendDot} ${styles.cellWarn}`} aria-hidden="true" />
          <span className={styles.legendLabel}>1–2</span>
          <span className={`${styles.legendDot} ${styles.cellHigh}`} aria-hidden="true" />
          <span className={styles.legendLabel}>3+</span>
        </span>
      </div>

      {/* ─── Loading ────────────────────────────────────────────────── */}
      {loading && (
        <div data-testid="carry-forward-loading" className={styles.statusMsg}>
          Loading heatmap data…
        </div>
      )}

      {/* ─── Empty states ───────────────────────────────────────────── */}
      {!loading && !data && (
        <div data-testid="carry-forward-empty" className={styles.statusMsg}>
          No heatmap data available.
        </div>
      )}

      {!loading && data && users.length === 0 && (
        <div data-testid="carry-forward-no-users" className={styles.statusMsg}>
          No team members found.
        </div>
      )}

      {/* ─── Grid ───────────────────────────────────────────────────── */}
      {!loading && data && users.length > 0 && (
        <div className={styles.tableWrapper}>
          <table className={styles.table} role="grid" aria-label="Carry-forward heatmap">
            <thead>
              <tr>
                {/* Empty corner cell */}
                <th className={styles.cornerCell} scope="col" aria-label="Team member" />
                {weekStarts.map((week) => (
                  <th
                    key={week}
                    scope="col"
                    className={styles.weekHeader}
                    title={week}
                  >
                    {/* Show MM-DD portion of the ISO date */}
                    {week.length >= 10 ? week.slice(5, 10) : week}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {users.map((user) => {
                // Build a fast lookup from weekStart → cell for this user
                const cells = user.cells ?? user.weekCells ?? [];
                const cellMap = new Map(cells.map((c) => [c.weekStart, c]));

                return (
                  <tr
                    key={user.userId}
                    data-testid={`heatmap-row-${user.userId}`}
                    className={styles.row}
                  >
                    {/* Row header: user display name */}
                    <th
                      scope="row"
                      className={styles.userCell}
                      title={user.displayName}
                    >
                      {user.displayName}
                    </th>

                    {weekStarts.map((week) => {
                      const cell = cellMap.get(week);
                      const count = cell?.carriedCount ?? 0;

                      return (
                        <td
                          key={week}
                          data-testid={`heatmap-cell-${user.userId}-${week}`}
                          className={`${styles.cell} ${cellStyleClass(count)}`}
                          aria-label={`${user.displayName}, week ${week}: ${count} carried forward`}
                        >
                          {count}
                        </td>
                      );
                    })}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
