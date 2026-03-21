/**
 * Hook for fetching the current user's model profile (Phase 1).
 *
 * The endpoint is defined in the OpenAPI fragment and is NOT generated into
 * the typed client. Raw fetch() is used so that no type generation step is
 * needed for this new path:
 *   - GET /api/v1/users/me/profile  — user model snapshot / profile panel
 *
 * The base URL still comes from ApiContext so this hook works with local,
 * proxied, and fully qualified API hosts. The Authorization header is built
 * the same way as the openapi-fetch middleware in api/client.ts: dev/stub
 * tokens are re-encoded as structured dev tokens; real JWTs are forwarded
 * as-is.
 */
import { useState, useCallback } from "react";
import { useApiBaseUrl } from "../api/ApiContext.js";
import { buildDevToken } from "../api/client.js";
import { useAuth } from "../context/AuthContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";

export interface PerformanceProfile {
  estimationAccuracy: number;
  completionReliability: number;
  avgCommitsPerWeek: number;
  avgCarryForwardPerWeek: number;
  topCategories: string[];
  categoryCompletionRates: Record<string, number>;
  priorityCompletionRates: Record<string, number>;
}

export interface UserPreferences {
  typicalPriorityPattern: string;
  recurringCommitTitles: string[];
  avgCheckInsPerWeek: number;
  preferredUpdateDays: string[];
}

export interface UserTrends {
  strategicAlignmentTrend: string;
  completionTrend: string;
  carryForwardTrend: string;
}

export interface UserProfileData {
  userId: string;
  weeksAnalyzed: number;
  performanceProfile: PerformanceProfile | null;
  preferences: UserPreferences | null;
  trends: UserTrends | null;
}

export interface UseUserProfileResult {
  /** Profile data, or null if not yet loaded. */
  profile: UserProfileData | null;
  /** True while the request is in-flight. */
  loading: boolean;
  /** Human-readable error message, or null. */
  error: string | null;
  /** Fetch (or re-fetch) the current user's profile. */
  fetchProfile: () => Promise<void>;
  /** Clear any error without re-fetching. */
  clearError: () => void;
}

/**
 * Fetches the authenticated user's model profile snapshot.
 *
 * Gated by the `userProfile` feature flag: returns immediately without
 * making a network request when the flag is disabled.
 */
export function useUserProfile(): UseUserProfileResult {
  const baseUrl = useApiBaseUrl();
  const { getToken, user } = useAuth();
  const flags = useFeatureFlags();

  const [profile, setProfile] = useState<UserProfileData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  const fetchProfile = useCallback(async () => {
    if (!flags.userProfile) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const rawToken = getToken();
      const isDevToken = rawToken.startsWith("dev-") || rawToken.startsWith("stub-");
      const token = isDevToken ? buildDevToken(user) : rawToken;

      const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");
      const response = await fetch(`${normalizedBaseUrl}/users/me/profile`, {
        method: "GET",
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        const errData = (await response.json().catch(() => null)) as {
          error?: { message?: string };
        } | null;
        setError(errData?.error?.message ?? `Request failed (${String(response.status)})`);
        return;
      }

      const data = (await response.json()) as UserProfileData;
      setProfile(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  }, [baseUrl, flags.userProfile, getToken, user]);

  return { profile, loading, error, fetchProfile, clearError };
}
