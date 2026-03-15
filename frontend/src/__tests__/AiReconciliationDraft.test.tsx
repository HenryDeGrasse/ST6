import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { AiReconciliationDraft } from "../components/AiReconciliationDraft.js";
import { FeatureFlagProvider } from "../context/FeatureFlagContext.js";
import { CompletionStatus } from "@weekly-commitments/contracts";
import type { ReconciliationDraftItem } from "@weekly-commitments/contracts";

function makeDraftItem(overrides: Partial<ReconciliationDraftItem> = {}): ReconciliationDraftItem {
  return {
    commitId: "commit-1",
    suggestedStatus: CompletionStatus.DONE,
    suggestedDeltaReason: null,
    suggestedActualResult: "Completed as planned",
    ...overrides,
  };
}

function renderWithFlags(
  ui: React.ReactElement,
  flags: { draftReconciliation: boolean } = { draftReconciliation: true },
) {
  return render(
    <FeatureFlagProvider flags={flags}>{ui}</FeatureFlagProvider>,
  );
}

describe("AiReconciliationDraft", () => {
  const defaultProps = {
    draftItems: [] as ReconciliationDraftItem[],
    draftStatus: "idle" as const,
    onFetchDraft: vi.fn(),
    onApplyDraft: vi.fn(),
  };

  it("renders nothing when feature flag is disabled", () => {
    const { container } = render(
      <FeatureFlagProvider flags={{ draftReconciliation: false }}>
        <AiReconciliationDraft {...defaultProps} />
      </FeatureFlagProvider>,
    );
    expect(container.innerHTML).toBe("");
  });

  it("shows Generate Draft button when idle and enabled", () => {
    renderWithFlags(<AiReconciliationDraft {...defaultProps} />);
    expect(screen.getByTestId("ai-draft-fetch")).toBeInTheDocument();
    expect(screen.getByText(/Generate Draft/)).toBeInTheDocument();
  });

  it("calls onFetchDraft when button is clicked", () => {
    const onFetchDraft = vi.fn();
    renderWithFlags(
      <AiReconciliationDraft {...defaultProps} onFetchDraft={onFetchDraft} />,
    );
    fireEvent.click(screen.getByTestId("ai-draft-fetch"));
    expect(onFetchDraft).toHaveBeenCalled();
  });

  it("shows loading state", () => {
    renderWithFlags(
      <AiReconciliationDraft {...defaultProps} draftStatus="loading" />,
    );
    expect(screen.getByTestId("ai-draft-loading")).toBeInTheDocument();
  });

  it("shows unavailable state", () => {
    renderWithFlags(
      <AiReconciliationDraft {...defaultProps} draftStatus="unavailable" />,
    );
    expect(screen.getByTestId("ai-draft-unavailable")).toBeInTheDocument();
  });

  it("renders draft items when ok", () => {
    const item = makeDraftItem();
    renderWithFlags(
      <AiReconciliationDraft
        {...defaultProps}
        draftStatus="ok"
        draftItems={[item]}
      />,
    );
    expect(screen.getByTestId("ai-draft-items")).toBeInTheDocument();
    expect(screen.getByTestId("ai-draft-item-0")).toBeInTheDocument();
    expect(screen.getByText(/Completed as planned/)).toBeInTheDocument();
  });

  it("calls onApplyDraft when Apply is clicked", () => {
    const onApplyDraft = vi.fn();
    const item = makeDraftItem();
    renderWithFlags(
      <AiReconciliationDraft
        {...defaultProps}
        draftStatus="ok"
        draftItems={[item]}
        onApplyDraft={onApplyDraft}
      />,
    );
    fireEvent.click(screen.getByTestId("ai-draft-apply-0"));
    expect(onApplyDraft).toHaveBeenCalledWith(item);
  });

  it("labels panel as beta", () => {
    renderWithFlags(
      <AiReconciliationDraft
        {...defaultProps}
        draftStatus="ok"
        draftItems={[makeDraftItem()]}
      />,
    );
    expect(screen.getByText(/Beta/)).toBeInTheDocument();
    expect(screen.getByText(/review before submitting/)).toBeInTheDocument();
  });
});
