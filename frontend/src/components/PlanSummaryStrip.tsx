import React from "react";
import type { WeeklyCommit } from "@weekly-commitments/contracts";
import { ChessPriority } from "@weekly-commitments/contracts";
import { ChessIcon } from "./icons/index.js";
import styles from "./PlanSummaryStrip.module.css";

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

/**
 * Horizontal strip showing at-a-glance plan metrics:
 * total commitments, strategic alignment %, non-strategic count,
 * KING count, and QUEEN count.
 */
export const PlanSummaryStrip: React.FC<PlanSummaryStripProps> = ({ commits }) => {
  const m = computeMetrics(commits);

  return (
    <div data-testid="plan-summary-strip" role="region" aria-label="Plan summary metrics" className={styles.strip}>
      {/* Total commitments */}
      <div className={styles.metricCard} data-testid="metric-total">
        <span className={styles.metricValue} aria-label="Total commitments">
          {m.total}
        </span>
        <span className={styles.metricLabel}>Total</span>
      </div>

      {/* Strategic alignment % */}
      <div className={styles.metricCard} data-testid="metric-alignment">
        <span className={styles.metricValue} aria-label="Strategic alignment percentage">
          {m.alignmentPct}%
        </span>
        <span className={styles.metricLabel}>Aligned</span>
      </div>

      {/* Non-strategic count */}
      <div className={styles.metricCard} data-testid="metric-non-strategic">
        <span className={styles.metricValue} aria-label="Non-strategic commitments">
          {m.nonStrategicCount}
        </span>
        <span className={styles.metricLabel}>Non-strategic</span>
      </div>

      {/* KING count – gold border card */}
      <div className={[styles.metricCard, styles.metricCardKing].join(" ")} data-testid="metric-king">
        <span className={styles.metricValue} aria-label="KING priority commitments">
          <ChessIcon piece="KING" size={16} />
          {m.kingCount}
        </span>
        <span className={styles.metricLabel}>KING</span>
      </div>

      {/* QUEEN count – gold border card */}
      <div className={[styles.metricCard, styles.metricCardQueen].join(" ")} data-testid="metric-queen">
        <span className={styles.metricValue} aria-label="QUEEN priority commitments">
          <ChessIcon piece="QUEEN" size={16} />
          {m.queenCount}
        </span>
        <span className={styles.metricLabel}>QUEEN</span>
      </div>
    </div>
  );
};
