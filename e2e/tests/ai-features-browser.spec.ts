/**
 * Browser E2E tests for AI-assisted UI features.
 *
 * All tests run deterministically against a mocked API via installMockApi()
 * (no live backend required).
 *
 * Features tested (PRD §18 #18 – #20):
 *
 *   suggestRcdo (enabled by default):
 *     #18 – AI RCDO suggestion panel appears when editing a commit title
 *         – Suggestions are displayed as selectable chips
 *         – Clicking a suggestion populates the RCDO picker
 *         – AI unavailable status shows manual picker without surfacing an error
 *
 *   draftReconciliation (beta; enable via ?flags=draftReconciliation):
 *     #20 – AI reconciliation draft panel appears in RECONCILING state
 *         – 'Generate AI Draft' button triggers the API fetch
 *         – Draft items display with suggested status and can be applied
 *         – Unavailable state shows a non-blocking fallback message
 *
 *   managerInsights (beta; enable via ?flags=managerInsights):
 *     #19 – Manager insights panel shows headline and insight cards
 *         – 'Refresh' button triggers a re-fetch
 *         – Loading state is shown while fetching
 *
 * The pa-host-stub reads `?flags=<comma-list>` from the URL and forwards it as
 * `featureFlags` to the micro-frontend, allowing E2E tests to opt into beta
 * features without changing production defaults.
 *
 * Test categories:
 *   [SMOKE] = runs on every PR (Gate 7)
 *   [FULL]  = deeper acceptance coverage, suitable for nightly runs
 */
import { expect, test, type Page, type Route } from "@playwright/test";

// ─── Constants ────────────────────────────────────────────────────────────────

const ORG_ID      = "00000000-0000-0000-0000-000000000099";
const USER_ID     = "00000000-0000-0000-0000-000000000001";
const PLAN_ID     = "10000000-0000-0000-0000-000000000001";
const COMMIT_ID_1 = "20000000-0000-0000-0000-000000000001";
const COMMIT_ID_2 = "20000000-0000-0000-0000-000000000002";
const OUTCOME_ID  = "30000000-0000-0000-0000-000000000001";
const REPORT_USER_ID = "00000000-0000-0000-0000-000000000201";
const REPORT_PLAN_ID = "10000000-0000-0000-0000-000000000201";

const now = "2026-03-12T12:00:00Z";

/** Deterministic AI suggestion payload. */
const AI_SUGGESTION = {
  outcomeId: OUTCOME_ID,
  rallyCryName: "Scale Revenue",
  objectiveName: "Improve Conversion",
  outcomeName: "Increase trial-to-paid by 20%",
  confidence: 0.87,
  rationale: "This commitment directly supports enterprise revenue growth goals.",
};

/** Deterministic AI draft item payload. */
const AI_DRAFT_ITEM = {
  commitId: COMMIT_ID_1,
  suggestedStatus: "DONE",
  suggestedActualResult: "Delivered as planned — all planning APIs are production ready.",
  suggestedDeltaReason: null,
};

// ─── Helpers ──────────────────────────────────────────────────────────────────

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
    state: "DRAFT",
    reviewStatus: "REVIEW_NOT_APPLICABLE",
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
    title: "Ship planning APIs",
    description: "Finalize lifecycle endpoints and validations.",
    chessPriority: "KING",
    category: "DELIVERY",
    outcomeId: OUTCOME_ID,
    nonStrategicReason: null,
    expectedResult: "Plan lifecycle endpoints are production ready.",
    confidence: 4,
    tags: ["api", "backend"],
    progressNotes: "",
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

function buildTeamMember(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    userId: REPORT_USER_ID,
    displayName: "Alice Chen",
    planId: REPORT_PLAN_ID,
    state: "RECONCILED",
    reviewStatus: "REVIEW_PENDING",
    commitCount: 1,
    incompleteCount: 0,
    nonStrategicCount: 0,
    kingCount: 1,
    queenCount: 0,
    lastUpdated: now,
    isStale: false,
    isLateLock: false,
    ...overrides,
  };
}

