/**
 * Hook for beta AI manager insight summaries.
 *
 * Optimization (step-11):
 *   Added request versioning (same pattern as usePlanQualityCheck) so that
 *   stale /ai/manager-insights responses are silently discarded when weekStart
 *   changes rapidly (e.g. quick week navigation on the team dashboard).
 */
import { useCallback, useRef, useState } from "react";
import type { ManagerInsightItem, ManagerInsightsResponse } from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import type { AiRequestStatus } from "./useAiSuggestions.js";

export interface UseAiManagerInsightsResult {
  headline: string | null;
  insights: ManagerInsightItem[];
  status: AiRequestStatus;
  fetchInsights: (weekStart: string) => Promise<void>;
  clearInsights: () => void;
}

export function useAiManagerInsights(): UseAiManagerInsightsResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [headline, setHeadline] = useState<string | null>(null);
  const [insights, setInsights] = useState<ManagerInsightItem[]>([]);
  const [status, setStatus] = useState<AiRequestStatus>("idle");

  /**
   * Monotonically-increasing counter used to detect stale responses.
   * Incremented at the start of each fetch and checked after the await.
   * If the ref value has moved on by the time the response arrives, the
   * result is discarded (same pattern as usePlanQualityCheck).
   */
  const requestVersionRef = useRef(0);

  const clearInsights = useCallback(() => {
    // Bump the version so any in-flight request's response is discarded.
    requestVersionRef.current += 1;
    setHeadline(null);
    setInsights([]);
    setStatus("idle");
  }, []);

  const fetchInsights = useCallback(
    async (weekStart: string) => {
      if (!flags.managerInsights) {
        return;
      }

      const requestVersion = requestVersionRef.current + 1;
      requestVersionRef.current = requestVersion;

      setStatus("loading");
      try {
        const resp = await client.POST("/ai/manager-insights", {
          body: { weekStart },
        });

        // Discard the result if a newer request has already started
        if (requestVersionRef.current !== requestVersion) {
          return;
        }

        if (resp.response.status === 429) {
          setStatus("rate_limited");
          setHeadline(null);
          setInsights([]);
          return;
        }

        if (resp.data) {
          const data = resp.data as ManagerInsightsResponse;
          if (data.status === "unavailable") {
            setStatus("unavailable");
            setHeadline(null);
            setInsights([]);
          } else {
            setStatus("ok");
            setHeadline(data.headline);
            setInsights(data.insights);
          }
        } else {
          setStatus("unavailable");
          setHeadline(null);
          setInsights([]);
        }
      } catch {
        // Only surface the error state if this is still the current request
        if (requestVersionRef.current !== requestVersion) {
          return;
        }
        setStatus("unavailable");
        setHeadline(null);
        setInsights([]);
      }
    },
    [client, flags.managerInsights],
  );

  return {
    headline,
    insights,
    status,
    fetchInsights,
    clearInsights,
  };
}
