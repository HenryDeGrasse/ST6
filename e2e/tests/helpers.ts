/**
 * Shared E2E test utilities for the Weekly Commitments test suite.
 *
 * Sections:
 *   1. API test constants  – real seeded backend IDs
 *   2. Browser/mock test constants – deterministic mock IDs
 *   3. Date utilities  – mondayOf(), mondayIso()
 *   4. API user helpers – freshUserId(), tokenFor()
 *   5. Core HTTP helper – api(), ApiResponse
 *   6. Plan/Commit API helpers – getPlan(), getCommits(), createPlan(), …
 *   7. Error assertion helpers – errorOf(), detailsOf(), errorHasCode()
 *   8. Browser builders – buildPlan(), buildCommit()
 *   9. Response helpers – json(), apiError()
 *  10. Mock API installer – MockApiOptions, installMockApi()
 */
import { expect, type Page, type Route } from '@playwright/test';

// ═══════════════════════════════════════════════════════════════════════════
// 1. API test constants (real seeded backend data)
// ═══════════════════════════════════════════════════════════════════════════

export const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080';

/** Real seeded org ID used by the dev/local backend profile. */
export const ORG_ID = 'a0000000-0000-0000-0000-000000000001';

/**
 * Seed user Carol Park — registered as both IC and her own manager in the
 * dev org graph (self-manages for demo purposes). See SEED_USER_ID usages in
 * full-week-lifecycle.spec.ts for the manager-review test.
 */
export const SEED_USER_ID = 'c0000000-0000-0000-0000-000000000001';

/** RCDO outcome IDs from RcdoDevDataInitializer (local profile seed data). */
export const OUTCOME_ENTERPRISE_DEALS = 'e0000000-0000-0000-0000-000000000001';
export const OUTCOME_API_UPTIME       = 'e0000000-0000-0000-0000-000000000002';
export const OUTCOME_DEMO_ENV         = '30000000-0000-0000-0000-000000000002';
export const OUTCOME_SALES_CYCLE      = '30000000-0000-0000-0000-000000000003';

// ═══════════════════════════════════════════════════════════════════════════
// 2. Browser / mock test constants (deterministic IDs for mocked APIs)
// ═══════════════════════════════════════════════════════════════════════════

export const MOCK_ORG_ID            = '00000000-0000-0000-0000-000000000099';
export const MOCK_USER_ID           = '00000000-0000-0000-0000-000000000001';
export const MOCK_MANAGER_USER_ID   = '00000000-0000-0000-0000-000000000101';
export const MOCK_REPORT_USER_ID    = '00000000-0000-0000-0000-000000000202';
export const MOCK_ADMIN_USER_ID     = 'c0000000-0000-0000-0000-000000000030';
export const MOCK_PLAN_ID           = '10000000-0000-0000-0000-000000000001';
export const MOCK_COMMIT_ID         = '20000000-0000-0000-0000-000000000001';
export const MOCK_OUTCOME_ID        = '30000000-0000-0000-0000-000000000001';
/** Frozen timestamp used for all mock createdAt/updatedAt/lockedAt fields. */
export const MOCK_NOW               = '2026-03-12T12:00:00Z';

/** Standard RCDO tree returned by the mock backend in browser-based tests. */
export const MOCK_RCDO_TREE = {
  rallyCries: [
    {
      id: 'rc-1',
      name: 'Scale Revenue',
      objectives: [
        {
          id: 'obj-1',
          name: 'Improve Conversion',
          rallyCryId: 'rc-1',
          outcomes: [
            {
              id: MOCK_OUTCOME_ID,
              name: 'Increase trial-to-paid by 20%',
              objectiveId: 'obj-1',
            },
          ],
        },
      ],
    },
  ],
};

// ═══════════════════════════════════════════════════════════════════════════
// 3. Date utilities
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Returns the ISO date string (YYYY-MM-DD) of the Monday that is
 * `weeksFromCurrent` weeks from the current week's Monday.
 *
 * weeksFromCurrent = 0  → this week's Monday
 * weeksFromCurrent = 1  → next week's Monday
 * weeksFromCurrent = -1 → last week's Monday
 */
export function mondayOf(weeksFromCurrent = 0): string {
  const today = new Date();
  const day = today.getUTCDay();
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(
    Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()),
  );
  monday.setUTCDate(monday.getUTCDate() + diffToMonday + weeksFromCurrent * 7);
  return monday.toISOString().slice(0, 10);
}

/**
 * Alias for mondayOf(). Preferred in browser-based tests where the name
 * "mondayIso" is more conventional within the PA host mock layer.
 */
