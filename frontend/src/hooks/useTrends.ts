/**
 * Hook for cross-week IC trend analytics.
 *
 * Fetches rolling-window planning metrics from GET /users/me/trends.
 * The panel is optional and non-blocking — errors are surfaced as
 * human-readable strings rather than thrown exceptions.
 */
import { useState, useCallback } from "react";
import type { TrendsResponse } from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";

export interface UseTrendsResult {
  /** Trend data, or null if not yet loaded. */
  trends: TrendsResponse | null;
  /** True while the request is in-flight. */
  loading: boolean;
  /** Human-readable error message, or null. */
  error: string | null;
  /** Fetch (or re-fetch) trend data for the given rolling window size. */
  fetchTrends: (weeks?: number) => Promise<void>;
  /** Clear any error without re-fetching. */
  clearError: () => void;
}

/**
 * Fetches cross-week planning trend data for the authenticated IC.
 *
 * Gated by the `icTrends` feature flag: returns immediately without
 * making a network request when the flag is disabled.
 */
export function useTrends(): UseTrendsResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [trends, setTrends] = useState<TrendsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  const fetchTrends = useCallback(
    async (weeks?: number) => {
      if (!flags.icTrends) {
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/users/me/trends", {
          params: {
            query: weeks !== undefined ? { weeks } : {},
          },
        });

        if (resp.data) {
          setTrends(resp.data as TrendsResponse);
        } else {
          const err = resp.error as { error?: { message?: string } } | undefined;
          setError(err?.error?.message ?? `Request failed (${String(resp.response.status)})`);
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [client, flags.icTrends],
  );

  return { trends, loading, error, fetchTrends, clearError };
}
