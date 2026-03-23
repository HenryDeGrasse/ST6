/**
 * Browser-based E2E tests for the Executive Dashboard page.
 *
 * All tests run deterministically against a mocked API via installMockApi()
 * (no live backend required).
 *
 * The Executive Dashboard is only accessible to users with the ADMIN role
 * and is further gated by the `executiveDashboard` feature flag (default: true).
 *
 * Persona used: Dana Torres (IC + MANAGER + ADMIN) — persona key "dana".
 *
 * Coverage:
 *   [SMOKE] Access guard — non-ADMIN cannot see nav-executive
 *   [SMOKE] Dashboard loads for ADMIN (Dana)
 *   [SMOKE] Flag-disabled → shows disabled message
 *   [SMOKE] Summary stat cards render (total, on-track, attention, at-risk, coverage, confidence)
 *   [SMOKE] Strategic capacity utilization stacked bar renders
 *   [SMOKE] Rally cry health cards render with names and confidence
 *   [SMOKE] Team comparison section renders with bucket names and member counts
 *   [SMOKE] Executive briefing panel renders
 *   [SMOKE] Executive briefing content and insights render
 *           Executive briefing error state when API returns 500
 *           Executive briefing rate-limited state when API returns 429
 *           Executive briefing refresh button triggers re-fetch
 *           Org backlog overview hidden when useIssueBacklog flag is off
 *           Org backlog overview shown when useIssueBacklog flag is on
 *           Week selector renders with prev/next/label controls
 *           Changing week triggers re-fetch of dashboard data
 *
 * Test categories:
 *   [SMOKE] = runs on every PR (Gate 7)
 *   [FULL]  = deeper acceptance coverage, suitable for nightly runs
 */
import { expect, test, type Page } from "@playwright/test";
import {
  installMockApi,
  buildExecutiveDashboardResponse,
  buildExecutiveBriefingResponse,
  json,
  apiError,
  mondayIso,
} from "./helpers";

// ══════════════════════════════════════════════════════════════════════════
// Helpers
// ══════════════════════════════════════════════════════════════════════════

/** Open host-shell dev tools if needed, then switch persona and wait for remount. */
async function switchPersona(page: Page, personaKey: string): Promise<void> {
  const toggle = page.getByRole("button", { name: "Toggle dev tools" });
  const select = page.getByTestId("persona-select");

  if (!(await select.isVisible().catch(() => false))) {
    await toggle.click();
  }

  await expect(select).toBeVisible();
  await select.selectOption(personaKey);
  await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
}

/** Switch to Dana (IC + MANAGER + ADMIN), wait for app remount. */
async function switchToDana(page: Page): Promise<void> {
  await switchPersona(page, "dana");
}

/** Navigate to the Executive Dashboard page. */
async function gotoExecutive(page: Page): Promise<void> {
  await expect(page.getByTestId("nav-executive")).toBeVisible();
  await page.getByTestId("nav-executive").click();
  await expect(page.getByTestId("executive-dashboard-page")).toBeVisible();
}

/** Full setup: mock API → Dana persona → executive page. */
async function setupExecutive(
  page: Page,
  overrides: Parameters<typeof installMockApi>[1] = {},
): Promise<void> {
  await installMockApi(page, {
    executiveDashboard: buildExecutiveDashboardResponse(),
    executiveBriefing: buildExecutiveBriefingResponse(),
    ...overrides,
  });
  await page.goto("/");
  await switchToDana(page);
  await gotoExecutive(page);
}

// ══════════════════════════════════════════════════════════════════════════
// Access Guard
// ══════════════════════════════════════════════════════════════════════════

