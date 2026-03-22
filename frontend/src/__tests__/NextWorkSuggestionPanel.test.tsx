import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { NextWorkSuggestionPanel } from "../components/NextWorkSuggestionPanel.js";
import { ChessPriority } from "@weekly-commitments/contracts";
import type { NextWorkSuggestion, SuggestionFeedbackRequest } from "@weekly-commitments/contracts";

/* ── Helpers ──────────────────────────────────────────────────────────────── */

function makeSuggestion(overrides: Partial<NextWorkSuggestion> = {}): NextWorkSuggestion {
  return {
    suggestionId: "sugg-1",
    title: "Complete Q2 planning document",
    suggestedOutcomeId: "outcome-1",
    suggestedChessPriority: ChessPriority.QUEEN,
    confidence: 0.85,
    source: "CARRY_FORWARD",
    sourceDetail: "Not completed in week of 2026-03-09",
    rationale: "This item was not done last week.",
    ...overrides,
  };
}

const defaultProps = {
  suggestions: [] as NextWorkSuggestion[],
  status: "idle" as const,
  onAccept: vi.fn().mockResolvedValue(true),
  onFeedback: vi.fn().mockResolvedValue(true),
  onRefresh: vi.fn(),
};

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("NextWorkSuggestionPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    defaultProps.onAccept = vi.fn().mockResolvedValue(true);
    defaultProps.onFeedback = vi.fn().mockResolvedValue(true);
    defaultProps.onRefresh = vi.fn();
  });

  it("renders the panel with header", () => {
    render(<NextWorkSuggestionPanel {...defaultProps} />);

    expect(screen.getByTestId("next-work-suggestion-panel")).toBeInTheDocument();
    expect(screen.getByText("AI-Suggested Work")).toBeInTheDocument();
    expect(screen.getByText("Advisory")).toBeInTheDocument();
  });

  it("shows idle state with fetch button when status is idle", () => {
    render(<NextWorkSuggestionPanel {...defaultProps} status="idle" />);

    expect(screen.getByTestId("next-work-idle")).toBeInTheDocument();
    expect(screen.getByTestId("next-work-fetch-btn")).toBeInTheDocument();
    expect(screen.getByText("Show AI suggestions")).toBeInTheDocument();
  });

  it("calls onRefresh when the fetch button is clicked in idle state", () => {
    const onRefresh = vi.fn();
    render(<NextWorkSuggestionPanel {...defaultProps} status="idle" onRefresh={onRefresh} />);

    fireEvent.click(screen.getByTestId("next-work-fetch-btn"));
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });

  it("calls onRefresh when the refresh button is clicked", () => {
    const onRefresh = vi.fn();
    render(<NextWorkSuggestionPanel {...defaultProps} onRefresh={onRefresh} />);

    fireEvent.click(screen.getByTestId("next-work-refresh-btn"));
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });

  it("shows loading state", () => {
    render(<NextWorkSuggestionPanel {...defaultProps} status="loading" />);

    expect(screen.getByTestId("next-work-loading")).toBeInTheDocument();
    expect(screen.getByText(/Analysing your history/)).toBeInTheDocument();
  });

  it("disables refresh button while loading", () => {
    render(<NextWorkSuggestionPanel {...defaultProps} status="loading" />);

    expect(screen.getByTestId("next-work-refresh-btn")).toBeDisabled();
  });

  it("shows rate limit message", () => {
    render(<NextWorkSuggestionPanel {...defaultProps} status="rate_limited" />);

    expect(screen.getByTestId("next-work-rate-limited")).toBeInTheDocument();
    expect(screen.getByText(/Rate limit reached/)).toBeInTheDocument();
  });

  it("shows unavailable message", () => {
    render(<NextWorkSuggestionPanel {...defaultProps} status="unavailable" />);

    expect(screen.getByTestId("next-work-unavailable")).toBeInTheDocument();
    expect(screen.getByText(/Suggestions unavailable/)).toBeInTheDocument();
  });

  it("shows empty state when status is ok and no suggestions", () => {
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[]} />);

    expect(screen.getByTestId("next-work-empty")).toBeInTheDocument();
    expect(screen.getByText(/No suggestions this week/)).toBeInTheDocument();
  });

  it("renders suggestion with title, priority badge, and source badge", () => {
    const suggestion = makeSuggestion();
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(screen.getByTestId("next-work-suggestion-list")).toBeInTheDocument();
    expect(screen.getByTestId(`next-work-suggestion-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByText("Complete Q2 planning document")).toBeInTheDocument();
    expect(screen.getByTestId(`next-work-priority-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByText(/Queen/i)).toBeInTheDocument();
    expect(screen.getByText(/Carry-forward/i)).toBeInTheDocument();
  });

  it("shows rationale and sourceDetail inside the expanded 'Why?' section", () => {
    const suggestion = makeSuggestion();
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    // Why section is collapsed by default
    expect(screen.queryByTestId(`next-work-why-content-${suggestion.suggestionId}`)).not.toBeInTheDocument();
    expect(screen.queryByTestId(`next-work-rationale-${suggestion.suggestionId}`)).not.toBeInTheDocument();

    // Expand it
    fireEvent.click(screen.getByTestId(`next-work-why-toggle-${suggestion.suggestionId}`));

    expect(screen.getByTestId(`next-work-why-content-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByTestId(`next-work-rationale-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByText(/Not completed in week of 2026-03-09/)).toBeInTheDocument();
    expect(screen.getByText(/This item was not done last week/)).toBeInTheDocument();
  });

  it("renders Accept, Defer, Decline buttons per suggestion", () => {
    const suggestion = makeSuggestion();
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(screen.getByTestId(`next-work-accept-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByTestId(`next-work-defer-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByTestId(`next-work-decline-${suggestion.suggestionId}`)).toBeInTheDocument();
  });

  it("calls onAccept and onFeedback when Accept is clicked", async () => {
    const onAccept = vi.fn().mockResolvedValue(true);
    const onFeedback = vi.fn().mockResolvedValue(true);
    const suggestion = makeSuggestion();

    render(
      <NextWorkSuggestionPanel
        {...defaultProps}
        status="ok"
        suggestions={[suggestion]}
        onAccept={onAccept}
        onFeedback={onFeedback}
      />,
    );

    fireEvent.click(screen.getByTestId(`next-work-accept-${suggestion.suggestionId}`));

    await waitFor(() => {
      expect(onAccept).toHaveBeenCalledWith(suggestion);
      expect(onFeedback).toHaveBeenCalledWith<[SuggestionFeedbackRequest]>({
        suggestionId: suggestion.suggestionId,
        action: "ACCEPT",
        sourceType: suggestion.source,
        sourceDetail: suggestion.sourceDetail,
      });
    });
  });

  it("does not call onFeedback when onAccept returns false", async () => {
    const onAccept = vi.fn().mockResolvedValue(false);
    const onFeedback = vi.fn().mockResolvedValue(true);
    const suggestion = makeSuggestion();

    render(
      <NextWorkSuggestionPanel
        {...defaultProps}
        status="ok"
        suggestions={[suggestion]}
        onAccept={onAccept}
        onFeedback={onFeedback}
      />,
    );

    fireEvent.click(screen.getByTestId(`next-work-accept-${suggestion.suggestionId}`));

    await waitFor(() => {
      expect(onAccept).toHaveBeenCalledWith(suggestion);
    });

    expect(onFeedback).not.toHaveBeenCalled();
  });

  it("calls onFeedback with DEFER when Defer is clicked", async () => {
    const onFeedback = vi.fn().mockResolvedValue(true);
    const suggestion = makeSuggestion();

    render(
      <NextWorkSuggestionPanel
        {...defaultProps}
        status="ok"
        suggestions={[suggestion]}
        onFeedback={onFeedback}
      />,
    );

    fireEvent.click(screen.getByTestId(`next-work-defer-${suggestion.suggestionId}`));

    await waitFor(() => {
      expect(onFeedback).toHaveBeenCalledWith<[SuggestionFeedbackRequest]>({
        suggestionId: suggestion.suggestionId,
        action: "DEFER",
        sourceType: suggestion.source,
        sourceDetail: suggestion.sourceDetail,
      });
    });
  });

  it("opens decline form when Decline is clicked", () => {
    const suggestion = makeSuggestion();
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    fireEvent.click(screen.getByTestId(`next-work-decline-${suggestion.suggestionId}`));

    expect(screen.getByTestId(`next-work-decline-form-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByTestId(`next-work-decline-reason-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByTestId(`next-work-decline-confirm-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByTestId(`next-work-decline-cancel-${suggestion.suggestionId}`)).toBeInTheDocument();
  });

  it("hides action buttons while decline form is open", () => {
    const suggestion = makeSuggestion();
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    fireEvent.click(screen.getByTestId(`next-work-decline-${suggestion.suggestionId}`));

    expect(screen.queryByTestId(`next-work-accept-${suggestion.suggestionId}`)).not.toBeInTheDocument();
    expect(screen.queryByTestId(`next-work-defer-${suggestion.suggestionId}`)).not.toBeInTheDocument();
  });

  it("cancels decline form on cancel click", () => {
    const suggestion = makeSuggestion();
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    fireEvent.click(screen.getByTestId(`next-work-decline-${suggestion.suggestionId}`));
    expect(screen.getByTestId(`next-work-decline-form-${suggestion.suggestionId}`)).toBeInTheDocument();

    fireEvent.click(screen.getByTestId(`next-work-decline-cancel-${suggestion.suggestionId}`));
    expect(screen.queryByTestId(`next-work-decline-form-${suggestion.suggestionId}`)).not.toBeInTheDocument();
    // Action buttons restored
    expect(screen.getByTestId(`next-work-accept-${suggestion.suggestionId}`)).toBeInTheDocument();
  });

  it("calls onFeedback with DECLINE and reason when decline is confirmed", async () => {
    const onFeedback = vi.fn().mockResolvedValue(true);
    const suggestion = makeSuggestion();

    render(
      <NextWorkSuggestionPanel
        {...defaultProps}
        status="ok"
        suggestions={[suggestion]}
        onFeedback={onFeedback}
      />,
    );

    fireEvent.click(screen.getByTestId(`next-work-decline-${suggestion.suggestionId}`));
    fireEvent.change(screen.getByTestId(`next-work-decline-reason-${suggestion.suggestionId}`), {
      target: { value: "Not relevant this sprint" },
    });
    fireEvent.click(screen.getByTestId(`next-work-decline-confirm-${suggestion.suggestionId}`));

    await waitFor(() => {
      expect(onFeedback).toHaveBeenCalledWith<[SuggestionFeedbackRequest]>({
        suggestionId: suggestion.suggestionId,
        action: "DECLINE",
        reason: "Not relevant this sprint",
        sourceType: suggestion.source,
        sourceDetail: suggestion.sourceDetail,
      });
    });
  });

  it("calls onFeedback with DECLINE and null reason when reason is empty", async () => {
    const onFeedback = vi.fn().mockResolvedValue(true);
    const suggestion = makeSuggestion();

    render(
      <NextWorkSuggestionPanel
        {...defaultProps}
        status="ok"
        suggestions={[suggestion]}
        onFeedback={onFeedback}
      />,
    );

    fireEvent.click(screen.getByTestId(`next-work-decline-${suggestion.suggestionId}`));
    // Don't type any reason
    fireEvent.click(screen.getByTestId(`next-work-decline-confirm-${suggestion.suggestionId}`));

    await waitFor(() => {
      expect(onFeedback).toHaveBeenCalledWith(
        expect.objectContaining({ action: "DECLINE", reason: null }),
      );
    });
  });

  it("keeps the decline form open when feedback submission fails", async () => {
    const onFeedback = vi.fn().mockResolvedValue(false);
    const suggestion = makeSuggestion();

    render(
      <NextWorkSuggestionPanel
        {...defaultProps}
        status="ok"
        suggestions={[suggestion]}
        onFeedback={onFeedback}
      />,
    );

    fireEvent.click(screen.getByTestId(`next-work-decline-${suggestion.suggestionId}`));
    fireEvent.click(screen.getByTestId(`next-work-decline-confirm-${suggestion.suggestionId}`));

    await waitFor(() => {
      expect(onFeedback).toHaveBeenCalledTimes(1);
    });

    expect(screen.getByTestId(`next-work-decline-form-${suggestion.suggestionId}`)).toBeInTheDocument();
  });

  it("renders the suggested RCDO label when an outcome is present", () => {
    const suggestion = makeSuggestion({ suggestedOutcomeId: "outcome-1" });
    render(
      <NextWorkSuggestionPanel
        {...defaultProps}
        status="ok"
        suggestions={[suggestion]}
        resolveOutcomeLabel={() => "Win FY26 expansion / Strengthen onboarding / Improve activation"}
      />,
    );

    expect(screen.getByTestId(`next-work-rcdo-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByText(/Improve activation/)).toBeInTheDocument();
  });

  it("renders multiple suggestions", () => {
    const s1 = makeSuggestion({ suggestionId: "s1", title: "Item A" });
    const s2 = makeSuggestion({ suggestionId: "s2", title: "Item B" });

    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[s1, s2]} />);

    expect(screen.getByTestId(`next-work-suggestion-s1`)).toBeInTheDocument();
    expect(screen.getByTestId(`next-work-suggestion-s2`)).toBeInTheDocument();
    expect(screen.getByText("Item A")).toBeInTheDocument();
    expect(screen.getByText("Item B")).toBeInTheDocument();
  });

  it("renders suggestion with COVERAGE_GAP source label", () => {
    const suggestion = makeSuggestion({ source: "COVERAGE_GAP", sourceDetail: "No coverage for outcome" });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(screen.getByText(/Coverage gap/i)).toBeInTheDocument();
  });

  it("renders suggestion without priority badge when no priority", () => {
    const suggestion = makeSuggestion({ suggestedChessPriority: null });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(screen.queryByTestId(`next-work-priority-${suggestion.suggestionId}`)).not.toBeInTheDocument();
  });

  it("shows advisory hint text", () => {
    render(<NextWorkSuggestionPanel {...defaultProps} />);

    expect(screen.getByText(/These suggestions are advisory/)).toBeInTheDocument();
  });

  /* ── Phase 3: External ticket source ─────────────────────────────────── */

  it("renders EXTERNAL_TICKET source label", () => {
    const suggestion = makeSuggestion({
      source: "EXTERNAL_TICKET",
      externalTicketUrl: "https://jira.example.com/PROJ-42",
      externalTicketStatus: "In Progress",
    });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(screen.getByText(/External ticket/i)).toBeInTheDocument();
  });

  it("renders external ticket status badge when source is EXTERNAL_TICKET", () => {
    const suggestion = makeSuggestion({
      source: "EXTERNAL_TICKET",
      externalTicketStatus: "In Progress",
      externalTicketUrl: "https://jira.example.com/PROJ-42",
    });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(
      screen.getByTestId(`next-work-ticket-status-${suggestion.suggestionId}`),
    ).toBeInTheDocument();
    expect(screen.getByText("In Progress")).toBeInTheDocument();
  });

  it("renders external ticket link when source is EXTERNAL_TICKET and URL is present", () => {
    const suggestion = makeSuggestion({
      source: "EXTERNAL_TICKET",
      externalTicketUrl: "https://jira.example.com/PROJ-42",
      externalTicketStatus: "In Progress",
    });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    const ticketLink = screen.getByTestId(`next-work-ticket-link-${suggestion.suggestionId}`);
    expect(ticketLink).toBeInTheDocument();
    const anchor = ticketLink.querySelector("a");
    expect(anchor).toHaveAttribute("href", "https://jira.example.com/PROJ-42");
    expect(anchor).toHaveAttribute("target", "_blank");
    expect(anchor).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("does not render ticket link when externalTicketUrl is absent", () => {
    const suggestion = makeSuggestion({
      source: "EXTERNAL_TICKET",
      externalTicketUrl: null,
      externalTicketStatus: "In Progress",
    });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(
      screen.queryByTestId(`next-work-ticket-link-${suggestion.suggestionId}`),
    ).not.toBeInTheDocument();
  });

  it("does not render ticket status badge when externalTicketStatus is absent", () => {
    const suggestion = makeSuggestion({
      source: "EXTERNAL_TICKET",
      externalTicketUrl: "https://jira.example.com/PROJ-42",
      externalTicketStatus: null,
    });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(
      screen.queryByTestId(`next-work-ticket-status-${suggestion.suggestionId}`),
    ).not.toBeInTheDocument();
  });

  it("does not render ticket link or status badge for non-EXTERNAL_TICKET source", () => {
    const suggestion = makeSuggestion({
      source: "CARRY_FORWARD",
      externalTicketUrl: "https://jira.example.com/PROJ-42",
      externalTicketStatus: "In Progress",
    });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(
      screen.queryByTestId(`next-work-ticket-link-${suggestion.suggestionId}`),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId(`next-work-ticket-status-${suggestion.suggestionId}`),
    ).not.toBeInTheDocument();
  });

  /* ── Phase 2: Confidence display ──────────────────────────────────────── */

  it("shows AI-generated label for each suggestion", () => {
    const suggestion = makeSuggestion();
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(screen.getByTestId(`next-work-ai-label-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByText("AI-generated")).toBeInTheDocument();
  });

  it("shows confidence percentage rounded to nearest integer", () => {
    const suggestion = makeSuggestion({ confidence: 0.85 });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(screen.getByTestId(`next-work-confidence-pct-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByText("85%")).toBeInTheDocument();
  });

  it("rounds confidence percentage correctly (e.g. 0.777 → 78%)", () => {
    const suggestion = makeSuggestion({ confidence: 0.777 });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(screen.getByText("78%")).toBeInTheDocument();
  });

  it("renders confidence bar element with correct aria attributes", () => {
    const suggestion = makeSuggestion({ confidence: 0.6 });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    const bar = screen.getByTestId(`next-work-confidence-bar-${suggestion.suggestionId}`);
    expect(bar).toBeInTheDocument();
    expect(bar).toHaveAttribute("role", "progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "60");
    expect(bar).toHaveAttribute("aria-valuemin", "0");
    expect(bar).toHaveAttribute("aria-valuemax", "100");
  });

  it("shows confidence row for each suggestion", () => {
    const suggestion = makeSuggestion();
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(screen.getByTestId(`next-work-confidence-row-${suggestion.suggestionId}`)).toBeInTheDocument();
  });

  /* ── Phase 2: 'Why this suggestion?' expandable section ──────────────── */

  it("renders 'Why this suggestion?' toggle button for each suggestion", () => {
    const suggestion = makeSuggestion();
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    const toggle = screen.getByTestId(`next-work-why-toggle-${suggestion.suggestionId}`);
    expect(toggle).toBeInTheDocument();
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(toggle).toHaveTextContent(/Why this suggestion/i);
  });

  it("'Why?' section is collapsed by default — content not in DOM", () => {
    const suggestion = makeSuggestion();
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    expect(screen.queryByTestId(`next-work-why-content-${suggestion.suggestionId}`)).not.toBeInTheDocument();
    expect(screen.queryByTestId(`next-work-rationale-${suggestion.suggestionId}`)).not.toBeInTheDocument();
  });

  it("expands 'Why?' section when toggle is clicked", () => {
    const suggestion = makeSuggestion();
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    const toggle = screen.getByTestId(`next-work-why-toggle-${suggestion.suggestionId}`);
    fireEvent.click(toggle);

    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByTestId(`next-work-why-content-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByText(/Not completed in week of 2026-03-09/)).toBeInTheDocument();
    expect(screen.getByTestId(`next-work-rationale-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.getByText(/This item was not done last week/)).toBeInTheDocument();
  });

  it("collapses 'Why?' section when toggle is clicked again", () => {
    const suggestion = makeSuggestion();
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    const toggle = screen.getByTestId(`next-work-why-toggle-${suggestion.suggestionId}`);

    fireEvent.click(toggle);
    expect(screen.getByTestId(`next-work-why-content-${suggestion.suggestionId}`)).toBeInTheDocument();

    fireEvent.click(toggle);
    expect(screen.queryByTestId(`next-work-why-content-${suggestion.suggestionId}`)).not.toBeInTheDocument();
    expect(toggle).toHaveAttribute("aria-expanded", "false");
  });

  it("each suggestion has independent 'Why?' expand state", () => {
    const s1 = makeSuggestion({ suggestionId: "s1", title: "Item A" });
    const s2 = makeSuggestion({ suggestionId: "s2", title: "Item B" });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[s1, s2]} />);

    // Expand s1 only
    fireEvent.click(screen.getByTestId("next-work-why-toggle-s1"));

    expect(screen.getByTestId("next-work-why-content-s1")).toBeInTheDocument();
    expect(screen.queryByTestId("next-work-why-content-s2")).not.toBeInTheDocument();
  });

  it("does not render rationale section in 'Why?' when rationale is empty", () => {
    const suggestion = makeSuggestion({ rationale: "" });
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[suggestion]} />);

    fireEvent.click(screen.getByTestId(`next-work-why-toggle-${suggestion.suggestionId}`));

    expect(screen.getByTestId(`next-work-why-content-${suggestion.suggestionId}`)).toBeInTheDocument();
    expect(screen.queryByTestId(`next-work-rationale-${suggestion.suggestionId}`)).not.toBeInTheDocument();
  });

  /* ── Phase 2: Sort by confidence descending ──────────────────────────── */

  it("renders suggestions sorted by confidence descending", () => {
    const low = makeSuggestion({ suggestionId: "low", title: "Low confidence", confidence: 0.4 });
    const high = makeSuggestion({ suggestionId: "high", title: "High confidence", confidence: 0.9 });
    const mid = makeSuggestion({ suggestionId: "mid", title: "Mid confidence", confidence: 0.65 });

    // Pass in unsorted order
    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={[low, high, mid]} />);

    const items = screen.getAllByRole("listitem");
    expect(items[0]).toHaveTextContent("High confidence");
    expect(items[1]).toHaveTextContent("Mid confidence");
    expect(items[2]).toHaveTextContent("Low confidence");
  });

  it("does not mutate the original suggestions array when sorting", () => {
    const low = makeSuggestion({ suggestionId: "low", title: "Low", confidence: 0.3 });
    const high = makeSuggestion({ suggestionId: "high", title: "High", confidence: 0.9 });
    const original = [low, high];

    render(<NextWorkSuggestionPanel {...defaultProps} status="ok" suggestions={original} />);

    // Original array order should be unchanged
    expect(original[0].suggestionId).toBe("low");
    expect(original[1].suggestionId).toBe("high");
  });
});
