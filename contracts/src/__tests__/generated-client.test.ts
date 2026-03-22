import { describe, expect, expectTypeOf, it } from "vitest";

import {
  createWeeklyCommitmentsClient,
  type WeeklyCommitmentsApiComponents,
  type WeeklyCommitmentsApiOperations,
  type WeeklyCommitmentsApiPaths,
} from "../index.js";

type Assert<T extends true> = T;
type HasKey<T, K extends PropertyKey> = K extends keyof T ? true : false;
type IsRequiredKey<T, K extends keyof T> = Pick<T, K> extends Required<Pick<T, K>> ? true : false;

const API_PATHS = [
  "/weeks/{weekStart}/plans",
  "/weeks/{weekStart}/plans/me",
  "/plans/{planId}",
  "/weeks/{weekStart}/plans/{userId}",
  "/weeks/{weekStart}/plans/{userId}/commits",
  "/plans/{planId}/lock",
  "/plans/{planId}/start-reconciliation",
  "/plans/{planId}/submit-reconciliation",
  "/plans/{planId}/carry-forward",
  "/plans/{planId}/commits",
  "/commits/{commitId}",
  "/commits/{commitId}/actual",
  "/commits/{commitId}/check-in",
  "/commits/{commitId}/check-ins",
  "/weeks/{weekStart}/team/summary",
  "/weeks/{weekStart}/team/rcdo-rollup",
  "/notifications/unread",
  "/notifications/{notificationId}/read",
  "/notifications/read-all",
  "/plans/{planId}/review",
  "/rcdo/tree",
  "/rcdo/search",
  "/ai/suggest-rcdo",
  "/ai/draft-reconciliation",
  "/ai/manager-insights",
  "/ai/plan-quality-check",
  "/ai/suggest-next-work",
  "/ai/suggestion-feedback",
  "/plans/draft-from-history",
  "/users/me/trends",
  "/admin/org-policy",
  "/admin/org-policy/digest",
  "/admin/adoption-metrics",
  "/admin/ai-usage",
  "/admin/rcdo-health",
  "/integrations/link-ticket",
  "/commits/{commitId}/linked-tickets",
  "/integrations/webhook/{provider}",
  "/users/me/capacity",
  "/team/capacity",
  "/users/me/estimation-coaching",
  "/health",
  "/plans/{planId}/quick-update",
  "/ai/check-in-options",
  "/users/me/profile",
  "/analytics/outcome-coverage",
  "/analytics/carry-forward-heatmap",
  "/analytics/category-shifts",
  "/analytics/estimation-accuracy",
  "/analytics/predictions/{userId}",
  "/outcomes/metadata",
  "/outcomes/{outcomeId}/metadata",
  "/outcomes/{outcomeId}/progress",
  "/outcomes/urgency-summary",
  "/team/strategic-slack",
  "/outcomes/forecasts",
  "/outcomes/{outcomeId}/forecast",
  "/ai/team-plan-suggestion",
  "/ai/team-plan-suggestion/apply",
  "/executive/strategic-health",
  "/ai/executive-briefing",
  "/teams",
  "/teams/{teamId}",
  "/teams/{teamId}/members",
  "/teams/{teamId}/members/{userId}",
  "/teams/{teamId}/access-requests",
  "/teams/{teamId}/access-requests/{requestId}",
  "/teams/{teamId}/issues",
  "/issues/{issueId}",
  "/issues/{issueId}/assign",
  "/issues/{issueId}/commit",
  "/issues/{issueId}/release",
  "/issues/{issueId}/comment",
  "/issues/{issueId}/time-entry",
  "/plans/{planId}/assignments",
  "/weeks/{weekStart}/plan/assignments",
  "/weeks/{weekStart}/plan/assignments/{assignmentId}",
  "/ai/suggest-effort-type",
  "/ai/rank-backlog",
  "/ai/recommend-weekly-issues",
  "/ai/suggest-deferrals",
  "/ai/coverage-gap-inspirations",
  "/ai/search-issues",
] as const satisfies readonly (keyof WeeklyCommitmentsApiPaths)[];

