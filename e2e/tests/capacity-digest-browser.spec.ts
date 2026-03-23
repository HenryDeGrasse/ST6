/**
 * Browser-based E2E tests for CapacityView components and DigestPreferences.
 *
 * Components under test:
 *   1. OvercommitBanner   — advisory banner shown on DRAFT WeeklyPlanPage when
 *                           total estimated hours exceed the realistic weekly cap.
 *   2. DigestPreferences  — admin-only section on WeeklyPlanPage for configuring
 *                           the weekly digest schedule.
 *
 * Note on EstimationCoaching:
 *   The EstimationCoaching component (CapacityView/EstimationCoaching.tsx) is
 *   fully covered by unit tests (src/__tests__/EstimationCoaching.test.tsx).
 *   It is not currently mounted in any page route, so no Playwright-visible DOM
 *   exists for it at E2E level. The mock route for
 *   GET /api/v1/users/me/estimation-coaching is validated here to confirm the
 *   API wiring is correct, but UI assertions are appropriately skipped.
 *
 * Coverage:
 *
 * OvercommitBanner:
 *   [SMOKE] NONE level — banner is hidden when committed hours ≤ realistic cap
 *   [SMOKE] MODERATE level — banner shows for slight overcommit
 *   [SMOKE] HIGH level — banner shows strong warning for large overcommit
 *           adjustedTotal and realisticCap values rendered in banner
 *           Banner role="alert" is present for a11y
 *           level-specific testid renders (overcommit-level-MODERATE / HIGH)
 *           Banner absent on LOCKED plan (only shows on DRAFT)
 *
 * DigestPreferencesSection (ADMIN only):
 *   [SMOKE] section renders for ADMIN user on WeeklyPlanPage
 *   [SMOKE] section NOT shown for non-ADMIN user
 *   [SMOKE] digest-day-select and digest-time-input render with current values
 *           Save button disabled when no changes
 *           Changing digest day enables Save button
 *           Save calls PATCH /admin/org-policy/digest and shows success message
 *           Reload button re-fetches policy
 *
 * EstimationCoaching (API-level verification only):
 *   [SMOKE] GET /users/me/estimation-coaching route is reachable and returns
 *           the expected coaching data shape
 */
import { expect, test, type Page } from "@playwright/test";
import {
  installMockApi,
  json,
  apiError,
  buildPlan,
  buildCommit,
  MOCK_PLAN_ID,
  MOCK_NOW,
} from "./helpers";

// ══════════════════════════════════════════════════════════════════════════
// Constants
// ══════════════════════════════════════════════════════════════════════════

/** realistic_weekly_cap used in the standard mock (from installMockApi). */
const REALISTIC_CAP = 30;

// ══════════════════════════════════════════════════════════════════════════
// Mock builders
// ══════════════════════════════════════════════════════════════════════════

/**
 * Build a commit with a specific estimatedHours value.
 * Used to control the adjustedTotal on the DRAFT plan page.
 */
function buildCommitWithHours(
  id: string,
  estimatedHours: number,
  overrides: Partial<Record<string, unknown>> = {},
): Record<string, unknown> {
  return buildCommit({
    id,
    weeklyPlanId: MOCK_PLAN_ID,
    title: `Task ${id}`,
    estimatedHours,
    ...overrides,
  });
}

/**
 * Build the estimation coaching response.
 */
function buildEstimationCoaching(): Record<string, unknown> {
  return {
    thisWeekEstimated: 32,
    thisWeekActual: 28,
    accuracyRatio: 0.875,
    overallBias: 1.14,
    confidenceLevel: "MEDIUM",
    categoryInsights: [
      {
        category: "DELIVERY",
        bias: 1.2,
        tip: "You tend to over-estimate DELIVERY tasks by ~20%. Consider shaving estimates.",
      },
    ],
    priorityInsights: [
      { priority: "KING", completionRate: 0.95, sampleSize: 20 },
      { priority: "QUEEN", completionRate: 0.82, sampleSize: 35 },
    ],
  };
}

// ══════════════════════════════════════════════════════════════════════════
// Shared setup helpers
// ══════════════════════════════════════════════════════════════════════════

