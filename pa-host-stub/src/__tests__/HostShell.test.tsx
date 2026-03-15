import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";

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

import { HostShell } from "../HostShell.js";

describe("PA Host Stub - HostShell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the host shell with header", async () => {
    render(<HostShell />);
    expect(screen.getByTestId("pa-host-shell")).toBeInTheDocument();
    expect(screen.getByText("PA Host Application")).toBeInTheDocument();
    await screen.findByTestId("create-plan-btn");
  });

  it("displays the stub user name", async () => {
    render(<HostShell />);
    expect(screen.getByText("Jane Doe (stub)")).toBeInTheDocument();
    await screen.findByTestId("create-plan-btn");
  });

  it("shows WC slot and mounts the weekly commitments app by default", async () => {
    render(<HostShell />);
    expect(screen.getByTestId("wc-slot")).toBeInTheDocument();
    expect(screen.getByTestId("wc-remote-mount")).toBeInTheDocument();
    await screen.findByTestId("create-plan-btn");
    expect(screen.getByTestId("weekly-plan-page")).toBeInTheDocument();
  });

  it("switches to dashboard tab and mounts manager team view", async () => {
    render(<HostShell />);
    await screen.findByTestId("create-plan-btn");
    fireEvent.click(screen.getByText("Dashboard"));
    expect(screen.getByTestId("wc-dashboard-slot")).toBeInTheDocument();
    expect(screen.getByTestId("wc-dashboard-remote-mount")).toBeInTheDocument();
    // The WC app is mounted with initialRoute="weekly/team" — verify IC slot is gone
    expect(screen.queryByTestId("wc-slot")).not.toBeInTheDocument();
    // Wait for team dashboard page to render
    await waitFor(() => {
      expect(screen.getByTestId("team-dashboard-page")).toBeInTheDocument();
    });
  });

  it("switches back to weekly tab", async () => {
    render(<HostShell />);
    await screen.findByTestId("create-plan-btn");
    fireEvent.click(screen.getByText("Dashboard"));
    await waitFor(() => {
      expect(screen.getByTestId("team-dashboard-page")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByText("Weekly Commitments"));
    expect(screen.getByTestId("wc-slot")).toBeInTheDocument();
    await screen.findByTestId("create-plan-btn");
  });

  it("displays auth/context handoff info in the WC slot", async () => {
    render(<HostShell />);
    expect(screen.getByTestId("wc-slot")).toHaveTextContent("userId");
    expect(screen.getByTestId("wc-slot")).toHaveTextContent("orgId");
    expect(screen.getByTestId("wc-slot")).toHaveTextContent("apiBaseUrl");
    await screen.findByTestId("create-plan-btn");
  });
});
