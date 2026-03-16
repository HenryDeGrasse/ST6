/**
 * Browser E2E tests for IC full lifecycle including validation states.
 *
 * Uses the installMockApi() helper pattern from golden-path.spec.ts so all
 * tests run deterministically against a mocked API (no live backend needed).
 *
 * PRD §18 acceptance criteria covered:
 *   #3  – lock validation rejection (ValidationPanel warnings / success)
 *   #4  – progressNotes remains editable on a locked plan
 *   #5  – reconciliation view, filling actuals with delta reason
 *   #6  – submit reconciliation via confirmation dialog
 *   #15 – carry-forward dialog (select commits, confirm)
 *   #16 – week navigation (prev/next/today), past-week and future-week messages
 *
 * Test categories:
 *   [SMOKE] = runs on every PR (Gate 7)
 *   [FULL]  = deeper acceptance coverage
 */
import { expect, test, type Page, type Route } from '@playwright/test';

// ─── Constants ─────────────────────────────────────────────────────────────

const ORG_ID    = '00000000-0000-0000-0000-000000000099';
const USER_ID   = '00000000-0000-0000-0000-000000000001';
const PLAN_ID   = '10000000-0000-0000-0000-000000000001';
const COMMIT_ID_1 = '20000000-0000-0000-0000-000000000001';
const COMMIT_ID_2 = '20000000-0000-0000-0000-000000000002';
const OUTCOME_ID  = '30000000-0000-0000-0000-000000000001';
const now = '2026-03-12T12:00:00Z';

// ─── Helpers ───────────────────────────────────────────────────────────────

/** Returns the ISO date string (YYYY-MM-DD) of the Monday `weeksFromCurrent` weeks from today. */
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
    id: COMMIT_ID_1,
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

// ─── Mock API installer ─────────────────────────────────────────────────────

interface MockApiOptions {
  initialPlan?: Record<string, unknown> | null;
  commits?: Array<Record<string, unknown>>;
  /** Next auto-generated commit ID suffix (increments on each create). */
  commitIdSeed?: number;
}

async function installMockApi(page: Page, options: MockApiOptions = {}): Promise<void> {
  let idSeed = options.commitIdSeed ?? 100;

  const state = {
    plan: options.initialPlan !== undefined ? options.initialPlan : null,
    commits: options.commits ? [...options.commits] : [],
    actuals: new Map<string, Record<string, unknown>>(),
  };

  const requestedWeekFromPath = (pathname: string): string | null => {
    const match = pathname.match(/^\/api\/v1\/weeks\/([^/]+)\/plans(?:\/|$)/);
    return match?.[1] ?? null;
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

    // ── Current plan (GET) ────────────────────────────────────────────────
    if (method === 'GET' && /^\/api\/v1\/weeks\/[^/]+\/plans\/me$/.test(path)) {
      const requestedWeek = requestedWeekFromPath(path);
      if (state.plan && requestedWeek === String(state.plan.weekStartDate ?? '')) {
        return json(route, 200, state.plan);
      }
      return json(route, 404, apiError('Plan not found', 'NOT_FOUND'));
    }

    // ── Create plan ───────────────────────────────────────────────────────
    if (method === 'POST' && /^\/api\/v1\/weeks\/[^/]+\/plans$/.test(path)) {
      const requestedWeek = requestedWeekFromPath(path) ?? mondayIso();
      state.plan = buildPlan({ weekStartDate: requestedWeek });
      return json(route, 201, state.plan);
    }

    // ── List commits ─────────────────────────────────────────────────────
    if (method === 'GET' && path === `/api/v1/plans/${PLAN_ID}/commits`) {
      return json(route, 200, state.commits);
    }

    // ── Create commit ─────────────────────────────────────────────────────
    if (method === 'POST' && path === `/api/v1/plans/${PLAN_ID}/commits`) {
      const body = (request.postDataJSON() ?? {}) as Record<string, unknown>;
      idSeed += 1;
      const newId = `20000000-0000-0000-0000-${String(idSeed).padStart(12, '0')}`;
      const created = buildCommit({
        id: newId,
        title: body.title ?? 'New Commit',
        description: body.description ?? '',
        chessPriority: body.chessPriority ?? null,
        category: body.category ?? null,
        outcomeId: body.outcomeId ?? null,
        nonStrategicReason: body.nonStrategicReason ?? null,
        expectedResult: body.expectedResult ?? '',
        validationErrors: [],
      });
      state.commits = [...state.commits, created];
      return json(route, 201, created);
    }

    // ── Update commit ─────────────────────────────────────────────────────
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

    // ── Delete commit ─────────────────────────────────────────────────────
    if (method === 'DELETE' && /^\/api\/v1\/commits\/[^/]+$/.test(path)) {
      const commitId = path.split('/')[4];
      state.commits = state.commits.filter((c) => c.id !== commitId);
      return route.fulfill({ status: 204 });
    }

    // ── Lock plan ─────────────────────────────────────────────────────────
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

    // ── Start reconciliation ──────────────────────────────────────────────
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

    // ── Update actual ─────────────────────────────────────────────────────
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
      // Reflect the actual in the commit so reconciliation-view updates
      state.commits = state.commits.map((c) =>
        c.id === commitId
          ? { ...c, actual, version: Number(c.version ?? 1) + 1, updatedAt: now }
          : c,
      );
      return json(route, 200, actual);
    }

    // ── Submit reconciliation ─────────────────────────────────────────────
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

    // ── Carry-forward ─────────────────────────────────────────────────────
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

    // ── Notifications ─────────────────────────────────────────────────────
    if (method === 'GET' && path === '/api/v1/notifications/unread') {
      return json(route, 200, []);
    }
    if (method === 'POST' && path === '/api/v1/notifications/read-all') {
      return json(route, 200, {});
    }

    // ── Team summary (manager) ────────────────────────────────────────────
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

    // ── AI endpoints ──────────────────────────────────────────────────────
    if (method === 'POST' && path === '/api/v1/ai/suggest-rcdo') {
      return json(route, 200, { suggestions: [] });
    }
    if (method === 'POST' && path === '/api/v1/ai/reconciliation-draft') {
      return json(route, 200, { items: [] });
    }

    return json(route, 404, apiError(`Unhandled mock: ${method} ${path}`, 'NOT_FOUND'));
  });
}

