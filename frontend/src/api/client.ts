/**
 * API client factory for the Weekly Commitments service.
 *
 * Wraps the generated openapi-fetch client with:
 * - Bearer token injection from AuthContext (per-request, not at construction)
 * - Base URL configuration
 * - Standard error extraction
 *
 * The client uses only the standard `Authorization: Bearer <token>` header.
 * In dev mode the token is a structured string (`dev:<userId>:<orgId>:<roles>`)
 * that {@link DevRequestAuthenticator} can decode. In production, it is a
 * real JWT that {@link JwksRequestAuthenticator} validates via JWKS.
 */
import { createWeeklyCommitmentsClient, type WeeklyCommitmentsClient } from "@weekly-commitments/contracts";
import type { Middleware } from "openapi-fetch";

export interface ApiClientUser {
  userId: string;
  orgId: string;
  roles: string[];
}

export interface ApiClientOptions {
  baseUrl: string;
  getToken: () => string;
  getUser: () => ApiClientUser;
}

/**
 * Build a structured dev token that encodes user identity.
 *
 * Format: `dev:<userId>:<orgId>:<comma-separated-roles>`
 *
 * This is only used when the host-provided token starts with `dev-` or
 * `stub-`, indicating a local/dev environment. For real JWTs the token
 * is forwarded as-is.
 */
export function buildDevToken(user: ApiClientUser): string {
  return `dev:${user.userId}:${user.orgId}:${user.roles.join(",")}`;
}

/**
 * Create a configured API client that auto-injects auth headers
 * on every request. This ensures token refreshes and user-context
 * changes are picked up without needing to recreate the client.
 *
 * For dev/stub tokens the identity is encoded into a structured
 * Bearer token so no extra X- headers are needed — the same
 * `Authorization` contract is used in all environments.
 */
export function createApiClient(opts: ApiClientOptions): WeeklyCommitmentsClient {
  const client = createWeeklyCommitmentsClient({
    baseUrl: opts.baseUrl,
  });

  const authMiddleware: Middleware = {
    onRequest({ request }) {
      const rawToken = opts.getToken();
      const user = opts.getUser();

      // In dev/stub mode, encode identity into the token itself so the
      // backend can extract it from Authorization alone (no X- headers).
      const isDevToken = rawToken.startsWith("dev-") || rawToken.startsWith("stub-");
      const token = isDevToken ? buildDevToken(user) : rawToken;

      request.headers.set("Authorization", `Bearer ${token}`);
      request.headers.set("Content-Type", "application/json");
      return request;
    },
  };

  client.use(authMiddleware);

  return client;
}
