import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
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
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, status: 200 }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders the host shell with header and persona switcher", async () => {
    render(<HostShell />);
    expect(screen.getByTestId("pa-host-shell")).toBeInTheDocument();
    expect(screen.getByText("PA Host Application")).toBeInTheDocument();
    expect(screen.getByTestId("persona-select")).toBeInTheDocument();
    await screen.findByTestId("create-plan-btn");
  });

  it("defaults to Carol Park persona", async () => {
    render(<HostShell />);
    const select = screen.getByTestId("persona-select") as HTMLSelectElement;
    expect(select.value).toBe("carol");
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
    expect(screen.queryByTestId("wc-slot")).not.toBeInTheDocument();
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

  it("persona switcher changes the mounted user", async () => {
    render(<HostShell />);
    await screen.findByTestId("create-plan-btn");

    // Switch to Alice (IC only — no Dashboard tab)
    fireEvent.change(screen.getByTestId("persona-select"), { target: { value: "alice" } });

    // Dashboard tab should not be visible for IC-only users
    expect(screen.queryByText("Dashboard")).not.toBeInTheDocument();
    expect(screen.queryByText("Executive")).not.toBeInTheDocument();

    // WC app should remount with Alice's context
    await screen.findByTestId("weekly-plan-page");
  });

  it("shows the Executive tab for Dana and mounts the executive dashboard", async () => {
    render(<HostShell />);
    await screen.findByTestId("create-plan-btn");

    fireEvent.change(screen.getByTestId("persona-select"), { target: { value: "dana" } });

    expect(await screen.findByTestId("host-nav-executive")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("host-nav-executive"));

    expect(screen.getByTestId("wc-executive-slot")).toBeInTheDocument();
    expect(screen.getByTestId("wc-executive-remote-mount")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByTestId("executive-dashboard-page")).toBeInTheDocument();
    });
  });

  it("builds reset-seed auth headers from the active persona", async () => {
    render(<HostShell />);
    await screen.findByTestId("create-plan-btn");

    fireEvent.change(screen.getByTestId("persona-select"), { target: { value: "dana" } });
    await screen.findByTestId("host-nav-executive");

    fireEvent.click(screen.getByText("Reset Data"));

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        "/api/v1/dev/reset-seed",
        expect.objectContaining({
          method: "POST",
          headers: expect.objectContaining({
            Authorization: "Bearer dev:c0000000-0000-0000-0000-000000000030:a0000000-0000-0000-0000-000000000001:IC,MANAGER,ADMIN",
          }),
        }),
      );
    });
  });
});
