/**
 * Hook for Phase 3 outcome-metadata, urgency, and strategic-slack features.
 *
 * Endpoints served by Agent C's controllers are not yet merged into the main
 * openapi.yaml (they live in contracts/fragments/agent-c-urgency.yaml).
 * The hook therefore casts the typed API client to a minimal raw-fetch type
 * to call those paths without breaking TypeScript strict checks.
 *
 * Feature-flag gates:
 *  - outcomeUrgency  — fetchMetadata, fetchUrgencySummary, updateMetadata,
 *                      updateProgress
 *  - strategicSlack  — fetchStrategicSlack
 */
import { useState, useCallback } from "react";
import { useApiClient } from "../api/ApiContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";

// ─── Locally-defined TypeScript interfaces ───────────────────────────────────
//
// These mirror the backend DTOs in com.weekly.urgency and com.weekly.shared.
// They will be superseded by generated types once the fragment is merged into
// the main openapi.yaml.

/** Progress tracking model for an outcome. */
export type OutcomeProgressType = "ACTIVITY" | "METRIC" | "MILESTONE";

/** Urgency band classification for an outcome. */
export type UrgencyBand = "NO_TARGET" | "ON_TRACK" | "NEEDS_ATTENTION" | "AT_RISK" | "CRITICAL";

/** Strategic slack band classification for the org. */
export type SlackBand = "HIGH_SLACK" | "MODERATE_SLACK" | "LOW_SLACK" | "NO_SLACK";

/**
 * Outcome metadata response — returned by:
 *  GET  /api/v1/outcomes/metadata (list)
 *  GET  /api/v1/outcomes/{outcomeId}/metadata (single)
 *  PUT  /api/v1/outcomes/{outcomeId}/metadata (upsert)
 *  PATCH /api/v1/outcomes/{outcomeId}/progress (progress update)
 *
 * Mirrors {@code OutcomeMetadataResponse} in the backend.
 */
export interface OutcomeMetadataResponse {
  orgId: string;
  outcomeId: string;
  /** ISO-8601 date string, or null if no target date has been set. */
  targetDate: string | null;
  progressType: OutcomeProgressType;
  metricName: string | null;
  targetValue: number | null;
  currentValue: number | null;
  unit: string | null;
  /** Raw JSON array of milestone objects, or null. */
  milestones: string | null;
  /** Computed progress 0–100, or null if urgency has not been computed yet. */
  progressPct: number | null;
  urgencyBand: UrgencyBand | null;
  /** ISO-8601 datetime string of the last urgency computation, or null. */
  lastComputedAt: string | null;
  /** ISO-8601 datetime string when this metadata row was created. */
  createdAt: string;
  /** ISO-8601 datetime string of the last update to this row. */
  updatedAt: string;
}

/**
 * Urgency summary for a single RCDO outcome — returned in the
 * {@code outcomes} array of GET /api/v1/outcomes/urgency-summary.
 *
 * Mirrors {@code UrgencyInfo} in the backend (com.weekly.shared).
 */
export interface UrgencyInfo {
  outcomeId: string;
  outcomeName: string;
  /** ISO-8601 date string, or null if no target date is set. */
  targetDate: string | null;
  /** Current progress 0–100, or null if uncomputed. */
  progressPct: number | null;
  /** Expected progress at today's date given linear progression, or null. */
  expectedProgressPct: number | null;
  urgencyBand: UrgencyBand;
  /**
   * Calendar days remaining to the target date; negative if overdue.
   * Equal to {@code Number.MIN_SAFE_INTEGER} when no target date is set.
   */
  daysRemaining: number;
}

/**
 * Strategic slack summary for the org's tracked outcome portfolio — returned
 * as the {@code slack} property of GET /api/v1/team/strategic-slack.
 *
 * Mirrors {@code SlackInfo} in the backend (com.weekly.shared).
 */
export interface SlackInfo {
  slackBand: SlackBand;
  /** Recommended strategic focus floor, in the range 0.50–0.95. */
  strategicFocusFloor: number;
  /** Number of outcomes in the AT_RISK urgency band. */
  atRiskCount: number;
  /** Number of outcomes in the CRITICAL urgency band. */
  criticalCount: number;
}

