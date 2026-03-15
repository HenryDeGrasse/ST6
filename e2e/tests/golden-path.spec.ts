/**
 * Playwright acceptance coverage mapped to PRD §18.
 *
 * These scenarios run the PA host stub and mock the API boundary so the
 * micro-frontend can be exercised deterministically in CI without depending
 * on an external backend deployment.
 *
 * Test categories:
 * - [SMOKE] = runs on every PR (Gate 7)
 * - [FULL]  = deeper acceptance coverage, suitable for nightly runs
 */
import { expect, test, type Page, type Route } from '@playwright/test';

const ORG_ID = '00000000-0000-0000-0000-000000000099';
const USER_ID = '00000000-0000-0000-0000-000000000001';
const MANAGER_USER_ID = '00000000-0000-0000-0000-000000000101';
const REPORT_USER_ID = '00000000-0000-0000-0000-000000000202';
const PLAN_ID = '10000000-0000-0000-0000-000000000001';
const COMMIT_ID = '20000000-0000-0000-0000-000000000001';
const OUTCOME_ID = '30000000-0000-0000-0000-000000000001';
const now = '2026-03-12T12:00:00Z';

interface MockApiOptions {
  initialPlan?: Record<string, unknown> | null;
  commits?: Array<Record<string, unknown>>;
  teamSummaryUsers?: Array<Record<string, unknown>>;
  teamPlan?: Record<string, unknown> | null;
  teamCommits?: Array<Record<string, unknown>>;
  denyManagerDrillDown?: boolean;
  createPlanSeedsCommits?: boolean;
}

function mondayIso(weeksFromCurrent = 0): string {
  const today = new Date();
  const day = today.getUTCDay();
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()));
  monday.setUTCDate(monday.getUTCDate() + diffToMonday + weeksFromCurrent * 7);
  return monday.toISOString().slice(0, 10);
}

