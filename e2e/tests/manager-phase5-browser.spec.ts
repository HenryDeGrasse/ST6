/**
 * Browser-based E2E tests for manager Phase 5 features:
 *   1. PlanningCopilot — AI-generated team plan suggestions on Team Dashboard
 *   2. StrategicIntelligence — analytics tab with heatmap, timelines, predictions
 *
 * Uses the Carol persona (IC + MANAGER) — no persona switch needed.
 * Feature flags planningCopilot and strategicIntelligence are ON by default.
 *
 * Coverage:
 *
 * PlanningCopilot:
 *   [SMOKE] planning-copilot panel renders when flag is enabled
 *   [SMOKE] summary metrics card renders (headline, hours, at-risk count)
 *   [SMOKE] member cards render with toggle button
 *   [SMOKE] expand/collapse member suggestions via toggle
 *   [SMOKE] commit checkboxes appear when suggestions are expanded
 *           Apply Selected Suggestions button is enabled when suggestions exist
 *           Regenerate button triggers re-fetch with force=true
 *           Apply Selected calls POST /ai/team-plan-suggestion/apply
 *           Apply result message renders after successful apply
 *           Unchecking a commit removes it from apply payload
 *           Loading state renders while fetch is in-flight
 *           Rate-limited state renders on 429
 *           Unavailable state renders on unavailable response
 *           Error state renders on server error
 *           Panel NOT shown when planningCopilot flag is off
 *
 * StrategicIntelligence:
 *   [SMOKE] tab bar renders when strategicIntelligence flag is on
 *   [SMOKE] switching to strategic-intelligence tab shows the panel
 *   [SMOKE] carry-forward heatmap section renders
 *   [SMOKE] heatmap rows render for each user
 *   [SMOKE] outcome coverage section renders
 *           Timelines section can be collapsed and expanded
 *           Heatmap section can be collapsed and expanded
 *           Empty timelines state when no outcomes provided
 *           Predictions section renders when predictions flag is on
 *           Tab bar is NOT shown when strategicIntelligence flag is off
 *           Switching back to overview tab hides the panel
 */
import { expect, test, type Page } from "@playwright/test";
import {
  installMockApi,
  json,
  apiError,
  MOCK_ORG_ID,
  MOCK_USER_ID,
  MOCK_OUTCOME_ID,
  MOCK_NOW,
  mondayIso,
} from "./helpers";

// ══════════════════════════════════════════════════════════════════════════
// Constants
// ══════════════════════════════════════════════════════════════════════════

/** Carol Park — default E2E persona (IC + MANAGER). */
const CAROL_USER_ID = "c0000000-0000-0000-0000-000000000001";
const CAROL_DISPLAY_NAME = "Carol Park";

/** A second team member used in copilot suggestions. */
const ALICE_USER_ID = "c0000000-0000-0000-0000-000000000010";
const ALICE_DISPLAY_NAME = "Alice Chen";

// ══════════════════════════════════════════════════════════════════════════
// Mock response builders
// ══════════════════════════════════════════════════════════════════════════

function buildTeamPlanSuggestion(): Record<string, unknown> {
  return {
    status: "ok",
    weekStart: mondayIso(),
    summary: {
      teamCapacityHours: 80,
      suggestedHours: 72,
      bufferHours: 8,
      atRiskOutcomeCount: 2,
      criticalOutcomeCount: 0,
      strategicFocusFloor: 0.7,
      headline: "Focus on scaling revenue and reliability outcomes this week.",
    },
    members: [
      {
        userId: CAROL_USER_ID,
        displayName: CAROL_DISPLAY_NAME,
        totalEstimated: 36,
        realisticCapacity: 40,
        overcommitRisk: null,
        strengthSummary: "Strong in delivery and planning.",
        suggestedCommits: [
          {
            title: "Implement trial-to-paid funnel improvements",
            outcomeId: MOCK_OUTCOME_ID,
            chessPriority: "QUEEN",
            estimatedHours: 8,
            rationale: "This outcome is at risk of missing quarterly target.",
            source: "COVERAGE_GAP",
          },
          {
            title: "Write API reliability runbook",
            outcomeId: null,
            chessPriority: "ROOK",
            estimatedHours: 4,
            rationale: "Reduces operational risk.",
            source: null,
          },
        ],
      },
      {
        userId: ALICE_USER_ID,
        displayName: ALICE_DISPLAY_NAME,
        totalEstimated: 36,
        realisticCapacity: 40,
        overcommitRisk: null,
        strengthSummary: "Consistent delivery track record.",
        suggestedCommits: [
          {
            title: "Complete backend migration",
            outcomeId: null,
            chessPriority: "KING",
            estimatedHours: 12,
            rationale: "Unblocks platform reliability work.",
            source: "CARRY_FORWARD",
          },
        ],
      },
    ],
    outcomeAllocations: [],
    llmRefined: true,
  };
}

