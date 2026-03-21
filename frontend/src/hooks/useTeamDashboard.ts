/**
 * Hook for manager team dashboard data.
 */
import { useState, useCallback } from "react";
import type { TeamSummaryResponse, RcdoRollupResponse, ApiErrorResponse } from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";

export interface TeamDashboardFilters {
  state?: string;
  outcomeId?: string;
  incomplete?: boolean;
  nonStrategic?: boolean;
  priority?: string;
  category?: string;
}

export interface UseTeamDashboardResult {
  summary: TeamSummaryResponse | null;
  rollup: RcdoRollupResponse | null;
  loading: boolean;
  error: string | null;
  fetchSummary: (weekStart: string, page?: number, size?: number, filters?: TeamDashboardFilters) => Promise<void>;
  fetchRollup: (weekStart: string) => Promise<void>;
  clearError: () => void;
}

export function useTeamDashboard(): UseTeamDashboardResult {
  const client = useApiClient();
  const [summary, setSummary] = useState<TeamSummaryResponse | null>(null);
  const [rollup, setRollup] = useState<RcdoRollupResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  const extractError = useCallback((resp: { error?: unknown; response: Response }): string => {
    const err = resp.error as ApiErrorResponse | undefined;
    if (err?.error?.message) return err.error.message;
    return `Request failed (${String(resp.response.status)})`;
  }, []);

  const fetchSummary = useCallback(
    async (weekStart: string, page = 0, size = 20, filters: TeamDashboardFilters = {}) => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/weeks/{weekStart}/team/summary", {
          params: {
            path: { weekStart },
            query: {
              page,
              size,
              state: filters.state as never,
              outcomeId: filters.outcomeId,
              incomplete: filters.incomplete,
              nonStrategic: filters.nonStrategic,
              priority: filters.priority as never,
              category: filters.category as never,
            },
          },
        });
        if (resp.data) {
          setSummary(resp.data as TeamSummaryResponse);
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

  const fetchRollup = useCallback(
    async (weekStart: string) => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/weeks/{weekStart}/team/rcdo-rollup", {
          params: { path: { weekStart } },
        });
        if (resp.data) {
          setRollup(resp.data as RcdoRollupResponse);
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

  return { summary, rollup, loading, error, fetchSummary, fetchRollup, clearError };
}