describe("generated OpenAPI client", () => {
  it("covers every v1 path in the committed OpenAPI spec", () => {
    expect(API_PATHS).toHaveLength(83);
  });

  it("creates a typed openapi-fetch client", () => {
    const client = createWeeklyCommitmentsClient({
      baseUrl: "/api/v1",
      headers: {
        Authorization: "Bearer test-token",
      },
    });

    expect(client).toHaveProperty("GET");
    expect(client).toHaveProperty("POST");
    expect(client).toHaveProperty("PATCH");
    expect(client).toHaveProperty("DELETE");
  });

  it("exports generated schema and operation maps for legacy and Phase 6 consumers", () => {
    const weeklyPlanHasId: Assert<HasKey<WeeklyCommitmentsApiComponents["schemas"]["WeeklyPlan"], "id">> = true;
    const issueHasIssueKey: Assert<HasKey<WeeklyCommitmentsApiComponents["schemas"]["Issue"], "issueKey">> = true;
    const createWeeklyAssignmentHasIssueId: Assert<
      HasKey<WeeklyCommitmentsApiComponents["schemas"]["CreateWeeklyAssignmentRequest"], "issueId">
    > = true;
    const issueActivityTypeSchemaExists: Assert<
      HasKey<WeeklyCommitmentsApiComponents["schemas"], "IssueActivityType">
    > = true;
    const createIssueHasResponses: Assert<HasKey<WeeklyCommitmentsApiOperations["createIssue"], "responses">> = true;
    const listTeamIssuesHasParameters: Assert<
      HasKey<WeeklyCommitmentsApiOperations["listTeamIssues"], "parameters">
    > = true;
    const semanticSearchIssuesHasResponses: Assert<
      HasKey<WeeklyCommitmentsApiOperations["semanticSearchIssues"], "responses">
    > = true;

    expect(weeklyPlanHasId).toBe(true);
    expect(issueHasIssueKey).toBe(true);
    expect(createWeeklyAssignmentHasIssueId).toBe(true);
    expect(issueActivityTypeSchemaExists).toBe(true);
    expect(createIssueHasResponses).toBe(true);
    expect(listTeamIssuesHasParameters).toBe(true);
    expect(semanticSearchIssuesHasResponses).toBe(true);

    expectTypeOf<WeeklyCommitmentsApiComponents["schemas"]["IssueActivityType"]>().toEqualTypeOf<
      | "CREATED"
      | "STATUS_CHANGE"
      | "ASSIGNMENT_CHANGE"
      | "PRIORITY_CHANGE"
      | "EFFORT_TYPE_CHANGE"
      | "ESTIMATE_CHANGE"
      | "COMMENT"
      | "TIME_ENTRY"
      | "OUTCOME_CHANGE"
      | "COMMITTED_TO_WEEK"
      | "RELEASED_TO_BACKLOG"
      | "CARRIED_FORWARD"
      | "BLOCKED"
      | "UNBLOCKED"
      | "DESCRIPTION_CHANGE"
      | "TITLE_CHANGE"
    >();
    expectTypeOf<WeeklyCommitmentsApiOperations["createIssue"]>().toMatchTypeOf<object>();
  });

  it("requires If-Match header on lock, start-reconciliation, submit-reconciliation, and carry-forward", () => {
    type LockHeader = WeeklyCommitmentsApiOperations["lockPlan"]["parameters"]["header"];
    type StartHeader = WeeklyCommitmentsApiOperations["startReconciliation"]["parameters"]["header"];
    type SubmitHeader = WeeklyCommitmentsApiOperations["submitReconciliation"]["parameters"]["header"];
    type CarryHeader = WeeklyCommitmentsApiOperations["carryForward"]["parameters"]["header"];

    const lockHasIfMatch: Assert<HasKey<LockHeader, "If-Match">> = true;
    const startHasIfMatch: Assert<HasKey<StartHeader, "If-Match">> = true;
    const submitHasIfMatch: Assert<HasKey<SubmitHeader, "If-Match">> = true;
    const carryHasIfMatch: Assert<HasKey<CarryHeader, "If-Match">> = true;

    const lockRequiresIfMatch: Assert<IsRequiredKey<LockHeader, "If-Match">> = true;
    const startRequiresIfMatch: Assert<IsRequiredKey<StartHeader, "If-Match">> = true;
    const submitRequiresIfMatch: Assert<IsRequiredKey<SubmitHeader, "If-Match">> = true;
    const carryRequiresIfMatch: Assert<IsRequiredKey<CarryHeader, "If-Match">> = true;

    expect(lockHasIfMatch).toBe(true);
    expect(startHasIfMatch).toBe(true);
    expect(submitHasIfMatch).toBe(true);
    expect(carryHasIfMatch).toBe(true);
    expect(lockRequiresIfMatch).toBe(true);
    expect(startRequiresIfMatch).toBe(true);
    expect(submitRequiresIfMatch).toBe(true);
    expect(carryRequiresIfMatch).toBe(true);
  });

  it("models new team backlog and weekly assignment path parameters correctly", () => {
    type TeamIssuePathParams = WeeklyCommitmentsApiPaths["/teams/{teamId}/issues"]["get"]["parameters"]["path"];
    type WeekAssignmentPathParams = WeeklyCommitmentsApiPaths["/weeks/{weekStart}/plan/assignments"]["post"]["parameters"]["path"];
    type DeleteAssignmentPathParams =
      WeeklyCommitmentsApiPaths["/weeks/{weekStart}/plan/assignments/{assignmentId}"]["delete"]["parameters"]["path"];

    const teamIssuesHasTeamId: Assert<HasKey<TeamIssuePathParams, "teamId">> = true;
    const createAssignmentHasWeekStart: Assert<HasKey<WeekAssignmentPathParams, "weekStart">> = true;
    const removeAssignmentHasAssignmentId: Assert<HasKey<DeleteAssignmentPathParams, "assignmentId">> = true;

    expect(teamIssuesHasTeamId).toBe(true);
    expect(createAssignmentHasWeekStart).toBe(true);
    expect(removeAssignmentHasAssignmentId).toBe(true);
  });
});
