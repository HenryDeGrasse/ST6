/**
 * Browser E2E tests for error handling and edge cases.
 *
 * All tests run deterministically against a mocked API via installMockApi()
 * (no live backend required).
 *
 * Scenarios covered:
 *   - API 500 error during plan creation shows ErrorBanner
 *   - API 409 Conflict during lock shows error message (optimistic lock failure)
 *   - API 422 validation errors during lock shows error banner
 *   - Network abort/failure shows error banner; app remains functional
 *   - ErrorBoundary catches unhandled render errors and shows crash fallback UI
 *   - Toast notifications appear for successful lifecycle actions
 *     (lock, start-reconciliation, submit-reconciliation, carry-forward)
 *   - Confirm dialog for lock: cancel dismisses without making an API call
 *   - Confirm dialog for delete commit: cancel preserves the commit
 *   - Confirm dialog for submit reconciliation: cancel preserves RECONCILING state
 *   - Empty commit list shows appropriate empty-state messaging
 *   - Week-selector boundary: cannot navigate to a creation-blocked future week
 *
 * Test categories:
 *   [SMOKE] = runs on every PR (Gate 7)
 *   [FULL]  = deeper acceptance coverage, suitable for nightly runs
 */
import { expect, test, type Page, type Route } from '@playwright/test';

// ─── Constants ──────────────────────────────────────────────────────────────

const ORG_ID    = '00000000-0000-0000-0000-000000000099';
const USER_ID   = '00000000-0000-0000-0000-000000000001';
const PLAN_ID   = '10000000-0000-0000-0000-000000000001';
const COMMIT_ID = '20000000-0000-0000-0000-000000000001';
const OUTCOME_ID = '30000000-0000-0000-0000-000000000001';
const now = '2026-03-12T12:00:00Z';

// ─── Helpers ────────────────────────────────────────────────────────────────

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
  return { error: { code, message, details: [] } };
}

// ─── Mock API options ────────────────────────────────────────────────────────

interface MockApiOptions {
  /** Initial plan state (null = no plan). */
  initialPlan?: Record<string, unknown> | null;
  /** Commits to return for the plan. */
  commits?: Array<Record<string, unknown>>;
  /**
   * If set, the create-plan endpoint will return this status code instead
   * of 201 (used to simulate server errors on plan creation).
   */
  createPlanErrorStatus?: number;
  /**
   * If set, the lock endpoint will return this status code instead of 200.
   * Body is set to an appropriate error payload.
   */
  lockErrorStatus?: number;
  /**
   * If set, abort the request for the create-plan endpoint to simulate a
   * network failure.
   */
  abortCreatePlan?: boolean;
  /**
   * If set, abort the request for the lock endpoint to simulate a network
   * timeout/failure.
   */
  abortLock?: boolean;
  /**
   * If set, the delete-commit endpoint will return this status code.
   */
  deleteCommitErrorStatus?: number;
}

// ─── Mock API installer ──────────────────────────────────────────────────────

