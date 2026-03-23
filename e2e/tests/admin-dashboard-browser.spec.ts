/**
 * Browser-based E2E tests for AdminDashboardPage (all 7 tabs).
 *
 * All tests run deterministically against mocked APIs — no live backend required.
 * Tests use the "dana" persona (IC + MANAGER + ADMIN) to access the admin dashboard.
 *
 * Coverage:
 *   [SMOKE] Access-denied guard for non-ADMIN users
 *   [SMOKE] Admin dashboard renders for ADMIN user (Dana persona)
 *   [SMOKE] All 7 tab buttons render in the tab bar
 *
 *   Tab 1 – Adoption Funnel:
 *   [SMOKE] Panel renders with metric cards and table
 *           Active users and cadence compliance metric cards
 *           Window buttons 4w/8w/12w/26w render
 *           Clicking 4w window button refetches with 4 weeks
 *           Adoption table rows render
 *
 *   Tab 2 – Cadence Config:
 *   [SMOKE] Read-only cadence fields display (lockDay, lockTime, reconcileDay, reconcileTime)
 *           Digest day and time selectors render
 *           Changing digest day enables Save button
 *           Save calls PATCH /admin/org-policy/digest and shows success message
 *           Reload button re-fetches org policy
 *           Save button disabled when form not dirty
 *
 *   Tab 3 – Chess Rules:
 *   [SMOKE] Chess rule fields render (king required, max king, max queen, block stale RCDO)
 *
 *   Tab 4 – RCDO Health:
 *   [SMOKE] RCDO metrics (total, covered, stale counts) render
 *           RCDO table rows render for covered outcomes
 *           Stale outcome rows render with stale badge
 *           Refresh button triggers re-fetch
 *
 *   Tab 5 – AI Usage:
 *   [SMOKE] Acceptance rate, cache hit rate, tokens-saved metric cards render
 *           Feedback breakdown (accepted, deferred, declined) renders
 *           Window buttons render; clicking 4w refetches
 *
 *   Tab 6 – Feature Flags:
 *   [SMOKE] Flag list renders with toggle checkboxes
 *           Save Flags button disabled when not dirty
 *           Toggling a flag enables Save/Reset buttons
 *           Save Flags writes to localStorage and shows saved message
 *           Reset Flags reverts toggles and clears dirty state
 *
 *   Tab 7 – Outcome Targets:
 *   [SMOKE] Gated message shown when outcomeUrgency flag is off
 *           OutcomeMetadataEditor shown when outcomeUrgency flag is on
 */
import { expect, test, type Page } from "@playwright/test";
import {
  installMockApi,
  json,
  apiError,
  MOCK_ORG_ID,
  MOCK_NOW,
  MOCK_OUTCOME_ID,
  mondayIso,
} from "./helpers";

// ══════════════════════════════════════════════════════════════════════════
// Builders for admin-specific mock responses
// ══════════════════════════════════════════════════════════════════════════

function buildAdoptionMetrics(weeks = 8): Record<string, unknown> {
  const points = Array.from({ length: weeks }, (_, i) => ({
    weekStart: mondayIso(-(weeks - 1 - i)),
    activeUsers: 10 + i,
    plansCreated: 10 + i,
    plansLocked: 8 + i,
    plansReconciled: 6 + i,
    plansReviewed: 4 + i,
  }));
  return {
    weeks,
    windowStart: points[0].weekStart,
    windowEnd: points[points.length - 1].weekStart,
    totalActiveUsers: 18,
    cadenceComplianceRate: 0.82,
    weeklyPoints: points,
  };
}

function buildAiUsageMetrics(weeks = 8): Record<string, unknown> {
  return {
    weeks,
    windowStart: mondayIso(-(weeks - 1)),
    windowEnd: mondayIso(0),
    totalFeedbackCount: 120,
    acceptedCount: 85,
    deferredCount: 20,
    declinedCount: 15,
    acceptanceRate: 0.71,
    cacheHits: 340,
    cacheMisses: 60,
    cacheHitRate: 0.85,
    approximateTokensSpent: 60_000,
    approximateTokensSaved: 340_000,
  };
}

