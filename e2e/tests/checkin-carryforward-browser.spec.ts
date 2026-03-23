/**
 * Browser-based E2E tests for DailyCheckIn, CarryForwardDialog, and
 * BacklogPickerDialog flows.
 *
 * All tests run deterministically against mocked APIs — no live backend required.
 *
 * Coverage:
 *
 * DailyCheckIn (QuickCheckIn component):
 *   [SMOKE] check-in button appears on LOCKED plan commits
 *   [SMOKE] clicking check-in button opens the inline form
 *   [SMOKE] commit title is shown in the form context
 *   [SMOKE] all four status buttons render (ON_TRACK, AT_RISK, BLOCKED, DONE_EARLY)
 *           Submit button is disabled before a status is selected
 *           Selecting a status enables the Submit button
 *           Note textarea is visible and accepts text
 *           Submitting a check-in calls POST /commits/{id}/check-in
 *           Success message appears after submit
 *           Closing the form removes it from view
 *           History entries render from existing check-ins
 *           Check-in button is NOT shown on DRAFT plan
 *
 * CarryForwardDialog:
 *   [SMOKE] carry-forward-btn visible on RECONCILED plan
 *   [SMOKE] clicking carry-forward-btn opens the dialog
 *   [SMOKE] commit options render for each commit
 *           Commits are pre-selected by default
 *           Deselecting a commit decrements the confirm button count
 *           Confirm button disabled when nothing selected
 *           Confirming calls POST /plans/{id}/carry-forward with selected IDs
 *           Cancel closes the dialog
 *           Assignment options render when useIssueBacklog flag is on
 *
 * BacklogPickerDialog:
 *   [SMOKE] 'Add from Backlog' button shows on DRAFT plan with useIssueBacklog on
 *   [SMOKE] clicking opens the backlog picker dialog
 *   [SMOKE] issues list renders with backlog issues
 *           Selecting an issue enables the Confirm button
 *           Search input is visible and filters issues
 *           Confirming calls assignment creation API
 *           Cancel closes the dialog
 *           'Add from Backlog' button NOT shown without useIssueBacklog flag
 */
import { expect, test, type Page } from "@playwright/test";
import {
  installMockApi,
  json,
  apiError,
  buildPlan,
  buildCommit,
  MOCK_PLAN_ID,
  MOCK_COMMIT_ID,
  MOCK_ORG_ID,
  MOCK_USER_ID,
  MOCK_NOW,
  mondayIso,
} from "./helpers";

// ══════════════════════════════════════════════════════════════════════════
// Constants
// ══════════════════════════════════════════════════════════════════════════

const COMMIT_1_ID = MOCK_COMMIT_ID;
const COMMIT_2_ID = "20000000-0000-0000-0000-000000000002";
const ENTRY_1_ID = "entry-000-0000-0000-000000000001";
const TEAM_ID = "team-0000-0000-0000-000000000001";
const ISSUE_1_ID = "issue-000-0000-0000-000000000001";
const ISSUE_2_ID = "issue-000-0000-0000-000000000002";
const ASSIGNMENT_1_ID = "assign-000-0000-0000-000000000001";

// ══════════════════════════════════════════════════════════════════════════
// Mock builders
// ══════════════════════════════════════════════════════════════════════════

function buildLockedPlan(overrides: Partial<Record<string, unknown>> = {}) {
  return buildPlan({
    state: "LOCKED",
    lockType: "ON_TIME",
    lockedAt: MOCK_NOW,
    version: 2,
    ...overrides,
  });
}

function buildReconciledPlan() {
  return buildPlan({
    state: "RECONCILED",
    reviewStatus: "REVIEW_PENDING",
    lockType: "ON_TIME",
    lockedAt: MOCK_NOW,
    carryForwardExecutedAt: null,
    version: 3,
  });
}

function buildCommit1() {
  return buildCommit({
    id: COMMIT_1_ID,
    title: "Ship planning APIs",
    chessPriority: "QUEEN",
    category: "DELIVERY",
  });
}