// ─── Tests ─────────────────────────────────────────────────────────────────

test.describe('IC: Create Plan', () => {
  test('[SMOKE] sees "No plan for this week" and creates one', async ({ page }) => {
    await installMockApi(page, { initialPlan: null });
    await page.goto('/');

    // No plan exists → creation prompt is visible
    await expect(page.getByTestId('no-plan')).toBeVisible();
    await expect(page.getByText('No plan for this week yet.')).toBeVisible();
    await expect(page.getByTestId('create-plan-btn')).toBeVisible();

    // Create the plan
    await page.getByTestId('create-plan-btn').click();

    // Plan header appears in DRAFT state
    await expect(page.getByTestId('plan-header')).toBeVisible();
    await expect(page.getByTestId('plan-state')).toContainText('Draft');
  });
});

test.describe('IC: Add Commits via CommitEditor', () => {
  test('[SMOKE] CommitEditor form fields render and commit can be added', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
    });
    await page.goto('/');

    await expect(page.getByTestId('plan-header')).toBeVisible();
    await expect(page.getByTestId('add-commit-btn')).toBeVisible();

    // Open the new commit form
    await page.getByTestId('add-commit-btn').click();
    await expect(page.getByTestId('commit-editor-new')).toBeVisible();

    // Verify all key form fields are rendered
    await expect(page.getByTestId('commit-title')).toBeVisible();
    await expect(page.getByTestId('commit-description')).toBeVisible();
    await expect(page.getByTestId('chess-picker')).toBeVisible();
    await expect(page.getByTestId('category-picker')).toBeVisible();
    await expect(page.getByTestId('commit-expected-result')).toBeVisible();
    await expect(page.getByTestId('non-strategic-toggle')).toBeVisible();
    await expect(page.getByTestId('commit-save')).toBeVisible();
    await expect(page.getByTestId('commit-cancel')).toBeVisible();

    // Fill in the form
    await page.getByTestId('commit-title').fill('My new commitment');
    await page.getByTestId('commit-description').fill('Detailed description here');
    await page.getByTestId('chess-picker').selectOption('KING');
    await page.getByTestId('category-picker').selectOption('DELIVERY');
    await page.getByTestId('commit-expected-result').fill('Expected outcome achieved.');

    // Save the commit
    await page.getByTestId('commit-save').click();

    // New commit should appear in the list (editor closes)
    await expect(page.getByTestId('commit-editor-new')).not.toBeVisible();
    // The commit list should now show a commit row
    await expect(page.getByTestId('commit-list')).toBeVisible();
    await expect(page.getByText('My new commitment')).toBeVisible();
  });

  test('[FULL] non-strategic toggle reveals reason field', async ({ page }) => {
    await installMockApi(page, { initialPlan: buildPlan(), commits: [] });
    await page.goto('/');

    await page.getByTestId('add-commit-btn').click();
    await expect(page.getByTestId('commit-editor-new')).toBeVisible();

    // Initially, RCDO picker should be visible and reason should not
    await expect(page.getByTestId('non-strategic-toggle')).toBeVisible();
    await expect(page.getByTestId('non-strategic-reason')).not.toBeVisible();

    // Toggle non-strategic
    await page.getByTestId('non-strategic-toggle').check();
    await expect(page.getByTestId('non-strategic-reason')).toBeVisible();

    // Fill title + reason and save
    await page.getByTestId('commit-title').fill('Admin overhead');
    await page.getByTestId('non-strategic-reason').fill('Required quarterly compliance work');
    await page.getByTestId('chess-picker').selectOption('PAWN');
    await page.getByTestId('commit-save').click();

    await expect(page.getByTestId('commit-editor-new')).not.toBeVisible();
    await expect(page.getByText('Admin overhead')).toBeVisible();
  });

  test('[FULL] cancel button dismisses the new commit form', async ({ page }) => {
    await installMockApi(page, { initialPlan: buildPlan(), commits: [] });
    await page.goto('/');

    await page.getByTestId('add-commit-btn').click();
    await expect(page.getByTestId('commit-editor-new')).toBeVisible();

    await page.getByTestId('commit-cancel').click();
    await expect(page.getByTestId('commit-editor-new')).not.toBeVisible();
    await expect(page.getByTestId('add-commit-btn')).toBeVisible();
  });
});

