/**
 * Hooks for weekly commit CRUD and actuals updates.
 */
import { useState, useCallback } from "react";
import type {
  WeeklyCommit,
  WeeklyCommitActual,
  CreateCommitRequest,
  UpdateCommitRequest,
  UpdateActualRequest,
  ApiErrorResponse,
  WeeklyCommitmentsApiComponents,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";

/** Generated body type for createCommit (description has a default). */
type GenCreateBody = WeeklyCommitmentsApiComponents["schemas"]["CreateCommitRequest"];
/** Generated body type for updateCommit. */
type GenUpdateBody = WeeklyCommitmentsApiComponents["schemas"]["UpdateCommitRequest"];
/** Generated body type for updateActual. */
type GenActualBody = WeeklyCommitmentsApiComponents["schemas"]["UpdateActualRequest"];

export interface UseCommitsResult {
  commits: WeeklyCommit[];
  loading: boolean;
  error: string | null;
  conflictVersion: number | null;
  fetchCommits: (planId: string) => Promise<void>;
  createCommit: (planId: string, req: CreateCommitRequest) => Promise<WeeklyCommit | null>;
  updateCommit: (commitId: string, version: number, req: UpdateCommitRequest) => Promise<WeeklyCommit | null>;
  deleteCommit: (commitId: string) => Promise<boolean>;
  updateActual: (commitId: string, version: number, req: UpdateActualRequest) => Promise<WeeklyCommitActual | null>;
  resetCommits: () => void;
  clearError: () => void;
}

export function useCommits(): UseCommitsResult {
  const client = useApiClient();
  const [commits, setCommits] = useState<WeeklyCommit[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflictVersion, setConflictVersion] = useState<number | null>(null);

  const clearError = useCallback(() => {
    setError(null);
    setConflictVersion(null);
  }, []);

  const resetCommits = useCallback(() => {
    setCommits([]);
  }, []);

  const extractError = useCallback(
    (resp: { error?: unknown; response: Response }): string => {
      const err = resp.error as ApiErrorResponse | undefined;
      if (err?.error?.message) return err.error.message;
      return `Request failed (${String(resp.response.status)})`;
    },
    [],
  );

  const fetchCommits = useCallback(
    async (planId: string) => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/plans/{planId}/commits", {
          params: { path: { planId } },
        });
        if (resp.data) {
          setCommits(resp.data as WeeklyCommit[]);
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

  const createCommit = useCallback(
    async (planId: string, req: CreateCommitRequest): Promise<WeeklyCommit | null> => {
      setLoading(true);
      setError(null);
      try {
        const body: GenCreateBody = {
          title: req.title,
          description: req.description ?? "",
          chessPriority: req.chessPriority ?? undefined,
          category: req.category ?? undefined,
          outcomeId: req.outcomeId ?? undefined,
          nonStrategicReason: req.nonStrategicReason ?? undefined,
          expectedResult: req.expectedResult ?? "",
          confidence: req.confidence ?? undefined,
          tags: req.tags ?? [],
        };
        const resp = await client.POST("/plans/{planId}/commits", {
          params: { path: { planId } },
          body,
        });
        if (resp.data) {
          const created = resp.data as WeeklyCommit;
          setCommits((prev) => [...prev, created]);
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

  const updateCommit = useCallback(
    async (commitId: string, version: number, req: UpdateCommitRequest): Promise<WeeklyCommit | null> => {
      setLoading(true);
      setError(null);
      setConflictVersion(null);
      try {
        const body: GenUpdateBody = {};
        if (req.title !== undefined) body.title = req.title;
        if (req.description !== undefined) body.description = req.description;
        if (req.chessPriority !== undefined) body.chessPriority = req.chessPriority ?? undefined;
        if (req.category !== undefined) body.category = req.category ?? undefined;
        if (req.outcomeId !== undefined) body.outcomeId = req.outcomeId ?? undefined;
        if (req.nonStrategicReason !== undefined) body.nonStrategicReason = req.nonStrategicReason ?? undefined;
        if (req.expectedResult !== undefined) body.expectedResult = req.expectedResult;
        if (req.confidence !== undefined) body.confidence = req.confidence ?? undefined;
        if (req.tags !== undefined) body.tags = req.tags;
        if (req.progressNotes !== undefined) body.progressNotes = req.progressNotes;
        const resp = await client.PATCH("/commits/{commitId}", {
          params: {
            path: { commitId },
            header: { "If-Match": version },
          },
          body,
        });
        if (resp.data) {
          const updated = resp.data as WeeklyCommit;
          setCommits((prev) => prev.map((c) => (c.id === commitId ? updated : c)));
          return updated;
        }
        if (resp.response.status === 409) {
          const body = resp.error as ApiErrorResponse | undefined;
          const cv = body?.error?.details?.[0]?.currentVersion;
          if (typeof cv === "number") setConflictVersion(cv);
          setError("Conflict: this commit was modified. Refresh to see latest.");
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
    [client, extractError],
  );

  const deleteCommit = useCallback(
    async (commitId: string): Promise<boolean> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.DELETE("/commits/{commitId}", {
          params: { path: { commitId } },
        });
        if (resp.response.ok) {
          setCommits((prev) => prev.filter((c) => c.id !== commitId));
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

  const updateActual = useCallback(
    async (commitId: string, version: number, req: UpdateActualRequest): Promise<WeeklyCommitActual | null> => {
      setLoading(true);
      setError(null);
      setConflictVersion(null);
      try {
        const actualBody: GenActualBody = {
          actualResult: req.actualResult,
          completionStatus: req.completionStatus,
          deltaReason: req.deltaReason ?? undefined,
          timeSpent: req.timeSpent ?? undefined,
        };
        const resp = await client.PATCH("/commits/{commitId}/actual", {
          params: {
            path: { commitId },
            header: { "If-Match": version },
          },
          body: actualBody,
        });
        if (resp.data) {
          return resp.data as WeeklyCommitActual;
        }
        if (resp.response.status === 409) {
          const body = resp.error as ApiErrorResponse | undefined;
          const cv = body?.error?.details?.[0]?.currentVersion;
          if (typeof cv === "number") setConflictVersion(cv);
          setError("Conflict: commit was modified. Refresh and try again.");
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
    [client, extractError],
  );

  return {
    commits,
    loading,
    error,
    conflictVersion,
    fetchCommits,
    createCommit,
    updateCommit,
    deleteCommit,
    updateActual,
    resetCommits,
    clearError,
  };
}
