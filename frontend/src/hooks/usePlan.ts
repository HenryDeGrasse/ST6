/**
 * Hooks for weekly plan lifecycle operations.
 *
 * These hooks encapsulate API calls, loading/error state, and
 * optimistic conflict handling for the plan entity.
 */
import { useState, useCallback } from "react";
import type { WeeklyPlan, ApiErrorResponse } from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";

export interface UsePlanResult {
  plan: WeeklyPlan | null;
  loading: boolean;
  error: string | null;
  conflictVersion: number | null;
  fetchPlan: (weekStart: string) => Promise<void>;
  createPlan: (weekStart: string) => Promise<WeeklyPlan | null>;
  lockPlan: (planId: string, version: number) => Promise<WeeklyPlan | null>;
  startReconciliation: (planId: string, version: number) => Promise<WeeklyPlan | null>;
  submitReconciliation: (planId: string, version: number) => Promise<WeeklyPlan | null>;
  carryForward: (planId: string, version: number, commitIds: string[]) => Promise<WeeklyPlan | null>;
  clearError: () => void;
}

export function usePlan(): UsePlanResult {
  const client = useApiClient();
  const [plan, setPlan] = useState<WeeklyPlan | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflictVersion, setConflictVersion] = useState<number | null>(null);

  const clearError = useCallback(() => {
    setError(null);
    setConflictVersion(null);
  }, []);

  const extractError = useCallback(
    (resp: { error?: unknown; response: Response }): string => {
      const err = resp.error as ApiErrorResponse | undefined;
      if (err?.error?.message) return err.error.message;
      return `Request failed (${String(resp.response.status)})`;
    },
    [],
  );

  const postPlanLifecycleWithRetry = useCallback(
    async (path: "/plans/{planId}/lock" | "/plans/{planId}/start-reconciliation" | "/plans/{planId}/submit-reconciliation" | "/plans/{planId}/carry-forward", planId: string, version: number, body?: { commitIds: string[] }) => {
      const firstResp = await client.POST(path, {
        params: {
          path: { planId },
          header: {
            "Idempotency-Key": crypto.randomUUID(),
            "If-Match": version,
          },
        },
        ...(body ? { body } : {}),
      });

      if (firstResp.data) {
        return { resp: firstResp, retried: false };
      }

      if (firstResp.response.status === 409) {
        const err = firstResp.error as ApiErrorResponse | undefined;
        const currentVersion = err?.error?.details?.[0]?.currentVersion;
        if (typeof currentVersion === "number") {
          setConflictVersion(currentVersion);
          const retryResp = await client.POST(path, {
            params: {
              path: { planId },
              header: {
                "Idempotency-Key": crypto.randomUUID(),
                "If-Match": currentVersion,
              },
            },
            ...(body ? { body } : {}),
          });
          return { resp: retryResp, retried: true };
        }
      }

      return { resp: firstResp, retried: false };
    },
    [client],
  );

  const fetchPlan = useCallback(
    async (weekStart: string) => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/weeks/{weekStart}/plans/me", {
          params: { path: { weekStart } },
        });
        if (resp.data) {
          setPlan(resp.data as WeeklyPlan);
        } else if (resp.response.status === 404) {
          setPlan(null);
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

  const createPlan = useCallback(
    async (weekStart: string): Promise<WeeklyPlan | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.POST("/weeks/{weekStart}/plans", {
          params: { path: { weekStart } },
        });
        if (resp.data) {
          const created = resp.data as WeeklyPlan;
          setPlan(created);
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

  const lockPlan = useCallback(
    async (planId: string, version: number): Promise<WeeklyPlan | null> => {
      setLoading(true);
      setError(null);
      setConflictVersion(null);
      try {
        const { resp } = await postPlanLifecycleWithRetry("/plans/{planId}/lock", planId, version);
        if (resp.data) {
          const locked = resp.data as WeeklyPlan;
          setPlan(locked);
          return locked;
        }
        if (resp.response.status === 409) {
          setError("Conflict: the plan was modified. Please try again.");
          return null;
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
    [extractError, postPlanLifecycleWithRetry],
  );

  const startReconciliation = useCallback(
    async (planId: string, version: number): Promise<WeeklyPlan | null> => {
      setLoading(true);
      setError(null);
      setConflictVersion(null);
      try {
        const { resp } = await postPlanLifecycleWithRetry(
          "/plans/{planId}/start-reconciliation",
          planId,
          version,
        );
        if (resp.data) {
          const updated = resp.data as WeeklyPlan;
          setPlan(updated);
          return updated;
        }
        if (resp.response.status === 409) {
          setError("Conflict: the plan was modified. Please try again.");
          return null;
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
    [extractError, postPlanLifecycleWithRetry],
  );

  const submitReconciliation = useCallback(
    async (planId: string, version: number): Promise<WeeklyPlan | null> => {
      setLoading(true);
      setError(null);
      setConflictVersion(null);
      try {
        const { resp } = await postPlanLifecycleWithRetry(
          "/plans/{planId}/submit-reconciliation",
          planId,
          version,
        );
        if (resp.data) {
          const updated = resp.data as WeeklyPlan;
          setPlan(updated);
          return updated;
        }
        if (resp.response.status === 409) {
          setError("Conflict: the plan was modified. Please try again.");
          return null;
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
    [extractError, postPlanLifecycleWithRetry],
  );

  const carryForward = useCallback(
    async (planId: string, version: number, commitIds: string[]): Promise<WeeklyPlan | null> => {
      setLoading(true);
      setError(null);
      setConflictVersion(null);
      try {
        const { resp } = await postPlanLifecycleWithRetry(
          "/plans/{planId}/carry-forward",
          planId,
          version,
          { commitIds },
        );
        if (resp.data) {
          const updated = resp.data as WeeklyPlan;
          setPlan(updated);
          return updated;
        }
        if (resp.response.status === 409) {
          setError("Conflict: the plan was modified. Please try again.");
          return null;
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
    [extractError, postPlanLifecycleWithRetry],
  );

  return {
    plan,
    loading,
    error,
    conflictVersion,
    fetchPlan,
    createPlan,
    lockPlan,
    startReconciliation,
    submitReconciliation,
    carryForward,
    clearError,
  };
}
