import { useCallback, useState } from "react";
import type {
  ApplyTeamPlanSuggestionRequest,
  ApplyTeamPlanSuggestionResponse,
  TeamPlanSuggestionResponse,
  TeamPlanSuggestionUnavailableResponse,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import type { AiRequestStatus } from "./useAiSuggestions.js";

export interface UsePlanningCopilotResult {
  suggestion: TeamPlanSuggestionResponse | null;
  applyResult: ApplyTeamPlanSuggestionResponse | null;
  suggestionStatus: AiRequestStatus;
  applyStatus: AiRequestStatus;
  error: string | null;
  fetchSuggestion: (weekStart: string) => Promise<void>;
  applySuggestion: (request: ApplyTeamPlanSuggestionRequest) => Promise<ApplyTeamPlanSuggestionResponse | null>;
  clearError: () => void;
  clearApplyResult: () => void;
}

export function usePlanningCopilot(): UsePlanningCopilotResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [suggestion, setSuggestion] = useState<TeamPlanSuggestionResponse | null>(null);
  const [applyResult, setApplyResult] = useState<ApplyTeamPlanSuggestionResponse | null>(null);
  const [suggestionStatus, setSuggestionStatus] = useState<AiRequestStatus>("idle");
  const [applyStatus, setApplyStatus] = useState<AiRequestStatus>("idle");
  const [error, setError] = useState<string | null>(null);

  const fetchSuggestion = useCallback(
    async (weekStart: string) => {
      if (!flags.planningCopilot) {
        return;
      }

      setSuggestionStatus("loading");
      setError(null);
      setApplyResult(null);
      try {
        const resp = await client.POST("/ai/team-plan-suggestion", {
          body: { weekStart },
        });

        if (resp.response.status === 429) {
          setSuggestionStatus("rate_limited");
          setSuggestion(null);
          return;
        }

        if (resp.data) {
          const data = resp.data as TeamPlanSuggestionResponse | TeamPlanSuggestionUnavailableResponse;
          if ("status" in data && data.status === "unavailable") {
            setSuggestionStatus("unavailable");
            setSuggestion(null);
            return;
          }

          setSuggestionStatus("ok");
          setSuggestion(data as TeamPlanSuggestionResponse);
          return;
        }

        setSuggestionStatus("unavailable");
        setSuggestion(null);
      } catch (e) {
        setSuggestionStatus("unavailable");
        setSuggestion(null);
        setError(e instanceof Error ? e.message : "Network error");
      }
    },
    [client, flags.planningCopilot],
  );

  const applySuggestion = useCallback(
    async (request: ApplyTeamPlanSuggestionRequest): Promise<ApplyTeamPlanSuggestionResponse | null> => {
      if (!flags.planningCopilot) {
        return null;
      }

      setApplyStatus("loading");
      setError(null);
      setApplyResult(null);
      try {
        const resp = await client.POST("/ai/team-plan-suggestion/apply", {
          body: request,
        });

        if (resp.response.status === 429) {
          setApplyStatus("rate_limited");
          setApplyResult(null);
          return null;
        }

        if (resp.data) {
          const data = resp.data as ApplyTeamPlanSuggestionResponse | TeamPlanSuggestionUnavailableResponse;
          if ("status" in data && data.status === "unavailable" && !("members" in data)) {
            setApplyStatus("unavailable");
            setApplyResult(null);
            return null;
          }

          const applied = data as ApplyTeamPlanSuggestionResponse;
          setApplyStatus("ok");
          setApplyResult(applied);
          return applied;
        }

        setApplyStatus("unavailable");
        setApplyResult(null);
        return null;
      } catch (e) {
        setApplyStatus("unavailable");
        setApplyResult(null);
        setError(e instanceof Error ? e.message : "Network error");
        return null;
      }
    },
    [client, flags.planningCopilot],
  );

  const clearError = useCallback(() => setError(null), []);
  const clearApplyResult = useCallback(() => setApplyResult(null), []);

  return {
    suggestion,
    applyResult,
    suggestionStatus,
    applyStatus,
    error,
    fetchSuggestion,
    applySuggestion,
    clearError,
    clearApplyResult,
  };
}
