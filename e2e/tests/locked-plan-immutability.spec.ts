/**
 * API-level tests for locked plan immutability (PRD §18 #4).
 *
 * Covers every immutability rule specified in §18 #4:
 *   - PATCH title on locked commit             → 409 FIELD_FROZEN
 *   - PATCH chessPriority on locked commit     → 409 FIELD_FROZEN
 *   - PATCH category on locked commit          → 409 FIELD_FROZEN
 *   - PATCH outcomeId on locked commit         → 409 FIELD_FROZEN
 *   - PATCH description on locked commit       → 409 FIELD_FROZEN
 *   - PATCH progressNotes on LOCKED commit     → 200 (single mutable field)
 *   - PATCH progressNotes on RECONCILING commit→ 200
 *   - Multiple progressNotes updates in LOCKED → 200 each
 *   - Add new commit to LOCKED plan            → 409 PLAN_NOT_IN_DRAFT
 *   - Delete commit from LOCKED plan           → 409 PLAN_NOT_IN_DRAFT
 *   - Add new commit to RECONCILING plan       → 409 PLAN_NOT_IN_DRAFT
 *   - PATCH progressNotes on RECONCILED commit → 409 FIELD_FROZEN
 *   - PATCH title on RECONCILED commit         → 409 FIELD_FROZEN
 *   - Delete commit from RECONCILED plan       → 409 PLAN_NOT_IN_DRAFT
 *
 * Prerequisite: Backend running on localhost:8080 (./scripts/dev.sh --seed)
 *
 * Run:
 *   cd e2e && npx playwright test tests/locked-plan-immutability.spec.ts --project=api
 */
import { expect, test } from '@playwright/test';
import {
  OUTCOME_API_UPTIME,
  OUTCOME_ENTERPRISE_DEALS,
  api,
  createCommit,
  createPlan,
  errorOf,
  freshUserId,
  getCommits,
  getPlan,
  lockPlan,
  mondayOf,
  refreshRcdo,
  startReconciliation,
  submitReconciliation,
  tokenFor,
  updateActual,
} from './helpers';

const CURRENT_WEEK = mondayOf(0);

async function getCommit(planId: string, commitId: string, token: string): Promise<Record<string, unknown>> {
  const res = await getCommits(planId, token);
  expect(res.status).toBe(200);
  const commit = res.body.find((candidate) => candidate.id === commitId);
  expect(commit).toBeTruthy();
  return commit!;
}

// ── Plan state setup helpers ───────────────────────────────────────────────

interface PlanContext {
  planId: string;
  commitId: string;
  planVersion: number;
  commitVersion: number;
}

/**
 * Creates a plan with exactly 1 KING commit and locks it.
 * Returns planId, commitId, the post-lock plan version, and commit version.
 */
async function setupLockedPlan(token: string): Promise<PlanContext> {
  const planRes = await createPlan(CURRENT_WEEK, token);
  expect(planRes.status).toBe(201);
  const planId = planRes.body.id as string;

  const commitRes = await createCommit(planId, {
    title: 'Immutability test commit',
    chessPriority: 'KING',
    category: 'DELIVERY',
    outcomeId: OUTCOME_ENTERPRISE_DEALS,
    description: 'Initial description for immutability testing',
    expectedResult: 'Validated immutability rules',
  }, token);
  expect(commitRes.status).toBe(201);
  const commitId = commitRes.body.id as string;

  await refreshRcdo(token);

  const planBeforeLock = await getPlan(planId, token);
  const lockRes = await lockPlan(planId, planBeforeLock.body.version as number, token);
  expect(lockRes.status).toBe(200);
  expect(lockRes.body.state).toBe('LOCKED');

  // Re-fetch commit version post-lock (snapshot fields populate at lock time)
  const commits = await getCommits(planId, token);
  const commit = commits.body.find((c) => c.id === commitId)!;
  expect(commit).toBeTruthy();

  return {
    planId,
    commitId,
    planVersion: lockRes.body.version as number,
    commitVersion: commit.version as number,
  };
}

/**
 * Creates a plan, locks it, then transitions to RECONCILING.
 * Returns planId, commitId, the post-start-reconciliation plan version,
 * and the current commit version.
 */
async function setupReconcilingPlan(token: string): Promise<PlanContext> {
  const { planId, commitId } = await setupLockedPlan(token);

  const planAfterLock = await getPlan(planId, token);
  const reconRes = await startReconciliation(planId, planAfterLock.body.version as number, token);
  expect(reconRes.status).toBe(200);
  expect(reconRes.body.state).toBe('RECONCILING');

  // Re-fetch commit version (commits are unchanged by start-reconciliation,
  // but fetching ensures we have the authoritative version)
  const commits = await getCommits(planId, token);
  const commit = commits.body.find((c) => c.id === commitId)!;
  expect(commit).toBeTruthy();

  return {
    planId,
    commitId,
    planVersion: reconRes.body.version as number,
    commitVersion: commit.version as number,
  };
}

/**
 * Creates a plan, locks it, reconciles, fills actuals, and submits.
 * Returns planId, commitId, the post-submit plan version, and commit version.
 */
