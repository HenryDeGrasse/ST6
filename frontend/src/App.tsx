import React, { useState } from "react";
import { AuthProvider } from "./context/AuthContext.js";
import { ApiProvider } from "./api/ApiContext.js";
import { FeatureFlagProvider, type FeatureFlags } from "./context/FeatureFlagContext.js";
import { WeeklyPlanPage } from "./pages/WeeklyPlanPage.js";
import { TeamDashboardPage } from "./pages/TeamDashboardPage.js";
import { ErrorBoundary } from "./components/ErrorBoundary.js";
import { ToastProvider } from "./context/ToastContext.js";
import { ThemeProvider } from "./theme/ThemeContext.js";

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
    <ThemeProvider>
      {/* All app content sits above the atmospheric overlays (z-index: 0) */}
      <div style={{ position: "relative", zIndex: 1 }}>
        <AuthProvider user={user} token={token}>
          <ApiProvider baseUrl={apiBaseUrl}>
            <FeatureFlagProvider flags={featureFlags}>
              <ErrorBoundary>
                <ToastProvider>
                  <div data-testid="weekly-commitments-app">
                    {isManager && (
                      <nav
                        data-testid="main-nav"
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: "1.5rem",
                          padding: "0.75rem 1rem",
                          borderBottom: "1px solid #4A3F35",
                          marginBottom: "0.5rem",
                        }}
                      >
                        <button
                          data-testid="nav-my-plan"
                          onClick={() => setRoute("weekly")}
                          style={{
                            fontFamily: "'Cinzel', serif",
                            fontSize: "0.7rem",
                            fontWeight: route === "weekly" ? 700 : 500,
                            textTransform: "uppercase" as const,
                            letterSpacing: "0.2em",
                            color: route === "weekly" ? "#C9A962" : "#9C8B7A",
                            background: "none",
                            border: "none",
                            borderBottom: route === "weekly" ? "2px solid #C9A962" : "2px solid transparent",
                            paddingBottom: "0.5rem",
                            cursor: "pointer",
                            transition: "color 300ms ease-out, border-color 300ms ease-out",
                          }}
                        >
                          My Plan
                        </button>
                        <button
                          data-testid="nav-team-dashboard"
                          onClick={() => setRoute("weekly/team")}
                          style={{
                            fontFamily: "'Cinzel', serif",
                            fontSize: "0.7rem",
                            fontWeight: route === "weekly/team" ? 700 : 500,
                            textTransform: "uppercase" as const,
                            letterSpacing: "0.2em",
                            color: route === "weekly/team" ? "#C9A962" : "#9C8B7A",
                            background: "none",
                            border: "none",
                            borderBottom: route === "weekly/team" ? "2px solid #C9A962" : "2px solid transparent",
                            paddingBottom: "0.5rem",
                            cursor: "pointer",
                            transition: "color 300ms ease-out, border-color 300ms ease-out",
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
      </div>
    </ThemeProvider>
  );
};
