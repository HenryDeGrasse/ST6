import { useCallback, useState } from "react";
import type {
  ApiErrorResponse,
  ExecutiveBriefingResponse,
  ExecutiveDashboardResponse,
  ExecutiveDashboardUnavailableResponse,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import type { AiRequestStatus } from "./useAiSuggestions.js";

export interface UseExecutiveDashboardResult {
  dashboard: ExecutiveDashboardResponse | null;
  briefing: ExecutiveBriefingResponse | null;
  dashboardStatus: AiRequestStatus;
  briefingStatus: AiRequestStatus;
  errorDashboard: string | null;
  errorBriefing: string | null;
  fetchDashboard: (weekStart?: string) => Promise<void>;
  fetchBriefing: (weekStart: string) => Promise<void>;
  clearErrors: () => void;
}

export function useExecutiveDashboard(): UseExecutiveDashboardResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [dashboard, setDashboard] = useState<ExecutiveDashboardResponse | null>(null);
  const [briefing, setBriefing] = useState<ExecutiveBriefingResponse | null>(null);
  const [dashboardStatus, setDashboardStatus] = useState<AiRequestStatus>("idle");
  const [briefingStatus, setBriefingStatus] = useState<AiRequestStatus>("idle");
  const [errorDashboard, setErrorDashboard] = useState<string | null>(null);
  const [errorBriefing, setErrorBriefing] = useState<string | null>(null);

  const extractError = useCallback((resp: { error?: unknown; response: Response }): string => {
    const err = resp.error as ApiErrorResponse | undefined;
    return err?.error?.message ?? `Request failed (${String(resp.response.status)})`;
  }, []);

  const fetchDashboard = useCallback(
    async (weekStart?: string) => {
      if (!flags.executiveDashboard) {
        return;
      }

      setDashboardStatus("loading");
      setErrorDashboard(null);
      try {
        const resp = await client.GET("/executive/strategic-health", {
          params: { query: weekStart ? { weekStart } : {} },
        });
        if (resp.data) {
          const data = resp.data as ExecutiveDashboardResponse | ExecutiveDashboardUnavailableResponse;
          if ("status" in data && data.status === "unavailable") {
            setDashboardStatus("unavailable");
            setDashboard(null);
            return;
          }

          setDashboardStatus("ok");
          setDashboard(data as ExecutiveDashboardResponse);
        } else {
          setDashboardStatus("unavailable");
          setDashboard(null);
          setErrorDashboard(extractError(resp));
        }
      } catch (e) {
        setDashboardStatus("unavailable");
        setDashboard(null);
        setErrorDashboard(e instanceof Error ? e.message : "Network error");
      }
    },
    [client, extractError, flags.executiveDashboard],
  );

  const fetchBriefing = useCallback(
    async (weekStart: string) => {
      if (!flags.executiveDashboard) {
        return;
      }

      setBriefingStatus("loading");
      setErrorBriefing(null);
      try {
        const resp = await client.POST("/ai/executive-briefing", {
          body: { weekStart },
        });

        if (resp.response.status === 429) {
          setBriefingStatus("rate_limited");
          setBriefing(null);
          return;
        }

        if (resp.data) {
          const data = resp.data as ExecutiveBriefingResponse;
          if (data.status === "unavailable") {
            setBriefingStatus("unavailable");
            setBriefing(null);
            return;
          }

          setBriefingStatus("ok");
          setBriefing(data);
        } else {
          setBriefingStatus("unavailable");
          setBriefing(null);
          setErrorBriefing(extractError(resp));
        }
      } catch (e) {
        setBriefingStatus("unavailable");
        setBriefing(null);
        setErrorBriefing(e instanceof Error ? e.message : "Network error");
      }
    },
    [client, extractError, flags.executiveDashboard],
  );

  const clearErrors = useCallback(() => {
    setErrorDashboard(null);
    setErrorBriefing(null);
  }, []);

  return {
    dashboard,
    briefing,
    dashboardStatus,
    briefingStatus,
    errorDashboard,
    errorBriefing,
    fetchDashboard,
    fetchBriefing,
    clearErrors,
  };
}