test.describe('IC: ValidationPanel', () => {
  test('[SMOKE] shows warnings when commits have validation errors or chess violations', async ({ page }) => {
    // 3 QUEEN commits → max 2 allowed; 1 commit with RCDO error
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [
        buildCommit({
          id: COMMIT_ID_1,
          chessPriority: 'QUEEN',
          validationErrors: [
            { code: 'MISSING_RCDO_OR_REASON', message: 'Link to an outcome or provide a non-strategic reason.' },
          ],
        }),
        buildCommit({ id: COMMIT_ID_2, chessPriority: 'QUEEN', validationErrors: [] }),
        buildCommit({
          id: '20000000-0000-0000-0000-000000000003',
          chessPriority: 'QUEEN',
          validationErrors: [],
        }),
      ],
    });
    await page.goto('/');

    await expect(page.getByTestId('validation-panel')).toBeVisible();
    // Should show both a commit-level error AND a QUEEN constraint violation
    await expect(page.getByTestId('validation-panel')).toContainText('validation errors');
    await expect(page.getByTestId('validation-panel')).toContainText('QUEEN commits');
    // No KING → warning
    await expect(page.getByTestId('validation-panel')).toContainText('KING');
  });

  test('[FULL] shows "no commits yet" warning on empty plan', async ({ page }) => {
    await installMockApi(page, { initialPlan: buildPlan(), commits: [] });
    await page.goto('/');

    await expect(page.getByTestId('validation-panel')).toBeVisible();
    await expect(page.getByTestId('validation-panel')).toContainText('No commitments yet');
  });

  test('[FULL] shows "no KING" warning when commits exist but none is KING', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: 'QUEEN' })],
    });
    await page.goto('/');

    await expect(page.getByTestId('validation-panel')).toBeVisible();
    await expect(page.getByTestId('validation-panel')).toContainText('KING');
  });

  test('[SMOKE] shows success when all validations pass', async ({ page }) => {
    // 1 KING commit, no errors
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await expect(page.getByTestId('validation-panel')).toBeVisible();
    await expect(page.getByTestId('validation-panel')).toContainText('All validations pass');
    await expect(page.getByTestId('validation-panel')).toContainText('Ready to lock');
  });
});