function buildRcdoHealthReport(): Record<string, unknown> {
  return {
    generatedAt: MOCK_NOW,
    windowWeeks: 8,
    totalOutcomes: 3,
    coveredOutcomes: 2,
    topOutcomes: [
      {
        outcomeId: MOCK_OUTCOME_ID,
        outcomeName: "Increase trial-to-paid by 20%",
        objectiveId: "obj-1",
        objectiveName: "Improve Conversion",
        rallyCryId: "rc-1",
        rallyCryName: "Scale Revenue",
        commitCount: 12,
      },
      {
        outcomeId: "outcome-002",
        outcomeName: "Reduce API latency to <100ms",
        objectiveId: "obj-2",
        objectiveName: "Platform Reliability",
        rallyCryId: "rc-2",
        rallyCryName: "Scale Infrastructure",
        commitCount: 5,
      },
    ],
    staleOutcomes: [
      {
        outcomeId: "outcome-stale-001",
        outcomeName: "Expand to EU market",
        objectiveId: "obj-3",
        objectiveName: "Global Expansion",
        rallyCryId: "rc-3",
        rallyCryName: "International Growth",
        commitCount: 0,
      },
    ],
  };
}

// ══════════════════════════════════════════════════════════════════════════
// Admin mock route installer
// ══════════════════════════════════════════════════════════════════════════

interface AdminMockOptions {
  adoptionWeeks?: number;
  aiUsageWeeks?: number;
  denyOrgPolicy?: boolean;
}

/**
 * Installs admin-specific API routes AFTER installMockApi (LIFO precedence).
 * installMockApi already handles GET/PATCH /admin/org-policy — these extend it
 * with the three additional admin endpoints that installMockApi leaves as 404.
 */
async function installAdminMocks(page: Page, opts: AdminMockOptions = {}): Promise<void> {
  // ── GET /api/v1/admin/adoption-metrics ────────────────────────────────
  await page.route(/\/api\/v1\/admin\/adoption-metrics(\?.*)?$/, async (route) => {
    if (route.request().method() === "GET") {
      const url = new URL(route.request().url());
      const weeks = parseInt(url.searchParams.get("weeks") ?? String(opts.adoptionWeeks ?? 8), 10);
      return json(route, 200, buildAdoptionMetrics(weeks));
    }
    return route.fallback();
  });

  // ── GET /api/v1/admin/ai-usage ────────────────────────────────────────
  await page.route(/\/api\/v1\/admin\/ai-usage(\?.*)?$/, async (route) => {
    if (route.request().method() === "GET") {
      const url = new URL(route.request().url());
      const weeks = parseInt(url.searchParams.get("weeks") ?? String(opts.aiUsageWeeks ?? 8), 10);
      return json(route, 200, buildAiUsageMetrics(weeks));
    }
    return route.fallback();
  });

  // ── GET /api/v1/admin/rcdo-health ─────────────────────────────────────
  await page.route(/\/api\/v1\/admin\/rcdo-health$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, buildRcdoHealthReport());
    }
    return route.fallback();
  });

  // ── Outcome metadata (needed for outcome-targets tab) ─────────────────
  await page.route(/\/api\/v1\/outcomes\/metadata$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, []);
    }
    return route.fallback();
  });
}

// ══════════════════════════════════════════════════════════════════════════
// Helpers
// ══════════════════════════════════════════════════════════════════════════

/** Switch to Dana (ADMIN persona) and wait for app remount. */
async function switchToDana(page: Page) {
  await page.selectOption('[data-testid="persona-select"]', "dana");
  await expect(page.getByTestId("weekly-commitments-app")).toBeVisible();
}

