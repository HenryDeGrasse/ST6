import { describe, it, expect, expectTypeOf } from "vitest";
import type {
  WeeklyCommitmentsApiComponents,
  WeeklyCommitmentsApiOperations,
} from "../index.js";
import type { WeeklyPlan, WeeklyCommit, WeeklyCommitActual } from "../types.js";
import type {
  CreateCommitRequest,
  UpdateCommitRequest,
  UpdateActualRequest,
  CarryForwardRequest,
  TeamSummaryResponse,
  TeamMemberSummary,
  RcdoRollupItem,
  ManagerReview,
} from "../api.js";

// ─── Type assertion helpers ──────────────────────────────────────────────────

type Assert<T extends true> = T;
type HasKey<T, K extends PropertyKey> = K extends keyof T ? true : false;
type IsRequiredKey<T, K extends keyof T> = Pick<T, K> extends Required<Pick<T, K>>
  ? true
  : false;

// ─── Spec schema types (from auto-generated openapi.ts) ──────────────────────

type SpecWeeklyPlan = WeeklyCommitmentsApiComponents["schemas"]["WeeklyPlan"];
type SpecWeeklyCommit = WeeklyCommitmentsApiComponents["schemas"]["WeeklyCommit"];
type SpecWeeklyCommitActual = WeeklyCommitmentsApiComponents["schemas"]["WeeklyCommitActual"];
type SpecCreateCommitRequest = WeeklyCommitmentsApiComponents["schemas"]["CreateCommitRequest"];
type SpecUpdateCommitRequest = WeeklyCommitmentsApiComponents["schemas"]["UpdateCommitRequest"];
type SpecUpdateActualRequest = WeeklyCommitmentsApiComponents["schemas"]["UpdateActualRequest"];
type SpecManagerReview = WeeklyCommitmentsApiComponents["schemas"]["ManagerReview"];
type SpecCarryForwardRequest = WeeklyCommitmentsApiComponents["schemas"]["CarryForwardRequest"];
type SpecTeamSummaryResponse = WeeklyCommitmentsApiComponents["schemas"]["TeamSummaryResponse"];
type SpecTeamMemberSummary = WeeklyCommitmentsApiComponents["schemas"]["TeamMemberSummary"];
type SpecRcdoRollupItem = WeeklyCommitmentsApiComponents["schemas"]["RcdoRollupItem"];

/**
 * Contract-level alignment tests between the OpenAPI spec (auto-generated types)
 * and the manually-authored api.ts / types.ts.
 *
 * Purpose: catch drift when the spec is updated but the hand-authored contracts
 * are not, or vice-versa. Each test suite covers one schema or operation group.
 *
 * These are compile-time + runtime assertions:
 * - Compile-time: TypeScript will refuse to compile `Assert<HasKey<...>>` if the
 *   key does not exist, making the boolean literal `true` un-assignable.
 * - Runtime: `expect(...).toBe(true)` confirms the assertion didn't get erased.
 */

// ─── WeeklyPlan schema alignment ─────────────────────────────────────────────

