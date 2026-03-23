import { expect, test, type Page } from '@playwright/test';
import {
  apiError,
  buildCommit,
  buildPlan,
  installMockApi,
  json,
  MOCK_COMMIT_ID,
  MOCK_NOW,
  MOCK_OUTCOME_ID,
  MOCK_PLAN_ID,
  mondayIso,
} from './helpers';

type MockEntity = Record<string, unknown>;

const CARRY_FORWARD_COMMIT_ID = '20000000-ai-lc-0000-000000000099';
const NEXT_WORK_SUGGESTION_ID = 'sugg-ai-lifecycle-0001';
const NEXT_WORK_TITLE = 'Address strategic coverage gap for trial-to-paid outcome';
const MANUAL_COMMIT_TITLE = 'Improve API response time';
const AI_DRAFT_ACTUAL = 'Completed API reliability improvements as planned.';

interface LifecycleState {
  plan: MockEntity | null;
  commits: MockEntity[];
  nextWorkSuggestions: MockEntity[];
  createdCommitBodies: MockEntity[];
  createdCommitIds: string[];
  suggestionFeedbacks: MockEntity[];
  actualUpdates: MockEntity[];
  carryForwardCommitIds: string[];
  nextCommitSeed: number;
}

function buildDraftPlan(weekStart = mondayIso()): MockEntity {
  return buildPlan({
    weekStartDate: weekStart,
    state: 'DRAFT',
  });
}

function buildLockedPlan(weekStart = mondayIso()): MockEntity {
  return buildPlan({
    weekStartDate: weekStart,
    state: 'LOCKED',
    lockType: 'ON_TIME',
    lockedAt: MOCK_NOW,
    version: 2,
  });
}

function buildReconcilingPlan(weekStart = mondayIso()): MockEntity {
  return buildPlan({
    weekStartDate: weekStart,
    state: 'RECONCILING',
    lockType: 'ON_TIME',
    lockedAt: MOCK_NOW,
    version: 3,
  });
}

function buildCarryForwardCommit(): MockEntity {
  return buildCommit({
    id: CARRY_FORWARD_COMMIT_ID,
    title: 'Continue API reliability work',
    chessPriority: 'QUEEN',
    category: 'DELIVERY',
    carriedFromCommitId: MOCK_COMMIT_ID,
  });
}

function buildNextWorkSuggestion(): MockEntity {
  return {
    suggestionId: NEXT_WORK_SUGGESTION_ID,
    title: NEXT_WORK_TITLE,
    suggestedOutcomeId: MOCK_OUTCOME_ID,
    suggestedChessPriority: 'ROOK',
    confidence: 0.85,
    source: 'COVERAGE_GAP',
    sourceDetail: 'No commits linked to this outcome in the last 2 weeks.',
    rationale: 'This outcome is at risk of missing its quarterly target.',
  };
}

function buildRcdoSuggestion(): MockEntity {
  return {
    outcomeId: MOCK_OUTCOME_ID,
    outcomeName: 'Increase trial-to-paid by 20%',
    objectiveId: 'obj-1',
    objectiveName: 'Improve Conversion',
    rallyCryName: 'Scale Revenue',
    rationale: 'Direct alignment with strategic outcome.',
    confidence: 0.91,
  };
}

function buildQualityCheckResponse(): MockEntity {
  return {
    status: 'ok',
    nudges: [
      {
        type: 'MISSING_STRATEGIC_COMMITS',
        severity: 'WARNING',
        message: 'Only 1 of 2 commits is linked to a strategic outcome.',
      },
    ],
  };
}

function buildDraftFromHistoryResponse(weekStart: string): MockEntity {
  return {
    status: 'ok',
    planId: MOCK_PLAN_ID,
    weekStart,
    suggestedCommits: [
      {
        id: CARRY_FORWARD_COMMIT_ID,
        title: 'Continue API reliability work',
        chessPriority: 'QUEEN',
        category: 'DELIVERY',
        carriedFromCommitId: MOCK_COMMIT_ID,
      },
    ],
  };
}

