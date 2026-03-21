// ─── Outcome Coverage Timeline ──────────────────────────────

export interface OutcomeCoverageWeek {
  weekStart: string;
  commitCount: number;
  contributorCount: number;
  highPriorityCount: number;
}

export interface OutcomeCoverageTimeline {
  weeks: OutcomeCoverageWeek[];
  trendDirection: "RISING" | "FALLING" | "STABLE";
  /** Backwards-compatible alias used by existing frontend code. */
  outcomeId?: string;
}

// ─── Carry-Forward Heatmap ──────────────────────────────────

export interface HeatmapCell {
  weekStart: string;
  carriedCount: number;
}

export interface HeatmapUser {
  userId: string;
  displayName: string;
  /** Canonical field matching the backend DTO and OpenAPI schema. */
  weekCells?: HeatmapCell[];
  /** Backwards-compatible alias used by existing frontend code. */
  cells?: HeatmapCell[];
}

export interface CarryForwardHeatmap {
  users: HeatmapUser[];
  /** Backwards-compatible derived field used by existing frontend code. */
  weekStarts?: string[];
}

// ─── Category Shift Analysis ────────────────────────────────

export interface CategoryShift {
  category: string;
  delta: number;
}

export interface UserCategoryShift {
  userId: string;
  currentDistribution: Record<string, number>;
  priorDistribution: Record<string, number>;
  /** Canonical field matching the backend DTO and OpenAPI schema. */
  biggestShift?: CategoryShift;
  /** Backwards-compatible aliases used by existing frontend code. */
  biggestShiftCategory?: string;
  biggestShiftDelta?: number;
}

/** Backwards-compatible wrapper for older imports. */
export interface CategoryShiftAnalysis {
  users: UserCategoryShift[];
}

// ─── Estimation Accuracy Distribution ───────────────────────

export interface UserEstimationAccuracy {
  userId: string;
  avgConfidence: number;
  completionRate: number;
  calibrationGap: number;
}

/** Backwards-compatible wrapper for older imports. */
export interface EstimationAccuracyDistribution {
  users: UserEstimationAccuracy[];
}

// ─── Predictions ─────────────────────────────────────────────

export interface Prediction {
  type: string;
  likely: boolean;
  confidence: "HIGH" | "MEDIUM" | "LOW";
  reason: string;
  /** Backwards-compatible alias used by existing frontend code. */
  subjectId?: string;
}

/** Backwards-compatible wrapper for older imports. */
export interface UserPredictions {
  userId: string;
  predictions: Prediction[];
}
