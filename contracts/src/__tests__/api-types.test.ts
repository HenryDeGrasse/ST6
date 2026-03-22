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
  QuickUpdateRequest,
  QuickUpdateResponse,
  CheckInOptionsResponse,
  UserProfileResponse,
  OutcomeMetadataRequest,
  OutcomeMetadataResponse,
  UrgencySummaryResponse,
  StrategicSlackResponse,
  PaginatedResponse,
  OrgPolicy,
  UpdateDigestConfigRequest,
  AdoptionMetrics,
  WeeklyAdoptionPoint,
  AiUsageMetrics,
  RcdoHealthReport,
  OutcomeHealthItem,
  NotificationItem,
  NotificationTypeValue,
  WeeklyPlanDraftReadyNotificationPayload,
  PlanMisalignmentBriefingNotificationPayload,
  OutcomeForecastListResponse,
  TeamPlanSuggestionResponse,
  ApplyTeamPlanSuggestionResponse,
  ExecutiveDashboardResponse,
  ExecutiveBriefingResponse,
} from "../api.js";
import type { NextWorkSuggestionsResponse, SuggestionFeedbackRequest, TrendsResponse } from "../types.js";
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
      actualHours: 6.5,
    };
    expect(req.completionStatus).toBe("PARTIALLY");
    expect(req.deltaReason).toBe("Scope change mid-week");
    expect(req.actualHours).toBe(6.5);
  });
});

