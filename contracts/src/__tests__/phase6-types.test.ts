/**
 * Phase 6: Issue Backlog, Teams & AI Work Intelligence
 *
 * Type-level tests verifying the new enums, entity types, and API contracts.
 */
import { describe, it, expect } from "vitest";
import {
  EffortType,
  IssueStatus,
  TeamRole,
  AccessRequestStatus,
  IssueActivityType,
} from "../enums.js";
import type {
  Issue,
  WeeklyAssignment,
  WeeklyAssignmentActual,
  IssueActivity,
  Team,
  TeamMember,
  TeamAccessRequest,
} from "../types.js";
import type {
  CreateIssueRequest,
  UpdateIssueRequest,
  IssueListResponse,
  IssueDetailResponse,
  AssignIssueRequest,
  CommitIssueToWeekRequest,
  CreateWeeklyAssignmentRequest,
  ReleaseIssueRequest,
  AddCommentRequest,
  LogTimeEntryRequest,
  WeeklyAssignmentsResponse,
  CreateTeamRequest,
  UpdateTeamRequest,
  AddTeamMemberRequest,
  TeamListResponse,
  TeamDetailResponse,
  TeamAccessRequestAction,
  TeamAccessRequestListResponse,
  SuggestEffortTypeRequest,
  SuggestEffortTypeResponse,
  RankBacklogRequest,
  RankedIssue,
  RankBacklogResponse,
  RecommendWeeklyIssuesRequest,
  RecommendedIssue,
  RecommendWeeklyIssuesResponse,
  SuggestDeferralsRequest,
  DeferralSuggestion,
  SuggestDeferralsResponse,
  CoverageGapInspirationsResponse,
  SemanticSearchRequest,
  SemanticSearchHit,
  SemanticSearchResponse,
} from "../api.js";
import { ChessPriority, CompletionStatus } from "../enums.js";

describe("EffortType enum", () => {
  it("contains all four effort types", () => {
    expect(Object.values(EffortType)).toEqual(["BUILD", "MAINTAIN", "COLLABORATE", "LEARN"]);
  });

  it("has correct string values", () => {
    expect(EffortType.BUILD).toBe("BUILD");
    expect(EffortType.MAINTAIN).toBe("MAINTAIN");
    expect(EffortType.COLLABORATE).toBe("COLLABORATE");
    expect(EffortType.LEARN).toBe("LEARN");
  });
});

describe("IssueStatus enum", () => {
  it("contains all four statuses", () => {
    expect(Object.values(IssueStatus)).toEqual(["OPEN", "IN_PROGRESS", "DONE", "ARCHIVED"]);
  });
});

describe("TeamRole enum", () => {
  it("contains OWNER and MEMBER", () => {
    expect(Object.values(TeamRole)).toEqual(["OWNER", "MEMBER"]);
  });
});

describe("AccessRequestStatus enum", () => {
  it("contains PENDING, APPROVED, DENIED", () => {
    expect(Object.values(AccessRequestStatus)).toEqual(["PENDING", "APPROVED", "DENIED"]);
  });
});

describe("IssueActivityType enum", () => {
  it("contains all 16 activity types", () => {
    expect(Object.values(IssueActivityType)).toHaveLength(16);
  });

  it("matches the Phase 6 migration plan activity names", () => {
    expect(Object.values(IssueActivityType)).toEqual([
      "CREATED",
      "STATUS_CHANGE",
      "ASSIGNMENT_CHANGE",
      "PRIORITY_CHANGE",
      "EFFORT_TYPE_CHANGE",
      "ESTIMATE_CHANGE",
      "COMMENT",
      "TIME_ENTRY",
      "OUTCOME_CHANGE",
      "COMMITTED_TO_WEEK",
      "RELEASED_TO_BACKLOG",
      "CARRIED_FORWARD",
      "BLOCKED",
      "UNBLOCKED",
      "DESCRIPTION_CHANGE",
      "TITLE_CHANGE",
    ]);
  });
});