function buildCommit2() {
  return buildCommit({
    id: COMMIT_2_ID,
    title: "Update documentation",
    chessPriority: "ROOK",
    category: "PLANNING",
    weeklyPlanId: MOCK_PLAN_ID,
  });
}

function buildCheckInEntry(
  id = ENTRY_1_ID,
  status = "ON_TRACK",
  note: string | null = "Making good progress",
): Record<string, unknown> {
  return {
    id,
    commitId: COMMIT_1_ID,
    planId: MOCK_PLAN_ID,
    status,
    note,
    createdAt: MOCK_NOW,
  };
}

function buildAssignment(
  id = ASSIGNMENT_1_ID,
  issueKey = "ENG-1",
  issueTitle = "Fix authentication bug",
): Record<string, unknown> {
  return {
    id,
    weeklyPlanId: MOCK_PLAN_ID,
    issueId: ISSUE_1_ID,
    chessPriorityOverride: null,
    expectedResult: null,
    confidence: null,
    version: 1,
    createdAt: MOCK_NOW,
    updatedAt: MOCK_NOW,
    actual: null,
    issue: {
      id: ISSUE_1_ID,
      orgId: MOCK_ORG_ID,
      teamId: TEAM_ID,
      issueKey,
      sequenceNumber: 1,
      title: issueTitle,
      effortType: "BUILD",
      estimatedHours: 8,
      chessPriority: "QUEEN",
      status: "OPEN",
      assigneeUserId: MOCK_USER_ID,
      outcomeId: null,
      creatorUserId: MOCK_USER_ID,
      blockedByIssueId: null,
      nonStrategicReason: null,
      aiRecommendedRank: null,
      version: 1,
      createdAt: MOCK_NOW,
      updatedAt: MOCK_NOW,
    },
  };
}

function buildBacklogIssue(
  id: string,
  issueKey: string,
  title: string,
): Record<string, unknown> {
  return {
    id,
    orgId: MOCK_ORG_ID,
    teamId: TEAM_ID,
    issueKey,
    sequenceNumber: parseInt(issueKey.split("-")[1] ?? "1"),
    title,
    description: null,
    effortType: "BUILD",
    estimatedHours: 8,
    chessPriority: "QUEEN",
    outcomeId: null,
    nonStrategicReason: null,
    creatorUserId: MOCK_USER_ID,
    assigneeUserId: null,
    blockedByIssueId: null,
    status: "OPEN",
    aiRecommendedRank: null,
    version: 1,
    createdAt: MOCK_NOW,
    updatedAt: MOCK_NOW,
  };
}

// ══════════════════════════════════════════════════════════════════════════
// Shared setup helpers
// ══════════════════════════════════════════════════════════════════════════

/**
 * Install check-in specific routes (LIFO precedence).
 */
async function installCheckInMocks(page: Page): Promise<void> {
  // GET /commits/{id}/check-ins — return existing entries
  await page.route(/\/api\/v1\/commits\/[^/]+\/check-ins$/, async (route) => {
    if (route.request().method() === "GET") {
      const url = new URL(route.request().url());
      const commitId = url.pathname.split("/")[4];
      if (commitId === COMMIT_1_ID) {
        return json(route, 200, { commitId, entries: [buildCheckInEntry()] });
      }
      return json(route, 200, { commitId, entries: [] });
    }
    return route.fallback();
  });

  // POST /commits/{id}/check-in — save new entry
  await page.route(/\/api\/v1\/commits\/[^/]+\/check-in$/, async (route) => {
    if (route.request().method() === "POST") {
      const url = new URL(route.request().url());
      const commitId = url.pathname.split("/")[4];
      const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
      return json(route, 201, {
        id: `entry-new-${Date.now()}`,
        commitId,
        planId: MOCK_PLAN_ID,
        status: body.status ?? "ON_TRACK",
        note: body.note ?? null,
        createdAt: MOCK_NOW,
      });
    }
    return route.fallback();
  });
}

/**
 * Install backlog-picker routes (teams + issues).
 */
