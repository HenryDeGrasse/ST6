/**
 * Browser-based E2E tests for AI-assisted plan workflow components:
 *   1. PlanQualityNudge   — advisory overlay shown before lock
 *   2. NextWorkSuggestionPanel — AI-suggested work panel on DRAFT plan
 *   3. QuickUpdateFlow    — rapid-fire batch check-in card deck (LOCKED plan)
 *
 * All tests run deterministically against mocked APIs — no live backend required.
 * Feature flags planQualityNudge, suggestNextWork, and quickUpdate are all ON
 * by default in FeatureFlagContext, so no localStorage setup is needed.
 *
 * Coverage:
 *
 * PlanQualityNudge:
 *   [SMOKE] Overlay appears before lock when planQualityNudge flag is enabled
 *   [SMOKE] "Review Plan" dismisses the overlay without locking
 *   [SMOKE] "Lock Anyway" proceeds to the lock confirm dialog
 *           Loading state shown while quality check is in-flight
 *           All-clear state shown when nudges list is empty
 *           Nudge items display with severity badges and messages
 *           Rate-limited state shown when API returns 429
 *           Unavailable state shown when API returns 503
 *           Escape key dismisses overlay
 *
 * NextWorkSuggestionPanel:
 *   [SMOKE] Panel renders on DRAFT plan page
 *   [SMOKE] Suggestions render with titles, confidence bars, and source badges
 *   [SMOKE] "Why this suggestion?" toggle expands rationale
 *           Accept calls commit creation and records ACCEPT feedback
 *           Defer records DEFER feedback
 *           Decline opens reason form; confirm records DECLINE feedback
 *           Decline cancel hides the form
 *           Refresh button triggers re-fetch
 *           Empty state shown when no suggestions
 *           Rate-limited state shown
 *
 * QuickUpdateFlow:
 *   [SMOKE] Quick Update button visible on LOCKED plan
 *   [SMOKE] Clicking Quick Update opens the card flow
 *   [SMOKE] Progress indicator shows "1 of N commitments"
 *   [SMOKE] Status buttons (ON_TRACK, AT_RISK, BLOCKED, DONE_EARLY) render
 *           Selecting a status advances to next card
 *           Note textarea is present on each card
 *           Previous button navigates back
 *           Last card shows "Submit All" button
 *           Close button dismisses the flow
 *           Submit All calls batch quick-update API and closes flow
 */
import { expect, test, type Page } from "@playwright/test";
import {
  installMockApi,
  json,
  apiError,
  buildPlan,
  buildCommit,
  MOCK_PLAN_ID,
  MOCK_OUTCOME_ID,
  MOCK_USER_ID,
  MOCK_NOW,
} from "./helpers";

// ══════════════════════════════════════════════════════════════════════════
// Test data builders
// ══════════════════════════════════════════════════════════════════════════

const SUGGESTION_1_ID = "sugg-0000-0000-0000-000000000001";
const SUGGESTION_2_ID = "sugg-0000-0000-0000-000000000002";

function buildQualityNudges() {
  return [
    {
      type: "MISSING_STRATEGIC_COMMITS",
      severity: "WARNING",
      message: "Only 1 of 5 commits is linked to a strategic outcome.",
    },
    {
      type: "LOW_CONFIDENCE",
      severity: "INFO",
      message: "3 commits have confidence below 3. Consider adjusting scope.",
    },
  ];
}

function buildNextWorkSuggestions() {
  return [
    {
      suggestionId: SUGGESTION_1_ID,
      title: "Address overdue API reliability work",
      suggestedOutcomeId: MOCK_OUTCOME_ID,
      suggestedChessPriority: "QUEEN",
      confidence: 0.88,
      source: "COVERAGE_GAP",
      sourceDetail: "API reliability outcome has had zero commits for 2 weeks.",
      rationale: "This outcome is at risk of missing its quarterly target.",
    },
    {
      suggestionId: SUGGESTION_2_ID,
      title: "Resume carried-forward migration task",
      suggestedOutcomeId: null,
      suggestedChessPriority: "ROOK",
      confidence: 0.72,
      source: "CARRY_FORWARD",
      sourceDetail: "This task was carried forward from last week.",
      rationale: "Completing carried-forward work improves cadence compliance.",
    },
  ];
}

