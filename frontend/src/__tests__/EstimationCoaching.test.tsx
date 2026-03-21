import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { EstimationCoaching } from "../components/CapacityView/EstimationCoaching.js";
import type { EstimationCoachingResponse } from "../hooks/useCapacity.js";

// ─── Mock hooks ───────────────────────────────────────────────────────────────

// Mock the entire useCapacity module so we can control the hook return value.
const mockFetchCoaching = vi.fn();

const mockUseEstimationCoaching = vi.fn(() => ({
  coaching: null as EstimationCoachingResponse | null,
  loading: false,
  error: null as string | null,
  fetchCoaching: mockFetchCoaching,
  clearError: vi.fn(),
}));

vi.mock("../hooks/useCapacity.js", () => ({
  useEstimationCoaching: () => mockUseEstimationCoaching(),
}));

// Mock FeatureFlagContext so the estimationCoaching flag can be toggled.
let mockEstimationCoaching = true;

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({
    suggestRcdo: true,
    draftReconciliation: false,
    managerInsights: false,
    icTrends: true,
    planQualityNudge: false,
    startMyWeek: false,
    suggestNextWork: false,
    dailyCheckIn: false,
    quickUpdate: false,
    userProfile: false,
    capacityTracking: false,
    estimationCoaching: mockEstimationCoaching,
    strategicIntelligence: false,
    predictions: false,
    outcomeUrgency: false,
    strategicSlack: false,
  }),
}));

// ─── Fixtures ─────────────────────────────────────────────────────────────────

