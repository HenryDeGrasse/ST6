import React, { useState } from "react";
import type { RcdoSuggestion } from "@weekly-commitments/contracts";
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";
import { StatusIcon } from "./icons/index.js";
import styles from "./AiSuggestionPanel.module.css";

export interface AiSuggestionPanelProps {
  suggestions: RcdoSuggestion[];
  status: AiRequestStatus;
  onAccept: (suggestion: RcdoSuggestion) => void;
}

/**
 * Displays AI-suggested RCDO mappings as clearly labeled suggestions.
 *
 * Shows the Outcome name as the primary headline (the specific thing
 * the work maps to), with the full Rally Cry → Objective breadcrumb
 * expandable on hover/click for context.
 */
export const AiSuggestionPanel: React.FC<AiSuggestionPanelProps> = ({
  suggestions,
  status,
  onAccept,
}) => {
  const [expandedIndex, setExpandedIndex] = useState<number | null>(null);

  if (status === "idle") {
    return null;
  }

  if (status === "loading") {
    return (
      <div data-testid="ai-suggestion-loading" className={styles.loading}>
        <StatusIcon icon="loading" size={14} />
        Finding relevant outcomes…
      </div>
    );
  }

  if (status === "rate_limited") {
    return (
      <div data-testid="ai-suggestion-rate-limited" className={styles.rateLimited}>
        Rate limit reached. Try again in a moment.
      </div>
    );
  }

  if (status === "unavailable" || suggestions.length === 0) {
    return null;
  }

  return (
    <div data-testid="ai-suggestion-panel" className={styles.panel}>
      <div className={styles.header}>
        <span className={styles.robotIcon}>
          <StatusIcon icon="robot" size={14} />
        </span>
        <span className={styles.title}>AI Suggestions</span>
        <span className={styles.hint}>(click to apply)</span>
      </div>

      {suggestions.map((suggestion, index) => (
        <button
          key={suggestion.outcomeId}
          type="button"
          data-testid={`ai-suggestion-${index}`}
          onClick={() => onAccept(suggestion)}
          onMouseEnter={() => setExpandedIndex(index)}
          onMouseLeave={() => setExpandedIndex(null)}
          className={styles.suggestionButton}
        >
          {/* Primary: Outcome name — the specific thing */}
          <div className={styles.suggestionRow}>
            <span className={styles.outcomeName}>{suggestion.outcomeName}</span>
            <span className={styles.confidence}>
              {Math.round(suggestion.confidence * 100)}%
            </span>
          </div>

          {/* Breadcrumb: Rally Cry → Objective (always visible but subtle) */}
          <div className={styles.breadcrumb}>
            <span className={styles.breadcrumbSegment}>{suggestion.rallyCryName}</span>
            <span className={styles.breadcrumbArrow} aria-hidden="true">›</span>
            <span className={styles.breadcrumbSegment}>{suggestion.objectiveName}</span>
          </div>

          {/* Rationale — shown on hover/expand */}
          {expandedIndex === index && suggestion.rationale && (
            <div className={styles.rationale}>
              {suggestion.rationale}
            </div>
          )}
        </button>
      ))}
    </div>
  );
};
