import React, { createContext, useContext, useMemo } from "react";
import type { WeeklyCommitmentsClient } from "@weekly-commitments/contracts";
import { createApiClient } from "./client.js";
import { useAuth } from "../context/AuthContext.js";

const ApiContext = createContext<WeeklyCommitmentsClient | null>(null);

export interface ApiProviderProps {
  baseUrl?: string;
  children: React.ReactNode;
}

/**
 * Provides the typed API client to descendant components.
 * Injects the bearer token from AuthContext.
 */
export const ApiProvider: React.FC<ApiProviderProps> = ({
  baseUrl = "/api/v1",
  children,
}) => {
  const { user, getToken } = useAuth();

  const client = useMemo(
    () => createApiClient({ baseUrl, getToken, getUser: () => user }),
    [baseUrl, getToken, user],
  );

  return <ApiContext.Provider value={client}>{children}</ApiContext.Provider>;
};

export function useApiClient(): WeeklyCommitmentsClient {
  const ctx = useContext(ApiContext);
  if (!ctx) {
    throw new Error("useApiClient must be used within an ApiProvider");
  }
  return ctx;
}
