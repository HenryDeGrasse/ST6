import { describe, it, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AdminDashboardPage } from "../pages/AdminDashboardPage.js";
import { AuthProvider, type AuthUser } from "../context/AuthContext.js";
import { ApiProvider } from "../api/ApiContext.js";
import { FeatureFlagProvider, type FeatureFlags } from "../context/FeatureFlagContext.js";

/* ── Mock API client ──────────────────────────────────────────────────────── */

const mockClient = {
  GET: vi.fn(),
  POST: vi.fn(),
  PATCH: vi.fn(),
  DELETE: vi.fn(),
  use: vi.fn(),
};

vi.mock("@weekly-commitments/contracts", async () => {
  const actual = await vi.importActual("@weekly-commitments/contracts");
  return {
    ...actual,
    createWeeklyCommitmentsClient: () => mockClient,
  };
});

/* ── Fixtures ─────────────────────────────────────────────────────────────── */

const ADMIN_USER: AuthUser = {
  userId: "admin-1",
  orgId: "org-1",
  displayName: "Alice Admin",
  roles: ["ADMIN"],
  timezone: "America/Chicago",
};

const IC_USER: AuthUser = {
  userId: "ic-1",
  orgId: "org-1",
  displayName: "Bob IC",
  roles: ["IC"],
  timezone: "America/Chicago",
};

const ORG_POLICY = {
  chessKingRequired: true,
  chessMaxKing: 1,
  chessMaxQueen: 2,
  lockDay: "MONDAY",
  lockTime: "10:00",
  reconcileDay: "FRIDAY",
  reconcileTime: "16:00",
  blockLockOnStaleRcdo: true,
  rcdoStalenessThresholdMinutes: 60,
  digestDay: "FRIDAY",
  digestTime: "17:00",
};

const ADOPTION_METRICS = {
  weeks: 8,
  windowStart: "2026-01-26",
  windowEnd: "2026-03-16",
  totalActiveUsers: 15,
  cadenceComplianceRate: 0.85,
  weeklyPoints: [
    {
      weekStart: "2026-03-09",
      activeUsers: 12,
      plansCreated: 12,
      plansLocked: 10,
      plansReconciled: 8,
      plansReviewed: 7,
    },
  ],
};

const AI_USAGE_METRICS = {
  weeks: 8,
  windowStart: "2026-01-26",
  windowEnd: "2026-03-16",
  totalFeedbackCount: 50,
  acceptedCount: 30,
  deferredCount: 12,
  declinedCount: 8,
  acceptanceRate: 0.6,
  cacheHits: 300,
  cacheMisses: 100,
  cacheHitRate: 0.75,
  approximateTokensSpent: 100000,
  approximateTokensSaved: 300000,
};

const RCDO_HEALTH = {
  generatedAt: "2026-03-19T12:00:00Z",
  windowWeeks: 8,
  totalOutcomes: 10,
  coveredOutcomes: 7,
  topOutcomes: [
    {
      outcomeId: "o1",
      outcomeName: "Increase Revenue",
      objectiveId: "obj1",
      objectiveName: "Grow Market",
      rallyCryId: "rc1",
      rallyCryName: "Dominate Q1",
      commitCount: 12,
    },
  ],
  staleOutcomes: [
    {
      outcomeId: "o2",
      outcomeName: "Reduce Churn",
      objectiveId: "obj2",
      objectiveName: "Retain Customers",
      rallyCryId: "rc1",
      rallyCryName: "Dominate Q1",
      commitCount: 0,
    },
  ],
};

/* ── Render helper ────────────────────────────────────────────────────────── */

function renderPage(user: AuthUser = ADMIN_USER, flags?: Partial<FeatureFlags>) {
  return render(
    <AuthProvider user={user} token="dev-admin-token">
      <ApiProvider baseUrl="https://example.test/api/v1">
        <FeatureFlagProvider flags={flags}>
          <AdminDashboardPage />
        </FeatureFlagProvider>
      </ApiProvider>
    </AuthProvider>,
  );
}

