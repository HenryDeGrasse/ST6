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

  it("renders the host shell and mounts the weekly commitments app", async () => {
    render(<HostShell />);
    expect(screen.getByTestId("pa-host-shell")).toBeInTheDocument();
    expect(screen.getByTestId("wc-slot")).toBeInTheDocument();
    expect(screen.getByTestId("wc-remote-mount")).toBeInTheDocument();
    await screen.findByTestId("create-plan-btn");
    expect(screen.getByTestId("weekly-plan-page")).toBeInTheDocument();
  });

  it("dev tools are collapsed by default", () => {
    render(<HostShell />);
    expect(screen.queryByTestId("persona-select")).not.toBeInTheDocument();
  });

  it("gear button is above normal app chrome but below modal overlays", () => {
    render(<HostShell />);
    const gear = screen.getByLabelText("Toggle dev tools").parentElement;
    expect(gear).toHaveStyle({ zIndex: "120" });
  });

  it("opens dev tools panel on gear click", async () => {
    render(<HostShell />);
    fireEvent.click(screen.getByLabelText("Toggle dev tools"));
    expect(screen.getByTestId("persona-select")).toBeInTheDocument();
    expect(screen.getByText("Reset Data")).toBeInTheDocument();
  });

  it("defaults to Carol Park persona", async () => {
    render(<HostShell />);
    fireEvent.click(screen.getByLabelText("Toggle dev tools"));
    const select = screen.getByTestId("persona-select") as HTMLSelectElement;
    expect(select.value).toBe("carol");
    await screen.findByTestId("create-plan-btn");
  });

  it("persona switcher changes the mounted user and remounts the app", async () => {
    render(<HostShell />);
    await screen.findByTestId("create-plan-btn");

    // Open dev tools and switch to Alice (IC only)
    fireEvent.click(screen.getByLabelText("Toggle dev tools"));
    fireEvent.change(screen.getByTestId("persona-select"), { target: { value: "alice" } });

    // App nav should not show Team Dashboard for IC-only users
    await screen.findByTestId("weekly-plan-page");
    expect(screen.queryByTestId("nav-team-dashboard")).not.toBeInTheDocument();
  });

  it("shows Team Dashboard nav tab for manager personas", async () => {
    render(<HostShell />);
    // Carol is a MANAGER — Team Dashboard tab should appear in the app nav
    await screen.findByTestId("nav-team-dashboard");
  });

  it("shows Executive nav tab for admin personas", async () => {
    render(<HostShell />);
    await screen.findByTestId("create-plan-btn");

    fireEvent.click(screen.getByLabelText("Toggle dev tools"));
    fireEvent.change(screen.getByTestId("persona-select"), { target: { value: "dana" } });

    // Dana is ADMIN — Executive tab appears in the app's own nav
    await screen.findByTestId("nav-executive");
  });

  it("builds reset-seed auth headers from the active persona", async () => {
    render(<HostShell />);
    await screen.findByTestId("create-plan-btn");

    fireEvent.click(screen.getByLabelText("Toggle dev tools"));
    fireEvent.change(screen.getByTestId("persona-select"), { target: { value: "dana" } });
    await screen.findByTestId("nav-executive");

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
