/**
 * Playwright E2E tests for the Executive Dashboard page.
 *
 * All tests run deterministically against a mocked API via installMockApi()
 * (no live backend required).
 *
 * The Executive Dashboard is only accessible to users with the ADMIN role
 * and is further gated by the `executiveDashboard` feature flag (default: true).
 *
 * Persona used: Dana Torres (IC + MANAGER + ADMIN) — persona key "dana".
 *
 * Test categories:
 *   [SMOKE] = runs on every PR (Gate 7)
 *   [FULL]  = deeper acceptance coverage, suitable for nightly runs
 */
import { expect, test } from "@playwright/test";
import {
  installMockApi,
  buildExecutiveDashboardResponse,
  buildExecutiveBriefingResponse,
} from "./helpers";

test.describe("Executive Dashboard Page", () => {
  test("[SMOKE] Executive dashboard loads for admin", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");

    // Select Dana Torres (ADMIN persona)
    await page.selectOption('[data-testid="persona-select"]', "dana");

    // Wait for the remounted app to be ready, then click the inner nav link
    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("nav-executive")).toBeVisible();
    await page.getByTestId("nav-executive").click();

    await expect(page.getByTestId("executive-dashboard-page")).toBeVisible();
  });

  test("Strategic health data renders", async ({ page }) => {
    // Use explicit dashboard mock data so assertions are deterministic
    await installMockApi(page, {
      executiveDashboard: buildExecutiveDashboardResponse(),
    });
    await page.goto("/");

    await page.selectOption('[data-testid="persona-select"]', "dana");
    await expect(page.getByTestId("nav-executive")).toBeVisible();
    await page.getByTestId("nav-executive").click();

    await expect(page.getByTestId("executive-dashboard-page")).toBeVisible();

    // Summary stat cards section
    await expect(page.getByTestId("executive-summary-stats")).toBeVisible();

    // Individual stat cards from buildExecutiveDashboardResponse summary
    await expect(page.getByTestId("exec-stat-total")).toBeVisible();
    await expect(page.getByTestId("exec-stat-on-track")).toBeVisible();

    // Rally cry rollup section (2 rally cries in mock data)
    await expect(page.getByTestId("executive-rally-cries")).toBeVisible();

    // Team bucket section (2 buckets in mock data)
    await expect(page.getByTestId("executive-team-buckets")).toBeVisible();
  });

  test("Executive briefing renders", async ({ page }) => {
    await installMockApi(page, {
      executiveDashboard: buildExecutiveDashboardResponse(),
      executiveBriefing: buildExecutiveBriefingResponse(),
    });
    await page.goto("/");

    await page.selectOption('[data-testid="persona-select"]', "dana");
    await expect(page.getByTestId("nav-executive")).toBeVisible();
    await page.getByTestId("nav-executive").click();

    await expect(page.getByTestId("executive-dashboard-page")).toBeVisible();

    // The ExecutiveBriefing section
    await expect(page.getByTestId("executive-briefing")).toBeVisible();

    // Briefing content — rendered when briefingStatus === "ok"
    await expect(page.getByTestId("executive-briefing-content")).toBeVisible();

    // Headline text from buildExecutiveBriefingResponse
    await expect(page.getByTestId("executive-briefing-content")).toContainText(
      "7 of 12 outcomes are on track",
    );

    // At least the first insight item
    await expect(page.getByTestId("executive-briefing-insight-0")).toBeVisible();
  });

  test("Non-admin cannot access executive nav", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");

    // Select Alice Chen (IC only — no ADMIN role)
    await page.selectOption('[data-testid="persona-select"]', "alice");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();

    // nav-executive must not appear for non-admin personas
    await expect(page.getByTestId("nav-executive")).not.toBeVisible();
  });
});