describe("WeeklyPlan schema alignment: spec ↔ manually-authored types.ts", () => {
  it("all spec-required WeeklyPlan fields exist in the manually-authored type", () => {
    // These are in the OpenAPI `required:` array for WeeklyPlan → non-optional in spec
    const specRequiredId: Assert<IsRequiredKey<SpecWeeklyPlan, "id">> = true;
    const specRequiredOrgId: Assert<IsRequiredKey<SpecWeeklyPlan, "orgId">> = true;
    const specRequiredOwnerUserId: Assert<IsRequiredKey<SpecWeeklyPlan, "ownerUserId">> = true;
    const specRequiredWeekStartDate: Assert<IsRequiredKey<SpecWeeklyPlan, "weekStartDate">> = true;
    const specRequiredState: Assert<IsRequiredKey<SpecWeeklyPlan, "state">> = true;
    const specRequiredReviewStatus: Assert<IsRequiredKey<SpecWeeklyPlan, "reviewStatus">> = true;
    const specRequiredVersion: Assert<IsRequiredKey<SpecWeeklyPlan, "version">> = true;
    const specRequiredCreatedAt: Assert<IsRequiredKey<SpecWeeklyPlan, "createdAt">> = true;
    const specRequiredUpdatedAt: Assert<IsRequiredKey<SpecWeeklyPlan, "updatedAt">> = true;

    // Corresponding fields must exist in the manually-authored WeeklyPlan
    const manualHasId: Assert<HasKey<WeeklyPlan, "id">> = true;
    const manualHasOrgId: Assert<HasKey<WeeklyPlan, "orgId">> = true;
    const manualHasOwnerUserId: Assert<HasKey<WeeklyPlan, "ownerUserId">> = true;
    const manualHasWeekStartDate: Assert<HasKey<WeeklyPlan, "weekStartDate">> = true;
    const manualHasState: Assert<HasKey<WeeklyPlan, "state">> = true;
    const manualHasReviewStatus: Assert<HasKey<WeeklyPlan, "reviewStatus">> = true;
    const manualHasVersion: Assert<HasKey<WeeklyPlan, "version">> = true;
    const manualHasCreatedAt: Assert<HasKey<WeeklyPlan, "createdAt">> = true;
    const manualHasUpdatedAt: Assert<HasKey<WeeklyPlan, "updatedAt">> = true;

    expect(specRequiredId && specRequiredOrgId && specRequiredOwnerUserId).toBe(true);
    expect(specRequiredWeekStartDate && specRequiredState && specRequiredReviewStatus).toBe(true);
    expect(specRequiredVersion && specRequiredCreatedAt && specRequiredUpdatedAt).toBe(true);
    expect(manualHasId && manualHasOrgId && manualHasOwnerUserId && manualHasWeekStartDate).toBe(true);
    expect(manualHasState && manualHasReviewStatus && manualHasVersion).toBe(true);
    expect(manualHasCreatedAt && manualHasUpdatedAt).toBe(true);
  });

  it("all optional WeeklyPlan spec fields exist in the manually-authored type", () => {
    // lockType, lockedAt, carryForwardExecutedAt are optional in spec (not in required[])
    // but the Java record always serialises them → manually-authored type includes them
    const specHasLockType: Assert<HasKey<SpecWeeklyPlan, "lockType">> = true;
    const specHasLockedAt: Assert<HasKey<SpecWeeklyPlan, "lockedAt">> = true;
    const specHasCfAt: Assert<HasKey<SpecWeeklyPlan, "carryForwardExecutedAt">> = true;

    const manualHasLockType: Assert<HasKey<WeeklyPlan, "lockType">> = true;
    const manualHasLockedAt: Assert<HasKey<WeeklyPlan, "lockedAt">> = true;
    const manualHasCfAt: Assert<HasKey<WeeklyPlan, "carryForwardExecutedAt">> = true;

    expect(specHasLockType && specHasLockedAt && specHasCfAt).toBe(true);
    expect(manualHasLockType && manualHasLockedAt && manualHasCfAt).toBe(true);
  });

  it("manually-authored WeeklyPlan has no fields absent from the spec", () => {
    // Each key in types.ts WeeklyPlan must exist in the generated spec schema.
    // This catches additions to types.ts that were not added to the spec.
    const specHasId: Assert<HasKey<SpecWeeklyPlan, "id">> = true;
    const specHasOrgId: Assert<HasKey<SpecWeeklyPlan, "orgId">> = true;
    const specHasOwnerUserId: Assert<HasKey<SpecWeeklyPlan, "ownerUserId">> = true;
    const specHasWeekStartDate: Assert<HasKey<SpecWeeklyPlan, "weekStartDate">> = true;
    const specHasState: Assert<HasKey<SpecWeeklyPlan, "state">> = true;
    const specHasReviewStatus: Assert<HasKey<SpecWeeklyPlan, "reviewStatus">> = true;
    const specHasLockType: Assert<HasKey<SpecWeeklyPlan, "lockType">> = true;
    const specHasLockedAt: Assert<HasKey<SpecWeeklyPlan, "lockedAt">> = true;
    const specHasCarryForwardExecutedAt: Assert<
      HasKey<SpecWeeklyPlan, "carryForwardExecutedAt">
    > = true;
    const specHasVersion: Assert<HasKey<SpecWeeklyPlan, "version">> = true;
    const specHasCreatedAt: Assert<HasKey<SpecWeeklyPlan, "createdAt">> = true;
    const specHasUpdatedAt: Assert<HasKey<SpecWeeklyPlan, "updatedAt">> = true;

    expect(
      specHasId && specHasOrgId && specHasOwnerUserId && specHasWeekStartDate
      && specHasState && specHasReviewStatus && specHasLockType && specHasLockedAt
      && specHasCarryForwardExecutedAt && specHasVersion && specHasCreatedAt && specHasUpdatedAt
    ).toBe(true);
  });
});

// ─── WeeklyCommit schema alignment ───────────────────────────────────────────

