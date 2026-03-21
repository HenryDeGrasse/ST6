/**
 * Hooks for Phase 4 capacity planning features.
 *
 * Three hooks are exported:
 * - useCapacityProfile  — fetches GET /users/me/capacity
 * - useTeamCapacity     — fetches GET /team/capacity?weekStart=X
 * - useEstimationCoaching — fetches GET /users/me/estimation-coaching?planId=X
 *
 * All hooks follow the useTrends.ts pattern exactly:
 * - use ApiContext client for typed HTTP calls
 * - gate on the relevant feature flag
 * - surface errors as human-readable strings rather than thrown exceptions
 */
import { useState, useCallback } from "react";
import { useApiClient } from "../api/ApiContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";

// ─── Inline TypeScript interfaces matching backend DTOs ───────────────────────

/** Data quality tier for a capacity profile. */
export type CapacityConfidenceLevel = "LOW" | "MEDIUM" | "HIGH";

/** Overcommitment severity level. */
export type OvercommitLevel = "NONE" | "MODERATE" | "HIGH";

/**
 * User capacity profile — returned by GET /users/me/capacity.
 * Mirrors CapacityProfileResponse in the backend.
 */
export interface CapacityProfile {
  orgId: string;
  userId: string;
  weeksAnalyzed: number;
  avgEstimatedHours: number | null;
  avgActualHours: number | null;
  estimationBias: number | null;
  realisticWeeklyCap: number | null;
  /** Raw JSON string of per-category bias breakdowns. */
  categoryBiasJson: string | null;
  /** Raw JSON string of per-priority completion statistics. */
  priorityCompletionJson: string | null;
  confidenceLevel: CapacityConfidenceLevel;
  computedAt: string | null;
}

/**
 * Capacity summary for a single team member.
 * Mirrors TeamMemberCapacity in the backend.
 */
export interface TeamMemberCapacity {
  userId: string;
  name: string | null;
  estimatedHours: number;
  adjustedEstimate: number;
  realisticCap: number | null;
  overcommitLevel: OvercommitLevel;
}

/**
 * Team capacity view for a given week — returned by GET /team/capacity.
 * Mirrors TeamCapacityResponse in the backend.
 */
export interface TeamCapacityResponse {
  weekStart: string;
  members: TeamMemberCapacity[];
}

/**
 * Per-category estimation bias insight.
 * Mirrors CategoryInsight in the backend.
 */
export interface CategoryInsight {
  category: string;
  bias: number | null;
  tip: string | null;
}

/**
 * Per-priority completion insight.
 * Mirrors PriorityInsight in the backend.
 */
export interface PriorityInsight {
  priority: string;
  completionRate: number;
  sampleSize: number;
}

/**
 * Estimation coaching for a plan — returned by GET /users/me/estimation-coaching.
 * Mirrors EstimationCoachingResponse in the backend.
 */
export interface EstimationCoachingResponse {
  thisWeekEstimated: number;
  thisWeekActual: number;
  accuracyRatio: number | null;
  overallBias: number | null;
  confidenceLevel: CapacityConfidenceLevel;
  categoryInsights: CategoryInsight[];
  priorityInsights: PriorityInsight[];
}

// ─── Hook result interfaces ───────────────────────────────────────────────────

export interface UseCapacityProfileResult {
  /** The user's capacity profile, or null if not yet loaded. */
  profile: CapacityProfile | null;
  /** True while the request is in-flight. */
  loading: boolean;
  /** Human-readable error message, or null. */
  error: string | null;
  /** Fetch (or re-fetch) the authenticated user's capacity profile. */
  fetchProfile: () => Promise<void>;
  /** Clear any error without re-fetching. */
  clearError: () => void;
}

export interface UseTeamCapacityResult {
  /** Team capacity data, or null if not yet loaded. */
  teamCapacity: TeamCapacityResponse | null;
  /** True while the request is in-flight. */
  loading: boolean;
  /** Human-readable error message, or null. */
  error: string | null;
  /** Fetch (or re-fetch) team capacity for the given week. */
  fetchTeamCapacity: (weekStart: string) => Promise<void>;
  /** Clear any error without re-fetching. */
  clearError: () => void;
}

export interface UseEstimationCoachingResult {
  /** Estimation coaching data, or null if not yet loaded. */
  coaching: EstimationCoachingResponse | null;
  /** True while the request is in-flight. */
  loading: boolean;
  /** Human-readable error message, or null. */
  error: string | null;
  /** Fetch (or re-fetch) estimation coaching for the given plan. */
  fetchCoaching: (planId: string) => Promise<void>;
  /** Clear any error without re-fetching. */
  clearError: () => void;
}

// ─── useCapacityProfile ───────────────────────────────────────────────────────

/**
 * Fetches the authenticated user's capacity profile.
 *
 * Gated by the `capacityTracking` feature flag: returns immediately without
 * making a network request when the flag is disabled.
 */
export function useCapacityProfile(): UseCapacityProfileResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [profile, setProfile] = useState<CapacityProfile | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  const fetchProfile = useCallback(async () => {
    if (!flags.capacityTracking) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const resp = await client.GET("/users/me/capacity");

      if (resp.data) {
        setProfile(resp.data as CapacityProfile);
      } else {
        const err = resp.error as { error?: { message?: string } } | undefined;
        setError(err?.error?.message ?? `Request failed (${String(resp.response.status)})`);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  }, [client, flags.capacityTracking]);

  return { profile, loading, error, fetchProfile, clearError };
}

// ─── useTeamCapacity ──────────────────────────────────────────────────────────

/**
 * Fetches the team capacity view for a given week (manager-only).
 *
 * Gated by the `capacityTracking` feature flag: returns immediately without
 * making a network request when the flag is disabled.
 */
export function useTeamCapacity(): UseTeamCapacityResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [teamCapacity, setTeamCapacity] = useState<TeamCapacityResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  const fetchTeamCapacity = useCallback(
    async (weekStart: string) => {
      if (!flags.capacityTracking) {
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/team/capacity", {
          params: {
            query: { weekStart },
          },
        });

        if (resp.data) {
          setTeamCapacity(resp.data as TeamCapacityResponse);
        } else {
          const err = resp.error as { error?: { message?: string } } | undefined;
          setError(err?.error?.message ?? `Request failed (${String(resp.response.status)})`);
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [client, flags.capacityTracking],
  );

  return { teamCapacity, loading, error, fetchTeamCapacity, clearError };
}

// ─── useEstimationCoaching ────────────────────────────────────────────────────

/**
 * Fetches post-reconciliation estimation coaching for a given plan.
 *
 * Gated by the `estimationCoaching` feature flag: returns immediately without
 * making a network request when the flag is disabled.
 */
export function useEstimationCoaching(): UseEstimationCoachingResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [coaching, setCoaching] = useState<EstimationCoachingResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  const fetchCoaching = useCallback(
    async (planId: string) => {
      if (!flags.estimationCoaching) {
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/users/me/estimation-coaching", {
          params: {
            query: { planId },
          },
        });

        if (resp.data) {
          setCoaching(resp.data as EstimationCoachingResponse);
        } else {
          const err = resp.error as { error?: { message?: string } } | undefined;
          setError(err?.error?.message ?? `Request failed (${String(resp.response.status)})`);
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [client, flags.estimationCoaching],
  );

  return { coaching, loading, error, fetchCoaching, clearError };
}
