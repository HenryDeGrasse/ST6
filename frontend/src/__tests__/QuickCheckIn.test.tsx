import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QuickCheckIn } from "../components/QuickCheckIn.js";
import type { CheckInEntry } from "@weekly-commitments/contracts";

/* ── Fixtures ─────────────────────────────────────────────────────────────── */

function makeEntry(overrides: Partial<CheckInEntry> = {}): CheckInEntry {
  return {
    id: "entry-1",
    commitId: "commit-1",
    status: "ON_TRACK",
    note: "Looking good",
    createdAt: "2026-03-11T09:00:00.000Z",
    ...overrides,
  };
}

const defaultProps = {
  commitId: "commit-1",
  commitTitle: "Deploy API v2",
  entries: [] as CheckInEntry[],
  loading: false,
  error: null,
  onCheckIn: vi.fn<() => Promise<boolean>>().mockResolvedValue(true),
  onClose: vi.fn(),
};

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("QuickCheckIn", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── Renders correctly ──────────────────────────────────────────────────────

  it("renders the container with the correct commit ID attribute", () => {
    render(<QuickCheckIn {...defaultProps} />);

    const container = screen.getByTestId("quick-check-in");
    expect(container).toHaveAttribute("data-commit-id", "commit-1");
  });

  it("renders the commit title", () => {
    render(<QuickCheckIn {...defaultProps} />);

    expect(screen.getByTestId("check-in-commit-title")).toHaveTextContent("Deploy API v2");
  });

  it("renders all four status buttons", () => {
    render(<QuickCheckIn {...defaultProps} />);

    expect(screen.getByTestId("check-in-status-on_track")).toBeInTheDocument();
    expect(screen.getByTestId("check-in-status-at_risk")).toBeInTheDocument();
    expect(screen.getByTestId("check-in-status-blocked")).toBeInTheDocument();
    expect(screen.getByTestId("check-in-status-done_early")).toBeInTheDocument();
  });

  it("renders the status button labels", () => {
    render(<QuickCheckIn {...defaultProps} />);

    expect(screen.getByText("On Track")).toBeInTheDocument();
    expect(screen.getByText("At Risk")).toBeInTheDocument();
    expect(screen.getByText("Blocked")).toBeInTheDocument();
    expect(screen.getByText("Done Early")).toBeInTheDocument();
  });

  it("renders the note textarea", () => {
    render(<QuickCheckIn {...defaultProps} />);

    expect(screen.getByTestId("check-in-note")).toBeInTheDocument();
  });

  it("renders the submit button", () => {
    render(<QuickCheckIn {...defaultProps} />);

    expect(screen.getByTestId("check-in-submit")).toBeInTheDocument();
    expect(screen.getByTestId("check-in-submit")).toHaveTextContent("Check In");
  });

  it("renders a close button", () => {
    render(<QuickCheckIn {...defaultProps} />);

    expect(screen.getByTestId("check-in-close")).toBeInTheDocument();
  });

  // ── Status selection ───────────────────────────────────────────────────────

  it("status buttons are initially not pressed", () => {
    render(<QuickCheckIn {...defaultProps} />);

    expect(screen.getByTestId("check-in-status-on_track")).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByTestId("check-in-status-at_risk")).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByTestId("check-in-status-blocked")).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByTestId("check-in-status-done_early")).toHaveAttribute("aria-pressed", "false");
  });

  it("sets aria-pressed=true on the selected status button", () => {
    render(<QuickCheckIn {...defaultProps} />);

    fireEvent.click(screen.getByTestId("check-in-status-at_risk"));

    expect(screen.getByTestId("check-in-status-at_risk")).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("check-in-status-on_track")).toHaveAttribute("aria-pressed", "false");
  });

  it("changes selection when a different status is clicked", () => {
    render(<QuickCheckIn {...defaultProps} />);

    fireEvent.click(screen.getByTestId("check-in-status-on_track"));
    fireEvent.click(screen.getByTestId("check-in-status-blocked"));

    expect(screen.getByTestId("check-in-status-blocked")).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("check-in-status-on_track")).toHaveAttribute("aria-pressed", "false");
  });

  // ── Submit button disabled state ───────────────────────────────────────────

  it("submit button is disabled when no status is selected", () => {
    render(<QuickCheckIn {...defaultProps} />);

    expect(screen.getByTestId("check-in-submit")).toBeDisabled();
  });

  it("submit button is enabled after a status is selected", () => {
    render(<QuickCheckIn {...defaultProps} />);

    fireEvent.click(screen.getByTestId("check-in-status-on_track"));

    expect(screen.getByTestId("check-in-submit")).not.toBeDisabled();
  });

  it("submit button is disabled while loading", () => {
    render(<QuickCheckIn {...defaultProps} loading={true} />);

    fireEvent.click(screen.getByTestId("check-in-status-on_track"));

    // Even after selection, loading disables submit
    expect(screen.getByTestId("check-in-submit")).toBeDisabled();
  });

  it("submit button shows 'Saving…' while loading", () => {
    render(<QuickCheckIn {...defaultProps} loading={true} />);

    expect(screen.getByTestId("check-in-submit")).toHaveTextContent("Saving…");
  });

  // ── Note input ─────────────────────────────────────────────────────────────

  it("note textarea accepts user input", () => {
    render(<QuickCheckIn {...defaultProps} />);

    const textarea = screen.getByTestId("check-in-note");
    fireEvent.change(textarea, { target: { value: "Waiting on PR approval" } });

    expect(textarea).toHaveValue("Waiting on PR approval");
  });

  it("note textarea is disabled while loading", () => {
    render(<QuickCheckIn {...defaultProps} loading={true} />);

    expect(screen.getByTestId("check-in-note")).toBeDisabled();
  });

  // ── Error display ──────────────────────────────────────────────────────────

  it("does not show error when error is null", () => {
    render(<QuickCheckIn {...defaultProps} />);

    expect(screen.queryByTestId("check-in-error")).not.toBeInTheDocument();
  });

  it("shows error message when error is set", () => {
    render(<QuickCheckIn {...defaultProps} error="Something went wrong" />);

    expect(screen.getByTestId("check-in-error")).toHaveTextContent("Something went wrong");
  });

  // ── Form submission ────────────────────────────────────────────────────────

  it("calls onCheckIn with status and note when submitted", async () => {
    const onCheckIn = vi.fn().mockResolvedValue(true);
    render(<QuickCheckIn {...defaultProps} onCheckIn={onCheckIn} />);

    fireEvent.click(screen.getByTestId("check-in-status-at_risk"));
    fireEvent.change(screen.getByTestId("check-in-note"), {
      target: { value: "PR blocked on review" },
    });
    fireEvent.click(screen.getByTestId("check-in-submit"));

    await waitFor(() => {
      expect(onCheckIn).toHaveBeenCalledWith({
        status: "AT_RISK",
        note: "PR blocked on review",
      });
    });
  });

  it("omits note from request when textarea is empty", async () => {
    const onCheckIn = vi.fn().mockResolvedValue(true);
    render(<QuickCheckIn {...defaultProps} onCheckIn={onCheckIn} />);

    fireEvent.click(screen.getByTestId("check-in-status-on_track"));
    fireEvent.click(screen.getByTestId("check-in-submit"));

    await waitFor(() => {
      expect(onCheckIn).toHaveBeenCalledWith({ status: "ON_TRACK" });
    });
  });

  it("omits note from request when textarea has only whitespace", async () => {
    const onCheckIn = vi.fn().mockResolvedValue(true);
    render(<QuickCheckIn {...defaultProps} onCheckIn={onCheckIn} />);

    fireEvent.click(screen.getByTestId("check-in-status-blocked"));
    fireEvent.change(screen.getByTestId("check-in-note"), {
      target: { value: "   " },
    });
    fireEvent.click(screen.getByTestId("check-in-submit"));

    await waitFor(() => {
      expect(onCheckIn).toHaveBeenCalledWith({ status: "BLOCKED" });
    });
  });

  it("trims whitespace from note before sending", async () => {
    const onCheckIn = vi.fn().mockResolvedValue(true);
    render(<QuickCheckIn {...defaultProps} onCheckIn={onCheckIn} />);

    fireEvent.click(screen.getByTestId("check-in-status-on_track"));
    fireEvent.change(screen.getByTestId("check-in-note"), {
      target: { value: "  some note  " },
    });
    fireEvent.click(screen.getByTestId("check-in-submit"));

    await waitFor(() => {
      expect(onCheckIn).toHaveBeenCalledWith({ status: "ON_TRACK", note: "some note" });
    });
  });

  it("shows success indicator after a successful submission", async () => {
    const onCheckIn = vi.fn().mockResolvedValue(true);
    render(<QuickCheckIn {...defaultProps} onCheckIn={onCheckIn} />);

    fireEvent.click(screen.getByTestId("check-in-status-done_early"));
    fireEvent.click(screen.getByTestId("check-in-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("check-in-success")).toBeInTheDocument();
    });
  });

  it("does not show success indicator before submission", () => {
    render(<QuickCheckIn {...defaultProps} />);

    expect(screen.queryByTestId("check-in-success")).not.toBeInTheDocument();
  });

  it("resets status selection and note after successful submission", async () => {
    const onCheckIn = vi.fn().mockResolvedValue(true);
    render(<QuickCheckIn {...defaultProps} onCheckIn={onCheckIn} />);

    fireEvent.click(screen.getByTestId("check-in-status-at_risk"));
    fireEvent.change(screen.getByTestId("check-in-note"), {
      target: { value: "Some note" },
    });
    fireEvent.click(screen.getByTestId("check-in-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("check-in-status-at_risk")).toHaveAttribute("aria-pressed", "false");
    });

    expect(screen.getByTestId("check-in-note")).toHaveValue("");
    expect(screen.getByTestId("check-in-submit")).toBeDisabled();
  });

  it("does not reset form when onCheckIn returns false", async () => {
    const onCheckIn = vi.fn().mockResolvedValue(false);
    render(<QuickCheckIn {...defaultProps} onCheckIn={onCheckIn} />);

    fireEvent.click(screen.getByTestId("check-in-status-blocked"));
    fireEvent.change(screen.getByTestId("check-in-note"), {
      target: { value: "Stuck" },
    });
    fireEvent.click(screen.getByTestId("check-in-submit"));

    await waitFor(() => expect(onCheckIn).toHaveBeenCalledOnce());

    expect(screen.getByTestId("check-in-status-blocked")).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("check-in-note")).toHaveValue("Stuck");
    expect(screen.queryByTestId("check-in-success")).not.toBeInTheDocument();
  });

  // ── Close button ───────────────────────────────────────────────────────────

  it("calls onClose when the close button is clicked", () => {
    const onClose = vi.fn();
    render(<QuickCheckIn {...defaultProps} onClose={onClose} />);

    fireEvent.click(screen.getByTestId("check-in-close"));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  // ── History timeline ───────────────────────────────────────────────────────

  it("does not render history section when entries are empty", () => {
    render(<QuickCheckIn {...defaultProps} entries={[]} />);

    expect(screen.queryByTestId("check-in-history")).not.toBeInTheDocument();
  });

  it("renders history section when entries exist", () => {
    const entries = [makeEntry()];
    render(<QuickCheckIn {...defaultProps} entries={entries} />);

    expect(screen.getByTestId("check-in-history")).toBeInTheDocument();
  });

  it("renders each entry as a list item", () => {
    const entries = [
      makeEntry({ id: "e1", status: "ON_TRACK" }),
      makeEntry({ id: "e2", status: "AT_RISK" }),
    ];
    render(<QuickCheckIn {...defaultProps} entries={entries} />);

    expect(screen.getByTestId("check-in-entry-e1")).toBeInTheDocument();
    expect(screen.getByTestId("check-in-entry-e2")).toBeInTheDocument();
  });

  it("displays the entry status label for each entry", () => {
    const entries = [makeEntry({ id: "e1", status: "BLOCKED" })];
    render(<QuickCheckIn {...defaultProps} entries={entries} />);

    expect(screen.getByTestId("check-in-entry-status-e1")).toHaveTextContent("Blocked");
  });

  it("displays the entry note when it exists", () => {
    const entries = [makeEntry({ id: "e1", note: "Waiting on design review" })];
    render(<QuickCheckIn {...defaultProps} entries={entries} />);

    expect(screen.getByTestId("check-in-entry-note-e1")).toHaveTextContent("Waiting on design review");
  });

  it("does not render a note element when entry note is empty", () => {
    const entries = [makeEntry({ id: "e1", note: "" })];
    render(<QuickCheckIn {...defaultProps} entries={entries} />);

    expect(screen.queryByTestId("check-in-entry-note-e1")).not.toBeInTheDocument();
  });

  it("shows entries in reverse chronological order (newest first)", () => {
    const entries = [
      makeEntry({ id: "e1", note: "First entry" }),
      makeEntry({ id: "e2", note: "Second entry" }),
      makeEntry({ id: "e3", note: "Third entry" }),
    ];
    render(<QuickCheckIn {...defaultProps} entries={entries} />);

    const historyEl = screen.getByTestId("check-in-history");
    const notes = historyEl.querySelectorAll("[data-testid^='check-in-entry-note-']");

    // Reversed: third first, then second, then first
    expect(notes[0]).toHaveTextContent("Third entry");
    expect(notes[1]).toHaveTextContent("Second entry");
    expect(notes[2]).toHaveTextContent("First entry");
  });

  it("does not call onCheckIn when submit is clicked without a status", () => {
    const onCheckIn = vi.fn();
    render(<QuickCheckIn {...defaultProps} onCheckIn={onCheckIn} />);

    // Do NOT click a status first
    fireEvent.click(screen.getByTestId("check-in-submit"));

    expect(onCheckIn).not.toHaveBeenCalled();
  });

  // ── DONE_EARLY status ──────────────────────────────────────────────────────

  it("allows selecting DONE_EARLY status", async () => {
    const onCheckIn = vi.fn().mockResolvedValue(true);
    render(<QuickCheckIn {...defaultProps} onCheckIn={onCheckIn} />);

    fireEvent.click(screen.getByTestId("check-in-status-done_early"));

    expect(screen.getByTestId("check-in-status-done_early")).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("check-in-submit")).not.toBeDisabled();
  });
});
