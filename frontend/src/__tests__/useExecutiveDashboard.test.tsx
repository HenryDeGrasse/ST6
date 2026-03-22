import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useExecutiveDashboard } from "../hooks/useExecutiveDashboard.js";

const mockClient = { GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn(), DELETE: vi.fn(), use: vi.fn() };
let mockExecutiveDashboard = true;

vi.mock("../api/ApiContext.js", () => ({ useApiClient: () => mockClient }));
vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({ executiveDashboard: mockExecutiveDashboard }),
}));

describe("useExecutiveDashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockExecutiveDashboard = true;
  });

  it("fetches dashboard data", async () => {
    mockClient.GET.mockResolvedValue({
      data: { weekStart: "2026-03-16", summary: { totalForecasts: 4, onTrackForecasts: 2, needsAttentionForecasts: 1, offTrackForecasts: 1, noDataForecasts: 0, averageForecastConfidence: 0.7, totalCapacityHours: 80, strategicHours: 40, nonStrategicHours: 40, strategicCapacityUtilizationPct: 50, nonStrategicCapacityUtilizationPct: 50, planningCoveragePct: 92 }, rallyCryRollups: [], teamBuckets: [], teamGroupingAvailable: true },
      response: { status: 200 },
    });
    const { result } = renderHook(() => useExecutiveDashboard());
    await act(async () => {
      await result.current.fetchDashboard("2026-03-16");
    });
    expect(mockClient.GET).toHaveBeenCalledWith("/executive/strategic-health", { params: { query: { weekStart: "2026-03-16" } } });
    expect(result.current.dashboardStatus).toBe("ok");
  });

  it("fetches executive briefing", async () => {
    mockClient.POST.mockResolvedValue({
      data: { status: "ok", headline: "Overall healthy", insights: [{ title: "Focus", detail: "Keep watching", severity: "INFO" }] },
      response: { status: 200 },
    });
    const { result } = renderHook(() => useExecutiveDashboard());
    await act(async () => {
      await result.current.fetchBriefing("2026-03-16");
    });
    expect(mockClient.POST).toHaveBeenCalledWith("/ai/executive-briefing", { body: { weekStart: "2026-03-16" } });
    expect(result.current.briefingStatus).toBe("ok");
  });

  it("does not surface a duplicate generic error on briefing rate limit", async () => {
    mockClient.POST.mockResolvedValue({
      error: { error: { message: "Rate limit reached" } },
      response: { status: 429 },
    });

    const { result } = renderHook(() => useExecutiveDashboard());
    await act(async () => {
      await result.current.fetchBriefing("2026-03-16");
    });

    expect(result.current.briefingStatus).toBe("rate_limited");
    expect(result.current.errorBriefing).toBeNull();
  });

  it("does nothing when feature flag is disabled", async () => {
    mockExecutiveDashboard = false;
    const { result } = renderHook(() => useExecutiveDashboard());
    await act(async () => {
      await result.current.fetchDashboard("2026-03-16");
      await result.current.fetchBriefing("2026-03-16");
    });
    expect(mockClient.GET).not.toHaveBeenCalled();
    expect(mockClient.POST).not.toHaveBeenCalled();
  });
});