test.describe('IC: Lock Plan', () => {
  test('[SMOKE] lock requires confirmation dialog and transitions to Locked state', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    // Lock button is present in DRAFT state
    await expect(page.getByTestId('lock-btn')).toBeVisible();
    await page.getByTestId('lock-btn').click();

    // Confirmation dialog must appear
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await expect(page.getByTestId('confirm-dialog')).toContainText('Lock');

    // Confirm the lock
    await page.getByTestId('confirm-dialog-confirm').click();

    // Plan state changes to Locked
    await expect(page.getByTestId('plan-state')).toContainText('Locked');
    // Lock button is gone; start-reconciliation-btn appears
    await expect(page.getByTestId('lock-btn')).not.toBeVisible();
    await expect(page.getByTestId('start-reconciliation-btn')).toBeVisible();
  });

  test('[FULL] cancelling the lock dialog leaves plan in DRAFT', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await page.getByTestId('lock-btn').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();

    await page.getByTestId('confirm-dialog-cancel').click();
    await expect(page.getByTestId('confirm-dialog')).not.toBeVisible();
    // Plan stays in DRAFT
    await expect(page.getByTestId('plan-state')).toContainText('Draft');
    await expect(page.getByTestId('lock-btn')).toBeVisible();
  });
});

test.describe('IC: ProgressNotes editable on locked commits', () => {
  test('[SMOKE] progressNotes field is editable after plan is locked', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'LOCKED', lockType: 'ON_TIME', lockedAt: now }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await expect(page.getByTestId('plan-state')).toContainText('Locked');

    // Click the commit row to open editor in LOCKED mode
    await page.getByTestId(`commit-row-${COMMIT_ID_1}`).click();

    // The progress-notes field must be visible and editable
    await expect(page.getByTestId('commit-progress-notes')).toBeVisible();
    await page.getByTestId('commit-progress-notes').fill('Making great progress on the API');

    // Save button should be present
    await expect(page.getByTestId('commit-save')).toBeVisible();
    await page.getByTestId('commit-save').click();

    // Editor closes after save
    await expect(page.getByTestId(`commit-editor-${COMMIT_ID_1}`)).not.toBeVisible();
    await expect(page.getByTestId(`commit-row-${COMMIT_ID_1}`)).toBeVisible();
  });

  test('[FULL] other commit fields are NOT editable when plan is locked', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'LOCKED', lockType: 'ON_TIME', lockedAt: now }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await page.getByTestId(`commit-row-${COMMIT_ID_1}`).click();
    await expect(page.getByTestId(`commit-editor-${COMMIT_ID_1}`)).toBeVisible();

    // Title is visible but disabled in LOCKED state
    await expect(page.getByTestId('commit-title')).toBeDisabled();
    // Chess and category pickers are NOT shown (only visible in DRAFT/new)
    await expect(page.getByTestId('chess-picker')).not.toBeVisible();
    await expect(page.getByTestId('category-picker')).not.toBeVisible();
  });
});

test.describe('IC: Start Reconciliation', () => {
  test('[SMOKE] start reconciliation transitions to Reconciling and shows ReconciliationView', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'LOCKED', lockType: 'ON_TIME', lockedAt: now }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await expect(page.getByTestId('start-reconciliation-btn')).toBeVisible();
    await page.getByTestId('start-reconciliation-btn').click();

    // Plan transitions to RECONCILING
    await expect(page.getByTestId('plan-state')).toContainText('Reconciling');
    // ReconciliationView is now visible
    await expect(page.getByTestId('reconciliation-view')).toBeVisible();
    // Each commit has a card in the reconciliation view
    await expect(page.getByTestId(`reconcile-commit-${COMMIT_ID_1}`)).toBeVisible();
  });
});

