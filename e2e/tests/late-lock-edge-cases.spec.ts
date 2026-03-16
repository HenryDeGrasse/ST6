/**
 * API-level tests for late lock validation and edge cases (PRD §18 #15, #16).
 *
 * Covers:
 *   §18 #15 — Late lock happy path:
 *     DRAFT → RECONCILING directly via start-reconciliation; lockType = LATE_LOCK;
 *     RCDO snapshot fields are populated on all RCDO-linked commits.
 *
 *   §18 #16 — Late lock validation still applies:
 *     Commit missing chessPriority       → 422 MISSING_CHESS_PRIORITY, plan stays DRAFT
 *     Commit missing RCDO link/reason    → 422 MISSING_RCDO_OR_REASON, plan stays DRAFT
 *     Chess rule violation               → 422 CHESS_RULE_VIOLATION, plan stays DRAFT
 *
 *   §18 #1 edge cases:
 *     Plan creation for past week        → 422 PAST_WEEK_CREATION_BLOCKED
 *     Plan creation idempotency          → second create returns 200 with same plan
 *     Invalid weekStart (not Monday)     → 422 INVALID_WEEK_START
 *
 *   Idempotency key edge case:
 *     Same key, different request body   → 422 IDEMPOTENCY_KEY_REUSE
 *
 * Prerequisite: Backend running on localhost:8080 (./scripts/dev.sh --seed)
 *
 * Run:
 *   cd e2e && npx playwright test tests/late-lock-edge-cases.spec.ts --project=api
 */
