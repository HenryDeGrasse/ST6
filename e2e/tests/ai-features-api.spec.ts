/**
 * API-level tests for AI-assisted features (PRD §18 #18, #19, #20).
 *
 * Covers:
 *   §18 #18 — RCDO auto-suggest:
 *     POST /api/v1/ai/suggest-rcdo returns suggestions with valid outcomeIds
 *     from the seeded RCDO hierarchy; response matches the JSON schema
 *     (status, suggestions[]{outcomeId, confidence, rationale, rallyCryName,
 *     objectiveName, outcomeName}).
 *
 *   §18 #19 — AI fallback:
 *     When the RCDO tree is empty (fresh org with no seed data), the endpoint
 *     returns 200 with status='unavailable' and an empty suggestions array,
 *     demonstrating graceful degradation.
 *
 *   §18 #20 — Reconciliation draft (beta):
 *     POST /api/v1/ai/draft-reconciliation returns suggested statuses for all
 *     commits in the plan. Each draft item includes commitId, suggestedStatus,
 *     suggestedDeltaReason, and suggestedActualResult.
 *
 *   Manager insights:
 *     POST /api/v1/ai/manager-insights returns a headline string and an
 *     insights array when a manager with direct reports requests it.
 *
 *   Rate-limiting boundary:
 *     Multiple rapid requests from the same user (well below the 20/min limit)
 *     all succeed — no 429 returned during normal test workloads.
 *
 * Note: Dev environment uses StubLlmClient so responses are canned/
 * deterministic. Tests verify the API contract, not LLM quality. All three
 * AI feature flags are enabled in the local and dev profiles.
 *
 * Prerequisite: Backend running on localhost:8080 (./scripts/dev.sh --seed)
 *
 * Run:
 *   cd e2e && npx playwright test tests/ai-features-api.spec.ts --project=api
 */
import { expect, test } from '@playwright/test';
import {
  API_BASE,
  ORG_ID,
  OUTCOME_API_UPTIME,
  OUTCOME_ENTERPRISE_DEALS,
  api,
  createCommit,
  createPlan,
  freshUserId,
  getCommits,
  mondayOf,
  type ApiResponse,
} from './helpers';

/**
 * A fresh org ID with no RCDO seed data — used to trigger the "unavailable"
 * fallback path (empty RCDO tree → 200 with status='unavailable').
 */
const UNSEEDED_ORG_ID = 'f0000000-0000-0000-0000-000000000099';

/**
 * Seeded personas from OrgGraphDevDataInitializer.
 * Carol is the manager with Alice and Bob as direct reports.
 */
const CAROL_ID = 'c0000000-0000-0000-0000-000000000001'; // Manager

/**
 * All RCDO outcome IDs from RcdoDevDataInitializer (seeded for ORG_ID).
 * Any outcomeId returned by the AI must belong to this set.
 */
const VALID_OUTCOME_IDS = new Set([
  'e0000000-0000-0000-0000-000000000001', // Close 10 enterprise deals in Q1
  'e0000000-0000-0000-0000-000000000002', // Achieve 99.9% API uptime
  '30000000-0000-0000-0000-000000000002', // Launch enterprise demo environment
  '30000000-0000-0000-0000-000000000003', // Reduce sales cycle by 20%
  '30000000-0000-0000-0000-000000000004', // Sign 3 healthcare pilot customers
  '30000000-0000-0000-0000-000000000005', // Complete SOC2 Type II certification
  '30000000-0000-0000-0000-000000000007', // Reduce deploy-to-production time to < 15 min
  '30000000-0000-0000-0000-000000000008', // Increase unit test coverage to 85%
  '30000000-0000-0000-0000-000000000009', // Every engineer presents at a tech talk
  '30000000-0000-0000-0000-000000000010', // Implement weekly commitments module
  '30000000-0000-0000-0000-000000000011', // Launch proactive health-score alerting
  '30000000-0000-0000-0000-000000000012', // Achieve NPS > 60
]);

const CONFIDENCE_MIN = 0.0;
const CONFIDENCE_MAX = 1.0;
const VALID_SUGGESTED_STATUSES = new Set(['DONE', 'PARTIALLY', 'NOT_DONE', 'DROPPED']);
const VALID_SEVERITIES = new Set(['INFO', 'WARNING', 'POSITIVE']);

function tokenFor(userId: string, orgId = ORG_ID, roles = 'IC'): string {
  return `Bearer dev:${userId}:${orgId}:${roles}`;
}

