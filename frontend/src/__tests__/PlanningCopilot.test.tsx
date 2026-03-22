import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PlanningCopilot } from "../components/Phase5/PlanningCopilot.js";

const mockFetchSuggestion = vi.fn();
const mockApplySuggestion = vi.fn();
const mockUsePlanningCopilot = vi.fn();
let mockPlanningCopilot = true;

vi.mock("../hooks/usePlanningCopilot.js", () => ({ usePlanningCopilot: () => mockUsePlanningCopilot() }));
vi.mock("../context/FeatureFlagContext.js", () => ({ useFeatureFlags: () => ({ planningCopilot: mockPlanningCopilot }) }));

describe("PlanningCopilot", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPlanningCopilot = true;
    mockUsePlanningCopilot.mockReturnValue({ suggestion: null, applyResult: null, suggestionStatus: "idle", applyStatus: "idle", error: null, fetchSuggestion: mockFetchSuggestion, applySuggestion: mockApplySuggestion });
  });

  it("renders nothing when feature flag is disabled", () => {
    mockPlanningCopilot = false;
    const { container } = render(<PlanningCopilot weekStart="2026-03-16" />);
    expect(container.firstChild).toBeNull();
  });

  it("renders members and applies selected suggestions", () => {
    mockUsePlanningCopilot.mockReturnValue({ suggestion: { status: "ok", weekStart: "2026-03-16", summary: { teamCapacityHours: 40, suggestedHours: 32, bufferHours: 8, atRiskOutcomeCount: 1, criticalOutcomeCount: 0, strategicFocusFloor: 0.5, headline: "Focus on key outcomes" }, members: [{ userId: "u1", displayName: "Alice", suggestedCommits: [{ title: "Ship thing", outcomeId: "o1", chessPriority: "KING", estimatedHours: 8, rationale: "Critical path", source: "forecast" }], totalEstimated: 8, realisticCapacity: 12, overcommitRisk: null, strengthSummary: "Strong finisher" }], outcomeAllocations: [], llmRefined: true }, applyResult: null, suggestionStatus: "ok", applyStatus: "idle", error: null, fetchSuggestion: mockFetchSuggestion, applySuggestion: mockApplySuggestion });
    render(<PlanningCopilot weekStart="2026-03-16" />);
    expect(screen.getByTestId("planning-copilot-member-u1")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("planning-copilot-apply"));
    expect(mockApplySuggestion).toHaveBeenCalled();
  });

  it("hides stale suggestion content while loading and shows only the specific error state", () => {
    mockUsePlanningCopilot.mockReturnValue({
      suggestion: {
        status: "ok",
        weekStart: "2026-03-16",
        summary: { teamCapacityHours: 40, suggestedHours: 32, bufferHours: 8, atRiskOutcomeCount: 1, criticalOutcomeCount: 0, strategicFocusFloor: 0.5, headline: "Focus on key outcomes" },
        members: [{ userId: "u1", displayName: "Alice", suggestedCommits: [{ title: "Ship thing", outcomeId: "o1", chessPriority: "KING", estimatedHours: 8, rationale: "Critical path", source: "forecast" }], totalEstimated: 8, realisticCapacity: 12, overcommitRisk: null, strengthSummary: "Strong finisher" }],
        outcomeAllocations: [],
        llmRefined: true,
      },
      applyResult: null,
      suggestionStatus: "loading",
      applyStatus: "idle",
      error: "Network error",
      fetchSuggestion: mockFetchSuggestion,
      applySuggestion: mockApplySuggestion,
    });

    render(<PlanningCopilot weekStart="2026-03-16" />);

    expect(screen.queryByTestId("planning-copilot-member-u1")).not.toBeInTheDocument();
    expect(screen.getByTestId("planning-copilot-loading")).toBeInTheDocument();
    expect(screen.getByTestId("planning-copilot-error")).toHaveTextContent("Network error");
    expect(screen.queryByTestId("planning-copilot-unavailable")).not.toBeInTheDocument();
  });
});
