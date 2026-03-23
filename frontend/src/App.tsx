import React, { useEffect, useRef, useState } from "react";
import { AuthProvider } from "./context/AuthContext.js";
import { ApiProvider } from "./api/ApiContext.js";
import { FeatureFlagProvider, useFeatureFlags, type FeatureFlags } from "./context/FeatureFlagContext.js";
import { WeeklyPlanPage } from "./pages/WeeklyPlanPage.js";
import { TeamDashboardPage } from "./pages/TeamDashboardPage.js";
import { AdminDashboardPage } from "./pages/AdminDashboardPage.js";
import { ExecutiveDashboardPage } from "./pages/ExecutiveDashboardPage.js";
import { MyInsightsPage } from "./pages/MyInsightsPage.js";
import { BacklogPage } from "./pages/BacklogPage.js";
import { TeamManagementPage } from "./pages/TeamManagementPage.js";
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

type AppRoute = "weekly" | "weekly/backlog" | "weekly/insights" | "weekly/team" | "weekly/team-management" | "admin" | "executive";

const ROUTE_TO_PATH: Record<AppRoute, string> = {
  weekly: "/",
  "weekly/backlog": "/backlog",
  "weekly/insights": "/insights",
  "weekly/team": "/teamdashboard",
  "weekly/team-management": "/team-management",
  admin: "/admin",
  executive: "/executive",
};

function isStandaloneLocalRoutingEnabled(): boolean {
  if (typeof window === "undefined") return false;
  return window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
}

function routeFromPath(pathname: string): AppRoute | null {
  const normalized = pathname !== "/" && pathname.endsWith("/")
    ? pathname.slice(0, -1)
    : pathname;

  switch (normalized) {
    case "/": return "weekly";
    case "/backlog": return "weekly/backlog";
    case "/insights": return "weekly/insights";
    case "/teamdashboard": return "weekly/team";
    case "/team-management": return "weekly/team-management";
    case "/admin": return "admin";
    case "/executive": return "executive";
    default: return null;
  }
}

interface NavigateEventDetail {
  route: AppRoute;
}

export interface AppProps {
  /** Override auth user (PA host injects real values). */
  user?: typeof DEV_USER;
  /** Override bearer token (PA host injects real JWT). */
  token?: string;
  /** API base URL */
  apiBaseUrl?: string;
  /** Initial route override. In production the PA host controls routing. */
  initialRoute?: AppRoute;
  /** Feature flag overrides. */
  featureFlags?: Partial<FeatureFlags>;
}

const NAV_BUTTON_STYLE = {
  fontFamily: "'Inter', sans-serif",
  fontSize: "0.8125rem",
  letterSpacing: "0.01em",
  background: "none",
  border: "none",
  paddingBottom: "0.5rem",
  cursor: "pointer",
  transition: "color 300ms ease-out, border-color 300ms ease-out",
};

