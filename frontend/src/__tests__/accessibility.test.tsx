/**
 * Accessibility (a11y) tests for key UI components.
 *
 * Each test renders the component with minimal required props and runs
 * axe-core to assert zero critical/serious violations per PRD §13.1.
 *
 * Only 'critical' and 'serious' impact violations fail the assertion;
 * 'minor' and 'moderate' violations are intentionally ignored per the
 * PRD target ("0 critical/serious violations").
 *
 * TODO (follow-up): Add full-page a11y testing via Playwright +
 * @axe-core/playwright to the E2E suite for end-to-end coverage
 * including computed styles, focus management, and dynamic interactions.
 */
import { describe, it, vi } from "vitest";
import { render } from "@testing-library/react";
import { CommitEditor } from "../components/CommitEditor.js";
import { ReconciliationView } from "../components/ReconciliationView.js";
import { TeamSummaryGrid } from "../components/TeamSummaryGrid.js";
import { PlanHeader } from "../components/PlanHeader.js";
import { PlanState, ReviewStatus, ChessPriority, CompletionStatus } from "@weekly-commitments/contracts";
import type { WeeklyCommit, WeeklyPlan, TeamMemberSummary } from "@weekly-commitments/contracts";
import { expectNoA11yViolations } from "../test/a11y-helpers.js";

// ─── Fixtures ──────────────────────────────────────────────────────────────────

function makeCommit(overrides: Partial<WeeklyCommit> = {}): WeeklyCommit {
  return {
    id: "commit-a11y-1",
    weeklyPlanId: "plan-a11y-1",
    title: "Deliver feature X",
    description: "Ship the new feature by end of week",
    chessPriority: ChessPriority.ROOK,
    category: null,
    outcomeId: null,
    nonStrategicReason: null,
    expectedResult: "Feature deployed to production",
    confidence: null,
    tags: [],
    progressNotes: "",
    snapshotRallyCryId: null,
    snapshotRallyCryName: null,
    snapshotObjectiveId: null,
    snapshotObjectiveName: null,
    snapshotOutcomeId: null,
    snapshotOutcomeName: null,
    carriedFromCommitId: null,
    version: 1,
    createdAt: "2026-03-09T00:00:00.000Z",
    updatedAt: "2026-03-09T00:00:00.000Z",
    validationErrors: [],
    actual: null,
    ...overrides,
  };
}

function makePlan(overrides: Partial<WeeklyPlan> = {}): WeeklyPlan {
  return {
    id: "plan-a11y-1",
    orgId: "org-1",
    ownerUserId: "user-1",
    weekStartDate: "2026-03-09",
    state: PlanState.DRAFT,
    reviewStatus: ReviewStatus.REVIEW_NOT_APPLICABLE,
    lockType: null,
    lockedAt: null,
    carryForwardExecutedAt: null,
    version: 1,
    createdAt: "2026-03-09T00:00:00.000Z",
    updatedAt: "2026-03-09T00:00:00.000Z",
    ...overrides,
  };
}

const mockUser: TeamMemberSummary = {
  userId: "user-a11y-1",
  displayName: "Alice Smith",
  planId: "plan-a11y-1",
  state: PlanState.LOCKED,
  reviewStatus: ReviewStatus.REVIEW_NOT_APPLICABLE,
  commitCount: 3,
  incompleteCount: 0,
  issueCount: 0,
  nonStrategicCount: 0,
  kingCount: 1,
  queenCount: 1,
  lastUpdated: "2026-03-09T12:00:00Z",
  isStale: false,
  isLateLock: false,
};

// ─── CommitEditor ──────────────────────────────────────────────────────────────

