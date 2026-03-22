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
  EffortType,
  IssueStatus,
  TeamRole,
  AccessRequestStatus,
} from "./enums.js";
import type { NotificationType } from "./events.js";
import type { ErrorCode } from "./errors.js";
import type {
  WeeklyPlan,
  WeeklyCommit,
  WeeklyCommitActual,
  ManagerReview,
  RcdoCry,
  CheckInStatus,
  CheckInEntry,
  Issue,
  WeeklyAssignment,
  WeeklyAssignmentActual,
  IssueActivity,
  Team,
  TeamMember,
  TeamAccessRequest,
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
  estimatedHours?: number | null;
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
  estimatedHours?: number | null;
}

export interface UpdateActualRequest {
  actualResult: string;
  completionStatus: CompletionStatus;
  deltaReason?: string | null;
  timeSpent?: number | null;
  actualHours?: number | null;
}

// ─── Check-In Endpoints ─────────────────────────────────────

export interface CheckInRequest {
  status: CheckInStatus;
  note?: string;
}

export type QuickUpdateNoteSource = "UNKNOWN" | "USER_TYPED" | "SUGGESTION_ACCEPTED";

export interface QuickUpdateItem {
  commitId: string;
  status: CheckInStatus;
  note?: string | null;
  noteSource?: QuickUpdateNoteSource | null;
  selectedSuggestionText?: string | null;
  selectedSuggestionSource?: string | null;
}

export interface QuickUpdateRequest {
  updates: QuickUpdateItem[];
}

export interface QuickUpdateResponse {
  updatedCount: number;
  entries: CheckInEntry[];
}

export interface CheckInOptionRequest {
  commitId: string;
  currentStatus?: string | null;
  lastNote?: string | null;
  daysSinceLastCheckIn?: number;
}

export type CheckInOptionSource = "user_history" | "team_common" | "ai_generated";

export interface CheckInOptionItem {
  text: string;
  source: CheckInOptionSource;
}

