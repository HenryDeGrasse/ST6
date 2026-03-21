import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { RcdoRollupPanel } from "../components/RcdoRollupPanel.js";
import { AuthProvider, type AuthUser } from "../context/AuthContext.js";
import { ApiProvider } from "../api/ApiContext.js";
import { FeatureFlagProvider, type FeatureFlags } from "../context/FeatureFlagContext.js";
import type { RcdoRollupResponse } from "@weekly-commitments/contracts";

const mockClient = {
  GET: vi.fn(),
  POST: vi.fn(),
  PUT: vi.fn(),
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

const DEFAULT_USER: AuthUser = {
  userId: "manager-1",
  orgId: "org-1",
  displayName: "Mina Manager",
  roles: ["MANAGER"],
  timezone: "America/Chicago",
};

function renderPanel(rollup: RcdoRollupResponse | null, loading: boolean, flags?: Partial<FeatureFlags>) {
  return render(
    <AuthProvider user={DEFAULT_USER} token="dev-manager-token">
      <ApiProvider baseUrl="https://example.test/api/v1">
        <FeatureFlagProvider flags={flags}>
          <RcdoRollupPanel rollup={rollup} loading={loading} />
        </FeatureFlagProvider>
      </ApiProvider>
    </AuthProvider>,
  );
}

describe("RcdoRollupPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockClient.GET.mockResolvedValue({ data: { outcomes: [] }, response: { status: 200 } });
  });

  it("renders nothing when loading", () => {
    const { container } = renderPanel(null, true);
    expect(container.firstChild).toBeNull();
    expect(mockClient.GET).not.toHaveBeenCalled();
  });

  it("renders empty state when no items", () => {
    const rollup: RcdoRollupResponse = {
      weekStart: "2026-03-09",
      items: [],
      nonStrategicCount: 0,
    };
    renderPanel(rollup, false);
    expect(screen.getByTestId("rollup-empty")).toBeInTheDocument();
    expect(mockClient.GET).not.toHaveBeenCalled();
  });

  it("renders non-strategic count", () => {
    const rollup: RcdoRollupResponse = {
      weekStart: "2026-03-09",
      items: [],
      nonStrategicCount: 3,
    };
    renderPanel(rollup, false);
    expect(screen.getByTestId("non-strategic-count")).toBeInTheDocument();
    expect(screen.getByText(/3 non-strategic/)).toBeInTheDocument();
  });

  it("renders rollup items in a table", () => {
    const rollup: RcdoRollupResponse = {
      weekStart: "2026-03-09",
      items: [
        {
          outcomeId: "oc-1",
          outcomeName: "Revenue Growth",
          objectiveId: "obj-1",
          objectiveName: "Scale ARR",
          rallyCryId: "rc-1",
          rallyCryName: "Win Market",
          commitCount: 5,
          kingCount: 1,
          queenCount: 2,
          rookCount: 1,
          bishopCount: 0,
          knightCount: 0,
          pawnCount: 1,
        },
      ],
      nonStrategicCount: 0,
    };
    renderPanel(rollup, false);
    expect(screen.getByTestId("rollup-table")).toBeInTheDocument();
    expect(screen.getByTestId("rollup-row-oc-1")).toBeInTheDocument();
    expect(screen.getByText("Win Market")).toBeInTheDocument();
    expect(screen.getByText("Scale ARR")).toBeInTheDocument();
    expect(screen.getByText("Revenue Growth")).toBeInTheDocument();
  });

  it("fetches urgency summary and shows a badge when outcome urgency is enabled", async () => {
    mockClient.GET.mockResolvedValue({
      data: {
        outcomes: [
          {
            outcomeId: "oc-1",
            outcomeName: "Revenue Growth",
            targetDate: "2026-06-30",
            progressPct: 55,
            expectedProgressPct: 60,
            urgencyBand: "AT_RISK",
            daysRemaining: 42,
          },
        ],
      },
      response: { status: 200 },
    });

    const rollup: RcdoRollupResponse = {
      weekStart: "2026-03-09",
      items: [
        {
          outcomeId: "oc-1",
          outcomeName: "Revenue Growth",
          objectiveId: "obj-1",
          objectiveName: "Scale ARR",
          rallyCryId: "rc-1",
          rallyCryName: "Win Market",
          commitCount: 5,
          kingCount: 1,
          queenCount: 2,
          rookCount: 1,
          bishopCount: 0,
          knightCount: 0,
          pawnCount: 1,
        },
      ],
      nonStrategicCount: 0,
    };

    renderPanel(rollup, false, { outcomeUrgency: true });

    await waitFor(() => {
      expect(mockClient.GET).toHaveBeenCalledWith("/outcomes/urgency-summary");
    });

    expect(await screen.findByTestId("urgency-badge")).toHaveAttribute("data-urgency-band", "AT_RISK");
    expect(screen.getByLabelText("Urgency: At Risk")).toBeInTheDocument();
  });
});