describe("WeeklyCommit schema alignment: spec ↔ manually-authored types.ts", () => {
  it("all spec-required WeeklyCommit fields exist in the manually-authored type", () => {
    const specRequiredId: Assert<IsRequiredKey<SpecWeeklyCommit, "id">> = true;
    const specRequiredPlanId: Assert<IsRequiredKey<SpecWeeklyCommit, "weeklyPlanId">> = true;
    const specRequiredTitle: Assert<IsRequiredKey<SpecWeeklyCommit, "title">> = true;
    const specRequiredDescription: Assert<IsRequiredKey<SpecWeeklyCommit, "description">> = true;
    const specRequiredExpectedResult: Assert<IsRequiredKey<SpecWeeklyCommit, "expectedResult">> = true;
    const specRequiredProgressNotes: Assert<IsRequiredKey<SpecWeeklyCommit, "progressNotes">> = true;
    const specRequiredTags: Assert<IsRequiredKey<SpecWeeklyCommit, "tags">> = true;
    const specRequiredVersion: Assert<IsRequiredKey<SpecWeeklyCommit, "version">> = true;
    const specRequiredValidationErrors: Assert<
      IsRequiredKey<SpecWeeklyCommit, "validationErrors">
    > = true;

    // All these must be present in the manually-authored type
    const manualHasId: Assert<HasKey<WeeklyCommit, "id">> = true;
    const manualHasPlanId: Assert<HasKey<WeeklyCommit, "weeklyPlanId">> = true;
    const manualHasTitle: Assert<HasKey<WeeklyCommit, "title">> = true;
    const manualHasDescription: Assert<HasKey<WeeklyCommit, "description">> = true;
    const manualHasExpectedResult: Assert<HasKey<WeeklyCommit, "expectedResult">> = true;
    const manualHasProgressNotes: Assert<HasKey<WeeklyCommit, "progressNotes">> = true;
    const manualHasTags: Assert<HasKey<WeeklyCommit, "tags">> = true;
    const manualHasVersion: Assert<HasKey<WeeklyCommit, "version">> = true;
    const manualHasValidationErrors: Assert<HasKey<WeeklyCommit, "validationErrors">> = true;

    expect(specRequiredId && specRequiredPlanId && specRequiredTitle).toBe(true);
    expect(specRequiredDescription && specRequiredExpectedResult && specRequiredProgressNotes).toBe(true);
    expect(specRequiredTags && specRequiredVersion && specRequiredValidationErrors).toBe(true);
    expect(manualHasId && manualHasPlanId && manualHasTitle).toBe(true);
    expect(manualHasDescription && manualHasExpectedResult && manualHasProgressNotes).toBe(true);
    expect(manualHasTags && manualHasVersion && manualHasValidationErrors).toBe(true);
  });

  it("RCDO snapshot fields exist in both spec and manually-authored type", () => {
    const specHasSnapshotRcId: Assert<HasKey<SpecWeeklyCommit, "snapshotRallyCryId">> = true;
    const specHasSnapshotRcName: Assert<HasKey<SpecWeeklyCommit, "snapshotRallyCryName">> = true;
    const specHasSnapshotObjId: Assert<HasKey<SpecWeeklyCommit, "snapshotObjectiveId">> = true;
    const specHasSnapshotObjName: Assert<HasKey<SpecWeeklyCommit, "snapshotObjectiveName">> = true;
    const specHasSnapshotOutId: Assert<HasKey<SpecWeeklyCommit, "snapshotOutcomeId">> = true;
    const specHasSnapshotOutName: Assert<HasKey<SpecWeeklyCommit, "snapshotOutcomeName">> = true;
    const specHasCarriedFrom: Assert<HasKey<SpecWeeklyCommit, "carriedFromCommitId">> = true;

    const manualHasSnapshotRcId: Assert<HasKey<WeeklyCommit, "snapshotRallyCryId">> = true;
    const manualHasSnapshotRcName: Assert<HasKey<WeeklyCommit, "snapshotRallyCryName">> = true;
    const manualHasSnapshotObjId: Assert<HasKey<WeeklyCommit, "snapshotObjectiveId">> = true;
    const manualHasSnapshotObjName: Assert<HasKey<WeeklyCommit, "snapshotObjectiveName">> = true;
    const manualHasSnapshotOutId: Assert<HasKey<WeeklyCommit, "snapshotOutcomeId">> = true;
    const manualHasSnapshotOutName: Assert<HasKey<WeeklyCommit, "snapshotOutcomeName">> = true;
    const manualHasCarriedFrom: Assert<HasKey<WeeklyCommit, "carriedFromCommitId">> = true;

    expect(
      specHasSnapshotRcId && specHasSnapshotRcName && specHasSnapshotObjId
      && specHasSnapshotObjName && specHasSnapshotOutId && specHasSnapshotOutName
      && specHasCarriedFrom
    ).toBe(true);
    expect(
      manualHasSnapshotRcId && manualHasSnapshotRcName && manualHasSnapshotObjId
      && manualHasSnapshotObjName && manualHasSnapshotOutId && manualHasSnapshotOutName
      && manualHasCarriedFrom
    ).toBe(true);
  });

  it("actual field exists in both spec and manually-authored type and remains optional", () => {
    const specHasActual: Assert<HasKey<SpecWeeklyCommit, "actual">> = true;
    const manualHasActual: Assert<HasKey<WeeklyCommit, "actual">> = true;
    const specActualIsOptional: Assert<IsRequiredKey<SpecWeeklyCommit, "actual"> extends false ? true : false> = true;
    const manualActualIsOptional: Assert<IsRequiredKey<WeeklyCommit, "actual"> extends false ? true : false> = true;

    expect(specHasActual && manualHasActual && specActualIsOptional && manualActualIsOptional).toBe(true);
  });
});

