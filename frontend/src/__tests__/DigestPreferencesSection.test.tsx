import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiProvider } from "../api/ApiContext.js";
import { DigestPreferencesSection } from "../components/DigestPreferencesSection.js";
import { AuthProvider, type AuthUser } from "../context/AuthContext.js";

const BASE_URL = "https://example.test/api/v1";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderWithProviders(user: AuthUser) {
  return render(
    <AuthProvider user={user} token="dev-local-token">
      <ApiProvider baseUrl={BASE_URL}>
        <DigestPreferencesSection />
      </ApiProvider>
    </AuthProvider>,
  );
}

describe("DigestPreferencesSection", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("does not render for non-admin users", () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    renderWithProviders({
      userId: "user-1",
      orgId: "org-1",
      displayName: "Taylor",
      roles: ["IC"],
      timezone: "America/Chicago",
    });

    expect(screen.queryByTestId("digest-preferences-section")).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("loads the current digest schedule for admin users", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const request = input instanceof Request ? input : new Request(String(input), init);
      expect(request.method).toBe("GET");
      expect(request.url).toBe(`${BASE_URL}/admin/org-policy`);

      return jsonResponse({
        chessKingRequired: true,
        chessMaxKing: 1,
        chessMaxQueen: 2,
        lockDay: "MONDAY",
        lockTime: "10:00",
        reconcileDay: "FRIDAY",
        reconcileTime: "16:00",
        blockLockOnStaleRcdo: true,
        rcdoStalenessThresholdMinutes: 60,
        digestDay: "TUESDAY",
        digestTime: "09:30",
      });
    });
    vi.stubGlobal("fetch", fetchMock);

    renderWithProviders({
      userId: "admin-1",
      orgId: "org-1",
      displayName: "Admin",
      roles: ["ADMIN"],
      timezone: "America/Chicago",
    });

    expect(await screen.findByTestId("digest-preferences-section")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId("digest-day-select")).toHaveValue("TUESDAY"));
    expect(screen.getByTestId("digest-time-input")).toHaveValue("09:30");
    expect(screen.getByTestId("save-digest-preferences-btn")).toBeDisabled();
  });

  it("saves updated digest preferences for admin users", async () => {
    const patchBodies: unknown[] = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const request = input instanceof Request ? input : new Request(String(input), init);

      if (request.method === "GET") {
        return jsonResponse({
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
        });
      }

      if (request.method === "PATCH") {
        patchBodies.push(await request.json());
        expect(request.url).toBe(`${BASE_URL}/admin/org-policy/digest`);
        return jsonResponse({
          chessKingRequired: true,
          chessMaxKing: 1,
          chessMaxQueen: 2,
          lockDay: "MONDAY",
          lockTime: "10:00",
          reconcileDay: "FRIDAY",
          reconcileTime: "16:00",
          blockLockOnStaleRcdo: true,
          rcdoStalenessThresholdMinutes: 60,
          digestDay: "MONDAY",
          digestTime: "08:15",
        });
      }

      return jsonResponse({
        error: {
          code: "NOT_FOUND",
          message: "Not found",
          details: [],
        },
      }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);

    renderWithProviders({
      userId: "admin-1",
      orgId: "org-1",
      displayName: "Admin",
      roles: ["MANAGER", "ADMIN"],
      timezone: "America/Chicago",
    });

    await screen.findByTestId("digest-preferences-section");
    await waitFor(() => expect(screen.getByTestId("digest-day-select")).toHaveValue("FRIDAY"));

    fireEvent.change(screen.getByTestId("digest-day-select"), { target: { value: "MONDAY" } });
    fireEvent.change(screen.getByTestId("digest-time-input"), { target: { value: "08:15" } });

    expect(screen.getByTestId("save-digest-preferences-btn")).toBeEnabled();
    await userEvent.click(screen.getByTestId("save-digest-preferences-btn"));

    await waitFor(() => {
      expect(patchBodies).toEqual([
        {
          digestDay: "MONDAY",
          digestTime: "08:15",
        },
      ]);
    });

    expect(screen.getByTestId("digest-preferences-success")).toHaveTextContent("Weekly digest schedule saved.");
    expect(screen.getByTestId("digest-day-select")).toHaveValue("MONDAY");
    expect(screen.getByTestId("digest-time-input")).toHaveValue("08:15");
    expect(screen.getByTestId("save-digest-preferences-btn")).toBeDisabled();
  });
});
