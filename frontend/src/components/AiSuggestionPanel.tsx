import React from "react";
import type { RcdoSuggestion } from "@weekly-commitments/contracts";
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";

export interface AiSuggestionPanelProps {
  suggestions: RcdoSuggestion[];
  status: AiRequestStatus;
  onAccept: (suggestion: RcdoSuggestion) => void;
}

/**
 * Displays AI-suggested RCDO mappings as clearly labeled suggestions.
 *
 * Per PRD: AI output is always presented as suggestions that users can
 * accept, edit, or ignore without blocking the manual workflow.
 */
export const AiSuggestionPanel: React.FC<AiSuggestionPanelProps> = ({
  suggestions,
  status,
  onAccept,
}) => {
  if (status === "idle") {
    return null;
  }

  if (status === "loading") {
    return (
      <div
        data-testid="ai-suggestion-loading"
        style={{
          padding: "0.5rem",
          fontSize: "0.85rem",
          color: "#666",
          fontStyle: "italic",
        }}
      >
        🤖 Finding relevant outcomes…
      </div>
    );
  }

  if (status === "rate_limited") {
    return (
      <div
        data-testid="ai-suggestion-rate-limited"
        style={{
          padding: "0.5rem",
          fontSize: "0.85rem",
          color: "#b71c1c",
        }}
      >
        Rate limit reached. Try again in a moment.
      </div>
    );
  }

  if (status === "unavailable" || suggestions.length === 0) {
    // Per PRD: no error state — manual picker is always available
    return null;
  }

  return (
    <div
      data-testid="ai-suggestion-panel"
      style={{
        padding: "0.5rem",
        border: "1px dashed #90caf9",
        borderRadius: "4px",
        backgroundColor: "#e3f2fd",
        marginBottom: "0.5rem",
      }}
    >
      <div
        style={{
          fontSize: "0.8rem",
          color: "#1565c0",
          fontWeight: 600,
          marginBottom: "0.25rem",
        }}
      >
        🤖 AI Suggestions
        <span
          style={{
            fontWeight: 400,
            marginLeft: "0.5rem",
            color: "#555",
            fontSize: "0.75rem",
          }}
        >
          (click to apply)
        </span>
      </div>

      {suggestions.map((suggestion, index) => (
        <button
          key={suggestion.outcomeId}
          data-testid={`ai-suggestion-${index}`}
          onClick={() => onAccept(suggestion)}
          style={{
            display: "block",
            width: "100%",
            textAlign: "left",
            padding: "0.4rem 0.5rem",
            marginBottom: index < suggestions.length - 1 ? "0.25rem" : 0,
            border: "1px solid #bbdefb",
            borderRadius: "3px",
            backgroundColor: "#fff",
            cursor: "pointer",
            fontSize: "0.85rem",
          }}
        >
          <div style={{ fontWeight: 500 }}>
            {suggestion.rallyCryName} → {suggestion.objectiveName} →{" "}
            {suggestion.outcomeName}
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", marginTop: "0.15rem" }}>
            <span style={{ color: "#666", fontSize: "0.8rem" }}>
              {suggestion.rationale}
            </span>
            <span style={{ color: "#1565c0", fontSize: "0.8rem", fontWeight: 500 }}>
              {Math.round(suggestion.confidence * 100)}% match
            </span>
          </div>
        </button>
      ))}
    </div>
  );
};