describe("Issue type", () => {
  it("supports a minimal issue", () => {
    const issue: Issue = {
      id: "issue-1",
      orgId: "org-1",
      teamId: "team-1",
      issueKey: "ENG-42",
      sequenceNumber: 42,
      title: "Implement OAuth flow",
      description: null,
      effortType: EffortType.BUILD,
      estimatedHours: 8,
      chessPriority: ChessPriority.QUEEN,
      outcomeId: null,
      nonStrategicReason: null,
      creatorUserId: "user-1",
      assigneeUserId: null,
      blockedByIssueId: null,
      status: IssueStatus.OPEN,
      aiRecommendedRank: null,
      aiRankRationale: null,
      aiSuggestedEffortType: null,
      version: 1,
      createdAt: "2026-03-22T10:00:00Z",
      updatedAt: "2026-03-22T10:00:00Z",
      archivedAt: null,
    };

    expect(issue.issueKey).toBe("ENG-42");
    expect(issue.effortType).toBe("BUILD");
    expect(issue.status).toBe("OPEN");
  });

  it("supports AI-enriched issue fields", () => {
    const issue: Issue = {
      id: "issue-2",
      orgId: "org-1",
      teamId: "team-1",
      issueKey: "ENG-43",
      sequenceNumber: 43,
      title: "Refactor auth middleware",
      description: "Reduces complexity by 30%",
      effortType: EffortType.MAINTAIN,
      estimatedHours: 4,
      chessPriority: ChessPriority.ROOK,
      outcomeId: "outcome-1",
      nonStrategicReason: null,
      creatorUserId: "user-1",
      assigneeUserId: "user-2",
      blockedByIssueId: "issue-1",
      status: IssueStatus.IN_PROGRESS,
      aiRecommendedRank: 2.5,
      aiRankRationale: "High urgency based on recent incidents",
      aiSuggestedEffortType: EffortType.MAINTAIN,
      version: 3,
      createdAt: "2026-03-22T09:00:00Z",
      updatedAt: "2026-03-22T11:00:00Z",
      archivedAt: null,
    };

    expect(issue.aiRecommendedRank).toBe(2.5);
    expect(issue.aiSuggestedEffortType).toBe("MAINTAIN");
    expect(issue.blockedByIssueId).toBe("issue-1");
  });
});

describe("WeeklyAssignment type", () => {
  it("links an issue to a weekly plan", () => {
    const assignment: WeeklyAssignment = {
      id: "assignment-1",
      orgId: "org-1",
      weeklyPlanId: "plan-1",
      issueId: "issue-1",
      chessPriorityOverride: ChessPriority.KING,
      expectedResult: "OAuth flow working in staging",
      confidence: 0.85,
      snapshotRallyCryId: "rc-1",
      snapshotRallyCryName: "Growth",
      snapshotObjectiveId: "obj-1",
      snapshotObjectiveName: "Improve Auth",
      snapshotOutcomeId: "outcome-1",
      snapshotOutcomeName: "Secure login",
      tags: ["auth", "security"],
      version: 1,
      createdAt: "2026-03-22T10:00:00Z",
      updatedAt: "2026-03-22T10:00:00Z",
    };

    expect(assignment.chessPriorityOverride).toBe("KING");
    expect(assignment.tags).toHaveLength(2);
    expect(assignment.confidence).toBe(0.85);
  });
});

describe("WeeklyAssignmentActual type", () => {
  it("records actuals for a reconciled assignment", () => {
    const actual: WeeklyAssignmentActual = {
      assignmentId: "assignment-1",
      orgId: "org-1",
      actualResult: "OAuth integrated and tested",
      completionStatus: CompletionStatus.DONE,
      deltaReason: null,
      hoursSpent: 7.5,
      createdAt: "2026-03-28T10:00:00Z",
      updatedAt: "2026-03-28T10:00:00Z",
    };

    expect(actual.completionStatus).toBe("DONE");
    expect(actual.hoursSpent).toBe(7.5);
  });
});

describe("IssueActivity type", () => {
  it("records a comment activity", () => {
    const activity: IssueActivity = {
      id: "activity-1",
      orgId: "org-1",
      issueId: "issue-1",
      actorUserId: "user-1",
      activityType: IssueActivityType.COMMENT,
      oldValue: null,
      newValue: null,
      commentText: "Blocked on external API response",
      hoursLogged: null,
      metadata: null,
      createdAt: "2026-03-22T10:30:00Z",
    };

    expect(activity.activityType).toBe("COMMENT");
    expect(activity.commentText).toBe("Blocked on external API response");
  });

  it("records a status change activity", () => {
    const activity: IssueActivity = {
      id: "activity-2",
      orgId: "org-1",
      issueId: "issue-1",
      actorUserId: "user-1",
      activityType: IssueActivityType.STATUS_CHANGE,
      oldValue: "OPEN",
      newValue: "IN_PROGRESS",
      commentText: null,
      hoursLogged: null,
      metadata: { triggeredBy: "assignment" },
      createdAt: "2026-03-22T11:00:00Z",
    };

    expect(activity.oldValue).toBe("OPEN");
    expect(activity.newValue).toBe("IN_PROGRESS");
    expect(activity.metadata).toHaveProperty("triggeredBy", "assignment");
  });
});