export function mondayIso(weeksFromCurrent = 0): string {
  return mondayOf(weeksFromCurrent);
}

// ═══════════════════════════════════════════════════════════════════════════
// 4. API test user helpers
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Returns a fresh random UUID each call.
 * Each test group should use a fresh user to start with a clean slate —
 * the DevRequestAuthenticator accepts any UUID as a valid user.
 */
export function freshUserId(): string {
  return crypto.randomUUID();
}

/**
 * Builds the dev-mode Authorization header value for the given user and roles.
 * The DevRequestAuthenticator accepts tokens of the form
 * `Bearer dev:<userId>:<orgId>:<roles>`.
 */
export function tokenFor(userId: string, roles = 'IC'): string {
  return `Bearer dev:${userId}:${ORG_ID}:${roles}`;
}

// ═══════════════════════════════════════════════════════════════════════════
// 5. Core HTTP helper
// ═══════════════════════════════════════════════════════════════════════════

export interface ApiResponse {
  status: number;
  body: Record<string, unknown>;
}

/**
 * Low-level HTTP helper for API-level integration tests.
 *
 * Automatically sets Content-Type, Authorization, If-Match, and
 * Idempotency-Key headers from the options. Parses the response body as JSON
 * (falls back to `{ _raw: string }` on parse error).
 */
export async function api(
  method: string,
  path: string,
  options: {
    token?: string;
    body?: unknown;
    ifMatch?: number;
    idempotencyKey?: string;
  } = {},
): Promise<ApiResponse> {
  const headers: Record<string, string> = {
    Authorization: options.token ?? tokenFor(freshUserId()),
    'Content-Type': 'application/json',
  };
  if (options.ifMatch !== undefined) {
    headers['If-Match'] = String(options.ifMatch);
  }
  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined,
  });

  let body: Record<string, unknown> = {};
  const text = await res.text();
  if (text) {
    try {
      body = JSON.parse(text) as Record<string, unknown>;
    } catch {
      body = { _raw: text } as Record<string, unknown>;
    }
  }
  return { status: res.status, body };
}

// ═══════════════════════════════════════════════════════════════════════════
// 6. Plan / Commit API helpers
// ═══════════════════════════════════════════════════════════════════════════

export async function getPlan(planId: string, token: string): Promise<ApiResponse> {
  return api('GET', `/api/v1/plans/${planId}`, { token });
}

export async function getCommits(
  planId: string,
  token: string,
): Promise<{ status: number; body: Array<Record<string, unknown>> }> {
  const res = await api('GET', `/api/v1/plans/${planId}/commits`, { token });
  const body = Array.isArray(res.body) ? res.body : [];
  return { status: res.status, body };
}

export async function createPlan(weekStart: string, token: string): Promise<ApiResponse> {
  return api('POST', `/api/v1/weeks/${weekStart}/plans`, { token });
}

/**
 * Creates a commit on the given plan.
 * `chessPriority` and `category` are optional (null allowed during DRAFT)
 * to support tests that verify missing-chessPriority / missing-category validation.
 */
export async function createCommit(
  planId: string,
  commit: {
    title: string;
    chessPriority?: string | null;
    category?: string | null;
    outcomeId?: string;
    nonStrategicReason?: string;
    description?: string;
    expectedResult?: string;
    confidence?: number;
    tags?: string[];
  },
  token: string,
): Promise<ApiResponse> {
  return api('POST', `/api/v1/plans/${planId}/commits`, { body: commit, token });
}

export async function deleteCommit(commitId: string, token: string): Promise<ApiResponse> {
  return api('DELETE', `/api/v1/commits/${commitId}`, { token });
}

export async function lockPlan(
  planId: string,
  version: number,
  token: string,
  idempotencyKey?: string,
): Promise<ApiResponse> {
  return api('POST', `/api/v1/plans/${planId}/lock`, {
    ifMatch: version,
    idempotencyKey: idempotencyKey ?? crypto.randomUUID(),
    token,
  });
}

export async function startReconciliation(
  planId: string,
  version: number,
  token: string,
): Promise<ApiResponse> {
  return api('POST', `/api/v1/plans/${planId}/start-reconciliation`, {
    ifMatch: version,
    idempotencyKey: crypto.randomUUID(),
    token,
  });
}

export async function submitReconciliation(
  planId: string,
  version: number,
  token: string,
): Promise<ApiResponse> {
  return api('POST', `/api/v1/plans/${planId}/submit-reconciliation`, {
    ifMatch: version,
    idempotencyKey: crypto.randomUUID(),
    token,
  });
}

