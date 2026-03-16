import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
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

describe("App", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the Weekly Commitments heading inside the theme root", async () => {
    render(<App />);
    expect(screen.getByTestId("wc-theme-root")).toHaveClass("wc-theme");
    expect(screen.getByText("Weekly Commitments")).toBeInTheDocument();
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
});