const AppShell: React.FC<{
  user: typeof DEV_USER;
  initialRoute: AppRoute;
}> = ({ user, initialRoute }) => {
  const flags = useFeatureFlags();
  const localRoutingEnabled = isStandaloneLocalRoutingEnabled();
  const [route, setRoute] = useState<AppRoute>(() => {
    if (localRoutingEnabled) {
      return routeFromPath(window.location.pathname) ?? initialRoute;
    }
    return initialRoute;
  });
  const [teamManagementTeamId, setTeamManagementTeamId] = useState<string | undefined>(undefined);
  const hasSyncedPathRef = useRef(false);
  const isManager = user.roles.includes("MANAGER");
  const isAdmin = user.roles.includes("ADMIN");
  const canAccessExecutive = isAdmin && flags.executiveDashboard;

  useEffect(() => {
    if (route === "executive" && !canAccessExecutive) {
      setRoute(isManager ? "weekly/team" : isAdmin ? "admin" : "weekly");
    }
  }, [canAccessExecutive, isAdmin, isManager, route]);

  useEffect(() => {
    if (!localRoutingEnabled) return;

    const targetPath = ROUTE_TO_PATH[route];
    if (window.location.pathname === targetPath) {
      hasSyncedPathRef.current = true;
      return;
    }

    if (hasSyncedPathRef.current) {
      window.history.pushState({}, "", targetPath);
    } else {
      window.history.replaceState({}, "", targetPath);
      hasSyncedPathRef.current = true;
    }
  }, [localRoutingEnabled, route]);

  useEffect(() => {
    const handleNavigate = (event: Event) => {
      const detail = (event as CustomEvent<NavigateEventDetail>).detail;
      if (detail?.route) {
        setRoute(detail.route);
      }
    };

    window.addEventListener("wc:navigate", handleNavigate);
    return () => {
      window.removeEventListener("wc:navigate", handleNavigate);
    };
  }, []);

  useEffect(() => {
    if (!localRoutingEnabled) return;

    const handlePopState = () => {
      const nextRoute = routeFromPath(window.location.pathname);
      if (nextRoute) setRoute(nextRoute);
    };

    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, [localRoutingEnabled]);

  const renderNavButton = (targetRoute: AppRoute, label: string, testId: string) => (
    <button
      data-testid={testId}
      onClick={() => setRoute(targetRoute)}
      style={{
        ...NAV_BUTTON_STYLE,
        fontWeight: route === targetRoute ? 700 : 500,
        color: route === targetRoute ? "#2563eb" : "#64748b",
        borderBottom: route === targetRoute ? "2px solid #2563eb" : "2px solid transparent",
      }}
    >
      {label}
    </button>
  );

  return (
    <div data-testid="weekly-commitments-app">
      <nav
          data-testid="main-nav"
          style={{
            display: "flex",
            alignItems: "center",
            gap: "1.5rem",
            padding: "0.75rem 1rem",
            borderBottom: "1px solid #e2e5ea",
            marginBottom: "0.5rem",
          }}
        >
          {renderNavButton("weekly", "My Plan", "nav-my-plan")}
          {renderNavButton("weekly/backlog", "Backlog", "nav-backlog")}
          {renderNavButton("weekly/insights", "My Insights", "nav-my-insights")}
          {isManager && renderNavButton("weekly/team", "Team Dashboard", "nav-team-dashboard")}
          {canAccessExecutive && renderNavButton("executive", "Executive", "nav-executive")}
          {isAdmin && renderNavButton("admin", "Admin", "nav-admin")}
        </nav>
      {route === "weekly" && <WeeklyPlanPage />}
      {route === "weekly/backlog" && (
        <BacklogPage
          onManageTeam={(teamId) => {
            setTeamManagementTeamId(teamId);
            setRoute("weekly/team-management");
          }}
        />
      )}
      {route === "weekly/insights" && <MyInsightsPage />}
      {route === "weekly/team" && isManager && <TeamDashboardPage />}
      {route === "weekly/team-management" && (
        <TeamManagementPage
          initialTeamId={teamManagementTeamId}
          onBack={() => setRoute("weekly/backlog")}
        />
      )}
      {route === "executive" && canAccessExecutive && <ExecutiveDashboardPage />}
      {route === "admin" && isAdmin && <AdminDashboardPage />}
    </div>
  );
};

/**
 * Root component of the Weekly Commitments micro-frontend.
 *
 * In production this is loaded as a Module Federation remote
 * by the PA host app. For local development it renders standalone
 * with synthetic auth and a stub API.
 */
export const App: React.FC<AppProps> = ({
  user = DEV_USER,
  token = DEV_TOKEN,
  apiBaseUrl = "/api/v1",
  initialRoute = "weekly",
  featureFlags,
}) => {
  return (
    <ThemeProvider>
      <div style={{ position: "relative" }}>
        <AuthProvider user={user} token={token}>
          <ApiProvider baseUrl={apiBaseUrl}>
            <FeatureFlagProvider flags={featureFlags}>
              <ErrorBoundary>
                <ToastProvider>
                  <AppShell user={user} initialRoute={initialRoute} />
                </ToastProvider>
              </ErrorBoundary>
            </FeatureFlagProvider>
          </ApiProvider>
        </AuthProvider>
      </div>
    </ThemeProvider>
  );
};
