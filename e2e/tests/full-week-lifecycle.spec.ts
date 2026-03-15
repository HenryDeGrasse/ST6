/**
 * E2E integration tests that exercise the real backend API through a full
 * weekly plan lifecycle.
 *
 * These tests hit the running backend (not mocked) and cover:
 *   1. Create plan → add commits → lock
 *   2. Start reconciliation → fill actuals → submit reconciliation
 *   3. Carry forward incomplete commits to next week
 *   4. Late-lock path (DRAFT → RECONCILING directly)
 *   5. Edge cases: missing actuals, missing delta reasons, chess violations
 *   6. Manager review after reconciliation
 *
 * Prerequisites:
 *   - Backend running on localhost:8080 (./scripts/dev.sh --seed)
 *   - Seed data loaded (org, user, RCDO tree)
 *
 * Run:
 *   cd e2e && npx playwright test tests/full-week-lifecycle.spec.ts --project=api
 */
import { expect, test } from '@playwright/test';

// ── Constants ──────────────────────────────────────────────────────────────
const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080';
const ORG_ID = 'a0000000-0000-0000-0000-000000000001';

// The seed user — registered in the dev org graph as both IC and their own
// manager. Only used for the manager-review test where org graph matters.
const SEED_USER_ID = 'c0000000-0000-0000-0000-000000000001';

// RCDO outcome IDs from RcdoDevDataInitializer
const OUTCOME_ENTERPRISE_DEALS = 'e0000000-0000-0000-0000-000000000001';
const OUTCOME_API_UPTIME = 'e0000000-0000-0000-0000-000000000002';

// ── Per-run unique user IDs ────────────────────────────────────────────────
// Each test group gets a fresh user so it always starts with a clean slate.
// The DevRequestAuthenticator accepts any UUID as a valid user.
function freshUserId(): string {
  return crypto.randomUUID();
}

function tokenFor(userId: string, roles = 'IC'): string {
  return `Bearer dev:${userId}:${ORG_ID}:${roles}`;
}

function mondayOf(weeksFromCurrent = 0): string {
  const today = new Date();
  const day = today.getUTCDay();
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()));
  monday.setUTCDate(monday.getUTCDate() + diffToMonday + weeksFromCurrent * 7);
  return monday.toISOString().slice(0, 10);
}

// The backend allows creating plans for current week (0) and next week (+1).
const CURRENT_WEEK = mondayOf(0);
const NEXT_WEEK = mondayOf(1);

// ── API helpers ────────────────────────────────────────────────────────────

interface ApiResponse {
  status: number;
  body: Record<string, unknown>;
}

async function api(
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
    Authorization: options.token ?? tokenFor(SEED_USER_ID),
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
      body = JSON.parse(text);
    } catch {
      body = { _raw: text } as Record<string, unknown>;
    }
  }
  return { status: res.status, body };
}

async function getPlan(planId: string, token: string): Promise<ApiResponse> {
  return api('GET', `/api/v1/plans/${planId}`, { token });
}

async function getCommits(planId: string, token: string): Promise<{ status: number; body: Array<Record<string, unknown>> }> {
  const res = await api('GET', `/api/v1/plans/${planId}/commits`, { token });
  const body = Array.isArray(res.body) ? res.body : [];
  return { status: res.status, body };
}

async function createPlan(weekStart: string, token: string): Promise<ApiResponse> {
  return api('POST', `/api/v1/weeks/${weekStart}/plans`, { token });
}

