import React, { createContext, useContext, useMemo } from "react";

/**
 * Feature flags for AI-assisted, analytics, and dashboard workflows.
 *
 * Per PRD §4, all currently shipped product flags are enabled by default in
 * local app runtime. Callers can still override individual flags via props or
 * persisted localStorage values.
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
  targetDateForecasting: boolean;
  planningCopilot: boolean;
  executiveDashboard: boolean;
  weeklyPlanningAgent: boolean;
  misalignmentAgent: boolean;
}

export const FEATURE_FLAGS_STORAGE_KEY = "wc-feature-flags";

const DEFAULT_FLAGS: FeatureFlags = {
  suggestRcdo: true,
  draftReconciliation: true,
  managerInsights: true,
  icTrends: true,
  planQualityNudge: true,
  startMyWeek: true,
  suggestNextWork: true,
  dailyCheckIn: true,
  quickUpdate: true,
  userProfile: true,
  capacityTracking: true,
  estimationCoaching: true,
  strategicIntelligence: true,
  predictions: true,
  outcomeUrgency: true,
  strategicSlack: true,
  targetDateForecasting: true,
  planningCopilot: true,
  executiveDashboard: true,
  weeklyPlanningAgent: true,
  misalignmentAgent: true,
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
