/**
 * Hook for the lock-time AI plan quality nudge (Wave 1).
 *
 * Calls POST /ai/plan-quality-check to run data-driven quality checks on a
 * plan before locking.  Results are advisory — the user can always proceed
 * to lock regardless of any nudges returned.
 *
 * The request is gated by the `planQualityNudge` feature flag.
 */
import { useState, useCallback, useRef } from "react";
import type { QualityNudge, PlanQualityCheckResponse } from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import type { AiRequestStatus } from "./useAiSuggestions.js";

export interface UsePlanQualityCheckResult {
  /** Nudge items returned from the quality check (may be empty). */
  nudges: QualityNudge[];
  /** Current request status. */
  status: AiRequestStatus;
  /** Run the quality check for the given plan ID. */
  checkQuality: (planId: string) => Promise<void>;
  /** Clear nudges and reset status to idle. */
  clearNudges: () => void;
}

/**
 * Fetches quality nudges for a plan before the user locks it.
 *
 * Gated by the `planQualityNudge` feature flag: if disabled, `checkQuality`
 * returns immediately without making a network request.
 */
export function usePlanQualityCheck(): UsePlanQualityCheckResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [nudges, setNudges] = useState<QualityNudge[]>([]);
  const [status, setStatus] = useState<AiRequestStatus>("idle");
  const requestVersionRef = useRef(0);

  const clearNudges = useCallback(() => {
    requestVersionRef.current += 1;
    setNudges([]);
    setStatus("idle");
  }, []);

  const checkQuality = useCallback(
    async (planId: string) => {
      if (!flags.planQualityNudge) {
        return;
      }

      const requestVersion = requestVersionRef.current + 1;
      requestVersionRef.current = requestVersion;

      setStatus("loading");
      try {
        const resp = await client.POST("/ai/plan-quality-check", {
          body: { planId },
        });

        if (requestVersionRef.current !== requestVersion) {
          return;
        }

        if (resp.response.status === 429) {
          setStatus("rate_limited");
          setNudges([]);
          return;
        }

        if (resp.data) {
          const data = resp.data as PlanQualityCheckResponse;
          if (data.status === "unavailable") {
            setStatus("unavailable");
            setNudges([]);
          } else {
            setStatus("ok");
            setNudges(data.nudges);
          }
        } else {
          setStatus("unavailable");
          setNudges([]);
        }
      } catch {
        if (requestVersionRef.current !== requestVersion) {
          return;
        }
        setStatus("unavailable");
        setNudges([]);
      }
    },
    [client, flags.planQualityNudge],
  );

  return { nudges, status, checkQuality, clearNudges };
}