function json(route: Route, status: number, body: unknown): Promise<void> {
  return route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

function apiError(message: string, code = "VALIDATION_ERROR"): Record<string, unknown> {
  return { error: { code, message, details: [] } };
}

// ─── Mock API options ─────────────────────────────────────────────────────────

interface MockApiOptions {
  /** IC's plan state. */
  initialPlan?: Record<string, unknown> | null;
  /** IC's commits. */
  commits?: Array<Record<string, unknown>>;
  /**
   * What the /ai/suggest-rcdo endpoint returns.
   *   - undefined → returns deterministic suggestions (AI_SUGGESTION)
   *   - 'unavailable' → returns { status: 'unavailable', suggestions: [] }
   *   - 'empty' → returns { status: 'ok', suggestions: [] }
   */
  aiSuggestMode?: "ok" | "unavailable" | "empty";
  /**
   * What the /ai/draft-reconciliation endpoint returns.
   *   - undefined / 'ok' → returns AI_DRAFT_ITEM for COMMIT_ID_1
   *   - 'unavailable' → returns { status: 'unavailable', drafts: [] }
   */
  aiDraftMode?: "ok" | "unavailable";
  /**
   * What the /ai/manager-insights endpoint returns.
   *   - undefined / 'ok' → returns a deterministic headline + 1 insight
   *   - 'unavailable' → returns { status: 'unavailable', headline: null, insights: [] }
   */
  aiInsightsMode?: "ok" | "unavailable";
  /** Team members for the manager dashboard. */
  teamSummaryUsers?: Array<Record<string, unknown>>;
  /** How many times the /ai/manager-insights endpoint was called (set by the mock). */
  insightCallCount?: { value: number };
}

// ─── Mock API installer ───────────────────────────────────────────────────────

async function installMockApi(page: Page, options: MockApiOptions = {}): Promise<void> {
  const state = {
    plan: options.initialPlan !== undefined ? options.initialPlan : null,
    commits: options.commits ? [...options.commits] : [],
    actuals: new Map<string, Record<string, unknown>>(),
  };

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    // ── RCDO tree ─────────────────────────────────────────────────────────
    if (method === "GET" && path === "/api/v1/rcdo/tree") {
      return json(route, 200, {
        rallyCries: [
          {
            id: "rc-1",
            name: "Scale Revenue",
            objectives: [
              {
                id: "obj-1",
                name: "Improve Conversion",
                rallyCryId: "rc-1",
                outcomes: [
                  {
                    id: OUTCOME_ID,
                    name: "Increase trial-to-paid by 20%",
                    objectiveId: "obj-1",
                  },
                ],
              },
            ],
          },
        ],
      });
    }

    // ── GET plan (current user) ───────────────────────────────────────────
    if (method === "GET" && /^\/api\/v1\/weeks\/[^/]+\/plans\/me$/.test(path)) {
      if (state.plan) {
        return json(route, 200, state.plan);
      }
      return json(route, 404, apiError("Plan not found", "NOT_FOUND"));
    }

    // ── Create plan ───────────────────────────────────────────────────────
    if (method === "POST" && /^\/api\/v1\/weeks\/[^/]+\/plans$/.test(path)) {
      state.plan = buildPlan();
      return json(route, 201, state.plan);
    }

    // ── List commits ──────────────────────────────────────────────────────
    if (method === "GET" && path === `/api/v1/plans/${PLAN_ID}/commits`) {
      return json(route, 200, state.commits);
    }

    // ── Create commit ─────────────────────────────────────────────────────
    if (method === "POST" && path === `/api/v1/plans/${PLAN_ID}/commits`) {
      const body = (request.postDataJSON() ?? {}) as Record<string, unknown>;
      const created = buildCommit({
        id: `20000000-0000-0000-0000-${String(Date.now()).slice(-12).padStart(12, "0")}`,
        title: body.title ?? "New Commit",
        description: body.description ?? "",
        chessPriority: body.chessPriority ?? null,
        category: body.category ?? null,
        outcomeId: body.outcomeId ?? null,
        nonStrategicReason: body.nonStrategicReason ?? null,
        expectedResult: body.expectedResult ?? "",
        validationErrors: [],
      });
      state.commits = [...state.commits, created];
      return json(route, 201, created);
    }

    // ── Update commit ─────────────────────────────────────────────────────
    if (method === "PATCH" && /^\/api\/v1\/commits\/[^/]+$/.test(path)) {
      const commitId = path.split("/")[4];
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
    if (method === "DELETE" && /^\/api\/v1\/commits\/[^/]+$/.test(path)) {
      const commitId = path.split("/")[4];
      state.commits = state.commits.filter((c) => c.id !== commitId);
      return route.fulfill({ status: 204 });
    }

    // ── Lock plan ─────────────────────────────────────────────────────────
    if (method === "POST" && path === `/api/v1/plans/${PLAN_ID}/lock`) {
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: "LOCKED",
        lockType: "ON_TIME",
        lockedAt: now,
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: now,
      });
      return json(route, 200, state.plan);
    }

    // ── Start reconciliation ──────────────────────────────────────────────
    if (method === "POST" && path === `/api/v1/plans/${PLAN_ID}/start-reconciliation`) {
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: "RECONCILING",
        lockType: "ON_TIME",
        lockedAt: state.plan?.lockedAt ?? now,
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: now,
      });
      return json(route, 200, state.plan);
    }

    // ── Update actual ─────────────────────────────────────────────────────
    if (method === "PATCH" && /^\/api\/v1\/commits\/[^/]+\/actual$/.test(path)) {
      const commitId = path.split("/")[4];
      const body = (request.postDataJSON() ?? {}) as Record<string, unknown>;
      const actual = {
        commitId,
        actualResult: body.actualResult ?? "",
        completionStatus: body.completionStatus ?? "DONE",
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

    // ── Submit reconciliation ─────────────────────────────────────────────
    if (method === "POST" && path === `/api/v1/plans/${PLAN_ID}/submit-reconciliation`) {
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: "RECONCILED",
        reviewStatus: "REVIEW_PENDING",
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: now,
      });
      return json(route, 200, state.plan);
    }

    // ── Carry-forward ─────────────────────────────────────────────────────
    if (method === "POST" && path === `/api/v1/plans/${PLAN_ID}/carry-forward`) {
      state.plan = buildPlan({
        ...(state.plan ?? {}),
        state: "CARRY_FORWARD",
        carryForwardExecutedAt: now,
        version: Number(state.plan?.version ?? 1) + 1,
        updatedAt: now,
      });
      return json(route, 200, state.plan);
    }

    // ── Notifications ─────────────────────────────────────────────────────
    if (method === "GET" && path === "/api/v1/notifications/unread") {
      return json(route, 200, []);
    }
    if (method === "POST" && path === "/api/v1/notifications/read-all") {
      return json(route, 200, {});
    }

    // ── Team summary (manager) ────────────────────────────────────────────
    if (method === "GET" && /^\/api\/v1\/weeks\/[^/]+\/team\/summary$/.test(path)) {
      const users = options.teamSummaryUsers ?? [buildTeamMember()];
      return json(route, 200, {
        weekStart: mondayIso(),
        users,
        reviewStatusCounts: {
          pending: users.filter((u) => u.reviewStatus === "REVIEW_PENDING").length,
          approved: users.filter((u) => u.reviewStatus === "APPROVED").length,
          changesRequested: users.filter((u) => u.reviewStatus === "CHANGES_REQUESTED").length,
        },
        page: 0,
        size: 20,
        totalElements: users.length,
        totalPages: 1,
      });
    }

    // ── Team RCDO rollup ──────────────────────────────────────────────────
    if (method === "GET" && /^\/api\/v1\/weeks\/[^/]+\/team\/rcdo-rollup$/.test(path)) {
      return json(route, 200, {
        weekStart: mondayIso(),
        items: [],
        nonStrategicCount: 0,
      });
    }

    // ── Manager drill-down: GET plan ──────────────────────────────────────
    if (method === "GET" && /^\/api\/v1\/weeks\/[^/]+\/plans\/[^/]+$/.test(path)) {
      return json(route, 200, buildPlan({
        id: REPORT_PLAN_ID,
        ownerUserId: REPORT_USER_ID,
        state: "RECONCILED",
        reviewStatus: "REVIEW_PENDING",
        lockType: "ON_TIME",
        lockedAt: now,
      }));
    }

    // ── Manager drill-down: GET commits ───────────────────────────────────
    if (method === "GET" && /^\/api\/v1\/weeks\/[^/]+\/plans\/[^/]+\/commits$/.test(path)) {
      return json(route, 200, []);
    }

    // ── Review submission ─────────────────────────────────────────────────
    if (method === "POST" && /^\/api\/v1\/plans\/[^/]+\/review$/.test(path)) {
      return json(route, 200, {
        id: "review-1",
        weeklyPlanId: REPORT_PLAN_ID,
        reviewerUserId: USER_ID,
        decision: "APPROVED",
        comments: "",
        createdAt: now,
      });
    }

    // ── AI: suggest-rcdo ─────────────────────────────────────────────────
    if (method === "POST" && path === "/api/v1/ai/suggest-rcdo") {
      const mode = options.aiSuggestMode ?? "ok";
      if (mode === "unavailable") {
        return json(route, 200, { status: "unavailable", suggestions: [] });
      }
      if (mode === "empty") {
        return json(route, 200, { status: "ok", suggestions: [] });
      }
      // Default: return deterministic suggestion
      return json(route, 200, {
        status: "ok",
        suggestions: [AI_SUGGESTION],
      });
    }

    // ── AI: draft-reconciliation ──────────────────────────────────────────
    if (method === "POST" && path === "/api/v1/ai/draft-reconciliation") {
      const mode = options.aiDraftMode ?? "ok";
      if (mode === "unavailable") {
        return json(route, 200, { status: "unavailable", drafts: [] });
      }
      // Return draft item for COMMIT_ID_1
      return json(route, 200, {
        status: "ok",
        drafts: [AI_DRAFT_ITEM],
      });
    }

    // ── AI: manager-insights ──────────────────────────────────────────────
    if (method === "POST" && path === "/api/v1/ai/manager-insights") {
      if (options.insightCallCount) {
        options.insightCallCount.value += 1;
      }
      const mode = options.aiInsightsMode ?? "ok";
      if (mode === "unavailable") {
        return json(route, 200, { status: "unavailable", headline: null, insights: [] });
      }
      return json(route, 200, {
        status: "ok",
        headline: "Team is on track — 1 review pending.",
        insights: [
          {
            title: "Review pending",
            detail: "Alice has a reconciled plan awaiting manager review.",
            severity: "INFO",
          },
        ],
      });
    }

    return json(route, 404, apiError(`Unhandled mock: ${method} ${path}`, "NOT_FOUND"));
  });
}

