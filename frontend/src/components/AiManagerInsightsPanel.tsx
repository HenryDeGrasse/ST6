import React from "react";
import type { ManagerInsightItem } from "@weekly-commitments/contracts";
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";

export interface AiManagerInsightsPanelProps {
  status: AiRequestStatus;
  headline: string | null;
  insights: ManagerInsightItem[];
  onRefresh: () => void;
}

const SEVERITY_COLORS: Record<ManagerInsightItem["severity"], string> = {
  INFO: "#1565c0",
  WARNING: "#b26a00",
  POSITIVE: "#2e7d32",
};

/**
 * Clearly labeled beta panel for AI-generated manager insights.
 *
 * The manual dashboard remains the source of truth; this panel is an
 * optional summary layer that managers can refresh, read, or ignore.
 */
export const AiManagerInsightsPanel: React.FC<AiManagerInsightsPanelProps> = ({
  status,
  headline,
  insights,
  onRefresh,
}) => {
  const flags = useFeatureFlags();

  if (!flags.managerInsights) {
    return null;
  }

  return (
    <div
      data-testid="ai-manager-insights"
      style={{
        padding: "0.75rem",
        border: "1px dashed #ce93d8",
        borderRadius: "4px",
        backgroundColor: "#faf5ff",
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
          <span style={{ fontWeight: 600, color: "#7b1fa2", fontSize: "0.9rem" }}>
            🤖 AI Manager Insights
          </span>
          <span style={{ fontSize: "0.75rem", color: "#555", marginLeft: "0.5rem" }}>
            Beta — summary only, verify against the dashboard below
          </span>
        </div>
        <button
          data-testid="ai-manager-insights-refresh"
          onClick={onRefresh}
          style={{
            padding: "0.3rem 0.75rem",
            fontSize: "0.85rem",
            border: "1px solid #ce93d8",
            borderRadius: "3px",
            backgroundColor: "#fff",
            cursor: "pointer",
          }}
        >
          Refresh
        </button>
      </div>

      {status === "loading" && (
        <div data-testid="ai-manager-insights-loading" style={{ color: "#666", fontStyle: "italic", fontSize: "0.85rem" }}>
          Summarizing team signals…
        </div>
      )}

      {status === "rate_limited" && (
        <div data-testid="ai-manager-insights-rate-limited" style={{ color: "#b71c1c", fontSize: "0.85rem" }}>
          Rate limit reached. Try again in a moment.
        </div>
      )}

      {status === "unavailable" && (
        <div data-testid="ai-manager-insights-unavailable" style={{ color: "#666", fontSize: "0.85rem" }}>
          AI insights unavailable. Use the manual dashboard below.
        </div>
      )}

      {status === "ok" && headline && (
        <div data-testid="ai-manager-insights-content">
          <p style={{ margin: "0 0 0.5rem", color: "#333", fontWeight: 500 }}>{headline}</p>
          {insights.length > 0 && (
            <ul style={{ margin: 0, paddingLeft: "1.2rem" }}>
              {insights.map((insight, index) => (
                <li key={`${insight.title}-${index}`} data-testid={`ai-manager-insight-${index}`} style={{ marginBottom: "0.35rem" }}>
                  <span style={{ color: SEVERITY_COLORS[insight.severity], fontWeight: 600 }}>
                    {insight.title}
                  </span>
                  <span style={{ color: "#555" }}> — {insight.detail}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
};