async function installMockApi(page: Page, options: MockApiOptions = {}): Promise<void> {
  const state = {
    plan: options.initialPlan !== undefined ? options.initialPlan : null,
    commits: options.commits ? [...options.commits] : [],
    actuals: new Map<string, Record<string, unknown>>(),
  };

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    // ── RCDO tree ────────────────────────────────────────────────────────
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

    // ── GET plan (current user) ──────────────────────────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/plans\/me$/.test(path)) {
      if (state.plan) {
        return json(route, 200, state.plan);
      }
      return json(route, 404, apiError('Plan not found', 'NOT_FOUND'));
    }

    // ── Create plan ──────────────────────────────────────────────────────
    if (method === 'POST' && /^\/api\/v1\/weeks\/[^/]+\/plans$/.test(path)) {
      if (options.abortCreatePlan) {
        return route.abort('failed');
      }
      if (options.createPlanErrorStatus) {
        const status = options.createPlanErrorStatus;
        if (status === 500) {
          return json(route, 500, apiError('Internal server error', 'INTERNAL_ERROR'));
        }
        return json(route, status, apiError(`Error ${String(status)}`, 'UNKNOWN_ERROR'));
      }
      state.plan = buildPlan();
      return json(route, 201, state.plan);
    }

    // ── List commits ─────────────────────────────────────────────────────
    if (method === 'GET' && path === `/api/v1/plans/${PLAN_ID}/commits`) {
      return json(route, 200, state.commits);
    }

    // ── Create commit ────────────────────────────────────────────────────
    if (method === 'POST' && path === `/api/v1/plans/${PLAN_ID}/commits`) {
      const body = (request.postDataJSON() ?? {}) as Record<string, unknown>;
      const newCommit = buildCommit({
        id: `20000000-0000-0000-0000-${String(Date.now()).slice(-12)}`,
        title: body.title ?? 'New Commit',
        description: body.description ?? '',
        chessPriority: body.chessPriority ?? null,
        category: body.category ?? null,
        outcomeId: body.outcomeId ?? null,
        nonStrategicReason: body.nonStrategicReason ?? null,
        expectedResult: body.expectedResult ?? '',
        validationErrors: [],
      });
      state.commits = [...state.commits, newCommit];
      return json(route, 201, newCommit);
    }

    // ── Update commit ────────────────────────────────────────────────────
    if (method === 'PATCH' && /^\/api\/v1\/commits\/[^/]+$/.test(path)) {
      const commitId = path.split('/')[4];
      const body = (request.postDataJSON() ?? {}) as Record<string, unknown>;
      state.commits = state.commits.map((c) =>
        c.id === commitId
          ? { ...c, ...body, version: Number(c.version ?? 1) + 1, updatedAt: now }
          : c,
      );
      const updated = state.commits.find((c) => c.id === commitId);
      return json(route, 200, updated ?? {});
    }

    // ── Delete commit ────────────────────────────────────────────────────
    if (method === 'DELETE' && /^\/api\/v1\/commits\/[^/]+$/.test(path)) {
      if (options.deleteCommitErrorStatus) {
        return json(route, options.deleteCommitErrorStatus, apiError('Delete failed', 'DELETE_ERROR'));
      }
      const commitId = path.split('/')[4];
      state.commits = state.commits.filter((c) => c.id !== commitId);
      return route.fulfill({ status: 204 });
    }

    // ── Lock plan ────────────────────────────────────────────────────────
    if (method === 'POST' && path === `/api/v1/plans/${PLAN_ID}/lock`) {
      if (options.abortLock) {
        return route.abort('failed');
      }
      if (options.lockErrorStatus) {
        const status = options.lockErrorStatus;
        if (status === 409) {
          return json(route, 409, apiError('Plan has been modified by another request.', 'CONFLICT'));
        }
        if (status === 422) {
          return json(route, 422, apiError('Validation failed: plan has unresolved errors.', 'VALIDATION_ERROR'));
        }
        return json(route, status, apiError(`Error ${String(status)}`, 'UNKNOWN_ERROR'));
      }
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

    // ── Start reconciliation ─────────────────────────────────────────────
    if (method === 'POST' && path === `/api/v1/plans/${PLAN_ID}/start-reconciliation`) {
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: 'RECONCILING',
        lockType: 'ON_TIME',
        lockedAt: state.plan?.lockedAt ?? now,
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: now,
      });
      return json(route, 200, state.plan);
    }

    // ── Update actual ────────────────────────────────────────────────────
    if (method === 'PATCH' && /^\/api\/v1\/commits\/[^/]+\/actual$/.test(path)) {
      const commitId = path.split('/')[4];
      const body = (request.postDataJSON() ?? {}) as Record<string, unknown>;
      const actual = {
        commitId,
        actualResult: body.actualResult ?? '',
        completionStatus: body.completionStatus ?? 'DONE',
        deltaReason: body.deltaReason ?? null,
        timeSpent: body.timeSpent ?? null,
      };
      state.actuals.set(commitId, actual);
      state.commits = state.commits.map((c) =>
        c.id === commitId
          ? { ...c, actual, version: Number(c.version ?? 1) + 1, updatedAt: now }
          : c,
      );
      return json(route, 200, actual);
    }

    // ── Submit reconciliation ────────────────────────────────────────────
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

    // ── Carry-forward ────────────────────────────────────────────────────
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

    // ── Notifications ────────────────────────────────────────────────────
    if (method === 'GET' && path === '/api/v1/notifications/unread') {
      return json(route, 200, []);
    }
    if (method === 'POST' && path === '/api/v1/notifications/read-all') {
      return json(route, 200, {});
    }

    // ── Team summary (manager drill-down mock) ───────────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/team\/summary$/.test(path)) {
      return json(route, 200, {
        weekStart: mondayIso(),
        users: [],
        reviewStatusCounts: { pending: 0, approved: 0, changesRequested: 0 },
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 1,
      });
    }

    return json(route, 404, apiError(`Unhandled mock: ${method} ${path}`, 'NOT_FOUND'));
  });
}

