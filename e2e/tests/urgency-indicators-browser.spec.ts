/**
 * Browser-based E2E tests for UrgencyIndicator components on the Team Dashboard:
 *   - StrategicSlackBanner  — renders with slack data when strategicSlack flag is ON
 *   - OutcomeMetadataEditor — opened via "Manage Outcome Targets" toggle
 *   - UrgencyBadge          — renders with appropriate label/band for each urgency level
 *   - OutcomeProgressCard   — (indirectly tested via OutcomeMetadataEditor rendering)
 *
 * All tests use the Carol persona (IC + MANAGER) — the default; no persona switch needed.
 * The outcomeUrgency and strategicSlack feature flags default to ON.
 *
 * Coverage:
 *
 * StrategicSlackBanner:
 *   [SMOKE] renders when strategicSlack flag is ON and data is available
 *   [SMOKE] shows correct band label (HIGH/MODERATE/LOW/NO SLACK)
 *   [SMOKE] shows strategic focus floor percentage
 *   [SMOKE] shows attention count when outcomes need attention
 *           banner NOT shown when strategicSlack flag is OFF
 *           banner NOT shown when API returns no slack data
 *
 * OutcomeMetadataEditor:
 *   [SMOKE] "Manage Outcome Targets" toggle is visible when outcomeUrgency flag is ON
 *   [SMOKE] clicking toggle opens the OutcomeMetadataEditor
 *   [SMOKE] clicking toggle again collapses it
 *   [SMOKE] outcome selector dropdown shows outcome from RCDO tree
 *           selecting an outcome reveals target-date and progress-type fields
 *           ACTIVITY hint shown when ACTIVITY progress type selected
 *           METRIC fields shown when METRIC progress type selected
 *           MILESTONE add-button shown when MILESTONE progress type selected
 *           milestone row can be added and removed
 *           Save button calls PUT /outcomes/{id}/metadata
 *           success message shown after save
 *           toggle NOT shown when outcomeUrgency flag is OFF
 *
 * UrgencyBadge:
 *   [SMOKE] ON_TRACK badge renders with correct label
 *   [SMOKE] AT_RISK badge renders with correct label
 *   [SMOKE] CRITICAL badge renders with correct label
 *   [SMOKE] NEEDS_ATTENTION badge renders with correct label
 *   [SMOKE] NO_TARGET badge renders with correct label
 *           badge data-urgency-band attribute matches the band
 *           UrgencyBadge in OutcomeMetadataEditor shows when metadata has urgencyBand
 */
import { expect, test, type Page } from "@playwright/test";
import {
  installMockApi,
  json,
  apiError,
  MOCK_ORG_ID,
  MOCK_OUTCOME_ID,
  MOCK_NOW,
} from "./helpers";

// ══════════════════════════════════════════════════════════════════════════
// Constants
// ══════════════════════════════════════════════════════════════════════════

const OUTCOME_NAME = "Increase trial-to-paid by 20%";

// ══════════════════════════════════════════════════════════════════════════
// Mock builders
// ══════════════════════════════════════════════════════════════════════════

function buildStrategicSlack(
  slackBand: string,
  strategicFocusFloor = 0.7,
  atRiskCount = 1,
  criticalCount = 0,
): Record<string, unknown> {
  return { slack: { slackBand, strategicFocusFloor, atRiskCount, criticalCount } };
}

function buildOutcomeMetadata(
  outcomeId: string,
  urgencyBand: string,
  targetDate: string | null = "2026-06-30",
): Record<string, unknown> {
  return {
    outcomeId,
    orgId: MOCK_ORG_ID,
    targetDate,
    progressType: "ACTIVITY",
    metricName: null,
    targetValue: null,
    currentValue: null,
    unit: null,
    milestones: null,
    urgencyBand,
    computedProgressPct: 45.0,
    expectedProgressPct: 60.0,
    daysRemaining: 99,
    updatedAt: MOCK_NOW,
  };
}

// ══════════════════════════════════════════════════════════════════════════
// Urgency-specific mock installer (LIFO on top of installMockApi)
// ══════════════════════════════════════════════════════════════════════════

