import React from "react";
import type { RcdoRollupResponse } from "@weekly-commitments/contracts";
import { ChessIcon } from "./icons/ChessIcon.js";
import { UrgencyBadge } from "./UrgencyIndicator/UrgencyBadge.js";
import { useOutcomeMetadata } from "../hooks/useOutcomeMetadata.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import styles from "./RcdoRollupPanel.module.css";

export interface RcdoRollupPanelProps {
  rollup: RcdoRollupResponse | null;
  loading: boolean;
}

/**
 * Displays the RCDO roll-up: commits grouped by outcome with
 * chess priority distribution and non-strategic counts.
 */
export const RcdoRollupPanel: React.FC<RcdoRollupPanelProps> = ({ rollup, loading }) => {
  const flags = useFeatureFlags();
  const { urgencySummary, fetchUrgencySummary } = useOutcomeMetadata();

  // Fetch urgency summary once when the feature flag is enabled.
  React.useEffect(() => {
    if (flags.outcomeUrgency) {
      void fetchUrgencySummary();
    }
  }, [flags.outcomeUrgency, fetchUrgencySummary]);

  // Build outcomeId → urgencyBand lookup for O(1) badge rendering.
  const urgencyMap = React.useMemo(() => {
    const map = new Map<string, string>();
    if (urgencySummary) {
      for (const info of urgencySummary) {
        map.set(info.outcomeId, info.urgencyBand);
      }
    }
    return map;
  }, [urgencySummary]);

  if (loading || !rollup) {
    return null;
  }

  return (
    <div data-testid="rcdo-rollup-panel" className={styles.panel}>
      <h3 className={styles.heading}>RCDO Roll-up</h3>

      {rollup.nonStrategicCount > 0 && (
        <div data-testid="non-strategic-count" className={styles.nonStrategicCard}>
          {rollup.nonStrategicCount} non-strategic commit
          {rollup.nonStrategicCount !== 1 ? "s" : ""} this week
        </div>
      )}

      {rollup.items.length === 0 && rollup.nonStrategicCount === 0 && (
        <p data-testid="rollup-empty" className={styles.empty}>
          No commits to roll up.
        </p>
      )}

      {rollup.items.length > 0 && (
        <div className={styles.tableWrap}>
          <table data-testid="rollup-table" className={styles.table}>
            <thead className={styles.thead}>
              <tr>
                <th className={styles.th} scope="col">
                  Rally Cry
                </th>
                <th className={styles.th} scope="col">
                  Objective
                </th>
                <th className={styles.th} scope="col">
                  Outcome
                </th>
                <th className={styles.th} scope="col">
                  Commits
                </th>
                <th className={styles.thPiece} scope="col" aria-label="King">
                  <ChessIcon piece="KING" size={16} />
                </th>
                <th className={styles.thPiece} scope="col" aria-label="Queen">
                  <ChessIcon piece="QUEEN" size={16} />
                </th>
                <th className={styles.thPiece} scope="col" aria-label="Rook">
                  <ChessIcon piece="ROOK" size={16} />
                </th>
                <th className={styles.thPiece} scope="col" aria-label="Bishop">
                  <ChessIcon piece="BISHOP" size={16} />
                </th>
                <th className={styles.thPiece} scope="col" aria-label="Knight">
                  <ChessIcon piece="KNIGHT" size={16} />
                </th>
                <th className={styles.thPiece} scope="col" aria-label="Pawn">
                  <ChessIcon piece="PAWN" size={16} />
                </th>
              </tr>
            </thead>
            <tbody>
              {rollup.items.map((item) => (
                <tr key={item.outcomeId} data-testid={`rollup-row-${item.outcomeId}`} className={styles.tr}>
                  <td className={styles.td}>{item.rallyCryName ?? "—"}</td>
                  <td className={styles.td}>{item.objectiveName ?? "—"}</td>
                  <td className={styles.td}>
                    {item.outcomeName ?? item.outcomeId}
                    {flags.outcomeUrgency && (() => {
                      const urgencyBand = urgencyMap.get(item.outcomeId);
                      return urgencyBand ? <UrgencyBadge urgencyBand={urgencyBand} size="sm" /> : null;
                    })()}
                  </td>
                  <td className={styles.td}>{item.commitCount}</td>
                  <td className={styles.tdCenter}>
                    {item.kingCount ? <span className={styles.pieceCount}>{item.kingCount}</span> : null}
                  </td>
                  <td className={styles.tdCenter}>
                    {item.queenCount ? <span className={styles.pieceCount}>{item.queenCount}</span> : null}
                  </td>
                  <td className={styles.tdCenter}>
                    {item.rookCount ? <span className={styles.pieceCount}>{item.rookCount}</span> : null}
                  </td>
                  <td className={styles.tdCenter}>
                    {item.bishopCount ? <span className={styles.pieceCount}>{item.bishopCount}</span> : null}
                  </td>
                  <td className={styles.tdCenter}>
                    {item.knightCount ? <span className={styles.pieceCount}>{item.knightCount}</span> : null}
                  </td>
                  <td className={styles.tdCenter}>
                    {item.pawnCount ? <span className={styles.pieceCount}>{item.pawnCount}</span> : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
