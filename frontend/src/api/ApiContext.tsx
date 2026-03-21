import React, { createContext, useContext, useMemo } from "react";
import type { WeeklyCommitmentsClient } from "@weekly-commitments/contracts";
import { createApiClient } from "./client.js";
import { useAuth } from "../context/AuthContext.js";

export const ApiContext = createContext<WeeklyCommitmentsClient | null>(null);
export const ApiBaseUrlContext = createContext<string | null>(null);

export interface ApiProviderProps {
  baseUrl?: string;
  children: React.ReactNode;
}

/**
 * Provides the typed API client to descendant components.
 * Injects the bearer token from AuthContext.
 */
export const ApiProvider: React.FC<ApiProviderProps> = ({ baseUrl = "/api/v1", children }) => {
  const { user, getToken } = useAuth();

  const client = useMemo(() => createApiClient({ baseUrl, getToken, getUser: () => user }), [baseUrl, getToken, user]);

  return (
    <ApiBaseUrlContext.Provider value={baseUrl}>
      <ApiContext.Provider value={client}>{children}</ApiContext.Provider>
    </ApiBaseUrlContext.Provider>
  );
};

export function useOptionalApiClient(): WeeklyCommitmentsClient | null {
  return useContext(ApiContext);
}

export function useApiClient(): WeeklyCommitmentsClient {
  const ctx = useOptionalApiClient();
  if (!ctx) {
    throw new Error("useApiClient must be used within an ApiProvider");
  }
  return ctx;
}

export function useOptionalApiBaseUrl(): string | null {
  return useContext(ApiBaseUrlContext);
}

export function useApiBaseUrl(): string {
  const ctx = useOptionalApiBaseUrl();
  if (ctx === null) {
    throw new Error("useApiBaseUrl must be used within an ApiProvider");
  }
  return ctx;
}
