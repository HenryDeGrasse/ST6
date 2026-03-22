/**
 * Playwright E2E tests for the persona switcher in the PA host stub.
 *
 * Verifies that switching personas causes the micro-frontend to remount with
 * the new user's context, and that role-gated navigation items appear or are
 * hidden correctly per the selected persona's roles.
 *
 * Personas (from pa-host-stub/src/HostShell.tsx):
 *   carol  — IC + MANAGER (default)
 *   alice  — IC only
 *   bob    — IC only
 *   dana   — IC + MANAGER + ADMIN
 *
 * Test categories:
 *   [SMOKE] = runs on every PR (Gate 7)
 *   [FULL]  = deeper acceptance coverage, suitable for nightly runs
 */
import { expect, test } from "@playwright/test";
import { installMockApi } from "./helpers";

test.describe("Persona Switching", () => {
  test("[SMOKE] Persona switcher remounts the micro-frontend with the new user", async ({
    page,
  }) => {
    await installMockApi(page);
    await page.goto("/");

    // App starts as Carol (IC + MANAGER) — team-dashboard nav should be present
    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("nav-team-dashboard")).toBeVisible();

    // Switch to Alice (IC only) — team-dashboard should disappear after remount
    await page.selectOption('[data-testid="persona-select"]', "alice");

    // Wait for the remounted app shell before asserting
    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("nav-team-dashboard")).not.toBeVisible();
  });

  test("Manager-only nav items hidden for IC persona", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");

    // Select Alice Chen (IC only — no MANAGER or ADMIN roles)
    await page.selectOption('[data-testid="persona-select"]', "alice");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();

    // nav-team-dashboard is only shown for MANAGER role
    await expect(page.getByTestId("nav-team-dashboard")).not.toBeVisible();

    // nav-executive is only shown for ADMIN role
    await expect(page.getByTestId("nav-executive")).not.toBeVisible();
  });

  test("Admin nav items visible for admin persona", async ({ page }) => {
    await installMockApi(page);
    await page.goto("/");

    // Select Dana Torres (IC + MANAGER + ADMIN)
    await page.selectOption('[data-testid="persona-select"]', "dana");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();

    // Dana has ADMIN role — both nav-executive and nav-admin should render
    await expect(page.getByTestId("nav-executive")).toBeVisible();
    await expect(page.getByTestId("nav-admin")).toBeVisible();
  });

  test("My Insights nav item is available for all roles", async ({ page }) => {
    await installMockApi(page);

    const personas = ["carol", "alice", "bob", "dana"] as const;

    for (const persona of personas) {
      await page.goto("/");
      await page.selectOption('[data-testid="persona-select"]', persona);

      // nav-my-insights is rendered for every role (no role gate in AppShell)
      await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
      await expect(page.getByTestId("nav-my-insights")).toBeVisible();
    }
  });
});