describe("CommitEditor – accessibility", () => {
  it("new-commit form (DRAFT) has no critical/serious a11y violations", async () => {
    const { container } = render(
      <CommitEditor
        planState={PlanState.DRAFT}
        rcdoTree={[]}
        rcdoSearchResults={[]}
        onRcdoSearch={vi.fn()}
        onRcdoClearSearch={vi.fn()}
        onSave={vi.fn()}
      />,
    );
    await expectNoA11yViolations(container);
  });

  it("edit-commit form (DRAFT) has no critical/serious a11y violations", async () => {
    const { container } = render(
      <CommitEditor
        commit={makeCommit()}
        planState={PlanState.DRAFT}
        rcdoTree={[]}
        rcdoSearchResults={[]}
        onRcdoSearch={vi.fn()}
        onRcdoClearSearch={vi.fn()}
        onSave={vi.fn()}
      />,
    );
    await expectNoA11yViolations(container);
  });

  it("progress-notes form (LOCKED) has no critical/serious a11y violations", async () => {
    const { container } = render(
      <CommitEditor
        commit={makeCommit()}
        planState={PlanState.LOCKED}
        rcdoTree={[]}
        rcdoSearchResults={[]}
        onRcdoSearch={vi.fn()}
        onRcdoClearSearch={vi.fn()}
        onSave={vi.fn()}
      />,
    );
    await expectNoA11yViolations(container);
  });

  it("read-only view (RECONCILED) has no critical/serious a11y violations", async () => {
    const { container } = render(
      <CommitEditor
        commit={makeCommit({
          actual: {
            commitId: "commit-a11y-1",
            actualResult: "Done",
            completionStatus: CompletionStatus.DONE,
            deltaReason: null,
            timeSpent: null,
          },
        })}
        planState={PlanState.RECONCILED}
        rcdoTree={[]}
        rcdoSearchResults={[]}
        onRcdoSearch={vi.fn()}
        onRcdoClearSearch={vi.fn()}
        onSave={vi.fn()}
      />,
    );
    await expectNoA11yViolations(container);
  });
});

// ─── ReconciliationView ────────────────────────────────────────────────────────

describe("ReconciliationView – accessibility", () => {
  it("reconciliation panel has no critical/serious a11y violations", async () => {
    const { container } = render(
      <ReconciliationView
        commits={[makeCommit()]}
        planState={PlanState.RECONCILING}
        onUpdateActual={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    await expectNoA11yViolations(container);
  });
});

// ─── TeamSummaryGrid ───────────────────────────────────────────────────────────

describe("TeamSummaryGrid – accessibility", () => {
  it("team table with users has no critical/serious a11y violations", async () => {
    const { container } = render(<TeamSummaryGrid users={[mockUser]} onDrillDown={vi.fn()} />);
    await expectNoA11yViolations(container);
  });

  it("empty-state message has no critical/serious a11y violations", async () => {
    const { container } = render(<TeamSummaryGrid users={[]} onDrillDown={vi.fn()} />);
    await expectNoA11yViolations(container);
  });
});

// ─── PlanHeader ────────────────────────────────────────────────────────────────

describe("PlanHeader – accessibility", () => {
  it("DRAFT state (Lock button) has no critical/serious a11y violations", async () => {
    const { container } = render(
      <PlanHeader
        plan={makePlan()}
        onLock={vi.fn()}
        onStartReconciliation={vi.fn()}
        onSubmitReconciliation={vi.fn()}
        onCarryForward={vi.fn()}
      />,
    );
    await expectNoA11yViolations(container);
  });

  it("LOCKED state (Start Reconciliation button) has no critical/serious a11y violations", async () => {
    const { container } = render(
      <PlanHeader
        plan={makePlan({ state: PlanState.LOCKED })}
        onLock={vi.fn()}
        onStartReconciliation={vi.fn()}
        onSubmitReconciliation={vi.fn()}
        onCarryForward={vi.fn()}
      />,
    );
    await expectNoA11yViolations(container);
  });

  it("RECONCILING state (Submit Reconciliation button) has no critical/serious a11y violations", async () => {
    const { container } = render(
      <PlanHeader
        plan={makePlan({ state: PlanState.RECONCILING })}
        onLock={vi.fn()}
        onStartReconciliation={vi.fn()}
        onSubmitReconciliation={vi.fn()}
        onCarryForward={vi.fn()}
        canSubmitReconciliation={true}
      />,
    );
    await expectNoA11yViolations(container);
  });

  it("RECONCILED state (Carry Forward button) has no critical/serious a11y violations", async () => {
    const { container } = render(
      <PlanHeader
        plan={makePlan({ state: PlanState.RECONCILED })}
        onLock={vi.fn()}
        onStartReconciliation={vi.fn()}
        onSubmitReconciliation={vi.fn()}
        onCarryForward={vi.fn()}
      />,
    );
    await expectNoA11yViolations(container);
  });
});