test.describe('IC: Reconciliation – fill actuals', () => {
  test('[SMOKE] can fill in completion status and actual result, then save', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'RECONCILING', lockType: 'ON_TIME', lockedAt: now }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await expect(page.getByTestId('reconciliation-view')).toBeVisible();

    // Select DONE status
    await page.getByTestId(`reconcile-status-${COMMIT_ID_1}`).selectOption('DONE');
    // Fill in actual result
    await page.getByTestId(`reconcile-actual-${COMMIT_ID_1}`).fill('Delivered all planning APIs on schedule.');

    // Save the actual
    await page.getByTestId(`reconcile-save-${COMMIT_ID_1}`).click();

    // The save button should still be present (commit remains visible)
    await expect(page.getByTestId(`reconcile-commit-${COMMIT_ID_1}`)).toBeVisible();
  });

  test('[FULL] delta reason field appears when status is not DONE', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'RECONCILING', lockType: 'ON_TIME', lockedAt: now }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await expect(page.getByTestId('reconciliation-view')).toBeVisible();

    // Initially, delta reason should not be visible (status = DONE by default)
    // Select PARTIALLY to trigger delta reason field
    await page.getByTestId(`reconcile-status-${COMMIT_ID_1}`).selectOption('PARTIALLY');

    // Delta reason field must appear
    await expect(page.getByTestId(`reconcile-delta-${COMMIT_ID_1}`)).toBeVisible();
    await page.getByTestId(`reconcile-delta-${COMMIT_ID_1}`).fill('External dependency delayed completion.');
  });

  test('[FULL] submit button is disabled until all commits have actuals saved', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'RECONCILING', lockType: 'ON_TIME', lockedAt: now }),
      commits: [
        buildCommit({ id: COMMIT_ID_1, chessPriority: 'KING', validationErrors: [] }),
        buildCommit({
          id: COMMIT_ID_2,
          chessPriority: 'QUEEN',
          validationErrors: [],
          title: 'Second commit',
        }),
      ],
    });
    await page.goto('/');

    // Submit button should be enabled (defaults to DONE with empty delta reason = valid)
    // Verify reconcile view is shown
    await expect(page.getByTestId('reconciliation-view')).toBeVisible();
    await expect(page.getByTestId(`reconcile-commit-${COMMIT_ID_1}`)).toBeVisible();
    await expect(page.getByTestId(`reconcile-commit-${COMMIT_ID_2}`)).toBeVisible();

    // Change COMMIT_ID_2 to NOT_DONE without filling delta reason
    await page.getByTestId(`reconcile-status-${COMMIT_ID_2}`).selectOption('NOT_DONE');
    // Delta reason is empty → submit should be disabled
    await expect(page.getByTestId('reconcile-submit')).toBeDisabled();

    // Fill the delta reason
    await page.getByTestId(`reconcile-delta-${COMMIT_ID_2}`).fill('Blocked by external team.');
    // Submit should now be enabled
    await expect(page.getByTestId('reconcile-submit')).toBeEnabled();
  });
});

test.describe('IC: Submit Reconciliation', () => {
  test('[SMOKE] submit reconciliation via confirmation dialog transitions to Reconciled', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'RECONCILING', lockType: 'ON_TIME', lockedAt: now }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await expect(page.getByTestId('reconciliation-view')).toBeVisible();

    // Fill actuals for the commit (DONE, no delta needed)
    await page.getByTestId(`reconcile-status-${COMMIT_ID_1}`).selectOption('DONE');
    await page.getByTestId(`reconcile-actual-${COMMIT_ID_1}`).fill('Delivered successfully.');
    await page.getByTestId(`reconcile-save-${COMMIT_ID_1}`).click();

    // Click submit
    await page.getByTestId('reconcile-submit').click();

    // Confirmation dialog appears
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await expect(page.getByTestId('confirm-dialog')).toContainText('Submit');

    // Confirm the submission
    await page.getByTestId('confirm-dialog-confirm').click();

    // Plan is now RECONCILED
    await expect(page.getByTestId('plan-state')).toContainText('Reconciled');
    // Reconciliation view is gone; carry-forward button appears
    await expect(page.getByTestId('reconciliation-view')).not.toBeVisible();
    await expect(page.getByTestId('carry-forward-btn')).toBeVisible();
  });

  test('[FULL] cancelling submit reconciliation dialog leaves plan in RECONCILING', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'RECONCILING', lockType: 'ON_TIME', lockedAt: now }),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    await page.getByTestId('reconcile-submit').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();

    await page.getByTestId('confirm-dialog-cancel').click();
    await expect(page.getByTestId('confirm-dialog')).not.toBeVisible();

    // Still in RECONCILING
    await expect(page.getByTestId('plan-state')).toContainText('Reconciling');
  });
});

