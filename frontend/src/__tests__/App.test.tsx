import { describe, it, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { App } from "../App.js";

// Mock the API client to prevent real network calls
vi.mock("@weekly-commitments/contracts", async () => {
  const actual = await vi.importActual("@weekly-commitments/contracts");
  return {
    ...actual,
    createWeeklyCommitmentsClient: () => ({
      GET: vi.fn().mockResolvedValue({ data: null, response: { status: 404 } }),
      POST: vi.fn().mockResolvedValue({ data: null, response: { status: 200 } }),
      PATCH: vi.fn().mockResolvedValue({ data: null, response: { status: 200 } }),
      DELETE: vi.fn().mockResolvedValue({ data: null, response: { status: 204, ok: true } }),
      use: vi.fn(),
    }),
  };
});

vi.mock("../pages/ExecutiveDashboardPage.js", () => ({
  ExecutiveDashboardPage: () => <div data-testid="executive-dashboard-page">Executive Dashboard</div>,
}));

describe("App", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.replaceState({}, "", "/");
  });

  it("renders the app shell inside the theme root", async () => {
    render(<App />);
    expect(screen.getByTestId("wc-theme-root")).toHaveClass("wc-theme");
    expect(screen.getByTestId("weekly-commitments-app")).toBeInTheDocument();
    await screen.findByTestId("create-plan-btn");
  });

  it("renders the weekly plan page with week selector", async () => {
    render(<App />);
    expect(screen.getByTestId("weekly-plan-page")).toBeInTheDocument();
    expect(screen.getByTestId("week-selector")).toBeInTheDocument();
    await screen.findByTestId("create-plan-btn");
  });

  it("shows create plan prompt when no plan exists", async () => {
    render(<App />);
    // Wait for async fetch to complete (returns 404 → no plan)
    const createBtn = await screen.findByTestId("create-plan-btn");
    expect(createBtn).toBeInTheDocument();
  });

  it("accepts custom user and token props", async () => {
    const customUser = {
      userId: "test-user-id",
      orgId: "test-org-id",
      displayName: "Test User",
      roles: ["IC"],
      timezone: "UTC",
    };
    render(<App user={customUser} token="test-token" />);
    expect(screen.getByTestId("weekly-plan-page")).toBeInTheDocument();
    await screen.findByTestId("create-plan-btn");
  });

  it("does not create a root stacking context that traps overlays under host chrome", async () => {
    const { container } = render(<App />);
    await screen.findByTestId("create-plan-btn");

    expect(container.querySelector('div[style*="z-index: 1"]')).toBeNull();
  });

  it("shows executive navigation for admins when the feature flag is enabled", async () => {
    const executiveUser = {
      userId: "exec-user-id",
      orgId: "test-org-id",
      displayName: "Executive User",
      roles: ["ADMIN"],
      timezone: "UTC",
    };

    render(<App user={executiveUser} initialRoute="admin" featureFlags={{ executiveDashboard: true }} />);

    fireEvent.click(screen.getByTestId("nav-executive"));

    expect(screen.getByTestId("executive-dashboard-page")).toBeInTheDocument();
    expect(screen.getByText("Executive Dashboard")).toBeInTheDocument();
  });

  it("supports standalone dev route alias /teamdashboard", async () => {
    window.history.replaceState({}, "", "/teamdashboard");

    render(<App />);

    expect(screen.getByTestId("team-dashboard-page")).toBeInTheDocument();
    expect(screen.getByTestId("nav-team-dashboard")).toBeInTheDocument();
  });

  it("updates the standalone URL when navigating to Team Dashboard", async () => {
    render(<App />);
    fireEvent.click(screen.getByTestId("nav-team-dashboard"));

    expect(window.location.pathname).toBe("/teamdashboard");
    expect(screen.getByTestId("team-dashboard-page")).toBeInTheDocument();
  });
});
