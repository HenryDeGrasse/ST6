/**
 * API request/response types for the Weekly Commitments v1 API.
 *
 * These types codify the contract between frontend and backend.
 * They are derived from the OpenAPI spec and PRD §11.
 */

import type {
  ChessPriority,
  CommitCategory,
  CompletionStatus,
  PlanState,
  ReviewStatus,
} from "./enums.js";
import type { ErrorCode } from "./errors.js";
import type {
  WeeklyPlan,
  WeeklyCommit,
  WeeklyCommitActual,
  ManagerReview,
  RcdoCry,
} from "./types.js";

// ─── Standard Error Envelope ────────────────────────────────

export interface ApiErrorDetail {
  field?: string;
  commitId?: string;
  commitIds?: string[];
  rule?: string;
  expected?: number;
  actual?: number;
  planState?: string;
  currentVersion?: number;
  provided?: string;
  weekStart?: string;
  lastRefreshedAt?: string;
  originalRequestHash?: string;
  targetUserId?: string;
  dependency?: string;
  carryForwardExecutedAt?: string;
}

export interface ApiError {
  code: ErrorCode;
  message: string;
  details: ApiErrorDetail[];
}

export interface ApiErrorResponse {
  error: ApiError;
}

// ─── Plan Endpoints ─────────────────────────────────────────

/** No body needed; weekStart is in the path, userId from JWT */
export type CreatePlanRequest = Record<string, never>;

/** Requires Idempotency-Key header and If-Match header */
export type LockPlanRequest = Record<string, never>;

/** Requires Idempotency-Key header */
export type StartReconciliationRequest = Record<string, never>;

/** Requires Idempotency-Key header */
export type SubmitReconciliationRequest = Record<string, never>;

export interface CarryForwardRequest {
  commitIds: string[];
}

// ─── Commit Endpoints ───────────────────────────────────────

export interface CreateCommitRequest {
  title: string;
  description?: string;
  chessPriority?: ChessPriority | null;
  category?: CommitCategory | null;
  outcomeId?: string | null;
  nonStrategicReason?: string | null;
  expectedResult?: string;
  confidence?: number | null;
  tags?: string[];
}

export interface UpdateCommitRequest {
  title?: string;
  description?: string;
  chessPriority?: ChessPriority | null;
  category?: CommitCategory | null;
  outcomeId?: string | null;
  nonStrategicReason?: string | null;
  expectedResult?: string;
  confidence?: number | null;
  tags?: string[];
  progressNotes?: string;
}

export interface UpdateActualRequest {
  actualResult: string;
  completionStatus: CompletionStatus;
  deltaReason?: string | null;
  timeSpent?: number | null;
}

// ─── Manager Endpoints ──────────────────────────────────────

export interface ReviewStatusCounts {
  pending: number;
  approved: number;
  changesRequested: number;
}

export interface TeamMemberSummary {
  userId: string;
  displayName: string | null;
  planId: string | null;
  state: PlanState | null;
  reviewStatus: ReviewStatus | null;
  commitCount: number;
  /** Count of saved actuals with completionStatus != DONE for RECONCILED/CARRY_FORWARD plans. */
  incompleteCount: number;
  /** Count of commits with validation errors (missing required fields). */
  issueCount: number;
  nonStrategicCount: number;
  kingCount: number;
  queenCount: number;
  lastUpdated: string | null;
  isStale: boolean;
  isLateLock: boolean;
}

export interface TeamSummaryResponse {
  weekStart: string;
  users: TeamMemberSummary[];
  reviewStatusCounts: ReviewStatusCounts;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type ReviewDecision = "APPROVED" | "CHANGES_REQUESTED";

export interface CreateReviewRequest {
  decision: ReviewDecision;
  comments: string;
}

// ─── RCDO Roll-up Endpoints ──────────────────────────────────

export interface RcdoRollupItem {
  outcomeId: string;
  outcomeName: string | null;
  objectiveId: string | null;
  objectiveName: string | null;
  rallyCryId: string | null;
  rallyCryName: string | null;
  commitCount: number;
  kingCount: number;
  queenCount: number;
  rookCount: number;
  bishopCount: number;
  knightCount: number;
  pawnCount: number;
}

export interface RcdoRollupResponse {
  weekStart: string;
  items: RcdoRollupItem[];
  nonStrategicCount: number;
}

// ─── Notification Endpoints ─────────────────────────────────

export interface NotificationItem {
  id: string;
  type: string;
  payload: Record<string, unknown>;
  read: boolean;
  createdAt: string;
}

// ─── RCDO Endpoints ─────────────────────────────────────────

export interface RcdoTreeResponse {
  rallyCries: RcdoCry[];
}

export interface RcdoSearchResult {
  id: string;
  name: string;
  objectiveId: string;
  objectiveName: string;
  rallyCryId: string;
  rallyCryName: string;
}

export interface RcdoSearchResponse {
  results: RcdoSearchResult[];
}

// ─── AI Endpoints ───────────────────────────────────────────

export interface SuggestRcdoRequest {
  title: string;
  description?: string;
}

export interface RcdoSuggestion {
  outcomeId: string;
  rallyCryName: string;
  objectiveName: string;
  outcomeName: string;
  confidence: number;
  rationale: string;
}

export type AiSuggestionStatus = "ok" | "unavailable";

export interface SuggestRcdoResponse {
  status: AiSuggestionStatus;
  suggestions: RcdoSuggestion[];
}

export interface DraftReconciliationRequest {
  planId: string;
}

export interface ReconciliationDraftItem {
  commitId: string;
  suggestedStatus: CompletionStatus;
  suggestedDeltaReason: string | null;
  suggestedActualResult: string;
}

export interface DraftReconciliationResponse {
  status: AiSuggestionStatus;
  drafts: ReconciliationDraftItem[];
}

export interface ManagerInsightsRequest {
  weekStart: string;
}

export interface ManagerInsightItem {
  title: string;
  detail: string;
  severity: "INFO" | "WARNING" | "POSITIVE";
}

export interface ManagerInsightsResponse {
  status: AiSuggestionStatus;
  headline: string | null;
  insights: ManagerInsightItem[];
}

// ─── Paginated Response Wrapper ─────────────────────────────

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// Re-export entity types for convenience
export type {
  WeeklyPlan,
  WeeklyCommit,
  WeeklyCommitActual,
  ManagerReview,
};
