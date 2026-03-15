import React from "react";
import type { WeeklyCommit } from "@weekly-commitments/contracts";
import { ChessPriority } from "@weekly-commitments/contracts";

export interface PlanSummaryStripProps {
  commits: WeeklyCommit[];
}

export interface PlanMetrics {
  total: number;
  strategicCount: number;
  nonStrategicCount: number;
  alignmentPct: number;
  kingCount: number;
  queenCount: number;
}

/** Compute at-a-glance plan metrics from commit list. */
export function computeMetrics(commits: WeeklyCommit[]): PlanMetrics {
  const total = commits.length;
  const strategicCount = commits.filter((c) => c.outcomeId != null).length;
  const nonStrategicCount = total - strategicCount;
  const alignmentPct = total > 0 ? Math.round((strategicCount / total) * 100) : 0;
  const kingCount = commits.filter((c) => c.chessPriority === ChessPriority.KING).length;
  const queenCount = commits.filter((c) => c.chessPriority === ChessPriority.QUEEN).length;
  return { total, strategicCount, nonStrategicCount, alignmentPct, kingCount, queenCount };
}

const metricCardStyle: React.CSSProperties = {
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  padding: "0.5rem 1rem",
  background: "#fff",
  borderRadius: "4px",
  minWidth: "80px",
  border: "1px solid #e0e0e0",
};

const metricValueStyle: React.CSSProperties = {
  fontSize: "1.25rem",
  fontWeight: 700,
};

const metricLabelStyle: React.CSSProperties = {
  fontSize: "0.75rem",
  color: "#666",
  marginTop: "0.125rem",
};

/**
 * Horizontal strip showing at-a-glance plan metrics:
 * total commitments, strategic alignment %, non-strategic count,
 * KING count, and QUEEN count.
 */
export const PlanSummaryStrip: React.FC<PlanSummaryStripProps> = ({ commits }) => {
  const m = computeMetrics(commits);

  return (
    <div
      data-testid="plan-summary-strip"
      role="region"
      aria-label="Plan summary metrics"
      style={{
        display: "flex",
        gap: "0.75rem",
        padding: "0.75rem",
        background: "#f5f7fa",
        borderRadius: "4px",
        marginBottom: "1rem",
        overflowX: "auto",
      }}
    >
      <div style={metricCardStyle} data-testid="metric-total">
        <span style={metricValueStyle} aria-label="Total commitments">{m.total}</span>
        <span style={metricLabelStyle}>Total</span>
      </div>

      <div style={metricCardStyle} data-testid="metric-alignment">
        <span style={metricValueStyle} aria-label="Strategic alignment percentage">{m.alignmentPct}%</span>
        <span style={metricLabelStyle}>Aligned</span>
      </div>

      <div style={metricCardStyle} data-testid="metric-non-strategic">
        <span style={metricValueStyle} aria-label="Non-strategic commitments">{m.nonStrategicCount}</span>
        <span style={metricLabelStyle}>Non-strategic</span>
      </div>

      <div style={metricCardStyle} data-testid="metric-king">
        <span style={metricValueStyle} aria-label="KING priority commitments">👑 {m.kingCount}</span>
        <span style={metricLabelStyle}>KING</span>
      </div>

      <div style={metricCardStyle} data-testid="metric-queen">
        <span style={metricValueStyle} aria-label="QUEEN priority commitments">♛ {m.queenCount}</span>
        <span style={metricLabelStyle}>QUEEN</span>
      </div>
    </div>
  );
};
