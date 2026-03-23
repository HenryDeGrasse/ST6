/**
 * Browser-based E2E tests for persona switching, role-gated navigation,
 * URL routing, browser history navigation, week-selector 'Today' button,
 * and wc:navigate custom event routing.
 *
 * Personas (from pa-host-stub/src/HostShell.tsx):
 *   carol  — IC + MANAGER (default)
 *   alice  — IC only
 *   bob    — IC only
 *   dana   — IC + MANAGER + ADMIN  (executiveDashboard flag defaults ON)
 *
 * Coverage:
 *
 * Role-based navigation:
 *   [SMOKE] Persona switcher remounts the micro-frontend with the new user
 *   [SMOKE] IC-only user (alice) sees My Plan, Backlog, My Insights — NOT Team Dashboard or Admin
 *   [SMOKE] Manager (carol) sees Team Dashboard tab
 *   [SMOKE] Admin (dana) sees Admin tab
 *   [SMOKE] Admin+executiveDashboard (dana) sees Executive tab
 *           My Insights nav item is available for all roles
 *
 * URL-based routing:
 *   [SMOKE] /backlog URL renders BacklogPage
 *   [SMOKE] /insights URL renders MyInsightsPage
 *   [SMOKE] /teamdashboard URL renders TeamDashboardPage (manager persona)
 *   [SMOKE] Clicking nav-admin navigates to /admin and renders AdminDashboardPage
 *   [SMOKE] Clicking nav-executive navigates to /executive and renders ExecutiveDashboardPage
 *
 * Browser history navigation:
 *   [SMOKE] browser back/forward navigation restores prior page
 *
 * Week selector:
 *   [SMOKE] 'Today' button appears after navigating to past week, jumps back to current week
 *
 * wc:navigate custom event:
 *   [SMOKE] dispatching wc:navigate({route:'weekly/backlog'}) shows BacklogPage
 *   [SMOKE] dispatching wc:navigate({route:'weekly/insights'}) shows MyInsightsPage
 *   [SMOKE] dispatching wc:navigate({route:'weekly/team'}) shows TeamDashboard (manager persona)
 *   [SMOKE] dispatching wc:navigate({route:'admin'}) shows AdminDashboard (admin persona)
 */
import { expect, test, type Page } from "@playwright/test";
import {
  installMockApi,
  json,
  mondayIso,
  MOCK_OUTCOME_ID,
  MOCK_NOW,
} from "./helpers";

// ══════════════════════════════════════════════════════════════════════════
// Helpers
// ══════════════════════════════════════════════════════════════════════════

/**
 * Opens the dev-tools panel (if not already open) and switches the persona.
 * Waits for the weekly-commitments-app container to be visible after remount.
 */
async function switchPersona(page: Page, personaKey: string): Promise<void> {
  const select = page.getByTestId("persona-select");
  if (!(await select.isVisible().catch(() => false))) {
    await page.getByRole("button", { name: "Toggle dev tools" }).click();
  }
  await expect(select).toBeVisible();
  await select.selectOption(personaKey);
  await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
}

/**
 * Installs admin-specific mock routes (adoption-metrics, ai-usage, rcdo-health)
 * in addition to the base installMockApi routes.
 */
async function installAdminMocks(page: Page): Promise<void> {
  await page.route(/\/api\/v1\/admin\/adoption-metrics(\?.*)?$/, async (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    return json(route, 200, {
      weeks: 8,
      windowStart: mondayIso(-7),
      windowEnd: mondayIso(0),
      totalActiveUsers: 12,
      cadenceComplianceRate: 0.82,
      weeklyPoints: Array.from({ length: 8 }, (_, i) => ({
        weekStart: mondayIso(-(7 - i)),
        activeUsers: 10 + i,
        plansCreated: 10 + i,
        plansLocked: 8 + i,
        plansReconciled: 6 + i,
        plansReviewed: 4 + i,
      })),
    });
  });

  await page.route(/\/api\/v1\/admin\/ai-usage(\?.*)?$/, async (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    return json(route, 200, {
      weeks: 8,
      windowStart: mondayIso(-7),
      windowEnd: mondayIso(0),
      totalFeedbackCount: 80,
      acceptedCount: 60,
      deferredCount: 10,
      declinedCount: 10,
      acceptanceRate: 0.75,
      cacheHits: 200,
      cacheMisses: 40,
      cacheHitRate: 0.83,
      approximateTokensSpent: 40_000,
      approximateTokensSaved: 200_000,
    });
  });

  await page.route(/\/api\/v1\/admin\/rcdo-health(\?.*)?$/, async (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    return json(route, 200, {
      generatedAt: MOCK_NOW,
      windowWeeks: 8,
      totalOutcomes: 2,
      coveredOutcomes: 2,
      topOutcomes: [
        {
          outcomeId: MOCK_OUTCOME_ID,
          outcomeName: "Increase trial-to-paid by 20%",
          objectiveId: "obj-1",
          objectiveName: "Improve Conversion",
          rallyCryId: "rc-1",
          rallyCryName: "Scale Revenue",
          commitCount: 10,
        },
      ],
      staleOutcomes: [],
    });
  });

  // executive/strategic-health is needed for ExecutiveDashboardPage
  await page.route(/\/api\/v1\/executive\/strategic-health(\?.*)?$/, async (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    return json(route, 200, {
      weekStart: mondayIso(),
      summary: {
        totalForecasts: 10,
        onTrackForecasts: 6,
        needsAttentionForecasts: 3,
        offTrackForecasts: 1,
        noDataForecasts: 0,
        averageForecastConfidence: 0.72,
        totalCapacityHours: 240,
        strategicHours: 168,
        nonStrategicHours: 72,
        strategicCapacityUtilizationPct: 0.70,
        nonStrategicCapacityUtilizationPct: 0.30,
        planningCoveragePct: 0.85,
      },
      rallyCryRollups: [],
      teamBuckets: [],
      teamGroupingAvailable: false,
    });
  });
}

