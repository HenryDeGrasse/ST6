/**
 * Browser-based E2E tests for MyInsightsPage (IC Trends & User Profile).
 *
 * All tests run deterministically against the mocked API via installMockApi()
 * — no live backend required.
 *
 * Coverage:
 *   [SMOKE] Navigation to My Insights tab
 *   [SMOKE] Key metric stat cards render with correct values
 *           - completion rate, strategic alignment, avg confidence,
 *             commits/week, carry-forward, avg hours
 *   [SMOKE] Sparklines render inside stat cards
 *           Distribution section — category donut, priority bars
 *   [SMOKE] Performance profile section renders
 *           Work habits section renders
 *           Signals / trend-badges section renders
 *   [FULL]  Hours comparison section renders
 *           Insights list within signals section
 *           Profile completion-by-priority and completion-by-category bars
 *           Profile key metrics values
 *           Work habits day chips, recurring work, check-in count
 *           Empty state when feature flags are off
 *           Error state when API returns 500
 */
import { expect, test, type Page } from "@playwright/test";
import {
  installMockApi,
  buildTrendsResponse,
  buildUserProfileResponse,
  json,
  apiError,
} from "./helpers";

// ── helpers ────────────────────────────────────────────────────────────────

/** Navigate to the My Insights page using the nav button. */
async function gotoInsights(page: Page) {
  await page.getByTestId("nav-my-insights").click();
  await expect(page.getByTestId("my-insights-page")).toBeVisible();
}

// ══════════════════════════════════════════════════════════════════════════
// Navigation
// ══════════════════════════════════════════════════════════════════════════