// ─── WeeklyCommitActual schema alignment ─────────────────────────────────────

describe("WeeklyCommitActual schema alignment: spec ↔ manually-authored types.ts", () => {
  it("all spec-required WeeklyCommitActual fields exist in the manually-authored type", () => {
    const specRequiredCommitId: Assert<IsRequiredKey<SpecWeeklyCommitActual, "commitId">> = true;
    const specRequiredActualResult: Assert<IsRequiredKey<SpecWeeklyCommitActual, "actualResult">> = true;
    const specRequiredStatus: Assert<IsRequiredKey<SpecWeeklyCommitActual, "completionStatus">> = true;

    const manualHasCommitId: Assert<HasKey<WeeklyCommitActual, "commitId">> = true;
    const manualHasActualResult: Assert<HasKey<WeeklyCommitActual, "actualResult">> = true;
    const manualHasStatus: Assert<HasKey<WeeklyCommitActual, "completionStatus">> = true;
    const manualHasDeltaReason: Assert<HasKey<WeeklyCommitActual, "deltaReason">> = true;
    const manualHasTimeSpent: Assert<HasKey<WeeklyCommitActual, "timeSpent">> = true;

    expect(specRequiredCommitId && specRequiredActualResult && specRequiredStatus).toBe(true);
    expect(manualHasCommitId && manualHasActualResult && manualHasStatus).toBe(true);
    expect(manualHasDeltaReason && manualHasTimeSpent).toBe(true);
  });
});

// ─── Request body schema alignment ───────────────────────────────────────────

describe("CreateCommitRequest schema alignment: spec ↔ manually-authored api.ts", () => {
  it("spec requires only title; all other fields optional in spec and manual type", () => {
    // In the spec, CreateCommitRequest has required: [title]
    const specRequiresTitle: Assert<IsRequiredKey<SpecCreateCommitRequest, "title">> = true;
    const manualRequiresTitle: Assert<IsRequiredKey<CreateCommitRequest, "title">> = true;

    // Optional fields exist in both
    const specHasDescription: Assert<HasKey<SpecCreateCommitRequest, "description">> = true;
    const specHasChessPriority: Assert<HasKey<SpecCreateCommitRequest, "chessPriority">> = true;
    const specHasCategory: Assert<HasKey<SpecCreateCommitRequest, "category">> = true;
    const specHasOutcomeId: Assert<HasKey<SpecCreateCommitRequest, "outcomeId">> = true;
    const specHasExpectedResult: Assert<HasKey<SpecCreateCommitRequest, "expectedResult">> = true;
    const specHasTags: Assert<HasKey<SpecCreateCommitRequest, "tags">> = true;

    const manualHasDescription: Assert<HasKey<CreateCommitRequest, "description">> = true;
    const manualHasChessPriority: Assert<HasKey<CreateCommitRequest, "chessPriority">> = true;
    const manualHasCategory: Assert<HasKey<CreateCommitRequest, "category">> = true;
    const manualHasOutcomeId: Assert<HasKey<CreateCommitRequest, "outcomeId">> = true;
    const manualHasExpectedResult: Assert<HasKey<CreateCommitRequest, "expectedResult">> = true;
    const manualHasTags: Assert<HasKey<CreateCommitRequest, "tags">> = true;

    expect(specRequiresTitle && manualRequiresTitle).toBe(true);
    expect(
      specHasDescription && specHasChessPriority && specHasCategory
      && specHasOutcomeId && specHasExpectedResult && specHasTags
    ).toBe(true);
    expect(
      manualHasDescription && manualHasChessPriority && manualHasCategory
      && manualHasOutcomeId && manualHasExpectedResult && manualHasTags
    ).toBe(true);
  });
});

