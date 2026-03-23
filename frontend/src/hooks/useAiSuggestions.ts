/**
 * Hook for AI-assisted RCDO suggestions and reconciliation drafts.
 *
 * All AI outputs are clearly labeled as suggestions. The user can
 * accept, edit, or ignore them without blocking the manual workflow.
 *
 * Optimization (step-11):
 *   - fetchSuggestions now holds an AbortController ref so that any
 *     in-flight /ai/suggest-rcdo POST is cancelled when a new debounce
 *     fires (rapid title edits) or when the component unmounts.
 *   - A useEffect cleanup aborts the pending request and clears the
 *     timeout on unmount to avoid stale-response overwrites.
 */
import { useState, useCallback, useRef, useEffect } from "react";
import type {
  RcdoSuggestion,
  SuggestRcdoResponse,
  DraftReconciliationResponse,
  ReconciliationDraftItem,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";

export interface UseAiSuggestionsResult {
  /** Current RCDO suggestions (may be empty). */
  suggestions: RcdoSuggestion[];
  /** AI suggestion status: 'idle' | 'loading' | 'ok' | 'unavailable'. */
  suggestStatus: AiRequestStatus;
  /** Fetch RCDO suggestions for a commitment title/description. */
  fetchSuggestions: (title: string, description?: string) => Promise<void>;
  /** Clear current suggestions. */
  clearSuggestions: () => void;

  /** Current reconciliation draft items (beta). */
  draftItems: ReconciliationDraftItem[];
  /** AI draft status. */
  draftStatus: AiRequestStatus;
  /** Fetch reconciliation draft for a plan (beta, behind flag). */
  fetchDraft: (planId: string) => Promise<void>;
  /** Clear draft items. */
  clearDraft: () => void;
}

export type AiRequestStatus = "idle" | "loading" | "ok" | "unavailable" | "rate_limited";

/** Minimum title length before triggering AI suggestions. */
const MIN_TITLE_LENGTH = 5;

/** Debounce delay in milliseconds for suggestion fetches. */
const DEBOUNCE_MS = 500;

export function useAiSuggestions(): UseAiSuggestionsResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [suggestions, setSuggestions] = useState<RcdoSuggestion[]>([]);
  const [suggestStatus, setSuggestStatus] = useState<AiRequestStatus>("idle");
  const [draftItems, setDraftItems] = useState<ReconciliationDraftItem[]>([]);
  const [draftStatus, setDraftStatus] = useState<AiRequestStatus>("idle");

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** AbortController for the most-recently-fired /ai/suggest-rcdo request. */
  const abortControllerRef = useRef<AbortController | null>(null);

  // Cleanup on unmount: cancel any pending debounce timer and in-flight request.
  useEffect(() => {
    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
        debounceRef.current = null;
      }
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    };
  }, []);

  const clearSuggestions = useCallback(() => {
    setSuggestions([]);
    setSuggestStatus("idle");
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
      debounceRef.current = null;
    }
    // Also abort any request that was already dispatched
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
  }, []);

  const fetchSuggestions = useCallback(
    async (title: string, description?: string) => {
      if (!flags.suggestRcdo) {
        return;
      }

      if (title.length < MIN_TITLE_LENGTH) {
        setSuggestions([]);
        setSuggestStatus("idle");
        if (debounceRef.current) {
          clearTimeout(debounceRef.current);
          debounceRef.current = null;
        }
        if (abortControllerRef.current) {
          abortControllerRef.current.abort();
          abortControllerRef.current = null;
        }
        return;
      }

      // Debounce: cancel previous timer and abort previous in-flight request
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }

      debounceRef.current = setTimeout(async () => {
        // Create a fresh AbortController for this request
        const controller = new AbortController();
        abortControllerRef.current = controller;

        setSuggestStatus("loading");
        try {
          const resp = await client.POST("/ai/suggest-rcdo", {
            body: { title, description },
            // openapi-fetch FetchOptions extends RequestInit, so signal is typed.
            signal: controller.signal,
          });

          // If this controller has already been superseded/aborted, discard result
          if (controller.signal.aborted) {
            return;
          }

          if (resp.response.status === 429) {
            setSuggestStatus("rate_limited");
            setSuggestions([]);
            return;
          }

          if (resp.data) {
            const data = resp.data as SuggestRcdoResponse;
            if (data.status === "unavailable") {
              setSuggestStatus("unavailable");
              setSuggestions([]);
            } else {
              setSuggestStatus("ok");
              setSuggestions(data.suggestions);
            }
          } else {
            setSuggestStatus("unavailable");
            setSuggestions([]);
          }
        } catch (err) {
          // Ignore AbortError — it means a newer request superseded this one
          if (err instanceof Error && err.name === "AbortError") {
            return;
          }
          setSuggestStatus("unavailable");
          setSuggestions([]);
        } finally {
          if (abortControllerRef.current === controller) {
            abortControllerRef.current = null;
          }
        }
      }, DEBOUNCE_MS);
    },
    [client, flags.suggestRcdo],
  );

  const clearDraft = useCallback(() => {
    setDraftItems([]);
    setDraftStatus("idle");
  }, []);

  const fetchDraft = useCallback(
    async (planId: string) => {
      if (!flags.draftReconciliation) {
        return;
      }

      setDraftStatus("loading");
      try {
        const resp = await client.POST("/ai/draft-reconciliation", {
          body: { planId },
        });

        if (resp.response.status === 429) {
          setDraftStatus("rate_limited");
          setDraftItems([]);
          return;
        }

        if (resp.data) {
          const data = resp.data as DraftReconciliationResponse;
          if (data.status === "unavailable") {
            setDraftStatus("unavailable");
            setDraftItems([]);
          } else {
            setDraftStatus("ok");
            setDraftItems(data.drafts);
          }
        } else {
          setDraftStatus("unavailable");
          setDraftItems([]);
        }
      } catch {
        setDraftStatus("unavailable");
        setDraftItems([]);
      }
    },
    [client, flags.draftReconciliation],
  );

  return {
    suggestions,
    suggestStatus,
    fetchSuggestions,
    clearSuggestions,
    draftItems,
    draftStatus,
    fetchDraft,
    clearDraft,
  };
}
