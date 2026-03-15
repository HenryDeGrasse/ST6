package com.weekly.auth;

/**
 * Adapter interface for JWT validation.
 *
 * <p>Implementations validate the JWT signature against the JWKS endpoint
 * and extract claims into a {@link UserPrincipal}. Keys are cached with
 * a 1-hour TTL and refreshed on signature failure (§9.1).
 */
public interface JwtAuthenticator {

    /**
     * Validates the bearer token and returns the authenticated principal.
     *
     * @param bearerToken the raw JWT (without "Bearer " prefix)
     * @return the authenticated user principal
     * @throws AuthenticationException if the token is invalid, expired, or
     *         cannot be verified against the JWKS endpoint
     */
    UserPrincipal authenticate(String bearerToken);
}
