import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { IssueDetailPanel } from "../components/IssueDetailPanel.js";
import type { IssueDetailResponse } from "@weekly-commitments/contracts";
import { IssueStatus, EffortType, ChessPriority, IssueActivityType } from "@weekly-commitments/contracts";

// Mock useRcdo so the component doesn't need ApiProvider
vi.mock("../hooks/useRcdo.js", () => ({
  useRcdo: () => ({
    tree: [
      {
        id: "cry-1",
        name: "Scale to $500M ARR",
        objectives: [
          {
            id: "obj-1",
            name: "Accelerate enterprise pipeline",
            rallyCryId: "cry-1",
            outcomes: [
              { id: "outcome-1", name: "Reduce sales cycle by 20%", objectiveId: "obj-1" },
            ],
          },
        ],
      },
    ],
    searchResults: [],
    fetchTree: vi.fn(),
    search: vi.fn(),
    clearSearch: vi.fn(),
  }),
}));

const MOCK_DETAIL: IssueDetailResponse = {
  issue: {
    id: "issue-1",
    orgId: "org-1",
    teamId: "team-1",
    issueKey: "ENG-1",
    sequenceNumber: 1,
    title: "Build enterprise onboarding wizard",
    description: "Multi-step wizard with SSO support.",
    effortType: EffortType.BUILD,
    estimatedHours: 24,
    chessPriority: ChessPriority.KING,
    outcomeId: null,
    nonStrategicReason: null,
    creatorUserId: "user-carol",
    assigneeUserId: "user-alice",
    blockedByIssueId: null,
    status: IssueStatus.IN_PROGRESS,
    aiRecommendedRank: null,
    aiRankRationale: null,
    aiSuggestedEffortType: null,
    version: 1,
    createdAt: "2026-03-01T10:00:00Z",
    updatedAt: "2026-03-15T12:00:00Z",
    archivedAt: null,
  },
  activities: [
    {
      id: "act-1",
      orgId: "org-1",
      issueId: "issue-1",
      actorUserId: "user-carol",
      activityType: IssueActivityType.CREATED,
      oldValue: null,
      newValue: null,
      commentText: null,
      hoursLogged: null,
      metadata: {},
      createdAt: "2026-03-01T10:00:00Z",
    },
  ],
};

const MEMBERS = [
  { userId: "user-alice", displayName: "Alice Chen" },
  { userId: "user-bob", displayName: "Bob Martinez" },
  { userId: "user-carol", displayName: "Carol Park" },
];