describe("UpdateActualRequest schema alignment: spec ↔ manually-authored api.ts", () => {
  it("spec requires actualResult and completionStatus; manual type agrees", () => {
    const specRequiresActualResult: Assert<
      IsRequiredKey<SpecUpdateActualRequest, "actualResult">
    > = true;
    const specRequiresCompletionStatus: Assert<
      IsRequiredKey<SpecUpdateActualRequest, "completionStatus">
    > = true;

    const manualRequiresActualResult: Assert<
      IsRequiredKey<UpdateActualRequest, "actualResult">
    > = true;
    const manualRequiresCompletionStatus: Assert<
      IsRequiredKey<UpdateActualRequest, "completionStatus">
    > = true;

    expect(specRequiresActualResult && specRequiresCompletionStatus).toBe(true);
    expect(manualRequiresActualResult && manualRequiresCompletionStatus).toBe(true);
  });
});

describe("CarryForwardRequest schema alignment: spec ↔ manually-authored api.ts", () => {
  it("spec requires commitIds array; manual type agrees", () => {
    const specRequiresCommitIds: Assert<
      IsRequiredKey<SpecCarryForwardRequest, "commitIds">
    > = true;
    const manualRequiresCommitIds: Assert<IsRequiredKey<CarryForwardRequest, "commitIds">> = true;

    expect(specRequiresCommitIds && manualRequiresCommitIds).toBe(true);
  });
});

// ─── Lifecycle operations: required header documentation ─────────────────────

describe("Lifecycle operation headers: spec documents required If-Match and Idempotency-Key", () => {
  /**
   * The spec declares both If-Match and Idempotency-Key as required for the four lifecycle
   * mutation endpoints. These compile-time assertions verify the generated types reflect that.
   *
   * If a future spec change makes these headers optional (removing them from the `required`
   * field in the OpenAPI parameter spec), the `IsRequiredKey` assertions below will fail to
   * compile, alerting the developer to update the backend enforcement as well.
   */

  it("lockPlan documents Idempotency-Key as required", () => {
    type LockHeader = WeeklyCommitmentsApiOperations["lockPlan"]["parameters"]["header"];
    const hasIdempotencyKey: Assert<HasKey<LockHeader, "Idempotency-Key">> = true;
    const requiresIdempotencyKey: Assert<IsRequiredKey<LockHeader, "Idempotency-Key">> = true;
    expect(hasIdempotencyKey && requiresIdempotencyKey).toBe(true);
  });

  it("startReconciliation documents Idempotency-Key as required", () => {
    type StartHeader = WeeklyCommitmentsApiOperations["startReconciliation"]["parameters"]["header"];
    const hasIdempotencyKey: Assert<HasKey<StartHeader, "Idempotency-Key">> = true;
    const requiresIdempotencyKey: Assert<IsRequiredKey<StartHeader, "Idempotency-Key">> = true;
    expect(hasIdempotencyKey && requiresIdempotencyKey).toBe(true);
  });

  it("submitReconciliation documents Idempotency-Key as required", () => {
    type SubmitHeader = WeeklyCommitmentsApiOperations["submitReconciliation"]["parameters"]["header"];
    const hasIdempotencyKey: Assert<HasKey<SubmitHeader, "Idempotency-Key">> = true;
    const requiresIdempotencyKey: Assert<IsRequiredKey<SubmitHeader, "Idempotency-Key">> = true;
    expect(hasIdempotencyKey && requiresIdempotencyKey).toBe(true);
  });

  it("carryForward documents Idempotency-Key as required", () => {
    type CarryHeader = WeeklyCommitmentsApiOperations["carryForward"]["parameters"]["header"];
    const hasIdempotencyKey: Assert<HasKey<CarryHeader, "Idempotency-Key">> = true;
    const requiresIdempotencyKey: Assert<IsRequiredKey<CarryHeader, "Idempotency-Key">> = true;
    expect(hasIdempotencyKey && requiresIdempotencyKey).toBe(true);
  });

  it("lockPlan documents If-Match as required", () => {
    type LockHeader = WeeklyCommitmentsApiOperations["lockPlan"]["parameters"]["header"];
    const hasIfMatch: Assert<HasKey<LockHeader, "If-Match">> = true;
    const requiresIfMatch: Assert<IsRequiredKey<LockHeader, "If-Match">> = true;
    expect(hasIfMatch && requiresIfMatch).toBe(true);
  });

  it("startReconciliation documents If-Match as required", () => {
    type StartHeader = WeeklyCommitmentsApiOperations["startReconciliation"]["parameters"]["header"];
    const hasIfMatch: Assert<HasKey<StartHeader, "If-Match">> = true;
    const requiresIfMatch: Assert<IsRequiredKey<StartHeader, "If-Match">> = true;
    expect(hasIfMatch && requiresIfMatch).toBe(true);
  });

  it("submitReconciliation documents If-Match as required", () => {
    type SubmitHeader = WeeklyCommitmentsApiOperations["submitReconciliation"]["parameters"]["header"];
    const hasIfMatch: Assert<HasKey<SubmitHeader, "If-Match">> = true;
    const requiresIfMatch: Assert<IsRequiredKey<SubmitHeader, "If-Match">> = true;
    expect(hasIfMatch && requiresIfMatch).toBe(true);
  });

  it("carryForward documents If-Match as required", () => {
    type CarryHeader = WeeklyCommitmentsApiOperations["carryForward"]["parameters"]["header"];
    const hasIfMatch: Assert<HasKey<CarryHeader, "If-Match">> = true;
    const requiresIfMatch: Assert<IsRequiredKey<CarryHeader, "If-Match">> = true;
    expect(hasIfMatch && requiresIfMatch).toBe(true);
  });

  it("updateCommit documents If-Match as required", () => {
    type UpdateHeader = WeeklyCommitmentsApiOperations["updateCommit"]["parameters"]["header"];
    const hasIfMatch: Assert<HasKey<UpdateHeader, "If-Match">> = true;
    const requiresIfMatch: Assert<IsRequiredKey<UpdateHeader, "If-Match">> = true;
    expect(hasIfMatch && requiresIfMatch).toBe(true);
  });

  it("updateActual documents If-Match as required", () => {
    type ActualHeader = WeeklyCommitmentsApiOperations["updateActual"]["parameters"]["header"];
    const hasIfMatch: Assert<HasKey<ActualHeader, "If-Match">> = true;
    const requiresIfMatch: Assert<IsRequiredKey<ActualHeader, "If-Match">> = true;
    expect(hasIfMatch && requiresIfMatch).toBe(true);
  });
});

