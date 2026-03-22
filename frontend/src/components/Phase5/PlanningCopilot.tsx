import React, { useEffect, useMemo, useState } from "react";
import type { ApplyTeamPlanSuggestionRequest, ChessPriority } from "@weekly-commitments/contracts";
import { useFeatureFlags } from "../../context/FeatureFlagContext.js";
import { usePlanningCopilot } from "../../hooks/usePlanningCopilot.js";
import styles from "./Phase5Panels.module.css";

export interface PlanningCopilotProps {
  weekStart: string;
}

export const PlanningCopilot: React.FC<PlanningCopilotProps> = ({ weekStart }) => {
  const flags = useFeatureFlags();
  const { suggestion, applyResult, suggestionStatus, applyStatus, error, fetchSuggestion, applySuggestion } =
    usePlanningCopilot();
  const [selected, setSelected] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (flags.planningCopilot) {
      void fetchSuggestion(weekStart);
    }
  }, [fetchSuggestion, flags.planningCopilot, weekStart]);

  useEffect(() => {
    if (!suggestion) {
      setSelected({});
      return;
    }

    const nextSelected: Record<string, boolean> = {};
    suggestion.members.forEach((member) => {
      member.suggestedCommits.forEach((commit, index) => {
        nextSelected[`${member.userId}:${index}:${commit.title}`] = true;
      });
    });
    setSelected(nextSelected);
  }, [suggestion]);

  const applyPayload = useMemo<ApplyTeamPlanSuggestionRequest | null>(() => {
    if (!suggestion) {
      return null;
    }

    const members = suggestion.members
      .map((member) => ({
        userId: member.userId,
        suggestedCommits: member.suggestedCommits
          .filter((commit, index) => selected[`${member.userId}:${index}:${commit.title}`])
          .map((commit) => ({
            title: commit.title,
            outcomeId: commit.outcomeId,
            rationale: commit.rationale,
            chessPriority: (commit.chessPriority ?? "PAWN") as ChessPriority,
            estimatedHours: commit.estimatedHours,
          })),
      }))
      .filter((member) => member.suggestedCommits.length > 0);

    return members.length > 0 ? { weekStart, members } : null;
  }, [selected, suggestion, weekStart]);

  if (!flags.planningCopilot) {
    return null;
  }

  return (
    <section data-testid="planning-copilot" className={styles.panel}>
      <div className={styles.headerRow}>
        <div>
          <div className={styles.eyebrow}>Manager Copilot</div>
          <h3 className={styles.title}>Planning Copilot</h3>
        </div>
        <button
          type="button"
          data-testid="planning-copilot-refresh"
          className={styles.secondaryButton}
          onClick={() => {
            void fetchSuggestion(weekStart);
          }}
        >
          Refresh
        </button>
      </div>

      {suggestionStatus === "loading" && (
        <div data-testid="planning-copilot-loading" className={styles.loading}>
          Generating manager-safe plan suggestions…
        </div>
      )}
      {suggestionStatus === "rate_limited" && (
        <div data-testid="planning-copilot-rate-limited" className={styles.error}>
          Rate limit reached. Try again soon.
        </div>
      )}
      {!error && suggestionStatus === "unavailable" && (
        <div data-testid="planning-copilot-unavailable" className={styles.empty}>
          Planning copilot is unavailable for this org.
        </div>
      )}
      {error && (
        <div data-testid="planning-copilot-error" className={styles.error}>
          {error}
        </div>
      )}

      {suggestion && suggestionStatus === "ok" && (
        <>
          <div data-testid="planning-copilot-summary" className={styles.metricGrid}>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>Headline</span>
              <span className={styles.metricBody}>{suggestion.summary.headline ?? "—"}</span>
            </div>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>Suggested Hours</span>
              <span className={styles.metricValueSmall}>{suggestion.summary.suggestedHours ?? "—"}</span>
            </div>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>Team Capacity</span>
              <span className={styles.metricValueSmall}>{suggestion.summary.teamCapacityHours ?? "—"}</span>
            </div>
            <div className={styles.metricCard}>
              <span className={styles.metricLabel}>At-risk Outcomes</span>
              <span className={styles.metricValueSmall}>{suggestion.summary.atRiskOutcomeCount}</span>
            </div>
          </div>

          <div className={styles.section}>
            {suggestion.members.map((member) => (
              <div key={member.userId} data-testid={`planning-copilot-member-${member.userId}`} className={styles.memberCard}>
                <div className={styles.memberHeader}>
                  <div>
                    <strong>{member.displayName ?? member.userId}</strong>
                    {member.strengthSummary && <div className={styles.subtle}>{member.strengthSummary}</div>}
                  </div>
                  <div className={styles.subtle}>
                    {member.totalEstimated ?? "—"}h suggested / {member.realisticCapacity ?? "—"}h realistic
                  </div>
                </div>
                <ul className={styles.list}>
                  {member.suggestedCommits.map((commit, index) => {
                    const key = `${member.userId}:${index}:${commit.title}`;
                    return (
                      <li key={key} className={styles.listItem}>
                        <label className={styles.checkboxRow}>
                          <input
                            type="checkbox"
                            checked={Boolean(selected[key])}
                            data-testid={`planning-copilot-select-${member.userId}-${index}`}
                            onChange={() => {
                              setSelected((current) => ({ ...current, [key]: !current[key] }));
                            }}
                          />
                          <span>
                            <strong>{commit.title}</strong>
                            <span className={styles.subtle}>
                              {` · ${commit.chessPriority ?? "PAWN"}`}
                              {commit.estimatedHours !== null ? ` · ${commit.estimatedHours}h` : ""}
                            </span>
                            {commit.rationale && <div className={styles.subtle}>{commit.rationale}</div>}
                          </span>
                        </label>
                      </li>
                    );
                  })}
                </ul>
              </div>
            ))}
          </div>

          <div className={styles.actions}>
            <button
              type="button"
              data-testid="planning-copilot-apply"
              className={styles.primaryButton}
              disabled={!applyPayload || applyStatus === "loading"}
              onClick={() => {
                if (applyPayload) {
                  void applySuggestion(applyPayload);
                }
              }}
            >
              {applyStatus === "loading" ? "Applying…" : "Apply Selected Suggestions"}
            </button>
          </div>
        </>
      )}

      {applyResult && (
        <div data-testid="planning-copilot-apply-result" className={styles.success}>
          Applied drafts for {applyResult.members.length} team member{applyResult.members.length === 1 ? "" : "s"}.
        </div>
      )}
    </section>
  );
};