function buildPlan(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: PLAN_ID,
    orgId: ORG_ID,
    ownerUserId: USER_ID,
    weekStartDate: mondayIso(),
    state: 'DRAFT',
    reviewStatus: 'REVIEW_NOT_APPLICABLE',
    lockType: null,
    lockedAt: null,
    carryForwardExecutedAt: null,
    version: 1,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function buildCommit(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: COMMIT_ID,
    weeklyPlanId: PLAN_ID,
    title: 'Ship planning APIs',
    description: 'Finalize lifecycle endpoints and validations.',
    chessPriority: 'KING',
    category: 'DELIVERY',
    outcomeId: OUTCOME_ID,
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
    createdAt: now,
    updatedAt: now,
    validationErrors: [],
    actual: null,
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
  return {
    error: {
      code,
      message,
      details: [],
    },
  };
}

async function installMockApi(page: Page, options: MockApiOptions = {}): Promise<void> {
  const state = {
    plan: options.initialPlan ?? null,
    commits: options.commits ? [...options.commits] : [],
    teamPlan: options.teamPlan ?? buildPlan({
      id: '10000000-0000-0000-0000-000000000999',
      ownerUserId: REPORT_USER_ID,
      state: 'RECONCILED',
      reviewStatus: 'REVIEW_PENDING',
      lockType: 'ON_TIME',
      lockedAt: now,
    }),
    teamCommits: options.teamCommits ?? [buildCommit({ weeklyPlanId: '10000000-0000-0000-0000-000000000999' })],
    teamSummaryUsers: options.teamSummaryUsers ?? [
      {
        userId: REPORT_USER_ID,
        planId: '10000000-0000-0000-0000-000000000999',
        state: 'RECONCILED',
        reviewStatus: 'REVIEW_PENDING',
        commitCount: 1,
        incompleteCount: 0,
        nonStrategicCount: 0,
        kingCount: 1,
        queenCount: 0,
        lastUpdated: now,
        isStale: false,
        isLateLock: false,
      },
    ],
    actuals: new Map<string, Record<string, unknown>>(),
  };

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

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
                outcomes: [
                  {
                    id: OUTCOME_ID,
                    name: 'Increase trial-to-paid by 20%',
                    objectiveId: 'obj-1',
                  },
                ],
              },
            ],
          },
        ],
      });
    }

    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/plans\/me$/.test(path)) {
      if (state.plan) {
        return json(route, 200, state.plan);
      }
      return json(route, 404, apiError('Plan not found', 'NOT_FOUND'));
    }

    if (method === 'POST' && /^\/api\/v1\/weeks\/[^/]+\/plans$/.test(path)) {
      state.plan = buildPlan();
      if (options.createPlanSeedsCommits && state.commits.length === 0) {
        state.commits = [buildCommit()];
      }
      return json(route, 201, state.plan);
    }

    if (method === 'GET' && path === `/api/v1/plans/${PLAN_ID}/commits`) {
      return json(route, 200, state.commits);
    }

    if (method === 'POST' && path === `/api/v1/plans/${PLAN_ID}/lock`) {
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: 'LOCKED',
        lockType: 'ON_TIME',
        lockedAt: now,
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: now,
      });
      return json(route, 200, state.plan);
    }

    if (method === 'POST' && path === `/api/v1/plans/${PLAN_ID}/start-reconciliation`) {
      const isLateLock = state.plan?.state === 'DRAFT';
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: 'RECONCILING',
        lockType: isLateLock ? 'LATE_LOCK' : (state.plan?.lockType ?? 'ON_TIME'),
        lockedAt: state.plan?.lockedAt ?? now,
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: now,
      });
      return json(route, 200, state.plan);
    }

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
          ? { ...commit, version: Number(commit.version ?? 1) + 1, updatedAt: now }
          : commit,
      );
      return json(route, 200, state.actuals.get(commitId));
    }

    if (method === 'POST' && path === `/api/v1/plans/${PLAN_ID}/submit-reconciliation`) {
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: 'RECONCILED',
        reviewStatus: 'REVIEW_PENDING',
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: now,
      });
      return json(route, 200, state.plan);
    }

    if (method === 'POST' && path === `/api/v1/plans/${PLAN_ID}/carry-forward`) {
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: 'CARRY_FORWARD',
        carryForwardExecutedAt: now,
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: now,
      });
      return json(route, 200, state.plan);
    }

    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/team\/summary$/.test(path)) {
      return json(route, 200, {
        weekStart: mondayIso(),
        users: state.teamSummaryUsers,
        reviewStatusCounts: {
          pending: state.teamSummaryUsers.filter((user) => user.reviewStatus === 'REVIEW_PENDING').length,
          approved: state.teamSummaryUsers.filter((user) => user.reviewStatus === 'APPROVED').length,
          changesRequested: state.teamSummaryUsers.filter((user) => user.reviewStatus === 'CHANGES_REQUESTED').length,
        },
        page: 0,
        size: 20,
        totalElements: state.teamSummaryUsers.length,
        totalPages: 1,
      });
    }

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

    if (method === 'GET' && path === '/api/v1/notifications/unread') {
      return json(route, 200, []);
    }

    if (method === 'POST' && path === '/api/v1/notifications/read-all') {
      return json(route, 200, {});
    }

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

    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/plans\/[^/]+$/.test(path)) {
      if (options.denyManagerDrillDown) {
        return json(route, 403, apiError('Access denied', 'FORBIDDEN'));
      }
      if (state.teamPlan) {
        return json(route, 200, state.teamPlan);
      }
      return json(route, 404, apiError('Plan not found', 'NOT_FOUND'));
    }

    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/plans\/[^/]+\/commits$/.test(path)) {
      return json(route, 200, state.teamCommits);
    }

    if (method === 'POST' && /^\/api\/v1\/plans\/[^/]+\/review$/.test(path)) {
      return json(route, 201, {
        id: 'review-1',
        weeklyPlanId: state.teamPlan?.id ?? PLAN_ID,
        reviewerUserId: MANAGER_USER_ID,
        decision: 'APPROVED',
        comments: 'Looks good.',
        createdAt: now,
      });
    }

    return json(route, 404, apiError(`Unhandled mock route: ${method} ${path}`, 'NOT_FOUND'));
  });
}