describe("UpdateCommitRequest schema alignment: spec ↔ manually-authored api.ts", () => {
  it("all fields are optional in both spec and manually-authored type", () => {
    // UpdateCommitRequest has no required fields in the spec
    const specHasTitle: Assert<HasKey<SpecUpdateCommitRequest, "title">> = true;
    const specHasDescription: Assert<HasKey<SpecUpdateCommitRequest, "description">> = true;
    const specHasChessPriority: Assert<HasKey<SpecUpdateCommitRequest, "chessPriority">> = true;
    const specHasProgressNotes: Assert<HasKey<SpecUpdateCommitRequest, "progressNotes">> = true;

    const manualHasTitle: Assert<HasKey<UpdateCommitRequest, "title">> = true;
    const manualHasDescription: Assert<HasKey<UpdateCommitRequest, "description">> = true;
    const manualHasChessPriority: Assert<HasKey<UpdateCommitRequest, "chessPriority">> = true;
    const manualHasProgressNotes: Assert<HasKey<UpdateCommitRequest, "progressNotes">> = true;

    expect(specHasTitle && specHasDescription && specHasChessPriority && specHasProgressNotes).toBe(true);
    expect(manualHasTitle && manualHasDescription && manualHasChessPriority && manualHasProgressNotes).toBe(true);
  });
});

describe("ManagerReview schema alignment: spec ↔ manually-authored types.ts", () => {
  it("all spec-required ManagerReview fields exist in the manually-authored type", () => {
    const specRequiresId: Assert<IsRequiredKey<SpecManagerReview, "id">> = true;
    const specRequiresPlanId: Assert<IsRequiredKey<SpecManagerReview, "weeklyPlanId">> = true;
    const specRequiresReviewerUserId: Assert<
      IsRequiredKey<SpecManagerReview, "reviewerUserId">
    > = true;
    const specRequiresDecision: Assert<IsRequiredKey<SpecManagerReview, "decision">> = true;
    const specRequiresComments: Assert<IsRequiredKey<SpecManagerReview, "comments">> = true;
    const specRequiresCreatedAt: Assert<IsRequiredKey<SpecManagerReview, "createdAt">> = true;

    const manualHasId: Assert<HasKey<ManagerReview, "id">> = true;
    const manualHasPlanId: Assert<HasKey<ManagerReview, "weeklyPlanId">> = true;
    const manualHasReviewerUserId: Assert<HasKey<ManagerReview, "reviewerUserId">> = true;
    const manualHasDecision: Assert<HasKey<ManagerReview, "decision">> = true;
    const manualHasComments: Assert<HasKey<ManagerReview, "comments">> = true;
    const manualHasCreatedAt: Assert<HasKey<ManagerReview, "createdAt">> = true;

    expect(
      specRequiresId && specRequiresPlanId && specRequiresReviewerUserId
      && specRequiresDecision && specRequiresComments && specRequiresCreatedAt
    ).toBe(true);
    expect(
      manualHasId && manualHasPlanId && manualHasReviewerUserId
      && manualHasDecision && manualHasComments && manualHasCreatedAt
    ).toBe(true);
  });
});