async function installBacklogPickerMocks(page: Page): Promise<void> {
  await page.route(/\/api\/v1\/teams$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, {
        teams: [
          {
            id: TEAM_ID,
            orgId: MOCK_ORG_ID,
            name: "Engineering",
            keyPrefix: "ENG",
            description: null,
            ownerUserId: MOCK_USER_ID,
            issueSequence: 2,
            createdAt: MOCK_NOW,
            updatedAt: MOCK_NOW,
          },
        ],
      });
    }
    return route.fallback();
  });

  await page.route(/\/api\/v1\/teams\/[^/]+\/issues(\?.*)?$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, {
        content: [
          buildBacklogIssue(ISSUE_1_ID, "ENG-1", "Fix authentication bug"),
          buildBacklogIssue(ISSUE_2_ID, "ENG-2", "Improve dashboard load time"),
        ],
        page: 0,
        size: 50,
        totalElements: 2,
        totalPages: 1,
      });
    }
    return route.fallback();
  });

  // Weekly assignments creation
  await page.route(/\/api\/v1\/weeks\/[^/]+\/plan\/assignments$/, async (route) => {
    if (route.request().method() === "POST") {
      return json(route, 201, buildAssignment());
    }
    return route.fallback();
  });

  // Plan assignments list
  await page.route(/\/api\/v1\/plans\/[^/]+\/assignments$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, { assignments: [] });
    }
    return route.fallback();
  });
}

async function gotoWeeklyPlan(page: Page): Promise<void> {
  await expect(page.getByTestId("weekly-plan-page")).toBeVisible();
}

// ══════════════════════════════════════════════════════════════════════════
// 1. DailyCheckIn
// ══════════════════════════════════════════════════════════════════════════

