/**
 * Browser-based E2E tests for TeamManagementPage.
 *
 * All tests run deterministically against mocked APIs — no live backend required.
 *
 * Coverage:
 *   [SMOKE] Navigation from Backlog "Manage Team" button
 *   [SMOKE] Direct URL /team-management renders the page
 *   [SMOKE] Team detail display (name, prefix, description)
 *   [SMOKE] Edit mode — edit team name and description, save
 *           Cancel edit mode discards changes
 *           Member table renders with members
 *   [SMOKE] Add member form is visible for owner
 *           Add member submits and shows new member
 *           Remove member button visible for owner, triggers API
 *   [SMOKE] Create Team modal opens, auto-derives prefix from name
 *           Create Team form submit creates team and selects it
 *           Create Team modal cancel closes without creating
 *   [SMOKE] Access requests section shows pending requests for owner
 *           Approve request calls API and updates status
 *           Deny request calls API and updates status
 *           Non-member sees "Request Access" button
 *           Request Access button submits and shows confirmation
 *   [FULL]  Back button navigates to backlog
 *           Team selector renders when multiple teams exist
 *           Empty state shown when no teams
 *           Error banner shown on API failure
 */
import { expect, test, type Page } from "@playwright/test";
import {
  installMockApi,
  json,
  apiError,
  MOCK_ORG_ID,
  MOCK_NOW,
} from "./helpers";

// ══════════════════════════════════════════════════════════════════════════
// Constants
// ══════════════════════════════════════════════════════════════════════════

/** Carol Park — the default E2E persona (IC + MANAGER). Owns team by default. */
const CAROL_USER_ID = "c0000000-0000-0000-0000-000000000001";
/** Alice Chen — non-owner member in some tests. */
const ALICE_USER_ID = "c0000000-0000-0000-0000-000000000010";
/** Bob Martinez — non-member in some tests. */
const BOB_USER_ID = "c0000000-0000-0000-0000-000000000020";

const TEAM_1_ID = "team-mgmt-0000-0000-000000000001";
const TEAM_2_ID = "team-mgmt-0000-0000-000000000002";
const ACCESS_REQUEST_ID = "access-req-000000000001";

// ══════════════════════════════════════════════════════════════════════════
// Builders
// ══════════════════════════════════════════════════════════════════════════

function buildTeam(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: TEAM_1_ID,
    orgId: MOCK_ORG_ID,
    name: "Engineering",
    keyPrefix: "ENG",
    description: "Core engineering team",
    ownerUserId: CAROL_USER_ID, // Carol is owner by default
    issueSequence: 5,
    createdAt: MOCK_NOW,
    updatedAt: MOCK_NOW,
    ...overrides,
  };
}

function buildTeamDetail(
  teamId = TEAM_1_ID,
  ownerUserId = CAROL_USER_ID,
  extraMembers: Array<Record<string, unknown>> = [],
): Record<string, unknown> {
  return {
    team: buildTeam({ id: teamId, ownerUserId }),
    members: [
      {
        teamId,
        userId: ownerUserId,
        orgId: MOCK_ORG_ID,
        role: "OWNER",
        joinedAt: MOCK_NOW,
      },
      {
        teamId,
        userId: ALICE_USER_ID,
        orgId: MOCK_ORG_ID,
        role: "MEMBER",
        joinedAt: MOCK_NOW,
      },
      ...extraMembers,
    ],
  };
}

function buildAccessRequest(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: ACCESS_REQUEST_ID,
    teamId: TEAM_1_ID,
    requesterUserId: BOB_USER_ID,
    orgId: MOCK_ORG_ID,
    status: "PENDING",
    decidedByUserId: null,
    decidedAt: null,
    createdAt: MOCK_NOW,
    ...overrides,
  };
}

// ══════════════════════════════════════════════════════════════════════════
// Mock installer for Team Management endpoints
// ══════════════════════════════════════════════════════════════════════════