/** Navigate to the Admin Dashboard tab. */
async function gotoAdmin(page: Page) {
  await page.getByTestId("nav-admin").click();
  await expect(page.getByTestId("admin-dashboard-page")).toBeVisible();
}

/** Switch to Dana, install admin mocks, navigate to admin dashboard. */
async function setupAdmin(page: Page, opts: AdminMockOptions = {}) {
  await installMockApi(page);
  await installAdminMocks(page, opts);
  await page.goto("/");
  await switchToDana(page);
  await gotoAdmin(page);
}

/** Click a tab in the admin tab bar and wait for its panel test-id to be visible. */
async function clickTab(page: Page, tabId: string) {
  await page.getByTestId(`admin-tab-${tabId}`).click();
}

// ══════════════════════════════════════════════════════════════════════════
// Access Guard
// ══════════════════════════════════════════════════════════════════════════

test.describe("Admin Dashboard — Access Guard", () => {
  test("[SMOKE] non-ADMIN user sees access-denied message", async ({ page }) => {
    await installMockApi(page);
    await installAdminMocks(page);
    await page.goto("/");
    // Default persona is Carol (IC + MANAGER, no ADMIN)
    // Navigate directly to /admin
    await page.goto("/admin");
    await expect(page.getByTestId("admin-dashboard-page")).toBeVisible();
    await expect(page.getByTestId("admin-access-denied")).toBeVisible();
    await expect(page.getByTestId("admin-access-denied")).toContainText("Access Denied");
  });

  test("[SMOKE] ADMIN user (Dana) sees the dashboard", async ({ page }) => {
    await setupAdmin(page);
    await expect(page.getByTestId("admin-tab-bar")).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Tab Bar
// ══════════════════════════════════════════════════════════════════════════

test.describe("Admin Dashboard — Tab Bar", () => {
  test.beforeEach(async ({ page }) => {
    await setupAdmin(page);
  });

  test("[SMOKE] all 7 tab buttons render", async ({ page }) => {
    const tabIds = ["adoption", "cadence", "chess", "rcdo-health", "ai-usage", "feature-flags", "outcome-targets"];
    for (const tabId of tabIds) {
      await expect(page.getByTestId(`admin-tab-${tabId}`)).toBeVisible();
    }
  });

  test("Adoption Funnel tab is active by default", async ({ page }) => {
    await expect(page.getByTestId("admin-tab-adoption")).toHaveAttribute("aria-selected", "true");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Tab 1 — Adoption Funnel
// ══════════════════════════════════════════════════════════════════════════

test.describe("Admin Dashboard — Adoption Funnel Tab", () => {
  test.beforeEach(async ({ page }) => {
    await setupAdmin(page);
    // Adoption tab is default — panel should already be visible
    await expect(page.getByTestId("adoption-funnel-panel")).toBeVisible();
  });

  test("[SMOKE] adoption-funnel-panel renders", async ({ page }) => {
    await expect(page.getByTestId("adoption-funnel-panel")).toBeVisible();
  });

  test("[SMOKE] metric-active-users card renders", async ({ page }) => {
    const card = page.getByTestId("metric-active-users");
    await expect(card).toBeVisible();
    await expect(card).toContainText("18"); // totalActiveUsers from buildAdoptionMetrics
  });

  test("[SMOKE] metric-cadence-compliance card shows rate", async ({ page }) => {
    const card = page.getByTestId("metric-cadence-compliance");
    await expect(card).toBeVisible();
    await expect(card).toContainText("82%"); // cadenceComplianceRate: 0.82
  });

  test("[SMOKE] adoption table renders with rows", async ({ page }) => {
    await expect(page.getByTestId("adoption-table")).toBeVisible();
    // 8w window → 8 rows
    const rows = page.getByTestId("adoption-table").locator("tbody tr");
    await expect(rows).toHaveCount(8);
  });

  test("[SMOKE] window buttons 4w/8w/12w/26w render", async ({ page }) => {
    await expect(page.getByTestId("window-btn-4")).toBeVisible();
    await expect(page.getByTestId("window-btn-8")).toBeVisible();
    await expect(page.getByTestId("window-btn-12")).toBeVisible();
    await expect(page.getByTestId("window-btn-26")).toBeVisible();
  });

  test("clicking 4w window button fetches 4-week window data", async ({ page }) => {
    let requestedWeeks = -1;
    await page.route(/\/api\/v1\/admin\/adoption-metrics(\?.*)?$/, async (route) => {
      if (route.request().method() === "GET") {
        const url = new URL(route.request().url());
        requestedWeeks = parseInt(url.searchParams.get("weeks") ?? "8", 10);
        return json(route, 200, buildAdoptionMetrics(requestedWeeks));
      }
      return route.fallback();
    });
    await page.getByTestId("window-btn-4").click();
    await page.waitForTimeout(300);
    expect(requestedWeeks).toBe(4);
    // 4w window → 4 rows
    const rows = page.getByTestId("adoption-table").locator("tbody tr");
    await expect(rows).toHaveCount(4);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Tab 2 — Cadence Config
// ══════════════════════════════════════════════════════════════════════════

test.describe("Admin Dashboard — Cadence Config Tab", () => {
  test.beforeEach(async ({ page }) => {
    await setupAdmin(page);
    await clickTab(page, "cadence");
    await expect(page.getByTestId("cadence-config-panel")).toBeVisible();
  });

  test("[SMOKE] cadence-config-panel renders", async ({ page }) => {
    await expect(page.getByTestId("cadence-config-panel")).toBeVisible();
  });

  test("[SMOKE] read-only lock day renders", async ({ page }) => {
    // orgPolicy from installMockApi: lockDay: 'MONDAY'
    await expect(page.getByTestId("lock-day")).toContainText("Monday");
  });

  test("[SMOKE] read-only lock time renders", async ({ page }) => {
    await expect(page.getByTestId("lock-time")).toContainText("10:00");
  });

  test("[SMOKE] read-only reconcile day renders", async ({ page }) => {
    // orgPolicy: reconcileDay: 'FRIDAY'
    await expect(page.getByTestId("reconcile-day")).toContainText("Friday");
  });

  test("[SMOKE] read-only reconcile time renders", async ({ page }) => {
    await expect(page.getByTestId("reconcile-time")).toContainText("16:00");
  });

  test("[SMOKE] digest day and time selectors render", async ({ page }) => {
    await expect(page.getByTestId("cadence-digest-day")).toBeVisible();
    await expect(page.getByTestId("cadence-digest-time")).toBeVisible();
  });

  test("Save button is disabled when form is not dirty", async ({ page }) => {
    await expect(page.getByTestId("save-cadence-btn")).toBeDisabled();
  });

  test("changing digest day enables Save button", async ({ page }) => {
    // Current digestDay is FRIDAY — change to THURSDAY to make dirty
    await page.getByTestId("cadence-digest-day").selectOption("THURSDAY");
    await expect(page.getByTestId("save-cadence-btn")).toBeEnabled();
  });

  test("Save calls PATCH org-policy/digest and shows success message", async ({ page }) => {
    let patchCalled = false;
    await page.route(/\/api\/v1\/admin\/org-policy\/digest$/, async (route) => {
      if (route.request().method() === "PATCH") {
        patchCalled = true;
        const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
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
          digestDay: body.digestDay ?? "THURSDAY",
          digestTime: body.digestTime ?? "17:00",
        });
      }
      return route.fallback();
    });

    await page.getByTestId("cadence-digest-day").selectOption("THURSDAY");
    await page.getByTestId("save-cadence-btn").click();
    await page.waitForTimeout(300);
    expect(patchCalled).toBe(true);
    await expect(page.getByTestId("cadence-success")).toBeVisible();
    await expect(page.getByTestId("cadence-success")).toContainText("Digest schedule saved");
  });

  test("Reload button refetches org policy", async ({ page }) => {
    let fetchCount = 0;
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

    const before = fetchCount;
    await page.getByTestId("reload-cadence-btn").click();
    await page.waitForTimeout(300);
    expect(fetchCount).toBeGreaterThan(before);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Tab 3 — Chess Rules
// ══════════════════════════════════════════════════════════════════════════

test.describe("Admin Dashboard — Chess Rules Tab", () => {
  test.beforeEach(async ({ page }) => {
    await setupAdmin(page);
    await clickTab(page, "chess");
    await expect(page.getByTestId("chess-rule-panel")).toBeVisible();
  });

  test("[SMOKE] chess-rule-panel renders", async ({ page }) => {
    await expect(page.getByTestId("chess-rule-panel")).toBeVisible();
  });

  test("[SMOKE] chess-king-required field renders", async ({ page }) => {
    // orgPolicy: chessKingRequired: true
    await expect(page.getByTestId("chess-king-required")).toContainText("Yes");
  });

  test("[SMOKE] chess-max-king field renders", async ({ page }) => {
    // orgPolicy: chessMaxKing: 1
    await expect(page.getByTestId("chess-max-king")).toContainText("1");
  });

  test("[SMOKE] chess-max-queen field renders", async ({ page }) => {
    // orgPolicy: chessMaxQueen: 2
    await expect(page.getByTestId("chess-max-queen")).toContainText("2");
  });

  test("[SMOKE] chess-block-stale-rcdo field renders", async ({ page }) => {
    // orgPolicy: blockLockOnStaleRcdo: false
    await expect(page.getByTestId("chess-block-stale-rcdo")).toContainText("No");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Tab 4 — RCDO Health
// ══════════════════════════════════════════════════════════════════════════

test.describe("Admin Dashboard — RCDO Health Tab", () => {
  test.beforeEach(async ({ page }) => {
    await setupAdmin(page);
    await clickTab(page, "rcdo-health");
    await expect(page.getByTestId("rcdo-health-panel")).toBeVisible();
  });

  test("[SMOKE] rcdo-health-panel renders", async ({ page }) => {
    await expect(page.getByTestId("rcdo-health-panel")).toBeVisible();
  });

  test("[SMOKE] metric-total-outcomes renders", async ({ page }) => {
    // buildRcdoHealthReport: totalOutcomes: 3
    await expect(page.getByTestId("metric-total-outcomes")).toContainText("3");
  });

  test("[SMOKE] metric-covered-outcomes renders", async ({ page }) => {
    // coveredOutcomes: 2
    await expect(page.getByTestId("metric-covered-outcomes")).toContainText("2");
  });

  test("[SMOKE] metric-stale-outcomes renders", async ({ page }) => {
    // staleOutcomes.length: 1
    await expect(page.getByTestId("metric-stale-outcomes")).toContainText("1");
  });

  test("[SMOKE] RCDO table renders covered outcome rows", async ({ page }) => {
    await expect(page.getByTestId("rcdo-table")).toBeVisible();
    await expect(page.getByTestId(`rcdo-row-${MOCK_OUTCOME_ID}`)).toBeVisible();
    await expect(page.getByTestId(`rcdo-row-${MOCK_OUTCOME_ID}`)).toContainText("Increase trial-to-paid");
  });

  test("[SMOKE] stale outcome row renders with Stale label", async ({ page }) => {
    await expect(page.getByTestId("rcdo-stale-row-outcome-stale-001")).toBeVisible();
    await expect(page.getByTestId("rcdo-stale-row-outcome-stale-001")).toContainText("Stale");
  });

  test("Refresh Report button triggers re-fetch", async ({ page }) => {
    let fetchCount = 0;
    await page.route(/\/api\/v1\/admin\/rcdo-health$/, async (route) => {
      if (route.request().method() === "GET") {
        fetchCount++;
        return json(route, 200, buildRcdoHealthReport());
      }
      return route.fallback();
    });
    const before = fetchCount;
    await page.getByTestId("reload-rcdo-btn").click();
    await page.waitForTimeout(300);
    expect(fetchCount).toBeGreaterThan(before);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Tab 5 — AI Usage
// ══════════════════════════════════════════════════════════════════════════

test.describe("Admin Dashboard — AI Usage Tab", () => {
  test.beforeEach(async ({ page }) => {
    await setupAdmin(page);
    await clickTab(page, "ai-usage");
    await expect(page.getByTestId("ai-usage-panel")).toBeVisible();
  });

  test("[SMOKE] ai-usage-panel renders", async ({ page }) => {
    await expect(page.getByTestId("ai-usage-panel")).toBeVisible();
  });

  test("[SMOKE] acceptance rate metric card renders", async ({ page }) => {
    // acceptanceRate: 0.71 → 71%
    await expect(page.getByTestId("metric-acceptance-rate")).toContainText("71%");
  });

  test("[SMOKE] cache hit rate metric card renders", async ({ page }) => {
    // cacheHitRate: 0.85 → 85%
    await expect(page.getByTestId("metric-cache-hit-rate")).toContainText("85%");
  });

  test("[SMOKE] tokens saved metric card renders", async ({ page }) => {
    // approximateTokensSaved: 340000
    await expect(page.getByTestId("metric-tokens-saved")).toContainText("340");
  });

  test("[SMOKE] feedback breakdown renders accepted/deferred/declined counts", async ({ page }) => {
    await expect(page.getByTestId("ai-feedback-breakdown")).toBeVisible();
    await expect(page.getByTestId("ai-accepted-count")).toContainText("85");
    await expect(page.getByTestId("ai-deferred-count")).toContainText("20");
    await expect(page.getByTestId("ai-declined-count")).toContainText("15");
  });

  test("[SMOKE] AI Usage window buttons render", async ({ page }) => {
    await expect(page.getByTestId("ai-window-btn-4")).toBeVisible();
    await expect(page.getByTestId("ai-window-btn-8")).toBeVisible();
    await expect(page.getByTestId("ai-window-btn-12")).toBeVisible();
    await expect(page.getByTestId("ai-window-btn-26")).toBeVisible();
  });

  test("clicking 4w AI window button fetches 4-week data", async ({ page }) => {
    let requestedWeeks = -1;
    await page.route(/\/api\/v1\/admin\/ai-usage(\?.*)?$/, async (route) => {
      if (route.request().method() === "GET") {
        const url = new URL(route.request().url());
        requestedWeeks = parseInt(url.searchParams.get("weeks") ?? "8", 10);
        return json(route, 200, buildAiUsageMetrics(requestedWeeks));
      }
      return route.fallback();
    });
    await page.getByTestId("ai-window-btn-4").click();
    await page.waitForTimeout(300);
    expect(requestedWeeks).toBe(4);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Tab 6 — Feature Flags
// ══════════════════════════════════════════════════════════════════════════

test.describe("Admin Dashboard — Feature Flags Tab", () => {
  test.beforeEach(async ({ page }) => {
    await setupAdmin(page);
    await clickTab(page, "feature-flags");
    await expect(page.getByTestId("feature-flag-panel")).toBeVisible();
  });

  test("[SMOKE] feature-flag-panel renders", async ({ page }) => {
    await expect(page.getByTestId("feature-flag-panel")).toBeVisible();
  });

  test("[SMOKE] flag list renders", async ({ page }) => {
    await expect(page.getByTestId("flag-list")).toBeVisible();
    // suggestRcdo is the first flag
    await expect(page.getByTestId("flag-row-suggestRcdo")).toBeVisible();
  });

  test("[SMOKE] each flag has a toggle checkbox", async ({ page }) => {
    await expect(page.getByTestId("flag-toggle-suggestRcdo")).toBeVisible();
    await expect(page.getByTestId("flag-toggle-icTrends")).toBeVisible();
    await expect(page.getByTestId("flag-toggle-planQualityNudge")).toBeVisible();
    await expect(page.getByTestId("flag-toggle-outcomeUrgency")).toBeVisible();
  });

  test("Save Flags and Reset buttons are disabled when no changes", async ({ page }) => {
    await expect(page.getByTestId("save-flags-btn")).toBeDisabled();
    await expect(page.getByTestId("reset-flags-btn")).toBeDisabled();
  });

  test("toggling a flag enables Save Flags and Reset buttons", async ({ page }) => {
    await page.getByTestId("flag-toggle-suggestRcdo").click();
    await expect(page.getByTestId("save-flags-btn")).toBeEnabled();
    await expect(page.getByTestId("reset-flags-btn")).toBeEnabled();
  });

  test("Save Flags writes to localStorage and shows saved message", async ({ page }) => {
    await page.getByTestId("flag-toggle-suggestRcdo").click();
    await page.getByTestId("save-flags-btn").click();

    // Check localStorage was updated
    const storedFlags = await page.evaluate(() =>
      localStorage.getItem("wc-feature-flags"),
    );
    expect(storedFlags).not.toBeNull();

    // Success message should appear
    await expect(page.getByTestId("flags-saved-msg")).toBeVisible();
    await expect(page.getByTestId("flags-saved-msg")).toContainText("Flags saved");

    // After saving, buttons should become disabled again (not dirty)
    await expect(page.getByTestId("save-flags-btn")).toBeDisabled();
  });

  test("Reset Flags reverts changes and disables Save/Reset", async ({ page }) => {
    // Toggle a flag to make it dirty
    const toggle = page.getByTestId("flag-toggle-suggestRcdo");
    const initialChecked = await toggle.isChecked();
    await toggle.click();
    expect(await toggle.isChecked()).toBe(!initialChecked);

    // Reset
    await page.getByTestId("reset-flags-btn").click();

    // Toggle should be back to initial state
    expect(await toggle.isChecked()).toBe(initialChecked);
    await expect(page.getByTestId("save-flags-btn")).toBeDisabled();
    await expect(page.getByTestId("reset-flags-btn")).toBeDisabled();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Tab 7 — Outcome Targets
// ══════════════════════════════════════════════════════════════════════════

test.describe("Admin Dashboard — Outcome Targets Tab", () => {
  test("[SMOKE] shows gated message when outcomeUrgency flag is off", async ({ page }) => {
    // Ensure outcomeUrgency is off
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.outcomeUrgency = false;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });
    await installMockApi(page);
    await installAdminMocks(page);
    await page.goto("/");
    await switchToDana(page);
    await gotoAdmin(page);
    await clickTab(page, "outcome-targets");
    await expect(page.getByTestId("outcome-targets-panel")).toBeVisible();
    // When flag is off: gated message shown
    await expect(page.getByTestId("outcome-targets-panel")).toContainText("Outcome Urgency");
    await expect(page.getByTestId("outcome-targets-panel")).toContainText("Feature Flags");
  });

  test("[SMOKE] shows OutcomeMetadataEditor when outcomeUrgency flag is on", async ({ page }) => {
    // Ensure outcomeUrgency is on
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.outcomeUrgency = true;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });
    await installMockApi(page);
    await installAdminMocks(page);
    // Add outcome-metadata route
    await page.route(/\/api\/v1\/outcomes\/metadata$/, async (route) => {
      if (route.request().method() === "GET") {
        return json(route, 200, []);
      }
      return route.fallback();
    });
    await page.goto("/");
    await switchToDana(page);
    await gotoAdmin(page);
    await clickTab(page, "outcome-targets");
    await expect(page.getByTestId("outcome-targets-panel")).toBeVisible();
    // OutcomeMetadataEditor should render (not the gated message)
    await expect(page.getByTestId("outcome-targets-panel")).not.toContainText("Feature Flags tab");
  });
});