export interface CheckInOptionsResponse {
  status: "ok";
  statusOptions: CheckInStatus[];
  progressOptions: CheckInOptionItem[];
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

export type NotificationTypeValue = `${NotificationType}`;

/** Payload emitted when the weekly-planning agent creates a draft plan. */
export interface WeeklyPlanDraftReadyNotificationPayload {
  planId: string;
  weekStartDate: string;
  route: string;
  message: string;
  suggestedCommitCount: number;
  suggestedHours: string;
  capacityHours: string;
}

/** Payload emitted when the misalignment agent briefs a manager on team risk. */
export interface PlanMisalignmentBriefingNotificationPayload {
  managerId: string;
  teamName: string;
  weekStartDate: string;
  route: string;
  message: string;
  overloadedMembers: string[];
  urgentOutcomesNeedingAttention: string[];
  highUrgencyHours: string;
  nonUrgentHours: string;
  flaggedPlanIds: string[];
  concernCount: number;
}

export interface NotificationItem {
  id: string;
  type: NotificationTypeValue;
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

// ─── Plan Quality Check Endpoints ──────────────────────────

/**
 * A data-driven quality nudge surfaced before plan locking (Wave 1).
 *
 * Nudges are advisory — the user can always proceed to lock regardless.
 */
export interface QualityNudge {
  /** Machine-readable nudge type (e.g. COVERAGE_GAP, CHESS_NO_KING). */
  type: string;
  /** Human-readable explanation shown in the UI. */
  message: string;
  severity: "INFO" | "WARNING" | "POSITIVE";
}

export interface PlanQualityCheckRequest {
  planId: string;
}

export interface PlanQualityCheckResponse {
  status: AiSuggestionStatus;
  nudges: QualityNudge[];
}

// ─── "Start My Week" — Draft from History ──────────────────

/**
 * Source tag indicating why a commit was suggested by the draft-from-history service.
 *
 * - CARRIED_FORWARD: not completed (status != DONE) in the most recent reconciled plan
 * - RECURRING: similar title or outcome ID appeared in 2+ consecutive historical weeks
 * - COVERAGE_GAP: coverage gap (reserved for future NextWorkService integration)
 */
export type CommitSourceType = "CARRIED_FORWARD" | "RECURRING" | "COVERAGE_GAP";

/** A single AI-suggested commit returned by the draft-from-history endpoint. */
export interface SuggestedCommit {
  /** ID of the newly created DRAFT commit. */
  commitId: string;
  title: string;
  description?: string | null;
  chessPriority?: ChessPriority | null;
  category?: CommitCategory | null;
  outcomeId?: string | null;
  nonStrategicReason?: string | null;
  expectedResult?: string | null;
  /** Why this commit was suggested. */
  source: CommitSourceType;
}

export interface DraftFromHistoryRequest {
  /** ISO date of the Monday that starts the target week (e.g. 2026-03-16). */
  weekStart: string;
}

export interface DraftFromHistoryResponse {
  /** ID of the created or updated DRAFT plan. */
  planId: string;
  /** Ordered list of suggested commits added to the draft plan. */
  suggestedCommits: SuggestedCommit[];
}

// ─── User Profile (Phase 1) ────────────────────────────────

export type TrendDirection = "IMPROVING" | "STABLE" | "WORSENING";

export interface PerformanceProfile {
  estimationAccuracy: number;
  completionReliability: number;
  avgCommitsPerWeek: number;
  avgCarryForwardPerWeek: number;
  topCategories: string[];
  categoryCompletionRates: Record<string, number>;
  priorityCompletionRates: Record<string, number>;
}

export interface UserPreferences {
  typicalPriorityPattern: string;
  recurringCommitTitles: string[];
  avgCheckInsPerWeek: number;
  preferredUpdateDays: string[];
}

export interface UserTrends {
  strategicAlignmentTrend: TrendDirection;
  completionTrend: TrendDirection;
  carryForwardTrend: TrendDirection;
}

export interface UserProfileResponse {
  userId: string;
  weeksAnalyzed: number;
  performanceProfile: PerformanceProfile | null;
  preferences: UserPreferences | null;
  trends: UserTrends | null;
}

// ─── Outcome Urgency (Phase 3) ─────────────────────────────

export type OutcomeProgressType = "ACTIVITY" | "METRIC" | "MILESTONE";
export type OutcomeUrgencyBand = "NO_TARGET" | "ON_TRACK" | "NEEDS_ATTENTION" | "AT_RISK" | "CRITICAL";
export type StrategicSlackBand = "HIGH_SLACK" | "MODERATE_SLACK" | "LOW_SLACK" | "NO_SLACK";
export type MilestoneStatus = "PENDING" | "IN_PROGRESS" | "DONE";

export interface Milestone {
  name: string;
  weight?: number;
  status: MilestoneStatus;
}

export interface OutcomeMetadataRequest {
  targetDate?: string | null;
  progressType?: OutcomeProgressType;
  metricName?: string | null;
  targetValue?: number | null;
  currentValue?: number | null;
  unit?: string | null;
  milestones?: string | null;
}

export interface OutcomeMetadataResponse {
  orgId: string;
  outcomeId: string;
  targetDate?: string | null;
  progressType: OutcomeProgressType;
  metricName?: string | null;
  targetValue?: number | null;
  currentValue?: number | null;
  unit?: string | null;
  milestones?: string | null;
  progressPct?: number | null;
  urgencyBand?: OutcomeUrgencyBand | null;
  lastComputedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProgressUpdateRequest {
  currentValue?: number | null;
  milestones?: string | null;
}

export interface UrgencyInfo {
  outcomeId: string;
  outcomeName: string;
  targetDate?: string | null;
  progressPct?: number | null;
  expectedProgressPct?: number | null;
  urgencyBand: OutcomeUrgencyBand;
  daysRemaining: number;
}

export interface SlackInfo {
  slackBand: StrategicSlackBand;
  strategicFocusFloor: number;
  atRiskCount: number;
  criticalCount: number;
}

export interface UrgencySummaryResponse {
  outcomes: UrgencyInfo[];
}

export interface StrategicSlackResponse {
  slack: SlackInfo;
}

// ─── Phase 5: Forecasting & Planning Intelligence ───────────

export type ForecastConfidenceBand = "LOW" | "MEDIUM" | "HIGH";
export type ForecastStatus = "NO_DATA" | "NO_TARGET_DATE" | "COMPLETE" | "ON_TRACK" | "NEEDS_ATTENTION" | "AT_RISK";

export interface OutcomeForecastFactor {
  type: string;
  label: string;
  score: number;
  detail: string;
}

export interface OutcomeForecastResponse {
  outcomeId: string;
  outcomeName: string;
  targetDate: string | null;
  projectedTargetDate: string | null;
  projectedProgressPct: number | null;
  projectedVelocity: number | null;
  confidenceScore: number | null;
  confidenceBand: ForecastConfidenceBand | null;
  forecastStatus: ForecastStatus | null;
  modelVersion: string | null;
  contributingFactors: OutcomeForecastFactor[];
  recommendations: string[];
  computedAt: string | null;
}

export interface OutcomeForecastListResponse {
  forecasts: OutcomeForecastResponse[];
}

export interface TeamPlanSuggestionRequest {
  weekStart: string;
}

export interface TeamPlanSuggestionUnavailableResponse {
  status: "unavailable";
}

export interface TeamPlanSummary {
  teamCapacityHours: number | null;
  suggestedHours: number | null;
  bufferHours: number | null;
  atRiskOutcomeCount: number;
  criticalOutcomeCount: number;
  strategicFocusFloor: number | null;
  headline: string | null;
}

export interface MemberOutcomeSlice {
  userId: string;
  displayName: string | null;
  hours: number | null;
  title: string | null;
}

export interface OutcomeAllocationSuggestion {
  outcomeId: string;
  outcomeName: string;
  urgencyBand: OutcomeUrgencyBand;
  recommendedHours: number | null;
  members: MemberOutcomeSlice[];
}

export interface PlanningCopilotSuggestedCommit {
  title: string;
  outcomeId: string | null;
  chessPriority: ChessPriority | null;
  estimatedHours: number | null;
  rationale: string | null;
  source: string | null;
}

export interface TeamMemberPlanSuggestion {
  userId: string;
  displayName: string | null;
  suggestedCommits: PlanningCopilotSuggestedCommit[];
  totalEstimated: number | null;
  realisticCapacity: number | null;
  overcommitRisk: string | null;
  strengthSummary: string | null;
}

export interface TeamPlanSuggestionResponse {
  status: "ok";
  weekStart: string;
  summary: TeamPlanSummary;
  members: TeamMemberPlanSuggestion[];
  outcomeAllocations: OutcomeAllocationSuggestion[];
  llmRefined: boolean;
}

export interface SuggestedCommitApplyRequest {
  title: string;
  outcomeId?: string | null;
  rationale?: string | null;
  chessPriority: ChessPriority;
  estimatedHours?: number | null;
}

export interface TeamMemberApplyRequest {
  userId: string;
  suggestedCommits: SuggestedCommitApplyRequest[];
}

export interface ApplyTeamPlanSuggestionRequest {
  weekStart: string;
  members: TeamMemberApplyRequest[];
}

export interface MemberDraftApplyResult {
  userId: string;
  displayName: string | null;
  planId: string;
  createdPlan: boolean;
  appliedCommits: WeeklyCommit[];
}

export interface ApplyTeamPlanSuggestionResponse {
  status: "ok";
  weekStart: string;
  members: MemberDraftApplyResult[];
}

export interface ExecutiveDashboardUnavailableResponse {
  status: "unavailable";
}

export interface ExecutiveSummary {
  totalForecasts: number;
  onTrackForecasts: number;
  needsAttentionForecasts: number;
  offTrackForecasts: number;
  noDataForecasts: number;
  averageForecastConfidence: number | null;
  totalCapacityHours: number | null;
  strategicHours: number | null;
  nonStrategicHours: number | null;
  strategicCapacityUtilizationPct: number | null;
  nonStrategicCapacityUtilizationPct: number | null;
  planningCoveragePct: number | null;
}

export interface RallyCryHealthRollup {
  rallyCryId: string | null;
  rallyCryName: string;
  forecastedOutcomeCount: number;
  onTrackCount: number;
  needsAttentionCount: number;
  offTrackCount: number;
  noDataCount: number;
  averageForecastConfidence: number | null;
  strategicHours: number | null;
}

export interface TeamBucketComparison {
  bucketId: string;
  memberCount: number;
  planCoveragePct: number | null;
  totalCapacityHours: number | null;
  strategicHours: number | null;
  nonStrategicHours: number | null;
  strategicCapacityUtilizationPct: number | null;
  averageForecastConfidence: number | null;
}

export interface ExecutiveDashboardResponse {
  weekStart: string;
  summary: ExecutiveSummary;
  rallyCryRollups: RallyCryHealthRollup[];
  teamBuckets: TeamBucketComparison[];
  teamGroupingAvailable: boolean;
}

export interface ExecutiveBriefingRequest {
  weekStart: string;
}

export interface ExecutiveBriefingItem {
  title: string;
  detail: string;
  severity: "INFO" | "WARNING" | "POSITIVE";
}

export interface ExecutiveBriefingResponse {
  status: "ok" | "unavailable";
  headline: string | null;
  insights: ExecutiveBriefingItem[];
}

// ─── Capacity Planning (Phase 4) ───────────────────────────

/** Data quality tier for a computed capacity profile. */
export type CapacityConfidenceLevel = "LOW" | "MEDIUM" | "HIGH";

/** Overcommitment severity for a single team member in a week. */
export type OvercommitLevel = "NONE" | "MODERATE" | "HIGH";

/**
 * User capacity profile returned by {@code GET /users/me/capacity}.
 *
 * Stores rolling historical metrics derived from estimated vs actual hours.
 */
export interface CapacityProfile {
  orgId: string;
  userId: string;
  weeksAnalyzed: number;
  avgEstimatedHours: number | null;
  avgActualHours: number | null;
  /** Historical actual/estimated ratio. */
  estimationBias: number | null;
  /** Sustainable weekly-capacity estimate derived from p50 actual hours. */
  realisticWeeklyCap: number | null;
  /** Raw JSON array string of per-category bias breakdowns. */
  categoryBiasJson: string | null;
  /** Raw JSON array string of per-priority completion statistics. */
  priorityCompletionJson: string | null;
  confidenceLevel: CapacityConfidenceLevel;
  /** ISO-8601 timestamp of the latest computation. */
  computedAt: string | null;
}

/** Backwards-compatible alias matching the OpenAPI schema name. */
export type CapacityProfileResponse = CapacityProfile;

/** Per-member capacity summary in the manager team-capacity view. */
export interface TeamMemberCapacity {
  userId: string;
  name: string | null;
  estimatedHours: number;
  adjustedEstimate: number;
  realisticCap: number | null;
  overcommitLevel: OvercommitLevel;
}

/** Manager team-capacity response returned by {@code GET /team/capacity}. */
export interface TeamCapacityResponse {
  weekStart: string;
  members: TeamMemberCapacity[];
}

/** Per-category estimation-bias insight in the coaching response. */
export interface CategoryInsight {
  category: string;
  bias: number | null;
  tip: string | null;
}

/** Per-priority historical completion insight in the coaching response. */
export interface PriorityInsight {
  priority: string;
  completionRate: number;
  sampleSize: number;
}

/**
 * Estimation coaching response returned by
 * {@code GET /users/me/estimation-coaching?planId=X}.
 */
export interface EstimationCoachingResponse {
  thisWeekEstimated: number;
  thisWeekActual: number;
  accuracyRatio: number | null;
  overallBias: number | null;
  confidenceLevel: CapacityConfidenceLevel;
  categoryInsights: CategoryInsight[];
  priorityInsights: PriorityInsight[];
}

// ─── Admin: Adoption Metrics ────────────────────────────────

/** Per-week adoption funnel counts for the admin dashboard. */
export interface WeeklyAdoptionPoint {
  /** ISO date (Monday). */
  weekStart: string;
  /** Distinct users with any plan this week. */
  activeUsers: number;
  /** Total plans in any state this week. */
  plansCreated: number;
  /** Plans that reached LOCKED state or beyond. */
  plansLocked: number;
  /** Plans that reached RECONCILED or CARRY_FORWARD state. */
  plansReconciled: number;
  /** Plans where a manager review was submitted. */
  plansReviewed: number;
}

/** Weekly adoption funnel metrics returned by {@code GET /admin/adoption-metrics}. */
export interface AdoptionMetrics {
  /** Number of weeks in the rolling window. */
  weeks: number;
  /** ISO date — earliest Monday in the rolling window. */
  windowStart: string;
  /** ISO date — most recent Monday in the rolling window. */
  windowEnd: string;
  /** Distinct users with any plan in the rolling window. */
  totalActiveUsers: number;
  /** Fraction of locked plans that were locked ON_TIME (not LATE_LOCK). */
  cadenceComplianceRate: number;
  /** Per-week funnel breakdown, oldest to newest. */
  weeklyPoints: WeeklyAdoptionPoint[];
}

// ─── Admin: AI Usage Metrics ────────────────────────────────

/** AI feature usage metrics returned by {@code GET /admin/ai-usage}. */
export interface AiUsageMetrics {
  /** Number of weeks in the rolling window. */
  weeks: number;
  /** ISO date — start of the rolling window. */
  windowStart: string;
  /** ISO date — end of the rolling window. */
  windowEnd: string;
  /** Total suggestion feedback records in the window. */
  totalFeedbackCount: number;
  /** Count of ACCEPT actions. */
  acceptedCount: number;
  /** Count of DEFER actions. */
  deferredCount: number;
  /** Count of DECLINE actions. */
  declinedCount: number;
  /** acceptedCount / totalFeedbackCount (0 if no feedback). */
  acceptanceRate: number;
  /** Cumulative AI cache hits since the last service restart. */
  cacheHits: number;
  /** Cumulative AI cache misses since the last service restart. */
  cacheMisses: number;
  /** cacheHits / (cacheHits + cacheMisses) (0 if no cache activity). */
  cacheHitRate: number;
  /** Coarse estimate of AI tokens spent (cacheMisses × 1000). */
  approximateTokensSpent: number;
  /** Coarse estimate of AI tokens saved by cache (cacheHits × 1000). */
  approximateTokensSaved: number;
}

// ─── Admin: RCDO Health ─────────────────────────────────────

/** Commit coverage data for a single RCDO outcome. */
export interface OutcomeHealthItem {
  outcomeId: string;
  outcomeName: string;
  objectiveId: string;
  objectiveName: string;
  rallyCryId: string;
  rallyCryName: string;
  /** Number of commits linked to this outcome in the analysis window. */
  commitCount: number;
}

/**
 * RCDO health report returned by {@code GET /admin/rcdo-health}.
 *
 * Ranks outcomes by commit coverage; outcomes with zero commits are
 * surfaced as stale.
 */
export interface RcdoHealthReport {
  /** ISO timestamp when the report was generated. */
  generatedAt: string;
  /** Number of weeks examined. */
  windowWeeks: number;
  /** Total outcomes in the RCDO tree. */
  totalOutcomes: number;
  /** Outcomes with at least one commit in the window. */
  coveredOutcomes: number;
  /** Covered outcomes ranked by commitCount descending. */
  topOutcomes: OutcomeHealthItem[];
  /** Outcomes with zero commits in the window, sorted by name. */
  staleOutcomes: OutcomeHealthItem[];
}

// ─── Admin: Org Policy ──────────────────────────────────────

/**
 * Snapshot of an org's configuration. Returned by {@code GET /admin/org-policy}
 * and the digest-config {@code PATCH} endpoints.
 */
export interface OrgPolicy {
  chessKingRequired: boolean;
  chessMaxKing: number;
  chessMaxQueen: number;
  /** Day-of-week for the plan lock reminder (e.g. "MONDAY"). */
  lockDay: string;
  /** Wall-clock time for the plan lock reminder in HH:mm format. */
  lockTime: string;
  /** Day-of-week for the reconciliation reminder (e.g. "FRIDAY"). */
  reconcileDay: string;
  /** Wall-clock time for the reconciliation reminder in HH:mm format. */
  reconcileTime: string;
  blockLockOnStaleRcdo: boolean;
  rcdoStalenessThresholdMinutes: number;
  /**
   * Day-of-week for the weekly digest notification (e.g. "FRIDAY").
   * The DigestJob fires once the current time is on or after {@link digestTime} on this day.
   */
  digestDay: string;
  /** Wall-clock time for the weekly digest in HH:mm format (e.g. "17:00"). */
  digestTime: string;
}

/**
 * Request body for {@code PATCH /admin/org-policy/digest}.
 */
export interface UpdateDigestConfigRequest {
  /** Day-of-week for the weekly digest (e.g. "FRIDAY", "MONDAY"). */
  digestDay: string;
  /** Wall-clock time for the weekly digest in HH:mm format (e.g. "17:00"). */
  digestTime: string;
}

// ─── Paginated Response Wrapper ─────────────────────────────

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ─── Jira / Linear Integration (Wave 4) ────────────────────

/** Supported external issue-tracker providers. */
export type IntegrationProvider = "JIRA" | "LINEAR";

/** Request body for {@code POST /integrations/link-ticket}. */
export interface LinkTicketRequest {
  /** ID of the weekly commit to link. */
  commitId: string;
  /** External issue-tracker provider. */
  provider: IntegrationProvider;
  /** Provider-specific ticket identifier (e.g. "PROJ-42" for Jira). */
  externalTicketId: string;
}

/** A link between a weekly commit and an external issue-tracker ticket. */
export interface ExternalTicketLink {
  id: string;
  orgId: string;
  commitId: string;
  provider: IntegrationProvider;
  externalTicketId: string;
  /** URL to the ticket in the provider's web UI. */
  externalTicketUrl: string | null;
  /** Current ticket status as returned by the provider (e.g. "In Progress"). */
  externalStatus: string | null;
  /** ISO timestamp of the most recent status sync. */
  lastSyncedAt: string | null;
  createdAt: string;
}

/** Response for {@code GET /commits/{commitId}/linked-tickets}. */
export interface LinkedTicketsResponse {
  commitId: string;
  links: ExternalTicketLink[];
}

/** Response for {@code POST /integrations/webhook/{provider}}. */
export interface WebhookResponse {
  provider: IntegrationProvider;
  checkInsCreated: number;
}

// Re-export entity types for convenience
export type { WeeklyPlan, WeeklyCommit, WeeklyCommitActual, ManagerReview };

// ─── Phase 6: Issue Backlog, Teams & AI Work Intelligence ───

// Issue CRUD

export interface CreateIssueRequest {
  title: string;
  description?: string | null;
  effortType?: EffortType | null;
  estimatedHours?: number | null;
  chessPriority?: ChessPriority | null;
  outcomeId?: string | null;
  nonStrategicReason?: string | null;
  assigneeUserId?: string | null;
  blockedByIssueId?: string | null;
}

export interface UpdateIssueRequest {
  title?: string;
  description?: string | null;
  effortType?: EffortType | null;
  estimatedHours?: number | null;
  chessPriority?: ChessPriority | null;
  outcomeId?: string | null;
  nonStrategicReason?: string | null;
  assigneeUserId?: string | null;
  blockedByIssueId?: string | null;
  status?: IssueStatus;
  version?: number;
}

/** Paginated list of issues, with optional activity audit trail */
export interface IssueListResponse {
  content: Issue[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface IssueDetailResponse {
  issue: Issue;
  activities: IssueActivity[];
}

// Issue action requests

export interface AssignIssueRequest {
  assigneeUserId: string | null;
}

export interface CommitIssueToWeekRequest {
  weekStart: string;
  chessPriorityOverride?: ChessPriority | null;
  expectedResult?: string | null;
  confidence?: number | null;
}

export interface CreateWeeklyAssignmentRequest {
  issueId: string;
  chessPriorityOverride?: ChessPriority | null;
  expectedResult?: string | null;
  confidence?: number | null;
}

export interface ReleaseIssueRequest {
  weeklyPlanId: string;
}

export interface AddCommentRequest {
  commentText: string;
}

export interface LogTimeEntryRequest {
  hoursLogged: number;
  note?: string | null;
}

// Weekly assignment responses

export interface WeeklyAssignmentWithActual extends WeeklyAssignment {
  actual?: WeeklyAssignmentActual | null;
  issue?: Issue | null;
}

export interface WeeklyAssignmentsResponse {
  assignments: WeeklyAssignmentWithActual[];
}

// Team management

export interface CreateTeamRequest {
  name: string;
  keyPrefix: string;
  description?: string | null;
}

export interface UpdateTeamRequest {
  name?: string;
  description?: string | null;
}

export interface AddTeamMemberRequest {
  userId: string;
  role?: TeamRole;
}

export interface TeamListResponse {
  teams: Team[];
}

export interface TeamDetailResponse {
  team: Team;
  members: TeamMember[];
}

export interface TeamAccessRequestAction {
  status: AccessRequestStatus;
}

export interface TeamAccessRequestListResponse {
  requests: TeamAccessRequest[];
}

// AI: Suggest effort type

export interface SuggestEffortTypeRequest {
  issueId?: string | null;
  title: string;
  description?: string | null;
}

export interface SuggestEffortTypeResponse {
  status: AiSuggestionStatus;
  suggestedEffortType: EffortType | null;
  rationale: string | null;
  confidence: number | null;
}

// AI: Backlog ranking

export interface RankBacklogRequest {
  teamId: string;
  issueIds?: string[];
}

export interface RankedIssue {
  issueId: string;
  rank: number;
  rationale: string;
}

export interface RankBacklogResponse {
  status: AiSuggestionStatus;
  rankedIssues: RankedIssue[];
}

// AI: Recommend weekly issues

export interface RecommendWeeklyIssuesRequest {
  weekStart: string;
  teamId?: string | null;
  maxItems?: number | null;
}

export interface RecommendedIssue {
  issueId: string;
  issueKey: string;
  title: string;
  effortType: EffortType | null;
  chessPriority: ChessPriority | null;
  rationale: string;
  confidence: number;
}

export interface RecommendWeeklyIssuesResponse {
  status: AiSuggestionStatus;
  recommendations: RecommendedIssue[];
}

// AI: Suggest deferrals (overcommit detection)

export interface SuggestDeferralsRequest {
  weeklyPlanId: string;
}

export interface DeferralSuggestion {
  issueId: string;
  issueKey: string;
  title: string;
  reason: string;
  impactIfDeferred: string;
}

export interface SuggestDeferralsResponse {
  status: AiSuggestionStatus;
  suggestions: DeferralSuggestion[];
}

// AI: Coverage gap inspirations

export interface CoverageGapInspirationsResponse {
  status: AiSuggestionStatus;
  inspirations: Array<{
    outcomeId: string | null;
    outcomeName: string | null;
    suggestedTitle: string;
    rationale: string;
    suggestedEffortType: EffortType | null;
  }>;
}

// Semantic search (RAG)

export interface SemanticSearchRequest {
  query: string;
  teamId?: string | null;
  effortType?: EffortType | null;
  status?: IssueStatus | null;
  limit?: number | null;
}

export interface SemanticSearchHit {
  issueId: string;
  issueKey: string;
  title: string;
  score: number;
  effortType: EffortType | null;
  status: IssueStatus;
}

export interface SemanticSearchResponse {
  status: AiSuggestionStatus;
  hits: SemanticSearchHit[];
}