/**
 * Request body for creating or updating outcome metadata.
 * All fields are optional — omitted fields leave existing values unchanged.
 *
 * Mirrors {@code OutcomeMetadataRequest} in the backend.
 */
export interface OutcomeMetadataRequest {
  /** ISO-8601 date string; pass null to remove target-date tracking. */
  targetDate?: string | null;
  progressType?: OutcomeProgressType;
  metricName?: string | null;
  targetValue?: number | null;
  currentValue?: number | null;
  unit?: string | null;
  /** Raw JSON array of milestone objects. */
  milestones?: string | null;
}

/**
 * Request body for a lightweight progress update.
 * Both fields are optional — supply only the ones that need updating.
 *
 * Mirrors {@code ProgressUpdateRequest} in the backend.
 */
export interface ProgressUpdateRequest {
  currentValue?: number | null;
  milestones?: string | null;
}

// ─── Internal raw-fetch helper type ─────────────────────────────────────────
//
// Agent C's endpoints are not in the typed path map yet.  Rather than using
// `any`, we cast to this minimal typed interface so TypeScript still checks
// the call sites without requiring the full openapi-fetch generic machinery.

interface RawApiGet {
  (url: string, opts?: { params?: { path?: Record<string, string>; query?: Record<string, string | number | boolean> } }): Promise<{
    data?: unknown;
    error?: unknown;
    response: Response;
  }>;
}

interface RawApiMutate {
  (url: string, opts?: { body?: unknown; params?: { path?: Record<string, string> } }): Promise<{
    data?: unknown;
    error?: unknown;
    response: Response;
  }>;
}

interface RawClient {
  GET: RawApiGet;
  PUT: RawApiMutate;
  PATCH: RawApiMutate;
}

// ─── Hook result interface ───────────────────────────────────────────────────

export interface UseOutcomeMetadataResult {
  /** All outcome metadata rows for the org, or null if not yet loaded. */
  outcomeMetadata: OutcomeMetadataResponse[] | null;
  /** Urgency summary for all outcomes, or null if not yet loaded. */
  urgencySummary: UrgencyInfo[] | null;
  /** Strategic slack summary for the org, or null if not yet loaded. */
  strategicSlack: SlackInfo | null;
  /** True while any request is in-flight. */
  loading: boolean;
  /** Human-readable error message, or null. */
  error: string | null;
  /** Fetch all outcome metadata rows for the org. Gated by outcomeUrgency flag. */
  fetchMetadata: () => Promise<void>;
  /** Fetch urgency summary for all org outcomes. Gated by outcomeUrgency flag. */
  fetchUrgencySummary: () => Promise<void>;
  /** Fetch strategic slack summary for the org. Gated by strategicSlack flag. */
  fetchStrategicSlack: () => Promise<void>;
  /**
   * Create or update outcome metadata for the given outcome.
   * Returns the saved response, or null on error.
   * Gated by outcomeUrgency flag.
   */
  updateMetadata: (outcomeId: string, data: OutcomeMetadataRequest) => Promise<OutcomeMetadataResponse | null>;
  /**
   * Update the progress fields (currentValue and/or milestones) of an existing
   * metadata record. Returns the updated response, or null on error.
   * Gated by outcomeUrgency flag.
   */
  updateProgress: (outcomeId: string, data: ProgressUpdateRequest) => Promise<OutcomeMetadataResponse | null>;
  /** Clear any error without re-fetching. */
  clearError: () => void;
}

// ─── Hook implementation ─────────────────────────────────────────────────────

/**
 * Provides outcome-metadata CRUD, urgency summary, and strategic-slack data
 * for the Phase 3 outcome-urgency feature.
 *
 * All network calls are gated by the `outcomeUrgency` or `strategicSlack`
 * feature flags — when a flag is disabled the corresponding function returns
 * immediately without making a request.
 */
