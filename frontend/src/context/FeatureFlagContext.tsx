import React, { createContext, useContext, useMemo } from "react";

/**
 * Feature flags for AI-assisted, analytics, and dashboard workflows.
 *
 * Per PRD §4:
 * - suggestRcdo: MVP ship (enabled by default)
 * - draftReconciliation: MVP beta (disabled by default)
 * - managerInsights: MVP beta (disabled by default)
 * - icTrends: Wave 1 — cross-week IC trend panel (enabled by default)
 * - planQualityNudge: Wave 1 — lock-time AI quality nudge (disabled by default)
 * - startMyWeek: Wave 2 — "Start from Last Week" draft-from-history flow (disabled by default)
 * - suggestNextWork: Wave 2 — AI next-work suggestions panel (disabled by default)
 * - dailyCheckIn: Wave 2 — quick daily check-in on locked commits (disabled by default)
 * - quickUpdate: Phase 1 — rapid-fire batch check-in flow (disabled by default)
 * - userProfile: Phase 1 — user model profile panel (disabled by default)
 * - capacityTracking: Phase 4 — estimated/actual hours tracking per commitment (disabled by default)
 * - estimationCoaching: Phase 4 — post-reconciliation estimation coaching feedback (disabled by default)
 * - strategicIntelligence: Analytics — multi-week strategic intelligence panel on the manager dashboard (disabled by default)
 * - predictions: Analytics — rule-based prediction alerts on the manager dashboard (disabled by default)
 * - outcomeUrgency: Phase 3 — urgency bands and target-date tracking for RCDO outcomes (disabled by default)
 * - strategicSlack: Phase 3 — strategic focus floor recommendations based on outcome urgency (disabled by default)
 */
export interface FeatureFlags {
  suggestRcdo: boolean;
  draftReconciliation: boolean;
  managerInsights: boolean;
  icTrends: boolean;
  planQualityNudge: boolean;
  startMyWeek: boolean;
  suggestNextWork: boolean;
  dailyCheckIn: boolean;
  quickUpdate: boolean;
  userProfile: boolean;
  capacityTracking: boolean;
  estimationCoaching: boolean;
  strategicIntelligence: boolean;
  predictions: boolean;
  outcomeUrgency: boolean;
  strategicSlack: boolean;
}

export const FEATURE_FLAGS_STORAGE_KEY = "wc-feature-flags";

const DEFAULT_FLAGS: FeatureFlags = {
  suggestRcdo: true,
  draftReconciliation: false,
  managerInsights: false,
  icTrends: true,
  planQualityNudge: false,
  startMyWeek: false,
  suggestNextWork: false,
  dailyCheckIn: false,
  quickUpdate: false,
  userProfile: false,
  capacityTracking: false,
  estimationCoaching: false,
  strategicIntelligence: false,
  predictions: false,
  outcomeUrgency: false,
  strategicSlack: false,
};

const FeatureFlagContext = createContext<FeatureFlags>(DEFAULT_FLAGS);

export interface FeatureFlagProviderProps {
  flags?: Partial<FeatureFlags>;
  children: React.ReactNode;
}

function readPersistedFlags(): Partial<FeatureFlags> {
  if (typeof window === "undefined") {
    return {};
  }

  try {
    const raw = window.localStorage.getItem(FEATURE_FLAGS_STORAGE_KEY);
    if (!raw) {
      return {};
    }

    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== "object") {
      return {};
    }

    const persisted: Partial<FeatureFlags> = {};
    const record = parsed as Record<string, unknown>;

    (Object.keys(DEFAULT_FLAGS) as (keyof FeatureFlags)[]).forEach((key) => {
      if (typeof record[key] === "boolean") {
        persisted[key] = record[key] as boolean;
      }
    });

    return persisted;
  } catch {
    return {};
  }
}

export const FeatureFlagProvider: React.FC<FeatureFlagProviderProps> = ({ flags, children }) => {
  const persistedFlags = useMemo(() => readPersistedFlags(), []);
  const merged = useMemo<FeatureFlags>(
    () => ({ ...DEFAULT_FLAGS, ...persistedFlags, ...flags }),
    [persistedFlags, flags],
  );

  return <FeatureFlagContext.Provider value={merged}>{children}</FeatureFlagContext.Provider>;
};

export function useFeatureFlags(): FeatureFlags {
  return useContext(FeatureFlagContext);
}
