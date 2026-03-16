import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { AiSuggestionPanel } from "../components/AiSuggestionPanel.js";
import type { RcdoSuggestion } from "@weekly-commitments/contracts";

function makeSuggestion(overrides: Partial<RcdoSuggestion> = {}): RcdoSuggestion {
  return {
    outcomeId: "outcome-1",
    rallyCryName: "Revenue Growth",
    objectiveName: "Enterprise Sales",
    outcomeName: "Close Q1 deals",
    confidence: 0.87,
    rationale: "Keywords match well",
    ...overrides,
  };
}

describe("AiSuggestionPanel", () => {
  it("renders nothing when idle", () => {
    const { container } = render(
      <AiSuggestionPanel suggestions={[]} status="idle" onAccept={vi.fn()} />,
    );
    expect(container.innerHTML).toBe("");
  });

  it("shows loading indicator", () => {
    render(
      <AiSuggestionPanel suggestions={[]} status="loading" onAccept={vi.fn()} />,
    );
    expect(screen.getByTestId("ai-suggestion-loading")).toBeInTheDocument();
    expect(screen.getByText(/Finding relevant outcomes/)).toBeInTheDocument();
  });

  it("shows rate limit message", () => {
    render(
      <AiSuggestionPanel suggestions={[]} status="rate_limited" onAccept={vi.fn()} />,
    );
    expect(screen.getByTestId("ai-suggestion-rate-limited")).toBeInTheDocument();
  });

  it("renders nothing when unavailable", () => {
    const { container } = render(
      <AiSuggestionPanel suggestions={[]} status="unavailable" onAccept={vi.fn()} />,
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders suggestions with confidence and rationale", () => {
    const suggestion = makeSuggestion();
    render(
      <AiSuggestionPanel suggestions={[suggestion]} status="ok" onAccept={vi.fn()} />,
    );
    expect(screen.getByTestId("ai-suggestion-panel")).toBeInTheDocument();
    // Outcome name is the headline
    expect(screen.getByText(/Close Q1 deals/)).toBeInTheDocument();
    // Breadcrumb shows Rally Cry and Objective
    expect(screen.getByText(/Revenue Growth/)).toBeInTheDocument();
    expect(screen.getByText(/Enterprise Sales/)).toBeInTheDocument();
    // Confidence badge
    expect(screen.getByText(/87%/)).toBeInTheDocument();
    // Rationale is shown on hover — verify it appears on mouseEnter
    fireEvent.mouseEnter(screen.getByTestId("ai-suggestion-0"));
    expect(screen.getByText(/Keywords match well/)).toBeInTheDocument();
  });

  it("calls onAccept when a suggestion is clicked", () => {
    const suggestion = makeSuggestion();
    const onAccept = vi.fn();
    render(
      <AiSuggestionPanel suggestions={[suggestion]} status="ok" onAccept={onAccept} />,
    );
    fireEvent.click(screen.getByTestId("ai-suggestion-0"));
    expect(onAccept).toHaveBeenCalledWith(suggestion);
  });

  it("renders multiple suggestions", () => {
    const suggestions = [
      makeSuggestion({ outcomeId: "a", confidence: 0.9, outcomeName: "Outcome A" }),
      makeSuggestion({ outcomeId: "b", confidence: 0.7, outcomeName: "Outcome B" }),
    ];
    render(
      <AiSuggestionPanel suggestions={suggestions} status="ok" onAccept={vi.fn()} />,
    );
    expect(screen.getByTestId("ai-suggestion-0")).toBeInTheDocument();
    expect(screen.getByTestId("ai-suggestion-1")).toBeInTheDocument();
  });

  it("labels panel as AI Suggestions", () => {
    render(
      <AiSuggestionPanel
        suggestions={[makeSuggestion()]}
        status="ok"
        onAccept={vi.fn()}
      />,
    );
    expect(screen.getByText(/AI Suggestions/)).toBeInTheDocument();
  });
});