export async function updateActual(
  commitId: string,
  commitVersion: number,
  actual: {
    actualResult: string;
    completionStatus: string;
    deltaReason?: string;
    timeSpent?: number;
  },
  token: string,
): Promise<ApiResponse> {
  return api('PATCH', `/api/v1/commits/${commitId}/actual`, {
    ifMatch: commitVersion,
    body: actual,
    token,
  });
}

export async function carryForward(
  planId: string,
  version: number,
  commitIds: string[],
  token: string,
  idempotencyKey?: string,
): Promise<ApiResponse> {
  return api('POST', `/api/v1/plans/${planId}/carry-forward`, {
    ifMatch: version,
    idempotencyKey: idempotencyKey ?? crypto.randomUUID(),
    body: { commitIds },
    token,
  });
}

/**
 * Refreshes the in-memory RCDO cache so the staleness clock resets.
 * Required because the InMemoryRcdoClient marks data as stale after 60 min
 * (the dev data initializer sets it once at startup).
 */
export async function refreshRcdo(token: string): Promise<void> {
  const res = await api('POST', '/api/v1/rcdo/refresh', { token });
  expect(res.status).toBe(200);
}

// ═══════════════════════════════════════════════════════════════════════════
// 7. Error assertion helpers
// ═══════════════════════════════════════════════════════════════════════════

/** Extracts the error envelope from an API response body. */
export function errorOf(res: ApiResponse): Record<string, unknown> {
  return res.body.error as Record<string, unknown>;
}

/** Returns the error detail objects from the error envelope. */
export function detailsOf(error: Record<string, unknown>): Array<Record<string, unknown>> {
  return (error.details as Array<Record<string, unknown>>) ?? [];
}

/**
 * Returns true if `code` appears at the top level of the error OR anywhere
 * in error.details[n].code. Useful because the backend may surface some
 * commit-level codes (e.g. MISSING_CHESS_PRIORITY) only inside the details
 * array while the envelope uses a more general code at the top level.
 */
export function errorHasCode(error: Record<string, unknown>, code: string): boolean {
  if (error.code === code) return true;
  return detailsOf(error).some((d) => d.code === code);
}

// ═══════════════════════════════════════════════════════════════════════════
// 8. Browser test builders
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Builds a mock plan object with sensible DRAFT defaults.
 * All fields can be overridden via the `overrides` parameter.
 */
export function buildPlan(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: MOCK_PLAN_ID,
    orgId: MOCK_ORG_ID,
    ownerUserId: MOCK_USER_ID,
    weekStartDate: mondayIso(),
    state: 'DRAFT',
    reviewStatus: 'REVIEW_NOT_APPLICABLE',
    lockType: null,
    lockedAt: null,
    carryForwardExecutedAt: null,
    version: 1,
    createdAt: MOCK_NOW,
    updatedAt: MOCK_NOW,
    ...overrides,
  };
}

/**
 * Builds a mock commit object with sensible defaults.
 * All fields can be overridden via the `overrides` parameter.
 */
export function buildCommit(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: MOCK_COMMIT_ID,
    weeklyPlanId: MOCK_PLAN_ID,
    title: 'Ship planning APIs',
    description: 'Finalize lifecycle endpoints and validations.',
    chessPriority: 'KING',
    category: 'DELIVERY',
    outcomeId: MOCK_OUTCOME_ID,
    nonStrategicReason: null,
    expectedResult: 'Plan lifecycle endpoints are production ready.',
    confidence: 4,
    tags: ['api', 'backend'],
    progressNotes: '',
    snapshotRallyCryId: null,
    snapshotRallyCryName: null,
    snapshotObjectiveId: null,
    snapshotObjectiveName: null,
    snapshotOutcomeId: null,
    snapshotOutcomeName: null,
    carriedFromCommitId: null,
    version: 1,
    createdAt: MOCK_NOW,
    updatedAt: MOCK_NOW,
    validationErrors: [],
    actual: null,
    ...overrides,
  };
}

// ═══════════════════════════════════════════════════════════════════════════
// 9. Browser response helpers
// ═══════════════════════════════════════════════════════════════════════════