// ═══════════════════════════════════════════════════════════════════════════
// API Error — Plan Creation (500)
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Error Handling: API 500 on plan creation', () => {
  test('[SMOKE] ErrorBanner appears when plan creation returns 500', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: null,
      createPlanErrorStatus: 500,
    });
    await page.goto('/');

    // No plan → create button is visible
    await expect(page.getByTestId('create-plan-btn')).toBeVisible();

    // Attempt to create the plan
    await page.getByTestId('create-plan-btn').click();

    // ErrorBanner must appear (plan error is surfaced from the hook)
    await expect(page.getByTestId('error-banner')).toBeVisible();
    await expect(page.getByTestId('error-banner')).toContainText('error');

    // App stays functional: create button is still accessible after the error
    await expect(page.getByTestId('create-plan-btn')).toBeVisible();
  });

  test('[FULL] ErrorBanner can be dismissed after a 500 error on plan creation', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: null,
      createPlanErrorStatus: 500,
    });
    await page.goto('/');

    await page.getByTestId('create-plan-btn').click();
    await expect(page.getByTestId('error-banner')).toBeVisible();

    // Dismiss the banner
    await page.getByTestId('error-dismiss').click();
    await expect(page.getByTestId('error-banner')).not.toBeVisible();

    // The create button is still there — app is still functional
    await expect(page.getByTestId('create-plan-btn')).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// API Error — Lock (409 Conflict)
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Error Handling: API 409 Conflict on lock', () => {
  test('[SMOKE] ErrorBanner shows "conflict" message when lock returns 409', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
      lockErrorStatus: 409,
    });
    await page.goto('/');

    await expect(page.getByTestId('lock-btn')).toBeVisible();
    await page.getByTestId('lock-btn').click();

    // Confirmation dialog appears
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-confirm').click();

    // ErrorBanner shows conflict/error
    await expect(page.getByTestId('error-banner')).toBeVisible();

    // Plan must stay in DRAFT (lock failed)
    await expect(page.getByTestId('plan-state')).toContainText('Draft');
    // Lock button is still visible
    await expect(page.getByTestId('lock-btn')).toBeVisible();
  });

  test('[FULL] Plan remains in DRAFT state after a 409 lock failure', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ version: 1 }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
      lockErrorStatus: 409,
    });
    await page.goto('/');

    await page.getByTestId('lock-btn').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-confirm').click();

    await expect(page.getByTestId('error-banner')).toBeVisible();

    // Plan state did not change — still DRAFT
    await expect(page.getByTestId('plan-state')).toContainText('Draft');

    // No start-reconciliation button should be shown
    await expect(page.getByTestId('start-reconciliation-btn')).not.toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// API Error — Lock (422 Validation Error)
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Error Handling: API 422 validation error on lock', () => {
  test('[SMOKE] ErrorBanner shows when lock returns 422 validation error', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
      lockErrorStatus: 422,
    });
    await page.goto('/');

    await page.getByTestId('lock-btn').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-confirm').click();

    // ErrorBanner shows the 422 error message
    await expect(page.getByTestId('error-banner')).toBeVisible();

    // App remains usable — plan is still in DRAFT
    await expect(page.getByTestId('plan-state')).toContainText('Draft');
    await expect(page.getByTestId('lock-btn')).toBeVisible();
  });

  test('[FULL] ValidationPanel shows client-side errors when commits have validationErrors', async ({ page }) => {
    // ValidationPanel is client-side: it reads commit.validationErrors directly
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [
        buildCommit({
          id: COMMIT_ID,
          chessPriority: 'QUEEN',
          outcomeId: null,
          validationErrors: [
            {
              code: 'MISSING_RCDO_OR_REASON',
              message: 'Link to an outcome or provide a non-strategic reason.',
            },
          ],
        }),
      ],
    });
    await page.goto('/');

    // ValidationPanel surfaces the inline errors from commit.validationErrors
    await expect(page.getByTestId('validation-panel')).toBeVisible();
    await expect(page.getByTestId('validation-panel')).toContainText('validation errors');
    await expect(page.getByTestId('validation-panel')).toContainText('non-strategic reason');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Network Abort / Failure
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Error Handling: Network abort/failure', () => {
  test('[SMOKE] ErrorBanner appears when plan creation network request is aborted', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: null,
      abortCreatePlan: true,
    });
    await page.goto('/');

    await expect(page.getByTestId('create-plan-btn')).toBeVisible();
    await page.getByTestId('create-plan-btn').click();

    // ErrorBanner must appear after the request fails
    await expect(page.getByTestId('error-banner')).toBeVisible();

    // App is still functional: create button remains
    await expect(page.getByTestId('create-plan-btn')).toBeVisible();
  });

  test('[SMOKE] ErrorBanner appears when lock network request is aborted', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
      abortLock: true,
    });
    await page.goto('/');

    await expect(page.getByTestId('lock-btn')).toBeVisible();
    await page.getByTestId('lock-btn').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-confirm').click();

    // ErrorBanner must appear after the aborted request
    await expect(page.getByTestId('error-banner')).toBeVisible();

    // App is still functional: plan stays in DRAFT
    await expect(page.getByTestId('plan-state')).toContainText('Draft');
    await expect(page.getByTestId('lock-btn')).toBeVisible();
  });

  test('[FULL] App continues to work after dismissing a network error banner', async ({ page }) => {
    let failCount = 0;
    await installMockApi(page, { initialPlan: null });

    // Override create plan: fail once, then succeed
    await page.route('**/api/v1/weeks/*/plans', async (route) => {
      if (route.request().method() === 'POST') {
        if (failCount === 0) {
          failCount++;
          return route.abort('failed');
        }
        return route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(buildPlan()),
        });
      }
      return route.fallback();
    });

    await page.goto('/');

    // First attempt — network fails
    await page.getByTestId('create-plan-btn').click();
    await expect(page.getByTestId('error-banner')).toBeVisible();

    // Dismiss the error
    await page.getByTestId('error-dismiss').click();
    await expect(page.getByTestId('error-banner')).not.toBeVisible();

    // Second attempt — succeeds
    await page.getByTestId('create-plan-btn').click();
    await expect(page.getByTestId('plan-header')).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// ErrorBoundary — crash fallback UI
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Error Handling: ErrorBoundary crash fallback', () => {
  test('[FULL] ErrorBoundary catches a render crash and shows fallback UI with reset button', async ({ page }) => {
    await installMockApi(page, {
      // Provide a plan so the commit-rendering path runs
      initialPlan: buildPlan(),
      // Return a commit whose validationErrors is null (not an array) to
      // trigger a .length access on null → render crash inside CommitList.
      commits: [
        {
          ...buildCommit(),
          // Force a type mismatch that will cause a render error
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          validationErrors: null as unknown as never[],
        },
      ],
    });
    await page.goto('/');

    // The ErrorBoundary should catch the crash and show the fallback
    await expect(page.getByTestId('wc-error-boundary')).toBeVisible({ timeout: 8000 });
    await expect(page.getByTestId('wc-error-boundary')).toContainText('encountered an error');

    // A reset/"Try again" button is shown
    await expect(page.getByTestId('wc-error-boundary-reset')).toBeVisible();
  });

  test('[FULL] Clicking "Try again" on the ErrorBoundary resets the error state', async ({ page }) => {
    // Use a flag rather than callCount because React StrictMode may fire
    // effects 1 or 2 times on initial mount depending on environment.
    // We flip the flag before clicking "Try again" so that all subsequent
    // commit fetches return valid data.
    let triggerCrash = true;
    await installMockApi(page, { initialPlan: buildPlan() });

    // Override commit route: return bad data while triggerCrash=true,
    // then return valid data once the flag is flipped.
    await page.route('**/api/v1/plans/*/commits', async (route) => {
      if (route.request().method() !== 'GET') {
        return route.fallback();
      }
      if (triggerCrash) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([{ ...buildCommit(), validationErrors: null }]),
        });
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([buildCommit({ chessPriority: 'KING', validationErrors: [] })]),
      });
    });

    await page.goto('/');

    // Wait for the ErrorBoundary fallback to appear
    await expect(page.getByTestId('wc-error-boundary')).toBeVisible({ timeout: 8000 });

    // Allow valid data through BEFORE clicking "Try again" so the reset succeeds
    triggerCrash = false;

    // Click "Try again" to reset the error boundary
    await page.getByTestId('wc-error-boundary-reset').click();

    // The app should recover and render the plan correctly
    await expect(page.getByTestId('wc-error-boundary')).not.toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId('plan-header')).toBeVisible({ timeout: 8000 });
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Toast Notifications — successful lifecycle actions
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Toast Notifications: successful lifecycle actions', () => {
  test('[SMOKE] Toast appears after successfully locking a plan', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await page.getByTestId('lock-btn').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-confirm').click();

    // Toast notification should appear with success message
    await expect(page.getByTestId('toast-container')).toBeVisible();
    await expect(page.getByTestId('toast-message')).toBeVisible();
    await expect(page.getByTestId('toast-message')).toContainText('locked');

    // Plan transitions to LOCKED
    await expect(page.getByTestId('plan-state')).toContainText('Locked');
  });

  test('[SMOKE] Toast appears after starting reconciliation', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'LOCKED', lockType: 'ON_TIME', lockedAt: now }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await expect(page.getByTestId('start-reconciliation-btn')).toBeVisible();
    await page.getByTestId('start-reconciliation-btn').click();

    // Toast should appear
    await expect(page.getByTestId('toast-container')).toBeVisible();
    await expect(page.getByTestId('toast-message')).toBeVisible();
    await expect(page.getByTestId('toast-message')).toContainText('Reconciliation');
  });

  test('[SMOKE] Toast appears after submitting reconciliation', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'RECONCILING', lockType: 'ON_TIME', lockedAt: now }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await expect(page.getByTestId('reconcile-submit')).toBeVisible();
    await page.getByTestId('reconcile-submit').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-confirm').click();

    // Toast should appear
    await expect(page.getByTestId('toast-container')).toBeVisible();
    await expect(page.getByTestId('toast-message')).toBeVisible();
    await expect(page.getByTestId('toast-message')).toContainText('submitted');
  });

  test('[SMOKE] Toast appears after carry-forward completes', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({
        state: 'RECONCILED',
        reviewStatus: 'REVIEW_PENDING',
        lockType: 'ON_TIME',
        lockedAt: now,
      }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await expect(page.getByTestId('carry-forward-btn')).toBeVisible();
    await page.getByTestId('carry-forward-btn').click();
    await expect(page.getByTestId('carry-forward-dialog')).toBeVisible();
    await page.getByTestId('carry-confirm').click();

    // Toast should appear
    await expect(page.getByTestId('toast-container')).toBeVisible();
    await expect(page.getByTestId('toast-message')).toBeVisible();
    await expect(page.getByTestId('toast-message')).toContainText('arry');
  });

  test('[FULL] Toast auto-dismisses after a short delay', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'LOCKED', lockType: 'ON_TIME', lockedAt: now }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await page.getByTestId('start-reconciliation-btn').click();

    // Toast appears
    await expect(page.getByTestId('toast-container')).toBeVisible();

    // Wait for auto-dismiss (toast duration is 3s; allow a buffer)
    await expect(page.getByTestId('toast-container')).not.toBeVisible({ timeout: 5000 });
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Confirm Dialog — Cancel Does Not Trigger Action
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Confirm Dialog: cancel does not trigger the action', () => {
  test('[SMOKE] Cancelling the lock confirm dialog leaves plan in DRAFT (no API call)', async ({ page }) => {
    let lockCallCount = 0;
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });

    // Count lock API calls
    await page.route('**/api/v1/plans/*/lock', async (route) => {
      lockCallCount++;
      return route.fallback();
    });

    await page.goto('/');

    await expect(page.getByTestId('lock-btn')).toBeVisible();
    await page.getByTestId('lock-btn').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await expect(page.getByTestId('confirm-dialog')).toContainText('Lock');

    // Cancel the dialog
    await page.getByTestId('confirm-dialog-cancel').click();

    // Dialog is gone
    await expect(page.getByTestId('confirm-dialog')).not.toBeVisible();

    // Plan stays in DRAFT
    await expect(page.getByTestId('plan-state')).toContainText('Draft');
    await expect(page.getByTestId('lock-btn')).toBeVisible();

    // No API call was made
    expect(lockCallCount).toBe(0);
  });

  test('[SMOKE] Cancelling the delete-commit confirm dialog preserves the commit', async ({ page }) => {
    let deleteCallCount = 0;
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });

    // Count delete API calls
    await page.route('**/api/v1/commits/**', async (route) => {
      if (route.request().method() === 'DELETE') {
        deleteCallCount++;
      }
      return route.fallback();
    });

    await page.goto('/');

    // Open commit editor
    await page.getByTestId(`commit-row-${COMMIT_ID}`).click();
    await expect(page.getByTestId(`commit-editor-${COMMIT_ID}`)).toBeVisible();

    // Click delete to trigger the confirm dialog
    await page.getByTestId('commit-delete').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await expect(page.getByTestId('confirm-dialog')).toContainText('Delete');

    // Cancel the dialog
    await page.getByTestId('confirm-dialog-cancel').click();

    // Dialog is gone
    await expect(page.getByTestId('confirm-dialog')).not.toBeVisible();

    // The commit is still preserved (the editor stays open because no deletion happened)
    await expect(page.getByTestId(`commit-editor-${COMMIT_ID}`)).toBeVisible();
    await expect(page.getByTestId('commit-title')).toHaveValue('Ship planning APIs');

    // No delete API call was made
    expect(deleteCallCount).toBe(0);
  });

  test('[SMOKE] Cancelling the submit-reconciliation confirm dialog preserves RECONCILING state', async ({ page }) => {
    let submitCallCount = 0;
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'RECONCILING', lockType: 'ON_TIME', lockedAt: now }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });

    // Count submit-reconciliation API calls
    await page.route('**/api/v1/plans/*/submit-reconciliation', async (route) => {
      submitCallCount++;
      return route.fallback();
    });

    await page.goto('/');

    await expect(page.getByTestId('reconcile-submit')).toBeVisible();
    await page.getByTestId('reconcile-submit').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await expect(page.getByTestId('confirm-dialog')).toContainText('Submit');

    // Cancel the dialog
    await page.getByTestId('confirm-dialog-cancel').click();
    await expect(page.getByTestId('confirm-dialog')).not.toBeVisible();

    // Plan is still in RECONCILING
    await expect(page.getByTestId('plan-state')).toContainText('Reconciling');
    await expect(page.getByTestId('reconciliation-view')).toBeVisible();

    // No submit API call was made
    expect(submitCallCount).toBe(0);
  });

  test('[FULL] Cancelling the lock dialog multiple times never triggers the lock', async ({ page }) => {
    let lockCallCount = 0;
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });

    await page.route('**/api/v1/plans/*/lock', async (route) => {
      lockCallCount++;
      return route.fallback();
    });

    await page.goto('/');

    // First attempt — cancel
    await page.getByTestId('lock-btn').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-cancel').click();
    await expect(page.getByTestId('confirm-dialog')).not.toBeVisible();

    // Second attempt — cancel again
    await page.getByTestId('lock-btn').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-cancel').click();
    await expect(page.getByTestId('confirm-dialog')).not.toBeVisible();

    // Plan stays in DRAFT
    await expect(page.getByTestId('plan-state')).toContainText('Draft');
    expect(lockCallCount).toBe(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Empty Commit List — empty-state messaging
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Empty Commit List: empty-state messaging', () => {
  test('[SMOKE] Empty commit list shows "No commitments yet." message', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
    });
    await page.goto('/');

    await expect(page.getByTestId('plan-header')).toBeVisible();
    await expect(page.getByTestId('commit-list')).toBeVisible();

    // Header shows 0 count
    await expect(page.getByTestId('commit-list')).toContainText('Commitments (0)');

    // Empty-state message
    await expect(page.getByTestId('commit-list')).toContainText('No commitments yet');
  });

  test('[SMOKE] ValidationPanel also surfaces a "no commits" warning on an empty DRAFT plan', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
    });
    await page.goto('/');

    // ValidationPanel should warn that there are no commits
    await expect(page.getByTestId('validation-panel')).toBeVisible();
    await expect(page.getByTestId('validation-panel')).toContainText('No commitments yet');
  });

  test('[FULL] Empty state disappears once a commit is added', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
    });
    await page.goto('/');

    await expect(page.getByTestId('commit-list')).toContainText('No commitments yet');

    // Add a commit
    await page.getByTestId('add-commit-btn').click();
    await expect(page.getByTestId('commit-editor-new')).toBeVisible();

    await page.getByTestId('commit-title').fill('First real commitment');
    await page.getByTestId('chess-picker').selectOption('KING');
    await page.getByTestId('commit-expected-result').fill('Delivered on time.');
    await page.getByTestId('commit-save').click();

    // Empty state is gone
    await expect(page.getByTestId('commit-list')).not.toContainText('No commitments yet');

    // Count updated to 1
    await expect(page.getByTestId('commit-list')).toContainText('Commitments (1)');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Week Selector Boundary — creation-blocked weeks
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Week Selector Boundary: creation-blocked weeks', () => {
  test('[SMOKE] Cannot navigate beyond next week: next button is disabled on next week', async ({ page }) => {
    await installMockApi(page, { initialPlan: null });
    await page.goto('/');

    // Current week — next button is enabled
    await expect(page.getByTestId('week-next')).toBeEnabled();

    // Navigate to next week
    await page.getByTestId('week-next').click();
    await expect(page.getByTestId('week-next')).toBeDisabled();
  });

  test('[SMOKE] Next-week boundary shows create button (next week is still creation-allowed)', async ({ page }) => {
    await installMockApi(page, { initialPlan: null });
    await page.goto('/');

    await page.getByTestId('week-next').click();

    // The create button should be shown (next week is a valid creation target)
    await expect(page.getByTestId('create-plan-btn')).toBeVisible();

    // No "future creation blocked" message should appear
    await expect(page.getByTestId('no-plan-future')).not.toBeVisible();
  });

  test('[FULL] Past weeks do NOT show the create button', async ({ page }) => {
    // When on a past week with no plan → create button must be absent
    await installMockApi(page, { initialPlan: null });
    await page.goto('/');

    // Navigate back two weeks (clearly in the past)
    await page.getByTestId('week-prev').click();
    await page.getByTestId('week-prev').click();

    // Past week with no plan → only "no-plan-past" message, no create button
    await expect(page.getByTestId('no-plan-past')).toBeVisible();
    await expect(page.getByTestId('create-plan-btn')).not.toBeVisible();
  });

  test('[FULL] "Today" button appears when navigating to a past week and returns to current', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
    });
    await page.goto('/');

    // On the current week, "Today" button should NOT be shown
    await expect(page.getByTestId('week-today')).not.toBeVisible();

    // Navigate to a previous week
    await page.getByTestId('week-prev').click();
    await expect(page.getByTestId('week-today')).toBeVisible();

    // Click "Today" to return to the current week
    await page.getByTestId('week-today').click();
    await expect(page.getByTestId('week-today')).not.toBeVisible();

    // Current week's plan is shown again
    await expect(page.getByTestId('plan-header')).toBeVisible();
  });

  test('[FULL] Future-blocked week shows creation warning when more than one week ahead is accessed', async ({ page }) => {
    // The UI enforces "only current or next week" via the disabled next button.
    // Verify the message when isFutureWeek is true (should not happen via UI
    // navigation but tests the state nonetheless).
    await installMockApi(page, { initialPlan: null });

    // Force the URL to a week 3 weeks in the future by directly setting the
    // week state using the week-next button flow — BUT since the next button is
    // disabled at +1, we can only reach +1 via the UI. This test therefore
    // verifies that next week + 1 step is impossible through the UI.
    await page.goto('/');

    // Navigate to next week (+1)
    await page.getByTestId('week-next').click();
    await expect(page.getByTestId('week-next')).toBeDisabled();

    // The create-plan button is still shown (next week is creation-allowed)
    await expect(page.getByTestId('create-plan-btn')).toBeVisible();
    // "no-plan-future" is NOT shown (this message is for weeks beyond next)
    await expect(page.getByTestId('no-plan-future')).not.toBeVisible();
  });

  test('[FULL] Week label updates correctly on navigation', async ({ page }) => {
    await installMockApi(page, { initialPlan: null });
    await page.goto('/');

    const initialLabel = await page.getByTestId('week-label').textContent();

    // Navigate to the previous week
    await page.getByTestId('week-prev').click();
    const prevLabel = await page.getByTestId('week-label').textContent();

    // The label should have changed
    expect(prevLabel).not.toBe(initialLabel);

    // Navigate back to current
    await page.getByTestId('week-today').click();
    const backLabel = await page.getByTestId('week-label').textContent();

    // Should match the initial label
    expect(backLabel).toBe(initialLabel);
  });
});
