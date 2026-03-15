import React from "react";
import type { RcdoRollupResponse } from "@weekly-commitments/contracts";

export interface RcdoRollupPanelProps {
  rollup: RcdoRollupResponse | null;
  loading: boolean;
}

/**
 * Displays the RCDO roll-up: commits grouped by outcome with
 * chess priority distribution and non-strategic counts.
 */
export const RcdoRollupPanel: React.FC<RcdoRollupPanelProps> = ({
  rollup,
  loading,
}) => {
  if (loading || !rollup) {
    return null;
  }

  return (
    <div data-testid="rcdo-rollup-panel" style={{ marginTop: "1.5rem" }}>
      <h3>RCDO Roll-up</h3>

      {rollup.nonStrategicCount > 0 && (
        <div
          data-testid="non-strategic-count"
          style={{
            padding: "0.5rem",
            background: "#fef3c7",
            borderRadius: "4px",
            marginBottom: "0.5rem",
          }}
        >
          {rollup.nonStrategicCount} non-strategic commit{rollup.nonStrategicCount !== 1 ? "s" : ""} this week
        </div>
      )}

      {rollup.items.length === 0 && rollup.nonStrategicCount === 0 && (
        <p data-testid="rollup-empty" style={{ color: "#888" }}>
          No commits to roll up.
        </p>
      )}

      {rollup.items.length > 0 && (
        <table
          data-testid="rollup-table"
          style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}
        >
          <thead>
            <tr style={{ borderBottom: "2px solid #ddd", textAlign: "left" }}>
              <th style={{ padding: "0.5rem" }}>Rally Cry</th>
              <th style={{ padding: "0.5rem" }}>Objective</th>
              <th style={{ padding: "0.5rem" }}>Outcome</th>
              <th style={{ padding: "0.5rem" }}>Commits</th>
              <th style={{ padding: "0.5rem" }}>👑</th>
              <th style={{ padding: "0.5rem" }}>♛</th>
              <th style={{ padding: "0.5rem" }}>♜</th>
              <th style={{ padding: "0.5rem" }}>♝</th>
              <th style={{ padding: "0.5rem" }}>♞</th>
              <th style={{ padding: "0.5rem" }}>♟</th>
            </tr>
          </thead>
          <tbody>
            {rollup.items.map((item) => (
              <tr
                key={item.outcomeId}
                data-testid={`rollup-row-${item.outcomeId}`}
                style={{ borderBottom: "1px solid #eee" }}
              >
                <td style={{ padding: "0.5rem" }}>{item.rallyCryName ?? "—"}</td>
                <td style={{ padding: "0.5rem" }}>{item.objectiveName ?? "—"}</td>
                <td style={{ padding: "0.5rem" }}>{item.outcomeName ?? item.outcomeId}</td>
                <td style={{ padding: "0.5rem" }}>{item.commitCount}</td>
                <td style={{ padding: "0.5rem" }}>{item.kingCount || ""}</td>
                <td style={{ padding: "0.5rem" }}>{item.queenCount || ""}</td>
                <td style={{ padding: "0.5rem" }}>{item.rookCount || ""}</td>
                <td style={{ padding: "0.5rem" }}>{item.bishopCount || ""}</td>
                <td style={{ padding: "0.5rem" }}>{item.knightCount || ""}</td>
                <td style={{ padding: "0.5rem" }}>{item.pawnCount || ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};