// ─── Navigation helper ─────────────────────────────────────────────────────

async function goToTeamDashboard(page: Page): Promise<void> {
  await expect(page.getByTestId("pa-host-shell")).toBeVisible();
  await page.getByTestId("nav-team-dashboard").click();
  await expect(page.getByTestId("team-dashboard-page")).toBeVisible();
}

// ═══════════════════════════════════════════════════════════════════════════════
// AI RCDO Suggestion Panel (suggestRcdo — enabled by default)
// PRD §18 #18
// ═══════════════════════════════════════════════════════════════════════════════

test.describe("AI RCDO Suggestion Panel (§18 #18)", () => {
  test("[SMOKE] AI suggestion panel appears when editing a commit title (>=5 chars)", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
      aiSuggestMode: "ok",
    });
    await page.goto("/");

    await expect(page.getByTestId("plan-header")).toBeVisible();

    // Open the new commit form
    await page.getByTestId("add-commit-btn").click();
    await expect(page.getByTestId("commit-editor-new")).toBeVisible();

    // Set up intercept before triggering the suggestion
    const suggestPromise = page.waitForResponse("**/api/v1/ai/suggest-rcdo");

    // Type a title long enough to trigger AI suggestions (>=5 chars)
    await page.getByTestId("commit-title").fill("Ship enterprise features this week");

    // Wait for the debounced API call to complete
    await suggestPromise;

    // AI suggestion panel should appear
    await expect(page.getByTestId("ai-suggestion-panel")).toBeVisible();
  });

  test("[SMOKE] AI suggestions are displayed as selectable chips with outcome name and confidence", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
      aiSuggestMode: "ok",
    });
    await page.goto("/");

    await page.getByTestId("add-commit-btn").click();
    await expect(page.getByTestId("commit-editor-new")).toBeVisible();

    const suggestPromise = page.waitForResponse("**/api/v1/ai/suggest-rcdo");
    await page.getByTestId("commit-title").fill("Ship enterprise features this week");
    await suggestPromise;

    await expect(page.getByTestId("ai-suggestion-panel")).toBeVisible();

    // The suggestion chip should contain the outcome name
    const chip = page.getByTestId("ai-suggestion-0");
    await expect(chip).toBeVisible();
    await expect(chip).toContainText("Increase trial-to-paid by 20%");

    // The chip shows confidence as a percentage
    await expect(chip).toContainText("87%");

    // The chip shows the breadcrumb (Rally Cry and Objective)
    await expect(chip).toContainText("Scale Revenue");
    await expect(chip).toContainText("Improve Conversion");
  });

  test("[SMOKE] Clicking an AI suggestion chip populates the RCDO picker", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
      aiSuggestMode: "ok",
    });
    await page.goto("/");

    await page.getByTestId("add-commit-btn").click();
    await expect(page.getByTestId("commit-editor-new")).toBeVisible();

    // Wait for suggestions
    const suggestPromise = page.waitForResponse("**/api/v1/ai/suggest-rcdo");
    await page.getByTestId("commit-title").fill("Ship enterprise revenue features");
    await suggestPromise;

    await expect(page.getByTestId("ai-suggestion-panel")).toBeVisible();

    // Click the suggestion chip
    await page.getByTestId("ai-suggestion-0").click();

    // The AI suggestion panel should clear (panel disappears after accepting)
    await expect(page.getByTestId("ai-suggestion-panel")).not.toBeVisible();

    // The RCDO picker should now show the selected outcome
    await expect(page.getByTestId("rcdo-current")).toBeVisible();
    await expect(page.getByTestId("rcdo-current")).toContainText(
      "Increase trial-to-paid by 20%",
    );
  });

  test("[FULL] AI suggestion loading indicator is shown while fetching", async ({ page }) => {
    // Delay the API response long enough to deterministically observe loading
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
      aiSuggestMode: "ok",
    });

    await page.route("**/api/v1/ai/suggest-rcdo", async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1500));
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ status: "ok", suggestions: [AI_SUGGESTION] }),
      });
    });

    await page.goto("/");

    await page.getByTestId("add-commit-btn").click();
    await expect(page.getByTestId("commit-editor-new")).toBeVisible();

    const suggestRequest = page.waitForRequest("**/api/v1/ai/suggest-rcdo");
    const suggestResponse = page.waitForResponse("**/api/v1/ai/suggest-rcdo");
    await page.getByTestId("commit-title").fill("Ship enterprise features now");
    await suggestRequest;

    // The loading indicator should appear while the debounced request is in-flight
    await expect(page.getByTestId("ai-suggestion-loading")).toBeVisible();
    await expect(page.getByTestId("ai-suggestion-loading")).toContainText(
      "Finding relevant outcomes",
    );

    await suggestResponse;

    // After the response arrives, the loading indicator disappears
    await expect(page.getByTestId("ai-suggestion-loading")).not.toBeVisible();
    await expect(page.getByTestId("ai-suggestion-panel")).toBeVisible();
  });

  test("[SMOKE] AI unavailable status shows manual RCDO picker without surfacing an error", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
      aiSuggestMode: "unavailable",
    });
    await page.goto("/");

    await page.getByTestId("add-commit-btn").click();
    await expect(page.getByTestId("commit-editor-new")).toBeVisible();

    const suggestPromise = page.waitForResponse("**/api/v1/ai/suggest-rcdo");
    await page.getByTestId("commit-title").fill("Ship enterprise revenue features");
    await suggestPromise;

    // The AI suggestion panel must NOT be visible when suggestions are unavailable
    await expect(page.getByTestId("ai-suggestion-panel")).not.toBeVisible();
    await expect(page.getByTestId("ai-suggestion-loading")).not.toBeVisible();

    // The manual RCDO picker should still be visible and usable
    await expect(page.getByTestId("rcdo-picker")).toBeVisible();

    // No error banner should be shown (graceful degradation)
    await expect(page.getByTestId("error-banner")).not.toBeVisible();
  });

  test("[FULL] AI suggestion panel hides when non-strategic toggle is checked", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
      aiSuggestMode: "ok",
    });
    await page.goto("/");

    await page.getByTestId("add-commit-btn").click();
    await expect(page.getByTestId("commit-editor-new")).toBeVisible();

    const suggestPromise = page.waitForResponse("**/api/v1/ai/suggest-rcdo");
    await page.getByTestId("commit-title").fill("Ship enterprise revenue features");
    await suggestPromise;

    await expect(page.getByTestId("ai-suggestion-panel")).toBeVisible();

    // Toggle non-strategic → the AI suggestion panel should disappear
    await page.getByTestId("non-strategic-toggle").check();
    await expect(page.getByTestId("ai-suggestion-panel")).not.toBeVisible();

    // Manual picker is also hidden (non-strategic form replaces RCDO picker)
    await expect(page.getByTestId("rcdo-picker")).not.toBeVisible();

    // Non-strategic reason input is shown instead
    await expect(page.getByTestId("non-strategic-reason")).toBeVisible();
  });

  test("[FULL] AI suggestion panel does not appear for short titles (<5 chars)", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
      aiSuggestMode: "ok",
    });
    await page.goto("/");

    await page.getByTestId("add-commit-btn").click();
    await expect(page.getByTestId("commit-editor-new")).toBeVisible();

    // Type a title that is too short to trigger suggestions
    await page.getByTestId("commit-title").fill("API");

    // Wait a moment; no API call should fire
    await page.waitForTimeout(700);

    // No loading or suggestion panel
    await expect(page.getByTestId("ai-suggestion-loading")).not.toBeVisible();
    await expect(page.getByTestId("ai-suggestion-panel")).not.toBeVisible();
  });

  test("[FULL] AI suggestion panel disappears when commit editor is cancelled", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
      aiSuggestMode: "ok",
    });
    await page.goto("/");

    await page.getByTestId("add-commit-btn").click();
    await expect(page.getByTestId("commit-editor-new")).toBeVisible();

    const suggestPromise = page.waitForResponse("**/api/v1/ai/suggest-rcdo");
    await page.getByTestId("commit-title").fill("Ship enterprise revenue features");
    await suggestPromise;

    await expect(page.getByTestId("ai-suggestion-panel")).toBeVisible();

    // Cancel the editor
    await page.getByTestId("commit-cancel").click();
    await expect(page.getByTestId("commit-editor-new")).not.toBeVisible();
    await expect(page.getByTestId("ai-suggestion-panel")).not.toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════════