function buildCarryForwardHeatmap(): Record<string, unknown> {
  const weeks = [mondayIso(-3), mondayIso(-2), mondayIso(-1), mondayIso(0)];
  return {
    users: [
      {
        userId: CAROL_USER_ID,
        displayName: CAROL_DISPLAY_NAME,
        weekCells: weeks.map((weekStart, i) => ({ weekStart, carriedCount: i === 2 ? 2 : 0 })),
      },
      {
        userId: ALICE_USER_ID,
        displayName: ALICE_DISPLAY_NAME,
        weekCells: weeks.map((weekStart) => ({ weekStart, carriedCount: 0 })),
      },
    ],
  };
}

function buildOutcomeCoverageTimeline(): Record<string, unknown> {
  return {
    weeks: [
      { weekStart: mondayIso(-2), commitCount: 3, contributorCount: 2, highPriorityCount: 1 },
      { weekStart: mondayIso(-1), commitCount: 4, contributorCount: 3, highPriorityCount: 2 },
      { weekStart: mondayIso(0), commitCount: 5, contributorCount: 3, highPriorityCount: 3 },
    ],
    trendDirection: "RISING",
  };
}

function buildPredictions(): Array<Record<string, unknown>> {
  return [
    {
      type: "CARRY_FORWARD_RISK",
      likely: true,
      confidence: "HIGH",
      reason: "High likelihood of carry-forward next week based on current pace.",
    },
  ];
}

// ══════════════════════════════════════════════════════════════════════════
// Mock installer for Phase 5 / manager analytics endpoints
// ══════════════════════════════════════════════════════════════════════════

interface Phase5MockOptions {
  suggestionStatus?: "ok" | "unavailable" | "network_error" | 429 | 500;
  delayMs?: number;
}

/**
 * Installs Phase 5 mock routes AFTER installMockApi (LIFO precedence).
 */
async function installPhase5Mocks(page: Page, opts: Phase5MockOptions = {}): Promise<void> {
  const suggestionStatus = opts.suggestionStatus ?? "ok";

  // ── POST /api/v1/ai/team-plan-suggestion ─────────────────────────────
  await page.route(/\/api\/v1\/ai\/team-plan-suggestion$/, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();

    if (opts.delayMs && opts.delayMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, opts.delayMs));
    }

    if (suggestionStatus === 429) {
      return json(route, 429, apiError("Rate limit exceeded", "RATE_LIMITED"));
    }
    if (suggestionStatus === 500) {
      return json(route, 500, apiError("Internal server error", "INTERNAL_ERROR"));
    }
    if (suggestionStatus === "network_error") {
      return route.abort("failed");
    }
    if (suggestionStatus === "unavailable") {
      return json(route, 200, { status: "unavailable" });
    }
    return json(route, 200, buildTeamPlanSuggestion());
  });

  // ── POST /api/v1/ai/team-plan-suggestion/apply ───────────────────────
  await page.route(/\/api\/v1\/ai\/team-plan-suggestion\/apply$/, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    return json(route, 200, {
      status: "ok",
      weekStart: mondayIso(),
      members: [
        {
          userId: CAROL_USER_ID,
          displayName: CAROL_DISPLAY_NAME,
          planId: "plan-copilot-001",
          createdPlan: false,
          appliedCommits: [],
        },
        {
          userId: ALICE_USER_ID,
          displayName: ALICE_DISPLAY_NAME,
          planId: "plan-copilot-002",
          createdPlan: false,
          appliedCommits: [],
        },
      ],
    });
  });

  // ── GET /api/v1/outcomes/forecasts ───────────────────────────────────
  await page.route(/\/api\/v1\/outcomes\/forecasts$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, { forecasts: [] });
    }
    return route.fallback();
  });

  // ── GET /api/v1/analytics/carry-forward-heatmap ──────────────────────
  await page.route(/\/api\/v1\/analytics\/carry-forward-heatmap(\?.*)?$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, buildCarryForwardHeatmap());
    }
    return route.fallback();
  });

  // ── GET /api/v1/analytics/outcome-coverage ───────────────────────────
  await page.route(/\/api\/v1\/analytics\/outcome-coverage(\?.*)?$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, buildOutcomeCoverageTimeline());
    }
    return route.fallback();
  });

  // ── GET /api/v1/analytics/predictions/{userId} ───────────────────────
  await page.route(/\/api\/v1\/analytics\/predictions\/[^/]+$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, buildPredictions());
    }
    return route.fallback();
  });
}