interface UrgencyMockOptions {
  slackBand?: string;
  slackAtRisk?: number;
  slackCritical?: number;
  slackFocusFloor?: number;
  noSlackData?: boolean;
  metadataUrgencyBand?: string;
  noMetadata?: boolean;
}

async function installUrgencyMocks(page: Page, opts: UrgencyMockOptions = {}): Promise<void> {
  const slackBand = opts.slackBand ?? "MODERATE_SLACK";
  const slackAtRisk = opts.slackAtRisk ?? 1;
  const slackCritical = opts.slackCritical ?? 0;
  const slackFocusFloor = opts.slackFocusFloor ?? 0.7;

  // GET /api/v1/team/strategic-slack
  await page.route(/\/api\/v1\/team\/strategic-slack(\?.*)?$/, async (route) => {
    if (route.request().method() === "GET") {
      if (opts.noSlackData) {
        return json(route, 404, apiError("Not found", "NOT_FOUND"));
      }
      return json(route, 200, buildStrategicSlack(slackBand, slackFocusFloor, slackAtRisk, slackCritical));
    }
    return route.fallback();
  });

  // GET /api/v1/outcomes/metadata
  await page.route(/\/api\/v1\/outcomes\/metadata$/, async (route) => {
    if (route.request().method() === "GET") {
      if (opts.noMetadata) {
        return json(route, 200, []);
      }
      return json(route, 200, [
        buildOutcomeMetadata(MOCK_OUTCOME_ID, opts.metadataUrgencyBand ?? "ON_TRACK"),
      ]);
    }
    return route.fallback();
  });

  // GET /api/v1/outcomes/urgency-summary
  await page.route(/\/api\/v1\/outcomes\/urgency-summary(\?.*)?$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, {
        outcomes: [
          {
            outcomeId: MOCK_OUTCOME_ID,
            outcomeName: OUTCOME_NAME,
            urgencyBand: opts.metadataUrgencyBand ?? "ON_TRACK",
            targetDate: "2026-06-30",
            daysRemaining: 99,
            computedProgressPct: 45,
            expectedProgressPct: 60,
          },
        ],
      });
    }
    return route.fallback();
  });

  // PUT /api/v1/outcomes/{outcomeId}/metadata — upsert
  await page.route(/\/api\/v1\/outcomes\/[^/]+\/metadata$/, async (route) => {
    const method = route.request().method();
    if (method === "PUT" || method === "POST") {
      const url = new URL(route.request().url());
      const outcomeId = url.pathname.split("/")[4] ?? MOCK_OUTCOME_ID;
      const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
      return json(route, 200, buildOutcomeMetadata(outcomeId, "ON_TRACK", typeof body.targetDate === "string" ? body.targetDate : null));
    }
    if (method === "GET") {
      return json(route, 200, buildOutcomeMetadata(MOCK_OUTCOME_ID, opts.metadataUrgencyBand ?? "ON_TRACK"));
    }
    return route.fallback();
  });

  // PATCH /api/v1/outcomes/{outcomeId}/progress
  await page.route(/\/api\/v1\/outcomes\/[^/]+\/progress$/, async (route) => {
    if (route.request().method() === "PATCH") {
      const url = new URL(route.request().url());
      const outcomeId = url.pathname.split("/")[4] ?? MOCK_OUTCOME_ID;
      return json(route, 200, buildOutcomeMetadata(outcomeId, "ON_TRACK"));
    }
    return route.fallback();
  });

  // GET /api/v1/outcomes/forecasts (used by targetDateForecasting flag)
  await page.route(/\/api\/v1\/outcomes\/forecasts(\?.*)?$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, { forecasts: [] });
    }
    return route.fallback();
  });
}

// ══════════════════════════════════════════════════════════════════════════
// Setup helper
// ══════════════════════════════════════════════════════════════════════════

async function gotoTeamDashboard(page: Page): Promise<void> {
  await page.getByTestId("nav-team-dashboard").click();
  await expect(page.getByTestId("team-dashboard-page")).toBeVisible();
}

