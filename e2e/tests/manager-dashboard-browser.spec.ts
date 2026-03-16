/**
 * Browser E2E tests for manager dashboard, drill-down, and review.
 *
 * Uses the installMockApi() helper pattern from golden-path.spec.ts so all
 * tests run deterministically against a mocked API (no live backend needed).
 *
 * PRD §18 acceptance criteria covered:
 *   #9  – Manager sees direct reports team summary grid with correct columns
 *   #10 – Manager views RCDO roll-up panel grouped by outcome
 *   #11 – Manager approves a plan; manager requests changes
 *   #12 – Manager drill-down: drill-in, commit table with RCDO snapshots, back
 *   #14 – ReviewPanel disables 'Request Changes' after carry-forward executed
 *   #17 – 'carry-forward-warning' is displayed when carryForwardExecutedAt set
 *
 * Test categories:
 *   [SMOKE] = runs on every PR (Gate 7)
 *   [FULL]  = deeper acceptance coverage
 */
import { expect, test, type Page, type Route } from '@playwright/test';

// ─── Constants ─────────────────────────────────────────────────────────────

const ORG_ID          = '00000000-0000-0000-0000-000000000099';
const USER_ID         = '00000000-0000-0000-0000-000000000001';
const MANAGER_USER_ID = '00000000-0000-0000-0000-000000000101';
const REPORT_USER_ID_1 = '00000000-0000-0000-0000-000000000201';
const REPORT_USER_ID_2 = '00000000-0000-0000-0000-000000000202';

const MANAGER_PLAN_ID  = '10000000-0000-0000-0000-000000000100';
const REPORT_PLAN_ID_1 = '10000000-0000-0000-0000-000000000201';
const REPORT_PLAN_ID_2 = '10000000-0000-0000-0000-000000000202';

const COMMIT_ID_1 = '20000000-0000-0000-0000-000000000001';
const COMMIT_ID_2 = '20000000-0000-0000-0000-000000000002';
const OUTCOME_ID  = '30000000-0000-0000-0000-000000000001';

const now = '2026-03-12T12:00:00Z';

// ─── Helpers ───────────────────────────────────────────────────────────────

/** Returns the ISO date (YYYY-MM-DD) of the Monday `weeksFromCurrent` weeks from today. */
function mondayIso(weeksFromCurrent = 0): string {
  const today = new Date();
  const day = today.getUTCDay();
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(
    Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()),
  );
  monday.setUTCDate(monday.getUTCDate() + diffToMonday + weeksFromCurrent * 7);
  return monday.toISOString().slice(0, 10);
}

function buildPlan(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: MANAGER_PLAN_ID,
    orgId: ORG_ID,
    ownerUserId: USER_ID,
    weekStartDate: mondayIso(),
    state: 'LOCKED',
    reviewStatus: 'REVIEW_NOT_APPLICABLE',
    lockType: 'ON_TIME',
    lockedAt: now,
    carryForwardExecutedAt: null,
    version: 1,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function buildCommit(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: COMMIT_ID_1,
    weeklyPlanId: REPORT_PLAN_ID_1,
    title: 'Close enterprise deal',
    description: 'Finalize contract with ACME Corp.',
    chessPriority: 'KING',
    category: 'DELIVERY',
    outcomeId: OUTCOME_ID,
    nonStrategicReason: null,
    expectedResult: 'Contract signed and onboarding started.',
    confidence: 4,
    tags: ['sales', 'enterprise'],
    progressNotes: '',
    snapshotRallyCryId: 'rc-1',
    snapshotRallyCryName: 'Scale Revenue',
    snapshotObjectiveId: 'obj-1',
    snapshotObjectiveName: 'Improve Conversion',
    snapshotOutcomeId: OUTCOME_ID,
    snapshotOutcomeName: 'Increase trial-to-paid by 20%',
    carriedFromCommitId: null,
    version: 1,
    createdAt: now,
    updatedAt: now,
    validationErrors: [],
    actual: null,
    ...overrides,
  };
}

function buildTeamMember(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    userId: REPORT_USER_ID_1,
    displayName: 'Alice Chen',
    planId: REPORT_PLAN_ID_1,
    state: 'RECONCILED',
    reviewStatus: 'REVIEW_PENDING',
    commitCount: 2,
    incompleteCount: 0,
    issueCount: 0,
    nonStrategicCount: 0,
    kingCount: 1,
    queenCount: 1,
    lastUpdated: now,
    isStale: false,
    isLateLock: false,
    ...overrides,
  };
}