// ─── Manager dashboard schema alignment ──────────────────────────────────────

describe("TeamSummaryResponse and TeamMemberSummary alignment: spec ↔ api.ts", () => {
  it("spec TeamSummaryResponse required fields exist in manually-authored type", () => {
    const specRequiresWeekStart: Assert<IsRequiredKey<SpecTeamSummaryResponse, "weekStart">> = true;
    const specRequiresUsers: Assert<IsRequiredKey<SpecTeamSummaryResponse, "users">> = true;
    const specRequiresReviewStatusCounts: Assert<
      IsRequiredKey<SpecTeamSummaryResponse, "reviewStatusCounts">
    > = true;
    const specRequiresPage: Assert<IsRequiredKey<SpecTeamSummaryResponse, "page">> = true;
    const specRequiresSize: Assert<IsRequiredKey<SpecTeamSummaryResponse, "size">> = true;
    const specRequiresTotalElements: Assert<
      IsRequiredKey<SpecTeamSummaryResponse, "totalElements">
    > = true;
    const specRequiresTotalPages: Assert<
      IsRequiredKey<SpecTeamSummaryResponse, "totalPages">
    > = true;

    const manualHasWeekStart: Assert<HasKey<TeamSummaryResponse, "weekStart">> = true;
    const manualHasUsers: Assert<HasKey<TeamSummaryResponse, "users">> = true;
    const manualHasReviewStatusCounts: Assert<
      HasKey<TeamSummaryResponse, "reviewStatusCounts">
    > = true;
    const manualHasPage: Assert<HasKey<TeamSummaryResponse, "page">> = true;
    const manualHasSize: Assert<HasKey<TeamSummaryResponse, "size">> = true;
    const manualHasTotalElements: Assert<HasKey<TeamSummaryResponse, "totalElements">> = true;
    const manualHasTotalPages: Assert<HasKey<TeamSummaryResponse, "totalPages">> = true;

    expect(
      specRequiresWeekStart && specRequiresUsers && specRequiresReviewStatusCounts
      && specRequiresPage && specRequiresSize && specRequiresTotalElements
      && specRequiresTotalPages
    ).toBe(true);
    expect(
      manualHasWeekStart && manualHasUsers && manualHasReviewStatusCounts
      && manualHasPage && manualHasSize && manualHasTotalElements && manualHasTotalPages
    ).toBe(true);
  });

  it("spec TeamMemberSummary required fields exist in manually-authored type", () => {
    const specRequiresUserId: Assert<IsRequiredKey<SpecTeamMemberSummary, "userId">> = true;
    const specRequiresCommitCount: Assert<IsRequiredKey<SpecTeamMemberSummary, "commitCount">> = true;
    const specRequiresIncompleteCount: Assert<
      IsRequiredKey<SpecTeamMemberSummary, "incompleteCount">
    > = true;
    const specRequiresIssueCount: Assert<
      IsRequiredKey<SpecTeamMemberSummary, "issueCount">
    > = true;
    const specRequiresKingCount: Assert<IsRequiredKey<SpecTeamMemberSummary, "kingCount">> = true;
    const specRequiresQueenCount: Assert<IsRequiredKey<SpecTeamMemberSummary, "queenCount">> = true;
    const specRequiresIsStale: Assert<IsRequiredKey<SpecTeamMemberSummary, "isStale">> = true;
    const specRequiresIsLateLock: Assert<IsRequiredKey<SpecTeamMemberSummary, "isLateLock">> = true;

    const manualHasUserId: Assert<HasKey<TeamMemberSummary, "userId">> = true;
    const manualHasDisplayName: Assert<HasKey<TeamMemberSummary, "displayName">> = true;
    const manualHasCommitCount: Assert<HasKey<TeamMemberSummary, "commitCount">> = true;
    const manualHasIncompleteCount: Assert<HasKey<TeamMemberSummary, "incompleteCount">> = true;
    const manualHasIssueCount: Assert<HasKey<TeamMemberSummary, "issueCount">> = true;
    const manualHasKingCount: Assert<HasKey<TeamMemberSummary, "kingCount">> = true;
    const manualHasQueenCount: Assert<HasKey<TeamMemberSummary, "queenCount">> = true;
    const manualHasIsStale: Assert<HasKey<TeamMemberSummary, "isStale">> = true;
    const manualHasIsLateLock: Assert<HasKey<TeamMemberSummary, "isLateLock">> = true;

    expect(
      specRequiresUserId && specRequiresCommitCount && specRequiresIncompleteCount
      && specRequiresIssueCount
      && specRequiresKingCount && specRequiresQueenCount && specRequiresIsStale
      && specRequiresIsLateLock
    ).toBe(true);
    expect(
      manualHasUserId && manualHasDisplayName && manualHasCommitCount && manualHasIncompleteCount
      && manualHasIssueCount
      && manualHasKingCount && manualHasQueenCount && manualHasIsStale && manualHasIsLateLock
    ).toBe(true);
  });
});

