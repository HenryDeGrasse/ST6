/**
 * Browser-based E2E tests for BacklogPage (Issue Backlog CRUD + Filters).
 *
 * All tests run deterministically against mocked APIs — no live backend required.
 *
 * Coverage:
 *   [SMOKE] Navigation to Backlog tab
 *   [SMOKE] Issue table renders with mocked issues
 *   [SMOKE] Status / effort / assignee filter controls are visible
 *           Status filter changes requests
 *           Effort-type filter changes requests
 *           Assignee filter shows team members
 *           AI ranking toggle shows/hides AI Rank column
 *   [SMOKE] New issue modal opens and closes
 *           New issue form submits and table refreshes
 *   [SMOKE] Issue detail panel opens on row click
 *           Issue detail panel shows comment and time-log inputs
 *           Issue detail panel closes via ✕ button
 *           Assign action in detail panel updates assignee
 *           Comment submit posts to API
 *           Time-log submit posts to API
 *   [FULL]  Team selector renders when multiple teams exist
 *           Manage Team button calls onManageTeam
 *           Pagination controls render when totalPages > 1
 *           Pagination next/prev navigate pages
 *           Empty state shown when no teams exist
 *           Error banner shown on API failure
 */
import { expect, test, type Page } from "@playwright/test";
import {
  installMockApi,
  json,
  apiError,
  MOCK_ORG_ID,
  MOCK_USER_ID,
  MOCK_NOW,
  MOCK_OUTCOME_ID,
} from "./helpers";

// ══════════════════════════════════════════════════════════════════════════
// Constants
// ══════════════════════════════════════════════════════════════════════════

const TEAM_1_ID = "team-0000-0000-0000-000000000001";
const TEAM_2_ID = "team-0000-0000-0000-000000000002";
const ISSUE_1_ID = "issue-000-0000-0000-000000000001";
const ISSUE_2_ID = "issue-000-0000-0000-000000000002";
const MEMBER_USER_ID = "c0000000-0000-0000-0000-000000000001"; // Carol Park

function buildTeam(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: TEAM_1_ID,
    orgId: MOCK_ORG_ID,
    name: "Engineering",
    keyPrefix: "ENG",
    description: "Core engineering team",
    ownerUserId: MOCK_USER_ID,
    issueSequence: 2,
    createdAt: MOCK_NOW,
    updatedAt: MOCK_NOW,
    ...overrides,
  };
}

function buildIssue(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: ISSUE_1_ID,
    orgId: MOCK_ORG_ID,
    teamId: TEAM_1_ID,
    issueKey: "ENG-1",
    sequenceNumber: 1,
    title: "Fix authentication bug",
    description: "Users are getting logged out unexpectedly.",
    effortType: "BUILD",
    estimatedHours: 8,
    chessPriority: "QUEEN",
    outcomeId: MOCK_OUTCOME_ID,
    nonStrategicReason: null,
    creatorUserId: MOCK_USER_ID,
    assigneeUserId: MEMBER_USER_ID,
    blockedByIssueId: null,
    status: "OPEN",
    aiRecommendedRank: 2,
    createdAt: MOCK_NOW,
    updatedAt: MOCK_NOW,
    version: 1,
    ...overrides,
  };
}

function buildTeamDetail(
  teamId = TEAM_1_ID,
  extraMembers: Array<Record<string, unknown>> = [],
): Record<string, unknown> {
  return {
    team: buildTeam({ id: teamId }),
    members: [
      {
        teamId,
        userId: MOCK_USER_ID,
        orgId: MOCK_ORG_ID,
        role: "OWNER",
        joinedAt: MOCK_NOW,
      },
      {
        teamId,
        userId: MEMBER_USER_ID,
        orgId: MOCK_ORG_ID,
        role: "MEMBER",
        joinedAt: MOCK_NOW,
      },
      ...extraMembers,
    ],
  };
}

function buildIssueList(issues: Array<Record<string, unknown>>, page = 0, totalPages = 1): Record<string, unknown> {
  return {
    content: issues,
    page,
    size: 20,
    totalElements: issues.length + (totalPages - 1) * 20,
    totalPages,
  };
}

