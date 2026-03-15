import { useCallback, useState } from "react";
import type {
  ManagerInsightItem,
  ManagerInsightsResponse,
} from "@weekly-commitments/contracts";
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

/**
 * Hook for beta AI manager insight summaries.
 */
export function useAiManagerInsights(): UseAiManagerInsightsResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [headline, setHeadline] = useState<string | null>(null);
  const [insights, setInsights] = useState<ManagerInsightItem[]>([]);
  const [status, setStatus] = useState<AiRequestStatus>("idle");

  const clearInsights = useCallback(() => {
    setHeadline(null);
    setInsights([]);
    setStatus("idle");
  }, []);

  const fetchInsights = useCallback(
    async (weekStart: string) => {
      if (!flags.managerInsights) {
        return;
      }

      setStatus("loading");
      try {
        const resp = await client.POST("/ai/manager-insights", {
          body: { weekStart },
        });

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