function json(route: Route, status: number, body: unknown): Promise<void> {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

function apiError(message: string, code = 'VALIDATION_ERROR'): Record<string, unknown> {
  return { error: { code, message, details: [] } };
}

// ─── Mock API installer ─────────────────────────────────────────────────────

interface MockApiOptions {
  /** Manager's own plan (returned for /weeks/{week}/plans/me). */
  managerPlan?: Record<string, unknown> | null;
  /** Team members to return from /team/summary. */
  teamSummaryUsers?: Array<Record<string, unknown>>;
  /** Plans for team members (keyed by userId). */
  teamPlans?: Record<string, Record<string, unknown>>;
  /** Commits for team members (keyed by planId). */
  teamCommits?: Record<string, Array<Record<string, unknown>>>;
  /** Return 403 for all drill-down plan requests. */
  denyManagerDrillDown?: boolean;
  /** If true, the team summary includes multiple pages (totalPages=2). */
  multiplePages?: boolean;
  /** Unread notifications to return. */
  notifications?: Array<Record<string, unknown>>;
  /** Override review status counts in the summary. */
  reviewStatusCounts?: { pending: number; approved: number; changesRequested: number };
}

async function installMockApi(page: Page, options: MockApiOptions = {}): Promise<void> {
  const state = {
    managerPlan: options.managerPlan !== undefined
      ? options.managerPlan
      : buildPlan({ ownerUserId: MANAGER_USER_ID }),

    teamSummaryUsers: options.teamSummaryUsers ?? [
      buildTeamMember({
        userId: REPORT_USER_ID_1,
        displayName: 'Alice Chen',
        planId: REPORT_PLAN_ID_1,
        state: 'RECONCILED',
        reviewStatus: 'REVIEW_PENDING',
      }),
      buildTeamMember({
        userId: REPORT_USER_ID_2,
        displayName: 'Bob Martinez',
        planId: REPORT_PLAN_ID_2,
        state: 'LOCKED',
        reviewStatus: 'REVIEW_NOT_APPLICABLE',
        kingCount: 1,
        queenCount: 0,
        commitCount: 1,
      }),
    ],

    teamPlans: options.teamPlans ?? {
      [REPORT_USER_ID_1]: buildPlan({
        id: REPORT_PLAN_ID_1,
        ownerUserId: REPORT_USER_ID_1,
        state: 'RECONCILED',
        reviewStatus: 'REVIEW_PENDING',
        lockType: 'ON_TIME',
        carryForwardExecutedAt: null,
      }),
      [REPORT_USER_ID_2]: buildPlan({
        id: REPORT_PLAN_ID_2,
        ownerUserId: REPORT_USER_ID_2,
        state: 'LOCKED',
        reviewStatus: 'REVIEW_NOT_APPLICABLE',
        lockType: 'ON_TIME',
      }),
    },

    teamCommits: options.teamCommits ?? {
      [REPORT_PLAN_ID_1]: [
        buildCommit({
          id: COMMIT_ID_1,
          weeklyPlanId: REPORT_PLAN_ID_1,
          title: 'Close enterprise deal',
          chessPriority: 'KING',
          snapshotRallyCryName: 'Scale Revenue',
          snapshotObjectiveName: 'Improve Conversion',
          snapshotOutcomeName: 'Increase trial-to-paid by 20%',
          actual: {
            commitId: COMMIT_ID_1,
            actualResult: 'Contract signed.',
            completionStatus: 'DONE',
            deltaReason: null,
            timeSpent: 35,
          },
        }),
        buildCommit({
          id: COMMIT_ID_2,
          weeklyPlanId: REPORT_PLAN_ID_1,
          title: 'Improve API uptime monitoring',
          chessPriority: 'QUEEN',
          snapshotRallyCryName: 'Scale Revenue',
          snapshotObjectiveName: 'Reduce Churn',
          snapshotOutcomeName: 'Increase API reliability to 99.9%',
          actual: {
            commitId: COMMIT_ID_2,
            actualResult: 'Dashboard 60% complete.',
            completionStatus: 'PARTIALLY',
            deltaReason: 'Blocked on infra team.',
            timeSpent: 20,
          },
        }),
      ],
      [REPORT_PLAN_ID_2]: [
        buildCommit({
          id: '20000000-0000-0000-0000-000000000010',
          weeklyPlanId: REPORT_PLAN_ID_2,
          title: 'Set up CI pipeline',
          chessPriority: 'KING',
        }),
      ],
    },

    notifications: options.notifications ?? [],
  };

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    // ── Manager's own plan (IC view) ────────────────────────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/plans\/me$/.test(path)) {
      if (state.managerPlan) {
        return json(route, 200, state.managerPlan);
      }
      return json(route, 404, apiError('No plan for this week', 'NOT_FOUND'));
    }

    // ── Team summary ────────────────────────────────────────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/team\/summary$/.test(path)) {
      const stateFilter = url.searchParams.get('state');
      const filtered = stateFilter
        ? state.teamSummaryUsers.filter((u) => u.state === stateFilter)
        : state.teamSummaryUsers;

      // Compute review status counts from the (filtered) users
      const reviewStatusCounts = options.reviewStatusCounts ?? filtered.reduce(
        (acc, user) => {
          if (user.reviewStatus === 'REVIEW_PENDING') acc.pending += 1;
          else if (user.reviewStatus === 'APPROVED') acc.approved += 1;
          else if (user.reviewStatus === 'CHANGES_REQUESTED') acc.changesRequested += 1;
          return acc;
        },
        { pending: 0, approved: 0, changesRequested: 0 },
      );

      return json(route, 200, {
        weekStart: mondayIso(),
        users: filtered,
        reviewStatusCounts,
        page: 0,
        size: 20,
        totalElements: options.multiplePages ? 40 : filtered.length,
        totalPages: options.multiplePages ? 2 : 1,
      });
    }

    // ── RCDO rollup ─────────────────────────────────────────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/team\/rcdo-rollup$/.test(path)) {
      return json(route, 200, {
        weekStart: mondayIso(),
        items: [
          {
            outcomeId: OUTCOME_ID,
            outcomeName: 'Increase trial-to-paid by 20%',
            objectiveId: 'obj-1',
            objectiveName: 'Improve Conversion',
            rallyCryId: 'rc-1',
            rallyCryName: 'Scale Revenue',
            commitCount: 2,
            kingCount: 1,
            queenCount: 1,
            rookCount: 0,
            bishopCount: 0,
            knightCount: 0,
            pawnCount: 0,
          },
        ],
        nonStrategicCount: 0,
      });
    }

    // ── Notifications ───────────────────────────────────────────────────────
    if (method === 'GET' && path === '/api/v1/notifications/unread') {
      return json(route, 200, state.notifications);
    }
    if (method === 'POST' && path === '/api/v1/notifications/read-all') {
      state.notifications = [];
      return json(route, 200, {});
    }
    if (method === 'POST' && /^\/api\/v1\/notifications\/[^/]+\/read$/.test(path)) {
      const notifId = path.split('/')[4];
      state.notifications = state.notifications.filter((n) => n.id !== notifId);
      return json(route, 200, {});
    }

    // ── AI manager insights ─────────────────────────────────────────────────
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

    // ── Manager drill-down: GET plan ─────────────────────────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/plans\/[^/]+$/.test(path)) {
      if (options.denyManagerDrillDown) {
        return json(route, 403, apiError('Access denied', 'FORBIDDEN'));
      }
      const userId = path.split('/').pop() ?? '';
      const teamPlan = (state.teamPlans as Record<string, Record<string, unknown>>)[userId];
      if (!teamPlan) {
        return json(route, 404, apiError('Plan not found', 'NOT_FOUND'));
      }
      return json(route, 200, teamPlan);
    }

    // ── Manager drill-down: GET commits ─────────────────────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/plans\/[^/]+\/commits$/.test(path)) {
      const parts = path.split('/');
      // path: /api/v1/weeks/{weekStart}/plans/{userId}/commits
      const userId = parts[parts.length - 2];
      const userPlan = (state.teamPlans as Record<string, Record<string, unknown>>)[userId];
      if (!userPlan) {
        return json(route, 200, []);
      }
      const planId = String(userPlan.id ?? '');
      const commits = (state.teamCommits as Record<string, Array<Record<string, unknown>>>)[planId] ?? [];
      return json(route, 200, commits);
    }

    // ── Submit review ────────────────────────────────────────────────────────
    if (method === 'POST' && /^\/api\/v1\/plans\/[^/]+\/review$/.test(path)) {
      const planId = path.split('/')[4];
      const body = (request.postDataJSON() ?? {}) as Record<string, unknown>;
      const decision = String(body.decision ?? 'APPROVED');
      const reviewStatus = decision === 'APPROVED' ? 'APPROVED' : 'CHANGES_REQUESTED';

      state.teamSummaryUsers = state.teamSummaryUsers.map((user) =>
        String(user.planId) === planId
          ? { ...user, reviewStatus }
          : user,
      );

      state.teamPlans = Object.fromEntries(
        Object.entries(state.teamPlans).map(([userId, plan]) => [
          userId,
          String(plan.id) === planId ? { ...plan, reviewStatus } : plan,
        ]),
      );

      return json(route, 200, {
        id: crypto.randomUUID(),
        weeklyPlanId: planId,
        reviewerUserId: MANAGER_USER_ID,
        decision,
        comments: String(body.comments ?? ''),
        createdAt: now,
      });
    }

    // ── RCDO tree (referenced by IC view, not manager dashboard) ────────────
    if (method === 'GET' && path === '/api/v1/rcdo/tree') {
      return json(route, 200, {
        rallyCries: [
          {
            id: 'rc-1',
            name: 'Scale Revenue',
            objectives: [
              {
                id: 'obj-1',
                name: 'Improve Conversion',
                rallyCryId: 'rc-1',
                outcomes: [{ id: OUTCOME_ID, name: 'Increase trial-to-paid by 20%', objectiveId: 'obj-1' }],
              },
            ],
          },
        ],
      });
    }

    // Fallback ─────────────────────────────────────────────────────────────
    return json(route, 404, apiError(`Unhandled mock: ${method} ${path}`, 'NOT_FOUND'));
  });
}