function buildIssueDetail(issue: Record<string, unknown>): Record<string, unknown> {
  return {
    issue,
    activities: [
      {
        id: "act-1",
        orgId: MOCK_ORG_ID,
        issueId: issue.id,
        actorUserId: MOCK_USER_ID,
        activityType: "CREATED",
        oldValue: null,
        newValue: null,
        commentText: null,
        hoursLogged: null,
        metadata: null,
        createdAt: MOCK_NOW,
      },
      {
        id: "act-2",
        orgId: MOCK_ORG_ID,
        issueId: issue.id,
        actorUserId: MOCK_USER_ID,
        activityType: "COMMENT",
        oldValue: null,
        newValue: null,
        commentText: "Initial triage done.",
        hoursLogged: null,
        metadata: null,
        createdAt: MOCK_NOW,
      },
    ],
  };
}

// ══════════════════════════════════════════════════════════════════════════
// Mock route installer for Backlog-specific endpoints
// ══════════════════════════════════════════════════════════════════════════

interface BacklogMockOptions {
  teams?: Array<Record<string, unknown>>;
  issues?: Array<Record<string, unknown>>;
  totalPages?: number;
  denyIssueCreate?: boolean;
}

/**
 * Installs backlog-specific mock routes on top of the base installMockApi.
 * Must be called AFTER installMockApi so these routes take precedence (LIFO).
 *
 * Uses regex URL patterns for precision — glob `*` does not cross path separators
 * so `/api/v1/issues/*` would miss `/api/v1/issues/{id}/comment`.
 */