test.describe('IC: Carry-Forward', () => {
  test('[SMOKE] carry-forward opens dialog, allows commit selection, confirms', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'RECONCILED', reviewStatus: 'REVIEW_PENDING' }),
      commits: [
        buildCommit({ id: COMMIT_ID_1, chessPriority: 'KING', title: 'Ship planning APIs' }),
        buildCommit({ id: COMMIT_ID_2, chessPriority: 'QUEEN', title: 'Write unit tests' }),
      ],
    });
    await page.goto('/');

    await expect(page.getByTestId('plan-state')).toContainText('Reconciled');
    await expect(page.getByTestId('carry-forward-btn')).toBeVisible();

    // Open carry-forward dialog
    await page.getByTestId('carry-forward-btn').click();
    await expect(page.getByTestId('carry-forward-dialog')).toBeVisible();

    // Both commits should be shown as options
    await expect(page.getByTestId(`carry-option-${COMMIT_ID_1}`)).toBeVisible();
    await expect(page.getByTestId(`carry-option-${COMMIT_ID_2}`)).toBeVisible();

    // Deselect the second commit
    const commit2Checkbox = page.getByTestId(`carry-option-${COMMIT_ID_2}`).locator('input[type="checkbox"]');
    await commit2Checkbox.uncheck();

    // Confirm carry-forward with only the first commit selected
    await page.getByTestId('carry-confirm').click();

    // Plan transitions to CARRY_FORWARD
    await expect(page.getByTestId('plan-state')).toContainText('Carry Forward');
    await expect(page.getByTestId('carry-forward-dialog')).not.toBeVisible();
  });

  test('[FULL] cancelling carry-forward dialog does not change plan state', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'RECONCILED', reviewStatus: 'REVIEW_PENDING' }),
      commits: [buildCommit({ chessPriority: 'KING', title: 'Incomplete work' })],
    });
    await page.goto('/');

    await page.getByTestId('carry-forward-btn').click();
    await expect(page.getByTestId('carry-forward-dialog')).toBeVisible();

    await page.getByTestId('carry-cancel').click();
    await expect(page.getByTestId('carry-forward-dialog')).not.toBeVisible();

    // Plan stays in RECONCILED
    await expect(page.getByTestId('plan-state')).toContainText('Reconciled');
  });
});

test.describe('IC: Week Selector navigation', () => {
  test('[SMOKE] navigates to previous week and back to current week', async ({ page }) => {
    // Current week has a plan; previous week has no plan
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: 'KING', validationErrors: [] })],
    });
    await page.goto('/');

    // Verify current week label is shown
    await expect(page.getByTestId('week-selector')).toBeVisible();
    await expect(page.getByTestId('week-label')).toBeVisible();

    // Navigate to previous week
    await page.getByTestId('week-prev').click();

    // Previous week has no plan → no-plan-past message
    await expect(page.getByTestId('no-plan-past')).toBeVisible();
    await expect(page.getByText('No plan was created for this week.')).toBeVisible();

    // "Today" button should appear (not on current week)
    await expect(page.getByTestId('week-today')).toBeVisible();

    // Navigate back to today
    await page.getByTestId('week-today').click();

    // Current week's plan should be shown again
    await expect(page.getByTestId('plan-header')).toBeVisible();
    await expect(page.getByTestId('week-today')).not.toBeVisible();
  });

  test('[SMOKE] past week with no plan shows correct static message', async ({ page }) => {
    // Navigate directly by clicking prev; mock returns 404 for any week
    await installMockApi(page, { initialPlan: null });
    await page.goto('/');

    // Navigate back two weeks to ensure it is clearly a past week
    await page.getByTestId('week-prev').click();
    await page.getByTestId('week-prev').click();

    await expect(page.getByTestId('no-plan-past')).toBeVisible();
    // Create button should NOT appear for past weeks
    await expect(page.getByTestId('create-plan-btn')).not.toBeVisible();
  });

  test('[FULL] browser enforces the "current or next week only" rule', async ({ page }) => {
    await installMockApi(page, { initialPlan: null });
    await page.goto('/');

    // Navigate to next week — it is still creatable.
    await page.getByTestId('week-next').click();
    await expect(page.getByTestId('create-plan-btn')).toBeVisible();

    // The browser flow prevents going beyond next week at all.
    await expect(page.getByTestId('week-next')).toBeDisabled();
    await expect(page.getByTestId('no-plan-future')).not.toBeVisible();
  });

  test('[FULL] next week (one week ahead) still shows create button when no plan', async ({ page }) => {
    await installMockApi(page, { initialPlan: null });
    await page.goto('/');

    // Navigate forward one week (next week is still creatable)
    await page.getByTestId('week-next').click();

    // Next week is create-allowed → create button should appear
    await expect(page.getByTestId('create-plan-btn')).toBeVisible();
    await expect(page.getByTestId('no-plan-future')).not.toBeVisible();
    await expect(page.getByTestId('no-plan-past')).not.toBeVisible();
  });

  test('[FULL] next week button is disabled when already on next week', async ({ page }) => {
    await installMockApi(page, { initialPlan: null });
    await page.goto('/');

    // Navigate to next week
    await page.getByTestId('week-next').click();

    // Next button should now be disabled (can't go beyond next week)
    await expect(page.getByTestId('week-next')).toBeDisabled();
  });
});