// ─── Helper: navigate to team dashboard ────────────────────────────────────

async function goToTeamDashboard(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('pa-host-shell')).toBeVisible();
  await page.getByTestId('nav-team-dashboard').click();
  await expect(page.getByTestId('team-dashboard-page')).toBeVisible();
}

// ═══════════════════════════════════════════════════════════════════════════
// Manager Dashboard Navigation
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard: Navigation', () => {
  test('[SMOKE] Manager sees nav-team-dashboard button and can navigate to Team Dashboard', async ({ page }) => {
    await installMockApi(page);
    await page.goto('/');

    // The host shell and micro-frontend load
    await expect(page.getByTestId('pa-host-shell')).toBeVisible();

    // nav-team-dashboard exists inside the App because Carol is a MANAGER
    await expect(page.getByTestId('nav-team-dashboard')).toBeVisible();

    // Click the nav button
    await page.getByTestId('nav-team-dashboard').click();

    // Team dashboard page is now shown
    await expect(page.getByTestId('team-dashboard-page')).toBeVisible();
  });

  test('[SMOKE] Team dashboard loads team summary grid with rows for each report', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    // The summary grid is visible
    await expect(page.getByTestId('team-summary-grid')).toBeVisible();

    // Both direct report rows appear
    await expect(page.getByTestId(`team-row-${REPORT_USER_ID_1}`)).toBeVisible();
    await expect(page.getByTestId(`team-row-${REPORT_USER_ID_2}`)).toBeVisible();
  });

  test('[FULL] Grid shows all expected column headers: State, Review, Commits, K/Q, Badges', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    const grid = page.getByTestId('team-summary-grid');
    await expect(grid).toBeVisible();

    // Column header text checks
    await expect(grid).toContainText('State');
    await expect(grid).toContainText('Review');
    await expect(grid).toContainText('Commits');
    await expect(grid).toContainText('Badges');
  });

  test('[FULL] Manager can navigate back to My Plan via nav-my-plan button', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    // nav-my-plan button exists inside the App
    await expect(page.getByTestId('nav-my-plan')).toBeVisible();
    await page.getByTestId('nav-my-plan').click();

    // Weekly plan page is now shown (team-dashboard-page is gone)
    await expect(page.getByTestId('team-dashboard-page')).not.toBeVisible();
    // The IC plan view should appear (plan-header or create-plan-btn)
    await expect(
      page.getByTestId('plan-header').or(page.getByTestId('create-plan-btn')),
    ).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Review Status Counts
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard: Review Status Counts', () => {
  test('[SMOKE] Dashboard shows correct pending/approved/changes-requested counts', async ({ page }) => {
    await installMockApi(page, {
      teamSummaryUsers: [
        buildTeamMember({ userId: REPORT_USER_ID_1, reviewStatus: 'REVIEW_PENDING' }),
        buildTeamMember({ userId: REPORT_USER_ID_2, reviewStatus: 'APPROVED' }),
      ],
      reviewStatusCounts: { pending: 1, approved: 1, changesRequested: 0 },
    });
    await goToTeamDashboard(page);

    const counts = page.getByTestId('review-status-counts');
    await expect(counts).toBeVisible();
    await expect(counts).toContainText('Pending:');
    await expect(counts).toContainText('Approved:');
    await expect(counts).toContainText('Changes Requested:');

    // Values should match the mock
    await expect(counts.locator('strong').first()).toContainText('1');
  });

  test('[FULL] Counts reflect all-approved state correctly', async ({ page }) => {
    await installMockApi(page, {
      teamSummaryUsers: [
        buildTeamMember({ userId: REPORT_USER_ID_1, reviewStatus: 'APPROVED', state: 'CARRY_FORWARD' }),
        buildTeamMember({ userId: REPORT_USER_ID_2, reviewStatus: 'APPROVED', state: 'RECONCILED' }),
      ],
      reviewStatusCounts: { pending: 0, approved: 2, changesRequested: 0 },
    });
    await goToTeamDashboard(page);

    const counts = page.getByTestId('review-status-counts');
    await expect(counts).toBeVisible();
    // All approved — pending is 0
    const strongElements = counts.locator('strong');
    // First strong = pending (0), second = approved (2), third = changesRequested (0)
    await expect(strongElements.nth(0)).toContainText('0');
    await expect(strongElements.nth(1)).toContainText('2');
    await expect(strongElements.nth(2)).toContainText('0');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Badges: 'Late Lock' and 'Stale'
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard: Badges', () => {
  test('[SMOKE] Late lock badge is shown for team member with isLateLock=true', async ({ page }) => {
    await installMockApi(page, {
      teamSummaryUsers: [
        buildTeamMember({
          userId: REPORT_USER_ID_1,
          displayName: 'Alice Chen',
          isLateLock: true,
          isStale: false,
        }),
      ],
    });
    await goToTeamDashboard(page);

    // LATE LOCK badge is visible for this user
    await expect(page.getByTestId(`late-lock-badge-${REPORT_USER_ID_1}`)).toBeVisible();
    await expect(page.getByTestId(`late-lock-badge-${REPORT_USER_ID_1}`)).toContainText('LATE LOCK');
  });

  test('[SMOKE] Stale badge is shown for team member with isStale=true', async ({ page }) => {
    await installMockApi(page, {
      teamSummaryUsers: [
        buildTeamMember({
          userId: REPORT_USER_ID_1,
          displayName: 'Alice Chen',
          isStale: true,
          isLateLock: false,
        }),
      ],
    });
    await goToTeamDashboard(page);

    // STALE badge is visible for this user
    await expect(page.getByTestId(`stale-badge-${REPORT_USER_ID_1}`)).toBeVisible();
    await expect(page.getByTestId(`stale-badge-${REPORT_USER_ID_1}`)).toContainText('STALE');
  });

  test('[FULL] A member with no special flags shows no stale or late-lock badge', async ({ page }) => {
    await installMockApi(page, {
      teamSummaryUsers: [
        buildTeamMember({
          userId: REPORT_USER_ID_1,
          isStale: false,
          isLateLock: false,
        }),
      ],
    });
    await goToTeamDashboard(page);

    await expect(page.getByTestId(`team-row-${REPORT_USER_ID_1}`)).toBeVisible();
    await expect(page.getByTestId(`stale-badge-${REPORT_USER_ID_1}`)).not.toBeVisible();
    await expect(page.getByTestId(`late-lock-badge-${REPORT_USER_ID_1}`)).not.toBeVisible();
  });

  test('[FULL] Both late-lock and stale badges can appear on the same row', async ({ page }) => {
    await installMockApi(page, {
      teamSummaryUsers: [
        buildTeamMember({
          userId: REPORT_USER_ID_1,
          isStale: true,
          isLateLock: true,
        }),
      ],
    });
    await goToTeamDashboard(page);

    await expect(page.getByTestId(`stale-badge-${REPORT_USER_ID_1}`)).toBeVisible();
    await expect(page.getByTestId(`late-lock-badge-${REPORT_USER_ID_1}`)).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Drill-Down
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard: Drill-Down', () => {
  test('[SMOKE] Clicking drill-down button shows PlanDrillDown for that member', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    // Click View button for REPORT_USER_ID_1
    await page.getByTestId(`drill-down-${REPORT_USER_ID_1}`).click();

    // PlanDrillDown view is shown
    await expect(page.getByTestId('plan-drilldown')).toBeVisible();
  });

  test('[SMOKE] PlanDrillDown shows commits table with RCDO snapshot columns', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    await page.getByTestId(`drill-down-${REPORT_USER_ID_1}`).click();
    await expect(page.getByTestId('plan-drilldown')).toBeVisible();

    // Commits table is rendered
    const commitsTable = page.getByTestId('drilldown-commits');
    await expect(commitsTable).toBeVisible();

    // Both commit rows appear
    await expect(page.getByTestId(`drilldown-commit-${COMMIT_ID_1}`)).toBeVisible();
    await expect(page.getByTestId(`drilldown-commit-${COMMIT_ID_2}`)).toBeVisible();

    // RCDO snapshot column content is present
    await expect(commitsTable).toContainText('Increase trial-to-paid by 20%');
    await expect(commitsTable).toContainText('Increase API reliability to 99.9%');
  });

  test('[FULL] PlanDrillDown shows actual result and completion status for RECONCILED plan', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    await page.getByTestId(`drill-down-${REPORT_USER_ID_1}`).click();
    await expect(page.getByTestId('plan-drilldown')).toBeVisible();

    // Actuals are shown for reconciled plan
    const actualStatus1 = page.getByTestId(`actual-status-${COMMIT_ID_1}`);
    await expect(actualStatus1).toBeVisible();
    await expect(actualStatus1).toContainText('DONE');

    const actualResult1 = page.getByTestId(`actual-result-${COMMIT_ID_1}`);
    await expect(actualResult1).toContainText('Contract signed.');

    // PARTIALLY done commit shows its actual
    const actualStatus2 = page.getByTestId(`actual-status-${COMMIT_ID_2}`);
    await expect(actualStatus2).toContainText('PARTIALLY');

    const actualDelta2 = page.getByTestId(`actual-delta-${COMMIT_ID_2}`);
    await expect(actualDelta2).toContainText('Blocked on infra team.');
  });

  test('[FULL] PlanDrillDown shows permission error when access is denied', async ({ page }) => {
    await installMockApi(page, { denyManagerDrillDown: true });
    await goToTeamDashboard(page);

    await page.getByTestId(`drill-down-${REPORT_USER_ID_1}`).click();

    // Error message is shown
    await expect(page.getByTestId('drilldown-error')).toBeVisible();
    await expect(page.getByTestId('drilldown-error')).toContainText('Access denied');
  });

  test('[FULL] PlanDrillDown shows "no plan" message for member without a plan', async ({ page }) => {
    await installMockApi(page, {
      teamSummaryUsers: [
        buildTeamMember({
          userId: REPORT_USER_ID_1,
          planId: null,
          state: null,
          reviewStatus: null,
          commitCount: 0,
          kingCount: 0,
          queenCount: 0,
        }),
      ],
      teamPlans: {},
    });
    await goToTeamDashboard(page);

    await page.getByTestId(`drill-down-${REPORT_USER_ID_1}`).click();

    // No plan message is shown
    await expect(page.getByTestId('drilldown-no-plan')).toBeVisible();
    await expect(page.getByTestId('drilldown-no-plan')).toContainText('no plan for the selected week');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Back Navigation
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard: Back Navigation', () => {
  test('[SMOKE] Clicking back button returns to the team dashboard from drill-down', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    // Navigate to drill-down
    await page.getByTestId(`drill-down-${REPORT_USER_ID_1}`).click();
    await expect(page.getByTestId('plan-drilldown')).toBeVisible();
    await expect(page.getByTestId('team-summary-grid')).not.toBeVisible();

    // Click back button
    await page.getByTestId('drilldown-back-btn').click();

    // Back to the dashboard
    await expect(page.getByTestId('team-summary-grid')).toBeVisible();
    await expect(page.getByTestId('plan-drilldown')).not.toBeVisible();
  });

  test('[FULL] Back button also returns from error state to the dashboard', async ({ page }) => {
    await installMockApi(page, { denyManagerDrillDown: true });
    await goToTeamDashboard(page);

    await page.getByTestId(`drill-down-${REPORT_USER_ID_1}`).click();
    await expect(page.getByTestId('drilldown-error')).toBeVisible();

    // The error view has a back button
    await page.getByRole('button', { name: '← Back' }).click();
    await expect(page.getByTestId('team-summary-grid')).toBeVisible();
    await expect(page.getByTestId('drilldown-error')).not.toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Review Panel
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard: Review Panel', () => {
  test('[SMOKE] ReviewPanel is shown when plan is RECONCILED with REVIEW_PENDING status', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    await page.getByTestId(`drill-down-${REPORT_USER_ID_1}`).click();
    await expect(page.getByTestId('plan-drilldown')).toBeVisible();

    // Review panel is visible (Alice's plan is RECONCILED / REVIEW_PENDING)
    await expect(page.getByTestId('review-panel')).toBeVisible();
    await expect(page.getByTestId('approve-btn')).toBeVisible();
    await expect(page.getByTestId('request-changes-btn')).toBeVisible();
    await expect(page.getByTestId('review-comments')).toBeVisible();
  });

  test('[SMOKE] Manager can approve plan: fills comments and clicks Approve', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    await page.getByTestId(`drill-down-${REPORT_USER_ID_1}`).click();
    await expect(page.getByTestId('review-panel')).toBeVisible();

    // Fill in review comments
    await page.getByTestId('review-comments').fill('Great work this week, Alice!');

    // Approve button is enabled after comments are filled
    await expect(page.getByTestId('approve-btn')).toBeEnabled();
    await page.getByTestId('approve-btn').click();

    // Review submitted confirmation
    await expect(page.getByTestId('review-submitted')).toBeVisible();
    await expect(page.getByTestId('review-submitted')).toContainText('Review submitted successfully.');

    // Returning to the dashboard shows refreshed review status
    await page.getByTestId('drilldown-back-btn').click();
    const aliceRow = page.getByTestId(`team-row-${REPORT_USER_ID_1}`);
    await expect(aliceRow.getByTestId('review-badge')).toContainText('APPROVED');
    await expect(page.getByTestId('review-status-counts')).toContainText('Approved: 1');
  });

  test('[FULL] Manager can request changes: fills comments and clicks Request Changes', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    await page.getByTestId(`drill-down-${REPORT_USER_ID_1}`).click();
    await expect(page.getByTestId('review-panel')).toBeVisible();

    await page.getByTestId('review-comments').fill('Please add more detail on the blocker.');

    await expect(page.getByTestId('request-changes-btn')).toBeEnabled();
    await page.getByTestId('request-changes-btn').click();

    await expect(page.getByTestId('review-submitted')).toBeVisible();

    await page.getByTestId('drilldown-back-btn').click();
    const aliceRow = page.getByTestId(`team-row-${REPORT_USER_ID_1}`);
    await expect(aliceRow.getByTestId('review-badge')).toContainText('CHANGES REQUESTED');
    await expect(page.getByTestId('review-status-counts')).toContainText('Changes Requested: 1');
  });

  test('[SMOKE] Approve and Request Changes buttons are disabled until comments are entered', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    await page.getByTestId(`drill-down-${REPORT_USER_ID_1}`).click();
    await expect(page.getByTestId('review-panel')).toBeVisible();

    // No comments entered yet — both buttons should be disabled
    await expect(page.getByTestId('approve-btn')).toBeDisabled();
    await expect(page.getByTestId('request-changes-btn')).toBeDisabled();
  });

  test('[SMOKE] ReviewPanel disables Request Changes when carryForwardExecutedAt is set', async ({ page }) => {
    await installMockApi(page, {
      teamPlans: {
        [REPORT_USER_ID_1]: buildPlan({
          id: REPORT_PLAN_ID_1,
          ownerUserId: REPORT_USER_ID_1,
          state: 'CARRY_FORWARD',
          reviewStatus: 'REVIEW_PENDING',
          lockType: 'ON_TIME',
          // Carry-forward has been executed
          carryForwardExecutedAt: now,
        }),
      },
    });
    await goToTeamDashboard(page);

    await page.getByTestId(`drill-down-${REPORT_USER_ID_1}`).click();
    await expect(page.getByTestId('review-panel')).toBeVisible();

    // Fill comments
    await page.getByTestId('review-comments').fill('Some feedback here.');

    // Approve is enabled but Request Changes is disabled
    await expect(page.getByTestId('approve-btn')).toBeEnabled();
    await expect(page.getByTestId('request-changes-btn')).toBeDisabled();

    // Warning is displayed
    await expect(page.getByTestId('carry-forward-warning')).toBeVisible();
    await expect(page.getByTestId('carry-forward-warning')).toContainText(
      'Carry-forward already executed',
    );
  });

  test('[FULL] ReviewPanel is not shown for a plan in LOCKED state (not reviewable)', async ({ page }) => {
    // Bob's plan is LOCKED (not RECONCILED) → no review panel
    await installMockApi(page);
    await goToTeamDashboard(page);

    await page.getByTestId(`drill-down-${REPORT_USER_ID_2}`).click();
    await expect(page.getByTestId('plan-drilldown')).toBeVisible();

    // Bob's plan is LOCKED → review panel should NOT be rendered
    await expect(page.getByTestId('review-panel')).not.toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// RCDO Rollup Panel
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard: RCDO Rollup Panel', () => {
  test('[SMOKE] RCDO rollup panel is visible with grouped commits', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    await expect(page.getByTestId('rcdo-rollup-panel')).toBeVisible();

    // Rollup table with rows
    await expect(page.getByTestId('rollup-table')).toBeVisible();
    await expect(page.getByTestId(`rollup-row-${OUTCOME_ID}`)).toBeVisible();

    // Shows rally cry / objective / outcome hierarchy
    const rollupTable = page.getByTestId('rollup-table');
    await expect(rollupTable).toContainText('Scale Revenue');
    await expect(rollupTable).toContainText('Improve Conversion');
    await expect(rollupTable).toContainText('Increase trial-to-paid by 20%');
  });

  test('[FULL] Rollup row shows commit count and chess priority breakdown', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    await expect(page.getByTestId(`rollup-row-${OUTCOME_ID}`)).toBeVisible();

    // Commit count = 2 (1 KING + 1 QUEEN in mock)
    const rollupRow = page.getByTestId(`rollup-row-${OUTCOME_ID}`);
    await expect(rollupRow).toContainText('2');
  });

  test('[FULL] Rollup shows non-strategic count when present', async ({ page }) => {
    await installMockApi(page);
    // Override RCDO rollup to include non-strategic commits
    await page.route('**/api/v1/weeks/*/team/rcdo-rollup', async (route) => {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          weekStart: mondayIso(),
          items: [],
          nonStrategicCount: 3,
        }),
      });
    });
    await goToTeamDashboard(page);

    await expect(page.getByTestId('rcdo-rollup-panel')).toBeVisible();
    await expect(page.getByTestId('non-strategic-count')).toBeVisible();
    await expect(page.getByTestId('non-strategic-count')).toContainText('3 non-strategic commit');
  });

  test('[FULL] Rollup shows "No commits to roll up" when empty and no non-strategic', async ({ page }) => {
    await installMockApi(page);
    await page.route('**/api/v1/weeks/*/team/rcdo-rollup', async (route) => {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          weekStart: mondayIso(),
          items: [],
          nonStrategicCount: 0,
        }),
      });
    });
    await goToTeamDashboard(page);

    await expect(page.getByTestId('rcdo-rollup-panel')).toBeVisible();
    await expect(page.getByTestId('rollup-empty')).toBeVisible();
    await expect(page.getByTestId('rollup-empty')).toContainText('No commits to roll up.');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// AI Manager Insights Panel
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard: AI Insights Panel', () => {
  test('[FULL] AI insights panel is hidden by default (managerInsights feature flag disabled)', async ({ page }) => {
    // The pa-host-stub does not pass featureFlags, so managerInsights defaults to false.
    // AiManagerInsightsPanel returns null when the flag is off.
    await installMockApi(page);
    await goToTeamDashboard(page);

    // Panel should NOT be visible because flag is disabled
    await expect(page.getByTestId('ai-manager-insights')).not.toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Notification Bell
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard: Notification Bell', () => {
  test('[SMOKE] Notification bell is visible on the team dashboard', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    await expect(page.getByTestId('notification-bell')).toBeVisible();
    await expect(page.getByTestId('notification-bell-btn')).toBeVisible();
  });

  test('[SMOKE] Clicking notification bell opens and closes the dropdown', async ({ page }) => {
    await installMockApi(page, { notifications: [] });
    await goToTeamDashboard(page);

    // Dropdown is not yet open
    await expect(page.getByTestId('notification-dropdown')).not.toBeVisible();

    // Click to open
    await page.getByTestId('notification-bell-btn').click();
    await expect(page.getByTestId('notification-dropdown')).toBeVisible();

    // Click again to close
    await page.getByTestId('notification-bell-btn').click();
    await expect(page.getByTestId('notification-dropdown')).not.toBeVisible();
  });

  test('[FULL] No-notifications empty state is shown in dropdown when there are none', async ({ page }) => {
    await installMockApi(page, { notifications: [] });
    await goToTeamDashboard(page);

    await page.getByTestId('notification-bell-btn').click();
    await expect(page.getByTestId('notification-dropdown')).toBeVisible();

    // Empty state message
    await expect(page.getByTestId('notification-dropdown')).toContainText('No new notifications');
  });

  test('[FULL] Notification count badge appears when there are unread notifications', async ({ page }) => {
    const notificationId = '99000000-0000-0000-0000-000000000001';
    await installMockApi(page, {
      notifications: [
        {
          id: notificationId,
          type: 'PLAN_STILL_DRAFT',
          payload: {},
          read: false,
          createdAt: now,
        },
      ],
    });
    await goToTeamDashboard(page);

    // Badge with count should appear
    await expect(page.getByTestId('notification-count')).toBeVisible();
    await expect(page.getByTestId('notification-count')).toContainText('1');
  });

  test('[FULL] Clicking bell with notifications shows notification items in dropdown', async ({ page }) => {
    const notificationId = '99000000-0000-0000-0000-000000000001';
    await installMockApi(page, {
      notifications: [
        {
          id: notificationId,
          type: 'PLAN_STILL_DRAFT',
          payload: {},
          read: false,
          createdAt: now,
        },
      ],
    });
    await goToTeamDashboard(page);

    await page.getByTestId('notification-bell-btn').click();
    await expect(page.getByTestId('notification-dropdown')).toBeVisible();

    // Notification item is shown in dropdown
    await expect(page.getByTestId(`notification-item-${notificationId}`)).toBeVisible();

    // "Mark all read" button appears
    await expect(page.getByTestId('mark-all-read-btn')).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Filters
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard: Filters', () => {
  test('[SMOKE] All filter controls are visible on the dashboard', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    const filters = page.getByTestId('team-filters');
    await expect(filters).toBeVisible();

    // State filter
    await expect(page.getByTestId('filter-state')).toBeVisible();

    // Priority filter
    await expect(page.getByTestId('filter-priority')).toBeVisible();

    // Category filter
    await expect(page.getByTestId('filter-category')).toBeVisible();

    // Incomplete filter checkbox
    await expect(page.getByTestId('filter-incomplete')).toBeVisible();

    // Non-strategic filter checkbox
    await expect(page.getByTestId('filter-non-strategic')).toBeVisible();
  });

  test('[FULL] Selecting a state filter triggers a new summary fetch with state param', async ({ page }) => {
    let summaryRequestCount = 0;
    let lastStateParam: string | null = null;

    await installMockApi(page);

    // Intercept to capture the state filter param
    await page.route('**/api/v1/weeks/*/team/summary*', async (route) => {
      summaryRequestCount++;
      const url = new URL(route.request().url());
      lastStateParam = url.searchParams.get('state');
      return route.continue();
    });

    await goToTeamDashboard(page);
    const initialCount = summaryRequestCount;

    // Select RECONCILED state filter
    await page.getByTestId('filter-state').selectOption('RECONCILED');

    // A new request is fired with the state param
    await expect(async () => {
      expect(summaryRequestCount).toBeGreaterThan(initialCount);
    }).toPass({ timeout: 3000 });
    expect(lastStateParam).toBe('RECONCILED');
  });

  test('[FULL] Priority filter is a dropdown with chess piece options', async ({ page }) => {
    await installMockApi(page);
    await goToTeamDashboard(page);

    const priorityFilter = page.getByTestId('filter-priority');
    await expect(priorityFilter).toBeVisible();

    // Check some priority options exist
    const options = await priorityFilter.locator('option').allTextContents();
    expect(options).toContain('All Priorities');
    expect(options.some((o) => o === 'KING')).toBe(true);
    expect(options.some((o) => o === 'QUEEN')).toBe(true);
  });

  test('[FULL] Incomplete checkbox filters to show only members with incomplete actuals', async ({ page }) => {
    let capturedIncomplete: string | null = null;

    await installMockApi(page);
    await page.route('**/api/v1/weeks/*/team/summary*', async (route) => {
      const url = new URL(route.request().url());
      capturedIncomplete = url.searchParams.get('incomplete');
      return route.continue();
    });

    await goToTeamDashboard(page);

    // Check the incomplete checkbox
    await page.getByTestId('filter-incomplete').check();

    await expect(async () => {
      expect(capturedIncomplete).toBe('true');
    }).toPass({ timeout: 3000 });
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Pagination
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard: Pagination', () => {
  test('[FULL] Pagination controls appear when totalPages > 1', async ({ page }) => {
    await installMockApi(page, { multiplePages: true });
    await goToTeamDashboard(page);

    // Pagination section is visible
    await expect(page.getByTestId('pagination')).toBeVisible();
    await expect(page.getByTestId('prev-page')).toBeVisible();
    await expect(page.getByTestId('next-page')).toBeVisible();
  });

  test('[FULL] Prev-page button is disabled on the first page', async ({ page }) => {
    await installMockApi(page, { multiplePages: true });
    await goToTeamDashboard(page);

    // On page 1 → Previous is disabled
    await expect(page.getByTestId('prev-page')).toBeDisabled();
    // Next is enabled
    await expect(page.getByTestId('next-page')).toBeEnabled();
  });

  test('[FULL] Clicking Next page advances to page 2 and enables Previous', async ({ page }) => {
    // We need the second page request to work — respond with empty page 2
    let pageParam = '0';
    await installMockApi(page, { multiplePages: true });
    await page.route('**/api/v1/weeks/*/team/summary*', async (route) => {
      const url = new URL(route.request().url());
      pageParam = url.searchParams.get('page') ?? '0';
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          weekStart: mondayIso(),
          users: [],
          reviewStatusCounts: { pending: 0, approved: 0, changesRequested: 0 },
          page: Number(pageParam),
          size: 20,
          totalElements: 40,
          totalPages: 2,
        }),
      });
    });

    await goToTeamDashboard(page);

    // Click Next
    await page.getByTestId('next-page').click();

    // Now on page 2 — Previous should be enabled
    await expect(async () => {
      await expect(page.getByTestId('prev-page')).toBeEnabled();
    }).toPass({ timeout: 3000 });

    // Next should now be disabled (last page)
    await expect(page.getByTestId('next-page')).toBeDisabled();
  });

  test('[FULL] Pagination controls do NOT appear when only one page of results', async ({ page }) => {
    await installMockApi(page, { multiplePages: false });
    await goToTeamDashboard(page);

    // With totalPages=1, pagination block should not be rendered
    await expect(page.getByTestId('pagination')).not.toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Empty team
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard: Empty State', () => {
  test('[FULL] Shows empty-state message when manager has no direct reports', async ({ page }) => {
    await installMockApi(page, { teamSummaryUsers: [] });
    await goToTeamDashboard(page);

    // Grid is replaced by empty state
    await expect(page.getByTestId('team-summary-empty')).toBeVisible();
    await expect(page.getByTestId('team-summary-empty')).toContainText('No direct reports found');
  });
});
