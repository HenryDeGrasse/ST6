import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAdminDashboard } from "../hooks/useAdminDashboard.js";
import type {
  OrgPolicy,
  AdoptionMetrics,
  AiUsageMetrics,
  RcdoHealthReport,
} from "@weekly-commitments/contracts";

/* ── Mock API client ──────────────────────────────────────────────────────── */

const mockClient = {
  GET: vi.fn(),
  POST: vi.fn(),
  PATCH: vi.fn(),
  DELETE: vi.fn(),
  use: vi.fn(),
};

vi.mock("../api/ApiContext.js", () => ({
  useApiClient: () => mockClient,
}));

/* ── Fixtures ─────────────────────────────────────────────────────────────── */

const makeOrgPolicy = (): OrgPolicy => ({
  chessKingRequired: true,
  chessMaxKing: 1,
  chessMaxQueen: 2,
  lockDay: "MONDAY",
  lockTime: "10:00",
  reconcileDay: "FRIDAY",
  reconcileTime: "16:00",
  blockLockOnStaleRcdo: true,
  rcdoStalenessThresholdMinutes: 60,
  digestDay: "FRIDAY",
  digestTime: "17:00",
});

const makeAdoptionMetrics = (): AdoptionMetrics => ({
  weeks: 8,
  windowStart: "2026-01-26",
  windowEnd: "2026-03-16",
  totalActiveUsers: 12,
  cadenceComplianceRate: 0.85,
  weeklyPoints: [
    {
      weekStart: "2026-03-09",
      activeUsers: 10,
      plansCreated: 10,
      plansLocked: 8,
      plansReconciled: 6,
      plansReviewed: 5,
    },
  ],
});

const makeAiUsageMetrics = (): AiUsageMetrics => ({
  weeks: 8,
  windowStart: "2026-01-26",
  windowEnd: "2026-03-16",
  totalFeedbackCount: 100,
  acceptedCount: 60,
  deferredCount: 25,
  declinedCount: 15,
  acceptanceRate: 0.6,
  cacheHits: 500,
  cacheMisses: 200,
  cacheHitRate: 0.714,
  approximateTokensSpent: 200000,
  approximateTokensSaved: 500000,
});