function buildDraftReconciliationResponse(commitId: string): MockEntity {
  return {
    status: 'ok',
    drafts: [
      {
        commitId,
        suggestedStatus: 'DONE',
        suggestedActualResult: AI_DRAFT_ACTUAL,
        suggestedDeltaReason: null,
      },
    ],
  };
}

function buildCreatedCommit(id: string, body: MockEntity): MockEntity {
  const tags = Array.isArray(body.tags)
    ? body.tags.filter((tag): tag is string => typeof tag === 'string')
    : [];

  return buildCommit({
    id,
    title: typeof body.title === 'string' ? body.title : 'New commit',
    description: typeof body.description === 'string' ? body.description : '',
    chessPriority: typeof body.chessPriority === 'string' ? body.chessPriority : null,
    category: typeof body.category === 'string' ? body.category : null,
    outcomeId: typeof body.outcomeId === 'string' ? body.outcomeId : null,
    nonStrategicReason: typeof body.nonStrategicReason === 'string' ? body.nonStrategicReason : null,
    expectedResult: typeof body.expectedResult === 'string' ? body.expectedResult : '',
    tags,
  });
}

function bumpPlan(state: LifecycleState, overrides: MockEntity): MockEntity {
  state.plan = buildPlan({
    ...(state.plan ?? {}),
    ...overrides,
    version: Number(state.plan?.version ?? 1) + 1,
    updatedAt: MOCK_NOW,
  });
  return state.plan;
}

