/**
 * API-level tests for manager dashboard, review flow, and permissions.
 * PRD §18 #9–#14, #17
 *
 * Covers:
 *   §18 #9:  Manager sees direct reports summary for current week
 *   §18 #10: Manager views RCDO roll-up grouped by outcome
 *   §18 #11: Manager approves a plan → reviewStatus = APPROVED
 *   §18 #11: Manager requests changes on RECONCILED plan → reviewStatus = CHANGES_REQUESTED,
 *            plan state reverts to RECONCILING
 *   §18 #12: IC requests GET /api/v1/weeks/{weekStart}/plans/{otherUserId} → 403 FORBIDDEN
 *   §18 #13: IC requests POST /api/v1/plans/{planId}/review → 403 FORBIDDEN
 *   §18 #14: Manager requests PATCH on a report's commit → 403 FORBIDDEN
 *   §18 #17: Manager requests changes after carry-forward already executed
 *            → 409 CARRY_FORWARD_ALREADY_EXECUTED
 *   §18 #17: Manager can still approve after carry-forward → 200
 *
 * Dev org graph (from OrgGraphDevDataInitializer):
 *   Carol Park  (CAROL_ID = SEED_USER_ID) — manager; manages Alice and Bob
 *   Alice Chen  (ALICE_ID)               — IC, direct report of Carol
 *   Bob Martinez (BOB_ID)               — IC, direct report of Carol
 *
 * Seeded state (from seed-data.sql):
 *   Alice's current-week plan: DRAFT  (b0…0010, 4 commits)
 *   Bob's current-week plan:   RECONCILED / REVIEW_PENDING (b0…0020)
 *   Carol's current-week plan: LOCKED (b0…0001)
 *   Alice's KING commit:       d0…0010 (in Alice's current-week DRAFT plan)
 *
 * Prerequisite: Backend running on localhost:8080 (./scripts/dev.sh --seed)
 *
 * Run:
 *   cd e2e && npx playwright test tests/manager-dashboard-api.spec.ts --project=api
 */
import { expect, test } from '@playwright/test';
import {
  OUTCOME_API_UPTIME,
  OUTCOME_ENTERPRISE_DEALS,
  SEED_USER_ID,
  api,
  carryForward,
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
  type ApiResponse,
} from './helpers';

/**
 * Seeded personas (from OrgGraphDevDataInitializer + seed-data.sql).
 * Carol is the manager; Alice and Bob are her direct reports.
 */
const CAROL_ID = SEED_USER_ID; // Manager
const ALICE_ID = 'c0000000-0000-0000-0000-000000000010'; // IC, Carol's direct report
const BOB_ID   = 'c0000000-0000-0000-0000-000000000020'; // IC, Carol's direct report

/**
 * Seeded plan / commit IDs that are stable across runs (ON CONFLICT DO NOTHING).
 * We only read from these; test-specific writes use NEXT_WEEK plans.
 */
const BOB_CURRENT_WEEK_PLAN_ID  = 'b0000000-0000-0000-0000-000000000020'; // RECONCILED
const ALICE_KING_COMMIT_ID      = 'd0000000-0000-0000-0000-000000000010'; // DRAFT commit

const PLAN_STATES = ['DRAFT', 'LOCKED', 'RECONCILING', 'RECONCILED', 'CARRY_FORWARD'];

// Token shortcuts for the three seeded personas
const carolToken = tokenFor(CAROL_ID, 'IC,MANAGER');
const aliceToken = tokenFor(ALICE_ID);
const bobToken = tokenFor(BOB_ID);

const CURRENT_WEEK = mondayOf(0);
const NEXT_WEEK = mondayOf(1);

async function submitReview(
  planId: string,
  decision: string,
  comments: string,
  token: string,
): Promise<ApiResponse> {
  return api('POST', `/api/v1/plans/${planId}/review`, {
    token,
    body: { decision, comments },
  });
}

