/**
 * Hook for managing weekly assignments (issues added to a weekly plan from the backlog).
 *
 * Endpoints used:
 *   GET  /plans/{planId}/assignments          — list assignments for a plan
 *   POST /weeks/{weekStart}/plan/assignments  — add an issue to the week plan
 *   DELETE /weeks/{weekStart}/plan/assignments/{assignmentId} — remove assignment
 *   POST /issues/{issueId}/release            — release issue back to backlog
 */
import { useState, useCallback } from "react";
import type {
  WeeklyAssignment,
  WeeklyAssignmentWithActual,
  CreateWeeklyAssignmentRequest,
  ApiErrorResponse,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";

export interface UseWeeklyAssignmentsResult {
  assignments: WeeklyAssignmentWithActual[];
  loading: boolean;
  error: string | null;
  fetchAssignments: (planId: string) => Promise<void>;
  createAssignment: (weekStart: string, req: CreateWeeklyAssignmentRequest) => Promise<WeeklyAssignment | null>;
  removeAssignment: (weekStart: string, assignmentId: string) => Promise<boolean>;
  releaseToBacklog: (issueId: string, weeklyPlanId: string) => Promise<boolean>;
  resetAssignments: () => void;
  clearError: () => void;
}

export function useWeeklyAssignments(): UseWeeklyAssignmentsResult {
  const client = useApiClient();
  const [assignments, setAssignments] = useState<WeeklyAssignmentWithActual[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);
  const resetAssignments = useCallback(() => setAssignments([]), []);

  const extractError = useCallback((resp: { error?: unknown; response: Response }): string => {
    const err = resp.error as ApiErrorResponse | undefined;
    if (err?.error?.message) return err.error.message;
    return `Request failed (${String(resp.response.status)})`;
  }, []);

  const fetchAssignments = useCallback(
    async (planId: string): Promise<void> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/plans/{planId}/assignments", {
          params: { path: { planId } },
        });
        if (resp.data) {
          const data = resp.data as { assignments: WeeklyAssignmentWithActual[] };
          setAssignments(data.assignments);
        } else {
          setError(extractError(resp));
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  const createAssignment = useCallback(
    async (weekStart: string, req: CreateWeeklyAssignmentRequest): Promise<WeeklyAssignment | null> => {
      setLoading(true);
      setError(null);
      try {
        const body: Record<string, unknown> = { issueId: req.issueId };
        if (req.chessPriorityOverride != null) body.chessPriorityOverride = req.chessPriorityOverride;
        if (req.expectedResult != null) body.expectedResult = req.expectedResult;
        if (req.confidence != null) body.confidence = req.confidence;

        const resp = await client.POST("/weeks/{weekStart}/plan/assignments", {
          params: { path: { weekStart } },
          body: body as Parameters<typeof client.POST>[1]["body"],
        });
        if (resp.data) {
          const created = resp.data as WeeklyAssignment;
          // Add to local list with null actual/issue (will be refreshed)
          setAssignments((prev) => [{ ...created, actual: null, issue: null }, ...prev]);
          return created;
        }
        setError(extractError(resp));
        return null;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return null;
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  const removeAssignment = useCallback(
    async (weekStart: string, assignmentId: string): Promise<boolean> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.DELETE("/weeks/{weekStart}/plan/assignments/{assignmentId}", {
          params: { path: { weekStart, assignmentId } },
        });
        if (resp.response.ok) {
          setAssignments((prev) => prev.filter((a) => a.id !== assignmentId));
          return true;
        }
        setError(extractError(resp));
        return false;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return false;
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  const releaseToBacklog = useCallback(
    async (issueId: string, weeklyPlanId: string): Promise<boolean> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.POST("/issues/{issueId}/release", {
          params: { path: { issueId } },
          body: { weeklyPlanId },
        });
        if (resp.data || resp.response.ok) {
          // Remove any assignment for this issue from local state
          setAssignments((prev) => prev.filter((a) => a.issueId !== issueId));
          return true;
        }
        setError(extractError(resp));
        return false;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return false;
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  return {
    assignments,
    loading,
    error,
    fetchAssignments,
    createAssignment,
    removeAssignment,
    releaseToBacklog,
    resetAssignments,
    clearError,
  };
}
