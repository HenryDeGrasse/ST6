/**
 * Hooks for multi-week strategic intelligence analytics.
 *
 * Five hooks are exported:
 * - useOutcomeCoverageTimeline — fetches GET /analytics/outcome-coverage?outcomeId=X&weeks=N
 * - useCarryForwardHeatmap    — fetches GET /analytics/carry-forward-heatmap?weeks=N
 * - useCategoryShifts         — fetches GET /analytics/category-shifts?weeks=N
 * - useEstimationAccuracy     — fetches GET /analytics/estimation-accuracy?weeks=N
 * - usePredictions            — fetches GET /analytics/predictions/{userId}
 *
 * These endpoints are not yet in the generated OpenAPI client, so the hooks use
 * the ApiContext client with raw path strings. That preserves the shared baseUrl
 * and auth middleware while still allowing calls to not-yet-generated endpoints.
 */
import { useCallback, useState } from "react";
import type {
  ApiErrorResponse,
  CarryForwardHeatmap,
  CategoryShiftAnalysis,
  EstimationAccuracyDistribution,
  OutcomeCoverageTimeline,
  Prediction,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";

type AnalyticsQuery = Record<string, string | number | boolean | undefined>;

type RawAnalyticsClient = {
  GET: (
    path: string,
    init?: { params?: { query?: AnalyticsQuery } },
  ) => Promise<{ data?: unknown; error?: unknown; response: Response }>;
};

interface RawOutcomeCoverageTimeline {
  weeks: OutcomeCoverageTimeline["weeks"];
  trendDirection: OutcomeCoverageTimeline["trendDirection"];
}

interface RawHeatmapUser {
  userId: string;
  displayName: string;
  weekCells: Array<{ weekStart: string; carriedCount: number }>;
}

interface RawCarryForwardHeatmap {
  users: RawHeatmapUser[];
}

interface RawCategoryShift {
  category: string;
  delta: number;
}

interface RawUserCategoryShift {
  userId: string;
  currentDistribution: Record<string, number>;
  priorDistribution: Record<string, number>;
  biggestShift: RawCategoryShift;
}

type RawUserEstimationAccuracy = EstimationAccuracyDistribution["users"][number];

type RawPrediction = Omit<Prediction, "subjectId">;

export interface TeamBacklogHealthSnapshot {
  teamId: string;
  openIssueCount: number;
  avgIssueAgeDays: number;
  blockedCount: number;
  buildCount: number;
  maintainCount: number;
  collaborateCount: number;
  learnCount: number;
  avgCycleTimeDays: number;
}

interface AnalyticsGetResult<T> {
  data: T | null;
  error: string | null;
}

function extractError(resp: { error?: unknown; response: Response }): string {
  const err = resp.error as ApiErrorResponse | undefined;
  return err?.error?.message ?? `Request failed (${String(resp.response.status)})`;
}

function sanitizeQuery(query?: AnalyticsQuery): AnalyticsQuery | undefined {
  if (!query) {
    return undefined;
  }

  const filtered = Object.fromEntries(Object.entries(query).filter(([, value]) => value !== undefined)) as AnalyticsQuery;
  return Object.keys(filtered).length > 0 ? filtered : undefined;
}

async function analyticsGet<T>(client: RawAnalyticsClient, path: string, query?: AnalyticsQuery): Promise<AnalyticsGetResult<T>> {
  const sanitizedQuery = sanitizeQuery(query);
  const resp = await client.GET(path, sanitizedQuery ? { params: { query: sanitizedQuery } } : undefined);

  if (resp.data !== undefined) {
    return { data: resp.data as T, error: null };
  }

  return { data: null, error: extractError(resp) };
}

function normalizeOutcomeCoverageTimeline(
  response: RawOutcomeCoverageTimeline,
  outcomeId: string,
): OutcomeCoverageTimeline {
  return {
    outcomeId,
    weeks: response.weeks,
    trendDirection: response.trendDirection,
  };
}

function normalizeCarryForwardHeatmap(response: RawCarryForwardHeatmap): CarryForwardHeatmap {
  const weekStarts = Array.from(new Set(response.users.flatMap((user) => user.weekCells.map((cell) => cell.weekStart)))).sort();

  return {
    users: response.users.map((user) => ({
      userId: user.userId,
      displayName: user.displayName,
      cells: user.weekCells,
    })),
    weekStarts,
  };
}

function normalizeCategoryShiftAnalysis(response: RawUserCategoryShift[]): CategoryShiftAnalysis {
  return {
    users: response.map((user) => ({
      userId: user.userId,
      currentDistribution: user.currentDistribution,
      priorDistribution: user.priorDistribution,
      biggestShiftCategory: user.biggestShift.category,
      biggestShiftDelta: user.biggestShift.delta,
    })),
  };
}

function normalizeEstimationAccuracyDistribution(
  response: RawUserEstimationAccuracy[],
): EstimationAccuracyDistribution {
  return { users: response };
}

function normalizePredictions(response: RawPrediction[], userId: string): Prediction[] {
  return response.map((prediction) => ({
    ...prediction,
    subjectId: userId,
  }));
}

export interface UseOutcomeCoverageTimelineResult {
  data: OutcomeCoverageTimeline | null;
  loading: boolean;
  error: string | null;
  fetch: (outcomeId: string, weeks?: number) => Promise<void>;
}

export function useOutcomeCoverageTimeline(): UseOutcomeCoverageTimelineResult {
  const client = useApiClient() as unknown as RawAnalyticsClient;
  const flags = useFeatureFlags();

  const [data, setData] = useState<OutcomeCoverageTimeline | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(
    async (outcomeId: string, weeks?: number) => {
      if (!flags.strategicIntelligence) {
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const result = await analyticsGet<RawOutcomeCoverageTimeline>(client, "/analytics/outcome-coverage", {
          outcomeId,
          weeks,
        });

        if (result.data) {
          setData(normalizeOutcomeCoverageTimeline(result.data, outcomeId));
        } else {
          setError(result.error);
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [client, flags.strategicIntelligence],
  );

  return { data, loading, error, fetch: fetchData };
}

export interface UseCarryForwardHeatmapResult {
  data: CarryForwardHeatmap | null;
  loading: boolean;
  error: string | null;
  fetch: (weeks?: number) => Promise<void>;
}

export function useCarryForwardHeatmap(): UseCarryForwardHeatmapResult {
  const client = useApiClient() as unknown as RawAnalyticsClient;
  const flags = useFeatureFlags();

  const [data, setData] = useState<CarryForwardHeatmap | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(
    async (weeks?: number) => {
      if (!flags.strategicIntelligence) {
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const result = await analyticsGet<RawCarryForwardHeatmap>(client, "/analytics/carry-forward-heatmap", { weeks });

        if (result.data) {
          setData(normalizeCarryForwardHeatmap(result.data));
        } else {
          setError(result.error);
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [client, flags.strategicIntelligence],
  );

  return { data, loading, error, fetch: fetchData };
}

export interface UseCategoryShiftsResult {
  data: CategoryShiftAnalysis | null;
  loading: boolean;
  error: string | null;
  fetch: (weeks?: number) => Promise<void>;
}

export function useCategoryShifts(): UseCategoryShiftsResult {
  const client = useApiClient() as unknown as RawAnalyticsClient;
  const flags = useFeatureFlags();

  const [data, setData] = useState<CategoryShiftAnalysis | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(
    async (weeks?: number) => {
      if (!flags.strategicIntelligence) {
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const result = await analyticsGet<RawUserCategoryShift[]>(client, "/analytics/category-shifts", { weeks });

        if (result.data) {
          setData(normalizeCategoryShiftAnalysis(result.data));
        } else {
          setError(result.error);
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [client, flags.strategicIntelligence],
  );

  return { data, loading, error, fetch: fetchData };
}

export interface UseEstimationAccuracyResult {
  data: EstimationAccuracyDistribution | null;
  loading: boolean;
  error: string | null;
  fetch: (weeks?: number) => Promise<void>;
}

export function useEstimationAccuracy(): UseEstimationAccuracyResult {
  const client = useApiClient() as unknown as RawAnalyticsClient;
  const flags = useFeatureFlags();

  const [data, setData] = useState<EstimationAccuracyDistribution | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(
    async (weeks?: number) => {
      if (!flags.strategicIntelligence) {
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const result = await analyticsGet<RawUserEstimationAccuracy[]>(client, "/analytics/estimation-accuracy", { weeks });

        if (result.data) {
          setData(normalizeEstimationAccuracyDistribution(result.data));
        } else {
          setError(result.error);
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [client, flags.strategicIntelligence],
  );

  return { data, loading, error, fetch: fetchData };
}

export interface UseOrgBacklogHealthResult {
  data: TeamBacklogHealthSnapshot[] | null;
  loading: boolean;
  error: string | null;
  fetch: () => Promise<void>;
  clearError: () => void;
}

export function useOrgBacklogHealth(): UseOrgBacklogHealthResult {
  const client = useApiClient() as unknown as RawAnalyticsClient;
  const flags = useFeatureFlags();

  const [data, setData] = useState<TeamBacklogHealthSnapshot[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    if (!flags.useIssueBacklog) {
      setData(null);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const result = await analyticsGet<TeamBacklogHealthSnapshot[]>(client, "/analytics/teams/backlog-health");
      if (result.data) {
        setData(result.data);
      } else {
        setError(result.error);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  }, [client, flags.useIssueBacklog]);

  const clearError = useCallback(() => setError(null), []);

  return { data, loading, error, fetch: fetchData, clearError };
}

export interface UsePredictionsResult {
  data: Prediction[] | null;
  loading: boolean;
  error: string | null;
  fetch: (userId: string) => Promise<void>;
}

export function usePredictions(): UsePredictionsResult {
  const client = useApiClient() as unknown as RawAnalyticsClient;
  const flags = useFeatureFlags();

  const [data, setData] = useState<Prediction[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(
    async (userId: string) => {
      if (!flags.predictions) {
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const result = await analyticsGet<RawPrediction[]>(client, `/analytics/predictions/${encodeURIComponent(userId)}`);

        if (result.data) {
          setData(normalizePredictions(result.data, userId));
        } else {
          setError(result.error);
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [client, flags.predictions],
  );

  return { data, loading, error, fetch: fetchData };
}