// ═══════════════════════════════════════════════════════════════════════════
// §18 #9 — Manager sees direct reports summary for current week
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard — Direct Reports Summary (§18 #9)', () => {

  test('[FULL] Manager (Carol) sees Alice and Bob in team summary for current week', async () => {
    const res = await api('GET', `/api/v1/weeks/${CURRENT_WEEK}/team/summary`, {
      token: carolToken,
    });

    expect(res.status).toBe(200);

    // Verify top-level shape
    expect(res.body.weekStart).toBe(CURRENT_WEEK);
    expect(Array.isArray(res.body.users)).toBe(true);

    const users = res.body.users as Array<Record<string, unknown>>;
    // Carol manages Alice and Bob → at least 2 entries
    expect(users.length).toBeGreaterThanOrEqual(2);

    // Both direct reports must appear in the summary
    const userIds = users.map((u) => u.userId as string);
    expect(userIds).toContain(ALICE_ID);
    expect(userIds).toContain(BOB_ID);

    // Both direct-report entries should expose the expected identifying fields
    const aliceEntry = users.find((u) => u.userId === ALICE_ID);
    expect(aliceEntry).toBeTruthy();
    expect(aliceEntry!.planId).toBeTruthy();
    expect(aliceEntry!.displayName).toBeTruthy();
    expect(PLAN_STATES).toContain(aliceEntry!.state);

    const bobEntry = users.find((u) => u.userId === BOB_ID);
    expect(bobEntry).toBeTruthy();
    expect(bobEntry!.planId).toBeTruthy();
    expect(bobEntry!.displayName).toBeTruthy();
    expect(PLAN_STATES).toContain(bobEntry!.state);
  });

  test('[FULL] Team summary includes review status counts (reviewStatusCounts)', async () => {
    const res = await api('GET', `/api/v1/weeks/${CURRENT_WEEK}/team/summary`, {
      token: carolToken,
    });

    expect(res.status).toBe(200);

    const counts = res.body.reviewStatusCounts as Record<string, unknown>;
    expect(counts).toBeTruthy();
    expect(typeof counts.pending).toBe('number');
    expect(typeof counts.approved).toBe('number');
    expect(typeof counts.changesRequested).toBe('number');

    const users = res.body.users as Array<Record<string, unknown>>;
    const derivedCounts = users.reduce(
      (acc, user) => {
        switch (user.reviewStatus) {
          case 'REVIEW_PENDING':
            acc.pending += 1;
            break;
          case 'APPROVED':
            acc.approved += 1;
            break;
          case 'CHANGES_REQUESTED':
            acc.changesRequested += 1;
            break;
          default:
            break;
        }
        return acc;
      },
      { approved: 0, changesRequested: 0, pending: 0 },
    );

    expect(counts.pending).toBe(derivedCounts.pending);
    expect(counts.approved).toBe(derivedCounts.approved);
    expect(counts.changesRequested).toBe(derivedCounts.changesRequested);
  });

  test('[FULL] Team summary pagination fields are present', async () => {
    const res = await api('GET', `/api/v1/weeks/${CURRENT_WEEK}/team/summary`, {
      token: carolToken,
    });

    expect(res.status).toBe(200);
    expect(typeof res.body.page).toBe('number');
    expect(typeof res.body.size).toBe('number');
    expect(typeof res.body.totalElements).toBe('number');
    expect(typeof res.body.totalPages).toBe('number');
    expect(res.body.totalElements as number).toBeGreaterThanOrEqual(2);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #10 — Manager views RCDO roll-up grouped by outcome
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Dashboard — RCDO Roll-Up (§18 #10)', () => {

  test('[FULL] Manager (Carol) gets RCDO roll-up grouped by outcome for current week', async () => {
    const res = await api('GET', `/api/v1/weeks/${CURRENT_WEEK}/team/rcdo-rollup`, {
      token: carolToken,
    });

    expect(res.status).toBe(200);
    expect(res.body.weekStart).toBe(CURRENT_WEEK);
    expect(Array.isArray(res.body.items)).toBe(true);

    const items = res.body.items as Array<Record<string, unknown>>;
    // Bob has 3 RCDO-linked commits; Alice has 3 → at least some items expected
    expect(items.length).toBeGreaterThanOrEqual(1);

    // Each roll-up item should have an outcomeId and commit count
    const firstItem = items[0];
    expect(firstItem.outcomeId).toBeTruthy();
    expect(typeof firstItem.commitCount).toBe('number');
    expect(firstItem.commitCount as number).toBeGreaterThanOrEqual(1);
  });

  test('[FULL] RCDO roll-up nonStrategicCount is positive (Bob has a non-strategic PAWN commit)', async () => {
    const res = await api('GET', `/api/v1/weeks/${CURRENT_WEEK}/team/rcdo-rollup`, {
      token: carolToken,
    });

    expect(res.status).toBe(200);
    expect(typeof res.body.nonStrategicCount).toBe('number');
    // Bob's seeded PAWN commit has nonStrategicReason set
    expect(res.body.nonStrategicCount as number).toBeGreaterThanOrEqual(1);
  });

  test('[FULL] RCDO roll-up items include chess priority breakdowns', async () => {
    const res = await api('GET', `/api/v1/weeks/${CURRENT_WEEK}/team/rcdo-rollup`, {
      token: carolToken,
    });

    expect(res.status).toBe(200);
    const items = res.body.items as Array<Record<string, unknown>>;
    expect(items.length).toBeGreaterThanOrEqual(1);

    // Verify roll-up item shape includes all priority fields
    const item = items[0];
    expect(typeof item.kingCount).toBe('number');
    expect(typeof item.queenCount).toBe('number');
    expect(typeof item.rookCount).toBe('number');
    expect(typeof item.bishopCount).toBe('number');
    expect(typeof item.knightCount).toBe('number');
    expect(typeof item.pawnCount).toBe('number');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #11 — Manager approves a RECONCILED plan
//
// Uses Bob's NEXT_WEEK plan (not the seeded current-week plan). The setup
// reuses an existing Bob NEXT_WEEK plan when present and advances it to a
// reviewable state, so the spec remains rerunnable against a dirty dev DB.
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Review — Approve (§18 #11)', () => {
  let planId: string;

  test.describe.configure({ mode: 'serial' });

  test('[FULL] Setup: create or reuse Bob NEXT_WEEK plan', async () => {
    const res = await createPlan(NEXT_WEEK, bobToken);
    expect([200, 201]).toContain(res.status);
    planId = res.body.id as string;
    expect(planId).toBeTruthy();
  });

  test('[FULL] Setup: ensure Bob\'s NEXT_WEEK plan has at least one commit', async () => {
    if (!planId) { test.skip(); return; }

    const plan = await getPlan(planId, bobToken);
    if (plan.body.state !== 'DRAFT') {
      return;
    }

    const commits = await getCommits(planId, bobToken);
    if (commits.body.length > 0) {
      return;
    }

    const king = await createCommit(planId, {
      title: 'Approve test: close enterprise deal',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
      expectedResult: 'Contract signed',
    }, bobToken);
    expect(king.status).toBe(201);
  });

  test('[FULL] Setup: progress Bob plan to RECONCILED when needed', async () => {
    if (!planId) { test.skip(); return; }

    let plan = await getPlan(planId, bobToken);

    if (plan.body.state === 'DRAFT') {
      await refreshRcdo(bobToken);
      const lockRes = await lockPlan(planId, plan.body.version as number, bobToken);
      expect(lockRes.status).toBe(200);
      expect(lockRes.body.state).toBe('LOCKED');
      plan = await getPlan(planId, bobToken);
    }

    if (plan.body.state === 'LOCKED') {
      const reconRes = await startReconciliation(planId, plan.body.version as number, bobToken);
      expect(reconRes.status).toBe(200);
      expect(reconRes.body.state).toBe('RECONCILING');
      plan = await getPlan(planId, bobToken);
    }

    if (plan.body.state === 'RECONCILING') {
      const commits = await getCommits(planId, bobToken);
      expect(commits.status).toBe(200);
      expect(commits.body.length).toBeGreaterThan(0);

      for (const commit of commits.body) {
        const actualRes = await updateActual(commit.id as string, commit.version as number, {
          actualResult: `Completed: ${String(commit.title ?? 'manager approval commit')}`,
          completionStatus: 'DONE',
          timeSpent: 8,
        }, bobToken);
        expect(actualRes.status).toBe(200);
      }

      plan = await getPlan(planId, bobToken);
      const submitRes = await submitReconciliation(planId, plan.body.version as number, bobToken);
      expect(submitRes.status).toBe(200);
      expect(submitRes.body.state).toBe('RECONCILED');
      expect(submitRes.body.reviewStatus).toBe('REVIEW_PENDING');
      return;
    }

    expect(['RECONCILED', 'CARRY_FORWARD']).toContain(plan.body.state);
  });

  test('[FULL] Carol (manager) approves Bob\'s plan → reviewStatus = APPROVED (§18 #11)', async () => {
    if (!planId) { test.skip(); return; }

    const plan = await getPlan(planId, carolToken);
    if (plan.body.state !== 'RECONCILED' && plan.body.state !== 'CARRY_FORWARD') {
      test.skip();
      return;
    }

    const reviewRes = await submitReview(planId, 'APPROVED', 'Great work this week, Bob!', carolToken);
    expect(reviewRes.status).toBe(200);
    expect(reviewRes.body.decision).toBe('APPROVED');
    expect(reviewRes.body.weeklyPlanId).toBe(planId);
    expect(reviewRes.body.reviewerUserId).toBe(CAROL_ID);

    // Verify plan reviewStatus changed to APPROVED while remaining reviewable
    const planAfter = await getPlan(planId, carolToken);
    expect(planAfter.status).toBe(200);
    expect(planAfter.body.reviewStatus).toBe('APPROVED');
    expect(['RECONCILED', 'CARRY_FORWARD']).toContain(planAfter.body.state);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #11, #17 — Manager requests changes → plan reverts to RECONCILING;
//                Manager requests changes after carry-forward → 409;
//                Manager can approve after carry-forward → 200
//
// Uses Alice's NEXT_WEEK plan and runs the following chain:
//   DRAFT → LOCKED → RECONCILING → RECONCILED
//     → [Carol: CHANGES_REQUESTED] → RECONCILING
//     → [Alice re-submits] → RECONCILED
//     → [Alice carry-forward] → CARRY_FORWARD
//     → [Carol: CHANGES_REQUESTED] → 409
//     → [Carol: APPROVED] → 200
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Review — Changes Requested + Carry Forward (§18 #11, #17)', () => {
  let planId: string;
  let kingCommitId: string;
  let queenCommitId: string;

  test.describe.configure({ mode: 'serial' });

  test('[FULL] Setup: create Alice NEXT_WEEK plan with KING and QUEEN commits', async () => {
    const res = await createPlan(NEXT_WEEK, aliceToken);
    expect([200, 201]).toContain(res.status);
    planId = res.body.id as string;
    expect(planId).toBeTruthy();

    if (res.body.state !== 'DRAFT') {
      // Plan from a previous run — clean state required to restart
      test.skip();
      return;
    }

    // Add KING commit linked to RCDO outcome
    const king = await createCommit(planId, {
      title: 'Changes-requested test: close enterprise deal',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
      expectedResult: 'Contract signed by EOW',
    }, aliceToken);
    expect(king.status).toBe(201);
    kingCommitId = king.body.id as string;

    // Add QUEEN commit (will be PARTIALLY done → eligible for carry-forward)
    const queen = await createCommit(planId, {
      title: 'Changes-requested test: improve API uptime monitoring',
      chessPriority: 'QUEEN',
      category: 'TECH_DEBT',
      outcomeId: OUTCOME_API_UPTIME,
      expectedResult: 'Alerting dashboard live',
    }, aliceToken);
    expect(queen.status).toBe(201);
    queenCommitId = queen.body.id as string;
  });

  test('[FULL] Setup: lock → start reconciliation', async () => {
    if (!planId || !kingCommitId || !queenCommitId) { test.skip(); return; }

    const plan = await getPlan(planId, aliceToken);
    if (plan.body.state !== 'DRAFT') { test.skip(); return; }

    await refreshRcdo(aliceToken);
    const lockRes = await lockPlan(planId, plan.body.version as number, aliceToken);
    expect(lockRes.status).toBe(200);
    expect(lockRes.body.state).toBe('LOCKED');

    const planAfterLock = await getPlan(planId, aliceToken);
    const reconRes = await startReconciliation(
      planId, planAfterLock.body.version as number, aliceToken,
    );
    expect(reconRes.status).toBe(200);
    expect(reconRes.body.state).toBe('RECONCILING');
  });

  test('[FULL] Setup: fill actuals (KING=DONE, QUEEN=PARTIALLY) and submit reconciliation', async () => {
    if (!planId || !kingCommitId || !queenCommitId) { test.skip(); return; }

    const plan = await getPlan(planId, aliceToken);
    if (plan.body.state !== 'RECONCILING') { test.skip(); return; }

    const commits = await getCommits(planId, aliceToken);
    const king = commits.body.find((c) => c.id === kingCommitId)!;
    const queen = commits.body.find((c) => c.id === queenCommitId)!;

    // KING: completed this week
    const kingActual = await updateActual(kingCommitId, king.version as number, {
      actualResult: 'Enterprise contract signed and onboarding started.',
      completionStatus: 'DONE',
      timeSpent: 35,
    }, aliceToken);
    expect(kingActual.status).toBe(200);

    // QUEEN: partially done — needs deltaReason for non-DONE status
    const queenActual = await updateActual(queenCommitId, queen.version as number, {
      actualResult: 'Alerting dashboard 60% complete.',
      completionStatus: 'PARTIALLY',
      deltaReason: 'Blocked on infra team for PagerDuty integration provisioning.',
      timeSpent: 20,
    }, aliceToken);
    expect(queenActual.status).toBe(200);

    // Submit reconciliation
    const planBeforeSubmit = await getPlan(planId, aliceToken);
    const submitRes = await submitReconciliation(
      planId, planBeforeSubmit.body.version as number, aliceToken,
    );
    expect(submitRes.status).toBe(200);
    expect(submitRes.body.state).toBe('RECONCILED');
    expect(submitRes.body.reviewStatus).toBe('REVIEW_PENDING');
  });

  test('[FULL] Carol requests changes → plan state reverts to RECONCILING, reviewStatus = CHANGES_REQUESTED (§18 #11)', async () => {
    if (!planId) { test.skip(); return; }

    const plan = await getPlan(planId, carolToken);
    if (plan.body.state !== 'RECONCILED') { test.skip(); return; }

    const reviewRes = await submitReview(
      planId,
      'CHANGES_REQUESTED',
      'Please add more detail on the PagerDuty blocker and expected resolution date.',
      carolToken,
    );
    expect(reviewRes.status).toBe(200);
    expect(reviewRes.body.decision).toBe('CHANGES_REQUESTED');

    // Verify plan state reverted to RECONCILING
    const planAfter = await getPlan(planId, aliceToken);
    expect(planAfter.status).toBe(200);
    expect(planAfter.body.state).toBe('RECONCILING');
    expect(planAfter.body.reviewStatus).toBe('CHANGES_REQUESTED');
  });

  test('[FULL] Alice re-submits reconciliation after CHANGES_REQUESTED → returns to RECONCILED', async () => {
    if (!planId) { test.skip(); return; }

    const plan = await getPlan(planId, aliceToken);
    // Can only proceed from RECONCILING (the CHANGES_REQUESTED revert)
    if (plan.body.state !== 'RECONCILING') { test.skip(); return; }

    // Actuals from the previous cycle are still persisted; re-submission should succeed
    const submitRes = await submitReconciliation(
      planId, plan.body.version as number, aliceToken,
    );
    expect(submitRes.status).toBe(200);
    expect(submitRes.body.state).toBe('RECONCILED');
    expect(submitRes.body.reviewStatus).toBe('REVIEW_PENDING');
  });

  test('[FULL] Alice carries forward the PARTIALLY-done QUEEN commit → plan becomes CARRY_FORWARD', async () => {
    if (!planId || !queenCommitId) { test.skip(); return; }

    const plan = await getPlan(planId, aliceToken);
    if (plan.body.state !== 'RECONCILED') { test.skip(); return; }

    const cfRes = await carryForward(
      planId, plan.body.version as number, [queenCommitId], aliceToken,
    );
    expect(cfRes.status).toBe(200);
    expect(cfRes.body.state).toBe('CARRY_FORWARD');
  });

  test('[FULL] Carol requests changes after carry-forward → 409 CARRY_FORWARD_ALREADY_EXECUTED (§18 #17)', async () => {
    if (!planId) { test.skip(); return; }

    const plan = await getPlan(planId, carolToken);
    if (plan.body.state !== 'CARRY_FORWARD') { test.skip(); return; }

    const reviewRes = await submitReview(
      planId,
      'CHANGES_REQUESTED',
      'Please revise the delta reason for the QUEEN commit.',
      carolToken,
    );

    expect(reviewRes.status).toBe(409);
    const error = errorOf(reviewRes);
    expect(error.code).toBe('CARRY_FORWARD_ALREADY_EXECUTED');
  });

  test('[FULL] Carol can still approve after carry-forward is executed → 200 (§18 #17)', async () => {
    if (!planId) { test.skip(); return; }

    const plan = await getPlan(planId, carolToken);
    // Plan must still be in CARRY_FORWARD (from the failed CHANGES_REQUESTED above)
    if (plan.body.state !== 'CARRY_FORWARD') { test.skip(); return; }

    const reviewRes = await submitReview(
      planId,
      'APPROVED',
      'Good effort overall. Comments for next week: resolve the PagerDuty blocker.',
      carolToken,
    );

    expect(reviewRes.status).toBe(200);
    expect(reviewRes.body.decision).toBe('APPROVED');
    expect(reviewRes.body.weeklyPlanId).toBe(planId);

    // Verify plan reviewStatus is now APPROVED (state stays CARRY_FORWARD)
    const planAfter = await getPlan(planId, carolToken);
    expect(planAfter.status).toBe(200);
    expect(planAfter.body.reviewStatus).toBe('APPROVED');
    expect(planAfter.body.state).toBe('CARRY_FORWARD');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #12 — IC requests manager drill-down endpoint → 403 FORBIDDEN
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Permissions — IC Forbidden Manager Drill-Down (§18 #12)', () => {

  test('[FULL] IC (non-manager) requests GET /weeks/{weekStart}/plans/{userId} → 403 FORBIDDEN', async () => {
    // A fresh user has no direct reports and cannot access any user's plan via manager endpoint
    const nonManagerToken = tokenFor(freshUserId());

    const res = await api('GET', `/api/v1/weeks/${CURRENT_WEEK}/plans/${ALICE_ID}`, {
      token: nonManagerToken,
    });

    expect(res.status).toBe(403);
    const error = errorOf(res);
    expect(error.code).toBe('FORBIDDEN');
  });

  test('[FULL] Alice (IC) requests GET /weeks/{weekStart}/plans/{bobId} → 403 FORBIDDEN (Alice is not Bob\'s manager)', async () => {
    // Alice is not Bob's manager in the org graph (Carol is Bob's manager)
    const res = await api('GET', `/api/v1/weeks/${CURRENT_WEEK}/plans/${BOB_ID}`, {
      token: aliceToken,
    });

    expect(res.status).toBe(403);
    const error = errorOf(res);
    expect(error.code).toBe('FORBIDDEN');
  });

  test('[FULL] Carol (manager) CAN access Alice\'s plan via manager drill-down endpoint → 200', async () => {
    // Positive case: Carol IS Alice's manager, so access is allowed
    const res = await api('GET', `/api/v1/weeks/${CURRENT_WEEK}/plans/${ALICE_ID}`, {
      token: carolToken,
    });

    expect(res.status).toBe(200);
    expect(res.body.ownerUserId).toBe(ALICE_ID);
    expect(PLAN_STATES).toContain(res.body.state);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #13 — IC requests review endpoint → 403 FORBIDDEN
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Permissions — IC Forbidden Review (§18 #13)', () => {

  test('[FULL] IC (non-manager) calls POST /plans/{planId}/review → 403 FORBIDDEN', async () => {
    // A fresh user is not the manager of Bob → FORBIDDEN on the review endpoint
    const nonManagerToken = tokenFor(freshUserId());

    // Bob's current-week plan is in RECONCILED state; review auth check
    // happens before the state check, so any plan ID is sufficient
    const res = await submitReview(
      BOB_CURRENT_WEEK_PLAN_ID,
      'APPROVED',
      'Attempting review as IC',
      nonManagerToken,
    );

    expect(res.status).toBe(403);
    const error = errorOf(res);
    expect(error.code).toBe('FORBIDDEN');
  });

  test('[FULL] Alice (IC, Bob\'s peer) calls POST /plans/{planId}/review on Bob\'s plan → 403 FORBIDDEN', async () => {
    // Alice and Bob are peers (both report to Carol). Alice cannot review Bob's plan.
    const res = await submitReview(
      BOB_CURRENT_WEEK_PLAN_ID,
      'APPROVED',
      'Alice attempting peer review',
      aliceToken,
    );

    expect(res.status).toBe(403);
    const error = errorOf(res);
    expect(error.code).toBe('FORBIDDEN');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #14 — Manager PATCH on a report's commit → 403 FORBIDDEN
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Permissions — Manager Cannot PATCH Report\'s Commit (§18 #14)', () => {

  test('[FULL] Carol (manager) PATCH on Alice\'s commit → 403 FORBIDDEN', async () => {
    // Carol manages Alice but does not own Alice's commits
    // Ownership check fires before version/state checks, so any If-Match value works
    const res = await api('PATCH', `/api/v1/commits/${ALICE_KING_COMMIT_ID}`, {
      token: carolToken,
      ifMatch: 1,
      body: { title: 'Carol attempting override of Alice\'s commit' },
    });

    expect(res.status).toBe(403);
    const error = errorOf(res);
    expect(error.code).toBe('FORBIDDEN');
  });

  test('[FULL] A fresh user cannot PATCH any commit they don\'t own → 403 FORBIDDEN', async () => {
    const foreignToken = tokenFor(freshUserId());

    const res = await api('PATCH', `/api/v1/commits/${ALICE_KING_COMMIT_ID}`, {
      token: foreignToken,
      ifMatch: 1,
      body: { title: 'Foreign user override attempt' },
    });

    expect(res.status).toBe(403);
    const error = errorOf(res);
    expect(error.code).toBe('FORBIDDEN');
  });
});
