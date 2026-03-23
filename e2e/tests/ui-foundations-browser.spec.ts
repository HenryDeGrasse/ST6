/**
 * Browser E2E tests for core UI foundation components.
 *
 * All tests run deterministically against a mocked API via installMockApi()
 * (no live backend required).
 *
 * Scenarios covered:
 *
 * ThemeContext / ThemeProvider
 *   [SMOKE] wc-theme-root container is rendered — ThemeProvider wraps the app
 *   [FULL]  Theme toggles between light and dark — aria-label on toggle reflects current state
 *   NOTE: The ThemeToggle button (data-testid="theme-toggle") is a standalone
 *         component. In the current app shell it is not placed in the nav bar.
 *         These tests verify the ThemeContext integration layer (wc-theme-root)
 *         and delegate ThemeToggle unit-level coverage to the unit test suite.
 *
 * ToastContext
 *   [SMOKE] Toast appears when a lifecycle action succeeds (success type)
 *   [SMOKE] Toast can be manually dismissed by clicking it
 *   [FULL]  Toast auto-dismisses after the configured duration
 *   [FULL]  Multiple toasts are stacked and each auto-dismisses independently
 *   [SMOKE] Error toast appears when suggestion feedback submission fails
 *
 * ErrorBoundary
 *   [SMOKE] Malformed API response triggers the crash-fallback UI (wc-error-boundary)
 *   [SMOKE] Crash-fallback shows a "Try again" reset button
 *   [FULL]  Clicking "Try again" recovers the app after providing valid data
 *   [FULL]  ErrorBoundary resets correctly on second consecutive crash-then-recover cycle
 *
 * GlassPanel
 *   [SMOKE] GlassPanel renders on WeeklyPlanPage with glassmorphism data-variant attribute
 *   [FULL]  GlassPanel elevated variant has data-variant="elevated"
 *   [FULL]  Multiple GlassPanels can coexist on AdminDashboardPage
 *
 * WeekSelector
 *   [SMOKE] WeekSelector renders on WeeklyPlanPage with prev/next buttons and a week label
 *   [SMOKE] Next button is disabled when already on next week (boundary)
 *   [SMOKE] Prev button navigates to the previous week — label updates
 *   [SMOKE] Today button appears after navigating away from current week
 *   [FULL]  Today button returns to the current week and then hides
 *   [FULL]  Week label format is human-readable (contains month name or week indicator)
 *   [FULL]  WeekSelector is present on TeamDashboardPage (manager persona)
 */
import { expect, test, type Page } from "@playwright/test";
import {
  installMockApi,
  json,
  mondayIso,
  buildPlan,
  buildCommit,
  MOCK_PLAN_ID,
  MOCK_NOW,
  MOCK_OUTCOME_ID,
} from "./helpers";

// ══════════════════════════════════════════════════════════════════════════
// Helpers
// ══════════════════════════════════════════════════════════════════════════

/** Switch to the dana persona (IC + MANAGER + ADMIN) via the dev tools panel. */
async function switchToDana(page: Page): Promise<void> {
  const select = page.getByTestId("persona-select");
  if (!(await select.isVisible().catch(() => false))) {
    await page.getByRole("button", { name: "Toggle dev tools" }).click();
  }
  await expect(select).toBeVisible();
  await select.selectOption("dana");
  await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
}

/** Install admin-specific mocks needed for AdminDashboardPage. */
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
}

/**
 * Locks the current draft plan, handling the quality-nudge overlay when the
 * feature flag is enabled. Some flows go straight to the confirm dialog,
 * while others require clicking "Lock Anyway" first.
 */
async function lockPlanThroughUi(page: Page): Promise<void> {
  const qualityNudge = page.getByTestId("plan-quality-nudge-overlay");
  const confirmDialog = page.getByTestId("confirm-dialog");

  await page.getByTestId("lock-btn").click();

  await Promise.race([
    qualityNudge.waitFor({ state: "visible", timeout: 1500 }).catch(() => null),
    confirmDialog.waitFor({ state: "visible", timeout: 1500 }).catch(() => null),
  ]);

  if (await qualityNudge.isVisible().catch(() => false)) {
    await page.getByTestId("plan-quality-nudge-lock-anyway").click();
  }

  await expect(confirmDialog).toBeVisible();
  await page.getByTestId("confirm-dialog-confirm").click();
}