test.describe("My Insights — Navigation", () => {
  test("[SMOKE] clicking My Insights nav renders the page", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");
    await gotoInsights(page);
  });

  test("direct URL /insights renders the page", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/insights");
    await expect(page.getByTestId("my-insights-page")).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Key Metric Stat Cards
// ══════════════════════════════════════════════════════════════════════════

test.describe("My Insights — Key Metric Stat Cards", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");
    await gotoInsights(page);
  });

  test("[SMOKE] key-metrics-section is visible", async ({ page }) => {
    await expect(page.getByTestId("key-metrics-section")).toBeVisible();
  });

  test("[SMOKE] stat-completion shows 85%", async ({ page }) => {
    // buildTrendsResponse has completionAccuracy: 0.85
    const card = page.getByTestId("stat-completion");
    await expect(card).toBeVisible();
    await expect(card).toContainText("85%");
  });

  test("[SMOKE] stat-alignment shows 82% and vs-team label", async ({ page }) => {
    // buildTrendsResponse has strategicAlignmentRate: 0.82, teamStrategicAlignmentRate: 0.74
    const card = page.getByTestId("stat-alignment");
    await expect(card).toBeVisible();
    await expect(card).toContainText("82%");
    await expect(card).toContainText("74%");
  });

  test("[SMOKE] stat-confidence is visible", async ({ page }) => {
    // buildTrendsResponse has avgConfidence: 3.8
    await expect(page.getByTestId("stat-confidence")).toBeVisible();
  });

  test("[SMOKE] stat-commits is visible with a numeric value", async ({ page }) => {
    const card = page.getByTestId("stat-commits");
    await expect(card).toBeVisible();
    // Should contain a decimal number like "6.0"
    await expect(card).toContainText(/\d+\.\d/);
  });

  test("[SMOKE] stat-carry-forward is visible", async ({ page }) => {
    const card = page.getByTestId("stat-carry-forward");
    await expect(card).toBeVisible();
    // avgCarryForwardPerWeek: 0.5
    await expect(card).toContainText("0.5/wk");
  });

  test("stat-hours renders estimated vs actual hours", async ({ page }) => {
    // buildTrendsResponse has avgEstimatedHoursPerWeek: 32.0, avgActualHoursPerWeek: 28.5
    const card = page.getByTestId("stat-hours");
    await expect(card).toBeVisible();
    await expect(card).toContainText("28.5h");
    await expect(card).toContainText("32.0h");
  });

  test("[SMOKE] stat cards contain sparkline elements", async ({ page }) => {
    // Sparklines render inside stat cards that have sparkData (completion, alignment, etc.)
    const sparklines = page.locator("[data-testid='stat-completion'] [data-testid='sparkline']");
    await expect(sparklines.first()).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Hours Comparison Section
// ══════════════════════════════════════════════════════════════════════════

test.describe("My Insights — Hours Comparison", () => {
  test("hours-section renders when trend data has estimatedHours", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");
    await gotoInsights(page);
    // buildTrendsResponse weekPoints include estimatedHours
    await expect(page.getByTestId("hours-section")).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Distribution Section (Category Donut + Priority Bars)
// ══════════════════════════════════════════════════════════════════════════

test.describe("My Insights — Distribution Charts", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");
    await gotoInsights(page);
  });

  test("[SMOKE] distribution-section is visible", async ({ page }) => {
    await expect(page.getByTestId("distribution-section")).toBeVisible();
  });

  test("[SMOKE] category donut chart renders", async ({ page }) => {
    const donut = page.getByTestId("distribution-section").getByTestId("category-donut");
    await expect(donut).toBeVisible();
  });

  test("[SMOKE] priority distribution bars render", async ({ page }) => {
    // HBar components render with data-testid="hbar"
    const bars = page.getByTestId("distribution-section").getByTestId("hbar");
    await expect(bars.first()).toBeVisible();
    // KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN = 6 bars
    await expect(bars).toHaveCount(6);
  });

  test("effort-type-section is hidden when effortTypeDistribution is absent", async ({ page }) => {
    // Default buildTrendsResponse has no effortTypeDistribution key
    await expect(page.getByTestId("effort-type-section")).not.toBeVisible();
  });

  test("effort-type-section renders when effortTypeDistribution is present", async ({ page }) => {
    // Override the trends route to include effortTypeDistribution
    const trendsWithEffort = {
      ...buildTrendsResponse(),
      effortTypeDistribution: {
        SYNCHRONOUS: 0.4,
        ASYNCHRONOUS: 0.35,
        DEEP_WORK: 0.25,
      },
    };
    await page.route("**/api/v1/users/me/trends", (route) =>
      json(route, 200, trendsWithEffort),
    );
    await page.reload();
    await gotoInsights(page);

    await expect(page.getByTestId("effort-type-section")).toBeVisible();
    await expect(page.getByTestId("effort-type-chart")).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Performance Profile Section
// ══════════════════════════════════════════════════════════════════════════

test.describe("My Insights — Performance Profile", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");
    await gotoInsights(page);
  });

  test("[SMOKE] profile-section is visible", async ({ page }) => {
    await expect(page.getByTestId("profile-section")).toBeVisible();
  });

  test("[SMOKE] completion-by-priority bars render", async ({ page }) => {
    // First profileCard within profile-section: "Completion by Priority" (6 HBars)
    const profileSection = page.getByTestId("profile-section");
    const hbars = profileSection.getByTestId("hbar");
    // KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN (6) + DELIVERY, PLANNING, COLLABORATION, LEARNING (4) = 10
    await expect(hbars).toHaveCount(10);
  });

  test("profile key metrics show correct values", async ({ page }) => {
    const profileSection = page.getByTestId("profile-section");
    // buildUserProfileResponse estimationAccuracy: 0.87 → 87%
    await expect(profileSection).toContainText("87%");
    // completionReliability: 0.88 → 88%
    await expect(profileSection).toContainText("88%");
    // avgCommitsPerWeek: 5.5
    await expect(profileSection).toContainText("5.5");
    // avgCarryForwardPerWeek: 0.4
    await expect(profileSection).toContainText("0.4");
  });

  test("profile subtext shows weeksAnalyzed from profile", async ({ page }) => {
    const profileSection = page.getByTestId("profile-section");
    // buildUserProfileResponse weeksAnalyzed: 6
    await expect(profileSection).toContainText("6 weeks");
  });

  test("completion-by-category bars render expected labels", async ({ page }) => {
    const profileSection = page.getByTestId("profile-section");
    await expect(profileSection).toContainText("Delivery");
    await expect(profileSection).toContainText("Planning");
    await expect(profileSection).toContainText("Collaboration");
    await expect(profileSection).toContainText("Learning");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Work Habits Section
// ══════════════════════════════════════════════════════════════════════════

test.describe("My Insights — Work Habits", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");
    await gotoInsights(page);
  });

  test("[SMOKE] habits-section is visible", async ({ page }) => {
    await expect(page.getByTestId("habits-section")).toBeVisible();
  });

  test("typical priority pattern renders", async ({ page }) => {
    // buildUserProfileResponse typicalPriorityPattern: 'Balanced — 1 KING, 2 QUEENs, 2 ROOKs'
    await expect(page.getByTestId("habits-section")).toContainText("Balanced");
  });

  test("check-ins per week renders", async ({ page }) => {
    // buildUserProfileResponse avgCheckInsPerWeek: 2.3
    await expect(page.getByTestId("habits-section")).toContainText("2.3");
  });

  test("preferred update day chips highlight Tuesday and Thursday", async ({ page }) => {
    // buildUserProfileResponse preferredUpdateDays: ['Tuesday', 'Thursday']
    const habitsSection = page.getByTestId("habits-section");
    // The day chips show abbreviated day codes TUE and THU
    await expect(habitsSection).toContainText("TUE");
    await expect(habitsSection).toContainText("THU");
  });

  test("recurring commit titles render", async ({ page }) => {
    // buildUserProfileResponse recurringCommitTitles: ['Weekly sync prep', 'Code review']
    const habitsSection = page.getByTestId("habits-section");
    await expect(habitsSection).toContainText("Weekly sync prep");
    await expect(habitsSection).toContainText("Code review");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Signals / Trend Badges Section
// ══════════════════════════════════════════════════════════════════════════

test.describe("My Insights — Signals & Trend Badges", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");
    await gotoInsights(page);
  });

  test("[SMOKE] signals-section is visible", async ({ page }) => {
    await expect(page.getByTestId("signals-section")).toBeVisible();
  });

  test("[SMOKE] trend badges show alignment, completion, carry-forward", async ({ page }) => {
    // buildUserProfileResponse trends: { strategicAlignmentTrend: 'IMPROVING', completionTrend: 'STABLE', carryForwardTrend: 'IMPROVING' }
    const signalsSection = page.getByTestId("signals-section");
    await expect(signalsSection).toContainText("improving");
    await expect(signalsSection).toContainText("stable");
    // Carry-forward trend badge
    await expect(signalsSection).toContainText("Carry-forward");
  });

  test("insights list shows POSITIVE and CONSISTENT_COMPLETION messages", async ({ page }) => {
    // buildTrendsResponse has 2 insights: HIGH_STRATEGIC_ALIGNMENT and CONSISTENT_COMPLETION
    const signalsSection = page.getByTestId("signals-section");
    await expect(signalsSection).toContainText("82%");
    await expect(signalsSection).toContainText("80% completion");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Empty State
// ══════════════════════════════════════════════════════════════════════════

test.describe("My Insights — Empty State", () => {
  test("[SMOKE] empty state when feature flags icTrends + userProfile are off", async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem(
        "wc-feature-flags",
        JSON.stringify({ icTrends: false, userProfile: false }),
      );
    });
    await installMockApi(page);
    await page.goto("/");
    await gotoInsights(page);

    await expect(page.getByTestId("my-insights-page")).toContainText("Not enough data yet");
    // No sections should be rendered
    await expect(page.getByTestId("key-metrics-section")).not.toBeVisible();
    await expect(page.getByTestId("profile-section")).not.toBeVisible();
  });

  test("minimal trend/profile responses hide profile-specific sections", async ({ page }) => {
    await installMockApi(page);

    // Register these AFTER installMockApi so they take precedence.
    await page.route("**/api/v1/users/me/trends", (route) =>
      json(route, 200, {
        weeksAnalyzed: 0,
        windowStart: null,
        windowEnd: null,
        strategicAlignmentRate: 0,
        teamStrategicAlignmentRate: 0,
        avgCarryForwardPerWeek: 0,
        carryForwardStreak: 0,
        avgConfidence: 0,
        completionAccuracy: 0,
        confidenceAccuracyGap: 0,
        avgEstimatedHoursPerWeek: null,
        avgActualHoursPerWeek: null,
        hoursAccuracyRatio: null,
        priorityDistribution: {},
        categoryDistribution: {},
        weekPoints: [],
        insights: [],
      }),
    );
    await page.route("**/api/v1/users/me/profile", (route) =>
      json(route, 200, {
        userId: "mock-user",
        weeksAnalyzed: 0,
        performanceProfile: null,
        preferences: null,
        trends: null,
      }),
    );

    await page.goto("/");
    await gotoInsights(page);

    await expect(page.getByTestId("key-metrics-section")).toBeVisible();
    await expect(page.getByTestId("profile-section")).not.toBeVisible();
    await expect(page.getByTestId("habits-section")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Error State
// ══════════════════════════════════════════════════════════════════════════

test.describe("My Insights — Error State", () => {
  test("error state shown when trends API returns 500", async ({ page }) => {
    await installMockApi(page);

    // Register these AFTER installMockApi so they take precedence.
    await page.route("**/api/v1/users/me/trends", (route) =>
      json(route, 500, apiError("Internal server error", "INTERNAL_ERROR")),
    );
    await page.route("**/api/v1/users/me/profile", (route) =>
      json(route, 500, apiError("Internal server error", "INTERNAL_ERROR")),
    );

    await page.goto("/");
    await gotoInsights(page);

    await expect(page.getByText("Internal server error")).toBeVisible();
  });
});
