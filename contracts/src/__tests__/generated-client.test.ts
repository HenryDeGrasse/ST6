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
] as const satisfies readonly (keyof WeeklyCommitmentsApiPaths)[];

describe("generated OpenAPI client", () => {
  it("covers every v1 path in the committed OpenAPI spec", () => {
    expect(API_PATHS).toHaveLength(55);
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

  it("exports generated schema and operation maps for consumers", () => {
    const weeklyPlanHasId: Assert<HasKey<WeeklyCommitmentsApiComponents["schemas"]["WeeklyPlan"], "id">> = true;
    const weeklyPlanHasReviewStatus: Assert<
      HasKey<WeeklyCommitmentsApiComponents["schemas"]["WeeklyPlan"], "reviewStatus">
    > = true;
    const suggestionResponseHasSuggestions: Assert<
      HasKey<WeeklyCommitmentsApiComponents["schemas"]["SuggestRcdoResponse"], "suggestions">
    > = true;
    const lockPlanHasResponses: Assert<HasKey<WeeklyCommitmentsApiOperations["lockPlan"], "responses">> = true;

    expect(weeklyPlanHasId).toBe(true);
    expect(weeklyPlanHasReviewStatus).toBe(true);
    expect(suggestionResponseHasSuggestions).toBe(true);
    expect(lockPlanHasResponses).toBe(true);

    expectTypeOf<WeeklyCommitmentsApiOperations["lockPlan"]>().toMatchTypeOf<object>();
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
});