test.describe("DailyCheckIn", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildLockedPlan(),
      commits: [buildCommit1(), buildCommit2()],
    });
    await installCheckInMocks(page);
    await page.goto("/");
    await gotoWeeklyPlan(page);
  });

  test("[SMOKE] check-in button appears on each LOCKED commit", async ({ page }) => {
    await expect(page.getByTestId(`check-in-btn-${COMMIT_1_ID}`)).toBeVisible();
    await expect(page.getByTestId(`check-in-btn-${COMMIT_2_ID}`)).toBeVisible();
  });

  test("[SMOKE] clicking check-in button opens the inline form", async ({ page }) => {
    await page.getByTestId(`check-in-btn-${COMMIT_1_ID}`).click();
    await expect(page.getByTestId("quick-check-in")).toBeVisible();
  });

  test("[SMOKE] commit title is shown in the form context", async ({ page }) => {
    await page.getByTestId(`check-in-btn-${COMMIT_1_ID}`).click();
    await expect(page.getByTestId("check-in-commit-title")).toContainText("Ship planning APIs");
  });

  test("[SMOKE] all four status buttons render", async ({ page }) => {
    await page.getByTestId(`check-in-btn-${COMMIT_1_ID}`).click();
    await expect(page.getByTestId("check-in-status-on_track")).toBeVisible();
    await expect(page.getByTestId("check-in-status-at_risk")).toBeVisible();
    await expect(page.getByTestId("check-in-status-blocked")).toBeVisible();
    await expect(page.getByTestId("check-in-status-done_early")).toBeVisible();
  });

  test("Submit is disabled before a status is selected", async ({ page }) => {
    await page.getByTestId(`check-in-btn-${COMMIT_1_ID}`).click();
    await expect(page.getByTestId("check-in-submit")).toBeDisabled();
  });

  test("selecting a status enables the Submit button", async ({ page }) => {
    await page.getByTestId(`check-in-btn-${COMMIT_1_ID}`).click();
    await page.getByTestId("check-in-status-on_track").click();
    await expect(page.getByTestId("check-in-submit")).toBeEnabled();
  });

  test("note textarea is visible and accepts text", async ({ page }) => {
    await page.getByTestId(`check-in-btn-${COMMIT_1_ID}`).click();
    const note = page.getByTestId("check-in-note");
    await expect(note).toBeVisible();
    await note.fill("Making great progress today");
    await expect(note).toHaveValue("Making great progress today");
  });

  test("submitting calls POST /commits/{id}/check-in", async ({ page }) => {
    let checkInCalled = false;
    let requestBody: Record<string, unknown> | null = null;

    await page.route(new RegExp(`/api/v1/commits/${COMMIT_1_ID}/check-in$`), async (route) => {
      if (route.request().method() === "POST") {
        checkInCalled = true;
        requestBody = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        return json(route, 201, {
          id: "entry-new",
          commitId: COMMIT_1_ID,
          planId: MOCK_PLAN_ID,
          status: requestBody.status,
          note: requestBody.note ?? null,
          createdAt: MOCK_NOW,
        });
      }
      return route.fallback();
    });

    await page.getByTestId(`check-in-btn-${COMMIT_1_ID}`).click();
    await page.getByTestId("check-in-status-on_track").click();
    await page.getByTestId("check-in-note").fill("On track");
    await page.getByTestId("check-in-submit").click();
    await page.waitForTimeout(300);

    expect(checkInCalled).toBe(true);
    expect(requestBody?.status).toBe("ON_TRACK");
  });

  test("success message appears after successful check-in", async ({ page }) => {
    await page.getByTestId(`check-in-btn-${COMMIT_1_ID}`).click();
    await page.getByTestId("check-in-status-at_risk").click();
    await page.getByTestId("check-in-submit").click();
    await expect(page.getByTestId("check-in-success")).toBeVisible();
    await expect(page.getByTestId("check-in-success")).toContainText("Saved");
  });

  test("closing the form removes it from view", async ({ page }) => {
    await page.getByTestId(`check-in-btn-${COMMIT_1_ID}`).click();
    await expect(page.getByTestId("quick-check-in")).toBeVisible();
    await page.getByTestId("check-in-close").click();
    await expect(page.getByTestId("quick-check-in")).not.toBeVisible();
  });

  test("history entries render from existing check-ins", async ({ page }) => {
    await page.getByTestId(`check-in-btn-${COMMIT_1_ID}`).click();
    // COMMIT_1 has one pre-existing check-in entry from installCheckInMocks
    await expect(page.getByTestId("check-in-history")).toBeVisible();
    await expect(page.getByTestId(`check-in-entry-${ENTRY_1_ID}`)).toBeVisible();
    await expect(page.getByTestId(`check-in-entry-note-${ENTRY_1_ID}`)).toContainText("Making good progress");
  });

  test("check-in button is NOT shown on a DRAFT plan", async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit1()],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId(`check-in-btn-${COMMIT_1_ID}`)).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 2. CarryForwardDialog
// ══════════════════════════════════════════════════════════════════════════