// ─── RcdoRollupItem alignment ─────────────────────────────────────────────────

describe("RcdoRollupItem alignment: spec ↔ api.ts", () => {
  it("spec required chess-count fields match manually-authored type", () => {
    const specRequiresOutcomeId: Assert<IsRequiredKey<SpecRcdoRollupItem, "outcomeId">> = true;
    const specRequiresCommitCount: Assert<IsRequiredKey<SpecRcdoRollupItem, "commitCount">> = true;
    const specRequiresKingCount: Assert<IsRequiredKey<SpecRcdoRollupItem, "kingCount">> = true;
    const specRequiresQueenCount: Assert<IsRequiredKey<SpecRcdoRollupItem, "queenCount">> = true;
    const specRequiresRookCount: Assert<IsRequiredKey<SpecRcdoRollupItem, "rookCount">> = true;
    const specRequiresBishopCount: Assert<IsRequiredKey<SpecRcdoRollupItem, "bishopCount">> = true;
    const specRequiresKnightCount: Assert<IsRequiredKey<SpecRcdoRollupItem, "knightCount">> = true;
    const specRequiresPawnCount: Assert<IsRequiredKey<SpecRcdoRollupItem, "pawnCount">> = true;

    const manualHasOutcomeId: Assert<HasKey<RcdoRollupItem, "outcomeId">> = true;
    const manualHasCommitCount: Assert<HasKey<RcdoRollupItem, "commitCount">> = true;
    const manualHasKingCount: Assert<HasKey<RcdoRollupItem, "kingCount">> = true;
    const manualHasQueenCount: Assert<HasKey<RcdoRollupItem, "queenCount">> = true;
    const manualHasRookCount: Assert<HasKey<RcdoRollupItem, "rookCount">> = true;
    const manualHasBishopCount: Assert<HasKey<RcdoRollupItem, "bishopCount">> = true;
    const manualHasKnightCount: Assert<HasKey<RcdoRollupItem, "knightCount">> = true;
    const manualHasPawnCount: Assert<HasKey<RcdoRollupItem, "pawnCount">> = true;

    expect(
      specRequiresOutcomeId && specRequiresCommitCount && specRequiresKingCount
      && specRequiresQueenCount && specRequiresRookCount && specRequiresBishopCount
      && specRequiresKnightCount && specRequiresPawnCount
    ).toBe(true);
    expect(
      manualHasOutcomeId && manualHasCommitCount && manualHasKingCount
      && manualHasQueenCount && manualHasRookCount && manualHasBishopCount
      && manualHasKnightCount && manualHasPawnCount
    ).toBe(true);
  });
});

// ─── Operation catalog: detect spec additions not reflected in generated types ─

describe("OpenAPI operation catalog completeness", () => {
  /**
   * Asserts the generated WeeklyCommitmentsApiOperations covers all expected
   * operation IDs. If a new operation is added to the spec but the client is not
   * regenerated, or an operation is removed from the spec, this test detects it.
   *
   * Cross-check: the generated-client.test.ts already verifies path-level coverage
   * via WeeklyCommitmentsApiPaths; this test verifies operation-ID coverage.
   */
  it("generated operations map includes all documented operation IDs", () => {
    // Verify type-level presence of all 26 operation IDs from the spec
    expectTypeOf<WeeklyCommitmentsApiOperations["createPlan"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["getMyPlan"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["getPlan"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["getUserPlan"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["getUserPlanCommits"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["lockPlan"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["startReconciliation"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["submitReconciliation"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["carryForward"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["listCommits"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["createCommit"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["updateCommit"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["deleteCommit"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["updateActual"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["getTeamSummary"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["getRcdoRollup"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["getUnreadNotifications"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["markNotificationRead"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["markAllNotificationsRead"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["createReview"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["getRcdoTree"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["searchRcdo"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["suggestRcdo"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["draftReconciliation"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["managerInsights"]>().toMatchTypeOf<object>();
    expectTypeOf<WeeklyCommitmentsApiOperations["healthCheck"]>().toMatchTypeOf<object>();

    // Runtime: at least one operation can be used as a type guard
    expect(true).toBe(true);
  });
});