// ══════════════════════════════════════════════════════════════════════════
// Shared setup helper
// ══════════════════════════════════════════════════════════════════════════

/**
 * Navigate to the Team Dashboard (Carol is MANAGER by default).
 */
async function gotoTeamDashboard(page: Page): Promise<void> {
  await page.getByTestId("nav-team-dashboard").click();
  await expect(page.getByTestId("team-dashboard-page")).toBeVisible();
}

/**
 * Full setup: install mocks, navigate to Team Dashboard.
 */
async function setupManagerDashboard(page: Page, opts: Phase5MockOptions = {}): Promise<void> {
  await installMockApi(page);
  await installPhase5Mocks(page, opts);
  await page.goto("/");
  await gotoTeamDashboard(page);
}

// ══════════════════════════════════════════════════════════════════════════
// 1. PlanningCopilot
// ══════════════════════════════════════════════════════════════════════════

test.describe("PlanningCopilot", () => {
  test("[SMOKE] planning-copilot panel renders when flag is enabled", async ({ page }) => {
    await setupManagerDashboard(page);
    await expect(page.getByTestId("planning-copilot")).toBeVisible();
  });

  test("[SMOKE] summary metrics card renders with headline and counts", async ({ page }) => {
    await setupManagerDashboard(page);
    const summary = page.getByTestId("planning-copilot-summary");
    await expect(summary).toBeVisible();
    // headline
    await expect(summary).toContainText("scaling revenue");
    // suggested hours
    await expect(summary).toContainText("72");
    // at-risk outcomes
    await expect(summary).toContainText("2");
  });

  test("[SMOKE] member cards render with toggle button", async ({ page }) => {
    await setupManagerDashboard(page);
    await expect(page.getByTestId(`planning-copilot-member-${CAROL_USER_ID}`)).toBeVisible();
    await expect(page.getByTestId(`planning-copilot-member-${ALICE_USER_ID}`)).toBeVisible();
    // Toggle buttons visible
    await expect(page.getByTestId(`planning-copilot-toggle-${CAROL_USER_ID}`)).toBeVisible();
    await expect(page.getByTestId(`planning-copilot-toggle-${ALICE_USER_ID}`)).toBeVisible();
  });

  test("[SMOKE] clicking toggle expands member suggestions", async ({ page }) => {
    await setupManagerDashboard(page);
    const toggle = page.getByTestId(`planning-copilot-toggle-${CAROL_USER_ID}`);
    await expect(toggle).toContainText("Show 2 suggestions");

    // Initially collapsed — checkboxes not visible
    await expect(page.getByTestId(`planning-copilot-select-${CAROL_USER_ID}-0`)).not.toBeVisible();

    // Expand
    await toggle.click();
    await expect(toggle).toContainText("Hide suggestions");
    await expect(page.getByTestId(`planning-copilot-select-${CAROL_USER_ID}-0`)).toBeVisible();
    await expect(page.getByTestId(`planning-copilot-select-${CAROL_USER_ID}-1`)).toBeVisible();
  });

  test("[SMOKE] commit checkboxes are checked by default after expand", async ({ page }) => {
    await setupManagerDashboard(page);
    await page.getByTestId(`planning-copilot-toggle-${CAROL_USER_ID}`).click();
    // Both commits pre-selected
    await expect(page.getByTestId(`planning-copilot-select-${CAROL_USER_ID}-0`)).toBeChecked();
    await expect(page.getByTestId(`planning-copilot-select-${CAROL_USER_ID}-1`)).toBeChecked();
  });

  test("[SMOKE] clicking toggle again collapses member suggestions", async ({ page }) => {
    await setupManagerDashboard(page);
    const toggle = page.getByTestId(`planning-copilot-toggle-${CAROL_USER_ID}`);
    await toggle.click();
    await expect(page.getByTestId(`planning-copilot-select-${CAROL_USER_ID}-0`)).toBeVisible();
    await toggle.click();
    await expect(page.getByTestId(`planning-copilot-select-${CAROL_USER_ID}-0`)).not.toBeVisible();
  });

  test("Apply Selected Suggestions button is enabled when suggestions loaded", async ({ page }) => {
    await setupManagerDashboard(page);
    await expect(page.getByTestId("planning-copilot-apply")).toBeEnabled();
  });

  test("Apply Selected Suggestions calls POST team-plan-suggestion/apply", async ({ page }) => {
    let applyCalled = false;
    let applyPayload: Record<string, unknown> | null = null;

    await setupManagerDashboard(page);

    await page.route(/\/api\/v1\/ai\/team-plan-suggestion\/apply$/, async (route) => {
      if (route.request().method() === "POST") {
        applyCalled = true;
        applyPayload = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        return json(route, 200, {
          status: "ok",
          weekStart: mondayIso(),
          members: [
            { userId: CAROL_USER_ID, displayName: CAROL_DISPLAY_NAME, planId: "p1", createdPlan: false, appliedCommits: [] },
          ],
        });
      }
      return route.fallback();
    });

    await page.getByTestId("planning-copilot-apply").click();
    await page.waitForTimeout(400);

    expect(applyCalled).toBe(true);
    expect(applyPayload?.weekStart).toBe(mondayIso());
    const members = applyPayload?.members as Array<Record<string, unknown>>;
    expect(Array.isArray(members)).toBe(true);
  });

  test("Apply result message renders after successful apply", async ({ page }) => {
    await setupManagerDashboard(page);
    await page.getByTestId("planning-copilot-apply").click();
    await expect(page.getByTestId("planning-copilot-apply-result")).toBeVisible();
    await expect(page.getByTestId("planning-copilot-apply-result")).toContainText("Applied drafts");
  });

  test("Regenerate button triggers re-fetch", async ({ page }) => {
    let fetchCount = 0;
    await setupManagerDashboard(page);

    await page.route(/\/api\/v1\/ai\/team-plan-suggestion$/, async (route) => {
      if (route.request().method() === "POST") {
        fetchCount++;
        return json(route, 200, buildTeamPlanSuggestion());
      }
      return route.fallback();
    });

    const before = fetchCount;
    await page.getByTestId("planning-copilot-refresh").click();
    await page.waitForTimeout(300);
    expect(fetchCount).toBeGreaterThan(before);
  });

  test("unchecking a commit excludes it from apply payload", async ({ page }) => {
    let applyPayload: Record<string, unknown> | null = null;

    await setupManagerDashboard(page);

    await page.route(/\/api\/v1\/ai\/team-plan-suggestion\/apply$/, async (route) => {
      if (route.request().method() === "POST") {
        applyPayload = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        return json(route, 200, {
          status: "ok",
          weekStart: mondayIso(),
          members: [],
        });
      }
      return route.fallback();
    });

    // Expand Carol's suggestions and uncheck the second commit
    await page.getByTestId(`planning-copilot-toggle-${CAROL_USER_ID}`).click();
    await page.getByTestId(`planning-copilot-select-${CAROL_USER_ID}-1`).uncheck();

    // Apply
    await page.getByTestId("planning-copilot-apply").click();
    await page.waitForTimeout(400);

    // Carol should have only 1 commit in the payload
    const members = applyPayload?.members as Array<Record<string, unknown>>;
    const carolEntry = members?.find((m) => m.userId === CAROL_USER_ID);
    const carolCommits = carolEntry?.suggestedCommits as unknown[];
    expect(carolCommits?.length).toBe(1);
  });

  test("loading state renders while fetch is in-flight then resolves to summary", async ({
    page,
  }) => {
    // 1500ms delay gives us a window to see the loading state
    await setupManagerDashboard(page, { delayMs: 1500 });
    // Loading state visible before response arrives
    await expect(page.getByTestId("planning-copilot-loading")).toBeVisible();
    // loading and summary are mutually exclusive — loading disappears after response
    await expect(page.getByTestId("planning-copilot-summary")).toBeVisible();
  });

  test("rate-limited state renders when API returns 429", async ({ page }) => {
    await setupManagerDashboard(page, { suggestionStatus: 429 });
    await expect(page.getByTestId("planning-copilot-rate-limited")).toBeVisible();
  });

  test("unavailable state renders when response has status=unavailable", async ({ page }) => {
    await setupManagerDashboard(page, { suggestionStatus: "unavailable" });
    await expect(page.getByTestId("planning-copilot-unavailable")).toBeVisible();
  });

  test("error state renders on network failure", async ({ page }) => {
    await setupManagerDashboard(page, { suggestionStatus: "network_error" });
    await expect(page.getByTestId("planning-copilot-error")).toBeVisible();
  });

  test("panel is NOT shown when planningCopilot flag is off", async ({ page }) => {
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.planningCopilot = false;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });
    await setupManagerDashboard(page);
    await expect(page.getByTestId("planning-copilot")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 2. StrategicIntelligence
// ══════════════════════════════════════════════════════════════════════════

test.describe("StrategicIntelligence", () => {
  test("[SMOKE] dashboard-tabs renders when strategicIntelligence flag is on", async ({ page }) => {
    await setupManagerDashboard(page);
    await expect(page.getByTestId("dashboard-tabs")).toBeVisible();
    await expect(page.getByTestId("tab-overview")).toBeVisible();
    await expect(page.getByTestId("tab-strategic-intelligence")).toBeVisible();
  });

  test("[SMOKE] clicking the Strategic Intelligence tab shows the panel", async ({ page }) => {
    await setupManagerDashboard(page);
    await page.getByTestId("tab-strategic-intelligence").click();
    await expect(page.getByTestId("strategic-intelligence-panel")).toBeVisible();
  });

  test("[SMOKE] switching back to overview hides the strategic intelligence panel", async ({
    page,
  }) => {
    await setupManagerDashboard(page);
    await page.getByTestId("tab-strategic-intelligence").click();
    await expect(page.getByTestId("strategic-intelligence-panel")).toBeVisible();
    await page.getByTestId("tab-overview").click();
    await expect(page.getByTestId("strategic-intelligence-panel")).not.toBeVisible();
    // Overview content still shows
    await expect(page.getByTestId("team-dashboard-page")).toBeVisible();
  });

  test("[SMOKE] carry-forward heatmap section renders", async ({ page }) => {
    await setupManagerDashboard(page);
    await page.getByTestId("tab-strategic-intelligence").click();
    await expect(page.getByTestId("section-heatmap")).toBeVisible();
    await expect(page.getByTestId("carry-forward-heatmap")).toBeVisible();
  });

  test("[SMOKE] heatmap rows render for each team member", async ({ page }) => {
    await setupManagerDashboard(page);
    await page.getByTestId("tab-strategic-intelligence").click();
    await expect(page.getByTestId(`heatmap-row-${CAROL_USER_ID}`)).toBeVisible();
    await expect(page.getByTestId(`heatmap-row-${ALICE_USER_ID}`)).toBeVisible();
  });

  test("[SMOKE] outcome coverage section renders", async ({ page }) => {
    await setupManagerDashboard(page);
    await page.getByTestId("tab-strategic-intelligence").click();
    await expect(page.getByTestId("section-timelines")).toBeVisible();
  });

  test("timelines empty state shown when no outcomes are available", async ({ page }) => {
    await installMockApi(page);
    await installPhase5Mocks(page);
    await page.route(/\/api\/v1\/rcdo\/tree$/, async (route) => {
      if (route.request().method() === "GET") {
        return json(route, 200, { rallyCries: [] });
      }
      return route.fallback();
    });
    await page.goto("/");
    await gotoTeamDashboard(page);
    await page.getByTestId("tab-strategic-intelligence").click();
    await expect(page.getByTestId("section-timelines")).toBeVisible();
    await expect(page.getByTestId("timelines-empty")).toBeVisible();
    await expect(page.getByTestId("timelines-empty")).toContainText("No outcomes available");
  });

  test("timelines section collapses via Hide button", async ({ page }) => {
    await setupManagerDashboard(page);
    await page.getByTestId("tab-strategic-intelligence").click();
    await expect(page.getByTestId("timelines-content")).toBeVisible();
    await page.getByTestId("toggle-timelines").click();
    await expect(page.getByTestId("timelines-content")).not.toBeVisible();
    // Toggle shows "Show"
    await expect(page.getByTestId("toggle-timelines")).toContainText("Show");
  });

  test("timelines section re-expands via Show button", async ({ page }) => {
    await setupManagerDashboard(page);
    await page.getByTestId("tab-strategic-intelligence").click();
    await page.getByTestId("toggle-timelines").click();
    await expect(page.getByTestId("timelines-content")).not.toBeVisible();
    await page.getByTestId("toggle-timelines").click();
    await expect(page.getByTestId("timelines-content")).toBeVisible();
  });

  test("heatmap section collapses via Hide button", async ({ page }) => {
    await setupManagerDashboard(page);
    await page.getByTestId("tab-strategic-intelligence").click();
    await expect(page.getByTestId("heatmap-content")).toBeVisible();
    await page.getByTestId("toggle-heatmap").click();
    await expect(page.getByTestId("heatmap-content")).not.toBeVisible();
  });

  test("predictions section renders when predictions flag is on", async ({ page }) => {
    await setupManagerDashboard(page);
    await page.getByTestId("tab-strategic-intelligence").click();
    await expect(page.getByTestId("section-predictions")).toBeVisible();
    await expect(page.getByTestId("predictions-content")).toBeVisible();
  });

  test("prediction alert items render from mock data", async ({ page }) => {
    await setupManagerDashboard(page);
    await page.getByTestId("tab-strategic-intelligence").click();
    // prediction-0 from buildPredictions
    await expect(page.getByTestId("prediction-0")).toBeVisible();
    await expect(page.getByTestId("prediction-0")).toContainText("Carry Forward Risk");
    await expect(page.getByTestId("prediction-confidence-0")).toContainText("High");
  });

  test("tab bar is NOT shown when strategicIntelligence flag is off", async ({ page }) => {
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.strategicIntelligence = false;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });
    await setupManagerDashboard(page);
    await expect(page.getByTestId("dashboard-tabs")).not.toBeVisible();
  });

  test("Overview tab is active by default", async ({ page }) => {
    await setupManagerDashboard(page);
    await expect(page.getByTestId("tab-overview")).toHaveAttribute("aria-selected", "true");
    await expect(page.getByTestId("tab-strategic-intelligence")).toHaveAttribute("aria-selected", "false");
  });

  test("Strategic Intelligence tab becomes active when clicked", async ({ page }) => {
    await setupManagerDashboard(page);
    await page.getByTestId("tab-strategic-intelligence").click();
    await expect(page.getByTestId("tab-strategic-intelligence")).toHaveAttribute("aria-selected", "true");
    await expect(page.getByTestId("tab-overview")).toHaveAttribute("aria-selected", "false");
  });
});
