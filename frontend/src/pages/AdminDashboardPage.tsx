import React, { useCallback, useEffect, useMemo, useState } from "react";
import type { OrgPolicy, UpdateDigestConfigRequest } from "@weekly-commitments/contracts";
import { GlassPanel } from "../components/GlassPanel.js";
import { ErrorBanner } from "../components/ErrorBanner.js";
import { OutcomeMetadataEditor } from "../components/UrgencyIndicator/OutcomeMetadataEditor.js";
import { useAdminDashboard } from "../hooks/useAdminDashboard.js";
import {
  useOutcomeMetadata,
  type OutcomeMetadataRequest,
  type ProgressUpdateRequest,
} from "../hooks/useOutcomeMetadata.js";
import { useRcdo } from "../hooks/useRcdo.js";
import { useAuth } from "../context/AuthContext.js";
import {
  FEATURE_FLAGS_STORAGE_KEY,
  useFeatureFlags,
  type FeatureFlags,
} from "../context/FeatureFlagContext.js";
import styles from "./AdminDashboardPage.module.css";

// ─── Tab configuration ──────────────────────────────────────────────────────

type AdminTab =
  | "adoption"
  | "cadence"
  | "chess"
  | "rcdo-health"
  | "ai-usage"
  | "feature-flags"
  | "outcome-targets";

const TABS: { id: AdminTab; label: string }[] = [
  { id: "adoption", label: "Adoption Funnel" },
  { id: "cadence", label: "Cadence Config" },
  { id: "chess", label: "Chess Rules" },
  { id: "rcdo-health", label: "RCDO Health" },
  { id: "ai-usage", label: "AI Usage" },
  { id: "feature-flags", label: "Feature Flags" },
  { id: "outcome-targets", label: "Outcome Targets" },
];

const DAY_OPTIONS = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;

/** Format a fractional rate (0–1) as a percentage string. */
function fmtPct(value: number): string {
  return `${Math.round(value * 100)}%`;
}

/** Capitalise first letter, lowercase rest (e.g. "MONDAY" → "Monday"). */
function titleCase(s: string): string {
  if (!s) return s;
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
}

// ─── Flag metadata ──────────────────────────────────────────────────────────

interface FlagMeta {
  key: keyof FeatureFlags;
  label: string;
  description: string;
}

const FLAG_META: FlagMeta[] = [
  {
    key: "suggestRcdo",
    label: "Suggest RCDO",
    description: "AI-powered RCDO outcome suggestions when creating commits.",
  },
  {
    key: "draftReconciliation",
    label: "Draft Reconciliation",
    description: "AI-drafted actuals during the reconciliation workflow.",
  },
  {
    key: "managerInsights",
    label: "Manager Insights",
    description: "LLM-generated manager insight headlines on the team dashboard.",
  },
  {
    key: "icTrends",
    label: "IC Trends",
    description: "Cross-week trend analytics panel on the IC plan page.",
  },
  {
    key: "planQualityNudge",
    label: "Plan Quality Nudge",
    description: "AI quality nudges surfaced at plan lock time.",
  },
  {
    key: "startMyWeek",
    label: "Start My Week",
    description: "Draft-from-history flow that pre-fills the new week from recent plans.",
  },
  {
    key: "suggestNextWork",
    label: "Suggest Next Work",
    description: "Data-driven next-work suggestions panel on the IC plan page.",
  },
  {
    key: "dailyCheckIn",
    label: "Daily Check-In",
    description: "Quick daily check-in capability for locked commits.",
  },
  {
    key: "outcomeUrgency",
    label: "Outcome Urgency",
    description: "Urgency bands and target-date tracking for RCDO outcomes.",
  },
  {
    key: "strategicSlack",
    label: "Strategic Slack",
    description: "Strategic focus floor recommendations based on outcome urgency.",
  },
  {
    key: "targetDateForecasting",
    label: "Target-date Forecasting",
    description: "Persisted outcome forecasting cards and risk summaries for managers.",
  },
  {
    key: "planningCopilot",
    label: "Planning Copilot",
    description: "Manager planning-copilot suggestions and apply flow for weekly draft plans.",
  },
  {
    key: "executiveDashboard",
    label: "Executive Dashboard",
    description: "Executive strategic-health dashboard and AI briefing surfaces.",
  },
  {
    key: "weeklyPlanningAgent",
    label: "Weekly Planning Agent",
    description: "Proactive weekly-planning agent notifications and draft-ready surfaces.",
  },
  {
    key: "misalignmentAgent",
    label: "Misalignment Agent",
    description: "Manager briefing notifications when team planning signals drift out of alignment.",
  },
];