function buildLockedPlan() {
  return buildPlan({
    state: "LOCKED",
    lockType: "ON_TIME",
    lockedAt: MOCK_NOW,
    version: 2,
  });
}

function buildLockedCommit(overrides: Partial<Record<string, unknown>> = {}) {
  return buildCommit({
    title: "Ship API reliability improvements",
    chessPriority: "QUEEN",
    category: "DELIVERY",
    ...overrides,
  });
}

// ══════════════════════════════════════════════════════════════════════════
// Shared setup helpers
// ══════════════════════════════════════════════════════════════════════════

/**
 * Set up a DRAFT plan page. Overrides can inject custom nudges / suggestions.
 */
async function setupDraftPlan(
  page: Page,
  overrides: {
    nudges?: unknown[];
    suggestions?: unknown[];
    delayQualityCheck?: boolean;
    qualityDelayMs?: number;
    qualityStatus429?: boolean;
    qualityStatus503?: boolean;
    suggestionStatus429?: boolean;
    emptySuggestions?: boolean;
  } = {},
): Promise<void> {
  const draft = buildPlan({ state: "DRAFT" });
  const commits = [buildCommit()];
  let createdCommitSeq = 0;

  await installMockApi(page, {
    initialPlan: draft,
    commits,
  });

  // Support commit creation from NextWorkSuggestionPanel Accept flow.
  await page.route(/\/api\/v1\/plans\/[^/]+\/commits$/, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
    createdCommitSeq += 1;
    const created = buildCommit({
      id: `next-work-commit-${String(createdCommitSeq).padStart(3, "0")}`,
      title: typeof body.title === "string" ? body.title : "AI suggested commit",
      chessPriority: body.chessPriority ?? null,
      outcomeId: body.outcomeId ?? null,
      tags: Array.isArray(body.tags) ? body.tags : [],
    });
    commits.push(created);
    return json(route, 201, created);
  });

  // Override quality-check POST to return richer nudges (registered after installMockApi → LIFO)
  await page.route(/\/api\/v1\/ai\/plan-quality-check$/, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    if (overrides.delayQualityCheck) {
      await new Promise((resolve) => setTimeout(resolve, 1500));
    }
    if (overrides.qualityDelayMs && overrides.qualityDelayMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, overrides.qualityDelayMs));
    }
    if (overrides.qualityStatus429) {
      return json(route, 429, apiError("Rate limit exceeded", "RATE_LIMITED"));
    }
    if (overrides.qualityStatus503) {
      return json(route, 503, apiError("Service unavailable", "UNAVAILABLE"));
    }
    const nudges = overrides.nudges ?? [];
    return json(route, 200, { status: "ok", nudges });
  });

  // Override next-work POST
  await page.route(/\/api\/v1\/ai\/suggest-next-work$/, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    if (overrides.suggestionStatus429) {
      return json(route, 429, apiError("Rate limit exceeded", "RATE_LIMITED"));
    }
    if (overrides.emptySuggestions) {
      return json(route, 200, { status: "ok", suggestions: [] });
    }
    const suggestions = overrides.suggestions ?? buildNextWorkSuggestions();
    return json(route, 200, { status: "ok", suggestions });
  });

  await page.goto("/");
  await expect(page.getByTestId("weekly-plan-page")).toBeVisible();
}

/**
 * Set up a LOCKED plan page with commits (for QuickUpdateFlow tests).
 */
