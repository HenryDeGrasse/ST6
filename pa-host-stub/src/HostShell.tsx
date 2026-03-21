import React, { useState, useCallback, useMemo } from "react";
import { App as WeeklyCommitmentsApp } from "@weekly-commitments/frontend";

// ─── Test feature-flag injection ─────────────────────────────────────────────
//
// The pa-host-stub is a development/test harness. Playwright E2E tests can
// enable beta feature flags by appending `?flags=<comma-separated-names>` to
// the URL, e.g.: `/?flags=draftReconciliation,managerInsights`
//
// This is NOT present in production — the real PA host passes flags via its
// own configuration mechanism.

function readUrlFeatureFlags(): {
  suggestRcdo?: boolean;
  draftReconciliation?: boolean;
  managerInsights?: boolean;
} {
  const flags = typeof window !== "undefined"
    ? new URLSearchParams(window.location.search).get("flags") ?? ""
    : "";
  return {
    ...(flags.includes("suggestRcdo") ? { suggestRcdo: true } : {}),
    ...(flags.includes("draftReconciliation") ? { draftReconciliation: true } : {}),
    ...(flags.includes("managerInsights") ? { managerInsights: true } : {}),
  };
}

// ─── Persona definitions ──────────────────────────────────────────────────────

interface Persona {
  key: string;
  label: string;
  description: string;
  user: {
    userId: string;
    orgId: string;
    displayName: string;
    roles: string[];
    timezone: string;
  };
  token: string;
}

const ORG_ID = "a0000000-0000-0000-0000-000000000001";

const PERSONAS: Persona[] = [
  {
    key: "carol",
    label: "Carol Park",
    description: "Manager + IC — LOCKED plan, manages Alice & Bob",
    user: {
      userId: "c0000000-0000-0000-0000-000000000001",
      orgId: ORG_ID,
      displayName: "Carol Park",
      roles: ["IC", "MANAGER"],
      timezone: "America/Chicago",
    },
    token: "dev-token-carol",
  },
  {
    key: "alice",
    label: "Alice Chen",
    description: "IC — DRAFT plan with validation issues",
    user: {
      userId: "c0000000-0000-0000-0000-000000000010",
      orgId: ORG_ID,
      displayName: "Alice Chen",
      roles: ["IC"],
      timezone: "America/New_York",
    },
    token: "dev-token-alice",
  },
  {
    key: "bob",
    label: "Bob Martinez",
    description: "IC — RECONCILED plan, awaiting review",
    user: {
      userId: "c0000000-0000-0000-0000-000000000020",
      orgId: ORG_ID,
      displayName: "Bob Martinez",
      roles: ["IC"],
      timezone: "America/Los_Angeles",
    },
    token: "dev-token-bob",
  },
];

// ─── Component ────────────────────────────────────────────────────────────────

