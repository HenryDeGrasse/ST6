import React from "react";
import type { ManagerInsightItem } from "@weekly-commitments/contracts";
import type { AiRequestStatus } from "../hooks/useAiSuggestions.js";
import { useFeatureFlags } from "../context/FeatureFlagContext.js";
import { StatusIcon } from "./icons/index.js";
import styles from "./AiManagerInsightsPanel.module.css";

const KNOWN_DISPLAY_NAMES: Record<string, string> = {
  "c0000000-0000-0000-0000-000000000001": "Carol Park",
  "c0000000-0000-0000-0000-000000000010": "Alice Chen",
  "c0000000-0000-0000-0000-000000000020": "Bob Martinez",
  "c0000000-0000-0000-0000-000000000030": "Dana Torres",
};

function humanizeInsightText(text: string | null): string | null {
  if (!text) return text;

  let next = text;
  for (const [userId, displayName] of Object.entries(KNOWN_DISPLAY_NAMES)) {
    next = next.replaceAll(userId, displayName);
    next = next.replaceAll(`User ${userId}`, displayName);
  }
  return next;
}

export interface AiManagerInsightsPanelProps {
  status: AiRequestStatus;
  headline: string | null;
  insights: ManagerInsightItem[];
  onRefresh: () => void;
}

const SEVERITY_CLASS: Record<ManagerInsightItem["severity"], string> = {
  INFO: styles.insightTitleInfo,
  WARNING: styles.insightTitleWarning,
  POSITIVE: styles.insightTitlePositive,
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
    <div data-testid="ai-manager-insights" className={styles.panel}>
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <span className={styles.robotIcon}>
            <StatusIcon icon="robot" size={14} />
          </span>
          <span className={styles.title}>AI Manager Insights</span>
          <span className={styles.betaBadge}>Beta</span>
          <span className={styles.betaHint}>Beta — summary only, verify against the dashboard below</span>
        </div>
        <button
          type="button"
          data-testid="ai-manager-insights-refresh"
          onClick={onRefresh}
          className={styles.refreshButton}
        >
          Refresh
        </button>
      </div>

      {status === "loading" && (
        <div data-testid="ai-manager-insights-loading" className={styles.loading}>
          Summarizing team signals…
        </div>
      )}

      {status === "rate_limited" && (
        <div data-testid="ai-manager-insights-rate-limited" className={styles.rateLimited}>
          Rate limit reached. Try again in a moment.
        </div>
      )}

      {status === "unavailable" && (
        <div data-testid="ai-manager-insights-unavailable" className={styles.unavailable}>
          AI insights unavailable. Use the manual dashboard below.
        </div>
      )}

      {status === "ok" && headline && (
        <div data-testid="ai-manager-insights-content" className={styles.content}>
          <p className={styles.headline}>{humanizeInsightText(headline)}</p>
          {insights.length > 0 && (
            <ul className={styles.insightList}>
              {insights.map((insight, index) => (
                <li
                  key={`${insight.title}-${index}`}
                  data-testid={`ai-manager-insight-${index}`}
                  className={styles.insightItem}
                >
                  <span className={SEVERITY_CLASS[insight.severity]}>{insight.title}</span>
                  <span className={styles.insightDetail}> — {humanizeInsightText(insight.detail)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
};
