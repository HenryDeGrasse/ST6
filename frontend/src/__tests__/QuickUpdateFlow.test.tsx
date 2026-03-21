import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QuickUpdateFlow } from "../components/QuickUpdate/QuickUpdateFlow.js";
import type {
  QuickUpdateCommitment,
  QuickUpdateFlowProps,
} from "../components/QuickUpdate/QuickUpdateFlow.js";
import { FeatureFlagProvider } from "../context/FeatureFlagContext.js";

// ── Mock useQuickUpdate ──────────────────────────────────────────────────────

const mockSubmitBatchUpdate = vi.fn();
const mockFetchCheckInOptions = vi.fn();

vi.mock("../hooks/useQuickUpdate.js", () => ({
  useQuickUpdate: () => ({
    loading: false,
    error: null,
    submitBatchUpdate: mockSubmitBatchUpdate,
    fetchCheckInOptions: mockFetchCheckInOptions,
    clearError: vi.fn(),
  }),
}));

// ── Fixtures ─────────────────────────────────────────────────────────────────

function makeCommitment(overrides: Partial<QuickUpdateCommitment> = {}): QuickUpdateCommitment {
  return {
    id: "commit-1",
    title: "Deploy API v2",
    category: "Engineering",
    chessPriority: "QUEEN",
    outcomeName: "Platform Stability",
    lastCheckInStatus: "ON_TRACK",
    lastCheckInNote: "Making progress",
    lastCheckInDaysAgo: 3,
    ...overrides,
  };
}

const defaultCommitments: QuickUpdateCommitment[] = [
  makeCommitment({ id: "commit-1", title: "Deploy API v2" }),
  makeCommitment({ id: "commit-2", title: "Write unit tests" }),
  makeCommitment({ id: "commit-3", title: "Update docs" }),
];

const defaultProps: QuickUpdateFlowProps = {
  commitments: defaultCommitments,
  planId: "plan-1",
  onComplete: vi.fn(),
  onClose: vi.fn(),
};

// ── Helper ────────────────────────────────────────────────────────────────────

