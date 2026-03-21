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
  OrgPolicy,
  UpdateDigestConfigRequest,
  AdoptionMetrics,
  WeeklyAdoptionPoint,
  AiUsageMetrics,
  RcdoHealthReport,
  OutcomeHealthItem,
} from "../api.js";
import type { NextWorkSuggestionsResponse, SuggestionFeedbackRequest } from "../types.js";
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

describe("NextWorkSuggestionsResponse", () => {
  it("supports the roadmap alias for next-work suggestions", () => {
    const resp: NextWorkSuggestionsResponse = {
      status: "ok",
      suggestions: [
        {
          suggestionId: "suggestion-1",
          title: "Follow up on activation fixes",
          suggestedOutcomeId: "outcome-1",
          suggestedChessPriority: ChessPriority.QUEEN,
          confidence: 0.82,
          source: "CARRY_FORWARD",
          sourceDetail: "Carried from the previous week",
          rationale: "This was not completed last week and remains strategically important.",
        },
      ],
    };
    const feedback: SuggestionFeedbackRequest = {
      suggestionId: resp.suggestions[0].suggestionId,
      action: "ACCEPT",
      sourceType: resp.suggestions[0].source,
      sourceDetail: resp.suggestions[0].sourceDetail,
    };

    expect(resp.suggestions[0].suggestedChessPriority).toBe("QUEEN");
    expect(feedback.action).toBe("ACCEPT");
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

describe("OrgPolicy", () => {
  it("contains all required fields including digest schedule", () => {
    const policy: OrgPolicy = {
      chessKingRequired: true,
      chessMaxKing: 1,
      chessMaxQueen: 2,
      lockDay: "MONDAY",
      lockTime: "10:00",
      reconcileDay: "FRIDAY",
      reconcileTime: "16:00",
      blockLockOnStaleRcdo: true,
      rcdoStalenessThresholdMinutes: 60,
      digestDay: "FRIDAY",
      digestTime: "17:00",
    };

    expect(policy.digestDay).toBe("FRIDAY");
    expect(policy.digestTime).toBe("17:00");
    expect(policy.chessMaxKing).toBe(1);
    expect(policy.chessMaxQueen).toBe(2);
  });

  it("supports Monday digest schedule for start-of-week summaries", () => {
    const policy: OrgPolicy = {
      chessKingRequired: true,
      chessMaxKing: 1,
      chessMaxQueen: 2,
      lockDay: "MONDAY",
      lockTime: "10:00",
      reconcileDay: "FRIDAY",
      reconcileTime: "16:00",
      blockLockOnStaleRcdo: true,
      rcdoStalenessThresholdMinutes: 60,
      digestDay: "MONDAY",
      digestTime: "08:00",
    };

    expect(policy.digestDay).toBe("MONDAY");
    expect(policy.digestTime).toBe("08:00");
  });
});

describe("UpdateDigestConfigRequest", () => {
  it("requires digestDay and digestTime", () => {
    const req: UpdateDigestConfigRequest = {
      digestDay: "FRIDAY",
      digestTime: "17:00",
    };

    expect(req.digestDay).toBe("FRIDAY");
    expect(req.digestTime).toBe("17:00");
  });

  it("supports any valid day of week", () => {
    const days = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
    for (const day of days) {
      const req: UpdateDigestConfigRequest = { digestDay: day, digestTime: "09:00" };
      expect(req.digestDay).toBe(day);
    }
  });
});

describe("AdoptionMetrics", () => {
  it("contains rolling-window metadata and weekly points", () => {
    const point: WeeklyAdoptionPoint = {
      weekStart: "2026-03-09",
      activeUsers: 10,
      plansCreated: 10,
      plansLocked: 8,
      plansReconciled: 6,
      plansReviewed: 5,
    };

    const metrics: AdoptionMetrics = {
      weeks: 8,
      windowStart: "2026-01-26",
      windowEnd: "2026-03-16",
      totalActiveUsers: 15,
      cadenceComplianceRate: 0.85,
      weeklyPoints: [point],
    };

    expect(metrics.weeks).toBe(8);
    expect(metrics.cadenceComplianceRate).toBeGreaterThan(0);
    expect(metrics.weeklyPoints).toHaveLength(1);
    expect(metrics.weeklyPoints[0].plansLocked).toBe(8);
  });
});

describe("AiUsageMetrics", () => {
  it("contains feedback counts and cache statistics", () => {
    const metrics: AiUsageMetrics = {
      weeks: 8,
      windowStart: "2026-01-26",
      windowEnd: "2026-03-16",
      totalFeedbackCount: 100,
      acceptedCount: 60,
      deferredCount: 25,
      declinedCount: 15,
      acceptanceRate: 0.6,
      cacheHits: 500,
      cacheMisses: 200,
      cacheHitRate: 0.714,
      approximateTokensSpent: 200000,
      approximateTokensSaved: 500000,
    };

    expect(metrics.acceptanceRate).toBe(0.6);
    expect(metrics.cacheHitRate).toBeGreaterThan(0);
    expect(metrics.approximateTokensSaved).toBeGreaterThan(metrics.approximateTokensSpent);
  });
});

describe("RcdoHealthReport", () => {
  it("contains covered and stale outcomes", () => {
    const item: OutcomeHealthItem = {
      outcomeId: "o1",
      outcomeName: "Increase Revenue",
      objectiveId: "obj1",
      objectiveName: "Grow Market",
      rallyCryId: "rc1",
      rallyCryName: "Dominate Q1",
      commitCount: 12,
    };

    const report: RcdoHealthReport = {
      generatedAt: "2026-03-19T12:00:00Z",
      windowWeeks: 8,
      totalOutcomes: 10,
      coveredOutcomes: 7,
      topOutcomes: [item],
      staleOutcomes: [],
    };

    expect(report.totalOutcomes).toBe(10);
    expect(report.coveredOutcomes).toBe(7);
    expect(report.topOutcomes).toHaveLength(1);
    expect(report.topOutcomes[0].commitCount).toBe(12);
    expect(report.staleOutcomes).toHaveLength(0);
  });

  it("supports stale outcomes with zero commits", () => {
    const staleItem: OutcomeHealthItem = {
      outcomeId: "o2",
      outcomeName: "Reduce Churn",
      objectiveId: "obj2",
      objectiveName: "Retain Customers",
      rallyCryId: "rc1",
      rallyCryName: "Dominate Q1",
      commitCount: 0,
    };

    const report: RcdoHealthReport = {
      generatedAt: "2026-03-19T12:00:00Z",
      windowWeeks: 8,
      totalOutcomes: 5,
      coveredOutcomes: 3,
      topOutcomes: [],
      staleOutcomes: [staleItem],
    };

    expect(report.staleOutcomes[0].commitCount).toBe(0);
  });
});