// ─── Sub-panel components ───────────────────────────────────────────────────

/**
 * Adoption Funnel tab — shows a table of plan lifecycle funnel by week,
 * plus summary metrics (total active users, cadence compliance rate).
 */
const AdoptionFunnelPanel: React.FC<{
  adoptionMetrics: ReturnType<typeof useAdminDashboard>["adoptionMetrics"];
  loading: boolean;
  error: string | null;
  onFetch: (weeks: number) => void;
  selectedWeeks: number;
}> = ({ adoptionMetrics, loading, error, onFetch, selectedWeeks }) => {
  const windowOptions = [4, 8, 12, 26];

  return (
    <div data-testid="adoption-funnel-panel">
      <h3 className={styles.sectionTitle}>Adoption Funnel</h3>
      <p className={styles.sectionDescription}>
        Weekly breakdown of the planning lifecycle funnel: plans created → locked → reconciled →
        reviewed. Use this to track team adoption and identify drop-off points.
      </p>

      <div className={styles.windowRow}>
        <span className={styles.windowLabel}>Window:</span>
        {windowOptions.map((w) => (
          <button
            key={w}
            data-testid={`window-btn-${String(w)}`}
            className={`${styles.windowButton} ${selectedWeeks === w ? styles.windowButtonActive : ""}`}
            onClick={() => {
              onFetch(w);
            }}
          >
            {w}w
          </button>
        ))}
      </div>

      {error && (
        <div data-testid="adoption-error" className={styles.errorBox}>
          {error}
        </div>
      )}

      {loading && !adoptionMetrics && (
        <div data-testid="adoption-loading" className={styles.loading}>
          Loading adoption metrics…
        </div>
      )}

      {adoptionMetrics && (
        <>
          <div data-testid="adoption-metrics" className={styles.metricStrip}>
            <div className={styles.metricCard} data-testid="metric-active-users">
              <span className={styles.metricLabel}>Active Users</span>
              <span className={styles.metricValue}>{adoptionMetrics.totalActiveUsers}</span>
              <span className={styles.metricSub}>{adoptionMetrics.weeks}-week window</span>
            </div>
            <div className={styles.metricCard} data-testid="metric-cadence-compliance">
              <span className={styles.metricLabel}>Cadence Compliance</span>
              <span className={styles.metricValue}>{fmtPct(adoptionMetrics.cadenceComplianceRate)}</span>
              <span className={styles.metricSub}>plans locked on time</span>
            </div>
          </div>

          {adoptionMetrics.weeklyPoints.length === 0 ? (
            <div data-testid="adoption-empty" className={styles.emptyState}>
              No plan data in the selected window.
            </div>
          ) : (
            <table data-testid="adoption-table" className={styles.table}>
              <thead>
                <tr>
                  <th>Week</th>
                  <th>Active</th>
                  <th>Created</th>
                  <th>Locked</th>
                  <th>Reconciled</th>
                  <th>Reviewed</th>
                </tr>
              </thead>
              <tbody>
                {[...adoptionMetrics.weeklyPoints].reverse().map((point) => (
                  <tr key={point.weekStart} data-testid={`adoption-row-${point.weekStart}`}>
                    <td>{point.weekStart}</td>
                    <td>{point.activeUsers}</td>
                    <td>{point.plansCreated}</td>
                    <td>{point.plansLocked}</td>
                    <td>{point.plansReconciled}</td>
                    <td>{point.plansReviewed}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  );
};

/**
 * Cadence Configuration tab — shows lock/reconcile day and time from org
 * policy (read-only; only digest is writable via the current API).
 */
const CadenceConfigPanel: React.FC<{
  orgPolicy: OrgPolicy | null;
  loading: boolean;
  error: string | null;
  onSaveDigest: (req: UpdateDigestConfigRequest) => Promise<OrgPolicy | null>;
  onReload: () => void;
}> = ({ orgPolicy, loading, error, onSaveDigest, onReload }) => {
  const [digestDay, setDigestDay] = useState(orgPolicy?.digestDay ?? "FRIDAY");
  const [digestTime, setDigestTime] = useState(orgPolicy?.digestTime ?? "17:00");
  const [saving, setSaving] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Sync local state when policy loads/reloads
  useEffect(() => {
    if (orgPolicy) {
      setDigestDay(orgPolicy.digestDay);
      setDigestTime(orgPolicy.digestTime);
    }
  }, [orgPolicy]);

  const isDirty =
    orgPolicy !== null &&
    (digestDay !== orgPolicy.digestDay || digestTime !== orgPolicy.digestTime);

  const handleSave = useCallback(async () => {
    setSaving(true);
    setSuccessMsg(null);
    const updated = await onSaveDigest({ digestDay, digestTime });
    setSaving(false);
    if (updated) {
      setSuccessMsg("Digest schedule saved.");
    }
  }, [digestDay, digestTime, onSaveDigest]);

  return (
    <div data-testid="cadence-config-panel">
      <h3 className={styles.sectionTitle}>Cadence Configuration</h3>
      <p className={styles.sectionDescription}>
        Lock and reconciliation reminder cadence is configured server-side. Digest schedule can be
        updated here.
        <span className={styles.readonlyBadge}>Read-only fields from server</span>
      </p>

      {error && (
        <div data-testid="cadence-error" className={styles.errorBox}>
          {error}
        </div>
      )}

      {loading && !orgPolicy && (
        <div data-testid="cadence-loading" className={styles.loading}>
          Loading policy…
        </div>
      )}

      {orgPolicy && (
        <>
          {/* Read-only cadence fields */}
          <div data-testid="cadence-fields" className={styles.fieldGrid}>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>Lock Day</span>
              <span data-testid="lock-day" className={styles.fieldValue}>
                {titleCase(orgPolicy.lockDay)}
              </span>
            </div>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>Lock Time</span>
              <span data-testid="lock-time" className={styles.fieldValue}>
                {orgPolicy.lockTime}
              </span>
            </div>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>Reconcile Day</span>
              <span data-testid="reconcile-day" className={styles.fieldValue}>
                {titleCase(orgPolicy.reconcileDay)}
              </span>
            </div>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>Reconcile Time</span>
              <span data-testid="reconcile-time" className={styles.fieldValue}>
                {orgPolicy.reconcileTime}
              </span>
            </div>
          </div>

          {/* Editable digest fields */}
          <h4 className={styles.sectionTitle} style={{ fontSize: "1rem" }}>
            Weekly Digest Schedule
          </h4>
          <div data-testid="digest-fields" className={styles.fieldGrid}>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Digest Day</span>
              <select
                data-testid="cadence-digest-day"
                value={digestDay}
                onChange={(e) => {
                  setDigestDay(e.target.value);
                  setSuccessMsg(null);
                }}
                disabled={loading || saving}
                className={styles.select}
              >
                {DAY_OPTIONS.map((d) => (
                  <option key={d} value={d}>
                    {titleCase(d)}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Digest Time</span>
              <input
                data-testid="cadence-digest-time"
                type="time"
                step={900}
                value={digestTime}
                onChange={(e) => {
                  setDigestTime(e.target.value);
                  setSuccessMsg(null);
                }}
                disabled={loading || saving}
                className={styles.input}
              />
            </label>
          </div>

          <div className={styles.actions}>
            <button
              type="button"
              data-testid="save-cadence-btn"
              className={styles.saveButton}
              disabled={loading || saving || !isDirty}
              onClick={() => {
                void handleSave();
              }}
            >
              {saving ? "Saving…" : "Save Digest Schedule"}
            </button>
            <button
              type="button"
              data-testid="reload-cadence-btn"
              className={styles.secondaryButton}
              disabled={loading || saving}
              onClick={() => {
                setSuccessMsg(null);
                onReload();
              }}
            >
              Reload
            </button>
            {successMsg && (
              <span data-testid="cadence-success" role="status" className={styles.successMsg}>
                {successMsg}
              </span>
            )}
          </div>
        </>
      )}
    </div>
  );
};

/**
 * Chess Rule Configuration tab — shows chess constraint settings from org
 * policy (read-only via API; server-side managed).
 */
const ChessRulePanel: React.FC<{
  orgPolicy: OrgPolicy | null;
  loading: boolean;
  error: string | null;
}> = ({ orgPolicy, loading, error }) => {
  return (
    <div data-testid="chess-rule-panel">
      <h3 className={styles.sectionTitle}>Chess Rule Configuration</h3>
      <p className={styles.sectionDescription}>
        Chess priority constraints applied at plan lock time. These settings are managed server-side.
        <span className={styles.readonlyBadge}>Read-only</span>
      </p>

      {error && (
        <div data-testid="chess-error" className={styles.errorBox}>
          {error}
        </div>
      )}

      {loading && !orgPolicy && (
        <div data-testid="chess-loading" className={styles.loading}>
          Loading policy…
        </div>
      )}

      {orgPolicy && (
        <div data-testid="chess-fields" className={styles.fieldGrid}>
          <div className={styles.field}>
            <span className={styles.fieldLabel}>King Required</span>
            <span data-testid="chess-king-required" className={styles.fieldValue}>
              {orgPolicy.chessKingRequired ? "Yes" : "No"}
            </span>
          </div>
          <div className={styles.field}>
            <span className={styles.fieldLabel}>Max King Commits</span>
            <span data-testid="chess-max-king" className={styles.fieldValue}>
              {orgPolicy.chessMaxKing}
            </span>
          </div>
          <div className={styles.field}>
            <span className={styles.fieldLabel}>Max Queen Commits</span>
            <span data-testid="chess-max-queen" className={styles.fieldValue}>
              {orgPolicy.chessMaxQueen}
            </span>
          </div>
          <div className={styles.field}>
            <span className={styles.fieldLabel}>Block Lock on Stale RCDO</span>
            <span data-testid="chess-block-stale-rcdo" className={styles.fieldValue}>
              {orgPolicy.blockLockOnStaleRcdo ? "Yes" : "No"}
            </span>
          </div>
        </div>
      )}
    </div>
  );
};

/**
 * RCDO Health tab — shows outcomes sorted by commit coverage, highlighting
 * stale (zero-commit) outcomes.
 */
const RcdoHealthPanel: React.FC<{
  rcdoHealthReport: ReturnType<typeof useAdminDashboard>["rcdoHealthReport"];
  loading: boolean;
  error: string | null;
  onFetch: () => void;
}> = ({ rcdoHealthReport, loading, error, onFetch }) => {
  return (
    <div data-testid="rcdo-health-panel">
      <h3 className={styles.sectionTitle}>RCDO Health</h3>
      <p className={styles.sectionDescription}>
        Commit coverage across RCDO outcomes over the last 8 weeks. Stale outcomes (zero commits)
        indicate strategic areas that may be under-resourced.
      </p>

      <div className={styles.actions}>
        <button
          type="button"
          data-testid="reload-rcdo-btn"
          className={styles.secondaryButton}
          disabled={loading}
          onClick={onFetch}
        >
          {loading ? "Loading…" : "Refresh Report"}
        </button>
      </div>

      {error && (
        <div data-testid="rcdo-error" className={styles.errorBox}>
          {error}
        </div>
      )}

      {loading && !rcdoHealthReport && (
        <div data-testid="rcdo-loading" className={styles.loading}>
          Loading RCDO health report…
        </div>
      )}

      {rcdoHealthReport && (
        <>
          <div data-testid="rcdo-metrics" className={styles.metricStrip}>
            <div className={styles.metricCard} data-testid="metric-total-outcomes">
              <span className={styles.metricLabel}>Total Outcomes</span>
              <span className={styles.metricValue}>{rcdoHealthReport.totalOutcomes}</span>
            </div>
            <div className={styles.metricCard} data-testid="metric-covered-outcomes">
              <span className={styles.metricLabel}>Covered</span>
              <span className={styles.metricValue}>{rcdoHealthReport.coveredOutcomes}</span>
              <span className={styles.metricSub}>
                {rcdoHealthReport.totalOutcomes > 0
                  ? fmtPct(rcdoHealthReport.coveredOutcomes / rcdoHealthReport.totalOutcomes)
                  : "—"}
              </span>
            </div>
            <div className={styles.metricCard} data-testid="metric-stale-outcomes">
              <span className={styles.metricLabel}>Stale</span>
              <span className={styles.metricValue}>{rcdoHealthReport.staleOutcomes.length}</span>
              <span className={styles.metricSub}>0 commits in {rcdoHealthReport.windowWeeks}w</span>
            </div>
          </div>

          {rcdoHealthReport.topOutcomes.length === 0 &&
            rcdoHealthReport.staleOutcomes.length === 0 ? (
            <div data-testid="rcdo-empty" className={styles.emptyState}>
              No outcomes found in the RCDO tree.
            </div>
          ) : (
            <table data-testid="rcdo-table" className={styles.table}>
              <thead>
                <tr>
                  <th>Outcome</th>
                  <th>Objective</th>
                  <th>Rally Cry</th>
                  <th>Commits</th>
                </tr>
              </thead>
              <tbody>
                {rcdoHealthReport.topOutcomes.map((item) => (
                  <tr key={item.outcomeId} data-testid={`rcdo-row-${item.outcomeId}`}>
                    <td>{item.outcomeName}</td>
                    <td>{item.objectiveName}</td>
                    <td>{item.rallyCryName}</td>
                    <td>{item.commitCount}</td>
                  </tr>
                ))}
                {rcdoHealthReport.staleOutcomes.map((item) => (
                  <tr
                    key={item.outcomeId}
                    data-testid={`rcdo-stale-row-${item.outcomeId}`}
                    className={styles.rcdoItemStale}
                  >
                    <td>
                      {item.outcomeName}
                      <span className={styles.staleTag}>Stale</span>
                    </td>
                    <td>{item.objectiveName}</td>
                    <td>{item.rallyCryName}</td>
                    <td>0</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  );
};

/**
 * AI Usage tab — shows suggestion acceptance rates and cache metrics.
 */
const AiUsagePanel: React.FC<{
  aiUsageMetrics: ReturnType<typeof useAdminDashboard>["aiUsageMetrics"];
  loading: boolean;
  error: string | null;
  onFetch: (weeks: number) => void;
  selectedWeeks: number;
}> = ({ aiUsageMetrics, loading, error, onFetch, selectedWeeks }) => {
  const windowOptions = [4, 8, 12, 26];

  return (
    <div data-testid="ai-usage-panel">
      <h3 className={styles.sectionTitle}>AI Usage</h3>
      <p className={styles.sectionDescription}>
        Suggestion acceptance, deferral, and decline rates alongside AI cache performance metrics.
      </p>

      <div className={styles.windowRow}>
        <span className={styles.windowLabel}>Window:</span>
        {windowOptions.map((w) => (
          <button
            key={w}
            data-testid={`ai-window-btn-${String(w)}`}
            className={`${styles.windowButton} ${selectedWeeks === w ? styles.windowButtonActive : ""}`}
            onClick={() => {
              onFetch(w);
            }}
          >
            {w}w
          </button>
        ))}
      </div>

      {error && (
        <div data-testid="ai-usage-error" className={styles.errorBox}>
          {error}
        </div>
      )}

      {loading && !aiUsageMetrics && (
        <div data-testid="ai-usage-loading" className={styles.loading}>
          Loading AI usage metrics…
        </div>
      )}

      {aiUsageMetrics && (
        <>
          <div data-testid="ai-usage-metrics" className={styles.metricStrip}>
            <div className={styles.metricCard} data-testid="metric-acceptance-rate">
              <span className={styles.metricLabel}>Acceptance Rate</span>
              <span className={styles.metricValue}>{fmtPct(aiUsageMetrics.acceptanceRate)}</span>
              <span className={styles.metricSub}>{aiUsageMetrics.totalFeedbackCount} total feedback</span>
            </div>
            <div className={styles.metricCard} data-testid="metric-cache-hit-rate">
              <span className={styles.metricLabel}>Cache Hit Rate</span>
              <span className={styles.metricValue}>{fmtPct(aiUsageMetrics.cacheHitRate)}</span>
              <span className={styles.metricSub}>
                {aiUsageMetrics.cacheHits} hits / {aiUsageMetrics.cacheMisses} misses
              </span>
            </div>
            <div className={styles.metricCard} data-testid="metric-tokens-saved">
              <span className={styles.metricLabel}>~Tokens Saved</span>
              <span className={styles.metricValue}>
                {aiUsageMetrics.approximateTokensSaved.toLocaleString()}
              </span>
              <span className={styles.metricSub}>by cache</span>
            </div>
          </div>

          <div data-testid="ai-feedback-breakdown" className={styles.fieldGrid}>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>Accepted</span>
              <span data-testid="ai-accepted-count" className={styles.fieldValue}>
                {aiUsageMetrics.acceptedCount}
              </span>
            </div>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>Deferred</span>
              <span data-testid="ai-deferred-count" className={styles.fieldValue}>
                {aiUsageMetrics.deferredCount}
              </span>
            </div>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>Declined</span>
              <span data-testid="ai-declined-count" className={styles.fieldValue}>
                {aiUsageMetrics.declinedCount}
              </span>
            </div>
          </div>
        </>
      )}
    </div>
  );
};

/**
 * Feature Flag Management tab — shows current AI feature flag states as
 * toggles. Since flags are provided by the host app, changes are persisted
 * to localStorage and take effect after reload.
 */
const FeatureFlagPanel: React.FC<{
  localFlags: FeatureFlags;
  onToggle: (key: keyof FeatureFlags) => void;
  onSave: () => void;
  onReset: () => void;
  isDirty: boolean;
  savedMsg: string | null;
}> = ({ localFlags, onToggle, onSave, onReset, isDirty, savedMsg }) => {
  return (
    <div data-testid="feature-flag-panel">
      <h3 className={styles.sectionTitle}>Feature Flag Management</h3>
      <p className={styles.sectionDescription}>
        Toggle AI-assisted features for this session. Changes are saved to local storage and take
        effect on the next page load.
      </p>

      <div data-testid="flag-list" className={styles.flagList}>
        {FLAG_META.map(({ key, label, description }) => (
          <div key={key} data-testid={`flag-row-${key}`} className={styles.flagRow}>
            <div className={styles.flagInfo}>
              <div className={styles.flagName}>{label}</div>
              <div className={styles.flagDesc}>{description}</div>
            </div>
            <label
              className={styles.toggle}
              aria-label={`Toggle ${label}`}
              data-testid={`flag-toggle-label-${key}`}
            >
              <input
                type="checkbox"
                data-testid={`flag-toggle-${key}`}
                checked={localFlags[key]}
                onChange={() => {
                  onToggle(key);
                }}
              />
              <span className={styles.toggleSlider} />
            </label>
          </div>
        ))}
      </div>

      <div className={styles.actions}>
        <button
          type="button"
          data-testid="save-flags-btn"
          className={styles.saveButton}
          disabled={!isDirty}
          onClick={onSave}
        >
          Save Flags
        </button>
        <button
          type="button"
          data-testid="reset-flags-btn"
          className={styles.secondaryButton}
          disabled={!isDirty}
          onClick={onReset}
        >
          Reset
        </button>
        {savedMsg && (
          <span data-testid="flags-saved-msg" role="status" className={styles.successMsg}>
            {savedMsg}
          </span>
        )}
      </div>
    </div>
  );
};

// ─── Main AdminDashboardPage ────────────────────────────────────────────────

/**
 * Administrative dashboard page for org-level configuration and analytics.
 *
 * Gated behind the ADMIN role. Renders a tabbed interface with:
 * - Adoption Funnel: per-week plan lifecycle funnel
 * - Cadence Configuration: lock/reconcile schedule (read) + digest config (write)
 * - Chess Rules: chess commit constraints from org policy (read-only)
 * - RCDO Health: outcome coverage ranking with stale outcome highlights
 * - AI Usage: suggestion acceptance rates and cache metrics
 * - Feature Flags: per-session AI feature flag toggles
 */
export const AdminDashboardPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user.roles.includes("ADMIN");
  const flags = useFeatureFlags();

  const [activeTab, setActiveTab] = useState<AdminTab>("adoption");
  const [adoptionWeeks, setAdoptionWeeks] = useState(8);
  const [aiUsageWeeks, setAiUsageWeeks] = useState(8);

  // Feature flag local state (initialized from context)
  const [localFlags, setLocalFlags] = useState<FeatureFlags>(() => ({ ...flags }));
  const [flagsDirty, setFlagsDirty] = useState(false);
  const [flagsSavedMsg, setFlagsSavedMsg] = useState<string | null>(null);

  const {
    orgPolicy,
    adoptionMetrics,
    aiUsageMetrics,
    rcdoHealthReport,
    loadingPolicy,
    loadingAdoption,
    loadingAiUsage,
    loadingRcdoHealth,
    errorPolicy,
    errorAdoption,
    errorAiUsage,
    errorRcdoHealth,
    fetchOrgPolicy,
    fetchAdoptionMetrics,
    fetchAiUsageMetrics,
    fetchRcdoHealth,
    updateDigestConfig,
    clearErrors,
  } = useAdminDashboard();

  const {
    outcomeMetadata,
    loading: metaLoading,
    fetchMetadata,
    updateMetadata,
    updateProgress,
  } = useOutcomeMetadata();

  const { tree: rcdoTree, fetchTree } = useRcdo();

  // Flatten the RCDO tree into { outcomeId, outcomeName } for the editor.
  const rcdoOutcomes = useMemo(
    () =>
      rcdoTree.flatMap((cry) =>
        cry.objectives.flatMap((obj) =>
          obj.outcomes.map((o) => ({ outcomeId: o.id, outcomeName: o.name })),
        ),
      ),
    [rcdoTree],
  );

  const handleSaveOutcomeMetadata = useCallback(
    async (outcomeId: string, data: OutcomeMetadataRequest): Promise<void> => {
      await updateMetadata(outcomeId, data);
    },
    [updateMetadata],
  );

  const handleUpdateOutcomeProgress = useCallback(
    async (outcomeId: string, data: ProgressUpdateRequest): Promise<void> => {
      await updateProgress(outcomeId, data);
    },
    [updateProgress],
  );

  // Fetch data for the active tab on mount and tab changes
  useEffect(() => {
    if (!isAdmin) return;
    switch (activeTab) {
      case "adoption":
        void fetchAdoptionMetrics(adoptionWeeks);
        break;
      case "cadence":
      case "chess":
        void fetchOrgPolicy();
        break;
      case "rcdo-health":
        void fetchRcdoHealth();
        break;
      case "ai-usage":
        void fetchAiUsageMetrics(aiUsageWeeks);
        break;
      case "feature-flags":
        // No server fetch needed
        break;
      case "outcome-targets":
        void fetchMetadata();
        void fetchTree();
        break;
    }
  }, [
    activeTab,
    isAdmin,
    fetchAdoptionMetrics,
    fetchOrgPolicy,
    fetchRcdoHealth,
    fetchAiUsageMetrics,
    adoptionWeeks,
    aiUsageWeeks,
    fetchMetadata,
    fetchTree,
  ]);

  const handleAdoptionFetch = useCallback(
    (weeks: number) => {
      setAdoptionWeeks(weeks);
      void fetchAdoptionMetrics(weeks);
    },
    [fetchAdoptionMetrics],
  );

  const handleAiUsageFetch = useCallback(
    (weeks: number) => {
      setAiUsageWeeks(weeks);
      void fetchAiUsageMetrics(weeks);
    },
    [fetchAiUsageMetrics],
  );

  const handleToggleFlag = useCallback((key: keyof FeatureFlags) => {
    setLocalFlags((prev) => ({ ...prev, [key]: !prev[key] }));
    setFlagsDirty(true);
    setFlagsSavedMsg(null);
  }, []);

  useEffect(() => {
    if (!flagsDirty) {
      setLocalFlags({ ...flags });
    }
  }, [flags, flagsDirty]);

  const handleSaveFlags = useCallback(() => {
    try {
      localStorage.setItem(FEATURE_FLAGS_STORAGE_KEY, JSON.stringify(localFlags));
    } catch {
      // Ignore storage errors
    }
    setFlagsDirty(false);
    setFlagsSavedMsg("Flags saved — reload the page to apply changes.");
  }, [localFlags]);

  const handleResetFlags = useCallback(() => {
    setLocalFlags({ ...flags });
    setFlagsDirty(false);
    setFlagsSavedMsg(null);
  }, [flags]);

  // Access denied guard
  if (!isAdmin) {
    return (
      <div data-testid="admin-dashboard-page" className={styles.page}>
        <GlassPanel className={styles.contentPanel}>
          <div data-testid="admin-access-denied" className={styles.accessDenied}>
            <h2 className={styles.accessDeniedTitle}>Access Denied</h2>
            <p className={styles.accessDeniedDesc}>
              The Admin Dashboard requires the ADMIN role.
            </p>
          </div>
        </GlassPanel>
      </div>
    );
  }

  const combinedError = errorPolicy ?? errorAdoption ?? errorAiUsage ?? errorRcdoHealth;

  return (
    <div data-testid="admin-dashboard-page" className={styles.page}>
      <GlassPanel className={styles.contentPanel}>
        <div className={styles.header}>
          <div>
            <h2 className={styles.heading}>Admin Dashboard</h2>
            <p className={styles.subheading}>
              Organisation-level analytics and configuration
            </p>
          </div>
        </div>

        <ErrorBanner message={combinedError} onDismiss={clearErrors} />

        {/* Tab bar */}
        <nav
          data-testid="admin-tab-bar"
          className={styles.tabBar}
          aria-label="Admin dashboard tabs"
          role="tablist"
        >
          {TABS.map(({ id, label }) => (
            <button
              key={id}
              id={`admin-tab-${id}`}
              type="button"
              data-testid={`admin-tab-${id}`}
              role="tab"
              aria-selected={activeTab === id}
              aria-controls={`admin-panel-${id}`}
              className={`${styles.tab} ${activeTab === id ? styles.tabActive : ""}`}
              onClick={() => {
                setActiveTab(id);
              }}
            >
              {label}
            </button>
          ))}
        </nav>

        {/* Tab panels */}
        <div
          id={`admin-panel-${activeTab}`}
          data-testid="admin-tab-panel"
          className={styles.tabPanel}
          role="tabpanel"
          aria-labelledby={`admin-tab-${activeTab}`}
        >
          {activeTab === "adoption" && (
            <AdoptionFunnelPanel
              adoptionMetrics={adoptionMetrics}
              loading={loadingAdoption}
              error={errorAdoption}
              onFetch={handleAdoptionFetch}
              selectedWeeks={adoptionWeeks}
            />
          )}

          {activeTab === "cadence" && (
            <CadenceConfigPanel
              orgPolicy={orgPolicy}
              loading={loadingPolicy}
              error={errorPolicy}
              onSaveDigest={updateDigestConfig}
              onReload={fetchOrgPolicy}
            />
          )}

          {activeTab === "chess" && (
            <ChessRulePanel
              orgPolicy={orgPolicy}
              loading={loadingPolicy}
              error={errorPolicy}
            />
          )}

          {activeTab === "rcdo-health" && (
            <RcdoHealthPanel
              rcdoHealthReport={rcdoHealthReport}
              loading={loadingRcdoHealth}
              error={errorRcdoHealth}
              onFetch={fetchRcdoHealth}
            />
          )}

          {activeTab === "ai-usage" && (
            <AiUsagePanel
              aiUsageMetrics={aiUsageMetrics}
              loading={loadingAiUsage}
              error={errorAiUsage}
              onFetch={handleAiUsageFetch}
              selectedWeeks={aiUsageWeeks}
            />
          )}

          {activeTab === "feature-flags" && (
            <FeatureFlagPanel
              localFlags={localFlags}
              onToggle={handleToggleFlag}
              onSave={handleSaveFlags}
              onReset={handleResetFlags}
              isDirty={flagsDirty}
              savedMsg={flagsSavedMsg}
            />
          )}

          {activeTab === "outcome-targets" && (
            <div data-testid="outcome-targets-panel">
              <h3 className={styles.sectionTitle}>Outcome Targets</h3>
              <p className={styles.sectionDescription}>
                Configure target dates and progress tracking for RCDO outcomes. Requires the{" "}
                <strong>Outcome Urgency</strong> feature flag to be enabled.
              </p>
              {flags.outcomeUrgency ? (
                <OutcomeMetadataEditor
                  outcomes={rcdoOutcomes}
                  metadata={outcomeMetadata}
                  onSave={handleSaveOutcomeMetadata}
                  onUpdateProgress={handleUpdateOutcomeProgress}
                  loading={metaLoading}
                />
              ) : (
                <p className={styles.emptyState}>
                  Enable the <strong>Outcome Urgency</strong> feature flag (via the{" "}
                  <em>Feature Flags</em> tab) to configure outcome targets.
                </p>
              )}
            </div>
          )}
        </div>
      </GlassPanel>
    </div>
  );
};