async function installBacklogMocks(page: Page, opts: BacklogMockOptions = {}): Promise<void> {
  const teams = opts.teams ?? [buildTeam()];
  const issue1 = buildIssue();
  const state = {
    issues: opts.issues ?? [
      issue1,
      buildIssue({
        id: ISSUE_2_ID,
        issueKey: "ENG-2",
        sequenceNumber: 2,
        title: "Improve dashboard load time",
        effortType: "MAINTAIN",
        status: "IN_PROGRESS",
        assigneeUserId: null,
        aiRecommendedRank: 1,
      }),
    ],
  };
  const totalPages = opts.totalPages ?? 1;

  // ── GET /api/v1/teams  (list) ─────────────────────────────────────────────
  await page.route(/\/api\/v1\/teams$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, { teams });
    }
    return route.continue();
  });

  // ── GET /api/v1/teams/{teamId}/access-requests ───────────────────────────
  await page.route(/\/api\/v1\/teams\/[^/]+\/access-requests$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, { requests: [] });
    }
    return route.continue();
  });

  // ── GET /api/v1/teams/{teamId}  (detail, exact — no sub-paths) ────────────
  await page.route(/\/api\/v1\/teams\/[^/]+$/, async (route) => {
    const url = new URL(route.request().url());
    const teamId = url.pathname.split("/").pop() ?? "";
    const method = route.request().method();

    if (method === "GET") {
      const matchedTeam = teams.find((t) => t.id === teamId);
      if (matchedTeam) {
        return json(route, 200, buildTeamDetail(String(matchedTeam.id)));
      }
      return json(route, 404, apiError("Team not found", "NOT_FOUND"));
    }
    return route.continue();
  });

  // ── GET|POST /api/v1/teams/{teamId}/issues ────────────────────────────────
  await page.route(/\/api\/v1\/teams\/[^/]+\/issues(\?.*)?$/, async (route) => {
    const method = route.request().method();
    const url = new URL(route.request().url());
    const teamId = url.pathname.split("/")[4] ?? TEAM_1_ID;

    if (method === "GET") {
      const status = url.searchParams.get("status");
      const effortType = url.searchParams.get("effortType");
      const assigneeUserId = url.searchParams.get("assigneeUserId");
      const sort = url.searchParams.get("sort");
      const pageParam = Number.parseInt(url.searchParams.get("page") ?? "0", 10);
      const size = Number.parseInt(url.searchParams.get("size") ?? "20", 10);

      let filtered = state.issues.filter((issue) => issue.teamId === teamId);
      if (status) filtered = filtered.filter((issue) => issue.status === status);
      if (effortType) filtered = filtered.filter((issue) => issue.effortType === effortType);
      if (assigneeUserId) filtered = filtered.filter((issue) => issue.assigneeUserId === assigneeUserId);

      if (sort === "ai_rank") {
        filtered = [...filtered].sort((a, b) => {
          const aRank = typeof a.aiRecommendedRank === "number" ? a.aiRecommendedRank : Number.MAX_SAFE_INTEGER;
          const bRank = typeof b.aiRecommendedRank === "number" ? b.aiRecommendedRank : Number.MAX_SAFE_INTEGER;
          return aRank - bRank;
        });
      }

      const content = filtered.slice(pageParam * size, (pageParam + 1) * size);
      return json(route, 200, {
        content,
        page: pageParam,
        size,
        totalElements: totalPages > 1 ? Math.max(filtered.length, totalPages * size) : filtered.length,
        totalPages,
      });
    }
    if (method === "POST") {
      if (opts.denyIssueCreate) {
        return json(route, 422, apiError("Validation failed", "VALIDATION_ERROR"));
      }
      const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
      const sequenceNumber = state.issues.length + 1;
      const newIssue = buildIssue({
        id: `issue-new-${String(sequenceNumber).padStart(4, "0")}`,
        issueKey: `ENG-${sequenceNumber}`,
        sequenceNumber,
        teamId,
        title: typeof body.title === "string" ? body.title : "New Issue",
        description: typeof body.description === "string" ? body.description : null,
        effortType: body.effortType ?? null,
        estimatedHours: typeof body.estimatedHours === "number" ? body.estimatedHours : null,
        chessPriority: body.chessPriority ?? null,
        outcomeId: body.outcomeId ?? null,
        assigneeUserId: body.assigneeUserId ?? null,
        status: "OPEN",
      });
      state.issues = [newIssue, ...state.issues];
      return json(route, 201, newIssue);
    }
    return route.continue();
  });

  // ── GET /api/v1/issues/{issueId}  (detail, exact) ────────────────────────
  await page.route(/\/api\/v1\/issues\/[^/]+$/, async (route) => {
    const method = route.request().method();
    const issueId = new URL(route.request().url()).pathname.split("/").pop() ?? "";

    if (method === "GET") {
      const matched = state.issues.find((i) => i.id === issueId);
      return json(route, 200, buildIssueDetail(matched ?? state.issues[0]));
    }
    if (method === "PATCH") {
      const matched = state.issues.find((i) => i.id === issueId) ?? state.issues[0];
      const payload = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
      const updated = { ...matched, ...payload, version: Number(matched.version ?? 1) + 1 };
      state.issues = state.issues.map((issue) => (issue.id === issueId ? updated : issue));
      return json(route, 200, updated);
    }
    return route.continue();
  });

  // ── POST /api/v1/issues/{issueId}/assign ─────────────────────────────────
  await page.route(/\/api\/v1\/issues\/[^/]+\/assign$/, async (route) => {
    if (route.request().method() === "POST") {
      const url = new URL(route.request().url());
      const segments = url.pathname.split("/");
      const issueId = segments[segments.length - 2];
      const payload = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
      const existing = state.issues.find((issue) => issue.id === issueId) ?? buildIssue({ id: issueId });
      const updated = {
        ...existing,
        assigneeUserId: payload.assigneeUserId ?? null,
        version: Number(existing.version ?? 1) + 1,
      };
      state.issues = state.issues.map((issue) => (issue.id === issueId ? updated : issue));
      return json(route, 200, updated);
    }
    return route.continue();
  });

  // ── POST /api/v1/issues/{issueId}/comment ────────────────────────────────
  await page.route(/\/api\/v1\/issues\/[^/]+\/comment$/, async (route) => {
    if (route.request().method() === "POST") {
      const url = new URL(route.request().url());
      const issueId = url.pathname.split("/").slice(-2)[0];
      const payload = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
      return json(route, 201, {
        id: "act-comment-1",
        orgId: MOCK_ORG_ID,
        issueId,
        actorUserId: MOCK_USER_ID,
        activityType: "COMMENT",
        oldValue: null,
        newValue: null,
        commentText: payload.commentText ?? "",
        hoursLogged: null,
        metadata: null,
        createdAt: MOCK_NOW,
      });
    }
    return route.continue();
  });

  // ── POST /api/v1/issues/{issueId}/time-entry ─────────────────────────────
  await page.route(/\/api\/v1\/issues\/[^/]+\/time-entry$/, async (route) => {
    if (route.request().method() === "POST") {
      const url = new URL(route.request().url());
      const issueId = url.pathname.split("/").slice(-2)[0];
      const payload = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
      return json(route, 201, {
        id: "act-time-1",
        orgId: MOCK_ORG_ID,
        issueId,
        actorUserId: MOCK_USER_ID,
        activityType: "TIME_ENTRY",
        oldValue: null,
        newValue: null,
        commentText: null,
        hoursLogged: payload.hoursLogged ?? 0,
        metadata: null,
        createdAt: MOCK_NOW,
      });
    }
    return route.continue();
  });
}

