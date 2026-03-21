// ─── Outcome Coverage Timeline ──────────────────────────────

export interface OutcomeCoverageWeek {
  weekStart: string;
  commitCount: number;
  contributorCount: number;
  highPriorityCount: number;
}

export interface OutcomeCoverageTimeline {
  outcomeId: string;
  weeks: OutcomeCoverageWeek[];
  trendDirection: "RISING" | "FALLING" | "STABLE";
}

// ─── Carry-Forward Heatmap ──────────────────────────────────

export interface HeatmapCell {
  weekStart: string;
  carriedCount: number;
}

export interface HeatmapUser {
  userId: string;
  displayName: string;
  cells: HeatmapCell[];
}

export interface CarryForwardHeatmap {
  users: HeatmapUser[];
  weekStarts: string[];
}

// ─── Category Shift Analysis ────────────────────────────────

export interface UserCategoryShift {
  userId: string;
  currentDistribution: Record<string, number>;
  priorDistribution: Record<string, number>;
  biggestShiftCategory: string;
  biggestShiftDelta: number;
}

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

export interface EstimationAccuracyDistribution {
  users: UserEstimationAccuracy[];
}

// ─── Predictions ─────────────────────────────────────────────

export interface Prediction {
  type: string;
  likely: boolean;
  confidence: "HIGH" | "MEDIUM" | "LOW";
  reason: string;
  subjectId: string;
}

export interface UserPredictions {
  userId: string;
  predictions: Prediction[];
}
