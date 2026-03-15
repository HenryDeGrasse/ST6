import { afterEach, describe, expect, it, vi } from "vitest";

import { buildDevToken, createApiClient } from "../api/client.js";

describe("buildDevToken", () => {
  it("encodes user identity into dev:<userId>:<orgId>:<roles> format", () => {
    const token = buildDevToken({
      userId: "user-123",
      orgId: "org-456",
      roles: ["IC", "MANAGER"],
    });
    expect(token).toBe("dev:user-123:org-456:IC,MANAGER");
  });

  it("handles empty roles", () => {
    const token = buildDevToken({
      userId: "user-123",
      orgId: "org-456",
      roles: [],
    });
    expect(token).toBe("dev:user-123:org-456:");
  });
});

describe("createApiClient", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("encodes dev-token identity into structured Bearer token with no X- headers", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("{}", {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const client = createApiClient({
      baseUrl: "https://example.test/api/v1",
      getToken: () => "dev-local-token",
      getUser: () => ({
        userId: "user-123",
        orgId: "org-456",
        roles: ["IC", "MANAGER"],
      }),
    });

    await client.GET("/health");

    expect(fetchMock).toHaveBeenCalledOnce();
    const request = fetchMock.mock.calls[0]?.[0] as Request;
    expect(request.headers.get("Authorization")).toBe(
      "Bearer dev:user-123:org-456:IC,MANAGER",
    );
    // No X- headers should be sent
    expect(request.headers.get("X-User-Id")).toBeNull();
    expect(request.headers.get("X-Org-Id")).toBeNull();
    expect(request.headers.get("X-Roles")).toBeNull();
  });

  it("encodes stub-token identity into structured Bearer token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("{}", {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const client = createApiClient({
      baseUrl: "https://example.test/api/v1",
      getToken: () => "stub-jwt-eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9",
      getUser: () => ({
        userId: "user-abc",
        orgId: "org-def",
        roles: ["IC"],
      }),
    });

    await client.GET("/health");

    expect(fetchMock).toHaveBeenCalledOnce();
    const request = fetchMock.mock.calls[0]?.[0] as Request;
    expect(request.headers.get("Authorization")).toBe(
      "Bearer dev:user-abc:org-def:IC",
    );
    expect(request.headers.get("X-User-Id")).toBeNull();
    expect(request.headers.get("X-Org-Id")).toBeNull();
    expect(request.headers.get("X-Roles")).toBeNull();
  });

  it("passes real JWT token as-is without X- headers", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("{}", {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const realJwt = "eyJhbGciOiJSUzI1NiJ9.payload.signature";
    const client = createApiClient({
      baseUrl: "https://example.test/api/v1",
      getToken: () => realJwt,
      getUser: () => ({
        userId: "user-123",
        orgId: "org-456",
        roles: ["IC"],
      }),
    });

    await client.GET("/health");

    expect(fetchMock).toHaveBeenCalledOnce();
    const request = fetchMock.mock.calls[0]?.[0] as Request;
    // Real JWT is passed through unmodified
    expect(request.headers.get("Authorization")).toBe(`Bearer ${realJwt}`);
    // No X- headers
    expect(request.headers.get("X-User-Id")).toBeNull();
    expect(request.headers.get("X-Org-Id")).toBeNull();
    expect(request.headers.get("X-Roles")).toBeNull();
  });
});