async function installLifecycleMocks(
  page: Page,
  options: {
    initialPlan?: MockEntity | null;
    commits?: MockEntity[];
    suggestions?: MockEntity[];
  } = {},
): Promise<LifecycleState> {
  await installMockApi(page, {
    initialPlan: options.initialPlan ?? null,
    commits: options.commits ?? [],
  });

  const state: LifecycleState = {
    plan: options.initialPlan ?? null,
    commits: [...(options.commits ?? [])],
    nextWorkSuggestions: [...(options.suggestions ?? [buildNextWorkSuggestion()])],
    createdCommitBodies: [],
    createdCommitIds: [],
    suggestionFeedbacks: [],
    actualUpdates: [],
    carryForwardCommitIds: [],
    nextCommitSeed: 700,
  };

  await page.route(/\/api\/v1\/weeks\/[^/]+\/plans\/me$/, async (route) => {
    if (route.request().method() !== 'GET') {
      return route.fallback();
    }

    if (state.plan) {
      return json(route, 200, state.plan);
    }

    return json(route, 404, apiError('Plan not found', 'NOT_FOUND'));
  });

  await page.route(/\/api\/v1\/plans\/draft-from-history$/, async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }

    const body = ((route.request().postDataJSON() as MockEntity | null) ?? {}) as MockEntity;
    const weekStart = typeof body.weekStart === 'string' ? body.weekStart : mondayIso();

    state.plan = buildDraftPlan(weekStart);
    state.commits = [buildCarryForwardCommit()];
    state.nextWorkSuggestions = [buildNextWorkSuggestion()];

    return json(route, 200, buildDraftFromHistoryResponse(weekStart));
  });

  await page.route(/\/api\/v1\/plans\/[^/]+\/commits$/, async (route) => {
    const method = route.request().method();
    const path = new URL(route.request().url()).pathname;

    if (path !== `/api/v1/plans/${MOCK_PLAN_ID}/commits`) {
      return route.fallback();
    }

    if (method === 'GET') {
      return json(route, 200, state.commits);
    }

    if (method === 'POST') {
      const body = ((route.request().postDataJSON() as MockEntity | null) ?? {}) as MockEntity;
      const newId = `20000000-0000-0000-0000-${String(++state.nextCommitSeed).padStart(12, '0')}`;
      const created = buildCreatedCommit(newId, body);

      state.createdCommitBodies.push(body);
      state.createdCommitIds.push(newId);
      state.commits = [...state.commits, created];

      return json(route, 201, created);
    }

    return route.fallback();
  });

  await page.route(/\/api\/v1\/ai\/suggest-next-work$/, async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }

    return json(route, 200, {
      status: 'ok',
      suggestions: state.nextWorkSuggestions,
    });
  });

  await page.route(/\/api\/v1\/suggestions\/next-work(\?.*)?$/, async (route) => {
    if (route.request().method() !== 'GET') {
      return route.fallback();
    }

    return json(route, 200, {
      status: 'ok',
      suggestions: state.nextWorkSuggestions,
    });
  });

  await page.route(/\/api\/v1\/ai\/suggestion-feedback$/, async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }

    const body = ((route.request().postDataJSON() as MockEntity | null) ?? {}) as MockEntity;
    state.suggestionFeedbacks.push(body);

    const suggestionId = typeof body.suggestionId === 'string' ? body.suggestionId : null;
    if (suggestionId) {
      state.nextWorkSuggestions = state.nextWorkSuggestions.filter(
        (suggestion) => suggestion.suggestionId !== suggestionId,
      );
    }

    return json(route, 200, { status: 'ok' });
  });

  await page.route(/\/api\/v1\/ai\/suggest-rcdo$/, async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }

    const body = ((route.request().postDataJSON() as MockEntity | null) ?? {}) as MockEntity;
    const title = typeof body.title === 'string' ? body.title : '';

    return json(route, 200, {
      status: 'ok',
      suggestions: title.trim().length >= 5 ? [buildRcdoSuggestion()] : [],
    });
  });

  await page.route(/\/api\/v1\/ai\/plan-quality-check$/, async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }

    return json(route, 200, buildQualityCheckResponse());
  });

  await page.route(/\/api\/v1\/plans\/[^/]+\/quality-check$/, async (route) => {
    if (route.request().method() !== 'GET') {
      return route.fallback();
    }

    return json(route, 200, buildQualityCheckResponse());
  });

  await page.route(new RegExp(`/api/v1/plans/${MOCK_PLAN_ID}/lock$`), async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }

    return json(
      route,
      200,
      bumpPlan(state, {
        state: 'LOCKED',
        lockType: 'ON_TIME',
        lockedAt: MOCK_NOW,
      }),
    );
  });

  await page.route(new RegExp(`/api/v1/plans/${MOCK_PLAN_ID}/start-reconciliation$`), async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }

    return json(
      route,
      200,
      bumpPlan(state, {
        state: 'RECONCILING',
        lockType: state.plan?.lockType ?? 'ON_TIME',
        lockedAt: state.plan?.lockedAt ?? MOCK_NOW,
      }),
    );
  });

  await page.route(/\/api\/v1\/ai\/draft-reconciliation$/, async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }

    const targetCommitId = String(state.commits[0]?.id ?? CARRY_FORWARD_COMMIT_ID);
    return json(route, 200, buildDraftReconciliationResponse(targetCommitId));
  });

  await page.route(/\/api\/v1\/commits\/[^/]+\/actual$/, async (route) => {
    if (route.request().method() !== 'PATCH') {
      return route.fallback();
    }

    const path = new URL(route.request().url()).pathname;
    const commitId = path.split('/')[4] ?? '';
    const body = ((route.request().postDataJSON() as MockEntity | null) ?? {}) as MockEntity;
    const actual = {
      commitId,
      actualResult: typeof body.actualResult === 'string' ? body.actualResult : '',
      completionStatus: typeof body.completionStatus === 'string' ? body.completionStatus : 'DONE',
      deltaReason: typeof body.deltaReason === 'string' ? body.deltaReason : null,
      timeSpent: typeof body.timeSpent === 'number' ? body.timeSpent : null,
    };

    state.actualUpdates.push(actual);
    state.commits = state.commits.map((commit) =>
      commit.id === commitId
        ? {
            ...commit,
            actual,
            version: Number(commit.version ?? 1) + 1,
            updatedAt: MOCK_NOW,
          }
        : commit,
    );

    return json(route, 200, actual);
  });

  await page.route(new RegExp(`/api/v1/plans/${MOCK_PLAN_ID}/submit-reconciliation$`), async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }

    return json(
      route,
      200,
      bumpPlan(state, {
        state: 'RECONCILED',
        reviewStatus: 'REVIEW_PENDING',
      }),
    );
  });

  await page.route(new RegExp(`/api/v1/plans/${MOCK_PLAN_ID}/carry-forward$`), async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }

    const body = ((route.request().postDataJSON() as { commitIds?: unknown } | null) ?? {}) as {
      commitIds?: unknown;
    };

    state.carryForwardCommitIds = Array.isArray(body.commitIds)
      ? body.commitIds.filter((id): id is string => typeof id === 'string')
      : [];

    return json(
      route,
      200,
      bumpPlan(state, {
        state: 'CARRY_FORWARD',
        carryForwardExecutedAt: MOCK_NOW,
      }),
    );
  });

  return state;
}