function makeCoaching(overrides: Partial<EstimationCoachingResponse> = {}): EstimationCoachingResponse {
  return {
    thisWeekEstimated: 30,
    thisWeekActual: 25,
    accuracyRatio: 0.83,
    overallBias: 1.2,
    confidenceLevel: "MEDIUM",
    categoryInsights: [],
    priorityInsights: [],
    ...overrides,
  };
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("EstimationCoaching", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockEstimationCoaching = true;
    mockFetchCoaching.mockResolvedValue(undefined);
    // Reset the hook mock to its default idle state.
    mockUseEstimationCoaching.mockReturnValue({
      coaching: null,
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
  });

  // ── Feature flag gate ──────────────────────────────────────────────────────

  it("renders nothing when estimationCoaching flag is off", () => {
    mockEstimationCoaching = false;
    const { container } = render(<EstimationCoaching planId="plan-1" />);
    expect(container.firstChild).toBeNull();
  });

  // ── Null / loading / error states ─────────────────────────────────────────

  it("renders nothing when coaching data is null and not loading", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: null,
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    const { container } = render(<EstimationCoaching planId="plan-1" />);
    expect(container.firstChild).toBeNull();
  });

  it("shows loading state while fetching", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: null,
      loading: true,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-1" />);
    expect(screen.getByTestId("estimation-coaching")).toBeInTheDocument();
    expect(screen.getByTestId("estimation-coaching-loading")).toBeInTheDocument();
    expect(screen.getByText(/Loading coaching insights/)).toBeInTheDocument();
  });

  it("shows error state when error is set", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: null,
      loading: false,
      error: "Failed to load coaching data",
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-1" />);
    expect(screen.getByTestId("estimation-coaching")).toBeInTheDocument();
    expect(screen.getByTestId("estimation-coaching-error")).toBeInTheDocument();
    expect(screen.getByText("Failed to load coaching data")).toBeInTheDocument();
  });

  // ── This week estimated vs actual ─────────────────────────────────────────

  it("renders coaching card with this-week estimated vs actual", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: makeCoaching({ thisWeekEstimated: 30, thisWeekActual: 25 }),
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-1" />);

    expect(screen.getByTestId("estimation-coaching")).toBeInTheDocument();
    expect(screen.getByTestId("estimation-coaching-this-week")).toBeInTheDocument();

    const estimated = screen.getByTestId("ec-metric-estimated");
    expect(estimated).toHaveTextContent("30h");

    const actual = screen.getByTestId("ec-metric-actual");
    expect(actual).toHaveTextContent("25h");
  });

  it("renders accuracy ratio metric", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: makeCoaching({ accuracyRatio: 0.83 }),
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-1" />);
    // 0.83 → "83%"
    expect(screen.getByTestId("ec-metric-accuracy")).toHaveTextContent("83%");
  });

  it("renders overall bias metric", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: makeCoaching({ overallBias: 1.2 }),
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-1" />);
    // 1.2 → "1.20×"
    expect(screen.getByTestId("ec-metric-overall-bias")).toHaveTextContent("1.20×");
  });

  it("renders confidence level badge", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: makeCoaching({ confidenceLevel: "HIGH" }),
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-1" />);
    expect(screen.getByTestId("estimation-coaching-confidence")).toHaveTextContent("HIGH");
  });

  // ── Category insights ─────────────────────────────────────────────────────

  it("renders category insights with tips", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: makeCoaching({
        categoryInsights: [
          { category: "DELIVERY", bias: 1.3, tip: "You tend to underestimate delivery tasks." },
          { category: "OPERATIONS", bias: 0.9, tip: null },
        ],
      }),
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-1" />);

    expect(screen.getByTestId("estimation-coaching-categories")).toBeInTheDocument();

    const item0 = screen.getByTestId("ec-category-insight-0");
    expect(item0).toHaveTextContent("DELIVERY");

    // Tip is present for the first insight
    expect(screen.getByTestId("ec-category-tip-0")).toHaveTextContent(
      "You tend to underestimate delivery tasks.",
    );

    const item1 = screen.getByTestId("ec-category-insight-1");
    expect(item1).toHaveTextContent("OPERATIONS");

    // No tip for the second insight
    expect(screen.queryByTestId("ec-category-tip-1")).not.toBeInTheDocument();
  });

  it("does not render category section when there are no category insights", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: makeCoaching({ categoryInsights: [] }),
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-1" />);
    expect(screen.queryByTestId("estimation-coaching-categories")).not.toBeInTheDocument();
  });

  it("renders category bias label for each insight", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: makeCoaching({
        categoryInsights: [{ category: "DELIVERY", bias: 1.25, tip: null }],
      }),
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-1" />);
    // 1.25 → "1.25× · +25% over"
    expect(screen.getByTestId("ec-category-bias-0")).toHaveTextContent("1.25×");
    expect(screen.getByTestId("ec-category-bias-0")).toHaveTextContent("+25% over");
  });

  // ── Priority insights ─────────────────────────────────────────────────────

  it("renders priority insights section when present", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: makeCoaching({
        priorityInsights: [
          { priority: "KING", completionRate: 1.0, sampleSize: 3 },
          { priority: "QUEEN", completionRate: 0.75, sampleSize: 8 },
        ],
      }),
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-1" />);

    expect(screen.getByTestId("estimation-coaching-priorities")).toBeInTheDocument();
    expect(screen.getByTestId("ec-priority-insight-0")).toBeInTheDocument();
    expect(screen.getByTestId("ec-priority-rate-0")).toHaveTextContent("100%");
    expect(screen.getByTestId("ec-priority-rate-1")).toHaveTextContent("75%");
  });

  it("does not render priority section when there are no priority insights", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: makeCoaching({ priorityInsights: [] }),
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-1" />);
    expect(screen.queryByTestId("estimation-coaching-priorities")).not.toBeInTheDocument();
  });

  // ── fetchCoaching is called on mount ─────────────────────────────────────

  it("calls fetchCoaching on mount with the planId", () => {
    mockUseEstimationCoaching.mockReturnValue({
      coaching: null,
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-abc" />);
    expect(mockFetchCoaching).toHaveBeenCalledWith("plan-abc");
  });

  it("does not call fetchCoaching when the flag is off", () => {
    mockEstimationCoaching = false;
    mockUseEstimationCoaching.mockReturnValue({
      coaching: null,
      loading: false,
      error: null,
      fetchCoaching: mockFetchCoaching,
      clearError: vi.fn(),
    });
    render(<EstimationCoaching planId="plan-abc" />);
    expect(mockFetchCoaching).not.toHaveBeenCalled();
  });
});
