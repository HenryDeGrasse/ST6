import React, { useEffect } from "react";
import { useFeatureFlags } from "../../context/FeatureFlagContext.js";
import { useExecutiveDashboard } from "../../hooks/useExecutiveDashboard.js";
import styles from "./Phase5Panels.module.css";
import briefingStyles from "./ExecutiveBriefing.module.css";

export interface ExecutiveBriefingProps {
  weekStart: string;
}

const SEVERITY_ACCENT: Record<string, string> = {
  POSITIVE: "#7A8C6E",
  INFO: "#6E7A8C",
  WARNING: "#C47A84",
};

const SEVERITY_LABEL: Record<string, string> = {
  POSITIVE: "Positive",
  INFO: "Info",
  WARNING: "Warning",
};

export const ExecutiveBriefing: React.FC<ExecutiveBriefingProps> = ({ weekStart }) => {
  const flags = useFeatureFlags();
  const { briefing, briefingStatus, errorBriefing, fetchBriefing } = useExecutiveDashboard();

  useEffect(() => {
    if (flags.executiveDashboard) {
      void fetchBriefing(weekStart);
    }
  }, [fetchBriefing, flags.executiveDashboard, weekStart]);

  if (!flags.executiveDashboard) {
    return null;
  }

  return (
    <section data-testid="executive-briefing" className={styles.panel}>
      <div className={styles.headerRow}>
        <div>
          <div className={styles.eyebrow}>AI Summary</div>
          <h3 className={styles.title}>Executive Briefing</h3>
        </div>
        <button type="button" data-testid="executive-briefing-refresh" className={styles.secondaryButton} onClick={() => { void fetchBriefing(weekStart); }}>
          Refresh
        </button>
      </div>

      {briefingStatus === "loading" && (
        <div data-testid="executive-briefing-loading" className={styles.loading}>
          Generating executive briefing…
        </div>
      )}
      {briefingStatus === "rate_limited" && (
        <div data-testid="executive-briefing-rate-limited" className={styles.error}>
          Rate limit reached. Try again soon.
        </div>
      )}
      {!errorBriefing && briefingStatus === "unavailable" && (
        <div data-testid="executive-briefing-unavailable" className={styles.empty}>
          Executive briefing is unavailable.
        </div>
      )}
      {errorBriefing && (
        <div data-testid="executive-briefing-error" className={styles.error}>
          {errorBriefing}
        </div>
      )}

      {briefing && briefingStatus === "ok" && (
        <div data-testid="executive-briefing-content" className={styles.section}>
          {briefing.headline && (
            <p className={briefingStyles.headline}>{briefing.headline}</p>
          )}
          <ul className={briefingStyles.insightList}>
            {briefing.insights.map((insight, index) => (
              <li
                key={`${insight.title}-${index}`}
                data-testid={`executive-briefing-insight-${index}`}
                className={briefingStyles.insightItem}
                style={{ borderLeftColor: SEVERITY_ACCENT[insight.severity] ?? "transparent" }}
              >
                <span className={briefingStyles.insightSeverity}>
                  {SEVERITY_LABEL[insight.severity] ?? insight.severity}
                </span>
                <span className={briefingStyles.insightTitle}>{insight.title}</span>
                <span className={briefingStyles.insightDetail}>{insight.detail}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
};