const makeRcdoHealthReport = (): RcdoHealthReport => ({
  generatedAt: "2026-03-19T12:00:00Z",
  windowWeeks: 8,
  totalOutcomes: 10,
  coveredOutcomes: 7,
  topOutcomes: [
    {
      outcomeId: "o1",
      outcomeName: "Outcome A",
      objectiveId: "obj1",
      objectiveName: "Objective 1",
      rallyCryId: "rc1",
      rallyCryName: "Rally Cry 1",
      commitCount: 15,
    },
  ],
  staleOutcomes: [
    {
      outcomeId: "o2",
      outcomeName: "Outcome B",
      objectiveId: "obj2",
      objectiveName: "Objective 2",
      rallyCryId: "rc1",
      rallyCryName: "Rally Cry 1",
      commitCount: 0,
    },
  ],
});

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("useAdminDashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("initialises with null data, not loading, no errors", () => {
    const { result } = renderHook(() => useAdminDashboard());

    expect(result.current.orgPolicy).toBeNull();
    expect(result.current.adoptionMetrics).toBeNull();
    expect(result.current.aiUsageMetrics).toBeNull();
    expect(result.current.rcdoHealthReport).toBeNull();
    expect(result.current.loadingPolicy).toBe(false);
    expect(result.current.loadingAdoption).toBe(false);
    expect(result.current.loadingAiUsage).toBe(false);
    expect(result.current.loadingRcdoHealth).toBe(false);
    expect(result.current.errorPolicy).toBeNull();
    expect(result.current.errorAdoption).toBeNull();
    expect(result.current.errorAiUsage).toBeNull();
    expect(result.current.errorRcdoHealth).toBeNull();
  });

  describe("fetchOrgPolicy", () => {
    it("sets orgPolicy on success", async () => {
      const data = makeOrgPolicy();
      mockClient.GET.mockResolvedValue({ data, response: { status: 200 } });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchOrgPolicy();
      });

      expect(result.current.orgPolicy).toEqual(data);
      expect(result.current.loadingPolicy).toBe(false);
      expect(result.current.errorPolicy).toBeNull();
    });

    it("calls GET /admin/org-policy", async () => {
      mockClient.GET.mockResolvedValue({ data: makeOrgPolicy(), response: { status: 200 } });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchOrgPolicy();
      });

      expect(mockClient.GET).toHaveBeenCalledWith("/admin/org-policy");
    });

    it("sets errorPolicy on failure", async () => {
      mockClient.GET.mockResolvedValue({
        data: undefined,
        error: { error: { message: "Forbidden" } },
        response: { status: 403 },
      });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchOrgPolicy();
      });

      expect(result.current.orgPolicy).toBeNull();
      expect(result.current.errorPolicy).toBe("Forbidden");
    });

    it("sets errorPolicy on network exception", async () => {
      mockClient.GET.mockRejectedValue(new Error("Network error"));

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchOrgPolicy();
      });

      expect(result.current.errorPolicy).toBe("Network error");
    });
  });

  describe("fetchAdoptionMetrics", () => {
    it("sets adoptionMetrics on success", async () => {
      const data = makeAdoptionMetrics();
      mockClient.GET.mockResolvedValue({ data, response: { status: 200 } });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchAdoptionMetrics(8);
      });

      expect(result.current.adoptionMetrics).toEqual(data);
      expect(result.current.errorAdoption).toBeNull();
    });

    it("calls GET /admin/adoption-metrics with weeks param", async () => {
      mockClient.GET.mockResolvedValue({ data: makeAdoptionMetrics(), response: { status: 200 } });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchAdoptionMetrics(12);
      });

      expect(mockClient.GET).toHaveBeenCalledWith("/admin/adoption-metrics", {
        params: { query: { weeks: 12 } },
      });
    });

    it("calls without weeks param when omitted", async () => {
      mockClient.GET.mockResolvedValue({ data: makeAdoptionMetrics(), response: { status: 200 } });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchAdoptionMetrics();
      });

      expect(mockClient.GET).toHaveBeenCalledWith("/admin/adoption-metrics", {
        params: { query: {} },
      });
    });

    it("sets errorAdoption on failure", async () => {
      mockClient.GET.mockResolvedValue({
        data: undefined,
        error: { error: { message: "Forbidden" } },
        response: { status: 403 },
      });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchAdoptionMetrics(8);
      });

      expect(result.current.errorAdoption).toBe("Forbidden");
    });
  });

  describe("fetchAiUsageMetrics", () => {
    it("sets aiUsageMetrics on success", async () => {
      const data = makeAiUsageMetrics();
      mockClient.GET.mockResolvedValue({ data, response: { status: 200 } });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchAiUsageMetrics(8);
      });

      expect(result.current.aiUsageMetrics).toEqual(data);
    });

    it("calls GET /admin/ai-usage with weeks param", async () => {
      mockClient.GET.mockResolvedValue({ data: makeAiUsageMetrics(), response: { status: 200 } });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchAiUsageMetrics(4);
      });

      expect(mockClient.GET).toHaveBeenCalledWith("/admin/ai-usage", {
        params: { query: { weeks: 4 } },
      });
    });

    it("sets errorAiUsage on network exception", async () => {
      mockClient.GET.mockRejectedValue(new Error("Network timeout"));

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchAiUsageMetrics();
      });

      expect(result.current.errorAiUsage).toBe("Network timeout");
    });
  });

  describe("fetchRcdoHealth", () => {
    it("sets rcdoHealthReport on success", async () => {
      const data = makeRcdoHealthReport();
      mockClient.GET.mockResolvedValue({ data, response: { status: 200 } });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchRcdoHealth();
      });

      expect(result.current.rcdoHealthReport).toEqual(data);
    });

    it("calls GET /admin/rcdo-health", async () => {
      mockClient.GET.mockResolvedValue({ data: makeRcdoHealthReport(), response: { status: 200 } });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchRcdoHealth();
      });

      expect(mockClient.GET).toHaveBeenCalledWith("/admin/rcdo-health");
    });

    it("sets errorRcdoHealth on failure", async () => {
      mockClient.GET.mockResolvedValue({
        data: undefined,
        error: { error: { message: "Server error" } },
        response: { status: 500 },
      });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchRcdoHealth();
      });

      expect(result.current.errorRcdoHealth).toBe("Server error");
    });
  });

  describe("updateDigestConfig", () => {
    it("calls PATCH /admin/org-policy/digest and returns updated policy", async () => {
      const updated = makeOrgPolicy();
      updated.digestDay = "MONDAY";
      updated.digestTime = "09:00";
      mockClient.PATCH.mockResolvedValue({ data: updated, response: { status: 200 } });

      const { result } = renderHook(() => useAdminDashboard());

      let returned: OrgPolicy | null = null;
      await act(async () => {
        returned = await result.current.updateDigestConfig({
          digestDay: "MONDAY",
          digestTime: "09:00",
        });
      });

      expect(mockClient.PATCH).toHaveBeenCalledWith("/admin/org-policy/digest", {
        body: { digestDay: "MONDAY", digestTime: "09:00" },
      });
      expect(returned).toEqual(updated);
      expect(result.current.orgPolicy).toEqual(updated);
    });

    it("returns null and sets error on failure", async () => {
      mockClient.PATCH.mockResolvedValue({
        data: undefined,
        error: { error: { message: "Validation failed" } },
        response: { status: 422 },
      });

      const { result } = renderHook(() => useAdminDashboard());

      let returned: OrgPolicy | null = undefined as unknown as OrgPolicy;
      await act(async () => {
        returned = await result.current.updateDigestConfig({
          digestDay: "TUESDAY",
          digestTime: "08:00",
        });
      });

      expect(returned).toBeNull();
      expect(result.current.errorPolicy).toBe("Validation failed");
    });
  });

  describe("clearErrors", () => {
    it("clears all error fields", async () => {
      mockClient.GET.mockResolvedValue({
        data: undefined,
        error: { error: { message: "Forbidden" } },
        response: { status: 403 },
      });

      const { result } = renderHook(() => useAdminDashboard());

      await act(async () => {
        await result.current.fetchOrgPolicy();
        await result.current.fetchAdoptionMetrics();
      });

      expect(result.current.errorPolicy).not.toBeNull();
      expect(result.current.errorAdoption).not.toBeNull();

      act(() => {
        result.current.clearErrors();
      });

      expect(result.current.errorPolicy).toBeNull();
      expect(result.current.errorAdoption).toBeNull();
      expect(result.current.errorAiUsage).toBeNull();
      expect(result.current.errorRcdoHealth).toBeNull();
    });
  });
});