/** Fulfills a Playwright route with a JSON body and the given status code. */
export function json(route: Route, status: number, body: unknown): Promise<void> {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

/**
 * Builds a standard API error envelope for use in mock route handlers.
 * Matches the backend's ErrorResponse DTO shape.
 */
export function apiError(message: string, code = 'VALIDATION_ERROR'): Record<string, unknown> {
  return {
    error: {
      code,
      message,
      details: [],
    },
  };
}

// ═══════════════════════════════════════════════════════════════════════════
// 10. Mock API installer (golden-path / full-lifecycle browser pattern)
// ═══════════════════════════════════════════════════════════════════════════

// ── Mock response builders ───────────────────────────────────────────────

/**
 * Builds a realistic TrendsResponse with 4 week points.
 */
export function buildTrendsResponse(): Record<string, unknown> {
  const weeks = [
    mondayIso(-3),
    mondayIso(-2),
    mondayIso(-1),
    mondayIso(0),
  ];
  return {
    weeksAnalyzed: 4,
    windowStart: weeks[0],
    windowEnd: weeks[3],
    strategicAlignmentRate: 0.82,
    teamStrategicAlignmentRate: 0.74,
    avgCarryForwardPerWeek: 0.5,
    carryForwardStreak: 0,
    avgConfidence: 3.8,
    completionAccuracy: 0.85,
    confidenceAccuracyGap: -0.05,
    avgEstimatedHoursPerWeek: 32.0,
    avgActualHoursPerWeek: 28.5,
    hoursAccuracyRatio: 0.89,
    priorityDistribution: { KING: 0.2, QUEEN: 0.35, ROOK: 0.25, BISHOP: 0.15, KNIGHT: 0.05, PAWN: 0.0 },
    categoryDistribution: { DELIVERY: 0.55, PLANNING: 0.2, COLLABORATION: 0.15, LEARNING: 0.1 },
    weekPoints: weeks.map((weekStart, i) => ({
      weekStart,
      totalCommits: 5 + i,
      strategicCommits: 4 + i,
      carryForwardCommits: i === 0 ? 1 : 0,
      avgConfidence: 3.5 + i * 0.1,
      completionRate: 0.8 + i * 0.03,
      hasActuals: i < 3,
      priorityCounts: { KING: 1, QUEEN: 2, ROOK: 1, BISHOP: 1, KNIGHT: 0, PAWN: 0 },
      categoryCounts: { DELIVERY: 3, PLANNING: 1, COLLABORATION: 1, LEARNING: 0 },
      estimatedHours: 30 + i * 2,
      actualHours: i < 3 ? 27 + i * 2 : null,
      hoursAccuracyRatio: i < 3 ? 0.9 : null,
    })),
    insights: [
      {
        type: 'HIGH_STRATEGIC_ALIGNMENT',
        message: 'Your strategic alignment rate (82%) is above team average (74%).',
        severity: 'POSITIVE',
      },
      {
        type: 'CONSISTENT_COMPLETION',
        message: 'You have maintained above 80% completion rate for 4 consecutive weeks.',
        severity: 'POSITIVE',
      },
    ],
  };
}

/**
 * Builds a UserProfileResponse with 6 weeks analyzed, performance profile,
 * preferences, and trends.
 */
export function buildUserProfileResponse(): Record<string, unknown> {
  return {
    userId: MOCK_USER_ID,
    weeksAnalyzed: 6,
    performanceProfile: {
      estimationAccuracy: 0.87,
      completionReliability: 0.88,
      avgCommitsPerWeek: 5.5,
      avgCarryForwardPerWeek: 0.4,
      topCategories: ['DELIVERY', 'PLANNING'],
      categoryCompletionRates: {
        DELIVERY: 0.91,
        PLANNING: 0.83,
        COLLABORATION: 0.78,
        LEARNING: 0.70,
      },
      priorityCompletionRates: {
        KING: 0.97,
        QUEEN: 0.89,
        ROOK: 0.83,
        BISHOP: 0.75,
        KNIGHT: 0.65,
        PAWN: 0.50,
      },
    },
    preferences: {
      typicalPriorityPattern: 'Balanced — 1 KING, 2 QUEENs, 2 ROOKs',
      recurringCommitTitles: ['Weekly sync prep', 'Code review'],
      avgCheckInsPerWeek: 2.3,
      preferredUpdateDays: ['Tuesday', 'Thursday'],
    },
    trends: {
      strategicAlignmentTrend: 'IMPROVING',
      completionTrend: 'STABLE',
      carryForwardTrend: 'IMPROVING',
    },
  };
}

/**
 * Builds an ExecutiveDashboardResponse with summary, 2 rally cry rollups,
 * and 2 team buckets.
 */
export function buildExecutiveDashboardResponse(): Record<string, unknown> {
  return {
    weekStart: mondayIso(),
    summary: {
      totalForecasts: 12,
      onTrackForecasts: 7,
      needsAttentionForecasts: 3,
      offTrackForecasts: 2,
      noDataForecasts: 0,
      averageForecastConfidence: 0.72,
      totalCapacityHours: 320,
      strategicHours: 224,
      nonStrategicHours: 96,
      strategicCapacityUtilizationPct: 0.70,
      nonStrategicCapacityUtilizationPct: 0.30,
      planningCoveragePct: 0.88,
    },
    rallyCryRollups: [
      {
        rallyCryId: 'rc-1',
        rallyCryName: 'Scale Revenue',
        forecastedOutcomeCount: 7,
        onTrackCount: 4,
        needsAttentionCount: 2,
        offTrackCount: 1,
        noDataCount: 0,
        averageForecastConfidence: 0.75,
        strategicHours: 140,
      },
      {
        rallyCryId: 'rc-2',
        rallyCryName: 'Platform Reliability',
        forecastedOutcomeCount: 5,
        onTrackCount: 3,
        needsAttentionCount: 1,
        offTrackCount: 1,
        noDataCount: 0,
        averageForecastConfidence: 0.68,
        strategicHours: 84,
      },
    ],
    teamBuckets: [
      {
        bucketId: 'engineering',
        memberCount: 8,
        planCoveragePct: 0.92,
        totalCapacityHours: 200,
        strategicHours: 144,
        nonStrategicHours: 56,
        strategicCapacityUtilizationPct: 0.72,
        averageForecastConfidence: 0.74,
      },
      {
        bucketId: 'product',
        memberCount: 4,
        planCoveragePct: 0.80,
        totalCapacityHours: 120,
        strategicHours: 80,
        nonStrategicHours: 40,
        strategicCapacityUtilizationPct: 0.67,
        averageForecastConfidence: 0.69,
      },
    ],
    teamGroupingAvailable: true,
  };
}

/**
 * Builds an ExecutiveBriefingResponse with status 'ok', a headline, and
 * 3 insights (one each of INFO, WARNING, POSITIVE severity).
 */
export function buildExecutiveBriefingResponse(): Record<string, unknown> {
  return {
    status: 'ok',
    headline: '7 of 12 outcomes are on track; 2 outcomes need attention this week.',
    insights: [
      {
        title: 'Strong strategic alignment',
        detail: '82% of commits this week are linked to strategic outcomes, above the 74% team average.',
        severity: 'POSITIVE',
      },
      {
        title: 'Forecast confidence dipping',
        detail: '3 outcomes have forecast confidence below 60%. Consider reviewing scope or timelines.',
        severity: 'WARNING',
      },
      {
        title: 'Planning coverage at 88%',
        detail: '88% of team members have submitted a plan for the current week.',
        severity: 'INFO',
      },
    ],
  };
}

export interface MockApiOptions {
  /** Initial state of the current user's plan (null → 404 on GET /plans/me). */
  initialPlan?: Record<string, unknown> | null;
  /** Initial list of commits for MOCK_PLAN_ID. */
  commits?: Array<Record<string, unknown>>;
  /** Override the team summary user list. */
  teamSummaryUsers?: Array<Record<string, unknown>>;
  /** Override the team drill-down plan (default: seeded RECONCILED report plan). */
  teamPlan?: Record<string, unknown> | null;
  /** Override the team drill-down commit list. */
  teamCommits?: Array<Record<string, unknown>>;
  /** When true, the manager drill-down plan endpoint returns 403. */
  denyManagerDrillDown?: boolean;
  /** When true, createPlan seeds one default commit if the list is empty. */
  createPlanSeedsCommits?: boolean;
  /** Override executive dashboard response (null → 404). */
  executiveDashboard?: Record<string, unknown> | null;
  /** Override executive briefing response (null → 404). */
  executiveBriefing?: Record<string, unknown> | null;
}

/**
 * Installs a comprehensive mock API on the given Playwright page.
 *
 * Routes covered:
 *   GET  /api/v1/rcdo/tree
 *   GET  /api/v1/weeks/{week}/plans/me
 *   POST /api/v1/weeks/{week}/plans
 *   GET  /api/v1/plans/{id}/commits
 *   POST /api/v1/plans/{id}/lock
 *   POST /api/v1/plans/{id}/start-reconciliation
 *   POST /api/v1/plans/{id}/submit-reconciliation
 *   POST /api/v1/plans/{id}/carry-forward
 *   PATCH /api/v1/commits/{id}/actual
 *   GET  /api/v1/weeks/{week}/team/summary
 *   GET  /api/v1/weeks/{week}/team/rcdo-rollup
 *   GET  /api/v1/notifications/unread
 *   POST /api/v1/notifications/read-all
 *   POST /api/v1/ai/manager-insights
 *   GET  /api/v1/users/me/trends
 *   GET  /api/v1/users/me/profile
 *   GET  /api/v1/users/me/capacity
 *   GET  /api/v1/admin/org-policy
 *   PATCH /api/v1/admin/org-policy/digest
 *   GET  /api/v1/executive/strategic-health
 *   POST /api/v1/ai/executive-briefing
 *   POST /api/v1/ai/plan-quality-check
 *   GET  /api/v1/plans/{id}/quality-check
 *   POST /api/v1/ai/suggest-next-work
 *   GET  /api/v1/suggestions/next-work
 *   POST /api/v1/ai/suggestion-feedback
 *   GET  /api/v1/weeks/{week}/plans/{userId}       (manager drill-down)
 *   GET  /api/v1/weeks/{week}/plans/{userId}/commits (manager drill-down commits)
 *   POST /api/v1/plans/{id}/review
 */
export async function installMockApi(page: Page, options: MockApiOptions = {}): Promise<void> {
  const state = {
    plan: options.initialPlan ?? null,
    commits: options.commits ? [...options.commits] : [],
    teamPlan: options.teamPlan ?? buildPlan({
      id: '10000000-0000-0000-0000-000000000999',
      ownerUserId: MOCK_REPORT_USER_ID,
      state: 'RECONCILED',
      reviewStatus: 'REVIEW_PENDING',
      lockType: 'ON_TIME',
      lockedAt: MOCK_NOW,
    }),
    teamCommits: options.teamCommits ?? [
      buildCommit({ weeklyPlanId: '10000000-0000-0000-0000-000000000999' }),
    ],
    teamSummaryUsers: options.teamSummaryUsers ?? [
      {
        userId: MOCK_REPORT_USER_ID,
        planId: '10000000-0000-0000-0000-000000000999',
        state: 'RECONCILED',
        reviewStatus: 'REVIEW_PENDING',
        commitCount: 1,
        incompleteCount: 0,
        nonStrategicCount: 0,
        kingCount: 1,
        queenCount: 0,
        lastUpdated: MOCK_NOW,
        isStale: false,
        isLateLock: false,
      },
    ],
    orgPolicy: {
      chessKingRequired: true,
      chessMaxKing: 1,
      chessMaxQueen: 2,
      lockDay: 'MONDAY',
      lockTime: '10:00',
      reconcileDay: 'FRIDAY',
      reconcileTime: '16:00',
      blockLockOnStaleRcdo: false,
      rcdoStalenessThresholdMinutes: 1440,
      digestDay: 'FRIDAY',
      digestTime: '17:00',
    },
    actuals: new Map<string, Record<string, unknown>>(),
  };

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const requestedWeek = path.match(/^\/api\/v1\/weeks\/([^/]+)/)?.[1] ?? mondayIso();

    // ── RCDO tree ────────────────────────────────────────────────────────
    if (method === 'GET' && path === '/api/v1/rcdo/tree') {
      return json(route, 200, MOCK_RCDO_TREE);
    }

    // ── Current user plan (GET /weeks/{week}/plans/me) ────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/plans\/me$/.test(path)) {
      if (state.plan) {
        return json(route, 200, state.plan);
      }
      return json(route, 404, apiError('Plan not found', 'NOT_FOUND'));
    }

    // ── Create plan ───────────────────────────────────────────────────────
    if (method === 'POST' && /^\/api\/v1\/weeks\/[^/]+\/plans$/.test(path)) {
      state.plan = buildPlan({ weekStartDate: requestedWeek });
      if (options.createPlanSeedsCommits && state.commits.length === 0) {
        state.commits = [buildCommit()];
      }
      return json(route, 201, state.plan);
    }

    // ── List commits ──────────────────────────────────────────────────────
    if (method === 'GET' && path === `/api/v1/plans/${MOCK_PLAN_ID}/commits`) {
      return json(route, 200, state.commits);
    }

    // ── Lock plan ─────────────────────────────────────────────────────────
    if (method === 'POST' && path === `/api/v1/plans/${MOCK_PLAN_ID}/lock`) {
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: 'LOCKED',
        lockType: 'ON_TIME',
        lockedAt: MOCK_NOW,
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: MOCK_NOW,
      });
      return json(route, 200, state.plan);
    }

    // ── Start reconciliation ──────────────────────────────────────────────
    if (method === 'POST' && path === `/api/v1/plans/${MOCK_PLAN_ID}/start-reconciliation`) {
      const isLateLock = state.plan?.state === 'DRAFT';
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: 'RECONCILING',
        lockType: isLateLock ? 'LATE_LOCK' : (state.plan?.lockType ?? 'ON_TIME'),
        lockedAt: state.plan?.lockedAt ?? MOCK_NOW,
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: MOCK_NOW,
      });
      return json(route, 200, state.plan);
    }

    // ── Update actual (PATCH /commits/{id}/actual) ────────────────────────
    if (method === 'PATCH' && /^\/api\/v1\/commits\/[^/]+\/actual$/.test(path)) {
      const commitId = path.split('/')[4];
      const payload = (request.postDataJSON() ?? {}) as Record<string, unknown>;
      state.actuals.set(commitId, {
        commitId,
        actualResult: payload.actualResult ?? '',
        completionStatus: payload.completionStatus ?? 'DONE',
        deltaReason: payload.deltaReason ?? null,
        timeSpent: payload.timeSpent ?? null,
      });
      state.commits = state.commits.map((commit) =>
        commit.id === commitId
          ? { ...commit, version: Number(commit.version ?? 1) + 1, updatedAt: MOCK_NOW }
          : commit,
      );
      return json(route, 200, state.actuals.get(commitId));
    }

    // ── Submit reconciliation ─────────────────────────────────────────────
    if (method === 'POST' && path === `/api/v1/plans/${MOCK_PLAN_ID}/submit-reconciliation`) {
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: 'RECONCILED',
        reviewStatus: 'REVIEW_PENDING',
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: MOCK_NOW,
      });
      return json(route, 200, state.plan);
    }

    // ── Carry-forward ─────────────────────────────────────────────────────
    if (method === 'POST' && path === `/api/v1/plans/${MOCK_PLAN_ID}/carry-forward`) {
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: 'CARRY_FORWARD',
        carryForwardExecutedAt: MOCK_NOW,
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: MOCK_NOW,
      });
      return json(route, 200, state.plan);
    }

    // ── Team summary ──────────────────────────────────────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/team\/summary$/.test(path)) {
      return json(route, 200, {
        weekStart: requestedWeek,
        users: state.teamSummaryUsers,
        reviewStatusCounts: {
          pending: state.teamSummaryUsers.filter(
            (u) => u.reviewStatus === 'REVIEW_PENDING',
          ).length,
          approved: state.teamSummaryUsers.filter(
            (u) => u.reviewStatus === 'APPROVED',
          ).length,
          changesRequested: state.teamSummaryUsers.filter(
            (u) => u.reviewStatus === 'CHANGES_REQUESTED',
          ).length,
        },
        page: 0,
        size: 20,
        totalElements: state.teamSummaryUsers.length,
        totalPages: 1,
      });
    }

    // ── RCDO roll-up ──────────────────────────────────────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/team\/rcdo-rollup$/.test(path)) {
      return json(route, 200, {
        weekStart: requestedWeek,
        items: [
          {
            outcomeId: MOCK_OUTCOME_ID,
            outcomeName: 'Increase trial-to-paid by 20%',
            objectiveId: 'obj-1',
            objectiveName: 'Improve Conversion',
            rallyCryId: 'rc-1',
            rallyCryName: 'Scale Revenue',
            commitCount: 1,
            kingCount: 1,
            queenCount: 0,
            rookCount: 0,
            bishopCount: 0,
            knightCount: 0,
            pawnCount: 0,
          },
        ],
        nonStrategicCount: 0,
      });
    }

    // ── Notifications ─────────────────────────────────────────────────────
    if (method === 'GET' && path === '/api/v1/notifications/unread') {
      return json(route, 200, []);
    }
    if (method === 'POST' && path === '/api/v1/notifications/read-all') {
      return json(route, 200, {});
    }

    // ── AI manager insights ───────────────────────────────────────────────
    if (method === 'POST' && path === '/api/v1/ai/manager-insights') {
      return json(route, 200, {
        status: 'ok',
        headline: 'One report is pending review.',
        insights: [
          {
            title: 'Review pending',
            detail: 'A reconciled plan is waiting for manager feedback.',
            severity: 'INFO',
          },
        ],
      });
    }

    // ── Manager drill-down: plan ──────────────────────────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/plans\/[^/]+$/.test(path)) {
      if (options.denyManagerDrillDown) {
        return json(route, 403, apiError('Access denied', 'FORBIDDEN'));
      }
      if (state.teamPlan) {
        return json(route, 200, state.teamPlan);
      }
      return json(route, 404, apiError('Plan not found', 'NOT_FOUND'));
    }

    // ── Manager drill-down: commits ───────────────────────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/plans\/[^/]+\/commits$/.test(path)) {
      return json(route, 200, state.teamCommits);
    }

    // ── Manager review ────────────────────────────────────────────────────
    if (method === 'POST' && /^\/api\/v1\/plans\/[^/]+\/review$/.test(path)) {
      return json(route, 201, {
        id: 'review-1',
        weeklyPlanId: state.teamPlan?.id ?? MOCK_PLAN_ID,
        reviewerUserId: MOCK_MANAGER_USER_ID,
        decision: 'APPROVED',
        comments: 'Looks good.',
        createdAt: MOCK_NOW,
      });
    }

    // ── User trends ───────────────────────────────────────────────────────
    if (method === 'GET' && path === '/api/v1/users/me/trends') {
      return json(route, 200, buildTrendsResponse());
    }

    // ── User profile ──────────────────────────────────────────────────────
    if (method === 'GET' && path === '/api/v1/users/me/profile') {
      return json(route, 200, buildUserProfileResponse());
    }

    // ── User capacity ─────────────────────────────────────────────────────
    if (method === 'GET' && path === '/api/v1/users/me/capacity') {
      return json(route, 200, {
        orgId: MOCK_ORG_ID,
        userId: MOCK_USER_ID,
        weeksAnalyzed: 6,
        avgEstimatedHours: 32.0,
        avgActualHours: 28.5,
        estimationBias: 0.89,
        realisticWeeklyCap: 30.0,
        categoryBiasJson: null,
        priorityCompletionJson: null,
        confidenceLevel: 'MEDIUM',
        computedAt: MOCK_NOW,
      });
    }

    // ── Digest preferences ────────────────────────────────────────────────
    if (method === 'GET' && path === '/api/v1/users/me/digest-preferences') {
      return json(route, 200, {});
    }

    // ── Admin org policy / digest config ──────────────────────────────────
    if (method === 'GET' && path === '/api/v1/admin/org-policy') {
      return json(route, 200, state.orgPolicy);
    }

    if (method === 'PATCH' && path === '/api/v1/admin/org-policy/digest') {
      const body = (request.postDataJSON() as Record<string, unknown> | null) ?? {};
      state.orgPolicy = {
        ...state.orgPolicy,
        digestDay: typeof body.digestDay === 'string' ? body.digestDay : state.orgPolicy.digestDay,
        digestTime: typeof body.digestTime === 'string' ? body.digestTime : state.orgPolicy.digestTime,
      };
      return json(route, 200, state.orgPolicy);
    }

    // ── Executive strategic health ─────────────────────────────────────────
    if (method === 'GET' && path === '/api/v1/executive/strategic-health') {
      if (options.executiveDashboard === null) {
        return json(route, 404, apiError('Executive dashboard not found', 'NOT_FOUND'));
      }
      return json(route, 200, options.executiveDashboard ?? buildExecutiveDashboardResponse());
    }

    // ── Executive briefing ────────────────────────────────────────────────
    if (method === 'POST' && path === '/api/v1/ai/executive-briefing') {
      if (options.executiveBriefing === null) {
        return json(route, 404, apiError('Executive briefing not found', 'NOT_FOUND'));
      }
      return json(route, 200, options.executiveBriefing ?? buildExecutiveBriefingResponse());
    }

    // ── AI suggest-rcdo ───────────────────────────────────────────────────
    if (method === 'POST' && path === '/api/v1/ai/suggest-rcdo') {
      return json(route, 200, { status: 'ok', suggestions: [] });
    }

    // ── AI plan quality check ─────────────────────────────────────────────
    if (method === 'POST' && path === '/api/v1/ai/plan-quality-check') {
      return json(route, 200, { status: 'ok', nudges: [] });
    }

    // ── Plan quality check ────────────────────────────────────────────────
    if (method === 'GET' && /^\/api\/v1\/plans\/[^/]+\/quality-check$/.test(path)) {
      return json(route, 200, { status: 'ok', nudges: [] });
    }

    // ── Next work suggestions ─────────────────────────────────────────────
    if (
      (method === 'GET' && path === '/api/v1/suggestions/next-work') ||
      (method === 'POST' && path === '/api/v1/ai/suggest-next-work')
    ) {
      return json(route, 200, { status: 'ok', suggestions: [] });
    }

    // ── Next work suggestion feedback ─────────────────────────────────────
    if (method === 'POST' && path === '/api/v1/ai/suggestion-feedback') {
      return json(route, 200, { status: 'ok' });
    }

    return json(route, 404, apiError(`Unhandled mock route: ${method} ${path}`, 'NOT_FOUND'));
  });
}
