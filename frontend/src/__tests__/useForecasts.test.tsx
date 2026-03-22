import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useForecasts } from "../hooks/useForecasts.js";

const mockClient = { GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn(), DELETE: vi.fn(), use: vi.fn() };
let mockTargetDateForecasting = true;

vi.mock("../api/ApiContext.js", () => ({ useApiClient: () => mockClient }));
vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({ targetDateForecasting: mockTargetDateForecasting }),
}));

describe("useForecasts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockTargetDateForecasting = true;
  });

  it("fetches outcome forecasts", async () => {
    mockClient.GET.mockResolvedValue({
      data: { forecasts: [{ outcomeId: "o1", outcomeName: "Outcome", targetDate: null, projectedTargetDate: null, projectedProgressPct: 55, projectedVelocity: null, confidenceScore: 0.7, confidenceBand: "HIGH", forecastStatus: "ON_TRACK", modelVersion: null, contributingFactors: [], recommendations: [], computedAt: null }] },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useForecasts());
    await act(async () => {
      await result.current.fetchForecasts();
    });

    expect(mockClient.GET).toHaveBeenCalledWith("/outcomes/forecasts");
    expect(result.current.forecasts).toHaveLength(1);
  });

  it("fetches a single forecast", async () => {
    mockClient.GET.mockResolvedValue({
      data: { outcomeId: "o1", outcomeName: "Outcome", targetDate: null, projectedTargetDate: null, projectedProgressPct: 55, projectedVelocity: null, confidenceScore: 0.7, confidenceBand: "HIGH", forecastStatus: "ON_TRACK", modelVersion: null, contributingFactors: [], recommendations: [], computedAt: null },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useForecasts());
    await act(async () => {
      await result.current.fetchForecast("o1");
    });

    expect(mockClient.GET).toHaveBeenCalledWith("/outcomes/{outcomeId}/forecast", {
      params: { path: { outcomeId: "o1" } },
    });
    expect(result.current.selectedForecast?.outcomeId).toBe("o1");
  });

  it("clears stale selected forecast before a failed fetch", async () => {
    mockClient.GET
      .mockResolvedValueOnce({
        data: { outcomeId: "o1", outcomeName: "Outcome", targetDate: null, projectedTargetDate: null, projectedProgressPct: 55, projectedVelocity: null, confidenceScore: 0.7, confidenceBand: "HIGH", forecastStatus: "ON_TRACK", modelVersion: null, contributingFactors: [], recommendations: [], computedAt: null },
        response: { status: 200 },
      })
      .mockResolvedValueOnce({
        error: { error: { message: "Not found" } },
        response: { status: 404 },
      });

    const { result } = renderHook(() => useForecasts());
    await act(async () => {
      await result.current.fetchForecast("o1");
    });
    expect(result.current.selectedForecast?.outcomeId).toBe("o1");

    await act(async () => {
      await result.current.fetchForecast("o2");
    });
    expect(result.current.selectedForecast).toBeNull();
    expect(result.current.errorForecast).toBe("Not found");
  });

  it("does nothing when feature flag is disabled", async () => {
    mockTargetDateForecasting = false;
    const { result } = renderHook(() => useForecasts());
    await act(async () => {
      await result.current.fetchForecasts();
      await result.current.fetchForecast("o1");
    });
    expect(mockClient.GET).not.toHaveBeenCalled();
  });
});