async function openWeeklyPlan(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('weekly-plan-page')).toBeVisible();
}

async function confirmDialog(page: Page): Promise<void> {
  await expect(page.getByTestId('confirm-dialog')).toBeVisible();
  await page.getByTestId('confirm-dialog-confirm').click();
}

async function acceptNextWork(page: Page, state: LifecycleState): Promise<string> {
  const beforeCreates = state.createdCommitIds.length;

  await page.getByTestId(`next-work-accept-${NEXT_WORK_SUGGESTION_ID}`).click();

  await expect.poll(() => state.createdCommitIds.length).toBe(beforeCreates + 1);
  const createdCommitId = state.createdCommitIds.at(-1);
  expect(createdCommitId).toBeTruthy();

  await expect(page.getByTestId(`next-work-suggestion-${NEXT_WORK_SUGGESTION_ID}`)).toHaveCount(0);
  await expect(page.getByTestId(`commit-row-${createdCommitId}`)).toBeVisible();

  return createdCommitId as string;
}

async function addManualCommitWithAiSuggestion(
  page: Page,
  state: LifecycleState,
  title = MANUAL_COMMIT_TITLE,
): Promise<string> {
  const beforeCreates = state.createdCommitIds.length;

  await page.getByTestId('add-commit-btn').click();
  await expect(page.getByTestId('commit-editor-new')).toBeVisible();

  await page.getByTestId('commit-title').fill(title);
  await expect(page.getByTestId('ai-suggestion-panel')).toBeVisible({ timeout: 2500 });
  await expect(page.getByTestId('ai-suggestion-panel')).toContainText('Increase trial-to-paid by 20%');

  await page.getByTestId('ai-suggestion-0').click();
  await expect(page.getByTestId('rcdo-current')).toContainText('Increase trial-to-paid by 20%');

  await page.getByTestId('commit-save').click();

  await expect.poll(() => state.createdCommitIds.length).toBe(beforeCreates + 1);
  const createdCommitId = state.createdCommitIds.at(-1);
  expect(createdCommitId).toBeTruthy();

  await expect(page.getByTestId(`commit-row-${createdCommitId}`)).toBeVisible();
  return createdCommitId as string;
}

async function applyAiDraft(page: Page, state: LifecycleState): Promise<void> {
  await expect(page.getByTestId('ai-reconciliation-draft')).toBeVisible();
  await page.getByTestId('ai-draft-fetch').click();
  await expect(page.getByTestId('ai-draft-item-0')).toContainText(AI_DRAFT_ACTUAL);

  await page.getByTestId('ai-draft-apply-0').click();
  await expect.poll(() => state.actualUpdates.some((actual) => actual.commitId === CARRY_FORWARD_COMMIT_ID)).toBe(true);
  await expect(page.getByTestId(`reconcile-actual-${CARRY_FORWARD_COMMIT_ID}`)).toHaveValue(AI_DRAFT_ACTUAL);
}