describe("IssueDetailPanel", () => {
  let onClose: ReturnType<typeof vi.fn>;
  let onFetchDetail: ReturnType<typeof vi.fn>;
  let onAddComment: ReturnType<typeof vi.fn>;
  let onLogTime: ReturnType<typeof vi.fn>;
  let onAssign: ReturnType<typeof vi.fn>;
  let onUpdate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    onClose = vi.fn();
    onFetchDetail = vi.fn().mockResolvedValue(MOCK_DETAIL);
    onAddComment = vi.fn().mockResolvedValue(undefined);
    onLogTime = vi.fn().mockResolvedValue(undefined);
    onAssign = vi.fn().mockResolvedValue(undefined);
    onUpdate = vi.fn().mockResolvedValue(undefined);
  });

  function renderPanel(issueId = "issue-1", extraProps: Record<string, unknown> = {}) {
    return render(
      <IssueDetailPanel
        issueId={issueId}
        onClose={onClose}
        onFetchDetail={onFetchDetail}
        onAddComment={onAddComment}
        onLogTime={onLogTime}
        onAssign={onAssign}
        teamMembers={MEMBERS}
        {...extraProps}
      />,
    );
  }

  // ─── Bug: gear overlaps close button ──────────────────────────────────────

  it("renders a close button with accessible label", async () => {
    renderPanel();
    await screen.findByTestId("issue-detail-close");
    const closeBtn = screen.getByTestId("issue-detail-close");
    expect(closeBtn).toBeInTheDocument();
    expect(closeBtn).toHaveAttribute("aria-label", "Close issue detail");
    expect(closeBtn).toHaveTextContent("×");
    expect(closeBtn).not.toHaveTextContent("\\u2715");
    // Should NOT have z-index that forces it to conflict with fixed-position gear
    expect(closeBtn).not.toHaveStyle({ position: "fixed" });
  });

  it("calls onClose when close button is clicked", async () => {
    renderPanel();
    await screen.findByTestId("issue-detail-close");
    fireEvent.click(screen.getByTestId("issue-detail-close"));
    expect(onClose).toHaveBeenCalledOnce();
  });

  // ─── Bug: assignee shows UUID not name ────────────────────────────────────

  it("shows assignee display name, not UUID", async () => {
    renderPanel();
    await screen.findByText("Build enterprise onboarding wizard");
    // Should show "Alice Chen" not "user-alice"
    expect(screen.getByText("Alice Chen")).toBeInTheDocument();
    expect(screen.queryByText("user-alice")).not.toBeInTheDocument();
  });

  it("shows 'Unassigned' when assigneeUserId is null", async () => {
    const noAssignee = {
      ...MOCK_DETAIL,
      issue: { ...MOCK_DETAIL.issue, assigneeUserId: null },
    };
    onFetchDetail.mockResolvedValue(noAssignee);
    renderPanel();
    await screen.findByText("Build enterprise onboarding wizard");
    expect(screen.getByText("Unassigned")).toBeInTheDocument();
  });

  // ─── Bug: no way to assign from panel ────────────────────────────────────

  it("renders an assignee dropdown in the detail panel", async () => {
    renderPanel();
    await screen.findByText("Build enterprise onboarding wizard");
    expect(screen.getByTestId("issue-assignee-select")).toBeInTheDocument();
  });

  it("dropdown shows all team members", async () => {
    renderPanel();
    await screen.findByText("Build enterprise onboarding wizard");
    const select = screen.getByTestId("issue-assignee-select") as HTMLSelectElement;
    const options = Array.from(select.options).map((o) => o.text);
    expect(options).toContain("Alice Chen");
    expect(options).toContain("Bob Martinez");
    expect(options).toContain("Carol Park");
  });

  it("calls onAssign when a different assignee is selected", async () => {
    renderPanel();
    await screen.findByText("Build enterprise onboarding wizard");
    const select = screen.getByTestId("issue-assignee-select");
    fireEvent.change(select, { target: { value: "user-bob" } });
    await waitFor(() => expect(onAssign).toHaveBeenCalledWith("issue-1", "user-bob"));
  });

  it("calls onAssign with null to unassign", async () => {
    renderPanel();
    await screen.findByText("Build enterprise onboarding wizard");
    const select = screen.getByTestId("issue-assignee-select");
    fireEvent.change(select, { target: { value: "" } });
    await waitFor(() => expect(onAssign).toHaveBeenCalledWith("issue-1", null));
  });

  // ─── Existing functionality ───────────────────────────────────────────────

  it("displays issue metadata correctly", async () => {
    renderPanel();
    await screen.findByText("Build enterprise onboarding wizard");
    expect(screen.getByText("ENG-1")).toBeInTheDocument();
    expect(screen.getByText("IN PROGRESS")).toBeInTheDocument();
    expect(screen.getByText("24h")).toBeInTheDocument();
    expect(screen.getByText("Build")).toBeInTheDocument();
  });

  it("uses human-readable placeholders instead of escaped unicode literals", async () => {
    renderPanel();
    await screen.findByText("Build enterprise onboarding wizard");
    expect(screen.getByPlaceholderText("Add a comment…")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Log time (hours)…")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Add a comment\\u2026")).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Log time (hours)\\u2026")).not.toBeInTheDocument();
  });

  it("keeps outcome link compact by default and expands only when editing", async () => {
    const detailWithOutcome = {
      ...MOCK_DETAIL,
      issue: { ...MOCK_DETAIL.issue, outcomeId: "outcome-1" },
    };
    onFetchDetail.mockResolvedValue(detailWithOutcome);
    renderPanel("issue-1", { onUpdate });
    await screen.findByText("Build enterprise onboarding wizard");

    expect(screen.getByText("Reduce sales cycle by 20%")).toBeInTheDocument();
    expect(screen.getByTestId("issue-outcome-edit-toggle")).toHaveTextContent("Change");
    expect(screen.queryByTestId("rcdo-picker")).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId("issue-outcome-edit-toggle"));
    expect(screen.getByTestId("rcdo-picker")).toBeInTheDocument();
  });

  it("shows 'Link outcome' action when no outcome is linked", async () => {
    renderPanel("issue-1", { onUpdate });
    await screen.findByText("Build enterprise onboarding wizard");
    expect(screen.getByTestId("issue-outcome-edit-toggle")).toHaveTextContent("Link outcome");
  });

  // ─── Hooks stability: null → value transition ────────────────────────────

  it("does not violate Rules of Hooks when issueId transitions from null to a value", async () => {
    // First render with null (no panel visible)
    const { rerender } = render(
      <IssueDetailPanel
        issueId={null}
        onClose={onClose}
        onFetchDetail={onFetchDetail}
        onAddComment={onAddComment}
        onLogTime={onLogTime}
        onAssign={onAssign}
        teamMembers={MEMBERS}
      />,
    );
    // Should render nothing (but hooks must still all be called)
    expect(screen.queryByTestId("issue-detail-panel")).not.toBeInTheDocument();

    // Re-render with a real issueId — this must NOT throw a hooks order error
    rerender(
      <IssueDetailPanel
        issueId="issue-1"
        onClose={onClose}
        onFetchDetail={onFetchDetail}
        onAddComment={onAddComment}
        onLogTime={onLogTime}
        onAssign={onAssign}
        teamMembers={MEMBERS}
      />,
    );

    await screen.findByText("Build enterprise onboarding wizard");
    expect(screen.getByTestId("issue-detail-panel")).toBeInTheDocument();
  });

  it("locks page scroll while the issue detail panel is open and restores it on unmount", async () => {
    const { unmount } = renderPanel();
    await screen.findByText("Build enterprise onboarding wizard");

    expect(document.body.style.overflow).toBe("hidden");
    expect(document.body.style.position).toBe("fixed");
    expect(document.body.style.width).toBe("100%");

    unmount();
    expect(document.body.style.overflow).toBe("");
    expect(document.body.style.position).toBe("");
    expect(document.body.style.width).toBe("");
  });
});
