import React, { useState } from "react";
import { AuthProvider } from "./context/AuthContext.js";
import { ApiProvider } from "./api/ApiContext.js";
import { FeatureFlagProvider, type FeatureFlags } from "./context/FeatureFlagContext.js";
import { WeeklyPlanPage } from "./pages/WeeklyPlanPage.js";
import { TeamDashboardPage } from "./pages/TeamDashboardPage.js";
import { ErrorBoundary } from "./components/ErrorBoundary.js";
import { ToastProvider } from "./context/ToastContext.js";

/**
 * Default auth context for standalone development.
 * In production the PA host provides real auth values.
 */
const DEV_USER = {
  userId: "c0000000-0000-0000-0000-000000000001",
  orgId: "a0000000-0000-0000-0000-000000000001",
  displayName: "Dev User",
  roles: ["IC", "MANAGER"],
  timezone: "America/Chicago",
};
const DEV_TOKEN = "dev-jwt-token";

export interface AppProps {
  /** Override auth user (PA host injects real values). */
  user?: typeof DEV_USER;
  /** Override bearer token (PA host injects real JWT). */
  token?: string;
  /** API base URL */
  apiBaseUrl?: string;
  /** Initial route override. In production the PA host controls routing. */
  initialRoute?: "weekly" | "weekly/team";
  /** Feature flag overrides (for beta features). */
  featureFlags?: Partial<FeatureFlags>;
}

/**
 * Root component of the Weekly Commitments micro-frontend.
 *
 * In production this is loaded as a Module Federation remote
 * by the PA host app. For local development it renders standalone
 * with synthetic auth and a stub API.
 *
 * Routes:
 * - /weekly (default): IC plan view
 * - /weekly/team: Manager dashboard
 */
export const App: React.FC<AppProps> = ({
  user = DEV_USER,
  token = DEV_TOKEN,
  apiBaseUrl = "/api/v1",
  initialRoute = "weekly",
  featureFlags,
}) => {
  const [route, setRoute] = useState<"weekly" | "weekly/team">(initialRoute);
  const isManager = user.roles.includes("MANAGER");

  return (
    <AuthProvider user={user} token={token}>
      <ApiProvider baseUrl={apiBaseUrl}>
        <FeatureFlagProvider flags={featureFlags}>
        <ErrorBoundary>
        <ToastProvider>
        <div data-testid="weekly-commitments-app">
          {isManager && (
            <nav data-testid="main-nav" style={{ marginBottom: "1rem", padding: "0.5rem" }}>
              <button
                data-testid="nav-my-plan"
                onClick={() => setRoute("weekly")}
                style={{
                  fontWeight: route === "weekly" ? "bold" : "normal",
                  marginRight: "1rem",
                }}
              >
                My Plan
              </button>
              <button
                data-testid="nav-team-dashboard"
                onClick={() => setRoute("weekly/team")}
                style={{
                  fontWeight: route === "weekly/team" ? "bold" : "normal",
                }}
              >
                Team Dashboard
              </button>
            </nav>
          )}
          {route === "weekly" && <WeeklyPlanPage />}
          {route === "weekly/team" && isManager && <TeamDashboardPage />}
        </div>
        </ToastProvider>
        </ErrorBoundary>
        </FeatureFlagProvider>
      </ApiProvider>
    </AuthProvider>
  );
};