import { expect, test } from '@playwright/test';
import {
  ORG_ID,
  OUTCOME_API_UPTIME,
  OUTCOME_ENTERPRISE_DEALS,
  api,
  carryForward,
  createCommit,
  createPlan,
  detailsOf,
  errorHasCode,
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
const PAST_WEEK = mondayOf(-1);

/**
 * Returns a date string for a non-Monday this week (Tuesday).
 * Used to test INVALID_WEEK_START rejection.
 */
function nonMondayThisWeek(): string {
  const monday = new Date(CURRENT_WEEK + 'T00:00:00Z');
  const tuesday = new Date(monday);
  tuesday.setUTCDate(monday.getUTCDate() + 1);
  return tuesday.toISOString().slice(0, 10);
}

// ═══════════════════════════════════════════════════════════════════════════
// §18 #15 — Late lock happy path
// DRAFT → RECONCILING directly via start-reconciliation
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Late Lock — Happy Path (§18 #15)', () => {

  const userId = freshUserId();
  const token = tokenFor(userId);
  let planId: string;
  let kingCommitId: string;
  let queenCommitId: string;

  test.describe.configure({ mode: 'serial' });

  test('[FULL] Setup: create plan and add valid commits (KING + QUEEN with RCDO links)', async () => {
    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    planId = planRes.body.id as string;
    expect(planRes.body.state).toBe('DRAFT');

    // KING commit linked to RCDO outcome
    const king = await createCommit(planId, {
      title: 'Late-lock KING: close enterprise deal',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
      expectedResult: 'Contract signed by EOW',
      confidence: 0.85,
    }, token);
    expect(king.status).toBe(201);
    kingCommitId = king.body.id as string;

    // QUEEN commit linked to RCDO outcome
    const queen = await createCommit(planId, {
      title: 'Late-lock QUEEN: improve API uptime monitoring',
      chessPriority: 'QUEEN',
      category: 'TECH_DEBT',
      outcomeId: OUTCOME_API_UPTIME,
      expectedResult: 'Monitoring dashboard live',
    }, token);
    expect(queen.status).toBe(201);
    queenCommitId = queen.body.id as string;
  });

  test('[FULL] Start reconciliation from DRAFT → implicit LATE_LOCK (DRAFT → RECONCILING)', async () => {
    await refreshRcdo(token);

    const planBefore = await getPlan(planId, token);
    expect(planBefore.status).toBe(200);
    expect(planBefore.body.state).toBe('DRAFT');
    expect(planBefore.body.lockType).toBeNull();

    // Call start-reconciliation directly from DRAFT (late lock path)
    const reconRes = await startReconciliation(planId, planBefore.body.version as number, token);

    expect(reconRes.status).toBe(200);
    expect(reconRes.body.state).toBe('RECONCILING');
    // lockType must be LATE_LOCK (not ON_TIME)
    expect(reconRes.body.lockType).toBe('LATE_LOCK');
    // lockedAt must be populated
    expect(reconRes.body.lockedAt).toBeTruthy();
  });

  test('[FULL] After late lock, plan has RECONCILING state and LATE_LOCK type', async () => {
    const plan = await getPlan(planId, token);
    expect(plan.status).toBe(200);
    expect(plan.body.state).toBe('RECONCILING');
    expect(plan.body.lockType).toBe('LATE_LOCK');
    expect(plan.body.lockedAt).toBeTruthy();
  });

  test('[FULL] After late lock, RCDO snapshot fields are populated on all RCDO-linked commits', async () => {
    const commitsRes = await getCommits(planId, token);
    expect(commitsRes.status).toBe(200);
    expect(commitsRes.body).toHaveLength(2);

    // Verify KING commit snapshot
    const king = commitsRes.body.find((c) => c.id === kingCommitId);
    expect(king).toBeTruthy();
    expect(king!.snapshotOutcomeId).toBe(OUTCOME_ENTERPRISE_DEALS);
    expect(typeof king!.snapshotOutcomeName).toBe('string');
    expect((king!.snapshotOutcomeName as string).length).toBeGreaterThan(0);
    expect(typeof king!.snapshotRallyCryName).toBe('string');
    expect((king!.snapshotRallyCryName as string).length).toBeGreaterThan(0);
    expect(typeof king!.snapshotObjectiveName).toBe('string');
    expect((king!.snapshotObjectiveName as string).length).toBeGreaterThan(0);
    expect(king!.snapshotRallyCryId).toBeTruthy();
    expect(king!.snapshotObjectiveId).toBeTruthy();

    // Verify QUEEN commit snapshot
    const queen = commitsRes.body.find((c) => c.id === queenCommitId);
    expect(queen).toBeTruthy();
    expect(queen!.snapshotOutcomeId).toBe(OUTCOME_API_UPTIME);
    expect(queen!.snapshotOutcomeName).toBeTruthy();
    expect(queen!.snapshotRallyCryId).toBeTruthy();
    expect(queen!.snapshotObjectiveId).toBeTruthy();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #16 — Late lock validation still applies
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Late Lock — Validation Still Applies (§18 #16)', () => {

  // ── Missing chessPriority ──────────────────────────────────────────────

  test('[FULL] Late lock with commit missing chessPriority → 422 MISSING_CHESS_PRIORITY, plan stays DRAFT', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    const planId = planRes.body.id as string;

    // Create a commit with no chessPriority (allowed in DRAFT)
    const commitRes = await createCommit(planId, {
      title: 'Late-lock commit missing chessPriority',
      // chessPriority intentionally omitted
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, token);
    expect(commitRes.status).toBe(201);
    expect(commitRes.body.chessPriority).toBeNull();

    await refreshRcdo(token);

    const plan = await getPlan(planId, token);
    expect(plan.body.state).toBe('DRAFT');

    // start-reconciliation from DRAFT should fail (late lock validation rejects it)
    const reconRes = await startReconciliation(planId, plan.body.version as number, token);

    expect(reconRes.status).toBe(422);
    const error = errorOf(reconRes);
    // MISSING_CHESS_PRIORITY must appear at top level or in details
    expect(errorHasCode(error, 'MISSING_CHESS_PRIORITY')).toBe(true);

    // Plan must remain in DRAFT (no implicit lock created)
    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('DRAFT');
    expect(planAfter.body.lockType).toBeNull();
    expect(planAfter.body.lockedAt).toBeNull();
  });

  // ── Missing RCDO link and non-strategic reason ─────────────────────────

  test('[FULL] Late lock with commit missing both outcomeId and nonStrategicReason → 422 MISSING_RCDO_OR_REASON, plan stays DRAFT', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    const planId = planRes.body.id as string;

    // Create a commit with chessPriority but no RCDO link and no non-strategic reason
    const commitRes = await createCommit(planId, {
      title: 'Late-lock commit with no RCDO link and no reason',
      chessPriority: 'KING',
      category: 'DELIVERY',
      // outcomeId and nonStrategicReason both omitted
    }, token);
    expect(commitRes.status).toBe(201);
    expect(commitRes.body.outcomeId).toBeNull();
    expect(commitRes.body.nonStrategicReason).toBeNull();

    await refreshRcdo(token);

    const plan = await getPlan(planId, token);
    expect(plan.body.state).toBe('DRAFT');

    // start-reconciliation from DRAFT must be rejected
    const reconRes = await startReconciliation(planId, plan.body.version as number, token);

    expect(reconRes.status).toBe(422);
    const error = errorOf(reconRes);
    // MISSING_RCDO_OR_REASON at top level or in details
    expect(errorHasCode(error, 'MISSING_RCDO_OR_REASON')).toBe(true);

    // Plan must remain in DRAFT
    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('DRAFT');
    expect(planAfter.body.lockType).toBeNull();
    expect(planAfter.body.lockedAt).toBeNull();
  });

  // ── Chess rule violation ───────────────────────────────────────────────

  test('[FULL] Late lock with chess rule violation (2 KING commits) → 422 CHESS_RULE_VIOLATION, plan stays DRAFT', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    const planId = planRes.body.id as string;

    // Add 2 KING commits — violates the MAX_KING=1 rule
    const king1 = await createCommit(planId, {
      title: 'Late-lock first KING (valid in isolation)',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, token);
    expect(king1.status).toBe(201);

    const king2 = await createCommit(planId, {
      title: 'Late-lock second KING (violates chess rule)',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_API_UPTIME,
    }, token);
    expect(king2.status).toBe(201);

    await refreshRcdo(token);

    const plan = await getPlan(planId, token);
    expect(plan.body.state).toBe('DRAFT');

    // start-reconciliation from DRAFT must be rejected due to chess rule violation
    const reconRes = await startReconciliation(planId, plan.body.version as number, token);

    expect(reconRes.status).toBe(422);
    const error = errorOf(reconRes);
    expect(error.code).toBe('CHESS_RULE_VIOLATION');

    // Plan must remain in DRAFT — no implicit lock created
    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('DRAFT');
    expect(planAfter.body.lockType).toBeNull();
    expect(planAfter.body.lockedAt).toBeNull();
  });

  // ── Empty plan ─────────────────────────────────────────────────────────

  test('[FULL] Late lock on empty plan (no commits) → 422, plan stays DRAFT', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    const planId = planRes.body.id as string;

    // No commits added
    await refreshRcdo(token);

    const plan = await getPlan(planId, token);
    expect(plan.body.state).toBe('DRAFT');

    // start-reconciliation from DRAFT with no commits must be rejected
    const reconRes = await startReconciliation(planId, plan.body.version as number, token);

    expect(reconRes.status).toBe(422);
    const error = errorOf(reconRes);
    // Empty plan has 0 KINGs — chess rule violation or missing priority
    expect([
      'CHESS_RULE_VIOLATION',
      'MISSING_CHESS_PRIORITY',
      'MISSING_RCDO_OR_REASON',
    ]).toContain(error.code as string);

    // Plan must remain in DRAFT
    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('DRAFT');
    expect(planAfter.body.lockType).toBeNull();
  });

  // ── Missing chessPriority AND missing RCDO — multiple validation errors ─

  test('[FULL] Late lock with commit missing both chessPriority and RCDO → 422, plan stays DRAFT', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    const planId = planRes.body.id as string;

    // Create a completely incomplete commit — no chessPriority, no RCDO link
    const commitRes = await createCommit(planId, {
      title: 'Completely incomplete commit for late lock test',
      // chessPriority, outcomeId, and nonStrategicReason all omitted
      category: 'DELIVERY',
    }, token);
    expect(commitRes.status).toBe(201);
    expect(commitRes.body.chessPriority).toBeNull();
    expect(commitRes.body.outcomeId).toBeNull();

    await refreshRcdo(token);

    const plan = await getPlan(planId, token);
    expect(plan.body.state).toBe('DRAFT');

    const reconRes = await startReconciliation(planId, plan.body.version as number, token);

    // Any validation error must be returned (exact code depends on which check runs first)
    expect(reconRes.status).toBe(422);
    const error = errorOf(reconRes);
    expect(error).toBeTruthy();
    expect(error.code).toBeTruthy();

    // Plan must remain in DRAFT
    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('DRAFT');
    expect(planAfter.body.lockType).toBeNull();
    expect(planAfter.body.lockedAt).toBeNull();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #1 edge cases — Plan creation validation
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Plan Creation — Edge Cases (§18 #1)', () => {

  // ── Idempotency: create same week twice ────────────────────────────────

  test('[FULL] Create plan for the same week twice → second returns 200 with the same plan', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    // First create → 201 Created
    const firstRes = await createPlan(CURRENT_WEEK, token);
    expect(firstRes.status).toBe(201);
    expect(firstRes.body.state).toBe('DRAFT');
    const firstPlanId = firstRes.body.id as string;
    expect(firstPlanId).toBeTruthy();

    // Second create for the same week and user → 200 OK (idempotent, same plan)
    const secondRes = await createPlan(CURRENT_WEEK, token);
    expect(secondRes.status).toBe(200);
    expect(secondRes.body.id).toBe(firstPlanId);
    expect(secondRes.body.state).toBe('DRAFT');
    expect(secondRes.body.orgId).toBe(ORG_ID);
    expect(secondRes.body.ownerUserId).toBe(userId);
    expect(secondRes.body.weekStartDate).toBe(CURRENT_WEEK);
  });

  // ── Past-week creation blocked ─────────────────────────────────────────

  test('[FULL] Create plan for a past week → 422 PAST_WEEK_CREATION_BLOCKED', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const res = await createPlan(PAST_WEEK, token);

    expect(res.status).toBe(422);
    const error = errorOf(res);
    expect(error.code).toBe('PAST_WEEK_CREATION_BLOCKED');

    // Details should include the weekStart that was provided
    const details = detailsOf(error);
    const hasWeekStart = details.some(
      (d) => d.weekStart === PAST_WEEK || d.provided === PAST_WEEK,
    );
    expect(hasWeekStart).toBe(true);
  });

  // ── Invalid weekStart (not a Monday) ──────────────────────────────────

  test('[FULL] Create plan with weekStart that is not a Monday → 422 INVALID_WEEK_START', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const tuesday = nonMondayThisWeek();
    const res = await createPlan(tuesday, token);

    expect(res.status).toBe(422);
    const error = errorOf(res);
    expect(error.code).toBe('INVALID_WEEK_START');

    // Details should reference the provided date
    const details = detailsOf(error);
    const hasProvidedDate = details.some((d) => d.provided === tuesday);
    expect(hasProvidedDate).toBe(true);
  });

  // ── Invalid weekStart: future beyond next week ─────────────────────────

  test('[FULL] Create plan for a week more than one week in advance → 422 INVALID_WEEK_START', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    // Two weeks in the future (beyond the allowed next-week advance)
    const twoWeeksOut = mondayOf(2);
    const res = await createPlan(twoWeeksOut, token);

    expect(res.status).toBe(422);
    const error = errorOf(res);
    // The backend returns INVALID_WEEK_START for future-beyond-next-week
    expect(error.code).toBe('INVALID_WEEK_START');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Idempotency key edge cases
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Idempotency Key — Edge Cases', () => {

  // ── Same key, different request body → REUSE error ───────────────────

  test('[FULL] Idempotency key reuse with the same path but different body → 422 IDEMPOTENCY_KEY_REUSE', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    const planId = planRes.body.id as string;

    const kingRes = await createCommit(planId, {
      title: 'Carry-forward idempotency test: KING commit',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, token);
    expect(kingRes.status).toBe(201);
    const kingCommitId = kingRes.body.id as string;

    const queenRes = await createCommit(planId, {
      title: 'Carry-forward idempotency test: QUEEN commit',
      chessPriority: 'QUEEN',
      category: 'TECH_DEBT',
      outcomeId: OUTCOME_API_UPTIME,
    }, token);
    expect(queenRes.status).toBe(201);
    const queenCommitId = queenRes.body.id as string;

    await refreshRcdo(token);

    const draftPlan = await getPlan(planId, token);
    const lockRes = await lockPlan(planId, draftPlan.body.version as number, token);
    expect(lockRes.status).toBe(200);

    const lockedPlan = await getPlan(planId, token);
    const reconRes = await startReconciliation(planId, lockedPlan.body.version as number, token);
    expect(reconRes.status).toBe(200);

    const commitsRes = await getCommits(planId, token);
    expect(commitsRes.status).toBe(200);
    const kingCommit = commitsRes.body.find((commit) => commit.id === kingCommitId);
    const queenCommit = commitsRes.body.find((commit) => commit.id === queenCommitId);
    expect(kingCommit).toBeTruthy();
    expect(queenCommit).toBeTruthy();

    const kingActual = await updateActual(kingCommitId, kingCommit!.version as number, {
      actualResult: 'Delivered the enterprise deal milestone',
      completionStatus: 'DONE',
      timeSpent: 24,
    }, token);
    expect(kingActual.status).toBe(200);

    const queenActual = await updateActual(queenCommitId, queenCommit!.version as number, {
      actualResult: 'Monitoring dashboard is partially complete',
      completionStatus: 'PARTIALLY',
      deltaReason: 'Blocked on external dependency',
      timeSpent: 16,
    }, token);
    expect(queenActual.status).toBe(200);

    const reconcilingPlan = await getPlan(planId, token);
    const submitRes = await submitReconciliation(planId, reconcilingPlan.body.version as number, token);
    expect(submitRes.status).toBe(200);
    expect(submitRes.body.state).toBe('RECONCILED');

    const reconciledPlan = await getPlan(planId, token);
    const sharedIdempotencyKey = crypto.randomUUID();

    // First carry-forward request succeeds.
    const firstCarryForward = await carryForward(
      planId,
      reconciledPlan.body.version as number,
      [queenCommitId],
      token,
      sharedIdempotencyKey,
    );
    expect(firstCarryForward.status).toBe(200);
    expect(firstCarryForward.body.state).toBe('CARRY_FORWARD');

    // Reusing the same key against the same endpoint but with a different body
    // must be rejected before the request reaches the controller.
    const secondCarryForward = await carryForward(
      planId,
      reconciledPlan.body.version as number,
      [kingCommitId],
      token,
      sharedIdempotencyKey,
    );
    expect(secondCarryForward.status).toBe(422);
    const error = errorOf(secondCarryForward);
    expect(error.code).toBe('IDEMPOTENCY_KEY_REUSE');

    const planAfter = await getPlan(planId, token);
    expect(planAfter.body.state).toBe('CARRY_FORWARD');
  });

  // ── Same key, same request → idempotent replay ─────────────────────────

  test('[FULL] Idempotency key reuse with the same request → replays the original response (no error)', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    const planId = planRes.body.id as string;

    await createCommit(planId, {
      title: 'Idempotency replay test: KING commit',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, token);

    await refreshRcdo(token);

    const plan = await getPlan(planId, token);
    const idempotencyKey = crypto.randomUUID();

    // First lock → succeeds
    const lock1 = await lockPlan(planId, plan.body.version as number, token, idempotencyKey);
    expect(lock1.status).toBe(200);
    expect(lock1.body.state).toBe('LOCKED');

    // Second call with the SAME key and SAME request → replays the cached response
    const lock2 = await lockPlan(planId, plan.body.version as number, token, idempotencyKey);
    // The replayed response should match the original (same state, same key)
    expect(lock2.status).toBe(200);
    expect(lock2.body.state).toBe('LOCKED');
  });

  // ── Missing idempotency key on lifecycle endpoint ──────────────────────

  test('[FULL] Call lifecycle endpoint without Idempotency-Key header → 400 MISSING_IDEMPOTENCY_KEY', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    const planId = planRes.body.id as string;

    // Attempt to lock without the Idempotency-Key header
    const res = await api('POST', `/api/v1/plans/${planId}/lock`, {
      token,
      ifMatch: planRes.body.version as number,
      // idempotencyKey intentionally omitted
    });

    expect(res.status).toBe(400);
    const error = errorOf(res);
    expect(error.code).toBe('MISSING_IDEMPOTENCY_KEY');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Late lock + plan creation boundary conditions
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Late Lock — Boundary Conditions', () => {

  // ── Normal lock (ON_TIME) vs. late lock (LATE_LOCK) same plan ──────────

  test('[FULL] Standard lock path (DRAFT → LOCKED → RECONCILING) sets lockType ON_TIME', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    const planId = planRes.body.id as string;

    await createCommit(planId, {
      title: 'ON_TIME lock test: KING commit',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, token);

    await refreshRcdo(token);

    const planBeforeLock = await getPlan(planId, token);
    // Lock first (ON_TIME path)
    const lockRes = await lockPlan(planId, planBeforeLock.body.version as number, token);
    expect(lockRes.status).toBe(200);
    expect(lockRes.body.state).toBe('LOCKED');
    expect(lockRes.body.lockType).toBe('ON_TIME');

    // Then start reconciliation (normal transition: LOCKED → RECONCILING)
    const planAfterLock = await getPlan(planId, token);
    const reconRes = await startReconciliation(planId, planAfterLock.body.version as number, token);
    expect(reconRes.status).toBe(200);
    expect(reconRes.body.state).toBe('RECONCILING');
    // lockType must remain ON_TIME (not LATE_LOCK)
    expect(reconRes.body.lockType).toBe('ON_TIME');
  });

  // ── Confirm late lock sets lockedAt ────────────────────────────────────

  test('[FULL] Late lock populates lockedAt timestamp on the plan', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    const planId = planRes.body.id as string;

    await createCommit(planId, {
      title: 'lockedAt timestamp test: KING commit',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, token);

    await refreshRcdo(token);

    // Verify lockedAt is null in DRAFT
    const planInDraft = await getPlan(planId, token);
    expect(planInDraft.body.state).toBe('DRAFT');
    expect(planInDraft.body.lockedAt).toBeNull();

    // Late lock via start-reconciliation
    const reconRes = await startReconciliation(planId, planInDraft.body.version as number, token);
    expect(reconRes.status).toBe(200);
    expect(reconRes.body.state).toBe('RECONCILING');
    expect(reconRes.body.lockType).toBe('LATE_LOCK');

    // lockedAt must now be a valid ISO timestamp string
    const lockedAt = reconRes.body.lockedAt as string;
    expect(lockedAt).toBeTruthy();
    expect(new Date(lockedAt).getTime()).not.toBeNaN();
    // The lockedAt timestamp should be reasonably recent (within the last 60 seconds)
    const nowMs = Date.now();
    const lockedAtMs = new Date(lockedAt).getTime();
    expect(nowMs - lockedAtMs).toBeLessThan(60_000);
  });
});