describe("Team type", () => {
  it("represents a team entity", () => {
    const team: Team = {
      id: "team-1",
      orgId: "org-1",
      name: "Platform Engineering",
      keyPrefix: "ENG",
      description: "Core platform and infrastructure",
      ownerUserId: "user-1",
      issueSequence: 42,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-03-22T10:00:00Z",
    };

    expect(team.keyPrefix).toBe("ENG");
    expect(team.issueSequence).toBe(42);
  });
});

describe("TeamMember type", () => {
  it("represents team membership", () => {
    const member: TeamMember = {
      teamId: "team-1",
      userId: "user-1",
      orgId: "org-1",
      role: TeamRole.OWNER,
      joinedAt: "2026-01-01T00:00:00Z",
    };

    expect(member.role).toBe("OWNER");
  });
});

describe("TeamAccessRequest type", () => {
  it("represents a pending access request", () => {
    const req: TeamAccessRequest = {
      id: "req-1",
      teamId: "team-1",
      requesterUserId: "user-2",
      orgId: "org-1",
      status: AccessRequestStatus.PENDING,
      decidedByUserId: null,
      decidedAt: null,
      createdAt: "2026-03-22T10:00:00Z",
    };

    expect(req.status).toBe("PENDING");
    expect(req.decidedByUserId).toBeNull();
  });
});

describe("Issue API request types", () => {
  it("supports CreateIssueRequest with minimal fields", () => {
    const req: CreateIssueRequest = {
      title: "Add rate limiting",
    };
    expect(req.title).toBe("Add rate limiting");
    expect(req.effortType).toBeUndefined();
  });

  it("supports CreateIssueRequest with all fields", () => {
    const req: CreateIssueRequest = {
      title: "Add rate limiting",
      description: "Protect API endpoints from abuse",
      effortType: EffortType.BUILD,
      estimatedHours: 6,
      chessPriority: ChessPriority.QUEEN,
      outcomeId: "outcome-1",
      nonStrategicReason: null,
      assigneeUserId: "user-2",
      blockedByIssueId: null,
    };
    expect(req.effortType).toBe("BUILD");
    expect(req.estimatedHours).toBe(6);
  });

  it("supports UpdateIssueRequest as partial patch", () => {
    const req: UpdateIssueRequest = {
      status: IssueStatus.IN_PROGRESS,
      version: 2,
    };
    expect(req.status).toBe("IN_PROGRESS");
    expect(req.title).toBeUndefined();
  });

  it("supports paginated IssueListResponse", () => {
    const resp: IssueListResponse = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    };
    expect(resp.content).toHaveLength(0);
  });

  it("supports IssueDetailResponse with activities", () => {
    const resp: IssueDetailResponse = {
      issue: {
        id: "issue-1",
        orgId: "org-1",
        teamId: "team-1",
        issueKey: "ENG-1",
        sequenceNumber: 1,
        title: "Test issue",
        description: null,
        effortType: null,
        estimatedHours: null,
        chessPriority: null,
        outcomeId: null,
        nonStrategicReason: null,
        creatorUserId: "user-1",
        assigneeUserId: null,
        blockedByIssueId: null,
        status: IssueStatus.OPEN,
        aiRecommendedRank: null,
        aiRankRationale: null,
        aiSuggestedEffortType: null,
        version: 1,
        createdAt: "2026-03-22T10:00:00Z",
        updatedAt: "2026-03-22T10:00:00Z",
        archivedAt: null,
      },
      activities: [],
    };
    expect(resp.issue.issueKey).toBe("ENG-1");
    expect(resp.activities).toHaveLength(0);
  });

  it("supports AssignIssueRequest with null (unassign)", () => {
    const req: AssignIssueRequest = { assigneeUserId: null };
    expect(req.assigneeUserId).toBeNull();
  });

  it("supports CommitIssueToWeekRequest", () => {
    const req: CommitIssueToWeekRequest = {
      weekStart: "2026-03-23",
      chessPriorityOverride: ChessPriority.QUEEN,
      expectedResult: "Feature in staging",
      confidence: 0.9,
    };
    expect(req.weekStart).toBe("2026-03-23");
    expect(req.confidence).toBe(0.9);
  });

  it("supports CreateWeeklyAssignmentRequest for week-scoped assignment endpoints", () => {
    const req: CreateWeeklyAssignmentRequest = {
      issueId: "issue-1",
      chessPriorityOverride: ChessPriority.QUEEN,
      expectedResult: "Feature in staging",
      confidence: 0.9,
    };
    expect(req.issueId).toBe("issue-1");
    expect(req.confidence).toBe(0.9);
  });

  it("supports ReleaseIssueRequest", () => {
    const req: ReleaseIssueRequest = { weeklyPlanId: "plan-1" };
    expect(req.weeklyPlanId).toBe("plan-1");
  });

  it("supports AddCommentRequest", () => {
    const req: AddCommentRequest = { commentText: "Blocked waiting for external API" };
    expect(req.commentText).toBe("Blocked waiting for external API");
  });

  it("supports LogTimeEntryRequest", () => {
    const req: LogTimeEntryRequest = { hoursLogged: 3.5, note: "Deep work session" };
    expect(req.hoursLogged).toBe(3.5);
  });
});