async function createCommit(
  planId: string,
  commit: {
    title: string;
    chessPriority: string;
    category: string;
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

async function deleteCommit(commitId: string, token: string): Promise<ApiResponse> {
  return api('DELETE', `/api/v1/commits/${commitId}`, { token });
}

async function lockPlan(planId: string, version: number, token: string): Promise<ApiResponse> {
  return api('POST', `/api/v1/plans/${planId}/lock`, {
    ifMatch: version,
    idempotencyKey: crypto.randomUUID(),
    token,
  });
}

async function startReconciliation(planId: string, version: number, token: string): Promise<ApiResponse> {
  return api('POST', `/api/v1/plans/${planId}/start-reconciliation`, {
    ifMatch: version,
    idempotencyKey: crypto.randomUUID(),
    token,
  });
}

async function submitReconciliation(planId: string, version: number, token: string): Promise<ApiResponse> {
  return api('POST', `/api/v1/plans/${planId}/submit-reconciliation`, {
    ifMatch: version,
    idempotencyKey: crypto.randomUUID(),
    token,
  });
}

async function updateActual(
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

async function carryForward(
  planId: string,
  version: number,
  commitIds: string[],
  token: string,
): Promise<ApiResponse> {
  return api('POST', `/api/v1/plans/${planId}/carry-forward`, {
    ifMatch: version,
    idempotencyKey: crypto.randomUUID(),
    body: { commitIds },
    token,
  });
}

/**
 * Refreshes the in-memory RCDO cache so the staleness clock resets.
 * Required because the InMemoryRcdoClient marks data as stale after
 * 60 min (the dev data initializer sets it once at startup).
 */
async function refreshRcdo(token: string): Promise<void> {
  const res = await api('POST', '/api/v1/rcdo/refresh', { token });
  expect(res.status).toBe(200);
}

// ═══════════════════════════════════════════════════════════════════════════
// Test: Full golden-path lifecycle
//   Create → Add commits → Lock → Reconcile → Carry forward
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Full Week Lifecycle (API-level)', () => {
  const userId = freshUserId();
  const token = tokenFor(userId);
  let planId: string;
  let kingCommitId: string;
  let queenCommitId: string;
  let pawnCommitId: string;

  test.describe.configure({ mode: 'serial' });

  test('[SMOKE] Step 1: Create a new weekly plan', async () => {
    const res = await createPlan(CURRENT_WEEK, token);
    expect(res.status).toBe(201);
    expect(res.body.state).toBe('DRAFT');
    expect(res.body.orgId).toBe(ORG_ID);
    expect(res.body.ownerUserId).toBe(userId);
    planId = res.body.id as string;
    expect(planId).toBeTruthy();
  });

  test('[SMOKE] Step 2: Add commits (KING, QUEEN, PAWN)', async () => {
    // Add a KING commit linked to an RCDO outcome
    const king = await createCommit(planId, {
      title: 'Close enterprise deal with Acme Corp',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
      description: 'Finalize contract and onboarding plan',
      expectedResult: 'Signed contract by EOW',
      confidence: 0.85,
      tags: ['sales', 'enterprise'],
    }, token);
    expect(king.status).toBe(201);
    expect(king.body.title).toBe('Close enterprise deal with Acme Corp');
    expect(king.body.chessPriority).toBe('KING');
    kingCommitId = king.body.id as string;

    // Add a QUEEN commit linked to an RCDO outcome
    const queen = await createCommit(planId, {
      title: 'Improve API uptime monitoring',
      chessPriority: 'QUEEN',
      category: 'TECH_DEBT',
      outcomeId: OUTCOME_API_UPTIME,
      description: 'Set up alerting and dashboards',
      expectedResult: 'Monitoring dashboard live',
    }, token);
    expect(queen.status).toBe(201);
    queenCommitId = queen.body.id as string;

    // Add a PAWN commit (non-strategic, no outcome)
    const pawn = await createCommit(planId, {
      title: 'Office supplies and admin tasks',
      chessPriority: 'PAWN',
      category: 'OPERATIONS',
      nonStrategicReason: 'Routine administrative work',
    }, token);
    expect(pawn.status).toBe(201);
    pawnCommitId = pawn.body.id as string;

    // Verify all 3 commits exist
    const allCommits = await getCommits(planId, token);
    expect(allCommits.status).toBe(200);
    expect(allCommits.body).toHaveLength(3);
  });

  test('[SMOKE] Step 3: Lock the plan (DRAFT → LOCKED)', async () => {
    await refreshRcdo(token);

    const plan = await getPlan(planId, token);
    expect(plan.status).toBe(200);
    const version = plan.body.version as number;

    const locked = await lockPlan(planId, version, token);
    expect(locked.status).toBe(200);
    expect(locked.body.state).toBe('LOCKED');
    expect(locked.body.lockType).toBe('ON_TIME');

    // Verify RCDO snapshots were populated on the KING commit
    const commits = await getCommits(planId, token);
    const kingCommit = commits.body.find((c) => c.id === kingCommitId);
    expect(kingCommit).toBeTruthy();
    expect(kingCommit!.snapshotOutcomeId).toBe(OUTCOME_ENTERPRISE_DEALS);
    expect(kingCommit!.snapshotOutcomeName).toBeTruthy();
    expect(kingCommit!.snapshotRallyCryName).toBeTruthy();
  });

  test('[SMOKE] Step 4: Start reconciliation (LOCKED → RECONCILING)', async () => {
    const plan = await getPlan(planId, token);
    const version = plan.body.version as number;

    const recon = await startReconciliation(planId, version, token);
    expect(recon.status).toBe(200);
    expect(recon.body.state).toBe('RECONCILING');
  });

  test('[SMOKE] Step 5: Submit reconciliation fails without actuals', async () => {
    const plan = await getPlan(planId, token);
    const version = plan.body.version as number;

    const fail = await submitReconciliation(planId, version, token);
    expect(fail.status).toBe(422);
    const error = fail.body.error as Record<string, unknown>;
    expect(error).toBeTruthy();
    expect(error.code).toBe('MISSING_COMPLETION_STATUS');
  });

  test('[SMOKE] Step 6: Fill in actuals for all commits', async () => {
    const commits = await getCommits(planId, token);
    expect(commits.status).toBe(200);

    // KING: DONE
    const king = commits.body.find((c) => c.id === kingCommitId)!;
    const kingActual = await updateActual(kingCommitId, king.version as number, {
      actualResult: 'Contract signed with Acme Corp, onboarding scheduled',
      completionStatus: 'DONE',
      timeSpent: 35,
    }, token);
    expect(kingActual.status).toBe(200);
    expect(kingActual.body.completionStatus).toBe('DONE');

    // QUEEN: PARTIALLY (requires deltaReason)
    const queen = commits.body.find((c) => c.id === queenCommitId)!;
    const queenActual = await updateActual(queenCommitId, queen.version as number, {
      actualResult: 'Dashboard created, alerting rules 60% done',
      completionStatus: 'PARTIALLY',
      deltaReason: 'Blocked on infra team for PagerDuty integration',
      timeSpent: 20,
    }, token);
    expect(queenActual.status).toBe(200);
    expect(queenActual.body.completionStatus).toBe('PARTIALLY');

    // PAWN: DONE
    const pawn = commits.body.find((c) => c.id === pawnCommitId)!;
    const pawnActual = await updateActual(pawnCommitId, pawn.version as number, {
      actualResult: 'All admin tasks completed',
      completionStatus: 'DONE',
      timeSpent: 5,
    }, token);
    expect(pawnActual.status).toBe(200);
    expect(pawnActual.body.completionStatus).toBe('DONE');
  });

  test('[SMOKE] Step 7: Submit reconciliation succeeds (RECONCILING → RECONCILED)', async () => {
    const plan = await getPlan(planId, token);
    const version = plan.body.version as number;

    const reconciled = await submitReconciliation(planId, version, token);
    expect(reconciled.status).toBe(200);
    expect(reconciled.body.state).toBe('RECONCILED');
    expect(reconciled.body.reviewStatus).toBe('REVIEW_PENDING');
  });

  test('[SMOKE] Step 8: Carry forward incomplete commit to next week', async () => {
    const plan = await getPlan(planId, token);
    const version = plan.body.version as number;

    // Carry forward the QUEEN commit (was PARTIALLY done)
    const cf = await carryForward(planId, version, [queenCommitId], token);
    expect(cf.status).toBe(200);
    expect(cf.body.state).toBe('CARRY_FORWARD');

    // Verify next week's plan was created with the carried commit
    const nextPlan = await api('GET', `/api/v1/weeks/${NEXT_WEEK}/plans/me`, { token });
    expect(nextPlan.status).toBe(200);
    expect(nextPlan.body.state).toBe('DRAFT');

    const nextPlanId = nextPlan.body.id as string;
    const nextCommits = await getCommits(nextPlanId, token);
    expect(nextCommits.status).toBe(200);
    expect(nextCommits.body.length).toBeGreaterThanOrEqual(1);

    // Find the carried-forward commit
    const carried = nextCommits.body.find((c) => c.carriedFromCommitId === queenCommitId);
    expect(carried).toBeTruthy();
    expect(carried!.title).toBe('Improve API uptime monitoring');
    expect(carried!.chessPriority).toBe('QUEEN');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Test: Late-lock path (DRAFT → RECONCILING directly)
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Late Lock Path (API-level)', () => {
  const userId = freshUserId();
  const token = tokenFor(userId);
  let planId: string;
  let commitId: string;

  test.describe.configure({ mode: 'serial' });

  test('[FULL] Create plan and add a commit', async () => {
    const res = await createPlan(CURRENT_WEEK, token);
    expect(res.status).toBe(201);
    planId = res.body.id as string;

    // Add exactly 1 KING commit
    const king = await createCommit(planId, {
      title: 'Late-lock test commit',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
      expectedResult: 'Testing late lock path',
    }, token);
    expect(king.status).toBe(201);
    commitId = king.body.id as string;
  });

  test('[FULL] Start reconciliation from DRAFT (late lock)', async () => {
    await refreshRcdo(token);

    const plan = await getPlan(planId, token);
    const version = plan.body.version as number;
    expect(plan.body.state).toBe('DRAFT');

    const recon = await startReconciliation(planId, version, token);
    expect(recon.status).toBe(200);
    expect(recon.body.state).toBe('RECONCILING');
    expect(recon.body.lockType).toBe('LATE_LOCK');
  });

  test('[FULL] Complete reconciliation after late lock', async () => {
    // Fill in actuals
    const commits = await getCommits(planId, token);
    const commit = commits.body.find((c) => c.id === commitId)!;
    const actualRes = await updateActual(commitId, commit.version as number, {
      actualResult: 'Completed via late lock',
      completionStatus: 'DONE',
    }, token);
    expect(actualRes.status).toBe(200);

    // Submit
    const plan = await getPlan(planId, token);
    const reconciled = await submitReconciliation(planId, plan.body.version as number, token);
    expect(reconciled.status).toBe(200);
    expect(reconciled.body.state).toBe('RECONCILED');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Test: Validation edge cases
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Validation Edge Cases (API-level)', () => {

  test('[FULL] Cannot lock without exactly 1 KING', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const res = await createPlan(CURRENT_WEEK, token);
    expect(res.status).toBe(201);
    const planId = res.body.id as string;

    // Add 2 QUEEN commits, no KING → lock should fail
    await createCommit(planId, {
      title: 'Queen commit 1',
      chessPriority: 'QUEEN',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, token);
    await createCommit(planId, {
      title: 'Queen commit 2',
      chessPriority: 'QUEEN',
      category: 'DELIVERY',
      outcomeId: OUTCOME_API_UPTIME,
    }, token);

    await refreshRcdo(token);

    const plan = await getPlan(planId, token);
    const locked = await lockPlan(planId, plan.body.version as number, token);
    expect(locked.status).toBe(422);
    const error = locked.body.error as Record<string, unknown>;
    expect(error.code).toBe('CHESS_RULE_VIOLATION');
  });

  test('[FULL] Cannot submit reconciliation with missing delta reason on PARTIALLY', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const res = await createPlan(CURRENT_WEEK, token);
    expect(res.status).toBe(201);
    const planId = res.body.id as string;

    // Add 1 KING commit
    const king = await createCommit(planId, {
      title: 'Delta reason test',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, token);
    const kingId = king.body.id as string;

    // Lock then reconcile
    await refreshRcdo(token);
    let plan = await getPlan(planId, token);
    await lockPlan(planId, plan.body.version as number, token);

    plan = await getPlan(planId, token);
    await startReconciliation(planId, plan.body.version as number, token);

    // Set actual as PARTIALLY but WITHOUT deltaReason
    const commits = await getCommits(planId, token);
    const commit = commits.body.find((c) => c.id === kingId)!;

    const actualRes = await updateActual(kingId, commit.version as number, {
      actualResult: 'Partially done',
      completionStatus: 'PARTIALLY',
      // intentionally omitting deltaReason
    }, token);

    // If the actual is accepted without deltaReason, submit should catch it
    if (actualRes.status === 200) {
      plan = await getPlan(planId, token);
      const submitRes = await submitReconciliation(planId, plan.body.version as number, token);
      expect(submitRes.status).toBe(422);
      const error = submitRes.body.error as Record<string, unknown>;
      expect(error.code).toBe('MISSING_DELTA_REASON');
    } else {
      // If actual is rejected at update time, that's also valid behavior
      expect(actualRes.status).toBe(422);
    }
  });

  test('[FULL] Cannot lock plan that is already locked', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const res = await createPlan(CURRENT_WEEK, token);
    expect(res.status).toBe(201);
    const planId = res.body.id as string;

    await createCommit(planId, {
      title: 'Double lock test',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, token);

    await refreshRcdo(token);
    let plan = await getPlan(planId, token);
    const lockRes = await lockPlan(planId, plan.body.version as number, token);
    expect(lockRes.status).toBe(200);
    expect(lockRes.body.state).toBe('LOCKED');

    // Try to lock again — should fail
    plan = await getPlan(planId, token);
    const doubleLock = await lockPlan(planId, plan.body.version as number, token);
    expect(doubleLock.status).toBe(409);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Test: Optimistic lock conflict (§18 Criterion #8)
//   Two concurrent PATCHes with the same version → first succeeds, second → 409
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Optimistic Lock Conflict (API-level)', () => {
  const userId = freshUserId();
  const token = tokenFor(userId);
  let planId: string;
  let commitId: string;
  let commitVersion: number;

  test.describe.configure({ mode: 'serial' });

  test('[FULL] Setup: create plan and commit', async () => {
    const res = await createPlan(CURRENT_WEEK, token);
    expect(res.status).toBe(201);
    planId = res.body.id as string;

    const king = await createCommit(planId, {
      title: 'Optimistic lock test commit',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, token);
    expect(king.status).toBe(201);
    commitId = king.body.id as string;
    commitVersion = king.body.version as number;
  });

  test('[FULL] Concurrent PATCHes with same version → second gets 409', async () => {
    // First PATCH succeeds
    const first = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { title: 'Updated by first PATCH' },
    });
    expect(first.status).toBe(200);
    expect(first.body.title).toBe('Updated by first PATCH');

    // Second PATCH with the SAME stale version → 409 Conflict
    const second = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: commitVersion,
      body: { title: 'Updated by second PATCH' },
    });
    expect(second.status).toBe(409);
    const error = second.body.error as Record<string, unknown>;
    expect(error).toBeTruthy();
    expect(error.code).toBe('CONFLICT');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Test: CONFLICTING_LINK eager validation (§5)
//   A commit with both outcomeId AND nonStrategicReason → 422
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Conflicting Link Validation (API-level)', () => {

  test('[FULL] Create commit with both outcomeId and nonStrategicReason → 422', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const res = await createPlan(CURRENT_WEEK, token);
    expect(res.status).toBe(201);
    const planId = res.body.id as string;

    const commit = await createCommit(planId, {
      title: 'Conflicting link test',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
      nonStrategicReason: 'This should be rejected',
    }, token);
    expect(commit.status).toBe(422);
    const error = commit.body.error as Record<string, unknown>;
    expect(error).toBeTruthy();
    expect(error.code).toBe('CONFLICTING_LINK');
  });

  test('[FULL] Update commit to add nonStrategicReason when outcomeId exists → 422', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const res = await createPlan(CURRENT_WEEK, token);
    expect(res.status).toBe(201);
    const planId = res.body.id as string;

    // Create a commit with outcomeId only
    const commit = await createCommit(planId, {
      title: 'Start with outcome',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, token);
    expect(commit.status).toBe(201);
    const commitId = commit.body.id as string;
    const version = commit.body.version as number;

    // Try to PATCH in a nonStrategicReason → should conflict
    const update = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: version,
      body: { nonStrategicReason: 'Trying to set both' },
    });
    expect(update.status).toBe(422);
    const error = update.body.error as Record<string, unknown>;
    expect(error).toBeTruthy();
    expect(error.code).toBe('CONFLICTING_LINK');
  });

  test('[FULL] FIELD_FROZEN: PATCH title on locked plan → 409', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const res = await createPlan(CURRENT_WEEK, token);
    expect(res.status).toBe(201);
    const planId = res.body.id as string;

    const commit = await createCommit(planId, {
      title: 'Freeze test',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, token);
    expect(commit.status).toBe(201);
    const commitId = commit.body.id as string;

    // Lock the plan
    await refreshRcdo(token);
    let plan = await getPlan(planId, token);
    const lockRes = await lockPlan(planId, plan.body.version as number, token);
    expect(lockRes.status).toBe(200);

    // Try to change title on locked commit → FIELD_FROZEN 409
    const commits = await getCommits(planId, token);
    const lockedCommit = commits.body.find((c) => c.id === commitId)!;
    const frozenPatch = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token,
      ifMatch: lockedCommit.version as number,
      body: { title: 'Should be rejected' },
    });
    expect(frozenPatch.status).toBe(409);
    const error = frozenPatch.body.error as Record<string, unknown>;
    expect(error).toBeTruthy();
    expect(error.code).toBe('FIELD_FROZEN');
  });

  test('[FULL] Manager PATCH on report\'s commit → 403', async () => {
    const icUserId = freshUserId();
    const icToken = tokenFor(icUserId);
    const managerUserId = freshUserId();
    const managerToken = tokenFor(managerUserId, 'IC,MANAGER');

    const res = await createPlan(CURRENT_WEEK, icToken);
    expect(res.status).toBe(201);
    const planId = res.body.id as string;

    const commit = await createCommit(planId, {
      title: 'IC commit',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, icToken);
    expect(commit.status).toBe(201);
    const commitId = commit.body.id as string;
    const version = commit.body.version as number;

    // Manager tries to PATCH the IC's commit → 403
    const managerPatch = await api('PATCH', `/api/v1/commits/${commitId}`, {
      token: managerToken,
      ifMatch: version,
      body: { title: 'Manager override' },
    });
    expect(managerPatch.status).toBe(403);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Test: Manager review after reconciliation
//
// Uses the SEED_USER_ID because the dev org graph initializer registers
// that user as their own manager (self-manages for demo purposes).
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Review (API-level)', () => {
  const icToken = tokenFor(SEED_USER_ID);
  const managerToken = tokenFor(SEED_USER_ID, 'IC,MANAGER');

  test.describe.configure({ mode: 'serial' });

  test('[FULL] Full lifecycle with manager review', async () => {
    // Use NEXT_WEEK to avoid colliding with the seed user's current-week plan
    const res = await createPlan(NEXT_WEEK, icToken);
    expect([200, 201]).toContain(res.status);
    const planId = res.body.id as string;

    // If plan already exists from a previous run, ensure it's usable
    if (res.body.state !== 'DRAFT') {
      // Plan from a previous run is in a terminal state — skip this test
      test.skip();
      return;
    }

    // Delete any existing commits (from carry-forward or previous run)
    const existing = await getCommits(planId, icToken);
    for (const c of existing.body) {
      await deleteCommit(c.id as string, icToken);
    }

    // Add a commit
    const king = await createCommit(planId, {
      title: 'Manager review test commit',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
    }, icToken);
    expect(king.status).toBe(201);

    // Lock
    await refreshRcdo(icToken);
    let plan = await getPlan(planId, icToken);
    const lockRes = await lockPlan(planId, plan.body.version as number, icToken);
    expect(lockRes.status).toBe(200);

    // Start reconciliation
    plan = await getPlan(planId, icToken);
    const reconRes = await startReconciliation(planId, plan.body.version as number, icToken);
    expect(reconRes.status).toBe(200);

    // Fill actuals
    const commits = await getCommits(planId, icToken);
    const commit = commits.body[0];
    const actualRes = await updateActual(commit.id as string, commit.version as number, {
      actualResult: 'Everything delivered',
      completionStatus: 'DONE',
      timeSpent: 40,
    }, icToken);
    expect(actualRes.status).toBe(200);

    // Submit reconciliation
    plan = await getPlan(planId, icToken);
    const reconciled = await submitReconciliation(planId, plan.body.version as number, icToken);
    expect(reconciled.status).toBe(200);
    expect(reconciled.body.state).toBe('RECONCILED');
    expect(reconciled.body.reviewStatus).toBe('REVIEW_PENDING');

    // Manager submits review
    const review = await api('POST', `/api/v1/plans/${planId}/review`, {
      token: managerToken,
      body: {
        decision: 'APPROVED',
        comments: 'Great work this week!',
      },
    });
    expect(review.status).toBe(200);
    expect(review.body.decision).toBe('APPROVED');
  });
});