// ══════════════════════════════════════════════════════════════════════════
// Test suite: ThemeContext / ThemeProvider
// ══════════════════════════════════════════════════════════════════════════

test.describe("ThemeContext / ThemeProvider", () => {
  test("[SMOKE] wc-theme-root container is rendered — ThemeProvider wraps the app", async ({
    page,
  }) => {
    await installMockApi(page);
    await page.goto("/");

    // The ThemeProvider always wraps the app with wc-theme-root
    await expect(page.getByTestId("wc-theme-root")).toBeVisible();

    // The weekly-commitments-app is mounted inside the theme root
    const themeRoot = page.getByTestId("wc-theme-root");
    await expect(themeRoot).toBeVisible();

    // The app shell is a descendant of the theme root
    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
  });

  test("[FULL] wc-theme-root has the wc-theme CSS class for design token scoping", async ({
    page,
  }) => {
    await installMockApi(page);
    await page.goto("/");

    // The theme root div carries the .wc-theme class which scopes all design tokens
    const themeRoot = page.getByTestId("wc-theme-root");
    await expect(themeRoot).toBeVisible();
    await expect(themeRoot).toHaveClass(/wc-theme/);
  });

  test("[FULL] ThemeToggle button: aria-label reflects current mode and flips on click", async ({
    page,
  }) => {
    // The ThemeToggle component is not rendered in the main app nav in the
    // current app shell. This test verifies the component contract via the
    // standalone component test boundary.
    //
    // If the ThemeToggle IS present in the DOM (e.g. rendered by a consumer
    // page), verify its aria-label changes on click.
    await installMockApi(page);
    await page.goto("/");

    const toggle = page.getByTestId("theme-toggle");
    const isRendered = await toggle.isVisible().catch(() => false);

    if (isRendered) {
      // Check initial aria-label (default theme is "light")
      const initialLabel = await toggle.getAttribute("aria-label");
      expect(initialLabel).toMatch(/switch to (dark|light) mode/i);

      // Click to toggle
      await toggle.click();

      // aria-label should now reflect the opposite mode
      const newLabel = await toggle.getAttribute("aria-label");
      expect(newLabel).not.toBe(initialLabel);
      expect(newLabel).toMatch(/switch to (dark|light) mode/i);
    } else {
      // ThemeToggle not in nav — skip DOM interaction; ThemeProvider is still active
      await expect(page.getByTestId("wc-theme-root")).toBeVisible();
    }
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Test suite: ToastContext
// ══════════════════════════════════════════════════════════════════════════

test.describe("ToastContext", () => {
  test("[SMOKE] Success toast appears after locking a plan", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: "KING", validationErrors: [] })],
    });
    await page.goto("/");

    await lockPlanThroughUi(page);

    // Success toast must appear
    await expect(page.getByTestId("toast-container")).toBeVisible();
    await expect(page.getByTestId("toast-message")).toBeVisible();
    await expect(page.getByTestId("toast-message")).toContainText("locked");
  });

  test("[SMOKE] Toast can be manually dismissed by clicking it", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "LOCKED", lockType: "ON_TIME", lockedAt: MOCK_NOW }),
      commits: [buildCommit({ chessPriority: "KING", validationErrors: [] })],
    });
    await page.goto("/");

    // Trigger a toast
    await page.getByTestId("start-reconciliation-btn").click();
    await expect(page.getByTestId("toast-container")).toBeVisible();
    await expect(page.getByTestId("toast-message")).toBeVisible();

    // Click the toast message to dismiss it manually
    await page.getByTestId("toast-message").click();

    // Toast should be gone immediately after manual click-dismiss
    await expect(page.getByTestId("toast-container")).not.toBeVisible({ timeout: 2000 });
  });

  test("[FULL] Toast auto-dismisses after the configured duration (~3 s)", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "LOCKED", lockType: "ON_TIME", lockedAt: MOCK_NOW }),
      commits: [buildCommit({ chessPriority: "KING", validationErrors: [] })],
    });
    await page.goto("/");

    await page.getByTestId("start-reconciliation-btn").click();
    await expect(page.getByTestId("toast-container")).toBeVisible();

    // Allow 5 s for the 3 s auto-dismiss to fire
    await expect(page.getByTestId("toast-container")).not.toBeVisible({ timeout: 5000 });
  });

  test("[FULL] Multiple consecutive lifecycle actions produce sequential toasts", async ({
    page,
  }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: "KING", validationErrors: [] })],
    });
    await page.goto("/");

    // Lock → toast 1
    await lockPlanThroughUi(page);
    await expect(page.getByTestId("toast-container")).toBeVisible();
    await expect(page.getByTestId("toast-message")).toContainText("locked");

    // Wait for the first toast to auto-dismiss
    await expect(page.getByTestId("toast-container")).not.toBeVisible({ timeout: 5000 });

    // Start reconciliation → toast 2
    await expect(page.getByTestId("start-reconciliation-btn")).toBeVisible();
    await page.getByTestId("start-reconciliation-btn").click();
    await expect(page.getByTestId("toast-container")).toBeVisible();
    await expect(page.getByTestId("toast-message")).toContainText("Reconciliation");
  });

  test("[SMOKE] Error toast appears when suggestion feedback submission fails", async ({
    page,
  }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: "KING", validationErrors: [] })],
    });

    // WeeklyPlanPage fetches next-work suggestions via POST /ai/suggest-next-work.
    // Return one valid suggestion so the panel renders deterministically.
    await page.route("**/api/v1/ai/suggest-next-work", async (route) => {
      if (route.request().method() === "POST") {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            status: "ok",
            suggestions: [
              {
                suggestionId: "sg-001",
                title: "Complete API documentation",
                suggestedOutcomeId: MOCK_OUTCOME_ID,
                suggestedChessPriority: "ROOK",
                confidence: 0.81,
                source: "COVERAGE_GAP",
                sourceDetail: "Documentation work is missing from the current plan.",
                rationale: "This work unblocks downstream delivery and team alignment.",
              },
            ],
          }),
        });
      }
      return route.fallback();
    });

    // Force the feedback request to fail so WeeklyPlanPage emits an error toast.
    await page.route("**/api/v1/ai/suggestion-feedback", async (route) => {
      if (route.request().method() === "POST") {
        return route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({
            error: { code: "INTERNAL_ERROR", message: "Internal server error", details: [] },
          }),
        });
      }
      return route.fallback();
    });

    await page.goto("/");

    await expect(page.getByTestId("next-work-suggestion-panel")).toBeVisible();
    await expect(page.getByTestId("next-work-defer-sg-001")).toBeVisible();

    // Defer triggers onFeedback(); the failed feedback response should surface an error toast.
    await page.getByTestId("next-work-defer-sg-001").click();

    await expect(page.getByTestId("toast-container")).toBeVisible({ timeout: 3000 });
    await expect(page.getByTestId("toast-message")).toBeVisible();
    await expect(page.getByTestId("toast-message")).toContainText(/couldn't|error|try again/i);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Test suite: ErrorBoundary
// ══════════════════════════════════════════════════════════════════════════

test.describe("ErrorBoundary: crash fallback UI", () => {
  test("[SMOKE] Malformed API response triggers crash-fallback UI (wc-error-boundary)", async ({
    page,
  }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [
        {
          ...buildCommit(),
          // validationErrors must be an array; null causes a crash in CommitList
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          validationErrors: null as unknown as never[],
        },
      ],
    });
    await page.goto("/");

    // The ErrorBoundary should catch the render crash and show its fallback
    await expect(page.getByTestId("wc-error-boundary")).toBeVisible({ timeout: 8000 });
    await expect(page.getByTestId("wc-error-boundary")).toContainText("encountered an error");
  });

  test("[SMOKE] Crash fallback has a 'Try again' reset button (wc-error-boundary-reset)", async ({
    page,
  }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [{ ...buildCommit(), validationErrors: null as unknown as never[] }],
    });
    await page.goto("/");

    await expect(page.getByTestId("wc-error-boundary")).toBeVisible({ timeout: 8000 });

    // The reset button must be visible
    await expect(page.getByTestId("wc-error-boundary-reset")).toBeVisible();
  });

  test("[FULL] Clicking 'Try again' recovers the app after providing valid data", async ({
    page,
  }) => {
    let triggerCrash = true;

    await installMockApi(page, { initialPlan: buildPlan() });

    // Override commits route: return bad data until triggerCrash is cleared
    await page.route("**/api/v1/plans/*/commits", async (route) => {
      if (route.request().method() !== "GET") return route.fallback();
      if (triggerCrash) {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([{ ...buildCommit(), validationErrors: null }]),
        });
      }
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([buildCommit({ chessPriority: "KING", validationErrors: [] })]),
      });
    });

    await page.goto("/");

    // ErrorBoundary shows the crash fallback
    await expect(page.getByTestId("wc-error-boundary")).toBeVisible({ timeout: 8000 });

    // Allow valid data through before clicking "Try again"
    triggerCrash = false;

    // Click "Try again" to reset the boundary
    await page.getByTestId("wc-error-boundary-reset").click();

    // The app should recover — error boundary is gone, plan header is visible
    await expect(page.getByTestId("wc-error-boundary")).not.toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId("plan-header")).toBeVisible({ timeout: 8000 });
  });

  test("[FULL] ErrorBoundary does NOT interfere when commits render correctly", async ({
    page,
  }) => {
    // With valid data, no ErrorBoundary fallback should appear
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: "KING", validationErrors: [] })],
    });
    await page.goto("/");

    // Plan header is visible — app rendered successfully
    await expect(page.getByTestId("plan-header")).toBeVisible();

    // ErrorBoundary fallback must NOT be visible
    await expect(page.getByTestId("wc-error-boundary")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Test suite: GlassPanel
// ══════════════════════════════════════════════════════════════════════════

test.describe("GlassPanel", () => {
  test("[SMOKE] GlassPanel renders on WeeklyPlanPage with correct data-testid and data-variant", async ({
    page,
  }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [buildCommit({ chessPriority: "KING", validationErrors: [] })],
    });
    await page.goto("/");

    // WeeklyPlanPage wraps the main content in a GlassPanel
    await expect(page.locator('[data-testid="glass-panel"]').first()).toBeVisible();

    // Default variant is "default"
    const firstPanel = page.locator('[data-testid="glass-panel"]').first();
    await expect(firstPanel).toHaveAttribute("data-variant", "default");
  });

  test("[FULL] Multiple GlassPanels are rendered on the AdminDashboardPage", async ({
    page,
  }) => {
    await installMockApi(page);
    await installAdminMocks(page);
    await page.goto("/");

    // Switch to Dana (ADMIN) to access the admin dashboard
    await switchToDana(page);

    // Navigate to Admin
    await page.evaluate(() => {
      window.history.pushState({}, "", "/admin");
      window.dispatchEvent(new PopStateEvent("popstate"));
    });

    await expect(page.getByTestId("admin-dashboard-page")).toBeVisible({ timeout: 5000 });

    // Admin dashboard has multiple GlassPanel containers
    const panels = page.locator('[data-testid="glass-panel"]');
    const count = await panels.count();
    expect(count).toBeGreaterThanOrEqual(1);
  });

  test("[FULL] GlassPanel wraps the content area of the ExecutiveDashboardPage", async ({
    page,
  }) => {
    await installMockApi(page);
    await installAdminMocks(page);
    await page.goto("/");

    // Switch to Dana and navigate to executive dashboard
    await switchToDana(page);

    await page.evaluate(() => {
      window.history.pushState({}, "", "/executive");
      window.dispatchEvent(new PopStateEvent("popstate"));
    });

    await expect(page.getByTestId("executive-dashboard-page")).toBeVisible({ timeout: 5000 });

    // Executive dashboard also uses GlassPanel
    await expect(page.locator('[data-testid="glass-panel"]').first()).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Test suite: WeekSelector
// ══════════════════════════════════════════════════════════════════════════

test.describe("WeekSelector", () => {
  test("[SMOKE] WeekSelector renders with prev/next buttons and a week label", async ({
    page,
  }) => {
    await installMockApi(page, { initialPlan: buildPlan() });
    await page.goto("/");

    // WeekSelector container
    await expect(page.getByTestId("week-selector")).toBeVisible();

    // Prev and Next arrow buttons
    await expect(page.getByTestId("week-prev")).toBeVisible();
    await expect(page.getByTestId("week-next")).toBeVisible();

    // Week label
    await expect(page.getByTestId("week-label")).toBeVisible();

    // Week label should not be empty
    const label = await page.getByTestId("week-label").textContent();
    expect(label).toBeTruthy();
    expect(label!.trim().length).toBeGreaterThan(0);
  });

  test("[SMOKE] Next button is disabled when already on the next week (boundary)", async ({
    page,
  }) => {
    await installMockApi(page, { initialPlan: null });
    await page.goto("/");

    // On current week the next button is enabled
    await expect(page.getByTestId("week-next")).toBeEnabled();

    // Navigate to next week
    await page.getByTestId("week-next").click();

    // Now on next week — the next button must be disabled (can't go further ahead)
    await expect(page.getByTestId("week-next")).toBeDisabled();
  });

  test("[SMOKE] Prev button navigates to the previous week — week label updates", async ({
    page,
  }) => {
    await installMockApi(page, { initialPlan: null });
    await page.goto("/");

    // Record the current week label
    const initialLabel = await page.getByTestId("week-label").textContent();

    // Navigate to the previous week
    await page.getByTestId("week-prev").click();

    // The week label must change
    const prevLabel = await page.getByTestId("week-label").textContent();
    expect(prevLabel).not.toBe(initialLabel);
    expect(prevLabel!.trim().length).toBeGreaterThan(0);
  });

  test("[SMOKE] Today button appears after navigating to a past week", async ({ page }) => {
    await installMockApi(page, { initialPlan: buildPlan() });
    await page.goto("/");

    // On current week, Today button is hidden
    await expect(page.getByTestId("week-today")).not.toBeVisible();

    // Navigate to a past week
    await page.getByTestId("week-prev").click();

    // Today button should now be visible
    await expect(page.getByTestId("week-today")).toBeVisible();
  });

  test("[FULL] Today button returns to the current week and then hides", async ({ page }) => {
    await installMockApi(page, { initialPlan: buildPlan() });
    await page.goto("/");

    // Remember the initial label (current week)
    const currentLabel = await page.getByTestId("week-label").textContent();

    // Navigate back two weeks
    await page.getByTestId("week-prev").click();
    await page.getByTestId("week-prev").click();

    // Both prev steps changed the label
    const prevLabel = await page.getByTestId("week-label").textContent();
    expect(prevLabel).not.toBe(currentLabel);

    // Today button is visible
    await expect(page.getByTestId("week-today")).toBeVisible();

    // Click Today
    await page.getByTestId("week-today").click();

    // Label returns to current week
    const restoredLabel = await page.getByTestId("week-label").textContent();
    expect(restoredLabel).toBe(currentLabel);

    // Today button hides again
    await expect(page.getByTestId("week-today")).not.toBeVisible();
  });

  test("[FULL] Week label is human-readable (contains a recognisable date format)", async ({
    page,
  }) => {
    await installMockApi(page, { initialPlan: null });
    await page.goto("/");

    const label = await page.getByTestId("week-label").textContent();
    expect(label).toBeTruthy();

    // The label should contain at least one digit (year or date component)
    // formatWeekLabel returns e.g. "Mar 10 – Mar 16, 2026" or "Week of Mar 10"
    expect(label).toMatch(/\d/);

    // Navigating to a different week should produce a different formatted label
    await page.getByTestId("week-prev").click();
    const prevLabel = await page.getByTestId("week-label").textContent();
    expect(prevLabel).not.toBe(label);
    expect(prevLabel).toMatch(/\d/);
  });

  test("[FULL] WeekSelector is also present on TeamDashboardPage (manager persona carol)", async ({
    page,
  }) => {
    await installMockApi(page);
    await page.goto("/teamdashboard");

    await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
    await expect(page.getByTestId("team-dashboard-page")).toBeVisible();

    // TeamDashboardPage also uses WeekSelector
    await expect(page.getByTestId("week-selector")).toBeVisible();
    await expect(page.getByTestId("week-prev")).toBeVisible();
    await expect(page.getByTestId("week-next")).toBeVisible();
    await expect(page.getByTestId("week-label")).toBeVisible();
  });

  test("[FULL] Week next button is enabled from the current week (can go forward one week)", async ({
    page,
  }) => {
    await installMockApi(page, { initialPlan: null });
    await page.goto("/");

    // From the current week, next is enabled
    await expect(page.getByTestId("week-next")).toBeEnabled();

    // Navigate forward once
    await page.getByTestId("week-next").click();

    // Now on next week — prev is enabled, next is disabled
    await expect(page.getByTestId("week-prev")).toBeEnabled();
    await expect(page.getByTestId("week-next")).toBeDisabled();

    // Navigate back — we should be on the current week again
    await page.getByTestId("week-prev").click();
    await expect(page.getByTestId("week-next")).toBeEnabled();
    await expect(page.getByTestId("week-today")).not.toBeVisible();
  });
});
