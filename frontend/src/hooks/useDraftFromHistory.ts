/**
 * Hook for the "Start My Week" draft-from-history flow (Wave 2).
 *
 * Calls POST /plans/draft-from-history to create or update a DRAFT plan
 * pre-filled with AI-suggested commits derived from the user's last 4 weeks
 * of planning history.
 *
 * The request is gated by the `startMyWeek` feature flag.
 */
import { useState, useCallback } from "react";
import type { DraftFromHistoryResponse, SuggestedCommit } from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";

export type DraftFromHistoryStatus = "idle" | "loading" | "ok" | "error" | "conflict";

export interface UseDraftFromHistoryResult {
  /** Status of the draft-from-history request. */
  status: DraftFromHistoryStatus;
  /** ID of the created/updated DRAFT plan, or null if not yet loaded. */
  planId: string | null;
  /** Suggested commits returned by the service, or empty if not yet loaded. */
  suggestedCommits: SuggestedCommit[];
  /** Human-readable error message, or null. */
  error: string | null;
  /** Execute the draft-from-history API call for the given week. */
  draftFromHistory: (weekStart: string) => Promise<DraftFromHistoryResponse | null>;
  /** Reset status, planId, commits, and error back to idle state. */
  reset: () => void;
}

/**
 * Creates a DRAFT plan pre-filled from planning history for the given week.
 *
 * Gated by the `startMyWeek` feature flag: if disabled, `draftFromHistory`
 * returns null immediately without making a network request.
 */
export function useDraftFromHistory(): UseDraftFromHistoryResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [status, setStatus] = useState<DraftFromHistoryStatus>("idle");
  const [planId, setPlanId] = useState<string | null>(null);
  const [suggestedCommits, setSuggestedCommits] = useState<SuggestedCommit[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reset = useCallback(() => {
    setStatus("idle");
    setPlanId(null);
    setSuggestedCommits([]);
    setError(null);
  }, []);

  const draftFromHistory = useCallback(
    async (weekStart: string): Promise<DraftFromHistoryResponse | null> => {
      if (!flags.startMyWeek) {
        return null;
      }

      setStatus("loading");
      setError(null);
      setPlanId(null);
      setSuggestedCommits([]);

      try {
        const resp = await client.POST("/plans/draft-from-history", {
          body: { weekStart },
        });

        if (resp.data) {
          const data = resp.data as DraftFromHistoryResponse;
          setPlanId(data.planId);
          setSuggestedCommits(data.suggestedCommits);
          setStatus("ok");
          return data;
        }

        if (resp.response.status === 409) {
          setStatus("conflict");
          setError("A plan already exists for this week and cannot be replaced.");
          return null;
        }

        const apiErr = resp.error as { error?: { message?: string } } | undefined;
        const msg = apiErr?.error?.message ?? `Request failed (${String(resp.response.status)})`;
        setError(msg);
        setStatus("error");
        return null;
      } catch (e) {
        const msg = e instanceof Error ? e.message : "Network error";
        setError(msg);
        setStatus("error");
        return null;
      }
    },
    [client, flags.startMyWeek],
  );

  return { status, planId, suggestedCommits, error, draftFromHistory, reset };
}