test.describe('IC Create → Lock', () => {
  test('[SMOKE] IC can create a weekly plan and lock it', async ({ page }) => {
    await installMockApi(page, { createPlanSeedsCommits: true });

    await page.goto('/');

    await expect(page.getByTestId('pa-host-shell')).toBeVisible();
    await expect(page.getByTestId('create-plan-btn')).toBeVisible();

    await page.getByTestId('create-plan-btn').click();
    await expect(page.getByTestId('plan-header')).toBeVisible();
    await expect(page.getByTestId('commit-row-' + COMMIT_ID)).toBeVisible();

    await page.getByTestId('lock-btn').click();
    await expect(page.getByTestId('plan-state')).toContainText('Locked');
  });
});

test.describe('IC Reconcile → Carry-Forward', () => {
  test('[SMOKE] IC can reconcile and carry forward', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({
        state: 'LOCKED',
        lockType: 'ON_TIME',
        lockedAt: now,
      }),
      commits: [buildCommit()],
    });

    await page.goto('/');

    await expect(page.getByTestId('start-reconciliation-btn')).toBeVisible();
    await page.getByTestId('start-reconciliation-btn').click();
    await expect(page.getByTestId('reconciliation-view')).toBeVisible();

    await page.getByTestId(`reconcile-status-${COMMIT_ID}`).selectOption('DONE');
    await page.getByTestId(`reconcile-actual-${COMMIT_ID}`).fill('Delivered the planning workflow end to end.');
    await page.getByTestId(`reconcile-save-${COMMIT_ID}`).click();
    await page.getByTestId('reconcile-submit').click();

    await expect(page.getByTestId('carry-forward-btn')).toBeVisible();
    await page.getByTestId('carry-forward-btn').click();
    await expect(page.getByTestId('carry-forward-dialog')).toBeVisible();
    await page.getByTestId('carry-confirm').click();

    await expect(page.getByTestId('plan-state')).toContainText('Carry Forward');
  });
});

test.describe('Manager Dashboard', () => {
  test('[SMOKE] Manager dashboard loads with team data', async ({ page }) => {
    await installMockApi(page);

    await page.goto('/');

    await page.getByTestId('nav-team-dashboard').click();
    await expect(page.getByTestId('team-dashboard-page')).toBeVisible();
    await expect(page.getByTestId('team-summary-grid')).toBeVisible();
    await expect(page.getByTestId(`team-row-${REPORT_USER_ID}`)).toBeVisible();
  });
});

test.describe('Lock Rejection', () => {
  test('[FULL] Lock is rejected when validation fails', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [
        buildCommit({
          id: 'commit-invalid',
          chessPriority: 'QUEEN',
          validationErrors: [
            {
              code: 'MISSING_RCDO_OR_REASON',
              message: 'Link to an outcome or provide a non-strategic reason.',
            },
          ],
        }),
        buildCommit({
          id: 'commit-invalid-2',
          chessPriority: 'QUEEN',
          validationErrors: [],
        }),
        buildCommit({
          id: 'commit-invalid-3',
          chessPriority: 'QUEEN',
          validationErrors: [],
        }),
      ],
    });

    await page.goto('/');

    await expect(page.getByTestId('validation-panel')).toBeVisible();
    await expect(page.getByTestId('validation-panel')).toContainText('validation errors');
    await expect(page.getByTestId('validation-panel')).toContainText('QUEEN commits — max 2 allowed');
  });
});

test.describe('Late Lock Path', () => {
  test('[FULL] Late lock badge is shown when reconciliation starts after the week closes', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({
        weekStartDate: mondayIso(-1),
        state: 'RECONCILING',
        lockType: 'LATE_LOCK',
        lockedAt: now,
      }),
      commits: [buildCommit()],
    });

    await page.goto('/');

    await expect(page.getByTestId('plan-header')).toBeVisible();
    await expect(page.getByTestId('plan-late-lock')).toBeVisible();
  });
});

test.describe('Permission Denial', () => {
  test('[FULL] Manager drill-down surfaces a permission error cleanly', async ({ page }) => {
    await installMockApi(page, { denyManagerDrillDown: true });

    await page.goto('/');
    await page.getByTestId('nav-team-dashboard').click();
    await expect(page.getByTestId('team-summary-grid')).toBeVisible();

    await page.getByTestId(`drill-down-${REPORT_USER_ID}`).click();
    await expect(page.getByTestId('drilldown-error')).toContainText('Access denied');
  });
});