interface TeamMgmtMockOptions {
  teams?: Array<Record<string, unknown>>;
  /** Override team owner (default: CAROL_USER_ID so Carol is owner) */
  ownerUserId?: string;
  /** Members to include in addition to owner + Alice */
  extraMembers?: Array<Record<string, unknown>>;
  /** Whether Carol is a member but NOT the owner */
  carolIsMemberNotOwner?: boolean;
  /** Whether Carol is neither owner nor member (non-member view) */
  carolIsNonMember?: boolean;
  /** Access requests to return */
  accessRequests?: Array<Record<string, unknown>>;
}

/**
 * Install team management mocks AFTER installMockApi so they take LIFO precedence.
 */
async function installTeamMgmtMocks(page: Page, opts: TeamMgmtMockOptions = {}): Promise<void> {
  const ownerUserId = opts.carolIsNonMember
    ? ALICE_USER_ID
    : opts.carolIsMemberNotOwner
      ? ALICE_USER_ID
      : (opts.ownerUserId ?? CAROL_USER_ID);

  const teams = opts.teams ?? [buildTeam({ ownerUserId })];
  const accessRequests = opts.accessRequests ?? [];

  // Compute members for team detail
  const baseMembers: Array<Record<string, unknown>> = [
    { teamId: TEAM_1_ID, userId: ownerUserId, orgId: MOCK_ORG_ID, role: "OWNER", joinedAt: MOCK_NOW },
  ];
  if (!opts.carolIsNonMember && ownerUserId !== CAROL_USER_ID) {
    // Carol is a member but not owner
    baseMembers.push({ teamId: TEAM_1_ID, userId: CAROL_USER_ID, orgId: MOCK_ORG_ID, role: "MEMBER", joinedAt: MOCK_NOW });
  }
  if (ownerUserId !== ALICE_USER_ID) {
    baseMembers.push({ teamId: TEAM_1_ID, userId: ALICE_USER_ID, orgId: MOCK_ORG_ID, role: "MEMBER", joinedAt: MOCK_NOW });
  }
  const allMembers = [...baseMembers, ...(opts.extraMembers ?? [])];

  // State for mutations
  const state = {
    teams: [...teams],
    members: allMembers,
    accessRequests: [...accessRequests],
  };

  // ── GET /api/v1/teams (list) ──────────────────────────────────────────
  await page.route(/\/api\/v1\/teams$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, { teams: state.teams });
    }
    if (route.request().method() === "POST") {
      const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
      const newTeam = buildTeam({
        id: `team-new-${Date.now()}`,
        name: body.name ?? "New Team",
        keyPrefix: body.keyPrefix ?? "NEW",
        description: body.description ?? null,
        ownerUserId: CAROL_USER_ID,
      });
      state.teams = [...state.teams, newTeam];
      return json(route, 201, newTeam);
    }
    return route.fallback();
  });

  // ── GET/PATCH /api/v1/teams/{teamId} (detail, exact) ──────────────────
  await page.route(/\/api\/v1\/teams\/[^/]+$/, async (route) => {
    const url = new URL(route.request().url());
    const teamId = url.pathname.split("/").pop() ?? "";
    const method = route.request().method();

    if (method === "GET") {
      const matchedTeam = state.teams.find((t) => t.id === teamId);
      if (!matchedTeam) return json(route, 404, apiError("Team not found", "NOT_FOUND"));
      return json(route, 200, {
        team: matchedTeam,
        members: state.members.filter((m) => m.teamId === teamId),
      });
    }
    if (method === "PATCH") {
      const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
      const existing = state.teams.find((t) => t.id === teamId);
      if (!existing) return json(route, 404, apiError("Team not found", "NOT_FOUND"));
      const updated = { ...existing, ...body, updatedAt: MOCK_NOW };
      state.teams = state.teams.map((t) => (t.id === teamId ? updated : t));
      return json(route, 200, updated);
    }
    return route.fallback();
  });

  // ── GET /api/v1/teams/{teamId}/access-requests (list) ─────────────────
  await page.route(/\/api\/v1\/teams\/[^/]+\/access-requests$/, async (route) => {
    const method = route.request().method();
    if (method === "GET") {
      return json(route, 200, { requests: state.accessRequests });
    }
    if (method === "POST") {
      // Non-member requesting access
      const url = new URL(route.request().url());
      const teamId = url.pathname.split("/")[4] ?? TEAM_1_ID;
      const newRequest = buildAccessRequest({
        id: `access-req-new-${Date.now()}`,
        teamId,
        requesterUserId: CAROL_USER_ID,
        status: "PENDING",
      });
      state.accessRequests = [...state.accessRequests, newRequest];
      return json(route, 201, newRequest);
    }
    return route.fallback();
  });

  // ── PATCH /api/v1/teams/{teamId}/access-requests/{requestId} ──────────
  await page.route(/\/api\/v1\/teams\/[^/]+\/access-requests\/[^/]+$/, async (route) => {
    if (route.request().method() === "PATCH") {
      const url = new URL(route.request().url());
      const segments = url.pathname.split("/");
      const requestId = segments[segments.length - 1];
      const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
      state.accessRequests = state.accessRequests.map((r) =>
        r.id === requestId ? { ...r, status: body.status ?? r.status, decidedAt: MOCK_NOW, decidedByUserId: CAROL_USER_ID } : r,
      );
      return json(route, 200, {});
    }
    return route.fallback();
  });

  // ── POST /api/v1/teams/{teamId}/members ───────────────────────────────
  await page.route(/\/api\/v1\/teams\/[^/]+\/members$/, async (route) => {
    if (route.request().method() === "POST") {
      const url = new URL(route.request().url());
      const teamId = url.pathname.split("/")[4] ?? TEAM_1_ID;
      const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
      const newMember = {
        teamId,
        userId: body.userId ?? "new-user-id",
        orgId: MOCK_ORG_ID,
        role: body.role ?? "MEMBER",
        joinedAt: MOCK_NOW,
      };
      state.members = [...state.members, newMember];
      return json(route, 201, newMember);
    }
    return route.fallback();
  });

  // ── DELETE /api/v1/teams/{teamId}/members/{userId} ────────────────────
  await page.route(/\/api\/v1\/teams\/[^/]+\/members\/[^/]+$/, async (route) => {
    if (route.request().method() === "DELETE") {
      const url = new URL(route.request().url());
      const segments = url.pathname.split("/");
      const removedUserId = segments[segments.length - 1];
      state.members = state.members.filter((m) => m.userId !== removedUserId);
      return json(route, 204, {});
    }
    return route.fallback();
  });

  // ── GET /api/v1/teams/{teamId}/issues (stub — backlog page needs it) ──
  await page.route(/\/api\/v1\/teams\/[^/]+\/issues(\?.*)?$/, async (route) => {
    if (route.request().method() === "GET") {
      return json(route, 200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    }
    return route.fallback();
  });
}

