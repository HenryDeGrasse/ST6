/**
 * Hook for AI next-work suggestions (Wave 2, Phase 1).
 *
 * Calls POST /ai/suggest-next-work to fetch data-driven suggestions for
 * the current week, and POST /ai/suggestion-feedback to record user
 * actions (ACCEPT, DEFER, DECLINE).
 *
 * Gated by the `suggestNextWork` feature flag.  When disabled, fetch
 * returns immediately without a network request.
 */
import { useState, useCallback, useRef } from "react";
import type {
  NextWorkSuggestion,
  SuggestionFeedbackRequest,
  SuggestNextWorkResponse,
  SuggestionFeedbackResponse,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import type { AiRequestStatus } from "./useAiSuggestions.js";

export interface UseNextWorkSuggestionsResult {
  /** Current suggestion list (empty when not yet fetched or unavailable). */
  suggestions: NextWorkSuggestion[];
  /** Current fetch status. */
  status: AiRequestStatus;
  /**
   * Fetch next-work suggestions for the given week (ISO Monday date).
   * Defaults to the current week when omitted.
   */
  fetchSuggestions: (weekStart?: string) => Promise<void>;
  /**
   * Submit user feedback for a suggestion.
   * Returns true on success, false on failure.
   */
  submitFeedback: (req: SuggestionFeedbackRequest) => Promise<boolean>;
  /** Dismiss a suggestion from the local list without a network call. */
  dismissSuggestion: (suggestionId: string) => void;
  /** Clear all suggestions and reset status. */
  clearSuggestions: () => void;
}

/**
 * Fetches AI-generated next-work suggestions and handles user feedback.
 *
 * Phase 1: pure data-driven suggestions (carry-forward items and RCDO
 * coverage gaps). No LLM required on the backend.
 */
export function useNextWorkSuggestions(): UseNextWorkSuggestionsResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [suggestions, setSuggestions] = useState<NextWorkSuggestion[]>([]);
  const [status, setStatus] = useState<AiRequestStatus>("idle");
  const requestVersionRef = useRef(0);

  const clearSuggestions = useCallback(() => {
    requestVersionRef.current += 1;
    setSuggestions([]);
    setStatus("idle");
  }, []);

  const fetchSuggestions = useCallback(
    async (weekStart?: string) => {
      if (!flags.suggestNextWork) {
        return;
      }

      const requestVersion = requestVersionRef.current + 1;
      requestVersionRef.current = requestVersion;

      setStatus("loading");
      try {
        const resp = await client.POST("/ai/suggest-next-work", {
          body: weekStart ? { weekStart } : {},
        });

        if (requestVersionRef.current !== requestVersion) {
          return;
        }

        if (resp.response.status === 429) {
          setStatus("rate_limited");
          setSuggestions([]);
          return;
        }

        if (resp.data) {
          const data = resp.data as SuggestNextWorkResponse;
          if (data.status === "unavailable") {
            setStatus("unavailable");
            setSuggestions([]);
          } else {
            setStatus("ok");
            setSuggestions(data.suggestions);
          }
        } else {
          setStatus("unavailable");
          setSuggestions([]);
        }
      } catch {
        if (requestVersionRef.current !== requestVersion) {
          return;
        }
        setStatus("unavailable");
        setSuggestions([]);
      }
    },
    [client, flags.suggestNextWork],
  );

  const submitFeedback = useCallback(
    async (req: SuggestionFeedbackRequest): Promise<boolean> => {
      if (!flags.suggestNextWork) {
        return false;
      }

      try {
        const resp = await client.POST("/ai/suggestion-feedback", {
          body: req,
        });

        if (resp.data) {
          const data = resp.data as SuggestionFeedbackResponse;
          return data.status === "ok";
        }
        return false;
      } catch {
        return false;
      }
    },
    [client, flags.suggestNextWork],
  );

  const dismissSuggestion = useCallback((suggestionId: string) => {
    setSuggestions((prev) => prev.filter((s) => s.suggestionId !== suggestionId));
  }, []);

  return {
    suggestions,
    status,
    fetchSuggestions,
    submitFeedback,
    dismissSuggestion,
    clearSuggestions,
  };
}
