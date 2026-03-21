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
 */
import { useState, useCallback } from "react";
import type { ApiErrorResponse, CheckInEntry } from "@weekly-commitments/contracts";
import { useApiBaseUrl } from "../api/ApiContext.js";
import { buildDevToken } from "../api/client.js";
import { useAuth } from "../context/AuthContext.js";

export interface QuickUpdateItem {
  commitId: string;
  status: string;
  note: string;
}

export interface CheckInOptionItem {
  text: string;
  source: string;
}

export interface CheckInOptionsResult {
  status: string;
  statusOptions: string[];
  progressOptions: CheckInOptionItem[];
}

export interface QuickUpdateResult {
  updatedCount: number;
  entries: CheckInEntry[];
}

export interface UseQuickUpdateResult {
  loading: boolean;
  error: string | null;
  submitBatchUpdate: (planId: string, updates: QuickUpdateItem[]) => Promise<QuickUpdateResult | null>;
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
        });

        if (!response.ok) {
          const errData = (await response.json().catch(() => null)) as ApiErrorResponse | null;
          setError(extractErrorMessage(errData, response));
          return null;
        }

        return (await response.json()) as TResponse;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return null;
      } finally {
        setLoading(false);
      }
    },
    [baseUrl, getAuthToken],
  );

  const submitBatchUpdate = useCallback(
    async (planId: string, updates: QuickUpdateItem[]): Promise<QuickUpdateResult | null> =>
      postJson<QuickUpdateResult>(`/plans/${encodeURIComponent(planId)}/quick-update`, { updates }),
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