async function setupLockedPlan(page: Page): Promise<void> {
  const locked = buildLockedPlan();
  const commit1 = buildLockedCommit({ id: "commit-001", title: "API reliability work" });
  const commit2 = buildLockedCommit({ id: "commit-002", title: "Migration task" });

  await installMockApi(page, {
    initialPlan: locked,
    commits: [commit1, commit2],
  });

  // Mock quick-update endpoint (uses raw fetch so not caught by installMockApi)
  await page.route(/\/api\/v1\/plans\/[^/]+\/quick-update$/, async (route) => {
    if (route.request().method() === "POST") {
      return json(route, 200, {
        updatedCount: 2,
        entries: [],
      });
    }
    return route.fallback();
  });

  // Mock check-in-options endpoint
  await page.route(/\/api\/v1\/ai\/check-in-options$/, async (route) => {
    if (route.request().method() === "POST") {
      return json(route, 200, {
        status: "ok",
        statusOptions: ["ON_TRACK", "AT_RISK", "BLOCKED", "DONE_EARLY"],
        progressOptions: [
          { text: "Making steady progress", source: "ai_generated" },
          { text: "Blocked by external dependency", source: "user_history" },
        ],
      });
    }
    return route.fallback();
  });

  await page.goto("/");
  await expect(page.getByTestId("weekly-plan-page")).toBeVisible();
}

// ══════════════════════════════════════════════════════════════════════════
// 1. PlanQualityNudge
// ══════════════════════════════════════════════════════════════════════════