export function useOutcomeMetadata(): UseOutcomeMetadataResult {
  const client = useApiClient();
  const flags = useFeatureFlags();

  const [outcomeMetadata, setOutcomeMetadata] = useState<OutcomeMetadataResponse[] | null>(null);
  const [urgencySummary, setUrgencySummary] = useState<UrgencyInfo[] | null>(null);
  const [strategicSlack, setStrategicSlack] = useState<SlackInfo | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  // Cast the typed client to our minimal raw interface so we can call
  // endpoints that are not yet registered in the generated path map.
  const raw = client as unknown as RawClient;

  const extractError = useCallback((resp: { error?: unknown; response: Response }): string => {
    const err = resp.error as { error?: { message?: string } } | undefined;
    if (err?.error?.message) return err.error.message;
    return `Request failed (${String(resp.response.status)})`;
  }, []);

  // ── fetchMetadata ──────────────────────────────────────────────────────────

  const fetchMetadata = useCallback(async () => {
    if (!flags.outcomeUrgency) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const resp = await raw.GET("/outcomes/metadata");
      if (resp.data) {
        setOutcomeMetadata(resp.data as OutcomeMetadataResponse[]);
      } else {
        setError(extractError(resp));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  }, [raw, flags.outcomeUrgency, extractError]);

  // ── fetchUrgencySummary ────────────────────────────────────────────────────

  const fetchUrgencySummary = useCallback(async () => {
    if (!flags.outcomeUrgency) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const resp = await raw.GET("/outcomes/urgency-summary");
      if (resp.data) {
        const data = resp.data as { outcomes: UrgencyInfo[] };
        setUrgencySummary(data.outcomes);
      } else {
        setError(extractError(resp));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  }, [raw, flags.outcomeUrgency, extractError]);

  // ── fetchStrategicSlack ────────────────────────────────────────────────────

  const fetchStrategicSlack = useCallback(async () => {
    if (!flags.strategicSlack) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const resp = await raw.GET("/team/strategic-slack");
      if (resp.data) {
        const data = resp.data as { slack: SlackInfo };
        setStrategicSlack(data.slack);
      } else {
        setError(extractError(resp));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  }, [raw, flags.strategicSlack, extractError]);

  // ── updateMetadata ─────────────────────────────────────────────────────────

  const updateMetadata = useCallback(
    async (outcomeId: string, data: OutcomeMetadataRequest): Promise<OutcomeMetadataResponse | null> => {
      if (!flags.outcomeUrgency) {
        return null;
      }
      setLoading(true);
      setError(null);
      try {
        const resp = await raw.PUT(`/outcomes/${outcomeId}/metadata`, { body: data });
        if (resp.data) {
          const updated = resp.data as OutcomeMetadataResponse;
          // Refresh the local list if it has already been loaded.
          setOutcomeMetadata((prev) => {
            if (prev === null) return null;
            const exists = prev.some((m) => m.outcomeId === outcomeId);
            if (exists) {
              return prev.map((m) => (m.outcomeId === outcomeId ? updated : m));
            }
            return [...prev, updated];
          });
          return updated;
        }
        setError(extractError(resp));
        return null;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return null;
      } finally {
        setLoading(false);
      }
    },
    [raw, flags.outcomeUrgency, extractError],
  );

  // ── updateProgress ─────────────────────────────────────────────────────────

  const updateProgress = useCallback(
    async (outcomeId: string, data: ProgressUpdateRequest): Promise<OutcomeMetadataResponse | null> => {
      if (!flags.outcomeUrgency) {
        return null;
      }
      setLoading(true);
      setError(null);
      try {
        const resp = await raw.PATCH(`/outcomes/${outcomeId}/progress`, { body: data });
        if (resp.data) {
          const updated = resp.data as OutcomeMetadataResponse;
          // Refresh the local list if it has already been loaded.
          setOutcomeMetadata((prev) => {
            if (prev === null) return null;
            return prev.map((m) => (m.outcomeId === outcomeId ? updated : m));
          });
          return updated;
        }
        setError(extractError(resp));
        return null;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return null;
      } finally {
        setLoading(false);
      }
    },
    [raw, flags.outcomeUrgency, extractError],
  );

  return {
    outcomeMetadata,
    urgencySummary,
    strategicSlack,
    loading,
    error,
    fetchMetadata,
    fetchUrgencySummary,
    fetchStrategicSlack,
    updateMetadata,
    updateProgress,
    clearError,
  };
}
