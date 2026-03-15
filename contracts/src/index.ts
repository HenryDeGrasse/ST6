/**
 * @weekly-commitments/contracts
 *
 * Shared TypeScript type definitions and enumerations for the
 * Weekly Commitments module. Both the micro-frontend and the
 * PA host stub consume these types.
 */

export {
  PlanState,
  ReviewStatus,
  ChessPriority,
  CompletionStatus,
  LockType,
  CommitCategory,
} from "./enums.js";

export type {
  WeeklyPlan,
  WeeklyCommit,
  WeeklyCommitActual,
  ManagerReview,
  RcdoOutcome,
  RcdoObjective,
  RcdoCry,
  ValidationError,
} from "./types.js";

export { ErrorCode, ERROR_HTTP_STATUS } from "./errors.js";
export type { ApiError, ApiErrorDetail, ApiErrorResponse } from "./api.js";

export {
  EventType,
  AggregateType,
  NotificationType,
} from "./events.js";
export type { OutboxEvent } from "./events.js";

export {
  createWeeklyCommitmentsClient,
} from "./client.js";
export type {
  WeeklyCommitmentsClient,
  WeeklyCommitmentsClientOptions,
  WeeklyCommitmentsApiPaths,
  WeeklyCommitmentsApiComponents,
  WeeklyCommitmentsApiOperations,
} from "./client.js";

export type {
  CreatePlanRequest,
  LockPlanRequest,
  StartReconciliationRequest,
  SubmitReconciliationRequest,
  CarryForwardRequest,
  CreateCommitRequest,
  UpdateCommitRequest,
  UpdateActualRequest,
  ReviewStatusCounts,
  TeamMemberSummary,
  TeamSummaryResponse,
  ReviewDecision,
  CreateReviewRequest,
  RcdoRollupItem,
  RcdoRollupResponse,
  NotificationItem,
  RcdoTreeResponse,
  RcdoSearchResult,
  RcdoSearchResponse,
  SuggestRcdoRequest,
  RcdoSuggestion,
  AiSuggestionStatus,
  SuggestRcdoResponse,
  DraftReconciliationRequest,
  ReconciliationDraftItem,
  DraftReconciliationResponse,
  ManagerInsightsRequest,
  ManagerInsightItem,
  ManagerInsightsResponse,
  PaginatedResponse,
} from "./api.js";