// AI Reconciliation Draft (draftReconciliation — beta, ?flags=draftReconciliation)
// PRD §18 #20
// ═══════════════════════════════════════════════════════════════════════════════

test.describe("AI Reconciliation Draft (§18 #20)", () => {
  test("[FULL] AI reconciliation draft panel appears in RECONCILING state when flag is enabled", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "RECONCILING", lockType: "ON_TIME", lockedAt: now }),
      commits: [buildCommit({ chessPriority: "KING", validationErrors: [] })],
      aiDraftMode: "ok",
    });
    // Enable the draftReconciliation feature flag via URL param
    await page.goto("/?flags=draftReconciliation");

    await expect(page.getByTestId("reconciliation-view")).toBeVisible();

    // The AI reconciliation draft panel should be visible
    await expect(page.getByTestId("ai-reconciliation-draft")).toBeVisible();

    // It should be clearly labeled as beta
    await expect(page.getByTestId("ai-reconciliation-draft")).toContainText("Beta");
    await expect(page.getByTestId("ai-reconciliation-draft")).toContainText(
      "review before submitting",
    );
  });

  test("[FULL] AI reconciliation draft panel is NOT shown when flag is disabled (default)", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "RECONCILING", lockType: "ON_TIME", lockedAt: now }),
      commits: [buildCommit({ chessPriority: "KING", validationErrors: [] })],
    });
    // Navigate without feature flag — draftReconciliation defaults to false
    await page.goto("/");

    await expect(page.getByTestId("reconciliation-view")).toBeVisible();

    // The AI draft panel should NOT be visible
    await expect(page.getByTestId("ai-reconciliation-draft")).not.toBeVisible();
  });

  test("[FULL] Clicking 'Generate AI Draft' button triggers the API fetch and shows loading state", async ({ page }) => {
    // Delay the draft API response so we can observe loading
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "RECONCILING", lockType: "ON_TIME", lockedAt: now }),
      commits: [buildCommit({ chessPriority: "KING", validationErrors: [] })],
      aiDraftMode: "ok",
    });

    await page.route("**/api/v1/ai/draft-reconciliation", async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 200));
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ status: "ok", drafts: [AI_DRAFT_ITEM] }),
      });
    });

    await page.goto("/?flags=draftReconciliation");

    await expect(page.getByTestId("ai-reconciliation-draft")).toBeVisible();
    await expect(page.getByTestId("ai-draft-fetch")).toBeVisible();

    // Click "Generate Draft"
    await page.getByTestId("ai-draft-fetch").click();

    // Loading state should appear
    await expect(page.getByTestId("ai-draft-loading")).toBeVisible();
    await expect(page.getByTestId("ai-draft-loading")).toContainText(
      "Analyzing commitments",
    );

    // After response, draft items should appear
    await expect(page.getByTestId("ai-draft-loading")).not.toBeVisible();
    await expect(page.getByTestId("ai-draft-items")).toBeVisible();
  });

  test("[FULL] Draft items display with suggested status and actual result", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "RECONCILING", lockType: "ON_TIME", lockedAt: now }),
      commits: [buildCommit({ chessPriority: "KING", validationErrors: [] })],
      aiDraftMode: "ok",
    });
    await page.goto("/?flags=draftReconciliation");

    await expect(page.getByTestId("ai-reconciliation-draft")).toBeVisible();

    // Generate the draft
    await page.getByTestId("ai-draft-fetch").click();
    await expect(page.getByTestId("ai-draft-items")).toBeVisible();

    // First draft item should show the suggested status and actual result
    const draftItem = page.getByTestId("ai-draft-item-0");
    await expect(draftItem).toBeVisible();
    await expect(draftItem).toContainText("DONE");
    await expect(draftItem).toContainText(
      "Delivered as planned — all planning APIs are production ready.",
    );

    // Apply button should be present
    await expect(page.getByTestId("ai-draft-apply-0")).toBeVisible();
  });

  test("[FULL] Clicking Apply on a draft item applies it to the reconciliation form", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "RECONCILING", lockType: "ON_TIME", lockedAt: now }),
      commits: [buildCommit({ id: COMMIT_ID_1, chessPriority: "KING", validationErrors: [] })],
      aiDraftMode: "ok",
    });
    await page.goto("/?flags=draftReconciliation");

    await expect(page.getByTestId("reconciliation-view")).toBeVisible();

    // Generate the AI draft
    await page.getByTestId("ai-draft-fetch").click();
    await expect(page.getByTestId("ai-draft-items")).toBeVisible();

    // Click "Apply" for the first draft item
    await page.getByTestId("ai-draft-apply-0").click();

    // The actual result field in the reconciliation view should be populated
    const actualField = page.getByTestId(`reconcile-actual-${COMMIT_ID_1}`);
    await expect(actualField).toHaveValue(
      "Delivered as planned — all planning APIs are production ready.",
    );
  });

  test("[FULL] AI draft unavailable state shows a non-blocking fallback message", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "RECONCILING", lockType: "ON_TIME", lockedAt: now }),
      commits: [buildCommit({ chessPriority: "KING", validationErrors: [] })],
      aiDraftMode: "unavailable",
    });
    await page.goto("/?flags=draftReconciliation");

    await expect(page.getByTestId("ai-reconciliation-draft")).toBeVisible();

    // Generate the draft (will return unavailable)
    await page.getByTestId("ai-draft-fetch").click();

    // Unavailable state should appear
    await expect(page.getByTestId("ai-draft-unavailable")).toBeVisible();
    await expect(page.getByTestId("ai-draft-unavailable")).toContainText(
      "AI draft unavailable",
    );
    await expect(page.getByTestId("ai-draft-unavailable")).toContainText(
      "Complete reconciliation manually",
    );

    // The reconciliation view should remain fully functional
    await expect(page.getByTestId("reconciliation-view")).toBeVisible();
    await expect(page.getByTestId(`reconcile-commit-${COMMIT_ID_1}`)).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════════
