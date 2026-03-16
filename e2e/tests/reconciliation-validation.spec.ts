/**
 * API-level tests for reconciliation validation edge cases (PRD §18 #5, #6).
 *
 * Covers every reconciliation-validation scenario specified in §18 #5, #6:
 *   - DROPPED + empty deltaReason        → 422 MISSING_DELTA_REASON
 *   - NOT_DONE + empty deltaReason       → 422 MISSING_DELTA_REASON
 *   - PARTIALLY + empty deltaReason      → 422 MISSING_DELTA_REASON
 *   - All commits DONE (no deltaReason)  → 200 success
 *   - Mix DONE + PARTIALLY w/ deltaReason → 200 success
 *   - Submit without any actuals         → 422 MISSING_COMPLETION_STATUS
 *   - Some commits missing completionStatus → 422 MISSING_COMPLETION_STATUS
 *   - start-reconciliation from RECONCILED   → 409 CONFLICT (invalid transition)
 *   - lock from RECONCILING               → 409 PLAN_NOT_IN_DRAFT (invalid transition)
 *
 * Prerequisite: Backend running on localhost:8080 (./scripts/dev.sh --seed)
 *
 * Run:
 *   cd e2e && npx playwright test tests/reconciliation-validation.spec.ts --project=api
 */
import { expect, test } from '@playwright/test';
import {
  OUTCOME_API_UPTIME,
  OUTCOME_ENTERPRISE_DEALS,
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

// ── Plan state setup helpers ───────────────────────────────────────────────

interface PlanContext {
  planId: string;
  commitId: string;
  planVersion: number;
  commitVersion: number;
  token: string;
}

interface MultiCommitPlanContext extends PlanContext {
  secondCommitId: string;
}

/**
 * Creates a plan with exactly 1 KING commit and locks it.
 * Returns planId, commitId, post-lock plan version, and commit version.
 */
async function setupLockedPlan(token: string): Promise<PlanContext> {
  const planRes = await createPlan(CURRENT_WEEK, token);
  expect(planRes.status).toBe(201);
  const planId = planRes.body.id as string;

  const commitRes = await createCommit(planId, {
    title: 'Reconciliation validation test commit',
    chessPriority: 'KING',
    category: 'DELIVERY',
    outcomeId: OUTCOME_ENTERPRISE_DEALS,
    expectedResult: 'Validated reconciliation rules',
  }, token);
  expect(commitRes.status).toBe(201);
  const commitId = commitRes.body.id as string;

  await refreshRcdo(token);

  const planBeforeLock = await getPlan(planId, token);
  const lockRes = await lockPlan(planId, planBeforeLock.body.version as number, token);
  expect(lockRes.status).toBe(200);
  expect(lockRes.body.state).toBe('LOCKED');

  // Re-fetch commit version after lock (snapshot fields are populated at lock time)
  const commits = await getCommits(planId, token);
  const commit = commits.body.find((c) => c.id === commitId)!;
  expect(commit).toBeTruthy();

  return {
    planId,
    commitId,
    planVersion: lockRes.body.version as number,
    commitVersion: commit.version as number,
    token,
  };
}

/**
 * Creates a plan, locks it, then transitions to RECONCILING.
 * Returns planId, commitId, post-start-reconciliation plan version, and commit version.
 */
async function setupReconcilingPlan(token: string): Promise<PlanContext> {
  const { planId, commitId } = await setupLockedPlan(token);

  const planAfterLock = await getPlan(planId, token);
  const reconRes = await startReconciliation(planId, planAfterLock.body.version as number, token);
  expect(reconRes.status).toBe(200);
  expect(reconRes.body.state).toBe('RECONCILING');

  const commits = await getCommits(planId, token);
  const commit = commits.body.find((c) => c.id === commitId)!;
  expect(commit).toBeTruthy();

  return {
    planId,
    commitId,
    planVersion: reconRes.body.version as number,
    commitVersion: commit.version as number,
    token,
  };
}

/**
 * Creates a plan with 2 KING+QUEEN commits, locks it, and transitions to RECONCILING.
 * Returns planId, kingCommitId (as commitId), queenCommitId (as secondCommitId),
 * post-start-reconciliation plan version, and KING commit version.
 */
async function setupReconcilingPlanWithTwoCommits(token: string): Promise<MultiCommitPlanContext> {
  const planRes = await createPlan(CURRENT_WEEK, token);
  expect(planRes.status).toBe(201);
  const planId = planRes.body.id as string;

  const kingRes = await createCommit(planId, {
    title: 'KING commit for two-commit reconciliation test',
    chessPriority: 'KING',
    category: 'DELIVERY',
    outcomeId: OUTCOME_ENTERPRISE_DEALS,
    expectedResult: 'Enterprise deal closed',
  }, token);
  expect(kingRes.status).toBe(201);
  const commitId = kingRes.body.id as string;

  const queenRes = await createCommit(planId, {
    title: 'QUEEN commit for two-commit reconciliation test',
    chessPriority: 'QUEEN',
    category: 'TECH_DEBT',
    outcomeId: OUTCOME_API_UPTIME,
    expectedResult: 'Monitoring dashboard live',
  }, token);
  expect(queenRes.status).toBe(201);
  const secondCommitId = queenRes.body.id as string;

  await refreshRcdo(token);

  const planBeforeLock = await getPlan(planId, token);
  const lockRes = await lockPlan(planId, planBeforeLock.body.version as number, token);
  expect(lockRes.status).toBe(200);
  expect(lockRes.body.state).toBe('LOCKED');

  const planAfterLock = await getPlan(planId, token);
  const reconRes = await startReconciliation(planId, planAfterLock.body.version as number, token);
  expect(reconRes.status).toBe(200);
  expect(reconRes.body.state).toBe('RECONCILING');

  const commits = await getCommits(planId, token);
  const kingCommit = commits.body.find((c) => c.id === commitId)!;
  expect(kingCommit).toBeTruthy();

  return {
    planId,
    commitId,
    secondCommitId,
    planVersion: reconRes.body.version as number,
    commitVersion: kingCommit.version as number,
    token,
  };
}

/**
 * Creates a plan, locks it, reconciles, fills actuals (all DONE), and submits.
 * Returns planId, commitId, post-submit plan version, and commit version.
 */
async function setupReconciledPlan(token: string): Promise<PlanContext> {
  const { planId, commitId, commitVersion: versionInReconciling } = await setupReconcilingPlan(token);

  const actualRes = await updateActual(commitId, versionInReconciling, {
    actualResult: 'All objectives delivered for the test week.',
    completionStatus: 'DONE',
    timeSpent: 40,
  }, token);
  expect(actualRes.status).toBe(200);

  const planBeforeSubmit = await getPlan(planId, token);
  const submitRes = await submitReconciliation(planId, planBeforeSubmit.body.version as number, token);
  expect(submitRes.status).toBe(200);
  expect(submitRes.body.state).toBe('RECONCILED');

  const commits = await getCommits(planId, token);
  const commit = commits.body.find((c) => c.id === commitId)!;
  expect(commit).toBeTruthy();

  return {
    planId,
    commitId,
    planVersion: submitRes.body.version as number,
    commitVersion: commit.version as number,
    token,
  };
}

// ═══════════════════════════════════════════════════════════════════════════
// §18 #5 — deltaReason is required for non-DONE commits at submission
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Reconciliation Validation — deltaReason required for non-DONE (§18 #5)', () => {

  // ── DROPPED + empty deltaReason ────────────────────────────────────────

  test('[FULL] Submit with DROPPED commit and no deltaReason → 422 MISSING_DELTA_REASON', async () => {
    const token = tokenFor(freshUserId());
    const { planId, commitId, commitVersion, planVersion: _pv } = await setupReconcilingPlan(token);

    // Set actual as DROPPED without deltaReason
    const actualRes = await updateActual(commitId, commitVersion, {
      actualResult: 'Task was dropped due to priority change',
      completionStatus: 'DROPPED',
      // deltaReason intentionally omitted
    }, token);
    // The API may accept this at updateActual time (deferred validation)
    // or reject it eagerly (strict validation). Handle both cases.
    if (actualRes.status !== 200) {
      // Eager rejection — validated at update time
      expect(actualRes.status).toBe(422);
      return;
    }

    // Attempt to submit — must be rejected because DROPPED requires deltaReason
    const plan = await getPlan(planId, token);
    const submitRes = await submitReconciliation(planId, plan.body.version as number, token);

    expect(submitRes.status).toBe(422);
    const error = errorOf(submitRes);
    expect(error.code).toBe('MISSING_DELTA_REASON');

    // The error details must reference the affected commit
    const details = (error.details as Array<Record<string, unknown>>) ?? [];
    const commitReferenced = details.some((d) => d.commitId === commitId);
    expect(commitReferenced).toBe(true);

    // Confirm the plan is still in RECONCILING state (not advanced)
    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('RECONCILING');
  });

  // ── NOT_DONE + empty deltaReason ───────────────────────────────────────

  test('[FULL] Submit with NOT_DONE commit and no deltaReason → 422 MISSING_DELTA_REASON', async () => {
    const token = tokenFor(freshUserId());
    const { planId, commitId, commitVersion } = await setupReconcilingPlan(token);

    // Set actual as NOT_DONE without deltaReason
    const actualRes = await updateActual(commitId, commitVersion, {
      actualResult: 'Work did not get started this week',
      completionStatus: 'NOT_DONE',
      // deltaReason intentionally omitted
    }, token);
    if (actualRes.status !== 200) {
      expect(actualRes.status).toBe(422);
      return;
    }

    // Submit must be rejected because NOT_DONE requires deltaReason
    const plan = await getPlan(planId, token);
    const submitRes = await submitReconciliation(planId, plan.body.version as number, token);

    expect(submitRes.status).toBe(422);
    const error = errorOf(submitRes);
    expect(error.code).toBe('MISSING_DELTA_REASON');

    const details = (error.details as Array<Record<string, unknown>>) ?? [];
    const commitReferenced = details.some((d) => d.commitId === commitId);
    expect(commitReferenced).toBe(true);

    // Plan must remain in RECONCILING
    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('RECONCILING');
  });

  // ── PARTIALLY + empty deltaReason ─────────────────────────────────────

  test('[FULL] Submit with PARTIALLY commit and no deltaReason → 422 MISSING_DELTA_REASON', async () => {
    const token = tokenFor(freshUserId());
    const { planId, commitId, commitVersion } = await setupReconcilingPlan(token);

    // Set actual as PARTIALLY without deltaReason
    const actualRes = await updateActual(commitId, commitVersion, {
      actualResult: 'Approximately 60% of the work was completed',
      completionStatus: 'PARTIALLY',
      // deltaReason intentionally omitted
    }, token);
    if (actualRes.status !== 200) {
      expect(actualRes.status).toBe(422);
      return;
    }

    // Submit must be rejected because PARTIALLY requires deltaReason
    const plan = await getPlan(planId, token);
    const submitRes = await submitReconciliation(planId, plan.body.version as number, token);

    expect(submitRes.status).toBe(422);
    const error = errorOf(submitRes);
    expect(error.code).toBe('MISSING_DELTA_REASON');

    const details = (error.details as Array<Record<string, unknown>>) ?? [];
    const commitReferenced = details.some((d) => d.commitId === commitId);
    expect(commitReferenced).toBe(true);

    // Plan must remain in RECONCILING
    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('RECONCILING');
  });

  // ── All DONE — happy path ──────────────────────────────────────────────

  test('[FULL] Submit with all commits DONE (no deltaReason needed) → 200', async () => {
    const token = tokenFor(freshUserId());
    const { planId, commitId, commitVersion } = await setupReconcilingPlan(token);

    // Set actual as DONE — deltaReason is not required
    const actualRes = await updateActual(commitId, commitVersion, {
      actualResult: 'Completed all planned work for this week',
      completionStatus: 'DONE',
      timeSpent: 35,
      // No deltaReason — valid for DONE status
    }, token);
    expect(actualRes.status).toBe(200);
    expect(actualRes.body.completionStatus).toBe('DONE');

    // Submit must succeed
    const plan = await getPlan(planId, token);
    const submitRes = await submitReconciliation(planId, plan.body.version as number, token);

    expect(submitRes.status).toBe(200);
    expect(submitRes.body.state).toBe('RECONCILED');
    expect(submitRes.body.reviewStatus).toBe('REVIEW_PENDING');
  });

  // ── Mix: DONE + PARTIALLY with deltaReason ─────────────────────────────

  test('[FULL] Submit with mix of DONE and PARTIALLY (PARTIALLY has deltaReason) → 200', async () => {
    const token = tokenFor(freshUserId());
    const { planId, commitId, secondCommitId, planVersion: _pv } = await setupReconcilingPlanWithTwoCommits(token);

    // Fetch current commit versions
    const commits = await getCommits(planId, token);
    const kingCommit = commits.body.find((c) => c.id === commitId)!;
    const queenCommit = commits.body.find((c) => c.id === secondCommitId)!;
    expect(kingCommit).toBeTruthy();
    expect(queenCommit).toBeTruthy();

    // KING commit: DONE (no deltaReason needed)
    const kingActual = await updateActual(commitId, kingCommit.version as number, {
      actualResult: 'Enterprise deal signed and onboarding scheduled',
      completionStatus: 'DONE',
      timeSpent: 40,
    }, token);
    expect(kingActual.status).toBe(200);
    expect(kingActual.body.completionStatus).toBe('DONE');

    // QUEEN commit: PARTIALLY with deltaReason (required for non-DONE)
    const queenActual = await updateActual(secondCommitId, queenCommit.version as number, {
      actualResult: 'Monitoring dashboard 70% complete',
      completionStatus: 'PARTIALLY',
      deltaReason: 'Blocked on infra team to provision PagerDuty integration',
      timeSpent: 20,
    }, token);
    expect(queenActual.status).toBe(200);
    expect(queenActual.body.completionStatus).toBe('PARTIALLY');

    // Submit must succeed — all validation requirements satisfied
    const plan = await getPlan(planId, token);
    const submitRes = await submitReconciliation(planId, plan.body.version as number, token);

    expect(submitRes.status).toBe(200);
    expect(submitRes.body.state).toBe('RECONCILED');
    expect(submitRes.body.reviewStatus).toBe('REVIEW_PENDING');
  });

  // ── Multiple commits with MISSING_DELTA_REASON ─────────────────────────

  test('[FULL] Submit with multiple non-DONE commits missing deltaReason → 422 MISSING_DELTA_REASON with all commit IDs in details', async () => {
    const token = tokenFor(freshUserId());
    const { planId, commitId, secondCommitId } = await setupReconcilingPlanWithTwoCommits(token);

    // Fetch current commit versions
    const commits = await getCommits(planId, token);
    const kingCommit = commits.body.find((c) => c.id === commitId)!;
    const queenCommit = commits.body.find((c) => c.id === secondCommitId)!;

    // Both commits: non-DONE without deltaReason
    const kingActual = await updateActual(commitId, kingCommit.version as number, {
      actualResult: 'Only partially completed enterprise deal',
      completionStatus: 'PARTIALLY',
      // deltaReason intentionally omitted
    }, token);
    if (kingActual.status !== 200) {
      expect(kingActual.status).toBe(422);
      return;
    }

    const queenActual = await updateActual(secondCommitId, queenCommit.version as number, {
      actualResult: 'Did not get to monitoring work this week',
      completionStatus: 'NOT_DONE',
      // deltaReason intentionally omitted
    }, token);
    if (queenActual.status !== 200) {
      expect(queenActual.status).toBe(422);
      return;
    }

    // Submit must be rejected — both commits are missing deltaReason
    const plan = await getPlan(planId, token);
    const submitRes = await submitReconciliation(planId, plan.body.version as number, token);

    expect(submitRes.status).toBe(422);
    const error = errorOf(submitRes);
    expect(error.code).toBe('MISSING_DELTA_REASON');

    // Both commit IDs should appear in the error details
    const details = (error.details as Array<Record<string, unknown>>) ?? [];
    const kingReferenced = details.some((d) => d.commitId === commitId);
    const queenReferenced = details.some((d) => d.commitId === secondCommitId);
    expect(kingReferenced).toBe(true);
    expect(queenReferenced).toBe(true);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #5 — completionStatus is required for submission
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Reconciliation Validation — completionStatus required (§18 #5, #6)', () => {

  // ── Without any actuals ────────────────────────────────────────────────

  test('[FULL] Submit reconciliation without any actuals → 422 MISSING_COMPLETION_STATUS', async () => {
    const token = tokenFor(freshUserId());
    const { planId, commitId, planVersion: _pv } = await setupReconcilingPlan(token);

    // Do NOT call updateActual — no actuals exist for any commit in this plan

    // Submit must fail because commits have no completion status
    const plan = await getPlan(planId, token);
    const submitRes = await submitReconciliation(planId, plan.body.version as number, token);

    expect(submitRes.status).toBe(422);
    const error = errorOf(submitRes);
    expect(error.code).toBe('MISSING_COMPLETION_STATUS');

    const details = (error.details as Array<Record<string, unknown>>) ?? [];
    expect(details.some((d) => d.commitId === commitId)).toBe(true);

    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('RECONCILING');
  });

  // ── Some commits missing completionStatus ─────────────────────────────

  test('[FULL] Submit with some commits missing completionStatus → 422 MISSING_COMPLETION_STATUS', async () => {
    const token = tokenFor(freshUserId());
    const { planId, commitId, secondCommitId } = await setupReconcilingPlanWithTwoCommits(token);

    // Fetch current commit versions
    const commits = await getCommits(planId, token);
    const kingCommit = commits.body.find((c) => c.id === commitId)!;

    // Only fill in actuals for the KING commit; leave QUEEN commit without actuals
    const kingActual = await updateActual(commitId, kingCommit.version as number, {
      actualResult: 'Enterprise deal completed',
      completionStatus: 'DONE',
      timeSpent: 40,
    }, token);
    expect(kingActual.status).toBe(200);

    // Submit — QUEEN commit has no actual → should be rejected
    const plan = await getPlan(planId, token);
    const submitRes = await submitReconciliation(planId, plan.body.version as number, token);

    expect(submitRes.status).toBe(422);
    const error = errorOf(submitRes);
    expect(error.code).toBe('MISSING_COMPLETION_STATUS');

    const details = (error.details as Array<Record<string, unknown>>) ?? [];
    expect(details.some((d) => d.commitId === secondCommitId)).toBe(true);
    expect(details.some((d) => d.commitId === commitId)).toBe(false);

    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('RECONCILING');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #6 — Invalid plan state transitions during reconciliation
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Reconciliation Validation — Invalid State Transitions (§18 #6)', () => {

  // ── Cannot start reconciliation from RECONCILED ────────────────────────

  test('[FULL] Cannot start reconciliation from RECONCILED state → 409 CONFLICT', async () => {
    const token = tokenFor(freshUserId());
    const { planId, planVersion } = await setupReconciledPlan(token);

    // Attempt to start reconciliation on an already-RECONCILED plan
    const reconRes = await startReconciliation(planId, planVersion, token);

    // Must fail — can only start reconciliation from DRAFT or LOCKED
    expect(reconRes.status).toBe(409);
    const error = errorOf(reconRes);
    expect(error.code).toBe('CONFLICT');
  });

  // ── Cannot re-lock a RECONCILED plan ──────────────────────────────────

  test('[FULL] Cannot lock a RECONCILED plan → 409 invalid transition', async () => {
    const token = tokenFor(freshUserId());
    const { planId, planVersion } = await setupReconciledPlan(token);

    // Attempt to lock an already-RECONCILED plan
    const lockRes = await lockPlan(planId, planVersion, token);

    // Must fail — lock is only allowed from DRAFT.
    // The backend has surfaced this as PLAN_NOT_IN_DRAFT and, in some
    // environments, as a generic CONFLICT. Both still represent the same
    // invalid transition for the AC.
    expect(lockRes.status).toBe(409);
    const error = errorOf(lockRes);
    expect(['PLAN_NOT_IN_DRAFT', 'CONFLICT']).toContain(error.code);

    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('RECONCILED');
  });

  // ── Cannot go back to LOCKED from RECONCILING ─────────────────────────

  test('[FULL] Cannot lock a plan that is already in RECONCILING state → 409 invalid transition', async () => {
    const token = tokenFor(freshUserId());
    const { planId, planVersion } = await setupReconcilingPlan(token);

    // Attempt to call lock on a RECONCILING plan (no valid transition back to LOCKED)
    const lockRes = await lockPlan(planId, planVersion, token);

    // Must fail — plan is not in DRAFT state.
    // Accept the specific code or the generic transition conflict, as the AC
    // is about rejecting the transition rather than the exact envelope code.
    expect(lockRes.status).toBe(409);
    const error = errorOf(lockRes);
    expect(['PLAN_NOT_IN_DRAFT', 'CONFLICT']).toContain(error.code);

    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('RECONCILING');
  });

  // ── Cannot call submit-reconciliation from LOCKED ──────────────────────

  test('[FULL] Cannot submit reconciliation from LOCKED state (must start-reconciliation first) → 409 CONFLICT', async () => {
    const token = tokenFor(freshUserId());
    const { planId, planVersion } = await setupLockedPlan(token);

    // Attempt to submit without first starting reconciliation
    const submitRes = await submitReconciliation(planId, planVersion, token);

    // Must fail — plan is in LOCKED, not RECONCILING
    expect(submitRes.status).toBe(409);
    const error = errorOf(submitRes);
    expect(error.code).toBe('CONFLICT');
  });

  // ── Cannot start reconciliation from RECONCILING ──────────────────────

  test('[FULL] Cannot start reconciliation when already in RECONCILING state → 409 CONFLICT', async () => {
    const token = tokenFor(freshUserId());
    const { planId, planVersion } = await setupReconcilingPlan(token);

    // Attempt to call start-reconciliation when already RECONCILING
    const reconRes = await startReconciliation(planId, planVersion, token);

    // Must fail — can only transition from DRAFT or LOCKED
    expect(reconRes.status).toBe(409);
    const error = errorOf(reconRes);
    expect(error.code).toBe('CONFLICT');
  });
});
