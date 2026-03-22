/**
 * Playwright E2E tests for the My Insights page.
 *
 * All tests run deterministically against a mocked API via installMockApi()
 * (no live backend required).
 *
 * Test categories:
 *   [SMOKE] = runs on every PR (Gate 7)
 *   [FULL]  = deeper acceptance coverage, suitable for nightly runs
 */
import { expect, test } from "@playwright/test";
import {
  installMockApi,
  buildTrendsResponse,
  buildUserProfileResponse,
} from "./helpers";

test.describe("My Insights Page", () => {
  test("[SMOKE] My Insights page loads", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");

    await page.getByTestId("nav-my-insights").click();
    await expect(page.getByTestId("my-insights-page")).toBeVisible();
  });

  test("Key metrics render", async ({ page }) => {
    // buildTrendsResponse() yields:
    //   completionAccuracy: 0.85  → "85%"
    //   strategicAlignmentRate: 0.82 → "82%"
    //   avgConfidence: 3.8 (raw; fmtPct renders it as-is)
    await installMockApi(page);
    await page.goto("/");

    await page.getByTestId("nav-my-insights").click();
    await expect(page.getByTestId("my-insights-page")).toBeVisible();

    // Stat cards are visible
    await expect(page.getByTestId("stat-completion")).toBeVisible();
    await expect(page.getByTestId("stat-alignment")).toBeVisible();
    await expect(page.getByTestId("stat-confidence")).toBeVisible();

    // Completion rate shows 85% (completionAccuracy: 0.85 from buildTrendsResponse)
    await expect(page.getByTestId("stat-completion")).toContainText("85%");

    // Strategic alignment shows 82% (strategicAlignmentRate: 0.82)
    await expect(page.getByTestId("stat-alignment")).toContainText("82%");
  });

  test("Profile section renders", async ({ page }) => {
    // buildUserProfileResponse() has weeksAnalyzed: 6 and a full performanceProfile,
    // so hasProfile will be true and the profile-section renders.
    await installMockApi(page);
    await page.goto("/");

    await page.getByTestId("nav-my-insights").click();
    await expect(page.getByTestId("my-insights-page")).toBeVisible();

    // Profile section (completion-by-priority / category bars) is visible
    await expect(page.getByTestId("profile-section")).toBeVisible();
  });

  test("Empty state — shown when trends and profile flags are disabled", async ({ page }) => {
    // Inject localStorage flags BEFORE the app initialises so FeatureFlagProvider
    // picks them up via readPersistedFlags() on first render.
    await page.addInitScript(() => {
      localStorage.setItem(
        "wc-feature-flags",
        JSON.stringify({ icTrends: false, userProfile: false }),
      );
    });

    // The mock API is still installed (routes needed by WeeklyPlanPage etc.),
    // but with the feature flags disabled the hooks never issue a request so
    // trends and profile remain null → empty state is shown.
    await installMockApi(page);
    await page.goto("/");

    await page.getByTestId("nav-my-insights").click();
    await expect(page.getByTestId("my-insights-page")).toBeVisible();

    await expect(page.getByTestId("my-insights-page")).toContainText(
      "Not enough data yet",
    );
  });
});
