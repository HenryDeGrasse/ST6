import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { TeamCapacityPanel } from "../components/CapacityView/TeamCapacityPanel.js";
import type { TeamCapacityResponse, TeamMemberCapacity } from "../hooks/useCapacity.js";

// ─── Mock hooks ───────────────────────────────────────────────────────────────

// Mock the entire useCapacity module so we can control the hook return value.
const mockFetchTeamCapacity = vi.fn();

const mockUseTeamCapacity = vi.fn(() => ({
  teamCapacity: null as TeamCapacityResponse | null,
  loading: false,
  error: null as string | null,
  fetchTeamCapacity: mockFetchTeamCapacity,
  clearError: vi.fn(),
}));

vi.mock("../hooks/useCapacity.js", () => ({
  useTeamCapacity: () => mockUseTeamCapacity(),
}));

// Mock FeatureFlagContext so the capacityTracking flag can be toggled.
let mockCapacityTracking = true;

vi.mock("../context/FeatureFlagContext.js", () => ({
  useFeatureFlags: () => ({
    suggestRcdo: true,
    draftReconciliation: false,
    managerInsights: false,
    icTrends: true,
    planQualityNudge: false,
    startMyWeek: false,
    suggestNextWork: false,
    dailyCheckIn: false,
    quickUpdate: false,
    userProfile: false,
    capacityTracking: mockCapacityTracking,
    estimationCoaching: false,
    strategicIntelligence: false,
    predictions: false,
    outcomeUrgency: false,
    strategicSlack: false,
  }),
}));

// ─── Fixtures ─────────────────────────────────────────────────────────────────

function makeMember(overrides: Partial<TeamMemberCapacity> = {}): TeamMemberCapacity {
  return {
    userId: "user-1",
    name: "Alice",
    estimatedHours: 30,
    adjustedEstimate: 28,
    realisticCap: 35,
    overcommitLevel: "NONE",
    ...overrides,
  };
}