const CURRENT_WEEK = mondayOf(0);

/** Calls POST /api/v1/ai/suggest-rcdo */
async function suggestRcdo(
  title: string,
  description: string | undefined,
  token: string,
): Promise<ApiResponse> {
  return api('POST', '/api/v1/ai/suggest-rcdo', {
    token,
    body: { title, description },
  });
}

/** Calls POST /api/v1/ai/draft-reconciliation */
async function draftReconciliation(planId: string, token: string): Promise<ApiResponse> {
  return api('POST', '/api/v1/ai/draft-reconciliation', {
    token,
    body: { planId },
  });
}

/** Calls POST /api/v1/ai/manager-insights */
async function managerInsights(weekStart: string, token: string): Promise<ApiResponse> {
  return api('POST', '/api/v1/ai/manager-insights', {
    token,
    body: { weekStart },
  });
}

// ═══════════════════════════════════════════════════════════════════════════
// §18 #18 — RCDO Auto-Suggest: happy path and schema validation
// ═══════════════════════════════════════════════════════════════════════════

test.describe('RCDO Auto-Suggest — Happy Path and Schema (§18 #18)', () => {

  test('[FULL] POST /ai/suggest-rcdo returns 200 with status="ok" and non-empty suggestions', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    // Use a title closely related to enterprise deals — the stub LLM and
    // CandidateSelector both process the seed RCDO tree and return the
    // first-matched outcome (e0000000-…-001: "Close 10 enterprise deals in Q1")
    const res = await suggestRcdo(
      'Close the enterprise deal with Acme Corp this week',
      'Finalize contract negotiation and kick off onboarding',
      token,
    );

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');
    expect(Array.isArray(res.body.suggestions)).toBe(true);
    const suggestions = res.body.suggestions as Array<Record<string, unknown>>;
    expect(suggestions.length).toBeGreaterThan(0);
  });

  test('[FULL] Each suggestion in suggest-rcdo response has the required fields (§18 #18 schema)', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    const res = await suggestRcdo(
      'Improve API uptime and reliability monitoring setup',
      'Set up alerting, dashboards, and on-call rotation',
      token,
    );

    expect(res.status).toBe(200);
    const suggestions = res.body.suggestions as Array<Record<string, unknown>>;
    expect(suggestions.length).toBeGreaterThan(0);

    for (const suggestion of suggestions) {
      // outcomeId must be present and a non-empty string
      expect(typeof suggestion.outcomeId).toBe('string');
      expect((suggestion.outcomeId as string).length).toBeGreaterThan(0);

      // confidence must be a number in [0, 1]
      expect(typeof suggestion.confidence).toBe('number');
      expect(suggestion.confidence as number).toBeGreaterThanOrEqual(CONFIDENCE_MIN);
      expect(suggestion.confidence as number).toBeLessThanOrEqual(CONFIDENCE_MAX);

      // rationale must be a non-empty string
      expect(typeof suggestion.rationale).toBe('string');
      expect((suggestion.rationale as string).length).toBeGreaterThan(0);

      // RCDO hierarchy fields must be present
      expect(typeof suggestion.rallyCryName).toBe('string');
      expect((suggestion.rallyCryName as string).length).toBeGreaterThan(0);
      expect(typeof suggestion.objectiveName).toBe('string');
      expect((suggestion.objectiveName as string).length).toBeGreaterThan(0);
      expect(typeof suggestion.outcomeName).toBe('string');
      expect((suggestion.outcomeName as string).length).toBeGreaterThan(0);
    }
  });

  test('[FULL] All outcomeIds in suggest-rcdo response are valid members of the seeded RCDO hierarchy (§18 #18)', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    // Use a generic business title so the candidate selector includes many outcomes
    const res = await suggestRcdo(
      'Drive strategic business growth across the company',
      'Coordinate cross-functional work aligned with company OKRs',
      token,
    );

    expect(res.status).toBe(200);
    const suggestions = res.body.suggestions as Array<Record<string, unknown>>;
    expect(suggestions.length).toBeGreaterThan(0);

    // Every returned outcomeId must be from the known seed RCDO hierarchy.
    // The ResponseValidator rejects any hallucinated ID that is not in the
    // candidate set, which in dev is the full seeded tree.
    for (const suggestion of suggestions) {
      const outcomeId = suggestion.outcomeId as string;
      expect(VALID_OUTCOME_IDS.has(outcomeId)).toBe(true);
    }
  });

  test('[FULL] suggest-rcdo response is sorted by confidence descending', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    // With the stub LLM, only one suggestion is returned, so this test
    // verifies the sort invariant when multiple suggestions are present
    // (and also that the single-suggestion case is valid).
    const res = await suggestRcdo(
      'Complete SOC2 Type II certification and healthcare expansion',
      'Work with legal and security team on audit preparation',
      token,
    );

    expect(res.status).toBe(200);
    const suggestions = res.body.suggestions as Array<Record<string, unknown>>;
    expect(suggestions.length).toBeGreaterThan(0);

    // Verify that confidence values are in descending order
    for (let i = 0; i < suggestions.length - 1; i++) {
      const current = suggestions[i].confidence as number;
      const next = suggestions[i + 1].confidence as number;
      expect(current).toBeGreaterThanOrEqual(next);
    }
  });

  test('[FULL] suggest-rcdo works with title only (no description)', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    // description is optional — verify the endpoint handles null/undefined gracefully
    const res = await suggestRcdo('Weekly engineering tech talk preparation', undefined, token);

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');
    expect(Array.isArray(res.body.suggestions)).toBe(true);
    // The stub LLM can still return a suggestion with just the title
    const suggestions = res.body.suggestions as Array<Record<string, unknown>>;
    expect(suggestions.length).toBeGreaterThanOrEqual(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #19 — AI fallback: graceful degradation when RCDO data unavailable
// ═══════════════════════════════════════════════════════════════════════════

test.describe('AI Fallback — RCDO Unavailable (§18 #19)', () => {

  test('[FULL] suggest-rcdo with unseeded org returns 200 with status="unavailable" and empty suggestions', async () => {
    // Use an org that has no RCDO seed data; DefaultAiSuggestionService checks
    // tree.rallyCries().isEmpty() and returns SuggestionResult.unavailable()
    // before calling the LLM. This exercises the graceful-degradation contract.
    const userId = freshUserId();
    const unseededToken = tokenFor(userId, UNSEEDED_ORG_ID);

    const res = await suggestRcdo(
      'Close enterprise deals with major accounts',
      'Strategic sales initiative for Q1',
      unseededToken,
    );

    // Must return 200 (not 5xx) — graceful degradation contract (PRD §4 §18 #19)
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('unavailable');
    expect(Array.isArray(res.body.suggestions)).toBe(true);
    expect((res.body.suggestions as unknown[]).length).toBe(0);
  });

  test('[FULL] draft-reconciliation with non-existent plan returns 200 with status="unavailable"', async () => {
    // Providing a planId that does not exist triggers the planExists() check,
    // which returns ReconciliationDraftResult.unavailable() instead of throwing.
    const userId = freshUserId();
    const token = tokenFor(userId);

    const nonExistentPlanId = crypto.randomUUID();
    const res = await draftReconciliation(nonExistentPlanId, token);

    // Must return 200 — graceful degradation when data is unavailable
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('unavailable');
    expect(Array.isArray(res.body.drafts)).toBe(true);
    expect((res.body.drafts as unknown[]).length).toBe(0);
  });

  test('[FULL] manager-insights with no direct reports returns 200 with "ok" status and default message', async () => {
    // A fresh user with MANAGER role but no org-graph direct reports gets a
    // response with status="ok" and the default "no data" headline.
    // This is distinct from "unavailable" (which signals LLM errors) —
    // it correctly conveys that the system worked but there was no data.
    const freshManagerId = freshUserId();
    const managerToken = tokenFor(freshManagerId, ORG_ID, 'IC,MANAGER');

    const res = await managerInsights(CURRENT_WEEK, managerToken);

    expect(res.status).toBe(200);
    // No LLM call is made when teamMembers is empty — service returns "ok" with
    // a default no-data message rather than "unavailable"
    expect(res.body.status).toBe('ok');
    expect(typeof res.body.headline).toBe('string');
    expect((res.body.headline as string).length).toBeGreaterThan(0);
    expect(Array.isArray(res.body.insights)).toBe(true);
    // No insights when there is no team data
    expect((res.body.insights as unknown[]).length).toBe(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// §18 #20 — Reconciliation draft (beta):
//   POST /api/v1/ai/draft-reconciliation returns suggested statuses for all
//   commits in the plan.
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Reconciliation Draft — Beta Feature (§18 #20)', () => {
  let planId: string;
  let kingCommitId: string;
  let queenCommitId: string;
  let userId: string;
  let token: string;

  test.describe.configure({ mode: 'serial' });

  test('[FULL] Setup: create plan with 2 commits (KING + QUEEN) for draft-reconciliation', async () => {
    userId = freshUserId();
    token = tokenFor(userId);

    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    planId = planRes.body.id as string;
    expect(planId).toBeTruthy();

    const king = await createCommit(planId, {
      title: 'Close enterprise deal with Acme Corp',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
      expectedResult: 'Signed contract by EOW',
    }, token);
    expect(king.status).toBe(201);
    kingCommitId = king.body.id as string;

    const queen = await createCommit(planId, {
      title: 'Improve API uptime monitoring dashboard',
      chessPriority: 'QUEEN',
      category: 'TECH_DEBT',
      outcomeId: OUTCOME_API_UPTIME,
      expectedResult: 'Dashboard live with PagerDuty integration',
    }, token);
    expect(queen.status).toBe(201);
    queenCommitId = queen.body.id as string;
  });

  test('[FULL] POST /ai/draft-reconciliation returns 200 with status="ok" and drafts for all commits (§18 #20)', async () => {
    if (!planId || !kingCommitId || !queenCommitId) { test.skip(); return; }

    const res = await draftReconciliation(planId, token);

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');
    expect(Array.isArray(res.body.drafts)).toBe(true);
    const drafts = res.body.drafts as Array<Record<string, unknown>>;
    // The stub LLM creates a draft for each commit in the plan
    expect(drafts.length).toBeGreaterThan(0);
  });

  test('[FULL] Each draft item includes commitId, suggestedStatus, and suggestedActualResult', async () => {
    if (!planId || !kingCommitId || !queenCommitId) { test.skip(); return; }

    const res = await draftReconciliation(planId, token);

    expect(res.status).toBe(200);
    const drafts = res.body.drafts as Array<Record<string, unknown>>;
    expect(drafts.length).toBeGreaterThan(0);

    for (const draft of drafts) {
      // commitId must be present and a non-empty string
      expect(typeof draft.commitId).toBe('string');
      expect((draft.commitId as string).length).toBeGreaterThan(0);

      // suggestedStatus must be one of the valid completion statuses
      expect(typeof draft.suggestedStatus).toBe('string');
      expect(VALID_SUGGESTED_STATUSES.has(draft.suggestedStatus as string)).toBe(true);

      // suggestedActualResult must be present and non-empty
      expect(typeof draft.suggestedActualResult).toBe('string');
      expect((draft.suggestedActualResult as string).length).toBeGreaterThan(0);

      // suggestedDeltaReason may be null (when status is DONE)
      // but if present it must be a string
      if (draft.suggestedDeltaReason !== null && draft.suggestedDeltaReason !== undefined) {
        expect(typeof draft.suggestedDeltaReason).toBe('string');
      }
    }
  });

  test('[FULL] All commitIds in draft-reconciliation response are from the plan (§18 #20)', async () => {
    if (!planId || !kingCommitId || !queenCommitId) { test.skip(); return; }

    // First, get the actual commit IDs from the plan
    const commits = await getCommits(planId, token);
    expect(commits.status).toBe(200);
    const actualCommitIds = new Set(commits.body.map((c) => c.id as string));

    const res = await draftReconciliation(planId, token);

    expect(res.status).toBe(200);
    const drafts = res.body.drafts as Array<Record<string, unknown>>;
    expect(drafts.length).toBeGreaterThan(0);

    // Every commitId returned in drafts must be a real commit in this plan
    // (ResponseValidator rejects IDs not in the candidate set)
    for (const draft of drafts) {
      const commitId = draft.commitId as string;
      expect(actualCommitIds.has(commitId)).toBe(true);
    }
  });

  test('[FULL] Stub LLM suggests DONE status for all commits in draft-reconciliation', async () => {
    if (!planId || !kingCommitId || !queenCommitId) { test.skip(); return; }

    // The StubLlmClient deterministically returns "DONE" for every commit
    // when progress notes are absent (default behavior per implementation).
    const res = await draftReconciliation(planId, token);

    expect(res.status).toBe(200);
    const drafts = res.body.drafts as Array<Record<string, unknown>>;
    expect(drafts.length).toBeGreaterThan(0);

    // In the dev stub, all commits are suggested as DONE
    for (const draft of drafts) {
      expect(draft.suggestedStatus).toBe('DONE');
    }
  });

  test('[FULL] draft-reconciliation is idempotent — repeated call returns same result', async () => {
    if (!planId) { test.skip(); return; }

    const res1 = await draftReconciliation(planId, token);
    const res2 = await draftReconciliation(planId, token);

    expect(res1.status).toBe(200);
    expect(res2.status).toBe(200);

    // Status must match on both calls
    expect(res1.body.status).toBe(res2.body.status);

    const drafts1 = res1.body.drafts as Array<Record<string, unknown>>;
    const drafts2 = res2.body.drafts as Array<Record<string, unknown>>;

    // Same number of drafts on repeated calls (cache hit on second call)
    expect(drafts1.length).toBe(drafts2.length);

    // Same commitIds in same order (cached response is deterministic)
    for (let i = 0; i < drafts1.length; i++) {
      expect(drafts1[i].commitId).toBe(drafts2[i].commitId);
      expect(drafts1[i].suggestedStatus).toBe(drafts2[i].suggestedStatus);
    }
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Manager insights — POST /api/v1/ai/manager-insights
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Manager Insights — Beta Feature', () => {

  test('[FULL] Manager with direct reports gets 200 with status="ok", headline, and insights array', async () => {
    // Carol (CAROL_ID) is the seeded manager with Alice and Bob as direct reports.
    // The manager-insights endpoint aggregates their plan data and calls the
    // StubLlmClient, which returns a deterministic headline + 2 insights.
    const carolToken = tokenFor(CAROL_ID, ORG_ID, 'IC,MANAGER');

    const res = await managerInsights(CURRENT_WEEK, carolToken);

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');

    // headline must be a non-empty string
    expect(typeof res.body.headline).toBe('string');
    expect((res.body.headline as string).length).toBeGreaterThan(0);

    // insights must be an array
    expect(Array.isArray(res.body.insights)).toBe(true);
  });

  test('[FULL] Each manager insight has title, detail, and severity fields', async () => {
    const carolToken = tokenFor(CAROL_ID, ORG_ID, 'IC,MANAGER');

    const res = await managerInsights(CURRENT_WEEK, carolToken);

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');
    const insights = res.body.insights as Array<Record<string, unknown>>;
    expect(insights.length).toBeGreaterThan(0);

    for (const insight of insights) {
      // title must be a non-empty string
      expect(typeof insight.title).toBe('string');
      expect((insight.title as string).length).toBeGreaterThan(0);

      // detail must be a non-empty string
      expect(typeof insight.detail).toBe('string');
      expect((insight.detail as string).length).toBeGreaterThan(0);

      // severity must be one of the valid values
      expect(typeof insight.severity).toBe('string');
      expect(VALID_SEVERITIES.has(insight.severity as string)).toBe(true);
    }
  });

  test('[FULL] Stub LLM returns expected headline and 2 insights for manager with data', async () => {
    // The StubLlmClient returns a fixed response when the ASSISTANT message
    // contains "Manager dashboard context". This test verifies the contract
    // that 2 insights with INFO and WARNING severities are returned.
    const carolToken = tokenFor(CAROL_ID, ORG_ID, 'IC,MANAGER');

    const res = await managerInsights(CURRENT_WEEK, carolToken);

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');

    const insights = res.body.insights as Array<Record<string, unknown>>;
    // The stub always returns 2 insights
    expect(insights.length).toBe(2);

    // Verify the stub's known severity values
    const severities = insights.map((i) => i.severity as string);
    expect(severities).toContain('INFO');
    expect(severities).toContain('WARNING');
  });

  test('[FULL] manager-insights is idempotent — repeated call returns same result (cache hit)', async () => {
    const carolToken = tokenFor(CAROL_ID, ORG_ID, 'IC,MANAGER');

    const res1 = await managerInsights(CURRENT_WEEK, carolToken);
    const res2 = await managerInsights(CURRENT_WEEK, carolToken);

    expect(res1.status).toBe(200);
    expect(res2.status).toBe(200);
    expect(res1.body.status).toBe(res2.body.status);
    expect(res1.body.headline).toBe(res2.body.headline);

    const insights1 = res1.body.insights as Array<Record<string, unknown>>;
    const insights2 = res2.body.insights as Array<Record<string, unknown>>;
    expect(insights1.length).toBe(insights2.length);
  });

  test('[FULL] manager-insights requires MANAGER role — IC-only token is rejected or returns no-data response', async () => {
    // A plain IC user (no MANAGER role) calling manager-insights gets either:
    //   • 403 FORBIDDEN if the endpoint enforces the MANAGER role
    //   • 200 OK with empty insights (no direct reports in org graph)
    // Both outcomes are acceptable — the important thing is no 5xx.
    const icUserId = freshUserId();
    const icToken = tokenFor(icUserId);

    const res = await managerInsights(CURRENT_WEEK, icToken);

    // Must not be a server error
    expect(res.status).toBeLessThan(500);

    if (res.status === 200) {
      // If allowed, the response should be a valid envelope
      expect(['ok', 'unavailable']).toContain(res.body.status);
      expect(Array.isArray(res.body.insights)).toBe(true);
    }
    // 403 is also acceptable — role enforcement is implementation-specific
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Rate limiting boundary
// ═══════════════════════════════════════════════════════════════════════════

test.describe('Rate Limiting Boundary', () => {

  test('[FULL] Multiple rapid suggest-rcdo requests from the same user do not trigger rate limiting', async () => {
    // The rate limit is 20 requests/min per user. We send 5 rapid requests —
    // well within the limit — to verify normal operation is not disrupted.
    const userId = freshUserId();
    const token = tokenFor(userId);

    const REQUEST_COUNT = 5;
    const results = await Promise.all(
      Array.from({ length: REQUEST_COUNT }, () =>
        suggestRcdo('Enterprise deal acceleration initiative', undefined, token),
      ),
    );

    // All requests must succeed (200 OK, not 429 Too Many Requests)
    for (const res of results) {
      expect(res.status).toBe(200);
      expect(res.body.status).toBe('ok');
    }
  });

  test('[FULL] Multiple rapid draft-reconciliation requests do not trigger rate limiting', async () => {
    const userId = freshUserId();
    const token = tokenFor(userId);

    // Create a plan with commits to draft reconciliation against
    const planRes = await createPlan(CURRENT_WEEK, token);
    expect(planRes.status).toBe(201);
    const planId = planRes.body.id as string;

    await createCommit(planId, {
      title: 'Rate limit test: KING commit',
      chessPriority: 'KING',
      category: 'DELIVERY',
      outcomeId: OUTCOME_ENTERPRISE_DEALS,
      expectedResult: 'Rate limit boundary verified',
    }, token);

    const REQUEST_COUNT = 3;
    const results = await Promise.all(
      Array.from({ length: REQUEST_COUNT }, () => draftReconciliation(planId, token)),
    );

    // All requests must succeed (200 OK — the cache absorbs repeat calls)
    for (const res of results) {
      expect(res.status).toBe(200);
    }
  });

  test('[FULL] Multiple rapid manager-insights requests do not trigger rate limiting', async () => {
    // Carol has direct reports, so this also verifies the LLM path isn't broken
    // by concurrent invocations in the dev environment.
    const carolToken = tokenFor(CAROL_ID, ORG_ID, 'IC,MANAGER');

    const REQUEST_COUNT = 3;
    const results = await Promise.all(
      Array.from({ length: REQUEST_COUNT }, () => managerInsights(CURRENT_WEEK, carolToken)),
    );

    for (const res of results) {
      expect(res.status).toBe(200);
      expect(['ok', 'unavailable']).toContain(res.body.status);
    }
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Authentication enforcement on AI endpoints
// ═══════════════════════════════════════════════════════════════════════════

test.describe('AI Endpoints — Authentication', () => {

  test('[FULL] suggest-rcdo without Authorization header → 401', async () => {
    const res = await fetch(`${API_BASE}/api/v1/ai/suggest-rcdo`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'Test commit', description: 'No auth' }),
    });

    // The backend must reject unauthenticated requests
    expect(res.status).toBe(401);
  });

  test('[FULL] draft-reconciliation without Authorization header → 401', async () => {
    const res = await fetch(`${API_BASE}/api/v1/ai/draft-reconciliation`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ planId: crypto.randomUUID() }),
    });

    expect(res.status).toBe(401);
  });

  test('[FULL] manager-insights without Authorization header → 401', async () => {
    const res = await fetch(`${API_BASE}/api/v1/ai/manager-insights`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ weekStart: CURRENT_WEEK }),
    });

    expect(res.status).toBe(401);
  });
});