/* ── Tests ────────────────────────────────────────────────────────────────── */

const pendingRequest = () => new Promise<never>(() => {});

describe("AdminDashboardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default to a pending request so simple render tests do not finish with
    // an unresolved post-render state update still in flight.
    mockClient.GET.mockImplementation(pendingRequest);
  });

  // ── Access control ─────────────────────────────────────────────────────────

  it("renders the page for admin users", () => {
    renderPage(ADMIN_USER);
    expect(screen.getByTestId("admin-dashboard-page")).toBeInTheDocument();
    expect(screen.getByText("Admin Dashboard")).toBeInTheDocument();
  });

  it("shows access denied for non-admin users", () => {
    renderPage(IC_USER);
    expect(screen.getByTestId("admin-access-denied")).toBeInTheDocument();
    expect(screen.getByText("Access Denied")).toBeInTheDocument();
    expect(mockClient.GET).not.toHaveBeenCalled();
  });

  // ── Tab navigation ──────────────────────────────────────────────────────────

  it("renders all seven tabs", () => {
    renderPage();
    expect(screen.getByTestId("admin-tab-adoption")).toBeInTheDocument();
    expect(screen.getByTestId("admin-tab-cadence")).toBeInTheDocument();
    expect(screen.getByTestId("admin-tab-chess")).toBeInTheDocument();
    expect(screen.getByTestId("admin-tab-rcdo-health")).toBeInTheDocument();
    expect(screen.getByTestId("admin-tab-ai-usage")).toBeInTheDocument();
    expect(screen.getByTestId("admin-tab-feature-flags")).toBeInTheDocument();
    expect(screen.getByTestId("admin-tab-outcome-targets")).toBeInTheDocument();
  });

  it("defaults to the Adoption Funnel tab", () => {
    renderPage();
    expect(screen.getByTestId("admin-tab-adoption")).toHaveAttribute("aria-selected", "true");
    expect(screen.getByTestId("adoption-funnel-panel")).toBeInTheDocument();
  });

  it("switches to Cadence Config tab on click", async () => {
    mockClient.GET.mockResolvedValue({ data: ORG_POLICY, response: { status: 200 } });
    renderPage();

    fireEvent.click(screen.getByTestId("admin-tab-cadence"));

    expect(screen.getByTestId("admin-tab-cadence")).toHaveAttribute("aria-selected", "true");
    expect(await screen.findByTestId("cadence-config-panel")).toBeInTheDocument();
  });

  it("switches to Chess Rules tab on click", async () => {
    mockClient.GET.mockResolvedValue({ data: ORG_POLICY, response: { status: 200 } });
    renderPage();

    fireEvent.click(screen.getByTestId("admin-tab-chess"));

    expect(await screen.findByTestId("chess-rule-panel")).toBeInTheDocument();
  });

  it("switches to RCDO Health tab on click", async () => {
    mockClient.GET.mockResolvedValue({ data: RCDO_HEALTH, response: { status: 200 } });
    renderPage();

    fireEvent.click(screen.getByTestId("admin-tab-rcdo-health"));

    expect(await screen.findByTestId("rcdo-health-panel")).toBeInTheDocument();
  });

  it("switches to AI Usage tab on click", async () => {
    mockClient.GET.mockResolvedValue({ data: AI_USAGE_METRICS, response: { status: 200 } });
    renderPage();

    fireEvent.click(screen.getByTestId("admin-tab-ai-usage"));

    expect(await screen.findByTestId("ai-usage-panel")).toBeInTheDocument();
  });

  it("switches to Feature Flags tab on click", () => {
    renderPage();

    fireEvent.click(screen.getByTestId("admin-tab-feature-flags"));

    expect(screen.getByTestId("feature-flag-panel")).toBeInTheDocument();
  });

  it("shows the outcome targets guidance when the feature flag is disabled", () => {
    renderPage();

    fireEvent.click(screen.getByTestId("admin-tab-outcome-targets"));

    expect(screen.getByTestId("outcome-targets-panel")).toBeInTheDocument();
    expect(screen.getByTestId("outcome-targets-panel")).toHaveTextContent(
      "Enable the Outcome Urgency feature flag",
    );
  });

  it("renders the outcome targets editor when the feature flag is enabled", async () => {
    mockClient.GET
      .mockResolvedValueOnce({ data: ADOPTION_METRICS, response: { status: 200 } })
      .mockResolvedValueOnce({ data: [], response: { status: 200 } })
      .mockResolvedValueOnce({ data: { rallyCries: [] }, response: { status: 200 } });

    renderPage(ADMIN_USER, { outcomeUrgency: true });

    fireEvent.click(screen.getByTestId("admin-tab-outcome-targets"));

    expect(await screen.findByTestId("outcome-metadata-editor")).toBeInTheDocument();
    await waitFor(() => {
      expect(mockClient.GET).toHaveBeenCalledWith("/outcomes/metadata");
      expect(mockClient.GET).toHaveBeenCalledWith("/rcdo/tree");
    });
  });

  // ── Adoption Funnel ──────────────────────────────────────────────────────────

  it("fetches adoption metrics on mount", async () => {
    mockClient.GET.mockResolvedValue({ data: ADOPTION_METRICS, response: { status: 200 } });
    renderPage();

    await waitFor(() => {
      expect(mockClient.GET).toHaveBeenCalledWith(
        "/admin/adoption-metrics",
        expect.objectContaining({ params: { query: { weeks: 8 } } }),
      );
    });
    expect(await screen.findByTestId("adoption-metrics")).toBeInTheDocument();
  });

  it("renders adoption metrics when data is loaded", async () => {
    mockClient.GET.mockResolvedValue({ data: ADOPTION_METRICS, response: { status: 200 } });
    renderPage();

    expect(await screen.findByTestId("adoption-metrics")).toBeInTheDocument();
    expect(screen.getByTestId("metric-active-users")).toHaveTextContent("15");
    expect(screen.getByTestId("metric-cadence-compliance")).toHaveTextContent("85%");
  });

  it("renders adoption table rows", async () => {
    mockClient.GET.mockResolvedValue({ data: ADOPTION_METRICS, response: { status: 200 } });
    renderPage();

    expect(await screen.findByTestId("adoption-table")).toBeInTheDocument();
    expect(screen.getByTestId("adoption-row-2026-03-09")).toBeInTheDocument();
  });

  it("re-fetches adoption metrics when window button is clicked", async () => {
    mockClient.GET.mockResolvedValue({ data: ADOPTION_METRICS, response: { status: 200 } });
    renderPage();

    await screen.findByTestId("adoption-metrics");
    vi.clearAllMocks();
    mockClient.GET.mockResolvedValue({ data: ADOPTION_METRICS, response: { status: 200 } });

    fireEvent.click(screen.getByTestId("window-btn-12"));

    await waitFor(() => {
      expect(mockClient.GET).toHaveBeenCalledWith(
        "/admin/adoption-metrics",
        expect.objectContaining({ params: { query: { weeks: 12 } } }),
      );
    });
  });

  it("shows empty state when no weekly points", async () => {
    mockClient.GET.mockResolvedValue({
      data: { ...ADOPTION_METRICS, weeklyPoints: [] },
      response: { status: 200 },
    });
    renderPage();

    expect(await screen.findByTestId("adoption-empty")).toBeInTheDocument();
  });

  // ── Cadence Config ──────────────────────────────────────────────────────────

  it("renders cadence fields from org policy", async () => {
    mockClient.GET.mockResolvedValue({ data: ORG_POLICY, response: { status: 200 } });
    renderPage();

    fireEvent.click(screen.getByTestId("admin-tab-cadence"));

    await waitFor(() => {
      expect(screen.getByTestId("lock-day")).toHaveTextContent("Monday");
    });
    expect(screen.getByTestId("lock-time")).toHaveTextContent("10:00");
    expect(screen.getByTestId("reconcile-day")).toHaveTextContent("Friday");
    expect(screen.getByTestId("reconcile-time")).toHaveTextContent("16:00");
  });

  it("save button is disabled when digest config is unchanged", async () => {
    mockClient.GET.mockResolvedValue({ data: ORG_POLICY, response: { status: 200 } });
    renderPage();

    fireEvent.click(screen.getByTestId("admin-tab-cadence"));

    await waitFor(() => {
      expect(screen.getByTestId("save-cadence-btn")).toBeDisabled();
    });
  });

  it("enables save button after editing digest day", async () => {
    mockClient.GET.mockResolvedValue({ data: ORG_POLICY, response: { status: 200 } });
    renderPage();

    fireEvent.click(screen.getByTestId("admin-tab-cadence"));

    await waitFor(() => {
      expect(screen.getByTestId("cadence-digest-day")).toBeInTheDocument();
    });

    fireEvent.change(screen.getByTestId("cadence-digest-day"), {
      target: { value: "MONDAY" },
    });

    expect(screen.getByTestId("save-cadence-btn")).toBeEnabled();
  });

  it("calls PATCH on save digest", async () => {
    const updatedPolicy = { ...ORG_POLICY, digestDay: "MONDAY", digestTime: "09:00" };
    mockClient.GET.mockResolvedValue({ data: ORG_POLICY, response: { status: 200 } });
    mockClient.PATCH.mockResolvedValue({ data: updatedPolicy, response: { status: 200 } });

    renderPage();
    fireEvent.click(screen.getByTestId("admin-tab-cadence"));

    await waitFor(() => {
      expect(screen.getByTestId("cadence-digest-day")).toBeInTheDocument();
    });

    fireEvent.change(screen.getByTestId("cadence-digest-day"), {
      target: { value: "MONDAY" },
    });
    fireEvent.change(screen.getByTestId("cadence-digest-time"), {
      target: { value: "09:00" },
    });

    fireEvent.click(screen.getByTestId("save-cadence-btn"));

    await waitFor(() => {
      expect(mockClient.PATCH).toHaveBeenCalledWith(
        "/admin/org-policy/digest",
        expect.objectContaining({
          body: { digestDay: "MONDAY", digestTime: "09:00" },
        }),
      );
    });

    await screen.findByTestId("cadence-success");
    expect(screen.getByTestId("cadence-success")).toHaveTextContent("Digest schedule saved.");
  });

  // ── Chess Rule Panel ──────────────────────────────────────────────────────────

  it("renders chess rule fields from org policy", async () => {
    mockClient.GET.mockResolvedValue({ data: ORG_POLICY, response: { status: 200 } });
    renderPage();

    fireEvent.click(screen.getByTestId("admin-tab-chess"));

    await waitFor(() => {
      expect(screen.getByTestId("chess-king-required")).toHaveTextContent("Yes");
    });
    expect(screen.getByTestId("chess-max-king")).toHaveTextContent("1");
    expect(screen.getByTestId("chess-max-queen")).toHaveTextContent("2");
    expect(screen.getByTestId("chess-block-stale-rcdo")).toHaveTextContent("Yes");
  });

  it("shows chess loading state while fetching", () => {
    // Never resolve
    mockClient.GET.mockImplementation(() => new Promise(() => {}));
    renderPage();

    fireEvent.click(screen.getByTestId("admin-tab-chess"));

    expect(screen.getByTestId("chess-loading")).toBeInTheDocument();
  });

  // ── RCDO Health ──────────────────────────────────────────────────────────────

  it("renders RCDO health metrics", async () => {
    mockClient.GET
      .mockResolvedValueOnce({ data: ADOPTION_METRICS, response: { status: 200 } }) // initial fetch
      .mockResolvedValue({ data: RCDO_HEALTH, response: { status: 200 } }); // rcdo fetch

    renderPage();
    fireEvent.click(screen.getByTestId("admin-tab-rcdo-health"));

    await waitFor(() => {
      expect(screen.getByTestId("rcdo-metrics")).toBeInTheDocument();
    });
    expect(screen.getByTestId("metric-total-outcomes")).toHaveTextContent("10");
    expect(screen.getByTestId("metric-covered-outcomes")).toHaveTextContent("7");
    expect(screen.getByTestId("metric-stale-outcomes")).toHaveTextContent("1");
  });

  it("renders covered and stale outcome rows in RCDO table", async () => {
    mockClient.GET
      .mockResolvedValueOnce({ data: ADOPTION_METRICS, response: { status: 200 } })
      .mockResolvedValue({ data: RCDO_HEALTH, response: { status: 200 } });

    renderPage();
    fireEvent.click(screen.getByTestId("admin-tab-rcdo-health"));

    await waitFor(() => {
      expect(screen.getByTestId("rcdo-row-o1")).toBeInTheDocument();
    });
    expect(screen.getByTestId("rcdo-stale-row-o2")).toBeInTheDocument();
    expect(screen.getAllByText("Stale").length).toBeGreaterThan(0);
  });

  it("shows empty state when no outcomes in RCDO report", async () => {
    const emptyReport = {
      ...RCDO_HEALTH,
      topOutcomes: [],
      staleOutcomes: [],
    };
    mockClient.GET
      .mockResolvedValueOnce({ data: ADOPTION_METRICS, response: { status: 200 } })
      .mockResolvedValue({ data: emptyReport, response: { status: 200 } });

    renderPage();
    fireEvent.click(screen.getByTestId("admin-tab-rcdo-health"));

    expect(await screen.findByTestId("rcdo-empty")).toBeInTheDocument();
  });

  // ── AI Usage ────────────────────────────────────────────────────────────────

  it("renders AI usage metrics", async () => {
    mockClient.GET
      .mockResolvedValueOnce({ data: ADOPTION_METRICS, response: { status: 200 } })
      .mockResolvedValue({ data: AI_USAGE_METRICS, response: { status: 200 } });

    renderPage();
    fireEvent.click(screen.getByTestId("admin-tab-ai-usage"));

    await waitFor(() => {
      expect(screen.getByTestId("ai-usage-metrics")).toBeInTheDocument();
    });
    expect(screen.getByTestId("metric-acceptance-rate")).toHaveTextContent("60%");
    expect(screen.getByTestId("metric-cache-hit-rate")).toHaveTextContent("75%");
  });

  it("renders AI feedback breakdown", async () => {
    mockClient.GET
      .mockResolvedValueOnce({ data: ADOPTION_METRICS, response: { status: 200 } })
      .mockResolvedValue({ data: AI_USAGE_METRICS, response: { status: 200 } });

    renderPage();
    fireEvent.click(screen.getByTestId("admin-tab-ai-usage"));

    await waitFor(() => {
      expect(screen.getByTestId("ai-accepted-count")).toHaveTextContent("30");
    });
    expect(screen.getByTestId("ai-deferred-count")).toHaveTextContent("12");
    expect(screen.getByTestId("ai-declined-count")).toHaveTextContent("8");
  });

  it("re-fetches AI usage with new window on button click", async () => {
    mockClient.GET
      .mockResolvedValueOnce({ data: ADOPTION_METRICS, response: { status: 200 } })
      .mockResolvedValue({ data: AI_USAGE_METRICS, response: { status: 200 } });

    renderPage();
    fireEvent.click(screen.getByTestId("admin-tab-ai-usage"));

    await screen.findByTestId("ai-usage-metrics");
    vi.clearAllMocks();
    mockClient.GET.mockResolvedValue({ data: AI_USAGE_METRICS, response: { status: 200 } });

    fireEvent.click(screen.getByTestId("ai-window-btn-4"));

    await waitFor(() => {
      expect(mockClient.GET).toHaveBeenCalledWith(
        "/admin/ai-usage",
        expect.objectContaining({ params: { query: { weeks: 4 } } }),
      );
    });
  });

  // ── Feature Flags ────────────────────────────────────────────────────────────

  it("renders all feature flag toggles", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("admin-tab-feature-flags"));

    expect(screen.getByTestId("flag-list")).toBeInTheDocument();
    expect(screen.getByTestId("flag-row-suggestRcdo")).toBeInTheDocument();
    expect(screen.getByTestId("flag-row-draftReconciliation")).toBeInTheDocument();
    expect(screen.getByTestId("flag-row-managerInsights")).toBeInTheDocument();
    expect(screen.getByTestId("flag-row-icTrends")).toBeInTheDocument();
    expect(screen.getByTestId("flag-row-planQualityNudge")).toBeInTheDocument();
    expect(screen.getByTestId("flag-row-startMyWeek")).toBeInTheDocument();
    expect(screen.getByTestId("flag-row-suggestNextWork")).toBeInTheDocument();
    expect(screen.getByTestId("flag-row-dailyCheckIn")).toBeInTheDocument();
    expect(screen.getByTestId("flag-row-outcomeUrgency")).toBeInTheDocument();
    expect(screen.getByTestId("flag-row-strategicSlack")).toBeInTheDocument();
  });

  it("save and reset buttons are disabled when flags are clean", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("admin-tab-feature-flags"));

    expect(screen.getByTestId("save-flags-btn")).toBeDisabled();
    expect(screen.getByTestId("reset-flags-btn")).toBeDisabled();
  });

  it("enables save/reset after toggling a flag", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("admin-tab-feature-flags"));

    fireEvent.click(screen.getByTestId("flag-toggle-suggestRcdo"));

    expect(screen.getByTestId("save-flags-btn")).toBeEnabled();
    expect(screen.getByTestId("reset-flags-btn")).toBeEnabled();
  });

  it("resets dirty flags on reset click", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("admin-tab-feature-flags"));

    const toggle = screen.getByTestId("flag-toggle-suggestRcdo");
    const wasChecked = (toggle as HTMLInputElement).checked;

    fireEvent.click(toggle);

    expect((screen.getByTestId("flag-toggle-suggestRcdo") as HTMLInputElement).checked).toBe(!wasChecked);

    fireEvent.click(screen.getByTestId("reset-flags-btn"));

    expect((screen.getByTestId("flag-toggle-suggestRcdo") as HTMLInputElement).checked).toBe(wasChecked);
    expect(screen.getByTestId("save-flags-btn")).toBeDisabled();
  });

  it("shows save confirmation after saving flags", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("admin-tab-feature-flags"));

    fireEvent.click(screen.getByTestId("flag-toggle-dailyCheckIn"));
    fireEvent.click(screen.getByTestId("save-flags-btn"));

    expect(screen.getByTestId("flags-saved-msg")).toBeInTheDocument();
    expect(screen.getByTestId("flags-saved-msg")).toHaveTextContent("Flags saved");
  });

  // ── Error handling ──────────────────────────────────────────────────────────

  it("shows error banner when adoption metrics fetch fails", async () => {
    mockClient.GET.mockResolvedValue({
      data: undefined,
      error: { error: { message: "Server unavailable" } },
      response: { status: 503 },
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId("adoption-error")).toBeInTheDocument();
    });
    expect(screen.getByTestId("adoption-error")).toHaveTextContent("Server unavailable");
  });

  it("shows loading indicator while fetching adoption metrics", () => {
    mockClient.GET.mockImplementation(() => new Promise(() => {})); // never resolves

    renderPage();

    expect(screen.getByTestId("adoption-loading")).toBeInTheDocument();
  });
});
