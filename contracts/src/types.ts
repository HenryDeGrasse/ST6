import type {
  PlanState,
  ReviewStatus,
  ChessPriority,
  CompletionStatus,
  LockType,
  CommitCategory,
} from "./enums.js";

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
