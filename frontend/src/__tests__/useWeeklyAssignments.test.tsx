import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useWeeklyAssignments } from "../hooks/useWeeklyAssignments.js";
import type { WeeklyAssignment, WeeklyAssignmentWithActual } from "@weekly-commitments/contracts";

/* ── Mock API client ── */

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockDelete = vi.fn();

vi.mock("../api/ApiContext.js", () => ({
  useApiClient: () => ({
    GET: mockGet,
    POST: mockPost,
    DELETE: mockDelete,
    PATCH: vi.fn(),
  }),
}));

/* ── Helpers ── */

function makeAssignment(overrides: Partial<WeeklyAssignment> = {}): WeeklyAssignment {
  return {
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
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides,
  };
}

function makeAssignmentWithActual(overrides: Partial<WeeklyAssignmentWithActual> = {}): WeeklyAssignmentWithActual {
  return {
    ...makeAssignment(),
    actual: null,
    issue: null,
    ...overrides,
  };
}

/* ── Tests ── */

describe("useWeeklyAssignments", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("initializes with empty assignments and idle state", () => {
    const { result } = renderHook(() => useWeeklyAssignments());
    expect(result.current.assignments).toEqual([]);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("fetchAssignments loads assignments for a plan", async () => {
    const assignments = [makeAssignmentWithActual(), makeAssignmentWithActual({ id: "assignment-2" })];
    mockGet.mockResolvedValueOnce({
      data: { assignments },
      response: new Response(null, { status: 200 }),
    });

    const { result } = renderHook(() => useWeeklyAssignments());
    await act(async () => {
      await result.current.fetchAssignments("plan-1");
    });

    expect(mockGet).toHaveBeenCalledWith("/plans/{planId}/assignments", {
      params: { path: { planId: "plan-1" } },
    });
    expect(result.current.assignments).toHaveLength(2);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("fetchAssignments sets error on failure", async () => {
    mockGet.mockResolvedValueOnce({
      data: undefined,
      error: { error: { message: "Not found" } },
      response: new Response(null, { status: 404 }),
    });

    const { result } = renderHook(() => useWeeklyAssignments());
    await act(async () => {
      await result.current.fetchAssignments("plan-1");
    });

    expect(result.current.error).toBe("Not found");
    expect(result.current.assignments).toEqual([]);
  });

  it("createAssignment creates a new assignment and adds it to state", async () => {
    const newAssignment = makeAssignment();
    mockPost.mockResolvedValueOnce({
      data: newAssignment,
      response: new Response(null, { status: 201 }),
    });

    const { result } = renderHook(() => useWeeklyAssignments());
    let created: WeeklyAssignment | null = null;
    await act(async () => {
      created = await result.current.createAssignment("2026-03-09", { issueId: "issue-1" });
    });

    expect(mockPost).toHaveBeenCalledWith("/weeks/{weekStart}/plan/assignments", {
      params: { path: { weekStart: "2026-03-09" } },
      body: expect.objectContaining({ issueId: "issue-1" }),
    });
    expect(created).toEqual(newAssignment);
    expect(result.current.assignments).toHaveLength(1);
    expect(result.current.assignments[0].id).toBe("assignment-1");
  });

  it("removeAssignment removes the assignment from state", async () => {
    const assignment = makeAssignmentWithActual();
    mockGet.mockResolvedValueOnce({
      data: { assignments: [assignment] },
      response: new Response(null, { status: 200 }),
    });
    mockDelete.mockResolvedValueOnce({
      response: new Response(null, { status: 204 }),
    });

    const { result } = renderHook(() => useWeeklyAssignments());
    await act(async () => {
      await result.current.fetchAssignments("plan-1");
    });
    expect(result.current.assignments).toHaveLength(1);

    await act(async () => {
      await result.current.removeAssignment("2026-03-09", "assignment-1");
    });

    expect(mockDelete).toHaveBeenCalledWith("/weeks/{weekStart}/plan/assignments/{assignmentId}", {
      params: { path: { weekStart: "2026-03-09", assignmentId: "assignment-1" } },
    });
    expect(result.current.assignments).toHaveLength(0);
  });

  it("releaseToBacklog removes the assignment for that issue from state", async () => {
    const assignment = makeAssignmentWithActual({ issueId: "issue-42" });
    mockGet.mockResolvedValueOnce({
      data: { assignments: [assignment] },
      response: new Response(null, { status: 200 }),
    });
    mockPost.mockResolvedValueOnce({
      data: { id: "issue-42", status: "OPEN" },
      response: new Response(null, { status: 200 }),
    });

    const { result } = renderHook(() => useWeeklyAssignments());
    await act(async () => {
      await result.current.fetchAssignments("plan-1");
    });
    expect(result.current.assignments).toHaveLength(1);

    await act(async () => {
      await result.current.releaseToBacklog("issue-42", "plan-1");
    });

    expect(mockPost).toHaveBeenCalledWith("/issues/{issueId}/release", {
      params: { path: { issueId: "issue-42" } },
      body: { weeklyPlanId: "plan-1" },
    });
    expect(result.current.assignments).toHaveLength(0);
  });

  it("resetAssignments clears the list", async () => {
    const assignment = makeAssignmentWithActual();
    mockGet.mockResolvedValueOnce({
      data: { assignments: [assignment] },
      response: new Response(null, { status: 200 }),
    });

    const { result } = renderHook(() => useWeeklyAssignments());
    await act(async () => {
      await result.current.fetchAssignments("plan-1");
    });
    expect(result.current.assignments).toHaveLength(1);

    act(() => {
      result.current.resetAssignments();
    });
    expect(result.current.assignments).toHaveLength(0);
  });

  it("clearError clears the error state", async () => {
    mockGet.mockResolvedValueOnce({
      data: undefined,
      error: { error: { message: "Forbidden" } },
      response: new Response(null, { status: 403 }),
    });

    const { result } = renderHook(() => useWeeklyAssignments());
    await act(async () => {
      await result.current.fetchAssignments("plan-1");
    });
    expect(result.current.error).toBe("Forbidden");

    act(() => {
      result.current.clearError();
    });
    expect(result.current.error).toBeNull();
  });
});