// ══════════════════════════════════════════════════════════════════════════
// Helper: navigate to Backlog page
// ══════════════════════════════════════════════════════════════════════════

async function gotoBacklog(page: Page) {
  await page.getByTestId("nav-backlog").click();
  await expect(page.getByTestId("backlog-page")).toBeVisible();
}

// ══════════════════════════════════════════════════════════════════════════
// Navigation
// ══════════════════════════════════════════════════════════════════════════

test.describe("Backlog — Navigation", () => {
  test("[SMOKE] clicking Backlog nav renders the page", async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page);
    await page.goto("/");
    await gotoBacklog(page);
  });

  test("direct URL /backlog renders the page", async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page);
    await page.goto("/backlog");
    await expect(page.getByTestId("backlog-page")).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Issue Table
// ══════════════════════════════════════════════════════════════════════════

test.describe("Backlog — Issue Table", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page);
    await page.goto("/");
    await gotoBacklog(page);
    await expect(page.getByTestId("issue-table")).toBeVisible();
  });

  test("[SMOKE] issue-table renders", async ({ page }) => {
    await expect(page.getByTestId("issue-table")).toBeVisible();
  });

  test("[SMOKE] mocked issues appear as rows", async ({ page }) => {
    // Issue 1
    await expect(page.getByTestId(`issue-row-${ISSUE_1_ID}`)).toBeVisible();
    await expect(page.getByTestId(`issue-row-${ISSUE_1_ID}`)).toContainText("ENG-1");
    await expect(page.getByTestId(`issue-row-${ISSUE_1_ID}`)).toContainText("Fix authentication bug");
    // Issue 2
    await expect(page.getByTestId(`issue-row-${ISSUE_2_ID}`)).toBeVisible();
    await expect(page.getByTestId(`issue-row-${ISSUE_2_ID}`)).toContainText("ENG-2");
  });

  test("issue row shows effort-type badge", async ({ page }) => {
    // ENG-1 has effortType: BUILD → shows "Build"
    await expect(page.getByTestId(`issue-row-${ISSUE_1_ID}`)).toContainText("Build");
  });

  test("issue row shows status badge", async ({ page }) => {
    // ENG-1 status: OPEN
    await expect(page.getByTestId(`issue-row-${ISSUE_1_ID}`)).toContainText("OPEN");
    // ENG-2 status: IN_PROGRESS
    await expect(page.getByTestId(`issue-row-${ISSUE_2_ID}`)).toContainText("IN PROGRESS");
  });

  test("issue row shows assigned team member name", async ({ page }) => {
    // ENG-1 assignee is Carol Park (c0000000-0000-0000-0000-000000000001)
    await expect(page.getByTestId(`issue-row-${ISSUE_1_ID}`)).toContainText("Carol Park");
  });

  test("unassigned issue shows 'Unassigned'", async ({ page }) => {
    // ENG-2 has no assignee
    await expect(page.getByTestId(`issue-row-${ISSUE_2_ID}`)).toContainText("Unassigned");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Filters
// ══════════════════════════════════════════════════════════════════════════

test.describe("Backlog — Filters", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page);
    await page.goto("/");
    await gotoBacklog(page);
  });

  test("[SMOKE] status filter control is visible", async ({ page }) => {
    await expect(page.getByTestId("backlog-status-filter")).toBeVisible();
  });

  test("[SMOKE] effort-type filter control is visible", async ({ page }) => {
    await expect(page.getByTestId("backlog-effort-filter")).toBeVisible();
  });

  test("[SMOKE] assignee filter control is visible", async ({ page }) => {
    await expect(page.getByTestId("backlog-assignee-filter")).toBeVisible();
  });

  test("assignee filter shows team members", async ({ page }) => {
    const assigneeSelect = page.getByTestId("backlog-assignee-filter");
    // Carol Park should appear as an option (MEMBER_USER_ID → "Carol Park")
    await expect(assigneeSelect.getByRole("option", { name: "Carol Park" })).toBeAttached();
  });

  test("selecting status filter narrows the issue table", async ({ page }) => {
    await page.getByTestId("backlog-status-filter").selectOption("OPEN");
    await expect(page.getByTestId(`issue-row-${ISSUE_1_ID}`)).toBeVisible();
    await expect(page.getByTestId(`issue-row-${ISSUE_2_ID}`)).not.toBeVisible();
  });

  test("selecting effort-type filter narrows the issue table", async ({ page }) => {
    await page.getByTestId("backlog-effort-filter").selectOption("MAINTAIN");
    await expect(page.getByTestId(`issue-row-${ISSUE_2_ID}`)).toBeVisible();
    await expect(page.getByTestId(`issue-row-${ISSUE_1_ID}`)).not.toBeVisible();
  });

  test("selecting assignee filter narrows the issue table", async ({ page }) => {
    await page.getByTestId("backlog-assignee-filter").selectOption(MEMBER_USER_ID);
    await expect(page.getByTestId(`issue-row-${ISSUE_1_ID}`)).toBeVisible();
    await expect(page.getByTestId(`issue-row-${ISSUE_2_ID}`)).not.toBeVisible();
  });

  test("AI ranking toggle shows AI Rank column", async ({ page }) => {
    // AI Rank column should NOT be in the table header initially
    const table = page.getByTestId("issue-table");
    await expect(table).not.toContainText("AI Rank");

    // Toggle on
    await page.getByTestId("backlog-ai-rank-toggle").check();
    await expect(table).toContainText("AI Rank");

    // ENG-1 has aiRecommendedRank: 2, so #2 should appear
    await expect(page.getByTestId(`issue-row-${ISSUE_1_ID}`)).toContainText("#2");
  });

  test("AI ranking toggle uncheck removes AI Rank column", async ({ page }) => {
    await page.getByTestId("backlog-ai-rank-toggle").check();
    await expect(page.getByTestId("issue-table")).toContainText("AI Rank");
    await page.getByTestId("backlog-ai-rank-toggle").uncheck();
    await expect(page.getByTestId("issue-table")).not.toContainText("AI Rank");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// New Issue Modal
// ══════════════════════════════════════════════════════════════════════════

test.describe("Backlog — New Issue Modal", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page, {
      teams: [
        buildTeam({ id: TEAM_1_ID, name: "Engineering" }),
        buildTeam({ id: TEAM_2_ID, name: "Product", keyPrefix: "PRD" }),
      ],
    });
    await page.goto("/");
    await gotoBacklog(page);
    await expect(page.getByTestId(`issue-row-${ISSUE_1_ID}`)).toBeVisible();
  });

  test("[SMOKE] clicking + New Issue opens the modal", async ({ page }) => {
    await page.getByTestId("backlog-new-issue-btn").click();
    await expect(page.getByTestId("new-issue-modal")).toBeVisible();
    await expect(page.getByTestId("issue-create-form")).toBeVisible();
  });

  test("[SMOKE] modal closes via Cancel button", async ({ page }) => {
    await page.getByTestId("backlog-new-issue-btn").click();
    await expect(page.getByTestId("new-issue-modal")).toBeVisible();
    await page.getByTestId("issue-create-cancel").click();
    await expect(page.getByTestId("new-issue-modal")).not.toBeVisible();
  });

  test("modal closes by clicking overlay backdrop", async ({ page }) => {
    await page.getByTestId("backlog-new-issue-btn").click();
    await expect(page.getByTestId("new-issue-modal")).toBeVisible();
    // Click the overlay (not the modal itself)
    await page.getByTestId("new-issue-modal").click({ position: { x: 5, y: 5 } });
    await expect(page.getByTestId("new-issue-modal")).not.toBeVisible();
  });

  test("submitting the new issue form posts the create request", async ({ page }) => {
    let createCalled = false;
    await page.route(/\/api\/v1\/teams\/[^/]+\/issues$/, async (route) => {
      if (route.request().method() === "POST") {
        createCalled = true;
        expect(route.request().postDataJSON()).toMatchObject({ title: "New E2E Test Issue" });
        return json(route, 201, buildIssue({ id: "issue-new-route", issueKey: "ENG-99", title: "New E2E Test Issue" }));
      }
      return route.continue();
    });

    await page.getByTestId("backlog-new-issue-btn").click();
    await expect(page.getByTestId("issue-create-form")).toBeVisible();

    await page.getByTestId("issue-title-input").fill("New E2E Test Issue");
    await expect(page.getByTestId("issue-title-input")).toHaveValue("New E2E Test Issue");
    await page.getByTestId("issue-team-select").selectOption(TEAM_1_ID);
    await expect(page.getByTestId("issue-team-select")).toHaveValue(TEAM_1_ID);
    await page.getByTestId("issue-create-form").evaluate((form) => {
      form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    });

    await page.waitForTimeout(300);
    expect(createCalled).toBe(true);
  });

  test("new issue form title is required — form does not submit empty", async ({ page }) => {
    await page.getByTestId("backlog-new-issue-btn").click();
    // Try submitting without a title
    await page.getByTestId("issue-create-submit").click();
    // Modal should remain visible (HTML5 required validation)
    await expect(page.getByTestId("new-issue-modal")).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Issue Detail Panel
// ══════════════════════════════════════════════════════════════════════════

test.describe("Backlog — Issue Detail Panel", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page);
    await page.goto("/");
    await gotoBacklog(page);
  });

  test("[SMOKE] clicking an issue row opens the detail panel", async ({ page }) => {
    await page.getByTestId(`issue-row-${ISSUE_1_ID}`).click();
    await expect(page.getByTestId("issue-detail-panel")).toBeVisible();
  });

  test("[SMOKE] detail panel shows comment input", async ({ page }) => {
    await page.getByTestId(`issue-row-${ISSUE_1_ID}`).click();
    await expect(page.getByTestId("issue-comment-input")).toBeVisible();
  });

  test("[SMOKE] detail panel shows time-log input", async ({ page }) => {
    await page.getByTestId(`issue-row-${ISSUE_1_ID}`).click();
    await expect(page.getByTestId("issue-time-input")).toBeVisible();
  });

  test("[SMOKE] detail panel closes via ✕ button", async ({ page }) => {
    await page.getByTestId(`issue-row-${ISSUE_1_ID}`).click();
    await expect(page.getByTestId("issue-detail-panel")).toBeVisible();
    await page.getByTestId("issue-detail-close").click();
    await expect(page.getByTestId("issue-detail-panel")).not.toBeVisible();
  });

  test("detail panel shows activity feed with comment", async ({ page }) => {
    await page.getByTestId(`issue-row-${ISSUE_1_ID}`).click();
    // buildIssueDetail includes a COMMENT activity with text "Initial triage done."
    await expect(page.getByTestId("issue-detail-panel")).toContainText("Initial triage done.");
  });

  test("detail panel shows assignee select", async ({ page }) => {
    await page.getByTestId(`issue-row-${ISSUE_1_ID}`).click();
    await expect(page.getByTestId("issue-assignee-select")).toBeVisible();
  });

  test("changing assignee updates the issue", async ({ page }) => {
    await page.getByTestId(`issue-row-${ISSUE_1_ID}`).click();
    const assigneeSelect = page.getByTestId("issue-assignee-select");
    await assigneeSelect.selectOption(MOCK_USER_ID);
    await expect(assigneeSelect).toHaveValue(MOCK_USER_ID);
  });

  test("submitting a comment calls comment API", async ({ page }) => {
    let commentCalled = false;
    // Track call via a more specific route registered after installBacklogMocks (LIFO)
    await page.route(new RegExp(`/api/v1/issues/${ISSUE_1_ID}/comment$`), async (route) => {
      if (route.request().method() === "POST") {
        commentCalled = true;
        return json(route, 201, {
          id: "act-new",
          orgId: MOCK_ORG_ID,
          issueId: ISSUE_1_ID,
          actorUserId: MOCK_USER_ID,
          activityType: "COMMENT",
          oldValue: null,
          newValue: null,
          commentText: "Test comment",
          hoursLogged: null,
          metadata: null,
          createdAt: MOCK_NOW,
        });
      }
      return route.continue();
    });

    await page.getByTestId(`issue-row-${ISSUE_1_ID}`).click();
    await page.getByTestId("issue-comment-input").fill("Test comment");
    await page.getByTestId("issue-comment-submit").click();
    await page.waitForTimeout(300);
    expect(commentCalled).toBe(true);
  });

  test("submitting time log calls time-entry API", async ({ page }) => {
    let timeEntryCalled = false;
    // Track call via a more specific route registered after installBacklogMocks (LIFO)
    await page.route(new RegExp(`/api/v1/issues/${ISSUE_1_ID}/time-entry$`), async (route) => {
      if (route.request().method() === "POST") {
        timeEntryCalled = true;
        return json(route, 201, {
          id: "act-time",
          orgId: MOCK_ORG_ID,
          issueId: ISSUE_1_ID,
          actorUserId: MOCK_USER_ID,
          activityType: "TIME_ENTRY",
          oldValue: null,
          newValue: null,
          commentText: null,
          hoursLogged: 2,
          metadata: null,
          createdAt: MOCK_NOW,
        });
      }
      return route.continue();
    });

    await page.getByTestId(`issue-row-${ISSUE_1_ID}`).click();
    await page.getByTestId("issue-time-input").fill("2");
    await page.getByTestId("issue-time-submit").click();
    await page.waitForTimeout(300);
    expect(timeEntryCalled).toBe(true);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Multi-team Selector
// ══════════════════════════════════════════════════════════════════════════

test.describe("Backlog — Team Selector", () => {
  test("team selector is hidden when only one team", async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page, { teams: [buildTeam()] });
    await page.goto("/");
    await gotoBacklog(page);
    await expect(page.getByTestId("backlog-team-select")).not.toBeVisible();
  });

  test("team selector renders when multiple teams exist", async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page, {
      teams: [
        buildTeam({ id: TEAM_1_ID, name: "Engineering" }),
        buildTeam({ id: TEAM_2_ID, name: "Product", keyPrefix: "PRD" }),
      ],
    });
    await page.goto("/");
    await gotoBacklog(page);
    const select = page.getByTestId("backlog-team-select");
    await expect(select).toBeVisible();
    await expect(select.getByRole("option", { name: "Engineering" })).toBeAttached();
    await expect(select.getByRole("option", { name: "Product" })).toBeAttached();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Manage Team Button
// ══════════════════════════════════════════════════════════════════════════

test.describe("Backlog — Manage Team Button", () => {
  test("Manage Team button navigates to team management", async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page);
    await page.goto("/");
    await gotoBacklog(page);

    await page.getByTestId("backlog-manage-team-btn").click();
    await expect(page.getByTestId("team-management-page")).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Pagination
// ══════════════════════════════════════════════════════════════════════════

test.describe("Backlog — Pagination", () => {
  test("pagination controls render when totalPages > 1", async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page, { totalPages: 3 });
    await page.goto("/");
    await gotoBacklog(page);
    await expect(page.getByTestId("backlog-prev-page")).toBeVisible();
    await expect(page.getByTestId("backlog-next-page")).toBeVisible();
  });

  test("pagination controls are hidden when only one page", async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page, { totalPages: 1 });
    await page.goto("/");
    await gotoBacklog(page);
    await expect(page.getByTestId("backlog-prev-page")).not.toBeVisible();
    await expect(page.getByTestId("backlog-next-page")).not.toBeVisible();
  });

  test("Prev button is disabled on the first page", async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page, { totalPages: 3 });
    await page.goto("/");
    await gotoBacklog(page);
    await expect(page.getByTestId("backlog-prev-page")).toBeDisabled();
    await expect(page.getByTestId("backlog-next-page")).toBeEnabled();
  });

  test("clicking Next navigates to page 2", async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page, { totalPages: 3 });
    await page.goto("/");
    await gotoBacklog(page);

    await page.getByTestId("backlog-next-page").click();
    await expect(page.getByText("Page 2 of 3")).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Empty & Error States
// ══════════════════════════════════════════════════════════════════════════

test.describe("Backlog — Empty & Error States", () => {
  test("empty state shown when no teams exist", async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page, { teams: [] });
    await page.goto("/");
    await gotoBacklog(page);
    await expect(page.getByTestId("backlog-page")).toContainText("any team");
  });

  test("empty table state shown when team has no issues", async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page, { issues: [] });
    await page.goto("/");
    await gotoBacklog(page);
    await expect(page.getByTestId("issue-table")).toContainText("No issues found");
  });

  test("error banner shown when teams API fails", async ({ page }) => {
    await installMockApi(page);
    await installBacklogMocks(page);
    await page.route(/\/api\/v1\/teams$/, async (route) => {
      if (route.request().method() === "GET") {
        return json(route, 500, apiError("Internal server error", "INTERNAL_ERROR"));
      }
      return route.continue();
    });
    await page.goto("/");
    await page.getByTestId("nav-backlog").click();
    await expect(page.getByTestId("backlog-page")).toBeVisible();
    await expect(page.getByTestId("error-banner")).toContainText("Internal server error");
  });
});