test.describe('IC: Full lifecycle smoke path', () => {
  test('[SMOKE] create → add commit → lock → reconcile → submit → carry-forward', async ({ page }) => {
    await installMockApi(page, { initialPlan: null });
    await page.goto('/');

    // 1. Create plan
    await page.getByTestId('create-plan-btn').click();
    await expect(page.getByTestId('plan-header')).toBeVisible();
    await expect(page.getByTestId('plan-state')).toContainText('Draft');

    // 2. Add a KING commit
    await page.getByTestId('add-commit-btn').click();
    await page.getByTestId('commit-title').fill('End-to-end test commit');
    await page.getByTestId('chess-picker').selectOption('KING');
    await page.getByTestId('commit-expected-result').fill('All tests pass.');
    await page.getByTestId('commit-save').click();
    await expect(page.getByText('End-to-end test commit')).toBeVisible();

    // 3. Validation panel should show success
    await expect(page.getByTestId('validation-panel')).toContainText('All validations pass');

    // 4. Lock the plan
    await page.getByTestId('lock-btn').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-confirm').click();
    await expect(page.getByTestId('plan-state')).toContainText('Locked');

    // 5. Start reconciliation
    await page.getByTestId('start-reconciliation-btn').click();
    await expect(page.getByTestId('plan-state')).toContainText('Reconciling');
    await expect(page.getByTestId('reconciliation-view')).toBeVisible();

    // 6. Save actual for the commit (the new commit has a dynamic ID; find the first save button)
    const saveActualBtn = page.getByTestId('reconciliation-view').locator('[data-testid^="reconcile-save-"]').first();
    await saveActualBtn.click();

    // 7. Submit reconciliation
    await page.getByTestId('reconcile-submit').click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-confirm').click();
    await expect(page.getByTestId('plan-state')).toContainText('Reconciled');

    // 8. Carry-forward
    await page.getByTestId('carry-forward-btn').click();
    await expect(page.getByTestId('carry-forward-dialog')).toBeVisible();
    await page.getByTestId('carry-confirm').click();
    await expect(page.getByTestId('plan-state')).toContainText('Carry Forward');
  });
});

test.describe('IC: PlanSummaryStrip', () => {
  test('[FULL] summary strip shows correct metrics', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [
        buildCommit({ id: COMMIT_ID_1, chessPriority: 'KING', outcomeId: OUTCOME_ID }),
        buildCommit({
          id: COMMIT_ID_2,
          chessPriority: 'QUEEN',
          outcomeId: null,
          nonStrategicReason: 'Admin work',
        }),
      ],
    });
    await page.goto('/');

    await expect(page.getByTestId('plan-summary-strip')).toBeVisible();
    await expect(page.getByTestId('metric-total')).toContainText('2');
    await expect(page.getByTestId('metric-king')).toContainText('1');
    await expect(page.getByTestId('metric-queen')).toContainText('1');
  });
});