async function setupReconciledPlan(token: string): Promise<PlanContext> {
  const { planId, commitId, commitVersion: commitVersionInReconciling } = await setupReconcilingPlan(token);

  // Fill in actuals so submitReconciliation is allowed
  const actualRes = await updateActual(commitId, commitVersionInReconciling, {
    actualResult: 'Delivered all objectives for the test week.',
    completionStatus: 'DONE',
    timeSpent: 40,
  }, token);
  expect(actualRes.status).toBe(200);

  const planBeforeSubmit = await getPlan(planId, token);
  const submitRes = await submitReconciliation(planId, planBeforeSubmit.body.version as number, token);
  expect(submitRes.status).toBe(200);
  expect(submitRes.body.state).toBe('RECONCILED');

  // Re-fetch commit version after submit
  const commits = await getCommits(planId, token);
  const commit = commits.body.find((c) => c.id === commitId)!;
  expect(commit).toBeTruthy();

  return {
    planId,
    commitId,
    planVersion: submitRes.body.version as number,
    commitVersion: commit.version as number,
  };
}

// ═══════════════════════════════════════════════════════════════════════════
// §18 #4 — Planning fields are frozen after lock
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Locked Plan — Frozen Planning Fields (§18 #4)', () => {

  test('[FULL] PATCH title on locked commit → 409 FIELD_FROZEN', async () => {
    const token = tokenFor(freshUserId());
    const { commitId, commitVersion } = await setupLockedPlan(token);

    const res = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { title: 'Updated title — should be rejected after lock' },
    });

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('FIELD_FROZEN');
  });

  test('[FULL] PATCH chessPriority on locked commit → 409 FIELD_FROZEN', async () => {
    const token = tokenFor(freshUserId());
    const { commitId, commitVersion } = await setupLockedPlan(token);

    const res = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { chessPriority: 'QUEEN' },
    });

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('FIELD_FROZEN');
  });

  test('[FULL] PATCH category on locked commit → 409 FIELD_FROZEN', async () => {
    const token = tokenFor(freshUserId());
    const { commitId, commitVersion } = await setupLockedPlan(token);

    const res = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { category: 'TECH_DEBT' },
    });

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('FIELD_FROZEN');
  });

  test('[FULL] PATCH outcomeId on locked commit → 409 FIELD_FROZEN', async () => {
    const token = tokenFor(freshUserId());
    const { commitId, commitVersion } = await setupLockedPlan(token);

    const res = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { outcomeId: OUTCOME_API_UPTIME },
    });

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('FIELD_FROZEN');
  });

  test('[FULL] PATCH description on locked commit → 409 FIELD_FROZEN', async () => {
    const token = tokenFor(freshUserId());
    const { commitId, commitVersion } = await setupLockedPlan(token);

    const res = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { description: 'Updated description — should be rejected after lock' },
    });

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('FIELD_FROZEN');
  });

  test('[FULL] Error details reference the plan state when FIELD_FROZEN is returned', async () => {
    const token = tokenFor(freshUserId());
    const { commitId, commitVersion } = await setupLockedPlan(token);

    const res = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { title: 'Rejected update' },
    });

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('FIELD_FROZEN');

    // The error details should include the plan state that caused the freeze
    const details = (error.details as Array<Record<string, unknown>>) ?? [];
    const hasPlanState = details.some(
      (d) => d.planState === 'LOCKED' || d.planState === 'RECONCILING' || d.planState === 'RECONCILED',
    );
    expect(hasPlanState).toBe(true);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #4 — progressNotes is the single mutable field on locked/reconciling plans
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Locked Plan — progressNotes is Mutable (§18 #4)', () => {

  test('[FULL] PATCH progressNotes on LOCKED commit → 200', async () => {
    const token = tokenFor(freshUserId());
    const { planId, commitId, commitVersion } = await setupLockedPlan(token);

    const newNotes = 'Mid-week status: contract review in progress, 60% complete.';
    const res = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { progressNotes: newNotes },
    });

    expect(res.status).toBe(200);
    expect(res.body.progressNotes).toBe(newNotes);

    const updatedCommit = await getCommit(planId, commitId, token);
    expect(updatedCommit.progressNotes).toBe(newNotes);
  });

  test('[FULL] PATCH progressNotes on RECONCILING commit → 200', async () => {
    const token = tokenFor(freshUserId());
    const { planId, commitId, commitVersion } = await setupReconcilingPlan(token);

    const newNotes = 'Reconciliation in progress — 80% done, blocked on external sign-off.';
    const res = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { progressNotes: newNotes },
    });

    expect(res.status).toBe(200);
    expect(res.body.progressNotes).toBe(newNotes);

    const updatedCommit = await getCommit(planId, commitId, token);
    expect(updatedCommit.progressNotes).toBe(newNotes);
  });

  test('[FULL] progressNotes can be updated multiple times on a LOCKED commit', async () => {
    const token = tokenFor(freshUserId());
    const { planId, commitId, commitVersion } = await setupLockedPlan(token);

    // First update
    const firstUpdate = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { progressNotes: 'First mid-week status update.' },
    });
    expect(firstUpdate.status).toBe(200);
    expect(firstUpdate.body.progressNotes).toBe('First mid-week status update.');

    const commitAfterFirstUpdate = await getCommit(planId, commitId, token);
    expect(commitAfterFirstUpdate.progressNotes).toBe('First mid-week status update.');

    // Second update using the current persisted version
    const secondUpdate = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitAfterFirstUpdate.version as number,
      body: { progressNotes: 'Second mid-week status update — 90% done.' },
    });
    expect(secondUpdate.status).toBe(200);
    expect(secondUpdate.body.progressNotes).toBe('Second mid-week status update — 90% done.');

    const commitAfterSecondUpdate = await getCommit(planId, commitId, token);
    expect(commitAfterSecondUpdate.progressNotes).toBe('Second mid-week status update — 90% done.');
  });

  test('[FULL] PATCH progressNotes does not unfreeze other fields on LOCKED commit', async () => {
    const token = tokenFor(freshUserId());
    const { planId, commitId, commitVersion } = await setupLockedPlan(token);

    // Update progressNotes (succeeds)
    const notesUpdate = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { progressNotes: 'Valid progress note.' },
    });
    expect(notesUpdate.status).toBe(200);

    const updatedCommit = await getCommit(planId, commitId, token);
    expect(updatedCommit.progressNotes).toBe('Valid progress note.');

    // Using the fresh persisted version, planning fields must still remain frozen
    const titleUpdate = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: updatedCommit.version as number,
      body: { title: 'Title update should still be frozen' },
    });
    expect(titleUpdate.status).toBe(409);
    const error = errorOf(titleUpdate);
    expect(error.code).toBe('FIELD_FROZEN');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #4 — Adding and deleting commits is blocked after lock
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Locked Plan — Add/Delete Commits Blocked (§18 #4)', () => {

  test('[FULL] Add new commit to LOCKED plan → 409 PLAN_NOT_IN_DRAFT', async () => {
    const token = tokenFor(freshUserId());
    const { planId } = await setupLockedPlan(token);

    const res = await createCommit(planId, {
      title: 'New commit on locked plan — should be rejected',
      chessPriority: 'QUEEN',
      category: 'DELIVERY',
      outcomeId: OUTCOME_API_UPTIME,
    }, token);

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('PLAN_NOT_IN_DRAFT');
  });

  test('[FULL] Delete commit from LOCKED plan → 409 PLAN_NOT_IN_DRAFT', async () => {
    const token = tokenFor(freshUserId());
    const { commitId } = await setupLockedPlan(token);

    const res = await api('DELETE', `/api/v1/commits/${commitId}`, { token });

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('PLAN_NOT_IN_DRAFT');
  });

  test('[FULL] Add new commit to RECONCILING plan → 409 PLAN_NOT_IN_DRAFT', async () => {
    const token = tokenFor(freshUserId());
    const { planId } = await setupReconcilingPlan(token);

    const res = await createCommit(planId, {
      title: 'New commit on reconciling plan — should be rejected',
      chessPriority: 'QUEEN',
      category: 'TECH_DEBT',
      outcomeId: OUTCOME_API_UPTIME,
    }, token);

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('PLAN_NOT_IN_DRAFT');
  });

  test('[FULL] Delete commit from RECONCILING plan → 409 PLAN_NOT_IN_DRAFT', async () => {
    const token = tokenFor(freshUserId());
    const { commitId } = await setupReconcilingPlan(token);

    const res = await api('DELETE', `/api/v1/commits/${commitId}`, { token });

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('PLAN_NOT_IN_DRAFT');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #4 — progressNotes becomes frozen once the plan reaches RECONCILED
// Per PRD §5 state table: RECONCILED → Edit progressNotes = ❌
// ═══════════════════════════════════════════════════════════════════════════

test.describe('RECONCILED State — All Commit Fields Frozen (§18 #4)', () => {

  test('[FULL] PATCH progressNotes on RECONCILED commit → 409 FIELD_FROZEN', async () => {
    const token = tokenFor(freshUserId());
    const { commitId, commitVersion } = await setupReconciledPlan(token);

    const res = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { progressNotes: 'Update in RECONCILED state — should be rejected.' },
    });

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('FIELD_FROZEN');
  });

  test('[FULL] PATCH title on RECONCILED commit → 409 FIELD_FROZEN', async () => {
    const token = tokenFor(freshUserId());
    const { commitId, commitVersion } = await setupReconciledPlan(token);

    const res = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { title: 'Title update in RECONCILED state — rejected' },
    });

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('FIELD_FROZEN');
  });

  test('[FULL] Delete commit from RECONCILED plan → 409 PLAN_NOT_IN_DRAFT', async () => {
    const token = tokenFor(freshUserId());
    const { commitId } = await setupReconciledPlan(token);

    const res = await api('DELETE', `/api/v1/commits/${commitId}`, { token });

    expect(res.status).toBe(409);
    const error = errorOf(res);
    expect(error.code).toBe('PLAN_NOT_IN_DRAFT');
  });
});
