import React, { createContext, useContext, useMemo } from "react";

/**
 * User identity and auth token provided by the PA host shell.
 *
 * In production, the PA host supplies these via Module Federation
 * shared scope or a context bridge. The pa-host-stub injects
 * synthetic values so the micro-frontend can develop standalone.
 */
export interface AuthUser {
  userId: string;
  orgId: string;
  displayName: string;
  roles: string[];
  timezone: string;
}

export interface AuthContextValue {
  user: AuthUser;
  token: string;
  /** Returns the current bearer token for API calls. */
  getToken: () => string;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export interface AuthProviderProps {
  user: AuthUser;
  token: string;
  children: React.ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({
  user,
  token,
  children,
}) => {
  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      getToken: () => token,
    }),
    [user, token],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