describe("Team API request types", () => {
  it("supports CreateTeamRequest", () => {
    const req: CreateTeamRequest = {
      name: "Platform Engineering",
      keyPrefix: "ENG",
      description: "Core platform team",
    };
    expect(req.keyPrefix).toBe("ENG");
  });

  it("supports UpdateTeamRequest as partial", () => {
    const req: UpdateTeamRequest = { name: "Platform & Infra" };
    expect(req.name).toBe("Platform & Infra");
    expect(req.description).toBeUndefined();
  });

  it("supports AddTeamMemberRequest", () => {
    const req: AddTeamMemberRequest = { userId: "user-2", role: TeamRole.MEMBER };
    expect(req.role).toBe("MEMBER");
  });

  it("supports TeamListResponse", () => {
    const resp: TeamListResponse = { teams: [] };
    expect(resp.teams).toHaveLength(0);
  });

  it("supports TeamDetailResponse", () => {
    const resp: TeamDetailResponse = {
      team: {
        id: "team-1",
        orgId: "org-1",
        name: "ENG",
        keyPrefix: "ENG",
        description: null,
        ownerUserId: "user-1",
        issueSequence: 0,
        createdAt: "2026-03-22T10:00:00Z",
        updatedAt: "2026-03-22T10:00:00Z",
      },
      members: [],
    };
    expect(resp.team.keyPrefix).toBe("ENG");
    expect(resp.members).toHaveLength(0);
  });

  it("supports TeamAccessRequestAction", () => {
    const action: TeamAccessRequestAction = { status: AccessRequestStatus.APPROVED };
    expect(action.status).toBe("APPROVED");
  });

  it("supports TeamAccessRequestListResponse", () => {
    const resp: TeamAccessRequestListResponse = { requests: [] };
    expect(resp.requests).toHaveLength(0);
  });
});

describe("WeeklyAssignmentsResponse", () => {
  it("supports assignments with embedded issue and actual", () => {
    const resp: WeeklyAssignmentsResponse = {
      assignments: [
        {
          id: "assignment-1",
          orgId: "org-1",
          weeklyPlanId: "plan-1",
          issueId: "issue-1",
          chessPriorityOverride: null,
          expectedResult: null,
          confidence: null,
          snapshotRallyCryId: null,
          snapshotRallyCryName: null,
          snapshotObjectiveId: null,
          snapshotObjectiveName: null,
          snapshotOutcomeId: null,
          snapshotOutcomeName: null,
          tags: [],
          version: 1,
          createdAt: "2026-03-22T10:00:00Z",
          updatedAt: "2026-03-22T10:00:00Z",
          actual: null,
          issue: null,
        },
      ],
    };
    expect(resp.assignments).toHaveLength(1);
    expect(resp.assignments[0].actual).toBeNull();
  });
});