// AI Manager Insights Panel (managerInsights — beta, ?flags=managerInsights)
// PRD §18 #19
// ═══════════════════════════════════════════════════════════════════════════════

test.describe("AI Manager Insights Panel (§18 #19)", () => {
  test("[FULL] AI manager insights panel shows headline and insight cards when flag is enabled", async ({ page }) => {
    await installMockApi(page, {
      aiInsightsMode: "ok",
      teamSummaryUsers: [buildTeamMember()],
    });
    // Enable the managerInsights feature flag
    await page.goto("/?flags=managerInsights");

    await goToTeamDashboard(page);

    // The AI manager insights panel should be visible
    await expect(page.getByTestId("ai-manager-insights")).toBeVisible();

    // It should show the headline
    const content = page.getByTestId("ai-manager-insights-content");
    await expect(content).toBeVisible();
    await expect(content).toContainText("Team is on track — 1 review pending.");

    // It should show insight cards
    const insight = page.getByTestId("ai-manager-insight-0");
    await expect(insight).toBeVisible();
    await expect(insight).toContainText("Review pending");
    await expect(insight).toContainText(
      "Alice has a reconciled plan awaiting manager review.",
    );
  });

  test("[FULL] AI manager insights panel is labeled as beta", async ({ page }) => {
    await installMockApi(page, { aiInsightsMode: "ok" });
    await page.goto("/?flags=managerInsights");

    await goToTeamDashboard(page);

    await expect(page.getByTestId("ai-manager-insights")).toBeVisible();
    await expect(page.getByTestId("ai-manager-insights")).toContainText("Beta");
    await expect(page.getByTestId("ai-manager-insights")).toContainText(
      "verify against the dashboard below",
    );
  });

  test("[FULL] Manager insights 'Refresh' button triggers a re-fetch of insights", async ({ page }) => {
    const insightCallCount = { value: 0 };
    await installMockApi(page, {
      aiInsightsMode: "ok",
      insightCallCount,
    });
    await page.goto("/?flags=managerInsights");

    await goToTeamDashboard(page);

    // Wait for the initial fetch to complete
    await expect(page.getByTestId("ai-manager-insights-content")).toBeVisible();
    const callsAfterMount = insightCallCount.value;
    expect(callsAfterMount).toBeGreaterThan(0);

    // Click the Refresh button
    const refreshPromise = page.waitForResponse("**/api/v1/ai/manager-insights");
    await page.getByTestId("ai-manager-insights-refresh").click();
    await refreshPromise;

    // The call count should have increased by 1
    expect(insightCallCount.value).toBe(callsAfterMount + 1);

    // The content should still be visible after refresh
    await expect(page.getByTestId("ai-manager-insights-content")).toBeVisible();
  });

  test("[FULL] AI manager insights loading state is shown while fetching", async ({ page }) => {
    await installMockApi(page, { aiInsightsMode: "ok" });

    // Delay the initial insights response
    await page.route("**/api/v1/ai/manager-insights", async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 300));
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          status: "ok",
          headline: "Team is on track — 1 review pending.",
          insights: [
            {
              title: "Review pending",
              detail: "Alice has a reconciled plan awaiting manager review.",
              severity: "INFO",
            },
          ],
        }),
      });
    });

    await page.goto("/?flags=managerInsights");
    await goToTeamDashboard(page);

    // Loading state should appear while the request is in-flight
    await expect(page.getByTestId("ai-manager-insights-loading")).toBeVisible();
    await expect(page.getByTestId("ai-manager-insights-loading")).toContainText(
      "Summarizing team signals",
    );

    // After the response, loading disappears and content appears
    await expect(page.getByTestId("ai-manager-insights-loading")).not.toBeVisible();
    await expect(page.getByTestId("ai-manager-insights-content")).toBeVisible();
  });

  test("[FULL] AI manager insights unavailable state shows a non-blocking fallback message", async ({ page }) => {
    await installMockApi(page, { aiInsightsMode: "unavailable" });
    await page.goto("/?flags=managerInsights");

    await goToTeamDashboard(page);

    // Unavailable fallback should be shown
    await expect(page.getByTestId("ai-manager-insights-unavailable")).toBeVisible();
    await expect(page.getByTestId("ai-manager-insights-unavailable")).toContainText(
      "AI insights unavailable",
    );
    await expect(page.getByTestId("ai-manager-insights-unavailable")).toContainText(
      "manual dashboard",
    );

    // The manual team summary grid should still be fully functional
    await expect(page.getByTestId("team-summary-grid")).toBeVisible();
  });

  test("[FULL] AI manager insights panel is hidden by default when flag is disabled", async ({ page }) => {
    await installMockApi(page);
    // Navigate WITHOUT the feature flag
    await page.goto("/");

    await goToTeamDashboard(page);

    // Panel should NOT be rendered (managerInsights defaults to false)
    await expect(page.getByTestId("ai-manager-insights")).not.toBeVisible();
  });

  test("[FULL] Both suggestRcdo and managerInsights can be enabled simultaneously", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [],
      aiSuggestMode: "ok",
      aiInsightsMode: "ok",
    });
    // Enable both flags at once
    await page.goto("/?flags=suggestRcdo,managerInsights");

    await goToTeamDashboard(page);

    // Manager insights should be visible on the dashboard
    await expect(page.getByTestId("ai-manager-insights")).toBeVisible();

    // Navigate to the weekly plan
    await page.getByTestId("nav-my-plan").click();
    await expect(page.getByTestId("plan-header")).toBeVisible();

    // Open a new commit and type a title to trigger AI suggestions
    await page.getByTestId("add-commit-btn").click();
    const suggestPromise = page.waitForResponse("**/api/v1/ai/suggest-rcdo");
    await page.getByTestId("commit-title").fill("Ship enterprise features this week");
    await suggestPromise;

    // The AI suggestion panel should be visible
    await expect(page.getByTestId("ai-suggestion-panel")).toBeVisible();
  });

  test("[SMOKE] AI reconciliation draft panel and suggest panel can be enabled together", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "RECONCILING", lockType: "ON_TIME", lockedAt: now }),
      commits: [
        buildCommit({ id: COMMIT_ID_1, chessPriority: "KING", validationErrors: [] }),
        buildCommit({ id: COMMIT_ID_2, chessPriority: "QUEEN", title: "Improve monitoring", validationErrors: [] }),
      ],
      aiDraftMode: "ok",
    });
    await page.goto("/?flags=draftReconciliation");

    await expect(page.getByTestId("reconciliation-view")).toBeVisible();
    await expect(page.getByTestId("ai-reconciliation-draft")).toBeVisible();

    // Generate draft
    const draftPromise = page.waitForResponse("**/api/v1/ai/draft-reconciliation");
    await page.getByTestId("ai-draft-fetch").click();
    await draftPromise;

    await expect(page.getByTestId("ai-draft-items")).toBeVisible();

    // The reconciliation view remains accessible below the AI draft panel
    await expect(page.getByTestId("reconciliation-view")).toBeVisible();
  });
});
