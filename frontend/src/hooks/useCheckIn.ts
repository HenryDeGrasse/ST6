/**
 * Hook for the Quick Daily Check-In feature (Wave 2).
 *
 * Wraps the two check-in API endpoints:
 * - POST /commits/{commitId}/check-in  — append a new status micro-update
 * - GET  /commits/{commitId}/check-ins — fetch the full append-only history
 *
 * History is cached locally after each fetch or successful add so the UI
 * can re-render without an extra round-trip.
 */
import { useState, useCallback, useRef } from "react";
import type { CheckInEntry, CheckInRequest, CheckInHistoryResponse, ApiErrorResponse } from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";

export interface UseCheckInResult {
  /** Ordered (oldest first) check-in entries for the most recently fetched commit. */
  entries: CheckInEntry[];
  /** True while any API call is in flight. */
  loading: boolean;
  /** Latest error message, or null when idle. */
  error: string | null;
  /**
   * POST a new check-in entry for the given commit.
   * Returns the created entry on success, or null on failure.
   * On success the new entry is appended to `entries` immediately.
   */
  addCheckIn: (commitId: string, req: CheckInRequest) => Promise<CheckInEntry | null>;
  /**
   * GET the full check-in history for the given commit and store in `entries`.
   */
  fetchCheckIns: (commitId: string) => Promise<void>;
  /** Clear cached entries (call when closing the check-in panel). */
  clearEntries: () => void;
  /** Clear the error banner. */
  clearError: () => void;
}

export function useCheckIn(): UseCheckInResult {
  const client = useApiClient();
  const [entries, setEntries] = useState<CheckInEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const activeCommitIdRef = useRef<string | null>(null);

  const clearEntries = useCallback(() => {
    activeCommitIdRef.current = null;
    setEntries([]);
    setLoading(false);
  }, []);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  const fetchCheckIns = useCallback(
    async (commitId: string) => {
      activeCommitIdRef.current = commitId;
      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/commits/{commitId}/check-ins", {
          params: { path: { commitId } },
        });
        if (activeCommitIdRef.current !== commitId) {
          return;
        }
        if (resp.data) {
          const data = resp.data as CheckInHistoryResponse;
          setEntries(data.entries);
        } else {
          const err = resp.error as ApiErrorResponse | undefined;
          setError(err?.error?.message ?? `Request failed (${String(resp.response.status)})`);
        }
      } catch (e) {
        if (activeCommitIdRef.current === commitId) {
          setError(e instanceof Error ? e.message : "Network error");
        }
      } finally {
        if (activeCommitIdRef.current === commitId) {
          setLoading(false);
        }
      }
    },
    [client],
  );

  const addCheckIn = useCallback(
    async (commitId: string, req: CheckInRequest): Promise<CheckInEntry | null> => {
      activeCommitIdRef.current = commitId;
      setLoading(true);
      setError(null);
      try {
        const resp = await client.POST("/commits/{commitId}/check-in", {
          params: { path: { commitId } },
          body: req,
        });
        if (resp.data) {
          const entry = resp.data as CheckInEntry;
          if (activeCommitIdRef.current === commitId) {
            setEntries((prev) => [...prev, entry]);
          }
          return entry;
        }
        if (activeCommitIdRef.current === commitId) {
          const err = resp.error as ApiErrorResponse | undefined;
          setError(err?.error?.message ?? `Request failed (${String(resp.response.status)})`);
        }
        return null;
      } catch (e) {
        if (activeCommitIdRef.current === commitId) {
          setError(e instanceof Error ? e.message : "Network error");
        }
        return null;
      } finally {
        if (activeCommitIdRef.current === commitId) {
          setLoading(false);
        }
      }
    },
    [client],
  );

  return { entries, loading, error, addCheckIn, fetchCheckIns, clearEntries, clearError };
}