async function setup(page: Page, opts: UrgencyMockOptions = {}): Promise<void> {
  await installMockApi(page);
  await installUrgencyMocks(page, opts);
  await page.goto("/");
  await gotoTeamDashboard(page);
}

// ══════════════════════════════════════════════════════════════════════════
// 1. StrategicSlackBanner
// ══════════════════════════════════════════════════════════════════════════

test.describe("StrategicSlackBanner", () => {
  test("[SMOKE] renders when strategicSlack flag is ON and data is available", async ({ page }) => {
    await setup(page, { slackBand: "MODERATE_SLACK" });
    await expect(page.getByTestId("strategic-slack-banner")).toBeVisible();
  });

  test("[SMOKE] shows HIGH band label for HIGH_SLACK", async ({ page }) => {
    await setup(page, { slackBand: "HIGH_SLACK" });
    await expect(page.getByTestId("strategic-slack-band-label")).toContainText("HIGH");
  });

  test("[SMOKE] shows MODERATE band label for MODERATE_SLACK", async ({ page }) => {
    await setup(page, { slackBand: "MODERATE_SLACK" });
    await expect(page.getByTestId("strategic-slack-band-label")).toContainText("MODERATE");
  });

  test("[SMOKE] shows LOW band label for LOW_SLACK", async ({ page }) => {
    await setup(page, { slackBand: "LOW_SLACK" });
    await expect(page.getByTestId("strategic-slack-band-label")).toContainText("LOW");
  });

  test("[SMOKE] shows NO SLACK band label for NO_SLACK", async ({ page }) => {
    await setup(page, { slackBand: "NO_SLACK" });
    await expect(page.getByTestId("strategic-slack-band-label")).toContainText("NO SLACK");
  });

  test("[SMOKE] shows strategic focus floor as percentage", async ({ page }) => {
    await setup(page, { slackBand: "MODERATE_SLACK", slackFocusFloor: 0.75 });
    await expect(page.getByTestId("strategic-slack-floor-hint")).toContainText("75%");
  });

  test("[SMOKE] shows attention text when outcomes need attention", async ({ page }) => {
    await setup(page, { slackBand: "LOW_SLACK", slackAtRisk: 2, slackCritical: 1 });
    // atRiskCount + criticalCount = 3
    await expect(page.getByTestId("strategic-slack-attention-text")).toContainText("3 outcomes");
  });

  test("shows singular 'outcome' when exactly one needs attention", async ({ page }) => {
    await setup(page, { slackBand: "AT_RISK", slackAtRisk: 1, slackCritical: 0 });
    await expect(page.getByTestId("strategic-slack-attention-text")).toContainText("1 outcome needs");
  });

  test("banner NOT shown when strategicSlack flag is OFF", async ({ page }) => {
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.strategicSlack = false;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });
    await setup(page);
    await expect(page.getByTestId("strategic-slack-banner")).not.toBeVisible();
  });

  test("banner NOT shown when API returns no slack data", async ({ page }) => {
    await setup(page, { noSlackData: true });
    await expect(page.getByTestId("strategic-slack-banner")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 2. OutcomeMetadataEditor
// ══════════════════════════════════════════════════════════════════════════

test.describe("OutcomeMetadataEditor", () => {
  test("[SMOKE] 'Manage Outcome Targets' toggle is visible when outcomeUrgency is ON", async ({
    page,
  }) => {
    await setup(page);
    await expect(page.getByTestId("outcome-targets-toggle")).toBeVisible();
  });

  test("[SMOKE] clicking toggle opens the OutcomeMetadataEditor", async ({ page }) => {
    await setup(page);
    await page.getByTestId("outcome-targets-toggle").click();
    await expect(page.getByTestId("outcome-metadata-editor")).toBeVisible();
  });

  test("[SMOKE] clicking toggle again collapses the editor", async ({ page }) => {
    await setup(page);
    await page.getByTestId("outcome-targets-toggle").click();
    await expect(page.getByTestId("outcome-metadata-editor")).toBeVisible();
    await page.getByTestId("outcome-targets-toggle").click();
    await expect(page.getByTestId("outcome-metadata-editor")).not.toBeVisible();
  });

  test("[SMOKE] outcome selector dropdown shows outcomes from RCDO tree", async ({ page }) => {
    await setup(page);
    await page.getByTestId("outcome-targets-toggle").click();
    const select = page.getByTestId("ome-outcome-select");
    await expect(select).toBeVisible();
    // The RCDO tree from installMockApi has one outcome
    await expect(select.getByRole("option", { name: OUTCOME_NAME })).toBeAttached();
  });

  test("selecting an outcome reveals target-date and progress-type fields", async ({ page }) => {
    await setup(page);
    await page.getByTestId("outcome-targets-toggle").click();
    await page.getByTestId("ome-outcome-select").selectOption(MOCK_OUTCOME_ID);
    await expect(page.getByTestId("ome-target-date")).toBeVisible();
    await expect(page.getByTestId(`ome-progress-type-activity`)).toBeVisible();
    await expect(page.getByTestId(`ome-progress-type-metric`)).toBeVisible();
    await expect(page.getByTestId(`ome-progress-type-milestone`)).toBeVisible();
  });

  test("ACTIVITY hint shown when ACTIVITY progress type is selected", async ({ page }) => {
    await setup(page);
    await page.getByTestId("outcome-targets-toggle").click();
    await page.getByTestId("ome-outcome-select").selectOption(MOCK_OUTCOME_ID);
    // ACTIVITY is the default; hint should be visible
    await expect(page.getByTestId("ome-activity-hint")).toBeVisible();
  });

  test("METRIC fields shown when METRIC progress type is selected", async ({ page }) => {
    await setup(page);
    await page.getByTestId("outcome-targets-toggle").click();
    await page.getByTestId("ome-outcome-select").selectOption(MOCK_OUTCOME_ID);
    await page.getByTestId("ome-progress-type-metric").click();
    await expect(page.getByTestId("ome-metric-fields")).toBeVisible();
    await expect(page.getByTestId("ome-metric-name")).toBeVisible();
    await expect(page.getByTestId("ome-target-value")).toBeVisible();
    await expect(page.getByTestId("ome-current-value")).toBeVisible();
  });

  test("MILESTONE section shown when MILESTONE progress type is selected", async ({ page }) => {
    await setup(page);
    await page.getByTestId("outcome-targets-toggle").click();
    await page.getByTestId("ome-outcome-select").selectOption(MOCK_OUTCOME_ID);
    await page.getByTestId("ome-progress-type-milestone").click();
    await expect(page.getByTestId("ome-milestone-fields")).toBeVisible();
    await expect(page.getByTestId("ome-add-milestone")).toBeVisible();
  });

  test("milestone row can be added and then removed", async ({ page }) => {
    await setup(page);
    await page.getByTestId("outcome-targets-toggle").click();
    await page.getByTestId("ome-outcome-select").selectOption(MOCK_OUTCOME_ID);
    await page.getByTestId("ome-progress-type-milestone").click();

    // Add a milestone
    await page.getByTestId("ome-add-milestone").click();
    await expect(page.getByTestId("ome-milestone-row-0")).toBeVisible();
    await page.getByTestId("ome-milestone-name-0").fill("Ship MVP");

    // Remove it
    await page.getByTestId("ome-remove-milestone-0").click();
    await expect(page.getByTestId("ome-milestone-row-0")).not.toBeVisible();
  });

  test("Save button calls PUT /outcomes/{id}/metadata and shows success message", async ({
    page,
  }) => {
    let saveCalled = false;
    let savePayload: Record<string, unknown> | null = null;

    await installMockApi(page);
    await installUrgencyMocks(page);

    await page.route(/\/api\/v1\/outcomes\/[^/]+\/metadata$/, async (route) => {
      if (route.request().method() === "PUT") {
        saveCalled = true;
        savePayload = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        const url = new URL(route.request().url());
        const outcomeId = url.pathname.split("/")[4] ?? MOCK_OUTCOME_ID;
        return json(route, 200, buildOutcomeMetadata(outcomeId, "ON_TRACK"));
      }
      return route.fallback();
    });

    await page.goto("/");
    await gotoTeamDashboard(page);
    await page.getByTestId("outcome-targets-toggle").click();
    await page.getByTestId("ome-outcome-select").selectOption(MOCK_OUTCOME_ID);
    await page.getByTestId("ome-target-date").fill("2026-12-31");
    await page.getByTestId("ome-save-btn").click();
    await page.waitForTimeout(400);

    expect(saveCalled).toBe(true);
    expect(savePayload?.progressType).toBe("ACTIVITY");
    await expect(page.getByTestId("ome-success")).toBeVisible();
    await expect(page.getByTestId("ome-success")).toContainText("Saved");
  });

  test("UrgencyBadge renders inside editor when metadata has urgencyBand", async ({ page }) => {
    await setup(page, { metadataUrgencyBand: "AT_RISK" });
    await page.getByTestId("outcome-targets-toggle").click();
    await page.getByTestId("ome-outcome-select").selectOption(MOCK_OUTCOME_ID);
    // Metadata has urgencyBand: AT_RISK → urgency row and badge should appear.
    // Scope to the editor row because the Team Dashboard rollup also renders badges.
    const urgencyRow = page.getByTestId("ome-urgency-row");
    const editorBadge = urgencyRow.getByTestId("urgency-badge");
    await expect(urgencyRow).toBeVisible();
    await expect(editorBadge).toBeVisible();
    await expect(editorBadge).toContainText("At Risk");
  });

  test("toggle NOT shown when outcomeUrgency flag is OFF", async ({ page }) => {
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.outcomeUrgency = false;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });
    await setup(page);
    await expect(page.getByTestId("outcome-targets-toggle")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 3. UrgencyBadge
// ══════════════════════════════════════════════════════════════════════════

test.describe("UrgencyBadge — via OutcomeMetadataEditor", () => {
  /**
   * Helper to open the editor for an outcome with a specific urgency band
   * and return the badge rendered inside the editor. The Team Dashboard rollup
   * also renders badges, so tests must scope to the metadata editor row.
   */
  async function openEditorWithBand(page: Page, urgencyBand: string) {
    await setup(page, { metadataUrgencyBand: urgencyBand });
    await page.getByTestId("outcome-targets-toggle").click();
    await page.getByTestId("ome-outcome-select").selectOption(MOCK_OUTCOME_ID);
    const editorBadge = page.getByTestId("ome-urgency-row").getByTestId("urgency-badge");
    await expect(editorBadge).toBeVisible();
    return editorBadge;
  }

  test("[SMOKE] ON_TRACK badge renders with 'On Track' label", async ({ page }) => {
    const editorBadge = await openEditorWithBand(page, "ON_TRACK");
    await expect(editorBadge).toContainText("On Track");
    await expect(editorBadge).toHaveAttribute("data-urgency-band", "ON_TRACK");
  });

  test("[SMOKE] AT_RISK badge renders with 'At Risk' label", async ({ page }) => {
    const editorBadge = await openEditorWithBand(page, "AT_RISK");
    await expect(editorBadge).toContainText("At Risk");
    await expect(editorBadge).toHaveAttribute("data-urgency-band", "AT_RISK");
  });

  test("[SMOKE] CRITICAL badge renders with 'Critical' label", async ({ page }) => {
    const editorBadge = await openEditorWithBand(page, "CRITICAL");
    await expect(editorBadge).toContainText("Critical");
    await expect(editorBadge).toHaveAttribute("data-urgency-band", "CRITICAL");
  });

  test("[SMOKE] NEEDS_ATTENTION badge renders with 'Attention' label", async ({ page }) => {
    const editorBadge = await openEditorWithBand(page, "NEEDS_ATTENTION");
    await expect(editorBadge).toContainText("Attention");
    await expect(editorBadge).toHaveAttribute("data-urgency-band", "NEEDS_ATTENTION");
  });

  test("[SMOKE] NO_TARGET badge renders with 'No Target' label", async ({ page }) => {
    const editorBadge = await openEditorWithBand(page, "NO_TARGET");
    await expect(editorBadge).toContainText("No Target");
    await expect(editorBadge).toHaveAttribute("data-urgency-band", "NO_TARGET");
  });
});