function renderWithFlags(
  ui: React.ReactElement,
  flags: { quickUpdate: boolean } = { quickUpdate: true },
) {
  return render(<FeatureFlagProvider flags={flags}>{ui}</FeatureFlagProvider>);
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("QuickUpdateFlow", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // AI options fetch resolves to null by default (no chips to render)
    mockFetchCheckInOptions.mockResolvedValue(null);
    // Batch submit resolves to a success response by default
    mockSubmitBatchUpdate.mockResolvedValue({ updatedCount: 3, entries: [] });
  });

  // ── (1) Feature-flag gate ────────────────────────────────────────────────

  it("renders nothing when quickUpdate flag is false", () => {
    const { container } = renderWithFlags(
      <QuickUpdateFlow {...defaultProps} />,
      { quickUpdate: false },
    );
    expect(container.innerHTML).toBe("");
  });

  // ── (2) First card renders commitment title ───────────────────────────────

  it("renders first commitment card with title", () => {
    renderWithFlags(<QuickUpdateFlow {...defaultProps} />);
    const card = screen.getByTestId("quick-update-card");
    expect(card).toBeInTheDocument();
    expect(card).toHaveTextContent("Deploy API v2");
  });

  // ── (3) Progress indicator ───────────────────────────────────────────────

  it("shows progress indicator '1 of 3'", () => {
    renderWithFlags(<QuickUpdateFlow {...defaultProps} />);
    expect(screen.getByTestId("quick-update-progress")).toHaveTextContent("1 of 3");
  });

  // ── (4) Four status buttons — no Dropped ────────────────────────────────

  it("renders all four ProgressStatus buttons with correct labels and no Dropped button", () => {
    renderWithFlags(<QuickUpdateFlow {...defaultProps} />);

    expect(screen.getByTestId("quick-update-status-ON_TRACK")).toBeInTheDocument();
    expect(screen.getByTestId("quick-update-status-AT_RISK")).toBeInTheDocument();
    expect(screen.getByTestId("quick-update-status-BLOCKED")).toBeInTheDocument();
    expect(screen.getByTestId("quick-update-status-DONE_EARLY")).toBeInTheDocument();

    expect(screen.getByText("On Track")).toBeInTheDocument();
    expect(screen.getByText("At Risk")).toBeInTheDocument();
    expect(screen.getByText("Blocked")).toBeInTheDocument();
    expect(screen.getByText("Done Early")).toBeInTheDocument();

    expect(screen.queryByText("Dropped")).not.toBeInTheDocument();
  });

  // ── (5) Next advances to second card ────────────────────────────────────

  it("clicking Next advances to the second card", () => {
    renderWithFlags(<QuickUpdateFlow {...defaultProps} />);

    fireEvent.click(screen.getByTestId("quick-update-next"));

    expect(screen.getByTestId("quick-update-card")).toHaveTextContent("Write unit tests");
    expect(screen.getByTestId("quick-update-progress")).toHaveTextContent("2 of 3");
  });

  // ── (6) Previous goes back ───────────────────────────────────────────────

  it("clicking Previous returns to the first card after advancing", () => {
    renderWithFlags(<QuickUpdateFlow {...defaultProps} />);

    fireEvent.click(screen.getByTestId("quick-update-next"));
    expect(screen.getByTestId("quick-update-card")).toHaveTextContent("Write unit tests");

    fireEvent.click(screen.getByTestId("quick-update-prev"));
    expect(screen.getByTestId("quick-update-card")).toHaveTextContent("Deploy API v2");
  });

  // ── (7) Selecting status stores update ──────────────────────────────────

  it("selecting a status marks that button as pressed", () => {
    renderWithFlags(<QuickUpdateFlow {...defaultProps} />);

    const onTrackBtn = screen.getByTestId("quick-update-status-ON_TRACK");
    expect(onTrackBtn).toHaveAttribute("aria-pressed", "false");

    fireEvent.click(onTrackBtn);

    expect(onTrackBtn).toHaveAttribute("aria-pressed", "true");
    // Other buttons remain unpressed
    expect(screen.getByTestId("quick-update-status-AT_RISK")).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(screen.getByTestId("quick-update-status-BLOCKED")).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(screen.getByTestId("quick-update-status-DONE_EARLY")).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  // ── (8) Typing custom note ───────────────────────────────────────────────

  it("typing a custom note stores it in the textarea", () => {
    renderWithFlags(<QuickUpdateFlow {...defaultProps} />);

    const textarea = screen.getByTestId("quick-update-note-input");
    fireEvent.change(textarea, { target: { value: "Making great progress on this task" } });

    expect(textarea).toHaveValue("Making great progress on this task");
  });

  // ── (9) Submit All calls submitBatchUpdate with correct updates ──────────

  it("Submit All on the last card calls submitBatchUpdate with correct updates", async () => {
    renderWithFlags(<QuickUpdateFlow {...defaultProps} />);

    // Card 1 — select On Track
    fireEvent.click(screen.getByTestId("quick-update-status-ON_TRACK"));

    // Navigate to card 2 — select At Risk
    fireEvent.click(screen.getByTestId("quick-update-next"));
    fireEvent.click(screen.getByTestId("quick-update-status-AT_RISK"));

    // Navigate to card 3 (last) — select Blocked
    fireEvent.click(screen.getByTestId("quick-update-next"));
    fireEvent.click(screen.getByTestId("quick-update-status-BLOCKED"));

    // Submit All is shown on the last card
    const submitBtn = screen.getByTestId("quick-update-submit");
    expect(submitBtn).toHaveTextContent("Submit All");
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(mockSubmitBatchUpdate).toHaveBeenCalledWith("plan-1", [
        { commitId: "commit-1", status: "ON_TRACK", note: "" },
        { commitId: "commit-2", status: "AT_RISK", note: "" },
        { commitId: "commit-3", status: "BLOCKED", note: "" },
      ]);
    });
  });

  // ── (10) Arrow-key navigation ────────────────────────────────────────────

  it("ArrowRight key advances to the next card", () => {
    renderWithFlags(<QuickUpdateFlow {...defaultProps} />);

    expect(screen.getByTestId("quick-update-card")).toHaveTextContent("Deploy API v2");

    fireEvent.keyDown(window, { key: "ArrowRight" });

    expect(screen.getByTestId("quick-update-card")).toHaveTextContent("Write unit tests");
  });

  it("ArrowLeft key goes back to the previous card", () => {
    renderWithFlags(<QuickUpdateFlow {...defaultProps} />);

    // First advance
    fireEvent.keyDown(window, { key: "ArrowRight" });
    expect(screen.getByTestId("quick-update-card")).toHaveTextContent("Write unit tests");

    // Then go back
    fireEvent.keyDown(window, { key: "ArrowLeft" });
    expect(screen.getByTestId("quick-update-card")).toHaveTextContent("Deploy API v2");
  });
});
