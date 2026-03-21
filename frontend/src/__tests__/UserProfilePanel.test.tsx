import { describe, it, expect } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { UserProfilePanel } from "../components/UserProfile/UserProfilePanel.js";
import { FeatureFlagProvider } from "../context/FeatureFlagContext.js";
import type { UserProfileData } from "../hooks/useUserProfile.js";

/* ── Helpers ──────────────────────────────────────────────────────────────── */

function renderWithFlags(
  ui: React.ReactElement,
  flags: { userProfile: boolean } = { userProfile: true },
) {
  return render(<FeatureFlagProvider flags={flags}>{ui}</FeatureFlagProvider>);
}

function makeProfile(overrides: Partial<UserProfileData> = {}): UserProfileData {
  return {
    userId: "user-1",
    weeksAnalyzed: 6,
    performanceProfile: {
      estimationAccuracy: 0.78,
      completionReliability: 0.85,
      avgCommitsPerWeek: 4.5,
      avgCarryForwardPerWeek: 0.5,
      topCategories: ["Engineering", "Design"],
      categoryCompletionRates: { Engineering: 0.9, Design: 0.75 },
      priorityCompletionRates: { QUEEN: 0.95, ROOK: 0.8 },
    },
    preferences: {
      typicalPriorityPattern: "QUEEN_HEAVY",
      recurringCommitTitles: ["Weekly sync"],
      avgCheckInsPerWeek: 2.5,
      preferredUpdateDays: ["Monday", "Wednesday"],
    },
    trends: {
      strategicAlignmentTrend: "IMPROVING",
      completionTrend: "STABLE",
      carryForwardTrend: "IMPROVING",
    },
    ...overrides,
  };
}

const defaultProps = {
  profile: null,
  loading: false,
  error: null,
};

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("UserProfilePanel", () => {
  it("renders nothing when userProfile flag is disabled", () => {
    const { container } = renderWithFlags(<UserProfilePanel {...defaultProps} />, {
      userProfile: false,
    });
    expect(container.innerHTML).toBe("");
  });

  it("renders the panel header when the flag is enabled", () => {
    renderWithFlags(<UserProfilePanel {...defaultProps} />);
    expect(screen.getByTestId("user-profile-panel")).toBeInTheDocument();
    expect(screen.getByText("My Profile")).toBeInTheDocument();
  });

  it("renders Show/Hide toggle button with 'Show' text initially", () => {
    renderWithFlags(<UserProfilePanel {...defaultProps} />);
    const toggle = screen.getByTestId("user-profile-toggle");
    expect(toggle).toBeInTheDocument();
    expect(toggle).toHaveTextContent("Show");
  });

  it("expanding shows full details including estimation accuracy and completion reliability", () => {
    renderWithFlags(<UserProfilePanel {...defaultProps} profile={makeProfile()} />);

    fireEvent.click(screen.getByTestId("user-profile-toggle"));

    expect(screen.getByTestId("metric-estimation-accuracy")).toBeInTheDocument();
    expect(screen.getByTestId("metric-estimation-accuracy")).toHaveTextContent("78%");

    expect(screen.getByTestId("metric-completion-reliability")).toBeInTheDocument();
    expect(screen.getByTestId("metric-completion-reliability")).toHaveTextContent("85%");
  });

  it("shows loading state when loading=true", () => {
    renderWithFlags(<UserProfilePanel {...defaultProps} loading={true} />);
    fireEvent.click(screen.getByTestId("user-profile-toggle"));

    expect(screen.getByTestId("user-profile-loading")).toBeInTheDocument();
  });

  it("shows error message when error is set", () => {
    renderWithFlags(<UserProfilePanel {...defaultProps} error="Network error" />);
    fireEvent.click(screen.getByTestId("user-profile-toggle"));

    expect(screen.getByTestId("user-profile-error")).toBeInTheDocument();
    expect(screen.getByText("Network error")).toBeInTheDocument();
  });

  it("shows empty state when weeksAnalyzed is 0", () => {
    renderWithFlags(
      <UserProfilePanel {...defaultProps} profile={makeProfile({ weeksAnalyzed: 0 })} />,
    );
    fireEvent.click(screen.getByTestId("user-profile-toggle"));

    expect(screen.getByTestId("user-profile-empty")).toBeInTheDocument();
  });

  it("shows empty state when profile is null", () => {
    renderWithFlags(<UserProfilePanel {...defaultProps} profile={null} />);
    fireEvent.click(screen.getByTestId("user-profile-toggle"));

    expect(screen.getByTestId("user-profile-empty")).toBeInTheDocument();
  });

  it("displays category completion rates in expanded view", () => {
    const profile = makeProfile({
      performanceProfile: {
        estimationAccuracy: 0.78,
        completionReliability: 0.85,
        avgCommitsPerWeek: 4.5,
        avgCarryForwardPerWeek: 0.5,
        topCategories: ["Engineering", "Design"],
        categoryCompletionRates: { Engineering: 0.9, Design: 0.75 },
        priorityCompletionRates: {},
      },
    });

    renderWithFlags(<UserProfilePanel {...defaultProps} profile={profile} />);

    fireEvent.click(screen.getByTestId("user-profile-toggle"));

    expect(screen.getByTestId("user-profile-category-rates")).toBeInTheDocument();
    expect(screen.getByText("Engineering")).toBeInTheDocument();
    expect(screen.getByText("90%")).toBeInTheDocument();
    expect(screen.getByText("Design")).toBeInTheDocument();
    expect(screen.getByText("75%")).toBeInTheDocument();
  });
});
