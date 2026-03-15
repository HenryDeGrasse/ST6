package com.weekly.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Strategy interface for authenticating an incoming HTTP request.
 *
 * <p>Different implementations are activated per environment:
 * <ul>
 *   <li>{@code DevRequestAuthenticator} — prefers a structured
 *       {@code Authorization: Bearer dev:&lt;userId&gt;:&lt;orgId&gt;:&lt;roles&gt;}
 *       token and falls back to legacy {@code X-User-Id} /
 *       {@code X-Org-Id} / {@code X-Roles} headers (local, dev, test profiles)</li>
 *   <li>{@code JwksRequestAuthenticator} — validates {@code Authorization: Bearer}
 *       JWT against the JWKS endpoint (prod, staging profiles)</li>
 * </ul>
 */
public interface RequestAuthenticator {

    /**
     * Authenticates the request and returns the authenticated principal.
     *
     * @param request the incoming HTTP request
     * @return the authenticated {@link UserPrincipal}
     * @throws AuthenticationException if authentication fails or required
     *         credentials are missing
     */
    UserPrincipal authenticate(HttpServletRequest request);
}