function makeTeamCapacity(members: TeamMemberCapacity[]): TeamCapacityResponse {
  return {
    weekStart: "2026-03-16",
    members,
  };
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("TeamCapacityPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCapacityTracking = true;
    mockFetchTeamCapacity.mockResolvedValue(undefined);
    // Reset the hook mock to its default idle state.
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: null,
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
  });

  // ── Feature flag gate ──────────────────────────────────────────────────────

  it("renders nothing when capacityTracking flag is off", () => {
    mockCapacityTracking = false;
    const { container } = render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(container.firstChild).toBeNull();
  });

  // ── Loading / error / null states ─────────────────────────────────────────

  it("shows loading state while fetching", () => {
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: null,
      loading: true,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(screen.getByTestId("team-capacity-panel")).toBeInTheDocument();
    expect(screen.getByTestId("team-capacity-loading")).toBeInTheDocument();
    expect(screen.getByText(/Loading team capacity/)).toBeInTheDocument();
  });

  it("shows error state when error is set", () => {
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: null,
      loading: false,
      error: "Failed to load team capacity",
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(screen.getByTestId("team-capacity-panel")).toBeInTheDocument();
    expect(screen.getByTestId("team-capacity-error")).toBeInTheDocument();
    expect(screen.getByText("Failed to load team capacity")).toBeInTheDocument();
  });

  it("renders nothing when teamCapacity is null and not loading", () => {
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: null,
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    const { container } = render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(container.firstChild).toBeNull();
  });

  // ── Team member rows ──────────────────────────────────────────────────────

  it("renders table with team member rows", () => {
    const members = [
      makeMember({ userId: "u1", name: "Alice", estimatedHours: 30, adjustedEstimate: 28, realisticCap: 35 }),
      makeMember({ userId: "u2", name: "Bob", estimatedHours: 40, adjustedEstimate: 38, realisticCap: 35 }),
    ];
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity(members),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);

    expect(screen.getByTestId("team-capacity-panel")).toBeInTheDocument();
    expect(screen.getByTestId("team-capacity-row-0")).toBeInTheDocument();
    expect(screen.getByTestId("team-capacity-row-1")).toBeInTheDocument();
    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
  });

  it("renders member hours in each row", () => {
    const members = [
      makeMember({ userId: "u1", name: "Alice", estimatedHours: 30, adjustedEstimate: 28, realisticCap: 35 }),
    ];
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity(members),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);

    const row = screen.getByTestId("team-capacity-row-0");
    expect(row).toHaveTextContent("30h");
    expect(row).toHaveTextContent("28h");
    expect(row).toHaveTextContent("35h");
  });

  it("falls back to userId when name is null", () => {
    const members = [
      makeMember({ userId: "user-xyz", name: null }),
    ];
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity(members),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(screen.getByText("user-xyz")).toBeInTheDocument();
  });

  it("shows '–' when realisticCap is null", () => {
    const members = [
      makeMember({ userId: "u1", name: "Alice", realisticCap: null }),
    ];
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity(members),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    const row = screen.getByTestId("team-capacity-row-0");
    expect(row).toHaveTextContent("–");
  });

  // ── Status icons / labels per overcommit level ────────────────────────────

  it("shows correct status icon for NONE overcommit level", () => {
    const members = [
      makeMember({ userId: "u1", name: "Alice", overcommitLevel: "NONE" }),
    ];
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity(members),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(screen.getByTestId("team-capacity-status-0")).toHaveTextContent("✅ OK");
  });

  it("shows correct status icon for MODERATE overcommit level", () => {
    const members = [
      makeMember({ userId: "u1", name: "Alice", overcommitLevel: "MODERATE" }),
    ];
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity(members),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(screen.getByTestId("team-capacity-status-0")).toHaveTextContent("⚠️ MODERATE");
  });

  it("shows correct status icon for HIGH overcommit level", () => {
    const members = [
      makeMember({ userId: "u1", name: "Alice", overcommitLevel: "HIGH" }),
    ];
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity(members),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(screen.getByTestId("team-capacity-status-0")).toHaveTextContent("⛔ HIGH");
  });

  // ── Team totals row ───────────────────────────────────────────────────────

  it("shows team totals row with summed hours", () => {
    const members = [
      makeMember({ userId: "u1", name: "Alice", estimatedHours: 30, adjustedEstimate: 28, realisticCap: 35 }),
      makeMember({ userId: "u2", name: "Bob", estimatedHours: 40, adjustedEstimate: 36, realisticCap: 35 }),
    ];
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity(members),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);

    const totals = screen.getByTestId("team-capacity-totals");
    expect(totals).toBeInTheDocument();
    // estimated: 30 + 40 = 70h
    expect(totals).toHaveTextContent("70h");
    // adjusted: 28 + 36 = 64h
    expect(totals).toHaveTextContent("64h");
    // realisticCap: 35 + 35 = 70h
    expect(totals).toHaveTextContent("70h");
  });

  it("shows 'Team Total' label in the totals row", () => {
    const members = [
      makeMember({ userId: "u1", name: "Alice" }),
    ];
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity(members),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(screen.getByTestId("team-capacity-totals")).toHaveTextContent("Team Total");
  });

  it("shows HIGH status in totals row when any member is HIGH", () => {
    const members = [
      makeMember({ userId: "u1", name: "Alice", overcommitLevel: "NONE" }),
      makeMember({ userId: "u2", name: "Bob", overcommitLevel: "HIGH" }),
    ];
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity(members),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(screen.getByTestId("team-capacity-totals-status")).toHaveTextContent("⛔ HIGH");
  });

  it("shows MODERATE status in totals row when worst level is MODERATE", () => {
    const members = [
      makeMember({ userId: "u1", name: "Alice", overcommitLevel: "NONE" }),
      makeMember({ userId: "u2", name: "Bob", overcommitLevel: "MODERATE" }),
    ];
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity(members),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(screen.getByTestId("team-capacity-totals-status")).toHaveTextContent("⚠️ MODERATE");
  });

  it("shows NONE status in totals row when all members are NONE", () => {
    const members = [
      makeMember({ userId: "u1", name: "Alice", overcommitLevel: "NONE" }),
      makeMember({ userId: "u2", name: "Bob", overcommitLevel: "NONE" }),
    ];
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity(members),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(screen.getByTestId("team-capacity-totals-status")).toHaveTextContent("✅ OK");
  });

  it("does not show totals row when members list is empty", () => {
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: makeTeamCapacity([]),
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(screen.queryByTestId("team-capacity-totals")).not.toBeInTheDocument();
  });

  // ── fetchTeamCapacity called on mount ─────────────────────────────────────

  it("calls fetchTeamCapacity on mount with the weekStart", () => {
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: null,
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(mockFetchTeamCapacity).toHaveBeenCalledWith("2026-03-16");
  });

  it("does not call fetchTeamCapacity when flag is off", () => {
    mockCapacityTracking = false;
    mockUseTeamCapacity.mockReturnValue({
      teamCapacity: null,
      loading: false,
      error: null,
      fetchTeamCapacity: mockFetchTeamCapacity,
      clearError: vi.fn(),
    });
    render(<TeamCapacityPanel weekStart="2026-03-16" />);
    expect(mockFetchTeamCapacity).not.toHaveBeenCalled();
  });
});
