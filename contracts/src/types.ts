import type { PlanState, ReviewStatus, ChessPriority, CompletionStatus, LockType, CommitCategory } from "./enums.js";

// ─── Weekly Plan ────────────────────────────────────────────

export interface WeeklyPlan {
  id: string;
  orgId: string;
  ownerUserId: string;
  weekStartDate: string; // ISO date, always a Monday
  state: PlanState;
  reviewStatus: ReviewStatus;
  lockType: LockType | null;
  lockedAt: string | null;
  carryForwardExecutedAt: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

// ─── Weekly Commit ──────────────────────────────────────────

export interface WeeklyCommit {
  id: string;
  weeklyPlanId: string;
  title: string;
  description: string;
  chessPriority: ChessPriority | null;
  category: CommitCategory | null;
  outcomeId: string | null;
  nonStrategicReason: string | null;
  expectedResult: string;
  confidence: number | null;
  tags: string[];
  progressNotes: string;

  // RCDO snapshot (populated at lock time)
  snapshotRallyCryId: string | null;
  snapshotRallyCryName: string | null;
  snapshotObjectiveId: string | null;
  snapshotObjectiveName: string | null;
  snapshotOutcomeId: string | null;
  snapshotOutcomeName: string | null;

  // Capacity planning
  estimatedHours?: number | null;

  // Carry-forward lineage
  carriedFromCommitId: string | null;

  version: number;
  createdAt: string;
  updatedAt: string;

  // Inline validation (server-computed)
  validationErrors: ValidationError[];

  // Reconciliation actuals (populated when plan is RECONCILING or later)
  actual?: WeeklyCommitActual | null;
}

export interface ValidationError {
  code: string;
  message: string;
}

// ─── Actuals ────────────────────────────────────────────────

export interface WeeklyCommitActual {
  commitId: string;
  actualResult: string;
  completionStatus: CompletionStatus;
  deltaReason: string | null;
  timeSpent: number | null; // minutes, optional
  actualHours?: number | null;
}

// ─── Manager Review ─────────────────────────────────────────

export interface ManagerReview {
  id: string;
  weeklyPlanId: string;
  reviewerUserId: string;
  decision: "APPROVED" | "CHANGES_REQUESTED";
  comments: string;
  createdAt: string;
}

// ─── RCDO Hierarchy (read-only from upstream) ───────────────

export interface RcdoOutcome {
  id: string;
  name: string;
  objectiveId: string;
}

export interface RcdoObjective {
  id: string;
  name: string;
  rallyCryId: string;
  outcomes: RcdoOutcome[];
}

export interface RcdoCry {
  id: string;
  name: string;
  objectives: RcdoObjective[];
}

// ─── AI Next-Work Suggestions ───────────────────────────────

export type NextWorkSource = "CARRY_FORWARD" | "COVERAGE_GAP" | "EXTERNAL_TICKET";

export interface NextWorkSuggestion {
  suggestionId: string; // UUID
  title: string;
  suggestedOutcomeId: string | null;
  suggestedChessPriority: ChessPriority | null;
  confidence: number; // [0.0, 1.0]
  source: NextWorkSource;
  sourceDetail: string;
  rationale: string;
  /** URL to the ticket in the provider's web UI. Only set when source is EXTERNAL_TICKET. */
  externalTicketUrl?: string | null;
  /** Last-synced status label from the external provider. Only set when source is EXTERNAL_TICKET. */
  externalTicketStatus?: string | null;
}

export interface SuggestNextWorkRequest {
  weekStart?: string; // ISO date (Monday); defaults to current week if omitted
}

export interface SuggestNextWorkResponse {
  status: "ok" | "unavailable";
  suggestions: NextWorkSuggestion[];
}

/** Backwards-compatible alias matching the roadmap naming. */
export type NextWorkSuggestionsResponse = SuggestNextWorkResponse;

export type SuggestionFeedbackAction = "ACCEPT" | "DEFER" | "DECLINE";

export interface SuggestionFeedbackRequest {
  suggestionId: string; // UUID
  action: SuggestionFeedbackAction;
  reason?: string | null;
  sourceType?: string | null;
  sourceDetail?: string | null;
}

export interface SuggestionFeedbackResponse {
  status: "ok" | "unavailable";
}

// ─── Quick Daily Check-In ───────────────────────────────────

export type CheckInStatus = "ON_TRACK" | "AT_RISK" | "BLOCKED" | "DONE_EARLY";

export interface CheckInEntry {
  id: string;
  commitId: string;
  status: CheckInStatus;
  note: string;
  createdAt: string;
}

export interface CheckInHistoryResponse {
  commitId: string;
  entries: CheckInEntry[];
}

// ─── Cross-Week Trends ──────────────────────────────────────

export type TrendSeverity = "INFO" | "WARNING" | "POSITIVE";

export interface TrendInsight {
  /** Machine-readable insight type identifier (e.g. CARRY_FORWARD_STREAK). */
  type: string;
  /** Human-readable explanation. */
  message: string;
  severity: TrendSeverity;
}

export interface WeekTrendPoint {
  /** ISO date (Monday). */
  weekStart: string;
  totalCommits: number;
  strategicCommits: number;
  carryForwardCommits: number;
  avgConfidence: number;
  completionRate: number;
  hasActuals: boolean;
  priorityCounts: Record<string, number>;
  categoryCounts: Record<string, number>;
  /** Summed estimated hours for the week, or null when absent. */
  estimatedHours: number | null;
  /** Summed actual hours for the week, or null when absent. */
  actualHours: number | null;
  /** actualHours / estimatedHours, or null when unavailable. */
  hoursAccuracyRatio: number | null;
}

export interface TrendsResponse {
  /** Number of weeks with at least one commit. */
  weeksAnalyzed: number;
  /** ISO date — earliest Monday in the rolling window. */
  windowStart: string;
  /** ISO date — most recent Monday in the rolling window. */
  windowEnd: string;
  /** Fraction of commits linked to an RCDO outcome. */
  strategicAlignmentRate: number;
  /** Org-wide strategic alignment rate for comparison. */
  teamStrategicAlignmentRate: number;
  /** Average carry-forward commits per active week. */
  avgCarryForwardPerWeek: number;
  /** Consecutive recent weeks with at least one carry-forward commit. */
  carryForwardStreak: number;
  /** Mean confidence across all commits in the window. */
  avgConfidence: number;
  /** Mean completion rate across reconciled weeks. */
  completionAccuracy: number;
  /** avgConfidence minus completionAccuracy (positive = overconfident). */
  confidenceAccuracyGap: number;
  /** Average estimated hours across weeks with estimate data. */
  avgEstimatedHoursPerWeek: number | null;
  /** Average actual hours across weeks with actual-hour data. */
  avgActualHoursPerWeek: number | null;
  /** totalActualHours / totalEstimatedHours across weeks that have both values, or null when unavailable. */
  hoursAccuracyRatio: number | null;
  /** Fraction of commits per ChessPriority. */
  priorityDistribution: Record<string, number>;
  /** Fraction of commits per CommitCategory. */
  categoryDistribution: Record<string, number>;
  /** Per-week breakdown, oldest to newest. */
  weekPoints: WeekTrendPoint[];
  /** Structured insights for notable patterns. */
  insights: TrendInsight[];
}