test.describe("Executive Dashboard — Access Guard", () => {
  test("[SMOKE] non-ADMIN user (Alice) cannot see nav-executive", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");
    await switchPersona(page, "alice");
    await expect(page.getByTestId("nav-executive")).not.toBeVisible();
  });

  test("[SMOKE] ADMIN user (Dana) sees nav-executive and can navigate", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");
    await switchToDana(page);
    await expect(page.getByTestId("nav-executive")).toBeVisible();
    await page.getByTestId("nav-executive").click();
    await expect(page.getByTestId("executive-dashboard-page")).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Feature Flag Gate
// ══════════════════════════════════════════════════════════════════════════

test.describe("Executive Dashboard — Feature Flag Gate", () => {
  test("[SMOKE] executive nav is hidden when executiveDashboard flag is off", async ({ page }) => {
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.executiveDashboard = false;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });
    await installMockApi(page);
    await page.goto("/");
    await switchToDana(page);
    await expect(page.getByTestId("nav-executive")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Summary Stat Cards
// ══════════════════════════════════════════════════════════════════════════

test.describe("Executive Dashboard — Summary Stats", () => {
  test.beforeEach(async ({ page }) => {
    await setupExecutive(page);
  });

  test("[SMOKE] executive-summary-stats section renders", async ({ page }) => {
    await expect(page.getByTestId("executive-summary-stats")).toBeVisible();
  });

  test("[SMOKE] exec-stat-total shows total forecast count", async ({ page }) => {
    // buildExecutiveDashboardResponse: totalForecasts: 12
    const card = page.getByTestId("exec-stat-total");
    await expect(card).toBeVisible();
    await expect(card).toContainText("12");
  });

  test("[SMOKE] exec-stat-on-track shows on-track count", async ({ page }) => {
    // onTrackForecasts: 7
    const card = page.getByTestId("exec-stat-on-track");
    await expect(card).toBeVisible();
    await expect(card).toContainText("7");
  });

  test("[SMOKE] exec-stat-attention shows needs-attention count", async ({ page }) => {
    // needsAttentionForecasts: 3
    await expect(page.getByTestId("exec-stat-attention")).toContainText("3");
  });

  test("[SMOKE] exec-stat-at-risk shows off-track count", async ({ page }) => {
    // offTrackForecasts: 2
    await expect(page.getByTestId("exec-stat-at-risk")).toContainText("2");
  });

  test("[SMOKE] exec-stat-coverage renders planning coverage ring", async ({ page }) => {
    await expect(page.getByTestId("exec-stat-coverage")).toBeVisible();
    // Progress ring should render
    await expect(
      page.getByTestId("exec-stat-coverage").getByTestId("progress-ring"),
    ).toBeVisible();
  });

  test("[SMOKE] exec-stat-confidence shows average confidence", async ({ page }) => {
    // averageForecastConfidence: 0.72 → 72%
    await expect(page.getByTestId("exec-stat-confidence")).toContainText("72%");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Strategic Capacity Utilization
// ══════════════════════════════════════════════════════════════════════════

test.describe("Executive Dashboard — Capacity Utilization", () => {
  test.beforeEach(async ({ page }) => {
    await setupExecutive(page);
  });

  test("[SMOKE] executive-capacity-bar section renders", async ({ page }) => {
    await expect(page.getByTestId("executive-capacity-bar")).toBeVisible();
  });

  test("[SMOKE] stacked-bar component renders within capacity section", async ({ page }) => {
    const capacityBar = page.getByTestId("executive-capacity-bar");
    await expect(capacityBar.getByTestId("stacked-bar")).toBeVisible();
  });

  test("[SMOKE] strategic utilization percentage is displayed", async ({ page }) => {
    // summary.strategicCapacityUtilizationPct: 0.70 → 70%
    await expect(page.getByTestId("executive-capacity-bar")).toContainText("70%");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Rally Cry Health
// ══════════════════════════════════════════════════════════════════════════

test.describe("Executive Dashboard — Rally Cry Health", () => {
  test.beforeEach(async ({ page }) => {
    await setupExecutive(page);
  });

  test("[SMOKE] executive-rally-cries section renders", async ({ page }) => {
    await expect(page.getByTestId("executive-rally-cries")).toBeVisible();
  });

  test("[SMOKE] first rally cry name is displayed", async ({ page }) => {
    // buildExecutiveDashboardResponse: rallyCryRollups[0].rallyCryName = "Scale Revenue"
    await expect(page.getByTestId("executive-rally-cries")).toContainText("Scale Revenue");
  });

  test("[SMOKE] second rally cry name is displayed", async ({ page }) => {
    // rallyCryRollups[1].rallyCryName = "Platform Reliability"
    await expect(page.getByTestId("executive-rally-cries")).toContainText("Platform Reliability");
  });

  test("[SMOKE] rally cry confidence percentages are shown", async ({ page }) => {
    // rallyCryRollups[0].averageForecastConfidence: 0.75 → 75%
    await expect(page.getByTestId("executive-rally-cries")).toContainText("75%");
  });

  test("[SMOKE] rally cry strategic hours are shown", async ({ page }) => {
    // rallyCryRollups[0].strategicHours: 140
    await expect(page.getByTestId("executive-rally-cries")).toContainText("140");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Team Comparison
// ══════════════════════════════════════════════════════════════════════════

test.describe("Executive Dashboard — Team Comparison", () => {
  test.beforeEach(async ({ page }) => {
    await setupExecutive(page);
  });

  test("[SMOKE] executive-team-buckets section renders", async ({ page }) => {
    await expect(page.getByTestId("executive-team-buckets")).toBeVisible();
  });

  test("[SMOKE] engineering team bucket renders", async ({ page }) => {
    // buildExecutiveDashboardResponse: teamBuckets[0].bucketId = "engineering"
    await expect(page.getByTestId("executive-team-buckets")).toContainText("engineering");
  });

  test("[SMOKE] product team bucket renders", async ({ page }) => {
    // teamBuckets[1].bucketId = "product"
    await expect(page.getByTestId("executive-team-buckets")).toContainText("product");
  });

  test("[SMOKE] team member counts are displayed", async ({ page }) => {
    // engineering: memberCount: 8
    await expect(page.getByTestId("executive-team-buckets")).toContainText("8 members");
    // product: memberCount: 4
    await expect(page.getByTestId("executive-team-buckets")).toContainText("4 members");
  });

  test("[SMOKE] HBar components render within team cards", async ({ page }) => {
    const teamSection = page.getByTestId("executive-team-buckets");
    // Each team card has Plan Coverage + Strategic Utilization HBars
    await expect(teamSection.getByTestId("hbar").first()).toBeVisible();
  });

  test("[SMOKE] team average confidence is displayed", async ({ page }) => {
    // teamBuckets[0].averageForecastConfidence: 0.74 → 74%
    await expect(page.getByTestId("executive-team-buckets")).toContainText("74%");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Executive Briefing
// ══════════════════════════════════════════════════════════════════════════

test.describe("Executive Dashboard — Executive Briefing", () => {
  test.beforeEach(async ({ page }) => {
    await setupExecutive(page);
  });

  test("[SMOKE] executive-briefing section renders", async ({ page }) => {
    await expect(page.getByTestId("executive-briefing")).toBeVisible();
  });

  test("[SMOKE] briefing content renders with headline", async ({ page }) => {
    await expect(page.getByTestId("executive-briefing-content")).toBeVisible();
    // buildExecutiveBriefingResponse: "7 of 12 outcomes are on track"
    await expect(page.getByTestId("executive-briefing-content")).toContainText(
      "7 of 12 outcomes are on track",
    );
  });

  test("[SMOKE] briefing insights render", async ({ page }) => {
    // At least insight-0 from the response
    await expect(page.getByTestId("executive-briefing-insight-0")).toBeVisible();
    await expect(page.getByTestId("executive-briefing-insight-1")).toBeVisible();
  });

  test("[SMOKE] briefing refresh button is visible", async ({ page }) => {
    await expect(page.getByTestId("executive-briefing-refresh")).toBeVisible();
  });

  test("briefing error state shown when POST /ai/executive-briefing returns 500", async ({
    page,
  }) => {
    await installMockApi(page, {
      executiveDashboard: buildExecutiveDashboardResponse(),
      executiveBriefing: null, // triggers 404 in installMockApi
    });
    // Override briefing route to return 500
    await page.route(/\/api\/v1\/ai\/executive-briefing$/, async (route) => {
      if (route.request().method() === "POST") {
        return json(route, 500, apiError("Briefing generation failed", "INTERNAL_ERROR"));
      }
      return route.fallback();
    });
    await page.goto("/");
    await switchToDana(page);
    await gotoExecutive(page);

    // Error or unavailable state should show
    const errorEl = page.getByTestId("executive-briefing-error");
    const unavailableEl = page.getByTestId("executive-briefing-unavailable");
    await expect(errorEl.or(unavailableEl)).toBeVisible();
  });

  test("briefing rate-limited state shown when POST returns 429", async ({ page }) => {
    await installMockApi(page, {
      executiveDashboard: buildExecutiveDashboardResponse(),
      executiveBriefing: null,
    });
    await page.route(/\/api\/v1\/ai\/executive-briefing$/, async (route) => {
      if (route.request().method() === "POST") {
        return json(route, 429, apiError("Rate limit exceeded", "RATE_LIMITED"));
      }
      return route.fallback();
    });
    await page.goto("/");
    await switchToDana(page);
    await gotoExecutive(page);

    await expect(page.getByTestId("executive-briefing-rate-limited")).toBeVisible();
  });

  test("refresh button triggers re-fetch of briefing", async ({ page }) => {
    let briefingCallCount = 0;
    // Register a counting route AFTER installMockApi (LIFO)
    await installMockApi(page, {
      executiveDashboard: buildExecutiveDashboardResponse(),
      executiveBriefing: buildExecutiveBriefingResponse(),
    });
    await page.route(/\/api\/v1\/ai\/executive-briefing$/, async (route) => {
      if (route.request().method() === "POST") {
        briefingCallCount++;
        return json(route, 200, buildExecutiveBriefingResponse());
      }
      return route.fallback();
    });

    await page.goto("/");
    await switchToDana(page);
    await gotoExecutive(page);
    await expect(page.getByTestId("executive-briefing-content")).toBeVisible();

    const before = briefingCallCount;
    await page.getByTestId("executive-briefing-refresh").click();
    await page.waitForTimeout(400);
    expect(briefingCallCount).toBeGreaterThan(before);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Org Backlog Overview (Phase 6, gated by useIssueBacklog flag)
// ══════════════════════════════════════════════════════════════════════════

test.describe("Executive Dashboard — Org Backlog Overview", () => {
  test("[SMOKE] exec-backlog-metrics section is hidden when useIssueBacklog is off", async ({
    page,
  }) => {
    // Default: useIssueBacklog = false
    await setupExecutive(page);
    await expect(page.getByTestId("exec-backlog-metrics")).not.toBeVisible();
  });

  test("[SMOKE] exec-backlog-metrics section renders when useIssueBacklog is on", async ({
    page,
  }) => {
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.useIssueBacklog = true;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });

    await installMockApi(page, {
      executiveDashboard: buildExecutiveDashboardResponse(),
      executiveBriefing: buildExecutiveBriefingResponse(),
    });

    // Stub the org backlog health analytics endpoint
    await page.route(/\/api\/v1\/analytics\/teams\/backlog-health(\?.*)?$/, async (route) => {
      if (route.request().method() === "GET") {
        return json(route, 200, [
          {
            teamId: "engineering",
            openIssueCount: 25,
            avgIssueAgeDays: 12,
            blockedCount: 2,
            buildCount: 10,
            maintainCount: 8,
            collaborateCount: 4,
            learnCount: 3,
            avgCycleTimeDays: 14,
          },
          {
            teamId: "product",
            openIssueCount: 15,
            avgIssueAgeDays: 8,
            blockedCount: 1,
            buildCount: 6,
            maintainCount: 5,
            collaborateCount: 3,
            learnCount: 1,
            avgCycleTimeDays: 10,
          },
        ]);
      }
      return route.fallback();
    });

    await page.goto("/");
    await switchToDana(page);
    await gotoExecutive(page);

    await expect(page.getByTestId("exec-backlog-metrics")).toBeVisible();
    // Total open = 25 + 15 = 40
    await expect(page.getByTestId("exec-backlog-open")).toContainText("40");
    // Team count = 2
    await expect(page.getByTestId("exec-backlog-teams")).toContainText("2");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Week Selector
// ══════════════════════════════════════════════════════════════════════════

test.describe("Executive Dashboard — Week Selector", () => {
  test.beforeEach(async ({ page }) => {
    await setupExecutive(page);
  });

  test("[SMOKE] week-selector renders with prev/next/label controls", async ({ page }) => {
    await expect(page.getByTestId("week-selector")).toBeVisible();
    await expect(page.getByTestId("week-prev")).toBeVisible();
    await expect(page.getByTestId("week-next")).toBeVisible();
    await expect(page.getByTestId("week-label")).toBeVisible();
  });

  test("clicking Prev week button changes the displayed week label", async ({ page }) => {
    const currentLabel = await page.getByTestId("week-label").textContent();
    await page.getByTestId("week-prev").click();
    const prevLabel = await page.getByTestId("week-label").textContent();
    expect(prevLabel).not.toEqual(currentLabel);
  });

  test("clicking Next week button changes the displayed week label", async ({ page }) => {
    // First go back so next has somewhere to go
    await page.getByTestId("week-prev").click();
    const labelAfterPrev = await page.getByTestId("week-label").textContent();
    await page.getByTestId("week-next").click();
    const labelAfterNext = await page.getByTestId("week-label").textContent();
    expect(labelAfterNext).not.toEqual(labelAfterPrev);
  });

  test("changing week triggers re-fetch of dashboard data", async ({ page }) => {
    let lastFetchedWeek: string | null = null;
    await page.route(/\/api\/v1\/executive\/strategic-health(\?.*)?$/, async (route) => {
      if (route.request().method() === "GET") {
        const url = new URL(route.request().url());
        lastFetchedWeek = url.searchParams.get("weekStart");
        return json(route, 200, buildExecutiveDashboardResponse());
      }
      return route.fallback();
    });

    await page.getByTestId("week-prev").click();
    await page.waitForTimeout(400);

    const expectedPrevWeek = mondayIso(-1);
    expect(lastFetchedWeek).toBe(expectedPrevWeek);
  });
});