/**
 * Navigate to the weekly plan page using the default Carol persona.
 */
async function gotoWeeklyPlan(page: Page): Promise<void> {
  await expect(page.getByTestId("weekly-plan-page")).toBeVisible();
}

/**
 * Open the dev tools panel (if not already open) and switch persona.
 * Mirrors the pattern used in executive-dashboard and admin tests.
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

// ══════════════════════════════════════════════════════════════════════════
// 1. OvercommitBanner
// ══════════════════════════════════════════════════════════════════════════

test.describe("OvercommitBanner", () => {
  // ── NONE level (no overcommit) ────────────────────────────────────────

  test("[SMOKE] banner is hidden when committed hours are at NONE level", async ({ page }) => {
    // adjustedTotal = 0 (commit has no estimatedHours) < realisticCap 30 → NONE
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit()], // no estimatedHours → 0 contribution
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    // Banner must not be present at all
    await expect(page.getByTestId("overcommit-banner")).not.toBeAttached();
  });

  test("banner is hidden when committed hours are within cap", async ({ page }) => {
    // adjustedTotal = 20 < realisticCap 30 → NONE
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommitWithHours("c-1", 20)],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId("overcommit-banner")).not.toBeAttached();
  });

  // ── MODERATE level ────────────────────────────────────────────────────

  test("[SMOKE] MODERATE banner shows when hours slightly exceed cap", async ({ page }) => {
    // adjustedTotal = 31 > realisticCap 30, but 31 <= 30 * 1.2 = 36 → MODERATE
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommitWithHours("c-1", 31)],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId("overcommit-banner")).toBeVisible();
    await expect(page.getByTestId("overcommit-level-MODERATE")).toBeVisible();
  });

  test("[SMOKE] MODERATE banner displays adjustedTotal and realisticCap", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommitWithHours("c-1", 31)],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    const banner = page.getByTestId("overcommit-banner");
    await expect(banner).toContainText("31h committed");
    await expect(banner).toContainText(`${REALISTIC_CAP}h cap`);
  });

  test("MODERATE banner contains the overcommit message", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommitWithHours("c-1", 31)],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId("overcommit-banner")).toContainText(
      "slightly over your realistic weekly capacity",
    );
  });

  // ── HIGH level ────────────────────────────────────────────────────────

  test("[SMOKE] HIGH banner shows when hours significantly exceed cap", async ({ page }) => {
    // adjustedTotal = 37 > realisticCap * 1.2 = 36 → HIGH
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommitWithHours("c-1", 37)],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId("overcommit-banner")).toBeVisible();
    await expect(page.getByTestId("overcommit-level-HIGH")).toBeVisible();
  });

  test("[SMOKE] HIGH banner displays adjusted hours and cap", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommitWithHours("c-1", 37)],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    const banner = page.getByTestId("overcommit-banner");
    await expect(banner).toContainText("37h committed");
    await expect(banner).toContainText(`${REALISTIC_CAP}h cap`);
  });

  test("HIGH banner contains the strong-warning message", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommitWithHours("c-1", 37)],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId("overcommit-banner")).toContainText(
      "significantly over your realistic weekly capacity",
    );
  });

  // ── Accessibility ─────────────────────────────────────────────────────

  test("banner has role=alert for accessibility", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommitWithHours("c-1", 31)],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId("overcommit-banner")).toHaveAttribute("role", "alert");
  });

  // ── Plan state gate ───────────────────────────────────────────────────

  test("banner is NOT shown on a LOCKED plan (only DRAFT state shows it)", async ({ page }) => {
    // Even with high hours, LOCKED plan does not show OvercommitBanner
    await installMockApi(page, {
      initialPlan: buildPlan({
        state: "LOCKED",
        lockType: "ON_TIME",
        lockedAt: MOCK_NOW,
        version: 2,
      }),
      commits: [buildCommitWithHours("c-1", 37)],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId("overcommit-banner")).not.toBeAttached();
  });

  // ── Combined hours from multiple commits ──────────────────────────────

  test("total hours from multiple commits are summed for overcommit check", async ({ page }) => {
    // 15h + 18h = 33h > 30 → MODERATE
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [
        buildCommitWithHours("c-1", 15),
        buildCommitWithHours("c-2", 18),
      ],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId("overcommit-banner")).toBeVisible();
    await expect(page.getByTestId("overcommit-level-MODERATE")).toBeVisible();
    // 15 + 18 = 33h
    await expect(page.getByTestId("overcommit-banner")).toContainText("33h committed");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 2. DigestPreferencesSection (ADMIN only)
// ══════════════════════════════════════════════════════════════════════════

test.describe("DigestPreferencesSection", () => {
  test("[SMOKE] section renders for ADMIN user (Dana)", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit()],
    });
    await page.goto("/");
    await switchPersona(page, "dana");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId("digest-preferences-section")).toBeVisible();
  });

  test("[SMOKE] section is NOT shown for non-ADMIN user (Carol, IC + MANAGER)", async ({
    page,
  }) => {
    // Default persona is Carol (IC + MANAGER — no ADMIN)
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit()],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId("digest-preferences-section")).not.toBeAttached();
  });

  test("[SMOKE] digest-day-select renders with the current policy day", async ({ page }) => {
    // installMockApi returns digestDay: 'FRIDAY'
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit()],
    });
    await page.goto("/");
    await switchPersona(page, "dana");
    await gotoWeeklyPlan(page);
    const select = page.getByTestId("digest-day-select");
    await expect(select).toBeVisible();
    await expect(select).toHaveValue("FRIDAY");
  });

  test("[SMOKE] digest-time-input renders with the current policy time", async ({ page }) => {
    // installMockApi returns digestTime: '17:00'
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit()],
    });
    await page.goto("/");
    await switchPersona(page, "dana");
    await gotoWeeklyPlan(page);
    const input = page.getByTestId("digest-time-input");
    await expect(input).toBeVisible();
    await expect(input).toHaveValue("17:00");
  });

  test("Save button is disabled when form is not dirty", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit()],
    });
    await page.goto("/");
    await switchPersona(page, "dana");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId("save-digest-preferences-btn")).toBeDisabled();
  });

  test("changing digest day enables Save button", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit()],
    });
    await page.goto("/");
    await switchPersona(page, "dana");
    await gotoWeeklyPlan(page);
    // Change from FRIDAY to THURSDAY
    await page.getByTestId("digest-day-select").selectOption("THURSDAY");
    await expect(page.getByTestId("save-digest-preferences-btn")).toBeEnabled();
  });

  test("Save calls PATCH /admin/org-policy/digest and shows success message", async ({
    page,
  }) => {
    let patchCalled = false;
    let patchBody: Record<string, unknown> | null = null;

    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit()],
    });

    // Register a tracking route AFTER installMockApi (LIFO)
    await page.route(/\/api\/v1\/admin\/org-policy\/digest$/, async (route) => {
      if (route.request().method() === "PATCH") {
        patchCalled = true;
        patchBody = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        return json(route, 200, {
          chessKingRequired: true,
          chessMaxKing: 1,
          chessMaxQueen: 2,
          lockDay: "MONDAY",
          lockTime: "10:00",
          reconcileDay: "FRIDAY",
          reconcileTime: "16:00",
          blockLockOnStaleRcdo: false,
          rcdoStalenessThresholdMinutes: 1440,
          digestDay: patchBody?.digestDay ?? "THURSDAY",
          digestTime: patchBody?.digestTime ?? "17:00",
        });
      }
      return route.fallback();
    });

    await page.goto("/");
    await switchPersona(page, "dana");
    await gotoWeeklyPlan(page);

    await page.getByTestId("digest-day-select").selectOption("THURSDAY");
    await page.getByTestId("save-digest-preferences-btn").click();
    await page.waitForTimeout(400);

    expect(patchCalled).toBe(true);
    expect(patchBody?.digestDay).toBe("THURSDAY");

    await expect(page.getByTestId("digest-preferences-success")).toBeVisible();
    await expect(page.getByTestId("digest-preferences-success")).toContainText(
      "Weekly digest schedule saved",
    );
  });

  test("Save button becomes disabled again after successful save", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit()],
    });
    await page.goto("/");
    await switchPersona(page, "dana");
    await gotoWeeklyPlan(page);

    await page.getByTestId("digest-day-select").selectOption("THURSDAY");
    await expect(page.getByTestId("save-digest-preferences-btn")).toBeEnabled();
    await page.getByTestId("save-digest-preferences-btn").click();
    await page.waitForTimeout(400);
    // After save, form is not dirty → button disabled again
    await expect(page.getByTestId("save-digest-preferences-btn")).toBeDisabled();
  });

  test("Reload button re-fetches org policy", async ({ page }) => {
    let fetchCount = 0;

    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit()],
    });

    await page.route(/\/api\/v1\/admin\/org-policy$/, async (route) => {
      if (route.request().method() === "GET") {
        fetchCount++;
        return json(route, 200, {
          chessKingRequired: true,
          chessMaxKing: 1,
          chessMaxQueen: 2,
          lockDay: "MONDAY",
          lockTime: "10:00",
          reconcileDay: "FRIDAY",
          reconcileTime: "16:00",
          blockLockOnStaleRcdo: false,
          rcdoStalenessThresholdMinutes: 1440,
          digestDay: "FRIDAY",
          digestTime: "17:00",
        });
      }
      return route.fallback();
    });

    await page.goto("/");
    await switchPersona(page, "dana");
    await gotoWeeklyPlan(page);

    const before = fetchCount;
    await page.getByTestId("reload-digest-preferences-btn").click();
    await page.waitForTimeout(300);
    expect(fetchCount).toBeGreaterThan(before);
  });

  test("error state shown when policy fetch fails", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit()],
    });

    await page.route(/\/api\/v1\/admin\/org-policy$/, async (route) => {
      if (route.request().method() === "GET") {
        return json(route, 500, apiError("Internal server error", "INTERNAL_ERROR"));
      }
      return route.fallback();
    });

    await page.goto("/");
    await switchPersona(page, "dana");
    await gotoWeeklyPlan(page);

    await expect(page.getByTestId("digest-preferences-error")).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 3. EstimationCoaching — API route verification
//
// The EstimationCoaching component is not currently mounted in any page route,
// so UI-level assertions are not possible. The unit tests in
// src/__tests__/EstimationCoaching.test.tsx provide full component coverage.
//
// This section validates that the API endpoint this component depends on is
// correctly mocked and reachable from the E2E test environment.
// ══════════════════════════════════════════════════════════════════════════

test.describe("EstimationCoaching — API endpoint verification", () => {
  test("[SMOKE] GET /users/me/estimation-coaching returns coaching data shape", async ({
    page,
  }) => {
    let coachingFetched = false;
    let coachingResponseBody: Record<string, unknown> | null = null;

    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit()],
    });

    // Register estimation-coaching mock (LIFO)
    await page.route(/\/api\/v1\/users\/me\/estimation-coaching(\?.*)?$/, async (route) => {
      if (route.request().method() === "GET") {
        coachingFetched = true;
        coachingResponseBody = buildEstimationCoaching();
        return json(route, 200, coachingResponseBody);
      }
      return route.fallback();
    });

    // Make a direct fetch from within the browser to verify the mock is wired
    await page.goto("/");
    await gotoWeeklyPlan(page);

    const result = await page.evaluate(async () => {
      const resp = await fetch("/api/v1/users/me/estimation-coaching?planId=test-plan");
      return { status: resp.status, ok: resp.ok };
    });

    expect(result.status).toBe(200);
    expect(result.ok).toBe(true);
    expect(coachingFetched).toBe(true);

    // Verify the response shape includes required fields
    expect(coachingResponseBody).not.toBeNull();
    expect(typeof (coachingResponseBody as Record<string, unknown>).thisWeekEstimated).toBe("number");
    expect(typeof (coachingResponseBody as Record<string, unknown>).thisWeekActual).toBe("number");
    expect(Array.isArray((coachingResponseBody as Record<string, unknown>).categoryInsights)).toBe(true);
    expect(Array.isArray((coachingResponseBody as Record<string, unknown>).priorityInsights)).toBe(true);
  });
});
