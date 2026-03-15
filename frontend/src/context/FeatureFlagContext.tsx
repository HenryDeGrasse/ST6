import React, { createContext, useContext, useMemo } from "react";

/**
 * Feature flags for AI-assisted workflows.
 *
 * Per PRD §4:
 * - suggestRcdo: MVP ship (enabled by default)
 * - draftReconciliation: MVP beta (disabled by default)
 * - managerInsights: MVP beta (disabled by default)
 */
export interface FeatureFlags {
  suggestRcdo: boolean;
  draftReconciliation: boolean;
  managerInsights: boolean;
}

const DEFAULT_FLAGS: FeatureFlags = {
  suggestRcdo: true,
  draftReconciliation: false,
  managerInsights: false,
};

const FeatureFlagContext = createContext<FeatureFlags>(DEFAULT_FLAGS);

export interface FeatureFlagProviderProps {
  flags?: Partial<FeatureFlags>;
  children: React.ReactNode;
}

export const FeatureFlagProvider: React.FC<FeatureFlagProviderProps> = ({
  flags,
  children,
}) => {
  const merged = useMemo<FeatureFlags>(
    () => ({ ...DEFAULT_FLAGS, ...flags }),
    [flags],
  );

  return (
    <FeatureFlagContext.Provider value={merged}>
      {children}
    </FeatureFlagContext.Provider>
  );
};

export function useFeatureFlags(): FeatureFlags {
  return useContext(FeatureFlagContext);
}