// ══════════════════════════════════════════════════════════════════════════
// Helper: go directly to Team Management page
// ══════════════════════════════════════════════════════════════════════════

async function gotoTeamManagement(page: Page) {
  await page.goto("/team-management");
  await expect(page.getByTestId("team-management-page")).toBeVisible();
}

// ══════════════════════════════════════════════════════════════════════════
// Navigation
// ══════════════════════════════════════════════════════════════════════════

test.describe("Team Management — Navigation", () => {
  test("[SMOKE] direct URL /team-management renders the page", async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page);
    await gotoTeamManagement(page);
  });

  test("[SMOKE] Manage Team button from Backlog navigates here", async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page);
    await page.goto("/backlog");
    await expect(page.getByTestId("backlog-page")).toBeVisible();
    await expect(page.getByTestId("backlog-manage-team-btn")).toBeVisible();
    await page.getByTestId("backlog-manage-team-btn").click();
    await expect(page.getByTestId("team-management-page")).toBeVisible();
  });

  test("back button navigates to backlog", async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page);
    await page.goto("/team-management");
    await expect(page.getByTestId("team-management-page")).toBeVisible();
    await page.getByTestId("team-mgmt-back-btn").click();
    await expect(page.getByTestId("backlog-page")).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Team Detail Display
// ══════════════════════════════════════════════════════════════════════════

