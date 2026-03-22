/**
 * Tests for the effort type distribution additions to MyInsightsPage.
 *
 * Validates that the EffortTypeMix section renders when
 * `trends.effortTypeDistribution` is populated, and is hidden otherwise.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MyInsightsPage } from "../pages/MyInsightsPage.js";
import type { TrendsResponse } from "@weekly-commitments/contracts";

/* ── Mocks ────────────────────────────────────────────────────────────────── */

const mockFetchTrends = vi.fn().mockResolvedValue(undefined);
const mockFetchProfile = vi.fn().mockResolvedValue(undefined);
const mockUseTrends = vi.fn();
const mockUseUserProfile = vi.fn();

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({ icTrends: true, userProfile: false }),
}));

vi.mock("../hooks/useTrends.js", () => ({
  useTrends: () => mockUseTrends(),
}));

vi.mock("../hooks/useUserProfile.js", () => ({
  useUserProfile: () => mockUseUserProfile(),
}));

/* ── Fixtures ─────────────────────────────────────────────────────────────── */

function baseTrends(overrides: Partial<TrendsResponse> = {}): TrendsResponse {
  return {
    weeksAnalyzed: 4,
    windowStart: "2026-02-16",
    windowEnd: "2026-03-09",
    strategicAlignmentRate: 0.75,
    teamStrategicAlignmentRate: 0.6,
    avgCarryForwardPerWeek: 1.5,
    carryForwardStreak: 0,
    avgConfidence: 0.8,
    completionAccuracy: 0.7,
    confidenceAccuracyGap: 0.05,
    avgEstimatedHoursPerWeek: null,
    avgActualHoursPerWeek: null,
    hoursAccuracyRatio: null,
    priorityDistribution: { KING: 0.3, PAWN: 0.7 },
    categoryDistribution: { DELIVERY: 0.8, OPERATIONS: 0.2 },
    weekPoints: [],
    insights: [],
    ...overrides,
  };
}

const noProfileResult = {
  profile: null,
  loading: false,
  error: null,
  fetchProfile: mockFetchProfile,
  clearError: vi.fn(),
};

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("MyInsightsPage — effort type distribution", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseUserProfile.mockReturnValue(noProfileResult);
  });

  it("shows effort type section when effortTypeDistribution is populated", () => {
    mockUseTrends.mockReturnValue({
      trends: baseTrends({
        effortTypeDistribution: { BUILD: 0.5, MAINTAIN: 0.3, COLLABORATE: 0.2 },
      }),
      loading: false,
      error: null,
      fetchTrends: mockFetchTrends,
      clearError: vi.fn(),
    });

    render(<MyInsightsPage />);

    expect(screen.getByTestId("effort-type-section")).toBeInTheDocument();
    expect(screen.getByText("Effort Type Mix")).toBeInTheDocument();
    expect(screen.getByTestId("effort-type-chart")).toBeInTheDocument();
  });

  it("hides effort type section when effortTypeDistribution is absent", () => {
    mockUseTrends.mockReturnValue({
      trends: baseTrends({ effortTypeDistribution: undefined }),
      loading: false,
      error: null,
      fetchTrends: mockFetchTrends,
      clearError: vi.fn(),
    });

    render(<MyInsightsPage />);

    expect(screen.queryByTestId("effort-type-section")).not.toBeInTheDocument();
    // Category mix should still show
    expect(screen.getByText("Category Mix")).toBeInTheDocument();
  });

  it("hides effort type section when effortTypeDistribution is empty", () => {
    mockUseTrends.mockReturnValue({
      trends: baseTrends({ effortTypeDistribution: {} }),
      loading: false,
      error: null,
      fetchTrends: mockFetchTrends,
      clearError: vi.fn(),
    });

    render(<MyInsightsPage />);

    expect(screen.queryByTestId("effort-type-section")).not.toBeInTheDocument();
  });

  it("still renders category mix alongside effort type mix", () => {
    mockUseTrends.mockReturnValue({
      trends: baseTrends({
        effortTypeDistribution: { BUILD: 0.6, LEARN: 0.4 },
      }),
      loading: false,
      error: null,
      fetchTrends: mockFetchTrends,
      clearError: vi.fn(),
    });

    render(<MyInsightsPage />);

    expect(screen.getByTestId("effort-type-section")).toBeInTheDocument();
    expect(screen.getByText("Category Mix")).toBeInTheDocument();
    // Both should be in the distribution section
    expect(screen.getByTestId("distribution-section")).toBeInTheDocument();
  });
});
