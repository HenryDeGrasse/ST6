/**
 * Hook for the rapid-fire batch check-in update flow (Phase 1).
 *
 * These endpoints are defined in the OpenAPI fragment file and are NOT
 * generated into the typed client. Raw fetch() is used so that no type
 * generation step is needed for these new paths:
 *   - POST /api/v1/plans/{planId}/quick-update  — batch check-in update
 *   - POST /api/v1/ai/check-in-options          — AI-generated option suggestions
 *
 * The base URL still comes from ApiContext so this hook works with local,
 * proxied, and fully qualified API hosts. The Authorization header is built
 * the same way as the openapi-fetch middleware in api/client.ts: dev/stub
 * tokens are re-encoded as structured dev tokens; real JWTs are forwarded
 * as-is.
 *
 * Optimizations (step-12):
 *   - AbortController ref: each postJson call creates a new AbortController
 *     and stores it. The active signal is passed to fetch() so the OS-level
 *     request is actually cancelled, not just discarded.
 *   - useEffect cleanup: on unmount the active controller is aborted, which
 *     prevents "can't perform state updates on unmounted component" warnings.
 *   - AbortError is swallowed silently so unmount-driven cancellation does
 *     not surface a spurious error to the user.
 *   - Success-path response.json() is now wrapped in try/catch so a
 *     malformed body results in a human-readable error rather than an
 *     uncaught rejection.
 */
import { useState, useCallback, useRef, useEffect } from "react";
import type {
  ApiErrorResponse,
  CheckInOptionsResponse,
  QuickUpdateItem,
  QuickUpdateResponse,
} from "@weekly-commitments/contracts";
import { useApiBaseUrl } from "../api/ApiContext.js";
import { buildDevToken } from "../api/client.js";
import { useAuth } from "../context/AuthContext.js";

export type CheckInOptionsResult = CheckInOptionsResponse;

export interface UseQuickUpdateResult {
  loading: boolean;
  error: string | null;
  submitBatchUpdate: (planId: string, updates: QuickUpdateItem[]) => Promise<QuickUpdateResponse | null>;
  fetchCheckInOptions: (
    commitId: string,
    currentStatus: string,
    lastNote: string,
    daysSinceLastCheckIn: number,
  ) => Promise<CheckInOptionsResult | null>;
  clearError: () => void;
}

function joinApiUrl(baseUrl: string, path: string): string {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${normalizedBaseUrl}${normalizedPath}`;
}

function extractErrorMessage(error: ApiErrorResponse | null, response: Response): string {
  return error?.error?.message ?? `Request failed (${String(response.status)})`;
}

export function useQuickUpdate(): UseQuickUpdateResult {
  const baseUrl = useApiBaseUrl();
  const { getToken, user } = useAuth();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** The AbortController for the currently in-flight request, if any. */
  const activeControllerRef = useRef<AbortController | null>(null);

  // Abort any in-flight request when the component unmounts.
  useEffect(() => {
    return () => {
      if (activeControllerRef.current) {
        activeControllerRef.current.abort();
        activeControllerRef.current = null;
      }
    };
  }, []);

  const getAuthToken = useCallback((): string => {
    const rawToken = getToken();
    const isDevToken = rawToken.startsWith("dev-") || rawToken.startsWith("stub-");
    return isDevToken ? buildDevToken(user) : rawToken;
  }, [getToken, user]);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  const postJson = useCallback(
    async <TResponse,>(path: string, body: unknown): Promise<TResponse | null> => {
      // Abort any previously-active request before starting a new one.
      if (activeControllerRef.current) {
        activeControllerRef.current.abort();
      }
      const controller = new AbortController();
      activeControllerRef.current = controller;

      const isCurrentRequest = (): boolean => activeControllerRef.current === controller;

      setLoading(true);
      setError(null);
      try {
        const response = await fetch(joinApiUrl(baseUrl, path), {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${getAuthToken()}`,
          },
          body: JSON.stringify(body),
          signal: controller.signal,
        });

        if (!isCurrentRequest() || controller.signal.aborted) {
          return null;
        }

        if (!response.ok) {
          const errData = (await response.json().catch(() => null)) as ApiErrorResponse | null;
          if (isCurrentRequest()) {
            setError(extractErrorMessage(errData, response));
          }
          return null;
        }

        // Guard against malformed JSON in a successful response body.
        try {
          const data = (await response.json()) as TResponse;
          if (!isCurrentRequest() || controller.signal.aborted) {
            return null;
          }
          return data;
        } catch {
          if (isCurrentRequest()) {
            setError("Received an invalid response from the server.");
          }
          return null;
        }
      } catch (e) {
        // Swallow AbortError — it means the component unmounted or a new
        // request superseded this one; no user-visible error is warranted.
        if (e instanceof Error && e.name === "AbortError") {
          return null;
        }
        if (isCurrentRequest()) {
          setError(e instanceof Error ? e.message : "Network error");
        }
        return null;
      } finally {
        // Only the still-active request may clear loading / release the ref.
        if (isCurrentRequest()) {
          setLoading(false);
          activeControllerRef.current = null;
        }
      }
    },
    [baseUrl, getAuthToken],
  );

  const submitBatchUpdate = useCallback(
    async (planId: string, updates: QuickUpdateItem[]): Promise<QuickUpdateResponse | null> =>
      postJson<QuickUpdateResponse>(`/plans/${encodeURIComponent(planId)}/quick-update`, { updates }),
    [postJson],
  );

  const fetchCheckInOptions = useCallback(
    async (
      commitId: string,
      currentStatus: string,
      lastNote: string,
      daysSinceLastCheckIn: number,
    ): Promise<CheckInOptionsResult | null> =>
      postJson<CheckInOptionsResult>("/ai/check-in-options", {
        commitId,
        currentStatus,
        lastNote,
        daysSinceLastCheckIn,
      }),
    [postJson],
  );

  return {
    loading,
    error,
    submitBatchUpdate,
    fetchCheckInOptions,
    clearError,
  };
}