export const HostShell: React.FC = () => {
  const [personaKey, setPersonaKey] = useState("carol");
  const [activeTab, setActiveTab] = useState<"weekly" | "dashboard">("weekly");
  const [resetting, setResetting] = useState(false);
  // Bump this key to force-remount the micro-frontend on persona switch
  const [mountKey, setMountKey] = useState(0);

  // Read feature flags from URL once on mount (for E2E tests).
  const testFeatureFlags = useMemo(() => readUrlFeatureFlags(), []);

  const persona = PERSONAS.find((p) => p.key === personaKey) ?? PERSONAS[0];
  const isManager = persona.user.roles.includes("MANAGER");

  const handlePersonaChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    const newKey = e.target.value;
    setPersonaKey(newKey);
    setActiveTab("weekly");
    // Force remount so AuthContext picks up the new user
    setMountKey((k) => k + 1);
  }, []);

  const handleResetData = useCallback(async () => {
    if (resetting) return;
    setResetting(true);
    try {
      const resp = await fetch("/api/v1/dev/reset-seed", {
        method: "POST",
        headers: {
          "Authorization": `Bearer dev:${persona.user.userId}:${persona.user.orgId}:${persona.user.roles.join(",")}`,
          "Content-Type": "application/json",
        },
      });
      if (resp.ok) {
        // Force remount to reload fresh data
        setMountKey((k) => k + 1);
      } else {
        console.error("Reset failed:", resp.status);
      }
    } catch (err) {
      // If the endpoint doesn't exist, fall back to reloading the page
      console.warn("Reset endpoint not available, reloading page...", err);
      window.location.reload();
    } finally {
      setResetting(false);
    }
  }, [resetting]);

  return (
    <div
      data-testid="pa-host-shell"
      style={{
        fontFamily: "'Crimson Pro', Georgia, 'Times New Roman', serif",
        background: "#1C1714",
        minHeight: "100vh",
        color: "#E8DFD4",
      }}
    >
      {/* ── Host header ── */}
      <header
        style={{
          padding: "0.75rem 1.5rem",
          background: "#251E19",
          borderBottom: "1px solid #4A3F35",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flexWrap: "wrap",
          gap: "0.75rem",
        }}
      >
        <h1
          style={{
            margin: 0,
            fontFamily: "'Cormorant Garamond', serif",
            fontSize: "1.375rem",
            fontWeight: 500,
            color: "#E8DFD4",
            letterSpacing: "-0.01em",
          }}
        >
          PA Host Application
        </h1>

        <div style={{ display: "flex", alignItems: "center", gap: "0.75rem", flexWrap: "wrap" }}>
          {/* Reset Data button */}
          <button
            onClick={() => { void handleResetData(); }}
            disabled={resetting}
            style={{
              fontFamily: "'Cinzel', serif",
              fontSize: "0.5625rem",
              fontWeight: 600,
              textTransform: "uppercase" as const,
              letterSpacing: "0.15em",
              padding: "0.3rem 0.75rem",
              borderRadius: "4px",
              border: "1px solid rgba(196, 122, 122, 0.4)",
              background: resetting ? "#3D332B" : "rgba(139, 38, 53, 0.15)",
              color: resetting ? "#7A6E62" : "#C47A84",
              cursor: resetting ? "not-allowed" : "pointer",
              transition: "background 200ms, border-color 200ms",
            }}
          >
            {resetting ? "Resetting…" : "Reset Data"}
          </button>

          {/* Persona switcher */}
          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
            <label
              htmlFor="persona-select"
              style={{
                fontFamily: "'Cinzel', serif",
                fontSize: "0.5625rem",
                fontWeight: 600,
                textTransform: "uppercase" as const,
                letterSpacing: "0.15em",
                color: "#9C8B7A",
              }}
            >
              Persona
            </label>
            <select
              id="persona-select"
              data-testid="persona-select"
              value={personaKey}
              onChange={handlePersonaChange}
              style={{
                fontFamily: "'Crimson Pro', serif",
                fontSize: "0.875rem",
                padding: "0.3rem 0.625rem",
                borderRadius: "4px",
                border: "1px solid #4A3F35",
                background: "#1C1714",
                color: "#E8DFD4",
                cursor: "pointer",
                minWidth: "10rem",
              }}
            >
              {PERSONAS.map((p) => (
                <option key={p.key} value={p.key}>
                  {p.label} ({p.user.roles.join(", ")})
                </option>
              ))}
            </select>
          </div>

          {/* Role badge */}
          <span
            style={{
              fontFamily: "'Cinzel', serif",
              fontSize: "0.5625rem",
              fontWeight: 600,
              textTransform: "uppercase" as const,
              letterSpacing: "0.15em",
              background: "rgba(201, 169, 98, 0.15)",
              color: "#C9A962",
              padding: "0.2rem 0.625rem",
              borderRadius: "4px",
              border: "1px solid rgba(201, 169, 98, 0.30)",
            }}
          >
            {persona.user.roles.join(", ")}
          </span>
        </div>
      </header>

      {/* ── Host navigation ── */}
      <nav
        style={{
          padding: "0.625rem 1.5rem",
          background: "#1C1714",
          display: "flex",
          gap: "1.5rem",
          borderBottom: "1px solid #4A3F35",
          alignItems: "center",
        }}
      >
        <button
          data-testid="host-nav-weekly"
          onClick={() => setActiveTab("weekly")}
          style={{
            fontFamily: "'Cinzel', serif",
            fontSize: "0.65rem",
            fontWeight: activeTab === "weekly" ? 700 : 500,
            textTransform: "uppercase" as const,
            letterSpacing: "0.2em",
            color: activeTab === "weekly" ? "#C9A962" : "#9C8B7A",
            background: "none",
            border: "none",
            borderBottom: activeTab === "weekly" ? "2px solid #C9A962" : "2px solid transparent",
            paddingBottom: "0.5rem",
            cursor: "pointer",
            transition: "color 300ms ease-out, border-color 300ms ease-out",
          }}
        >
          Weekly Commitments
        </button>
        {isManager && (
          <button
            data-testid="host-nav-dashboard"
            onClick={() => setActiveTab("dashboard")}
            style={{
              fontFamily: "'Cinzel', serif",
              fontSize: "0.65rem",
              fontWeight: activeTab === "dashboard" ? 700 : 500,
              textTransform: "uppercase" as const,
              letterSpacing: "0.2em",
              color: activeTab === "dashboard" ? "#C9A962" : "#9C8B7A",
              background: "none",
              border: "none",
              borderBottom: activeTab === "dashboard" ? "2px solid #C9A962" : "2px solid transparent",
              paddingBottom: "0.5rem",
              cursor: "pointer",
              transition: "color 300ms ease-out, border-color 300ms ease-out",
            }}
          >
            Dashboard
          </button>
        )}

        {/* Persona description hint */}
        <span
          style={{
            marginLeft: "auto",
            fontFamily: "'Crimson Pro', serif",
            fontSize: "0.8rem",
            color: "#C0B09F",
            fontStyle: "italic",
          }}
        >
          {persona.description}
        </span>
      </nav>

      {/* ── Content area ── */}
      <main style={{ padding: "0.5rem 1rem" }}>
        {activeTab === "weekly" && (
          <div data-testid="wc-slot">
            <section
              data-testid="wc-remote-mount"
              style={{
                marginTop: "0.5rem",
                borderRadius: "6px",
                border: "1px solid #4A3F35",
                overflow: "hidden",
              }}
            >
              <WeeklyCommitmentsApp
                key={mountKey}
                user={persona.user}
                token={persona.token}
                apiBaseUrl="/api/v1"
                initialRoute="weekly"
                featureFlags={testFeatureFlags}
              />
            </section>
          </div>
        )}
        {activeTab === "dashboard" && isManager && (
          <div data-testid="wc-dashboard-slot">
            <section
              data-testid="wc-dashboard-remote-mount"
              style={{
                marginTop: "0.5rem",
                borderRadius: "6px",
                border: "1px solid #4A3F35",
                overflow: "hidden",
              }}
            >
              <WeeklyCommitmentsApp
                key={mountKey + 1000}
                user={persona.user}
                token={persona.token}
                apiBaseUrl="/api/v1"
                initialRoute="weekly/team"
                featureFlags={testFeatureFlags}
              />
            </section>
          </div>
        )}
      </main>

      {/* ── Host footer ── */}
      <footer
        style={{
          padding: "0.625rem 1.5rem",
          borderTop: "1px solid #4A3F35",
          fontSize: "0.75rem",
          color: "#C0B09F",
          textAlign: "center" as const,
          fontFamily: "'Cinzel', serif",
          letterSpacing: "0.15em",
          textTransform: "uppercase" as const,
        }}
      >
        PA Host Stub — PM Remote Pattern Demo
      </footer>
    </div>
  );
};
