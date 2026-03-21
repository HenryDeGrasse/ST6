import React from "react";
import type { ReconciliationDraftItem } from "@weekly-commitments/contracts";
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import { StatusIcon } from "./icons/index.js";
import styles from "./AiReconciliationDraft.module.css";

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
    <div data-testid="ai-reconciliation-draft" className={styles.panel}>
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <span className={styles.robotIcon}>
            <StatusIcon icon="robot" size={14} />
          </span>
          <span className={styles.title}>AI Reconciliation Draft</span>
          <span className={styles.betaBadge}>Beta</span>
          <span className={styles.betaHint}>review before submitting</span>
        </div>
        {draftStatus === "idle" && (
          <button type="button" data-testid="ai-draft-fetch" onClick={onFetchDraft} className={styles.generateButton}>
            Generate Draft
          </button>
        )}
      </div>

      {draftStatus === "loading" && (
        <div data-testid="ai-draft-loading" className={styles.loading}>
          Analyzing commitments…
        </div>
      )}

      {draftStatus === "rate_limited" && (
        <div data-testid="ai-draft-rate-limited" className={styles.rateLimited}>
          Rate limit reached. Try again in a moment.
        </div>
      )}

      {draftStatus === "unavailable" && (
        <div data-testid="ai-draft-unavailable" className={styles.unavailable}>
          AI draft unavailable. Complete reconciliation manually.
        </div>
      )}

      {draftStatus === "ok" && draftItems.length > 0 && (
        <div data-testid="ai-draft-items" className={styles.draftItems}>
          {draftItems.map((item, index) => (
            <div key={item.commitId} data-testid={`ai-draft-item-${index}`} className={styles.draftItem}>
              <div className={styles.draftItemContent}>
                <span className={styles.draftStatus}>{item.suggestedStatus}</span>
                <span className={styles.draftResult}>{item.suggestedActualResult}</span>
              </div>
              <button
                type="button"
                data-testid={`ai-draft-apply-${index}`}
                onClick={() => onApplyDraft(item)}
                className={styles.applyButton}
              >
                Apply
              </button>
            </div>
          ))}
        </div>
      )}

      {draftStatus === "ok" && draftItems.length === 0 && (
        <div className={styles.empty}>No draft suggestions available.</div>
      )}
    </div>
  );
};
