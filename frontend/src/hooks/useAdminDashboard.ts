/**
 * Hook for admin dashboard data.
 *
 * Aggregates org policy, adoption metrics, AI usage metrics, and RCDO health
 * report into a single, coordinated hook so the AdminDashboardPage can fetch
 * all admin data without prop-drilling individual hooks.
 */
import { useState, useCallback } from "react";
import type {
  OrgPolicy,
  AdoptionMetrics,
  AiUsageMetrics,
  RcdoHealthReport,
  ApiErrorResponse,
  UpdateDigestConfigRequest,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";

export interface UseAdminDashboardResult {
  // ── Data ──────────────────────────────────────────────────
  orgPolicy: OrgPolicy | null;
  adoptionMetrics: AdoptionMetrics | null;
  aiUsageMetrics: AiUsageMetrics | null;
  rcdoHealthReport: RcdoHealthReport | null;

  // ── Loading state ─────────────────────────────────────────
  loadingPolicy: boolean;
  loadingAdoption: boolean;
  loadingAiUsage: boolean;
  loadingRcdoHealth: boolean;

  // ── Errors ────────────────────────────────────────────────
  errorPolicy: string | null;
  errorAdoption: string | null;
  errorAiUsage: string | null;
  errorRcdoHealth: string | null;

  // ── Actions ───────────────────────────────────────────────
  fetchOrgPolicy: () => Promise<void>;
  fetchAdoptionMetrics: (weeks?: number) => Promise<void>;
  fetchAiUsageMetrics: (weeks?: number) => Promise<void>;
  fetchRcdoHealth: () => Promise<void>;
  updateDigestConfig: (request: UpdateDigestConfigRequest) => Promise<OrgPolicy | null>;
  clearErrors: () => void;
}

/**
 * Provides all data required by the AdminDashboardPage.
 *
 * Each resource has its own loading/error state so individual panels
 * can show skeleton/error states independently.
 */
export function useAdminDashboard(): UseAdminDashboardResult {
  const client = useApiClient();

  const [orgPolicy, setOrgPolicy] = useState<OrgPolicy | null>(null);
  const [adoptionMetrics, setAdoptionMetrics] = useState<AdoptionMetrics | null>(null);
  const [aiUsageMetrics, setAiUsageMetrics] = useState<AiUsageMetrics | null>(null);
  const [rcdoHealthReport, setRcdoHealthReport] = useState<RcdoHealthReport | null>(null);

  const [loadingPolicy, setLoadingPolicy] = useState(false);
  const [loadingAdoption, setLoadingAdoption] = useState(false);
  const [loadingAiUsage, setLoadingAiUsage] = useState(false);
  const [loadingRcdoHealth, setLoadingRcdoHealth] = useState(false);

  const [errorPolicy, setErrorPolicy] = useState<string | null>(null);
  const [errorAdoption, setErrorAdoption] = useState<string | null>(null);
  const [errorAiUsage, setErrorAiUsage] = useState<string | null>(null);
  const [errorRcdoHealth, setErrorRcdoHealth] = useState<string | null>(null);

  const extractError = useCallback((resp: { error?: unknown; response: Response }): string => {
    const err = resp.error as ApiErrorResponse | undefined;
    if (err?.error?.message) return err.error.message;
    return `Request failed (${String(resp.response.status)})`;
  }, []);

  const fetchOrgPolicy = useCallback(async () => {
    setLoadingPolicy(true);
    setErrorPolicy(null);
    try {
      const resp = await client.GET("/admin/org-policy");
      if (resp.data) {
        setOrgPolicy(resp.data as OrgPolicy);
      } else {
        setErrorPolicy(extractError(resp));
      }
    } catch (e) {
      setErrorPolicy(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoadingPolicy(false);
    }
  }, [client, extractError]);

  const fetchAdoptionMetrics = useCallback(
    async (weeks?: number) => {
      setLoadingAdoption(true);
      setErrorAdoption(null);
      try {
        const resp = await client.GET("/admin/adoption-metrics", {
          params: { query: weeks !== undefined ? { weeks } : {} },
        });
        if (resp.data) {
          setAdoptionMetrics(resp.data as AdoptionMetrics);
        } else {
          setErrorAdoption(extractError(resp));
        }
      } catch (e) {
        setErrorAdoption(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoadingAdoption(false);
      }
    },
    [client, extractError],
  );

  const fetchAiUsageMetrics = useCallback(
    async (weeks?: number) => {
      setLoadingAiUsage(true);
      setErrorAiUsage(null);
      try {
        const resp = await client.GET("/admin/ai-usage", {
          params: { query: weeks !== undefined ? { weeks } : {} },
        });
        if (resp.data) {
          setAiUsageMetrics(resp.data as AiUsageMetrics);
        } else {
          setErrorAiUsage(extractError(resp));
        }
      } catch (e) {
        setErrorAiUsage(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoadingAiUsage(false);
      }
    },
    [client, extractError],
  );

  const fetchRcdoHealth = useCallback(async () => {
    setLoadingRcdoHealth(true);
    setErrorRcdoHealth(null);
    try {
      const resp = await client.GET("/admin/rcdo-health");
      if (resp.data) {
        setRcdoHealthReport(resp.data as RcdoHealthReport);
      } else {
        setErrorRcdoHealth(extractError(resp));
      }
    } catch (e) {
      setErrorRcdoHealth(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoadingRcdoHealth(false);
    }
  }, [client, extractError]);

  const updateDigestConfig = useCallback(
    async (request: UpdateDigestConfigRequest): Promise<OrgPolicy | null> => {
      setLoadingPolicy(true);
      setErrorPolicy(null);
      try {
        const resp = await client.PATCH("/admin/org-policy/digest", { body: request });
        if (resp.data) {
          const updated = resp.data as OrgPolicy;
          setOrgPolicy(updated);
          return updated;
        }
        setErrorPolicy(extractError(resp));
        return null;
      } catch (e) {
        setErrorPolicy(e instanceof Error ? e.message : "Network error");
        return null;
      } finally {
        setLoadingPolicy(false);
      }
    },
    [client, extractError],
  );

  const clearErrors = useCallback(() => {
    setErrorPolicy(null);
    setErrorAdoption(null);
    setErrorAiUsage(null);
    setErrorRcdoHealth(null);
  }, []);

  return {
    orgPolicy,
    adoptionMetrics,
    aiUsageMetrics,
    rcdoHealthReport,
    loadingPolicy,
    loadingAdoption,
    loadingAiUsage,
    loadingRcdoHealth,
    errorPolicy,
    errorAdoption,
    errorAiUsage,
    errorRcdoHealth,
    fetchOrgPolicy,
    fetchAdoptionMetrics,
    fetchAiUsageMetrics,
    fetchRcdoHealth,
    updateDigestConfig,
    clearErrors,
  };
}