describe("Quick update contracts", () => {
  it("supports batch commit updates and typed responses", () => {
    const req: QuickUpdateRequest = {
      updates: [{
        commitId: "commit-1",
        status: "ON_TRACK",
        note: "Waiting on review",
        noteSource: "USER_TYPED",
        selectedSuggestionText: null,
        selectedSuggestionSource: null,
      }],
    };
    const resp: QuickUpdateResponse = {
      updatedCount: 1,
      entries: [{ id: "entry-1", commitId: "commit-1", status: "ON_TRACK", note: "Waiting on review", createdAt: "2026-03-21T10:00:00Z" }],
    };

    expect(req.updates[0].status).toBe("ON_TRACK");
    expect(req.updates[0].noteSource).toBe("USER_TYPED");
    expect(resp.updatedCount).toBe(1);
  });

  it("supports AI check-in option responses", () => {
    const resp: CheckInOptionsResponse = {
      status: "ok",
      statusOptions: ["ON_TRACK", "AT_RISK", "BLOCKED", "DONE_EARLY"],
      progressOptions: [{ text: "Blocked on dependency", source: "ai_generated" }],
    };

    expect(resp.statusOptions).toContain("BLOCKED");
    expect(resp.progressOptions[0].source).toBe("ai_generated");
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

describe("Phase 5 agent notification payload helpers", () => {
  it("supports enum-backed notification items with open-ended payload objects", () => {
    const type: NotificationTypeValue = "WEEKLY_PLAN_DRAFT_READY";
    const notification: NotificationItem = {
      id: "notif-1",
      type,
      payload: {
        planId: "plan-1",
        message: "Draft ready",
      },
      read: false,
      createdAt: "2026-03-21T10:15:30Z",
    };

    expect(notification.type).toBe("WEEKLY_PLAN_DRAFT_READY");
    expect(notification.payload).toHaveProperty("planId", "plan-1");
  });

  it("supports weekly-planning draft-ready payloads", () => {
    const payload: WeeklyPlanDraftReadyNotificationPayload = {
      planId: "plan-1",
      weekStartDate: "2026-03-23",
      route: "weekly",
      message: "A draft weekly plan is ready.",
      suggestedCommitCount: 3,
      suggestedHours: "14.0",
      capacityHours: "18.0",
    };

    expect(payload.suggestedCommitCount).toBe(3);
    expect(payload.capacityHours).toBe("18.0");
  });

  it("supports misalignment briefing payloads", () => {
    const payload: PlanMisalignmentBriefingNotificationPayload = {
      managerId: "manager-1",
      teamName: "Platform",
      weekStartDate: "2026-03-23",
      route: "weekly/team",
      message: "Two plans over-index on non-urgent work.",
      overloadedMembers: ["Alice"],
      urgentOutcomesNeedingAttention: ["Reduce incident volume"],
      highUrgencyHours: "12.0",
      nonUrgentHours: "20.0",
      flaggedPlanIds: ["plan-1", "plan-2"],
      concernCount: 2,
    };

    expect(payload.flaggedPlanIds).toHaveLength(2);
    expect(payload.concernCount).toBe(2);
  });
});

describe("UserProfileResponse", () => {
  it("supports a populated behavioural profile", () => {
    const profile: UserProfileResponse = {
      userId: "user-1",
      weeksAnalyzed: 6,
      performanceProfile: {
        estimationAccuracy: 0.74,
        completionReliability: 0.81,
        avgCommitsPerWeek: 4.5,
        avgCarryForwardPerWeek: 0.8,
        topCategories: ["DELIVERY", "OPERATIONS"],
        categoryCompletionRates: { DELIVERY: 0.9 },
        priorityCompletionRates: { KING: 0.8 },
      },
      preferences: {
        typicalPriorityPattern: "1K-2Q-1R",
        recurringCommitTitles: ["Weekly ops review"],
        avgCheckInsPerWeek: 2.3,
        preferredUpdateDays: ["MONDAY", "WEDNESDAY"],
      },
      trends: {
        strategicAlignmentTrend: "IMPROVING",
        completionTrend: "STABLE",
        carryForwardTrend: "WORSENING",
      },
    };

    expect(profile.performanceProfile?.topCategories[0]).toBe("DELIVERY");
    expect(profile.trends?.carryForwardTrend).toBe("WORSENING");
  });
});

describe("Outcome urgency contracts", () => {
  it("supports metadata request/response payloads", () => {
    const req: OutcomeMetadataRequest = {
      targetDate: "2026-06-30",
      progressType: "METRIC",
      metricName: "ARR",
      targetValue: 100,
      currentValue: 45,
      unit: "%",
    };
    const resp: OutcomeMetadataResponse = {
      orgId: "org-1",
      outcomeId: "outcome-1",
      targetDate: "2026-06-30",
      progressType: "METRIC",
      metricName: "ARR",
      targetValue: 100,
      currentValue: 45,
      unit: "%",
      milestones: null,
      progressPct: 45,
      urgencyBand: "AT_RISK",
      lastComputedAt: "2026-03-21T12:00:00Z",
      createdAt: "2026-03-01T12:00:00Z",
      updatedAt: "2026-03-21T12:00:00Z",
    };

    expect(req.progressType).toBe("METRIC");
    expect(resp.urgencyBand).toBe("AT_RISK");
  });

  it("supports urgency summary and strategic slack envelopes", () => {
    const summary: UrgencySummaryResponse = {
      outcomes: [{
        outcomeId: "outcome-1",
        outcomeName: "Improve activation",
        targetDate: "2026-06-30",
        progressPct: 45,
        expectedProgressPct: 60,
        urgencyBand: "AT_RISK",
        daysRemaining: 101,
      }],
    };
    const slack: StrategicSlackResponse = {
      slack: {
        slackBand: "LOW_SLACK",
        strategicFocusFloor: 0.8,
        atRiskCount: 2,
        criticalCount: 1,
      },
    };

    expect(summary.outcomes[0].urgencyBand).toBe("AT_RISK");
    expect(slack.slack.slackBand).toBe("LOW_SLACK");
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


describe("Phase 5 forecasting contracts", () => {
  it("supports persisted outcome forecast responses", () => {
    const resp: OutcomeForecastListResponse = {
      forecasts: [{
        outcomeId: "00000000-0000-0000-0000-000000000001",
        outcomeName: "Improve activation",
        targetDate: "2026-04-12",
        projectedTargetDate: "2026-04-18",
        projectedProgressPct: 72.5,
        projectedVelocity: 5.25,
        confidenceScore: 0.76,
        confidenceBand: "HIGH",
        forecastStatus: "NEEDS_ATTENTION",
        modelVersion: "phase5-target-date-v1",
        contributingFactors: [{ type: "capacity", label: "Capacity coverage", score: 0.82, detail: "Strategic capacity is improving." }],
        recommendations: ["Add more mapped work"],
        computedAt: "2026-03-21T10:15:30Z",
      }],
    };

    expect(resp.forecasts[0].confidenceBand).toBe("HIGH");
    expect(resp.forecasts[0].contributingFactors[0].score).toBeGreaterThan(0);
  });
});

describe("Phase 5 planning-copilot contracts", () => {
  it("supports team suggestion payloads", () => {
    const resp: TeamPlanSuggestionResponse = {
      status: "ok",
      weekStart: "2026-03-23",
      summary: {
        teamCapacityHours: 40,
        suggestedHours: 32,
        bufferHours: 8,
        atRiskOutcomeCount: 1,
        criticalOutcomeCount: 0,
        strategicFocusFloor: 0.7,
        headline: "Healthy buffer remains.",
      },
      members: [{
        userId: "user-1",
        displayName: "Alice",
        suggestedCommits: [{
          title: "Advance onboarding milestone",
          outcomeId: "outcome-1",
          chessPriority: ChessPriority.QUEEN,
          estimatedHours: 6,
          rationale: "Historical strength on activation work.",
          source: "OUTCOME_DEMAND",
        }],
        totalEstimated: 6,
        realisticCapacity: 8,
        overcommitRisk: "LOW",
        strengthSummary: "Reliable on delivery commitments.",
      }],
      outcomeAllocations: [{
        outcomeId: "outcome-1",
        outcomeName: "Improve activation",
        urgencyBand: "AT_RISK",
        recommendedHours: 6,
        members: [{ userId: "user-1", displayName: "Alice", hours: 6, title: "Advance onboarding milestone" }],
      }],
      llmRefined: false,
    };

    expect(resp.members[0].suggestedCommits[0].chessPriority).toBe("QUEEN");
    expect(resp.outcomeAllocations[0].members[0].hours).toBe(6);
  });

  it("supports apply results with created draft metadata", () => {
    const resp: ApplyTeamPlanSuggestionResponse = {
      status: "ok",
      weekStart: "2026-03-23",
      members: [{
        userId: "user-1",
        displayName: "Alice",
        planId: "plan-1",
        createdPlan: true,
        appliedCommits: [],
      }],
    };

    expect(resp.members[0].createdPlan).toBe(true);
  });
});

describe("TrendsResponse", () => {
  it("supports additive effort-type trend fields", () => {
    const resp: TrendsResponse = {
      weeksAnalyzed: 2,
      windowStart: "2026-03-10",
      windowEnd: "2026-03-17",
      strategicAlignmentRate: 0.5,
      teamStrategicAlignmentRate: 0.4,
      avgCarryForwardPerWeek: 1,
      carryForwardStreak: 1,
      avgConfidence: 0.7,
      completionAccuracy: 0.6,
      confidenceAccuracyGap: 0.1,
      avgEstimatedHoursPerWeek: 12,
      avgActualHoursPerWeek: 10,
      hoursAccuracyRatio: 0.83,
      priorityDistribution: { KING: 0.5, QUEEN: 0.5 },
      categoryDistribution: { DELIVERY: 0.5, OPERATIONS: 0.5 },
      effortTypeDistribution: { BUILD: 0.5, MAINTAIN: 0.5 },
      weekPoints: [{
        weekStart: "2026-03-10",
        totalCommits: 2,
        strategicCommits: 1,
        carryForwardCommits: 0,
        avgConfidence: 0.7,
        completionRate: 0.5,
        hasActuals: true,
        priorityCounts: { KING: 1, QUEEN: 1 },
        categoryCounts: { DELIVERY: 1, OPERATIONS: 1 },
        estimatedHours: 12,
        actualHours: 10,
        hoursAccuracyRatio: 0.83,
        effortTypeCounts: { BUILD: 1, MAINTAIN: 1 },
      }],
      insights: [],
    };

    expect(resp.effortTypeDistribution?.BUILD).toBe(0.5);
    expect(resp.weekPoints[0].effortTypeCounts?.MAINTAIN).toBe(1);
  });
});

describe("Phase 5 executive contracts", () => {
  it("supports executive dashboard rollups", () => {
    const resp: ExecutiveDashboardResponse = {
      weekStart: "2026-03-23",
      summary: {
        totalForecasts: 4,
        onTrackForecasts: 2,
        needsAttentionForecasts: 1,
        offTrackForecasts: 1,
        noDataForecasts: 0,
        averageForecastConfidence: 0.71,
        totalCapacityHours: 120,
        strategicHours: 78,
        nonStrategicHours: 42,
        strategicCapacityUtilizationPct: 65,
        nonStrategicCapacityUtilizationPct: 35,
        planningCoveragePct: 92,
      },
      rallyCryRollups: [{
        rallyCryId: "rc-1",
        rallyCryName: "Growth",
        forecastedOutcomeCount: 2,
        onTrackCount: 1,
        needsAttentionCount: 1,
        offTrackCount: 0,
        noDataCount: 0,
        averageForecastConfidence: 0.73,
        strategicHours: 32,
      }],
      teamBuckets: [{
        bucketId: "team-a",
        memberCount: 5,
        planCoveragePct: 100,
        totalCapacityHours: 40,
        strategicHours: 28,
        nonStrategicHours: 12,
        strategicCapacityUtilizationPct: 70,
        averageForecastConfidence: 0.75,
      }],
      teamGroupingAvailable: true,
    };

    expect(resp.summary.totalForecasts).toBe(4);
    expect(resp.teamBuckets[0].bucketId).toBe("team-a");
  });

  it("supports executive briefing responses", () => {
    const resp: ExecutiveBriefingResponse = {
      status: "ok",
      headline: "Strategic capacity remains concentrated in forecasted work.",
      insights: [{ title: "Focus is healthy", detail: "Strategic utilization remains above 70%.", severity: "POSITIVE" }],
    };

    expect(resp.insights[0].severity).toBe("POSITIVE");
  });
});