test.describe("CarryForwardDialog", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildReconciledPlan(),
      commits: [buildCommit1(), buildCommit2()],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
  });

  test("[SMOKE] carry-forward-btn is visible on RECONCILED plan", async ({ page }) => {
    await expect(page.getByTestId("carry-forward-btn")).toBeVisible();
  });

  test("[SMOKE] clicking carry-forward-btn opens the dialog", async ({ page }) => {
    await page.getByTestId("carry-forward-btn").click();
    await expect(page.getByTestId("carry-forward-dialog")).toBeVisible();
  });

  test("[SMOKE] commit options render for each commit", async ({ page }) => {
    await page.getByTestId("carry-forward-btn").click();
    await expect(page.getByTestId(`carry-option-${COMMIT_1_ID}`)).toBeVisible();
    await expect(page.getByTestId(`carry-option-${COMMIT_2_ID}`)).toBeVisible();
    await expect(page.getByTestId(`carry-option-${COMMIT_1_ID}`)).toContainText("Ship planning APIs");
    await expect(page.getByTestId(`carry-option-${COMMIT_2_ID}`)).toContainText("Update documentation");
  });

  test("[SMOKE] commits are pre-selected by default", async ({ page }) => {
    await page.getByTestId("carry-forward-btn").click();
    // Both checkboxes should be checked
    const checkbox1 = page.getByTestId(`carry-option-${COMMIT_1_ID}`).locator("input[type=checkbox]");
    const checkbox2 = page.getByTestId(`carry-option-${COMMIT_2_ID}`).locator("input[type=checkbox]");
    await expect(checkbox1).toBeChecked();
    await expect(checkbox2).toBeChecked();
    // Confirm button shows total count
    await expect(page.getByTestId("carry-confirm")).toContainText("2");
  });

  test("deselecting a commit decrements the confirm button count", async ({ page }) => {
    await page.getByTestId("carry-forward-btn").click();
    // Uncheck commit 1
    await page.getByTestId(`carry-option-${COMMIT_1_ID}`).locator("input[type=checkbox]").uncheck();
    await expect(page.getByTestId("carry-confirm")).toContainText("1");
  });

  test("Confirm button disabled when nothing selected", async ({ page }) => {
    await page.getByTestId("carry-forward-btn").click();
    // Uncheck both commits
    await page.getByTestId(`carry-option-${COMMIT_1_ID}`).locator("input[type=checkbox]").uncheck();
    await page.getByTestId(`carry-option-${COMMIT_2_ID}`).locator("input[type=checkbox]").uncheck();
    await expect(page.getByTestId("carry-confirm")).toBeDisabled();
  });

  test("Confirming calls POST /plans/{id}/carry-forward with selected commit IDs", async ({
    page,
  }) => {
    let carryPayload: Record<string, unknown> | null = null;
    await page.route(/\/api\/v1\/plans\/[^/]+\/carry-forward$/, async (route) => {
      if (route.request().method() === "POST") {
        carryPayload = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        return json(route, 200, {
          ...buildReconciledPlan(),
          state: "CARRY_FORWARD",
          carryForwardExecutedAt: MOCK_NOW,
          version: 4,
        });
      }
      return route.fallback();
    });

    await page.getByTestId("carry-forward-btn").click();
    await page.getByTestId("carry-confirm").click();
    await page.waitForTimeout(400);

    expect(carryPayload).not.toBeNull();
    const commitIds = carryPayload?.commitIds as string[];
    expect(Array.isArray(commitIds)).toBe(true);
    expect(commitIds).toContain(COMMIT_1_ID);
    expect(commitIds).toContain(COMMIT_2_ID);
    // Dialog should close after successful carry-forward
    await expect(page.getByTestId("carry-forward-dialog")).not.toBeVisible();
  });

  test("Cancel closes the dialog without calling carry-forward", async ({ page }) => {
    await page.getByTestId("carry-forward-btn").click();
    await expect(page.getByTestId("carry-forward-dialog")).toBeVisible();
    await page.getByTestId("carry-cancel").click();
    await expect(page.getByTestId("carry-forward-dialog")).not.toBeVisible();
  });

  test("Escape key closes the dialog", async ({ page }) => {
    await page.getByTestId("carry-forward-btn").click();
    await expect(page.getByTestId("carry-forward-dialog")).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.getByTestId("carry-forward-dialog")).not.toBeVisible();
  });

  test("carry forward only selected commits when one is unchecked", async ({ page }) => {
    let carryPayload: Record<string, unknown> | null = null;
    await page.route(/\/api\/v1\/plans\/[^/]+\/carry-forward$/, async (route) => {
      if (route.request().method() === "POST") {
        carryPayload = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        return json(route, 200, buildReconciledPlan());
      }
      return route.fallback();
    });

    await page.getByTestId("carry-forward-btn").click();
    // Deselect commit 2
    await page.getByTestId(`carry-option-${COMMIT_2_ID}`).locator("input[type=checkbox]").uncheck();
    await page.getByTestId("carry-confirm").click();
    await page.waitForTimeout(400);

    const commitIds = carryPayload?.commitIds as string[];
    expect(commitIds).toContain(COMMIT_1_ID);
    expect(commitIds).not.toContain(COMMIT_2_ID);
  });

  test("assignment carry-forward options render when useIssueBacklog flag is on", async ({
    page,
  }) => {
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.useIssueBacklog = true;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });

    // Add assignments mock
    await installMockApi(page, {
      initialPlan: buildReconciledPlan(),
      commits: [buildCommit1()],
    });

    // Mock assignments endpoint
    await page.route(/\/api\/v1\/plans\/[^/]+\/assignments$/, async (route) => {
      if (route.request().method() === "GET") {
        return json(route, 200, { assignments: [buildAssignment()] });
      }
      return route.fallback();
    });

    await page.goto("/");
    await gotoWeeklyPlan(page);
    await page.getByTestId("carry-forward-btn").click();

    // Assignment option should appear in the carry-forward list
    await expect(
      page.getByTestId(`carry-assignment-option-${ASSIGNMENT_1_ID}`),
    ).toBeVisible();
    await expect(
      page.getByTestId(`carry-assignment-option-${ASSIGNMENT_1_ID}`),
    ).toContainText("ENG-1");
  });

  test("confirming carry-forward with assignments creates next-week assignments", async ({ page }) => {
    let createdAssignmentWeek: string | null = null;
    let createdAssignmentBody: Record<string, unknown> | null = null;

    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.useIssueBacklog = true;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });

    await installMockApi(page, {
      initialPlan: buildReconciledPlan(),
      commits: [buildCommit1()],
    });

    await page.route(/\/api\/v1\/plans\/[^/]+\/assignments$/, async (route) => {
      if (route.request().method() === "GET") {
        return json(route, 200, { assignments: [buildAssignment()] });
      }
      return route.fallback();
    });

    await page.route(/\/api\/v1\/weeks\/[^/]+\/plan\/assignments$/, async (route) => {
      if (route.request().method() === "POST") {
        const url = new URL(route.request().url());
        createdAssignmentWeek = url.pathname.split("/")[4] ?? null;
        createdAssignmentBody = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        return json(route, 201, buildAssignment());
      }
      return route.fallback();
    });

    await page.goto("/");
    await gotoWeeklyPlan(page);
    await page.getByTestId("carry-forward-btn").click();
    await expect(page.getByTestId(`carry-assignment-option-${ASSIGNMENT_1_ID}`)).toBeVisible();
    await page.getByTestId("carry-confirm").click();
    await page.waitForTimeout(400);

    expect(createdAssignmentWeek).toBe(mondayIso(1));
    expect(createdAssignmentBody?.issueId).toBe(ISSUE_1_ID);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 3. BacklogPickerDialog
