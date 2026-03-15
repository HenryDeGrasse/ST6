import React, { useState } from "react";
import { App as WeeklyCommitmentsApp } from "@weekly-commitments/frontend";

/**
 * Stub auth user representing the PA host's identity context.
 * In production the host provides real JWT claims.
 */
const STUB_USER = {
  userId: "c0000000-0000-0000-0000-000000000001",
  orgId: "a0000000-0000-0000-0000-000000000001",
  displayName: "Jane Doe (stub)",
  roles: ["IC", "MANAGER"],
  timezone: "America/Chicago",
};

const STUB_TOKEN = "stub-jwt-eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9";

/**
 * Simulates the PA host application shell.
 *
 * In production the WC micro-frontend is loaded via Module Federation.
 * This stub demonstrates:
 * 1. Auth/context handoff (user identity + JWT)
 * 2. Navigation frame (host → WC route)
 * 3. Slot rendering (the micro-frontend renders into a designated area)
 *
 * The auth contract is: the host provides `user` and `token` props
 * to the micro-frontend's root component, or exposes them via
 * a shared Module Federation context.
 */
export const HostShell: React.FC = () => {
  const [activeTab, setActiveTab] = useState<"weekly" | "dashboard">("weekly");

  return (
    <div data-testid="pa-host-shell" style={{ fontFamily: "system-ui, sans-serif" }}>
      {/* Host header / navigation */}
      <header
        style={{
          padding: "0.75rem 1.5rem",
          background: "#1a237e",
          color: "#fff",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <div>
          <h1 style={{ margin: 0, fontSize: "1.25rem" }}>PA Host Application</h1>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
          <span style={{ fontSize: "0.85rem" }}>{STUB_USER.displayName}</span>
          <span
            style={{
              fontSize: "0.7rem",
              background: "rgba(255,255,255,0.2)",
              padding: "0.15rem 0.5rem",
              borderRadius: "4px",
            }}
          >
            {STUB_USER.roles.join(", ")}
          </span>
        </div>
      </header>

      {/* Host navigation */}
      <nav
        style={{
          padding: "0.5rem 1.5rem",
          background: "#e8eaf6",
          display: "flex",
          gap: "1rem",
          borderBottom: "1px solid #c5cae9",
        }}
      >
        <button
          onClick={() => setActiveTab("weekly")}
          style={{
            padding: "0.35rem 1rem",
            fontWeight: activeTab === "weekly" ? 700 : 400,
            background: activeTab === "weekly" ? "#fff" : "transparent",
            border: "1px solid #c5cae9",
            borderRadius: "4px",
            cursor: "pointer",
          }}
        >
          Weekly Commitments
        </button>
        <button
          onClick={() => setActiveTab("dashboard")}
          style={{
            padding: "0.35rem 1rem",
            fontWeight: activeTab === "dashboard" ? 700 : 400,
            background: activeTab === "dashboard" ? "#fff" : "transparent",
            border: "1px solid #c5cae9",
            borderRadius: "4px",
            cursor: "pointer",
          }}
        >
          Dashboard
        </button>
      </nav>

      {/* Content area — this is where the micro-frontend renders */}
      <main style={{ padding: "1rem 1.5rem" }}>
        {activeTab === "weekly" && (
          <div data-testid="wc-slot">
            <div
              style={{
                padding: "1rem",
                background: "#fffde7",
                borderRadius: "4px",
                marginBottom: "1rem",
                fontSize: "0.85rem",
              }}
            >
              <strong>Host Stub Info:</strong> This local shell is actively mounting the WC micro-frontend
              with the same prop contract the PA host would use in production.
              <code style={{ display: "block", marginTop: "0.5rem", fontSize: "0.8rem", color: "#333" }}>
                {`{ userId: "${STUB_USER.userId}", orgId: "${STUB_USER.orgId}", token: "${STUB_TOKEN.slice(0, 20)}...", apiBaseUrl: "/api/v1" }`}
              </code>
            </div>
            <p style={{ color: "#555", marginTop: 0 }}>
              The host passes <code>user</code>, <code>token</code>, and <code>apiBaseUrl</code> to the remote.
              The remote owns its internal auth/api contexts from there.
            </p>
            <section
              data-testid="wc-remote-mount"
              style={{
                marginTop: "1rem",
                padding: "1rem",
                background: "#fafafa",
                border: "1px solid #e0e0e0",
                borderRadius: "8px",
              }}
            >
              <WeeklyCommitmentsApp
                user={STUB_USER}
                token={STUB_TOKEN}
                apiBaseUrl="/api/v1"
                initialRoute="weekly"
              />
            </section>
          </div>
        )}
        {activeTab === "dashboard" && (
          <div data-testid="wc-dashboard-slot">
            <div
              style={{
                padding: "1rem",
                background: "#e8f5e9",
                borderRadius: "4px",
                marginBottom: "1rem",
                fontSize: "0.85rem",
              }}
            >
              <strong>Host Stub Info:</strong> Manager dashboard view — the host mounts the same WC
              micro-frontend with <code>initialRoute=&quot;weekly/team&quot;</code> to render the team view.
            </div>
            <section
              data-testid="wc-dashboard-remote-mount"
              style={{
                marginTop: "1rem",
                padding: "1rem",
                background: "#fafafa",
                border: "1px solid #e0e0e0",
                borderRadius: "8px",
              }}
            >
              <WeeklyCommitmentsApp
                user={STUB_USER}
                token={STUB_TOKEN}
                apiBaseUrl="/api/v1"
                initialRoute="weekly/team"
              />
            </section>
          </div>
        )}
      </main>

      {/* Host footer */}
      <footer
        style={{
          padding: "0.5rem 1.5rem",
          borderTop: "1px solid #e0e0e0",
          fontSize: "0.75rem",
          color: "#888",
          textAlign: "center",
        }}
      >
        PA Host Stub — PM Remote Pattern Demo
      </footer>
    </div>
  );
};