test.describe("PlanQualityNudge", () => {
  test("[SMOKE] overlay appears when Lock is clicked on a DRAFT plan", async ({ page }) => {
    await setupDraftPlan(page, { nudges: buildQualityNudges() });
    await page.getByTestId("lock-btn").click();
    await expect(page.getByTestId("plan-quality-nudge-overlay")).toBeVisible();
    await expect(page.getByTestId("plan-quality-nudge-dialog")).toBeVisible();
  });

  test("[SMOKE] 'Review Plan' button dismisses the overlay", async ({ page }) => {
    await setupDraftPlan(page, { nudges: buildQualityNudges() });
    await page.getByTestId("lock-btn").click();
    await expect(page.getByTestId("plan-quality-nudge-overlay")).toBeVisible();
    await page.getByTestId("plan-quality-nudge-review").click();
    await expect(page.getByTestId("plan-quality-nudge-overlay")).not.toBeVisible();
  });

  test("[SMOKE] 'Lock Anyway' proceeds to lock confirm dialog", async ({ page }) => {
    await setupDraftPlan(page, { nudges: buildQualityNudges() });
    await page.getByTestId("lock-btn").click();
    await expect(page.getByTestId("plan-quality-nudge-overlay")).toBeVisible();
    await page.getByTestId("plan-quality-nudge-lock-anyway").click();
    // Overlay dismissed; confirm dialog should appear
    await expect(page.getByTestId("plan-quality-nudge-overlay")).not.toBeVisible();
    // ConfirmDialog for lock is shown
    await expect(page.getByRole("dialog")).toBeVisible();
  });

  test("loading state is shown while the quality check is in flight", async ({ page }) => {
    await setupDraftPlan(page, { nudges: buildQualityNudges(), qualityDelayMs: 1200 });
    await page.getByTestId("lock-btn").click();
    await expect(page.getByTestId("plan-quality-nudge-loading")).toBeVisible();
    await expect(page.getByTestId("plan-quality-nudge-dialog")).toHaveAttribute("aria-busy", "true");
    await expect(page.getByTestId("plan-quality-nudge-list")).toBeVisible();
  });

  test("all-clear state shown when nudges list is empty", async ({ page }) => {
    await setupDraftPlan(page, { nudges: [] });
    await page.getByTestId("lock-btn").click();
    await expect(page.getByTestId("plan-quality-nudge-all-clear")).toBeVisible();
    await expect(page.getByTestId("plan-quality-nudge-all-clear")).toContainText("Ready to lock");
  });

  test("nudge items display with severity badges", async ({ page }) => {
    await setupDraftPlan(page, { nudges: buildQualityNudges() });
    await page.getByTestId("lock-btn").click();
    await expect(page.getByTestId("plan-quality-nudge-list")).toBeVisible();
    await expect(page.getByTestId("plan-quality-nudge-item-0")).toBeVisible();
    await expect(page.getByTestId("plan-quality-nudge-badge-0")).toContainText("Note"); // WARNING → "Note"
    await expect(page.getByTestId("plan-quality-nudge-item-0")).toContainText("strategic outcome");
    await expect(page.getByTestId("plan-quality-nudge-item-1")).toBeVisible();
    await expect(page.getByTestId("plan-quality-nudge-badge-1")).toContainText("Info");
  });

  test("rate-limited state shown when quality check returns 429", async ({ page }) => {
    await setupDraftPlan(page, { qualityStatus429: true });
    await page.getByTestId("lock-btn").click();
    await expect(page.getByTestId("plan-quality-nudge-rate-limited")).toBeVisible();
    await expect(page.getByTestId("plan-quality-nudge-rate-limited")).toContainText("Rate limit");
  });

  test("unavailable state shown when quality check returns 503", async ({ page }) => {
    await setupDraftPlan(page, { qualityStatus503: true });
    await page.getByTestId("lock-btn").click();
    await expect(page.getByTestId("plan-quality-nudge-unavailable")).toBeVisible();
  });

  test("Escape key dismisses the overlay", async ({ page }) => {
    await setupDraftPlan(page, { nudges: buildQualityNudges() });
    await page.getByTestId("lock-btn").click();
    await expect(page.getByTestId("plan-quality-nudge-overlay")).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.getByTestId("plan-quality-nudge-overlay")).not.toBeVisible();
  });

  test("overlay is NOT shown when planQualityNudge flag is off", async ({ page }) => {
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.planQualityNudge = false;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });
    await setupDraftPlan(page);
    await page.getByTestId("lock-btn").click();
    // Should go straight to confirm dialog, no nudge overlay
    await expect(page.getByTestId("plan-quality-nudge-overlay")).not.toBeVisible();
    await expect(page.getByRole("dialog")).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 2. NextWorkSuggestionPanel
// ══════════════════════════════════════════════════════════════════════════

test.describe("NextWorkSuggestionPanel", () => {
  test("[SMOKE] panel renders on a DRAFT plan page", async ({ page }) => {
    await setupDraftPlan(page, { suggestions: buildNextWorkSuggestions() });
    await expect(page.getByTestId("next-work-suggestion-panel")).toBeVisible();
  });

  test("[SMOKE] suggestion items render with titles, confidence bars, and source badges", async ({
    page,
  }) => {
    await setupDraftPlan(page, { suggestions: buildNextWorkSuggestions() });
    const panel = page.getByTestId("next-work-suggestion-panel");
    await expect(panel).toBeVisible();

    // First suggestion (highest confidence 88% is first after sorting)
    const s1 = panel.getByTestId(`next-work-suggestion-${SUGGESTION_1_ID}`);
    await expect(s1).toBeVisible();
    await expect(s1).toContainText("API reliability");
    // Confidence bar
    await expect(s1.getByTestId(`next-work-confidence-bar-${SUGGESTION_1_ID}`)).toBeVisible();
    await expect(s1.getByTestId(`next-work-confidence-pct-${SUGGESTION_1_ID}`)).toContainText("88%");
    // Source badge
    await expect(s1).toContainText("Coverage gap");
  });

  test("[SMOKE] refresh button is visible and enabled", async ({ page }) => {
    await setupDraftPlan(page, { suggestions: buildNextWorkSuggestions() });
    await expect(page.getByTestId("next-work-refresh-btn")).toBeVisible();
    await expect(page.getByTestId("next-work-refresh-btn")).toBeEnabled();
  });

  test("[SMOKE] 'Why this suggestion?' toggle expands rationale", async ({ page }) => {
    await setupDraftPlan(page, { suggestions: buildNextWorkSuggestions() });
    const toggle = page.getByTestId(`next-work-why-toggle-${SUGGESTION_1_ID}`);
    await expect(toggle).toBeVisible();

    // Initially collapsed
    await expect(page.getByTestId(`next-work-why-content-${SUGGESTION_1_ID}`)).not.toBeVisible();

    // Expand
    await toggle.click();
    await expect(page.getByTestId(`next-work-why-content-${SUGGESTION_1_ID}`)).toBeVisible();
    await expect(page.getByTestId(`next-work-rationale-${SUGGESTION_1_ID}`)).toContainText("quarterly target");

    // Collapse
    await toggle.click();
    await expect(page.getByTestId(`next-work-why-content-${SUGGESTION_1_ID}`)).not.toBeVisible();
  });

  test("Accept creates a commit and records ACCEPT feedback", async ({ page }) => {
    let feedbackCalled = false;
    let createCommitBody: Record<string, unknown> | null = null;
    await setupDraftPlan(page, { suggestions: buildNextWorkSuggestions() });

    await page.route(/\/api\/v1\/plans\/[^/]+\/commits$/, async (route) => {
      if (route.request().method() === "POST") {
        createCommitBody = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
      }
      return route.fallback();
    });

    await page.route(/\/api\/v1\/ai\/suggestion-feedback$/, async (route) => {
      if (route.request().method() === "POST") {
        feedbackCalled = true;
        const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        expect(body.action).toBe("ACCEPT");
        expect(body.suggestionId).toBe(SUGGESTION_1_ID);
        return json(route, 200, { status: "ok" });
      }
      return route.fallback();
    });

    await page.getByTestId(`next-work-accept-${SUGGESTION_1_ID}`).click();
    await page.waitForTimeout(400);

    expect(createCommitBody?.title).toBe("Address overdue API reliability work");
    expect(createCommitBody?.chessPriority).toBe("QUEEN");
    expect(createCommitBody?.outcomeId).toBe(MOCK_OUTCOME_ID);
    expect(createCommitBody?.tags).toEqual(["draft_source:COVERAGE_GAP"]);
    expect(feedbackCalled).toBe(true);
    await expect(page.getByTestId(`next-work-suggestion-${SUGGESTION_1_ID}`)).not.toBeVisible();
  });

  test("Defer records DEFER feedback", async ({ page }) => {
    let feedbackAction: string | null = null;
    await setupDraftPlan(page, { suggestions: buildNextWorkSuggestions() });

    await page.route(/\/api\/v1\/ai\/suggestion-feedback$/, async (route) => {
      if (route.request().method() === "POST") {
        const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        feedbackAction = body.action as string;
        return json(route, 200, { status: "ok" });
      }
      return route.fallback();
    });

    await page.getByTestId(`next-work-defer-${SUGGESTION_1_ID}`).click();
    await page.waitForTimeout(300);
    expect(feedbackAction).toBe("DEFER");
  });

  test("Decline opens reason form", async ({ page }) => {
    await setupDraftPlan(page, { suggestions: buildNextWorkSuggestions() });

    await page.getByTestId(`next-work-decline-${SUGGESTION_1_ID}`).click();
    await expect(page.getByTestId(`next-work-decline-form-${SUGGESTION_1_ID}`)).toBeVisible();
    await expect(page.getByTestId(`next-work-decline-reason-${SUGGESTION_1_ID}`)).toBeVisible();
    await expect(page.getByTestId(`next-work-decline-confirm-${SUGGESTION_1_ID}`)).toBeVisible();
    await expect(page.getByTestId(`next-work-decline-cancel-${SUGGESTION_1_ID}`)).toBeVisible();
  });

  test("Decline cancel hides the form without calling API", async ({ page }) => {
    let feedbackCalled = false;
    await setupDraftPlan(page, { suggestions: buildNextWorkSuggestions() });
    await page.route(/\/api\/v1\/ai\/suggestion-feedback$/, async (route) => {
      feedbackCalled = true;
      return route.fallback();
    });

    await page.getByTestId(`next-work-decline-${SUGGESTION_1_ID}`).click();
    await expect(page.getByTestId(`next-work-decline-form-${SUGGESTION_1_ID}`)).toBeVisible();
    await page.getByTestId(`next-work-decline-cancel-${SUGGESTION_1_ID}`).click();
    await expect(page.getByTestId(`next-work-decline-form-${SUGGESTION_1_ID}`)).not.toBeVisible();
    expect(feedbackCalled).toBe(false);
  });

  test("Decline with reason records DECLINE feedback", async ({ page }) => {
    let feedbackBody: Record<string, unknown> | null = null;
    await setupDraftPlan(page, { suggestions: buildNextWorkSuggestions() });
    await page.route(/\/api\/v1\/ai\/suggestion-feedback$/, async (route) => {
      if (route.request().method() === "POST") {
        feedbackBody = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        return json(route, 200, { status: "ok" });
      }
      return route.fallback();
    });

    await page.getByTestId(`next-work-decline-${SUGGESTION_1_ID}`).click();
    await page.getByTestId(`next-work-decline-reason-${SUGGESTION_1_ID}`).fill("Not relevant this sprint");
    await page.getByTestId(`next-work-decline-confirm-${SUGGESTION_1_ID}`).click();
    await page.waitForTimeout(300);

    expect(feedbackBody?.action).toBe("DECLINE");
    expect(feedbackBody?.reason).toBe("Not relevant this sprint");
  });

  test("refresh button triggers re-fetch of suggestions", async ({ page }) => {
    let fetchCount = 0;
    await setupDraftPlan(page, { suggestions: buildNextWorkSuggestions() });
    await page.route(/\/api\/v1\/ai\/suggest-next-work$/, async (route) => {
      if (route.request().method() === "POST") {
        fetchCount++;
        return json(route, 200, { status: "ok", suggestions: buildNextWorkSuggestions() });
      }
      return route.fallback();
    });

    const before = fetchCount;
    await page.getByTestId("next-work-refresh-btn").click();
    await page.waitForTimeout(300);
    expect(fetchCount).toBeGreaterThan(before);
  });

  test("empty state shown when no suggestions returned", async ({ page }) => {
    await setupDraftPlan(page, { emptySuggestions: true });
    await expect(page.getByTestId("next-work-empty")).toBeVisible();
    await expect(page.getByTestId("next-work-empty")).toContainText("No suggestions");
  });

  test("rate-limited state shown when API returns 429", async ({ page }) => {
    await setupDraftPlan(page, { suggestionStatus429: true });
    await expect(page.getByTestId("next-work-rate-limited")).toBeVisible();
  });

  test("panel is NOT shown when suggestNextWork flag is off", async ({ page }) => {
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.suggestNextWork = false;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });
    await setupDraftPlan(page);
    await expect(page.getByTestId("next-work-suggestion-panel")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 3. QuickUpdateFlow
// ══════════════════════════════════════════════════════════════════════════

test.describe("QuickUpdateFlow", () => {
  test("[SMOKE] quick-update-btn is visible on LOCKED plan", async ({ page }) => {
    await setupLockedPlan(page);
    await expect(page.getByTestId("quick-update-btn")).toBeVisible();
  });

  test("[SMOKE] clicking quick-update-btn opens the flow", async ({ page }) => {
    await setupLockedPlan(page);
    await page.getByTestId("quick-update-btn").click();
    await expect(page.getByTestId("quick-update-flow")).toBeVisible();
    await expect(page.getByTestId("quick-update-card")).toBeVisible();
  });

  test("[SMOKE] progress indicator shows '1 of 2 commitments'", async ({ page }) => {
    await setupLockedPlan(page);
    await page.getByTestId("quick-update-btn").click();
    await expect(page.getByTestId("quick-update-progress")).toContainText("1 of 2");
  });

  test("[SMOKE] all four status buttons render on the card", async ({ page }) => {
    await setupLockedPlan(page);
    await page.getByTestId("quick-update-btn").click();
    await expect(page.getByTestId("quick-update-status-ON_TRACK")).toBeVisible();
    await expect(page.getByTestId("quick-update-status-AT_RISK")).toBeVisible();
    await expect(page.getByTestId("quick-update-status-BLOCKED")).toBeVisible();
    await expect(page.getByTestId("quick-update-status-DONE_EARLY")).toBeVisible();
  });

  test("[SMOKE] note textarea is present", async ({ page }) => {
    await setupLockedPlan(page);
    await page.getByTestId("quick-update-btn").click();
    await expect(page.getByTestId("quick-update-note-input")).toBeVisible();
  });

  test("AI check-in options are shown for the current commitment", async ({ page }) => {
    await setupLockedPlan(page);
    await page.getByTestId("quick-update-btn").click();
    await expect(page.getByText("AI progress notes")).toBeVisible();
    await expect(page.getByRole("button", { name: "Making steady progress" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Blocked by external dependency" })).toBeVisible();
  });

  test("selecting a status advances to the next card", async ({ page }) => {
    await setupLockedPlan(page);
    await page.getByTestId("quick-update-btn").click();
    await expect(page.getByTestId("quick-update-progress")).toContainText("1 of 2");
    await page.getByTestId("quick-update-status-ON_TRACK").click();
    await expect(page.getByTestId("quick-update-progress")).toContainText("2 of 2");
  });

  test("clicking Next button advances to the next card", async ({ page }) => {
    await setupLockedPlan(page);
    await page.getByTestId("quick-update-btn").click();
    await expect(page.getByTestId("quick-update-next")).toBeVisible();
    await page.getByTestId("quick-update-next").click();
    await expect(page.getByTestId("quick-update-progress")).toContainText("2 of 2");
  });

  test("Previous button navigates back to the prior card", async ({ page }) => {
    await setupLockedPlan(page);
    await page.getByTestId("quick-update-btn").click();
    await page.getByTestId("quick-update-next").click();
    await expect(page.getByTestId("quick-update-progress")).toContainText("2 of 2");
    await page.getByTestId("quick-update-prev").click();
    await expect(page.getByTestId("quick-update-progress")).toContainText("1 of 2");
  });

  test("Previous button is disabled on the first card", async ({ page }) => {
    await setupLockedPlan(page);
    await page.getByTestId("quick-update-btn").click();
    await expect(page.getByTestId("quick-update-prev")).toBeDisabled();
  });

  test("last card shows 'Submit All' button instead of Next", async ({ page }) => {
    await setupLockedPlan(page);
    await page.getByTestId("quick-update-btn").click();
    // Navigate to last card
    await page.getByTestId("quick-update-next").click();
    await expect(page.getByTestId("quick-update-submit")).toBeVisible();
    await expect(page.getByTestId("quick-update-next")).not.toBeVisible();
  });

  test("close button dismisses the flow", async ({ page }) => {
    await setupLockedPlan(page);
    await page.getByTestId("quick-update-btn").click();
    await expect(page.getByTestId("quick-update-flow")).toBeVisible();
    await page.getByRole("button", { name: "Close quick update" }).click();
    await expect(page.getByTestId("quick-update-flow")).not.toBeVisible();
  });

  test("Submit All calls batch quick-update API and closes flow", async ({ page }) => {
    let submitCalled = false;
    let submittedPayload: Record<string, unknown> | null = null;

    await setupLockedPlan(page);

    // Register a tracking route AFTER setupLockedPlan (LIFO)
    await page.route(/\/api\/v1\/plans\/[^/]+\/quick-update$/, async (route) => {
      if (route.request().method() === "POST") {
        submitCalled = true;
        submittedPayload = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        return json(route, 200, { updatedCount: 2, entries: [] });
      }
      return route.fallback();
    });

    await page.getByTestId("quick-update-btn").click();

    // Select status for card 1
    await page.getByTestId("quick-update-status-ON_TRACK").click();
    // Now on card 2 (selecting status auto-advances)
    await page.getByTestId("quick-update-status-AT_RISK").click();
    // Now on last card — click Submit All
    await page.getByTestId("quick-update-submit").click();
    await page.waitForTimeout(400);

    expect(submitCalled).toBe(true);
    expect(Array.isArray((submittedPayload as Record<string, unknown>)?.updates)).toBe(true);
    await expect(page.getByTestId("quick-update-flow")).not.toBeVisible();
  });

  test("quick-update-btn is NOT shown when quickUpdate flag is off", async ({ page }) => {
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.quickUpdate = false;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });
    await setupLockedPlan(page);
    await expect(page.getByTestId("quick-update-btn")).not.toBeVisible();
  });
});