// ══════════════════════════════════════════════════════════════════════════
// Test suite: Role-based navigation
// ══════════════════════════════════════════════════════════════════════════

test.describe("Role-based navigation", () => {
  test("[SMOKE] Persona switcher remounts the micro-frontend with the new user", async ({
    page,
  }) => {
    await installMockApi(page);
    await page.goto("/");

    // App starts as Carol (IC + MANAGER) — team-dashboard nav should be present
    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("nav-team-dashboard")).toBeVisible();

    // Switch to Alice (IC only) — team-dashboard should disappear after remount
    await switchPersona(page, "alice");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("nav-team-dashboard")).not.toBeVisible();
  });

  test("[SMOKE] IC-only user sees My Plan, Backlog, My Insights — NOT Team Dashboard, Admin, or Executive", async ({
    page,
  }) => {
    await installMockApi(page);
    await page.goto("/");

    // Switch to Alice Chen (IC only — no MANAGER or ADMIN roles)
    await switchPersona(page, "alice");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();

    // IC nav items must be present
    await expect(page.getByTestId("nav-my-plan")).toBeVisible();
    await expect(page.getByTestId("nav-backlog")).toBeVisible();
    await expect(page.getByTestId("nav-my-insights")).toBeVisible();

    // Manager/Admin nav items must be absent
    await expect(page.getByTestId("nav-team-dashboard")).not.toBeVisible();
    await expect(page.getByTestId("nav-admin")).not.toBeVisible();
    await expect(page.getByTestId("nav-executive")).not.toBeVisible();
  });

  test("[SMOKE] Manager user (carol) sees Team Dashboard tab", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");

    // Carol is IC + MANAGER — Team Dashboard nav should be present by default
    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("nav-team-dashboard")).toBeVisible();

    // Carol is not ADMIN — admin/executive tabs should not be visible
    await expect(page.getByTestId("nav-admin")).not.toBeVisible();
    await expect(page.getByTestId("nav-executive")).not.toBeVisible();
  });

  test("[SMOKE] Admin user (dana) sees Admin tab", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");

    // Switch to Dana (IC + MANAGER + ADMIN)
    await switchPersona(page, "dana");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("nav-admin")).toBeVisible();
  });

  test("[SMOKE] Admin user (dana) sees Executive tab (executiveDashboard flag defaults ON)", async ({
    page,
  }) => {
    await installMockApi(page);
    await page.goto("/");

    // Switch to Dana — executiveDashboard flag is true by default in FeatureFlagContext
    await switchPersona(page, "dana");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("nav-executive")).toBeVisible();
  });

  test("My Insights nav item is available for all roles", async ({ page }) => {
    await installMockApi(page);

    const personas = ["carol", "alice", "bob", "dana"] as const;

    for (const persona of personas) {
      await page.goto("/");
      await switchPersona(page, persona);

      await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
      await expect(page.getByTestId("nav-my-insights")).toBeVisible();
    }
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Test suite: URL-based routing
// ══════════════════════════════════════════════════════════════════════════

test.describe("URL-based routing", () => {
  test("[SMOKE] /backlog URL renders BacklogPage", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/backlog");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("backlog-page")).toBeVisible();

    // nav-backlog should be visually active (bold/selected)
    await expect(page.getByTestId("nav-backlog")).toBeVisible();
  });

  test("[SMOKE] /insights URL renders MyInsightsPage", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/insights");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("my-insights-page")).toBeVisible();

    await expect(page.getByTestId("nav-my-insights")).toBeVisible();
  });

  test("[SMOKE] /teamdashboard URL renders TeamDashboardPage (manager persona carol)", async ({
    page,
  }) => {
    await installMockApi(page);
    // carol is IC + MANAGER by default — /teamdashboard is accessible
    await page.goto("/teamdashboard");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("team-dashboard-page")).toBeVisible();

    await expect(page.getByTestId("nav-team-dashboard")).toBeVisible();
  });

  test("[SMOKE] /admin path routing renders AdminDashboardPage for an admin user", async ({ page }) => {
    await installMockApi(page);
    await installAdminMocks(page);
    await page.goto("/");

    // Switch to Dana first. We then change the URL in-place so persona state is preserved.
    await switchPersona(page, "dana");

    await page.evaluate(() => {
      window.history.pushState({}, "", "/admin");
      window.dispatchEvent(new PopStateEvent("popstate"));
    });

    await expect(page.getByTestId("admin-dashboard-page")).toBeVisible();
    await expect(page).toHaveURL(/\/admin$/);
  });

  test("[SMOKE] /executive path routing renders ExecutiveDashboardPage for an admin user", async ({ page }) => {
    await installMockApi(page);
    await installAdminMocks(page);
    await page.goto("/");

    // Switch to Dana first. We then change the URL in-place so persona state is preserved.
    await switchPersona(page, "dana");

    await page.evaluate(() => {
      window.history.pushState({}, "", "/executive");
      window.dispatchEvent(new PopStateEvent("popstate"));
    });

    await expect(page.getByTestId("executive-dashboard-page")).toBeVisible();
    await expect(page).toHaveURL(/\/executive$/);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Test suite: Browser history navigation (back/forward)
// ══════════════════════════════════════════════════════════════════════════

test.describe("Browser history navigation", () => {
  test("[SMOKE] browser back/forward navigation restores prior page", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");

    // Start on WeeklyPlanPage (/)
    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("weekly-plan-page")).toBeVisible();

    // Navigate to backlog
    await page.getByTestId("nav-backlog").click();
    await expect(page.getByTestId("backlog-page")).toBeVisible();
    await expect(page).toHaveURL(/\/backlog$/);

    // Navigate to my insights
    await page.getByTestId("nav-my-insights").click();
    await expect(page.getByTestId("my-insights-page")).toBeVisible();
    await expect(page).toHaveURL(/\/insights$/);

    // Browser back → should restore backlog
    await page.goBack();
    await expect(page.getByTestId("backlog-page")).toBeVisible();
    await expect(page).toHaveURL(/\/backlog$/);

    // Browser forward → should restore my insights
    await page.goForward();
    await expect(page.getByTestId("my-insights-page")).toBeVisible();
    await expect(page).toHaveURL(/\/insights$/);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Test suite: Week selector 'Today' button
// ══════════════════════════════════════════════════════════════════════════

test.describe("Week selector — Today button", () => {
  test("[SMOKE] 'Today' button appears after navigating to past week and returns to current week", async ({
    page,
  }) => {
    await installMockApi(page);
    await page.goto("/");

    // WeeklyPlanPage renders with week selector
    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("week-selector")).toBeVisible();

    // 'Today' button is NOT visible when already on the current week
    await expect(page.getByTestId("week-today")).not.toBeVisible();

    // Navigate to previous week
    await page.getByTestId("week-prev").click();

    // 'Today' button should now appear
    await expect(page.getByTestId("week-today")).toBeVisible();

    // Click 'Today' to jump back to current week
    await page.getByTestId("week-today").click();

    // 'Today' button should disappear again (we're on the current week)
    await expect(page.getByTestId("week-today")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Test suite: wc:navigate custom event routing
// ══════════════════════════════════════════════════════════════════════════

test.describe("wc:navigate custom event routing", () => {
  test("[SMOKE] dispatching wc:navigate({route:'weekly/backlog'}) shows BacklogPage", async ({
    page,
  }) => {
    await installMockApi(page);
    await page.goto("/");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();

    // Dispatch custom navigation event
    await page.evaluate(() => {
      window.dispatchEvent(
        new CustomEvent("wc:navigate", { detail: { route: "weekly/backlog" } }),
      );
    });

    await expect(page.getByTestId("backlog-page")).toBeVisible();
  });

  test("[SMOKE] dispatching wc:navigate({route:'weekly/insights'}) shows MyInsightsPage", async ({
    page,
  }) => {
    await installMockApi(page);
    await page.goto("/");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();

    await page.evaluate(() => {
      window.dispatchEvent(
        new CustomEvent("wc:navigate", { detail: { route: "weekly/insights" } }),
      );
    });

    await expect(page.getByTestId("my-insights-page")).toBeVisible();
  });

  test("[SMOKE] dispatching wc:navigate({route:'weekly/team'}) shows TeamDashboardPage (manager persona)", async ({
    page,
  }) => {
    await installMockApi(page);
    // carol (IC + MANAGER) is the default persona — can access team dashboard
    await page.goto("/");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();

    await page.evaluate(() => {
      window.dispatchEvent(
        new CustomEvent("wc:navigate", { detail: { route: "weekly/team" } }),
      );
    });

    await expect(page.getByTestId("team-dashboard-page")).toBeVisible();
  });

  test("[SMOKE] dispatching wc:navigate({route:'admin'}) shows AdminDashboardPage (admin persona)", async ({
    page,
  }) => {
    await installMockApi(page);
    await installAdminMocks(page);
    await page.goto("/");

    // Switch to Dana first (ADMIN role required to render AdminDashboardPage)
    await switchPersona(page, "dana");
    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();

    // Dispatch navigate event to admin route
    await page.evaluate(() => {
      window.dispatchEvent(
        new CustomEvent("wc:navigate", { detail: { route: "admin" } }),
      );
    });

    await expect(page.getByTestId("admin-dashboard-page")).toBeVisible();
  });
});