async function saveActual(page: Page, state: LifecycleState, commitId: string, actualResult: string): Promise<void> {
  await page.getByTestId(`reconcile-actual-${commitId}`).fill(actualResult);
  await page.getByTestId(`reconcile-save-${commitId}`).click();

  await expect
    .poll(() =>
      state.actualUpdates.some(
        (actual) => actual.commitId === commitId && actual.actualResult === actualResult,
      ),
    )
    .toBe(true);
}

test.describe('Full IC lifecycle with all AI features', () => {
  test('[FULL] Start from Last Week hydrates a DRAFT plan with carried work', async ({ page }) => {
    await installLifecycleMocks(page, { initialPlan: null, commits: [] });
    await openWeeklyPlan(page);

    await expect(page.getByTestId('no-plan')).toBeVisible();
    await page.getByTestId('start-from-last-week-btn').click();

    await expect(page.getByTestId('plan-header')).toBeVisible();
    await expect(page.getByTestId('plan-state')).toContainText('Draft');
    await expect(page.getByTestId(`commit-row-${CARRY_FORWARD_COMMIT_ID}`)).toBeVisible();
    await expect(page.getByTestId('next-work-suggestion-panel')).toBeVisible();
  });

  test('[FULL] Accepting next-work creates a commit and dismisses the suggestion', async ({ page }) => {
    const state = await installLifecycleMocks(page, {
      initialPlan: buildDraftPlan(),
      commits: [buildCarryForwardCommit()],
    });
    await openWeeklyPlan(page);

    const acceptedCommitId = await acceptNextWork(page, state);

    expect(state.createdCommitBodies[0]).toMatchObject({
      title: NEXT_WORK_TITLE,
      outcomeId: MOCK_OUTCOME_ID,
      chessPriority: 'ROOK',
      tags: ['draft_source:COVERAGE_GAP'],
    });
    expect(state.suggestionFeedbacks[0]).toMatchObject({
      suggestionId: NEXT_WORK_SUGGESTION_ID,
      action: 'ACCEPT',
    });
    await expect(page.getByTestId(`commit-row-${acceptedCommitId}`)).toContainText(NEXT_WORK_TITLE);
    await expect(page.getByTestId('next-work-empty')).toBeVisible();
  });

  test('[FULL] Manual commit creation can use the AI RCDO suggestion', async ({ page }) => {
    const state = await installLifecycleMocks(page, {
      initialPlan: buildDraftPlan(),
      commits: [buildCarryForwardCommit()],
    });
    await openWeeklyPlan(page);

    const manualCommitId = await addManualCommitWithAiSuggestion(page, state);

    expect(state.createdCommitBodies[0]).toMatchObject({
      title: MANUAL_COMMIT_TITLE,
      outcomeId: MOCK_OUTCOME_ID,
    });
    await expect(page.getByTestId(`commit-row-${manualCommitId}`)).toContainText(MANUAL_COMMIT_TITLE);
  });

  test('[FULL] Plan quality nudge blocks lock until the user chooses Lock Anyway', async ({ page }) => {
    await installLifecycleMocks(page, {
      initialPlan: buildDraftPlan(),
      commits: [buildCarryForwardCommit()],
    });
    await openWeeklyPlan(page);

    await page.getByTestId('lock-btn').click();
    await expect(page.getByTestId('plan-quality-nudge-overlay')).toBeVisible();
    await expect(page.getByTestId('plan-quality-nudge-item-0')).toContainText('strategic outcome');

    await page.getByTestId('plan-quality-nudge-lock-anyway').click();
    await confirmDialog(page);

    await expect(page.getByTestId('plan-state')).toContainText('Locked');
  });

  test('[FULL] Reconciliation AI draft can prefill actuals for a reconciling plan', async ({ page }) => {
    const state = await installLifecycleMocks(page, {
      initialPlan: buildReconcilingPlan(),
      commits: [buildCarryForwardCommit()],
    });
    await openWeeklyPlan(page);

    await expect(page.getByTestId('reconciliation-view')).toBeVisible();
    await applyAiDraft(page, state);

    expect(state.actualUpdates[0]).toMatchObject({
      commitId: CARRY_FORWARD_COMMIT_ID,
      completionStatus: 'DONE',
      actualResult: AI_DRAFT_ACTUAL,
    });
  });

  test('[SMOKE] Complete AI lifecycle works end-to-end with all AI features enabled', async ({ page }) => {
    const state = await installLifecycleMocks(page, { initialPlan: null, commits: [] });
    await openWeeklyPlan(page);

    // 1. Start from Last Week
    await expect(page.getByTestId('no-plan')).toBeVisible();
    await page.getByTestId('start-from-last-week-btn').click();
    await expect(page.getByTestId('plan-state')).toContainText('Draft');
    await expect(page.getByTestId(`commit-row-${CARRY_FORWARD_COMMIT_ID}`)).toBeVisible();

    // 2. Accept AI next-work suggestion
    const acceptedCommitId = await acceptNextWork(page, state);
    expect(state.createdCommitBodies[0]).toMatchObject({
      title: NEXT_WORK_TITLE,
      outcomeId: MOCK_OUTCOME_ID,
      chessPriority: 'ROOK',
      tags: ['draft_source:COVERAGE_GAP'],
    });

    // 3. Manually add a commit with AI RCDO suggestions
    const manualCommitId = await addManualCommitWithAiSuggestion(page, state);
    expect(state.createdCommitBodies[1]).toMatchObject({
      title: MANUAL_COMMIT_TITLE,
      outcomeId: MOCK_OUTCOME_ID,
    });

    // 4. Lock with quality nudge override
    await page.getByTestId('lock-btn').click();
    await expect(page.getByTestId('plan-quality-nudge-overlay')).toBeVisible();
    await page.getByTestId('plan-quality-nudge-lock-anyway').click();
    await confirmDialog(page);
    await expect(page.getByTestId('plan-state')).toContainText('Locked');

    // 5. Start reconciliation and apply AI draft to one item
    await page.getByTestId('start-reconciliation-btn').click();
    await expect(page.getByTestId('reconciliation-view')).toBeVisible();
    await applyAiDraft(page, state);

    // 6. Fill remaining actuals manually and submit reconciliation
    await saveActual(page, state, acceptedCommitId, 'Closed the coverage gap with a focused follow-up commit.');
    await saveActual(page, state, manualCommitId, 'Improved API response times for key endpoints.');
    await expect(page.getByTestId('reconcile-submit')).toBeEnabled();
    await page.getByTestId('reconcile-submit').click();
    await confirmDialog(page);
    await expect(page.getByTestId('plan-state')).toContainText('Reconciled');

    // 7. Carry-forward
    await page.getByTestId('carry-forward-btn').click();
    await expect(page.getByTestId('carry-forward-dialog')).toBeVisible();
    await expect(page.getByTestId('carry-confirm')).toContainText('Carry Forward (3)');
    await page.getByTestId('carry-confirm').click();

    await expect.poll(() => [...state.carryForwardCommitIds].sort()).toEqual(
      [CARRY_FORWARD_COMMIT_ID, acceptedCommitId, manualCommitId].sort(),
    );
    await expect(page.getByTestId('carry-forward-dialog')).not.toBeVisible();
    await expect(page.getByTestId('plan-state')).toContainText('Carry Forward');
  });
});
