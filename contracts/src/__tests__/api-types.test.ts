import { describe, it, expect } from "vitest";
import type {
  ApiErrorResponse,
  CarryForwardRequest,
  CreateCommitRequest,
  UpdateCommitRequest,
  UpdateActualRequest,
  TeamSummaryResponse,
  CreateReviewRequest,
  SuggestRcdoResponse,
  DraftReconciliationResponse,
  PaginatedResponse,
} from "../api.js";
import { ErrorCode } from "../errors.js";
import { ChessPriority, CompletionStatus, PlanState, ReviewStatus, CommitCategory } from "../enums.js";

/**
 * These tests verify the structural correctness of API contract types.
 * They exercise type-level assertions at runtime to ensure the shapes
 * are compatible with what the backend produces.
 */

describe("ApiErrorResponse structure", () => {
  it("conforms to the standard error envelope", () => {
    const response: ApiErrorResponse = {
      error: {
        code: ErrorCode.MISSING_CHESS_PRIORITY,
        message: "Commits missing chess priority",
        details: [{ commitIds: ["abc-123"] }],
      },
    };
    expect(response.error.code).toBe("MISSING_CHESS_PRIORITY");
    expect(response.error.details).toHaveLength(1);
  });
});

describe("CarryForwardRequest", () => {
  it("requires commitIds array", () => {
    const req: CarryForwardRequest = { commitIds: ["id1", "id2"] };
    expect(req.commitIds).toHaveLength(2);
  });
});

describe("CreateCommitRequest", () => {
  it("requires only title; all other fields optional", () => {
    const minimal: CreateCommitRequest = { title: "My commitment" };
    expect(minimal.title).toBe("My commitment");
    expect(minimal.chessPriority).toBeUndefined();
    expect(minimal.outcomeId).toBeUndefined();
  });

  it("allows all optional fields", () => {
    const full: CreateCommitRequest = {
      title: "Ship feature X",
      description: "Build and deploy",
      chessPriority: ChessPriority.KING,
      category: CommitCategory.DELIVERY,
      outcomeId: "uuid-123",
      nonStrategicReason: null,
      expectedResult: "Feature live",
      confidence: 0.85,
      tags: ["frontend", "launch"],
    };
    expect(full.chessPriority).toBe("KING");
    expect(full.tags).toHaveLength(2);
  });
});

describe("UpdateCommitRequest", () => {
  it("allows partial updates", () => {
    const patch: UpdateCommitRequest = { progressNotes: "50% done" };
    expect(patch.progressNotes).toBe("50% done");
    expect(patch.title).toBeUndefined();
  });
});

describe("UpdateActualRequest", () => {
  it("requires actualResult and completionStatus", () => {
    const req: UpdateActualRequest = {
      actualResult: "Done with minor adjustments",
      completionStatus: CompletionStatus.PARTIALLY,
      deltaReason: "Scope change mid-week",
    };
    expect(req.completionStatus).toBe("PARTIALLY");
    expect(req.deltaReason).toBe("Scope change mid-week");
  });
});

describe("TeamSummaryResponse", () => {
  it("includes users array and review status counts", () => {
    const summary: TeamSummaryResponse = {
      weekStart: "2026-03-09",
      users: [
        {
          userId: "user-1",
          displayName: "Alice Smith",
          planId: "plan-1",
          state: PlanState.LOCKED,
          reviewStatus: ReviewStatus.REVIEW_NOT_APPLICABLE,
          commitCount: 5,
          incompleteCount: 0,
          issueCount: 0,
          nonStrategicCount: 1,
          kingCount: 1,
          queenCount: 2,
          lastUpdated: "2026-03-09T10:00:00Z",
          isStale: false,
          isLateLock: false,
        },
      ],
      reviewStatusCounts: { pending: 3, approved: 1, changesRequested: 0 },
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    expect(summary.users).toHaveLength(1);
    expect(summary.reviewStatusCounts.pending).toBe(3);
  });
});

describe("CreateReviewRequest", () => {
  it("requires decision and comments", () => {
    const req: CreateReviewRequest = {
      decision: "APPROVED",
      comments: "Looks good!",
    };
    expect(req.decision).toBe("APPROVED");
  });
});

describe("SuggestRcdoResponse", () => {
  it("supports ok status with suggestions", () => {
    const resp: SuggestRcdoResponse = {
      status: "ok",
      suggestions: [
        {
          outcomeId: "outcome-1",
          rallyCryName: "Growth",
          objectiveName: "Expand market",
          outcomeName: "10k new users",
          confidence: 0.87,
          rationale: "Title mentions user growth",
        },
      ],
    };
    expect(resp.status).toBe("ok");
    expect(resp.suggestions).toHaveLength(1);
    expect(resp.suggestions[0].confidence).toBeGreaterThan(0);
  });

  it("supports unavailable status with empty suggestions", () => {
    const resp: SuggestRcdoResponse = {
      status: "unavailable",
      suggestions: [],
    };
    expect(resp.status).toBe("unavailable");
    expect(resp.suggestions).toHaveLength(0);
  });
});

describe("DraftReconciliationResponse", () => {
  it("can contain draft items", () => {
    const resp: DraftReconciliationResponse = {
      status: "ok",
      drafts: [
        {
          commitId: "c1",
          suggestedStatus: CompletionStatus.DONE,
          suggestedDeltaReason: null,
          suggestedActualResult: "Shipped successfully",
        },
      ],
    };
    expect(resp.drafts[0].suggestedStatus).toBe("DONE");
  });
});

describe("PaginatedResponse", () => {
  it("wraps any content type", () => {
    const page: PaginatedResponse<string> = {
      content: ["a", "b"],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    };
    expect(page.content).toHaveLength(2);
  });
});
