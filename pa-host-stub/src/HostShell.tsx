import React, { useState, useCallback, useMemo } from "react";
import { App as WeeklyCommitmentsApp } from "@weekly-commitments/frontend";

// ─── Test feature-flag injection ─────────────────────────────────────────────
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
  {
    key: "dana",
    label: "Dana Torres",
    description: "Admin + Manager — org-wide visibility, executive dashboard",
    user: {
      userId: "c0000000-0000-0000-0000-000000000030",
      orgId: ORG_ID,
      displayName: "Dana Torres",
      roles: ["IC", "MANAGER", "ADMIN"],
      timezone: "America/Chicago",
    },
    token: "dev-token-dana",
  },
];

// ─── Component ────────────────────────────────────────────────────────────────
export const HostShell: React.FC = () => {
  const [personaKey, setPersonaKey] = useState("carol");
  const [resetting, setResetting] = useState(false);
  const [devBarOpen, setDevBarOpen] = useState(false);
  // Bump to force-remount the micro-frontend on persona switch or reset
  const [mountKey, setMountKey] = useState(0);

  const testFeatureFlags = useMemo(() => readUrlFeatureFlags(), []);
  const persona = PERSONAS.find((p) => p.key === personaKey) ?? PERSONAS[0];

  const handlePersonaChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setPersonaKey(e.target.value);
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
        setMountKey((k) => k + 1);
      } else {
        console.error("Reset failed:", resp.status);
      }
    } catch (err) {
      console.warn("Reset endpoint not available, reloading page...", err);
      window.location.reload();
    } finally {
      setResetting(false);
    }
  }, [persona, resetting]);

  return (
    <div
      data-testid="pa-host-shell"
      style={{
        fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
        background: "#f8f9fb",
        minHeight: "100vh",
        color: "#1a2332",
      }}
    >
      {/* ── Floating dev tools (⚙ gear, top-right) ── */}
      <div style={{ position: "fixed", top: 8, right: 8, zIndex: 120, display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 4 }}>
        <button
          onClick={() => setDevBarOpen((o) => !o)}
          aria-label="Toggle dev tools"
          style={{
            width: 28, height: 28, borderRadius: 6,
            border: "1px solid #e2e5ea",
            background: devBarOpen ? "#1a2332" : "rgba(255,255,255,0.92)",
            color: devBarOpen ? "#fff" : "#94a3b8",
            fontSize: 13, cursor: "pointer",
            display: "flex", alignItems: "center", justifyContent: "center",
            boxShadow: "0 1px 4px rgba(0,0,0,0.08)",
            transition: "background 150ms, color 150ms",
          }}
          title="Dev Tools"
        >
          ⚙
        </button>

        {devBarOpen && (
          <div style={{
            background: "#fff", border: "1px solid #e2e5ea", borderRadius: 10,
            boxShadow: "0 8px 24px rgba(0,0,0,0.10)", padding: "12px 14px",
            display: "flex", flexDirection: "column", gap: 10, minWidth: 240,
          }}>
            <div style={{ fontSize: 10, fontWeight: 600, textTransform: "uppercase" as const, letterSpacing: "0.08em", color: "#94a3b8" }}>
              Dev Tools
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
              <label htmlFor="persona-select" style={{ fontSize: 11, fontWeight: 500, color: "#64748b" }}>
                Persona
              </label>
              <select
                id="persona-select"
                data-testid="persona-select"
                value={personaKey}
                onChange={handlePersonaChange}
                style={{ fontSize: 13, padding: "5px 8px", borderRadius: 6, border: "1px solid #e2e5ea", background: "#f8f9fb", color: "#1a2332", cursor: "pointer" }}
              >
                {PERSONAS.map((p) => (
                  <option key={p.key} value={p.key}>
                    {p.label} ({p.user.roles.join(", ")})
                  </option>
                ))}
              </select>
            </div>

            <div style={{ fontSize: 11, color: "#94a3b8", fontStyle: "italic", lineHeight: 1.4 }}>
              {persona.description}
            </div>

            <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
              {persona.user.roles.map((role) => (
                <span key={role} style={{ fontSize: 10, fontWeight: 600, textTransform: "uppercase" as const, letterSpacing: "0.04em", padding: "2px 8px", borderRadius: 4, background: "#dbeafe", color: "#2563eb" }}>
                  {role}
                </span>
              ))}
            </div>

            <button
              onClick={() => { void handleResetData(); }}
              disabled={resetting}
              style={{ fontSize: 11, fontWeight: 500, padding: "5px 12px", borderRadius: 6, border: "1px solid #fecaca", background: resetting ? "#f3f4f6" : "#fef2f2", color: resetting ? "#94a3b8" : "#dc2626", cursor: resetting ? "not-allowed" : "pointer" }}
            >
              {resetting ? "Resetting…" : "Reset Data"}
            </button>
          </div>
        )}
      </div>

      {/* ── App — full viewport, no host chrome ── */}
      <div data-testid="wc-slot">
        <section data-testid="wc-remote-mount">
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
    </div>
  );
};