describe("AI endpoint types", () => {
  it("supports SuggestEffortTypeRequest and Response", () => {
    const req: SuggestEffortTypeRequest = {
      title: "Refactor database layer",
      description: "Reduce query complexity",
      outcomeId: "00000000-0000-0000-0000-000000000001",
    };
    const resp: SuggestEffortTypeResponse = {
      status: "ok",
      suggestedType: EffortType.MAINTAIN,
      confidence: 0.88,
    };
    expect(req.title).toBe("Refactor database layer");
    expect(req.outcomeId).toBe("00000000-0000-0000-0000-000000000001");
    expect(resp.suggestedType).toBe("MAINTAIN");
    expect(resp.confidence).toBeGreaterThan(0);
  });

  it("supports RankBacklogRequest and Response", () => {
    const req: RankBacklogRequest = {
      teamId: "team-1",
      issueIds: ["issue-1", "issue-2"],
    };
    const ranked: RankedIssue = { issueId: "issue-1", rank: 1, rationale: "Highest urgency" };
    const resp: RankBacklogResponse = {
      status: "ok",
      rankedIssues: [ranked],
    };
    expect(req.issueIds).toHaveLength(2);
    expect(resp.rankedIssues[0].rank).toBe(1);
  });

  it("supports RecommendWeeklyIssuesRequest and Response", () => {
    const req: RecommendWeeklyIssuesRequest = {
      weekStart: "2026-03-23",
      teamId: "team-1",
      maxItems: 5,
    };
    expect(req.maxItems).toBe(5);
    const rec: RecommendedIssue = {
      issueId: "issue-1",
      issueKey: "ENG-1",
      title: "Ship OAuth",
      effortType: EffortType.BUILD,
      chessPriority: ChessPriority.QUEEN,
      rationale: "High priority for this sprint",
      confidence: 0.92,
    };
    const resp: RecommendWeeklyIssuesResponse = {
      status: "ok",
      recommendations: [rec],
    };
    expect(resp.recommendations[0].confidence).toBe(0.92);
  });

  it("supports SuggestDeferralsRequest and Response", () => {
    const req: SuggestDeferralsRequest = { weeklyPlanId: "plan-1" };
    const suggestion: DeferralSuggestion = {
      issueId: "issue-3",
      issueKey: "ENG-3",
      title: "Nice-to-have optimization",
      reason: "Low urgency, low complexity reduction",
      impactIfDeferred: "Minor performance degradation persists one more week",
    };
    const resp: SuggestDeferralsResponse = {
      status: "ok",
      suggestions: [suggestion],
    };
    expect(req.weeklyPlanId).toBe("plan-1");
    expect(resp.suggestions[0].reason).toBeTruthy();
  });

  it("supports CoverageGapInspirationsResponse", () => {
    const resp: CoverageGapInspirationsResponse = {
      status: "ok",
      inspirations: [
        {
          outcomeId: "outcome-3",
          outcomeName: "Reduce churn",
          suggestedTitle: "Implement proactive customer health monitoring",
          rationale: "Outcome has no linked issues this quarter",
          suggestedEffortType: EffortType.BUILD,
        },
      ],
    };
    expect(resp.inspirations[0].suggestedEffortType).toBe("BUILD");
  });

  it("supports SemanticSearchRequest and Response", () => {
    const req: SemanticSearchRequest = {
      query: "authentication and OAuth integration",
      teamId: "team-1",
      effortType: EffortType.BUILD,
      status: IssueStatus.OPEN,
      limit: 10,
    };
    const hit: SemanticSearchHit = {
      issueId: "issue-1",
      issueKey: "ENG-1",
      title: "Implement OAuth flow",
      score: 0.94,
      effortType: EffortType.BUILD,
      status: IssueStatus.OPEN,
    };
    const resp: SemanticSearchResponse = {
      status: "ok",
      hits: [hit],
    };
    expect(req.query).toBe("authentication and OAuth integration");
    expect(resp.hits[0].score).toBe(0.94);
    expect(resp.hits[0].effortType).toBe("BUILD");
  });

  it("supports unavailable status for all AI endpoints", () => {
    const effortResp: SuggestEffortTypeResponse = {
      status: "unavailable",
      suggestedType: null,
      confidence: null,
    };
    const rankResp: RankBacklogResponse = { status: "unavailable", rankedIssues: [] };
    const recResp: RecommendWeeklyIssuesResponse = { status: "unavailable", recommendations: [] };
    const deferResp: SuggestDeferralsResponse = { status: "unavailable", suggestions: [] };
    const searchResp: SemanticSearchResponse = { status: "unavailable", hits: [] };

    expect(effortResp.status).toBe("unavailable");
    expect(rankResp.status).toBe("unavailable");
    expect(recResp.status).toBe("unavailable");
    expect(deferResp.status).toBe("unavailable");
    expect(searchResp.status).toBe("unavailable");
  });
});