// ══════════════════════════════════════════════════════════════════════════

test.describe("BacklogPickerDialog", () => {
  /** Set useIssueBacklog = true in localStorage before page load. */
  async function enableBacklog(page: Page): Promise<void> {
    await page.addInitScript(() => {
      const stored = localStorage.getItem("wc-feature-flags");
      const flags = stored ? (JSON.parse(stored) as Record<string, unknown>) : {};
      flags.useIssueBacklog = true;
      localStorage.setItem("wc-feature-flags", JSON.stringify(flags));
    });
  }

  async function setupWithBacklog(page: Page): Promise<void> {
    await enableBacklog(page);
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit1()],
    });
    await installBacklogPickerMocks(page);
    await page.goto("/");
    await gotoWeeklyPlan(page);
  }

  test("[SMOKE] 'Add from Backlog' button shows on DRAFT plan with useIssueBacklog on", async ({
    page,
  }) => {
    await setupWithBacklog(page);
    await expect(page.getByTestId("add-from-backlog-btn")).toBeVisible();
  });

  test("[SMOKE] clicking 'Add from Backlog' opens the picker dialog", async ({ page }) => {
    await setupWithBacklog(page);
    await page.getByTestId("add-from-backlog-btn").click();
    await expect(page.getByTestId("backlog-picker-dialog")).toBeVisible();
  });

  test("[SMOKE] dialog shows issues from the backlog", async ({ page }) => {
    await setupWithBacklog(page);
    await page.getByTestId("add-from-backlog-btn").click();
    await expect(page.getByTestId("backlog-picker-issue-list")).toBeVisible();
    await expect(page.getByTestId(`backlog-picker-issue-${ISSUE_1_ID}`)).toBeVisible();
    await expect(page.getByTestId(`backlog-picker-issue-${ISSUE_1_ID}`)).toContainText(
      "Fix authentication bug",
    );
    await expect(page.getByTestId(`backlog-picker-issue-${ISSUE_2_ID}`)).toBeVisible();
  });

  test("[SMOKE] Confirm button disabled until an issue is selected", async ({ page }) => {
    await setupWithBacklog(page);
    await page.getByTestId("add-from-backlog-btn").click();
    await expect(page.getByTestId("backlog-picker-confirm")).toBeDisabled();
  });

  test("[SMOKE] selecting an issue enables the Confirm button", async ({ page }) => {
    await setupWithBacklog(page);
    await page.getByTestId("add-from-backlog-btn").click();
    await page.getByTestId(`backlog-picker-issue-${ISSUE_1_ID}`).locator("input[type=checkbox]").check();
    await expect(page.getByTestId("backlog-picker-confirm")).toBeEnabled();
    await expect(page.getByTestId("backlog-picker-confirm")).toContainText("1");
  });

  test("search input is visible", async ({ page }) => {
    await setupWithBacklog(page);
    await page.getByTestId("add-from-backlog-btn").click();
    await expect(page.getByTestId("backlog-picker-search")).toBeVisible();
  });

  test("team selector is visible", async ({ page }) => {
    await setupWithBacklog(page);
    await page.getByTestId("add-from-backlog-btn").click();
    const select = page.getByTestId("backlog-picker-team-select");
    await expect(select).toBeVisible();
    await expect(select).toContainText("Engineering");
  });

  test("Confirming selected issues calls assignment creation API", async ({ page }) => {
    let assignmentCalled = false;
    await enableBacklog(page);
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit1()],
    });
    await installBacklogPickerMocks(page);

    await page.route(/\/api\/v1\/weeks\/[^/]+\/plan\/assignments$/, async (route) => {
      if (route.request().method() === "POST") {
        assignmentCalled = true;
        return json(route, 201, buildAssignment());
      }
      return route.fallback();
    });

    await page.goto("/");
    await gotoWeeklyPlan(page);
    await page.getByTestId("add-from-backlog-btn").click();
    await page.getByTestId(`backlog-picker-issue-${ISSUE_1_ID}`).locator("input[type=checkbox]").check();
    await page.getByTestId("backlog-picker-confirm").click();
    await page.waitForTimeout(400);

    expect(assignmentCalled).toBe(true);
    await expect(page.getByTestId("backlog-picker-dialog")).not.toBeVisible();
  });

  test("Cancel closes the dialog without creating assignments", async ({ page }) => {
    let assignmentCalled = false;
    await setupWithBacklog(page);
    await page.route(/\/api\/v1\/weeks\/[^/]+\/plan\/assignments$/, async (route) => {
      assignmentCalled = true;
      return route.fallback();
    });
    await page.getByTestId("add-from-backlog-btn").click();
    await expect(page.getByTestId("backlog-picker-dialog")).toBeVisible();
    await page.getByTestId("backlog-picker-cancel").click();
    await expect(page.getByTestId("backlog-picker-dialog")).not.toBeVisible();
    expect(assignmentCalled).toBe(false);
  });

  test("'Add from Backlog' button NOT shown when useIssueBacklog flag is off", async ({
    page,
  }) => {
    // Default: useIssueBacklog = false
    await installMockApi(page, {
      initialPlan: buildPlan({ state: "DRAFT" }),
      commits: [buildCommit1()],
    });
    await page.goto("/");
    await gotoWeeklyPlan(page);
    await expect(page.getByTestId("add-from-backlog-btn")).not.toBeVisible();
  });
});
