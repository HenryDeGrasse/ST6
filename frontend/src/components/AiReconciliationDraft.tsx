import React from "react";
import type { ReconciliationDraftItem } from "@weekly-commitments/contracts";
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";

export interface AiReconciliationDraftProps {
  draftItems: ReconciliationDraftItem[];
  draftStatus: AiRequestStatus;
  onFetchDraft: () => void;
  onApplyDraft: (item: ReconciliationDraftItem) => void;
}

/**
 * AI-drafted reconciliation panel (beta, behind feature flag).
 *
 * Clearly labeled as "AI-generated draft — review before submitting"
 * per PRD §4. Users can apply individual items or ignore all.
 */
export const AiReconciliationDraft: React.FC<AiReconciliationDraftProps> = ({
  draftItems,
  draftStatus,
  onFetchDraft,
  onApplyDraft,
}) => {
  const flags = useFeatureFlags();

  if (!flags.draftReconciliation) {
    return null;
  }

  return (
    <div
      data-testid="ai-reconciliation-draft"
      style={{
        padding: "0.75rem",
        border: "1px dashed #a5d6a7",
        borderRadius: "4px",
        backgroundColor: "#e8f5e9",
        marginBottom: "1rem",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: "0.5rem",
        }}
      >
        <div>
          <span style={{ fontWeight: 600, color: "#2e7d32", fontSize: "0.9rem" }}>
            🤖 AI Reconciliation Draft
          </span>
          <span
            style={{ fontSize: "0.75rem", color: "#555", marginLeft: "0.5rem" }}
          >
            Beta — review before submitting
          </span>
        </div>
        {draftStatus === "idle" && (
          <button
            data-testid="ai-draft-fetch"
            onClick={onFetchDraft}
            style={{
              padding: "0.3rem 0.75rem",
              fontSize: "0.85rem",
              border: "1px solid #81c784",
              borderRadius: "3px",
              backgroundColor: "#fff",
              cursor: "pointer",
            }}
          >
            Generate Draft
          </button>
        )}
      </div>

      {draftStatus === "loading" && (
        <div data-testid="ai-draft-loading" style={{ color: "#666", fontStyle: "italic", fontSize: "0.85rem" }}>
          Analyzing commitments…
        </div>
      )}

      {draftStatus === "rate_limited" && (
        <div data-testid="ai-draft-rate-limited" style={{ color: "#b71c1c", fontSize: "0.85rem" }}>
          Rate limit reached. Try again in a moment.
        </div>
      )}

      {draftStatus === "unavailable" && (
        <div data-testid="ai-draft-unavailable" style={{ color: "#666", fontSize: "0.85rem" }}>
          AI draft unavailable. Complete reconciliation manually.
        </div>
      )}

      {draftStatus === "ok" && draftItems.length > 0 && (
        <div data-testid="ai-draft-items">
          {draftItems.map((item, index) => (
            <div
              key={item.commitId}
              data-testid={`ai-draft-item-${index}`}
              style={{
                padding: "0.4rem",
                border: "1px solid #c8e6c9",
                borderRadius: "3px",
                backgroundColor: "#fff",
                marginBottom: "0.25rem",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
              }}
            >
              <div style={{ flex: 1 }}>
                <span style={{ fontWeight: 500, fontSize: "0.85rem" }}>
                  {item.suggestedStatus}
                </span>
                <span style={{ color: "#555", fontSize: "0.8rem", marginLeft: "0.5rem" }}>
                  {item.suggestedActualResult}
                </span>
              </div>
              <button
                data-testid={`ai-draft-apply-${index}`}
                onClick={() => onApplyDraft(item)}
                style={{
                  padding: "0.2rem 0.5rem",
                  fontSize: "0.8rem",
                  border: "1px solid #81c784",
                  borderRadius: "3px",
                  backgroundColor: "#e8f5e9",
                  cursor: "pointer",
                }}
              >
                Apply
              </button>
            </div>
          ))}
        </div>
      )}

      {draftStatus === "ok" && draftItems.length === 0 && (
        <div style={{ color: "#666", fontSize: "0.85rem" }}>
          No draft suggestions available.
        </div>
      )}
    </div>
  );
};