test.describe("Team Management — Team Detail", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page);
    await gotoTeamManagement(page);
  });

  test("[SMOKE] team-info-section renders", async ({ page }) => {
    await expect(page.getByTestId("team-info-section")).toBeVisible();
  });

  test("[SMOKE] team name is displayed", async ({ page }) => {
    await expect(page.getByTestId("team-name-display")).toBeVisible();
    await expect(page.getByTestId("team-name-display")).toContainText("Engineering");
  });

  test("[SMOKE] team key prefix is displayed", async ({ page }) => {
    await expect(page.getByTestId("team-prefix-display")).toContainText("ENG");
  });

  test("team description is displayed when present", async ({ page }) => {
    await expect(page.getByTestId("team-desc-display")).toContainText("Core engineering team");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Team Detail Edit
// ══════════════════════════════════════════════════════════════════════════

test.describe("Team Management — Edit Team", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page); // Carol is owner
    await gotoTeamManagement(page);
  });

  test("[SMOKE] Edit button is visible for owner", async ({ page }) => {
    await expect(page.getByTestId("edit-team-btn")).toBeVisible();
  });

  test("[SMOKE] clicking Edit shows name and description inputs", async ({ page }) => {
    await page.getByTestId("edit-team-btn").click();
    await expect(page.getByTestId("edit-team-name-input")).toBeVisible();
    await expect(page.getByTestId("edit-team-desc-input")).toBeVisible();
  });

  test("editing name and saving calls update API", async ({ page }) => {
    let updateCalled = false;
    await page.route(/\/api\/v1\/teams\/[^/]+$/, async (route) => {
      if (route.request().method() === "PATCH") {
        updateCalled = true;
        const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        return json(route, 200, buildTeam({ name: body.name ?? "Engineering", ownerUserId: CAROL_USER_ID }));
      }
      return route.fallback();
    });

    await page.getByTestId("edit-team-btn").click();
    await page.getByTestId("edit-team-name-input").fill("Platform Engineering");
    await page.getByTestId("save-team-btn").click();
    await page.waitForTimeout(300);
    expect(updateCalled).toBe(true);
  });

  test("Cancel edit discards changes and hides inputs", async ({ page }) => {
    await page.getByTestId("edit-team-btn").click();
    await expect(page.getByTestId("edit-team-name-input")).toBeVisible();
    await page.getByTestId("edit-team-name-input").fill("Should Not Save");
    await page.getByTestId("cancel-edit-team-btn").click();
    await expect(page.getByTestId("edit-team-name-input")).not.toBeVisible();
    // Original name still shown
    await expect(page.getByTestId("team-name-display")).toContainText("Engineering");
  });

  test("Edit button is NOT visible for non-owner member", async ({ page }) => {
    // Carol is member but not owner
    await installMockApi(page);
    await installTeamMgmtMocks(page, { carolIsMemberNotOwner: true });
    await gotoTeamManagement(page);
    await expect(page.getByTestId("edit-team-btn")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Member Management
// ══════════════════════════════════════════════════════════════════════════

test.describe("Team Management — Members", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page); // Carol is owner, Alice is member
    await gotoTeamManagement(page);
  });

  test("[SMOKE] members-section renders", async ({ page }) => {
    await expect(page.getByTestId("members-section")).toBeVisible();
  });

  test("[SMOKE] member-table renders", async ({ page }) => {
    await expect(page.getByTestId("member-table")).toBeVisible();
  });

  test("[SMOKE] member rows render for each member", async ({ page }) => {
    // Carol (owner) and Alice (member) rows
    await expect(page.getByTestId(`member-row-${CAROL_USER_ID}`)).toBeVisible();
    await expect(page.getByTestId(`member-row-${ALICE_USER_ID}`)).toBeVisible();
  });

  test("owner role badge renders", async ({ page }) => {
    await expect(page.getByTestId(`member-row-${CAROL_USER_ID}`)).toContainText("OWNER");
  });

  test("member role badge renders", async ({ page }) => {
    await expect(page.getByTestId(`member-row-${ALICE_USER_ID}`)).toContainText("MEMBER");
  });

  test("[SMOKE] add-member-form is visible for owner", async ({ page }) => {
    await expect(page.getByTestId("add-member-form")).toBeVisible();
    await expect(page.getByTestId("add-member-input")).toBeVisible();
    await expect(page.getByTestId("add-member-btn")).toBeVisible();
  });

  test("remove-member button is visible for each non-self member", async ({ page }) => {
    // Alice can be removed (she's not Carol/self)
    await expect(page.getByTestId(`remove-member-${ALICE_USER_ID}`)).toBeVisible();
    // Carol cannot remove herself
    await expect(page.getByTestId(`remove-member-${CAROL_USER_ID}`)).not.toBeVisible();
  });

  test("add member button is disabled when input is empty", async ({ page }) => {
    await expect(page.getByTestId("add-member-btn")).toBeDisabled();
  });

  test("filling add-member input enables the Add Member button", async ({ page }) => {
    await page.getByTestId("add-member-input").fill(BOB_USER_ID);
    await expect(page.getByTestId("add-member-btn")).toBeEnabled();
  });

  test("submitting add-member calls API and shows new row", async ({ page }) => {
    await page.getByTestId("add-member-input").fill(BOB_USER_ID);
    await page.getByTestId("add-member-btn").click();
    await page.waitForTimeout(400);
    // Bob should appear in the member table
    await expect(page.getByTestId(`member-row-${BOB_USER_ID}`)).toBeVisible();
  });

  test("clicking Remove triggers member removal", async ({ page }) => {
    let removeCalled = false;
    await page.route(new RegExp(`/api/v1/teams/${TEAM_1_ID}/members/${ALICE_USER_ID}$`), async (route) => {
      if (route.request().method() === "DELETE") {
        removeCalled = true;
        return json(route, 204, {});
      }
      return route.fallback();
    });

    await page.getByTestId(`remove-member-${ALICE_USER_ID}`).click();
    await page.waitForTimeout(300);
    expect(removeCalled).toBe(true);
  });

  test("add-member-form is NOT visible for non-owner member", async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page, { carolIsMemberNotOwner: true });
    await gotoTeamManagement(page);
    await expect(page.getByTestId("add-member-form")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Create Team Modal
// ══════════════════════════════════════════════════════════════════════════

test.describe("Team Management — Create Team", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page);
    await gotoTeamManagement(page);
  });

  test("[SMOKE] Create Team button is visible for MANAGER role", async ({ page }) => {
    // Default persona is Carol (IC + MANAGER)
    await expect(page.getByTestId("create-team-btn")).toBeVisible();
  });

  test("[SMOKE] clicking Create Team opens the modal", async ({ page }) => {
    await page.getByTestId("create-team-btn").click();
    await expect(page.getByTestId("create-team-modal")).toBeVisible();
  });

  test("[SMOKE] prefix auto-derives from team name (multi-word)", async ({ page }) => {
    await page.getByTestId("create-team-btn").click();
    await page.getByTestId("create-team-name-input").fill("Platform Engineering");
    // "Platform Engineering" → PE (first letters of each word)
    await expect(page.getByTestId("create-team-prefix-input")).toHaveValue("PE");
  });

  test("prefix auto-derives from team name (single word)", async ({ page }) => {
    await page.getByTestId("create-team-btn").click();
    await page.getByTestId("create-team-name-input").fill("Frontend");
    // "Frontend" → first 4 chars = "FRON"
    await expect(page.getByTestId("create-team-prefix-input")).toHaveValue("FRON");
  });

  test("prefix preview text appears after typing a name", async ({ page }) => {
    await page.getByTestId("create-team-btn").click();
    await page.getByTestId("create-team-name-input").fill("Backend");
    // Prefix preview: "Issues will be keyed: BACK-1, BACK-2, …"
    await expect(page.getByTestId("create-team-modal")).toContainText("Issues will be keyed:");
  });

  test("prefix can be overridden manually", async ({ page }) => {
    await page.getByTestId("create-team-btn").click();
    await page.getByTestId("create-team-name-input").fill("Backend Services");
    // Auto-prefix = BS; override to "SVC"
    await page.getByTestId("create-team-prefix-input").fill("SVC");
    await expect(page.getByTestId("create-team-prefix-input")).toHaveValue("SVC");
  });

  test("Cancel closes the modal without creating a team", async ({ page }) => {
    await page.getByTestId("create-team-btn").click();
    await expect(page.getByTestId("create-team-modal")).toBeVisible();
    await page.getByTestId("create-team-cancel").click();
    await expect(page.getByTestId("create-team-modal")).not.toBeVisible();
  });

  test("Create Team submit button is disabled when name is empty", async ({ page }) => {
    await page.getByTestId("create-team-btn").click();
    await expect(page.getByTestId("create-team-submit")).toBeDisabled();
  });

  test("submitting the form calls POST /teams and closes modal", async ({ page }) => {
    let createCalled = false;
    await page.route(/\/api\/v1\/teams$/, async (route) => {
      if (route.request().method() === "POST") {
        createCalled = true;
        const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        return json(route, 201, buildTeam({
          id: "team-created-1",
          name: body.name ?? "New Team",
          keyPrefix: body.keyPrefix ?? "NEW",
          ownerUserId: CAROL_USER_ID,
        }));
      }
      if (route.request().method() === "GET") {
        return json(route, 200, { teams: [buildTeam()] });
      }
      return route.fallback();
    });

    await page.getByTestId("create-team-btn").click();
    await page.getByTestId("create-team-name-input").fill("Backend Services");
    await expect(page.getByTestId("create-team-prefix-input")).toHaveValue("BS");
    await page.getByTestId("create-team-submit").click();
    await page.waitForTimeout(400);
    expect(createCalled).toBe(true);
    await expect(page.getByTestId("create-team-modal")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Access Requests (owner view)
// ══════════════════════════════════════════════════════════════════════════

test.describe("Team Management — Access Requests (owner)", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page, {
      accessRequests: [buildAccessRequest()], // Bob wants to join
    });
    await gotoTeamManagement(page);
  });

  test("[SMOKE] access-requests-section renders for owner", async ({ page }) => {
    await expect(page.getByTestId("access-requests-section")).toBeVisible();
  });

  test("[SMOKE] pending access request row renders", async ({ page }) => {
    await expect(page.getByTestId(`access-request-${ACCESS_REQUEST_ID}`)).toBeVisible();
    // Should show the requester user ID
    await expect(page.getByTestId(`access-request-${ACCESS_REQUEST_ID}`)).toContainText(BOB_USER_ID);
  });

  test("[SMOKE] Approve and Deny buttons render for pending request", async ({ page }) => {
    await expect(page.getByTestId(`approve-request-${ACCESS_REQUEST_ID}`)).toBeVisible();
    await expect(page.getByTestId(`deny-request-${ACCESS_REQUEST_ID}`)).toBeVisible();
  });

  test("clicking Approve calls PATCH access-request API", async ({ page }) => {
    let approveCalled = false;
    await page.route(new RegExp(`/api/v1/teams/${TEAM_1_ID}/access-requests/${ACCESS_REQUEST_ID}$`), async (route) => {
      if (route.request().method() === "PATCH") {
        approveCalled = true;
        return json(route, 200, {});
      }
      return route.fallback();
    });
    await page.getByTestId(`approve-request-${ACCESS_REQUEST_ID}`).click();
    await page.waitForTimeout(300);
    expect(approveCalled).toBe(true);
  });

  test("clicking Deny calls PATCH access-request API", async ({ page }) => {
    let denyCalled = false;
    await page.route(new RegExp(`/api/v1/teams/${TEAM_1_ID}/access-requests/${ACCESS_REQUEST_ID}$`), async (route) => {
      if (route.request().method() === "PATCH") {
        denyCalled = true;
        const body = (route.request().postDataJSON() ?? {}) as Record<string, unknown>;
        expect(body.status).toBe("DENIED");
        return json(route, 200, {});
      }
      return route.fallback();
    });
    await page.getByTestId(`deny-request-${ACCESS_REQUEST_ID}`).click();
    await page.waitForTimeout(300);
    expect(denyCalled).toBe(true);
  });

  test("no pending requests shows empty state", async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page, { accessRequests: [] });
    await gotoTeamManagement(page);
    await expect(page.getByTestId("no-access-requests")).toBeVisible();
    await expect(page.getByTestId("no-access-requests")).toContainText("No pending access requests");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Request Access (non-member view)
// ══════════════════════════════════════════════════════════════════════════

test.describe("Team Management — Request Access (non-member)", () => {
  test.beforeEach(async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page, { carolIsNonMember: true });
    await gotoTeamManagement(page);
  });

  test("[SMOKE] request-access-section renders for non-member", async ({ page }) => {
    await expect(page.getByTestId("request-access-section")).toBeVisible();
  });

  test("[SMOKE] Request Access button is visible", async ({ page }) => {
    await expect(page.getByTestId("request-access-btn")).toBeVisible();
  });

  test("clicking Request Access shows confirmation message", async ({ page }) => {
    await page.getByTestId("request-access-btn").click();
    await page.waitForTimeout(300);
    await expect(page.getByTestId("access-request-sent")).toBeVisible();
    await expect(page.getByTestId("access-request-sent")).toContainText("Access request sent");
  });

  test("members-section is NOT visible for non-member", async ({ page }) => {
    await expect(page.getByTestId("members-section")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Team Selector (multiple teams)
// ══════════════════════════════════════════════════════════════════════════

test.describe("Team Management — Team Selector", () => {
  test("team selector renders when multiple teams exist", async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page, {
      teams: [
        buildTeam({ id: TEAM_1_ID, name: "Engineering", ownerUserId: CAROL_USER_ID }),
        buildTeam({ id: TEAM_2_ID, name: "Product", keyPrefix: "PRD", ownerUserId: CAROL_USER_ID }),
      ],
    });
    await gotoTeamManagement(page);
    const select = page.getByTestId("team-mgmt-team-select");
    await expect(select).toBeVisible();
    await expect(select.getByRole("option", { name: "Engineering" })).toBeAttached();
    await expect(select.getByRole("option", { name: "Product" })).toBeAttached();
  });

  test("team selector is hidden when only one team", async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page, { teams: [buildTeam()] });
    await gotoTeamManagement(page);
    await expect(page.getByTestId("team-mgmt-team-select")).not.toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// Empty & Error States
// ══════════════════════════════════════════════════════════════════════════

test.describe("Team Management — Empty & Error States", () => {
  test("empty state for manager shows 'No teams yet. Create one'", async ({ page }) => {
    await installMockApi(page);
    await installTeamMgmtMocks(page, { teams: [] });
    await gotoTeamManagement(page);
    await expect(page.getByTestId("team-mgmt-empty")).toBeVisible();
    await expect(page.getByTestId("team-mgmt-empty")).toContainText("Create one to get started");
  });

  test("error banner shown on teams API failure", async ({ page }) => {
    await installMockApi(page);
    // Override teams to fail AFTER installMockApi (LIFO)
    await page.route(/\/api\/v1\/teams$/, async (route) => {
      if (route.request().method() === "GET") {
        return json(route, 500, apiError("Internal server error", "INTERNAL_ERROR"));
      }
      return route.fallback();
    });
    await gotoTeamManagement(page);
    await expect(page.getByTestId("error-banner")).toBeVisible();
    await expect(page.getByTestId("error-banner")).toContainText("Internal server error");
  });
});
